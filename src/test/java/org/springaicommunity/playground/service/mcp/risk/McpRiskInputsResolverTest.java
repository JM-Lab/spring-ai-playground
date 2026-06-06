/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springaicommunity.playground.service.mcp.risk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry.OAuthDefaults;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry.TransportSpec;
import org.springaicommunity.playground.service.mcp.catalog.McpRiskFactors;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRiskInputsResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpRiskInputsResolver resolver = new McpRiskInputsResolver(objectMapper);

    @Test
    void extractsUrlFromConnectionJson() {
        String json = "{\"url\":\"https://api.example.com/mcp\",\"endpoint\":\"/messages\"}";
        assertEquals("https://api.example.com/mcp", resolver.extractUrl(json));
    }

    @Test
    void extractsEndpointWhenUrlMissing() {
        String json = "{\"endpoint\":\"https://api.example.com/sse\"}";
        assertEquals("https://api.example.com/sse", resolver.extractUrl(json));
    }

    @Test
    void emptyConnectionJsonReturnsNull() {
        assertEquals(null, resolver.extractUrl(""));
        assertEquals(null, resolver.extractUrl(null));
    }

    @Test
    void authModeFromCatalogTakesPrecedence() {
        TransportSpec transport = new TransportSpec(McpTransportType.STREAMABLE_HTTP,
                "https://api.example.com/mcp", null, "OAUTH",
                List.of(), List.of(), List.of(),
                new OAuthDefaults("https://accounts.google.com", List.of()),
                List.of(), null);
        McpCatalogEntry entry = new McpCatalogEntry("Test", "Test", "DEV", Set.of(), 1, "v",
                true, "", "GA", null, false, List.of(transport), "", false, Set.of(), List.of(), "");
        McpServerInfo serverInfo = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "Test", "",
                0L, 0L, "{}");
        assertEquals(McpAuthMode.OAUTH_STANDARD, resolver.resolveAuthMode(serverInfo, entry));
    }

    @Test
    void inferAuthFromConnectionJsonOAuth() {
        String json = "{\"oauth\":{\"issuerUri\":\"https://x\"}}";
        assertEquals(McpAuthMode.OAUTH_STANDARD, resolver.inferFromConnectionJson(json));
    }

    @Test
    void inferAuthFromConnectionJsonBearerHeaders() {
        String json = "{\"headers\":{\"Authorization\":\"Bearer xyz\"}}";
        assertEquals(McpAuthMode.BEARER, resolver.inferFromConnectionJson(json));
    }

    @Test
    void inferAuthDefaultsToNone() {
        assertEquals(McpAuthMode.NONE, resolver.inferFromConnectionJson("{}"));
        assertEquals(McpAuthMode.NONE, resolver.inferFromConnectionJson(null));
    }

    @Test
    void docCompletenessReflectsCatalogFields() {
        TransportSpec transport = new TransportSpec(McpTransportType.STREAMABLE_HTTP,
                "https://api.example.com/mcp", null, "OAUTH",
                List.of(), List.of(), List.of(),
                new OAuthDefaults("https://x.example.com", List.of("read:repo")),
                List.of(), null);
        McpCatalogEntry entry = new McpCatalogEntry("Test", "Test", "DEV", Set.of(), 1, "v",
                true, "Full description.", "GA", null, false,
                List.of(transport), "https://docs.example.com/mcp", true, Set.of(), List.of(),
                "spring-ai-community");
        McpServerInfo serverInfo = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "Test", "",
                0L, 0L, "{}", McpServerInfo.DEFAULT_CATEGORY, Set.of(), System.currentTimeMillis());
        McpServerRiskCalculator.DocCompleteness doc = resolver.docCompletenessOf(serverInfo, entry);
        assertTrue(doc.docsUrlPresent());
        assertTrue(doc.docsAdequate());
        assertTrue(doc.recentlyUsedWithin90Days());
    }

    @Test
    void docCompletenessFlagsMissingDimensions() {
        McpCatalogEntry entry = new McpCatalogEntry("Test", "Test", "DEV", Set.of(), 1, "v",
                true, "Full description.", "GA", null, false,
                List.of(), "", false, Set.of(), List.of(), "spring-ai-community");
        McpServerInfo serverInfo = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "Test", "",
                0L, 0L, "{}", McpServerInfo.DEFAULT_CATEGORY, Set.of(),
                System.currentTimeMillis() - java.time.Duration.ofDays(120).toMillis());
        McpServerRiskCalculator.DocCompleteness doc = resolver.docCompletenessOf(serverInfo, entry);
        assertFalse(doc.docsUrlPresent());
        assertFalse(doc.docsAdequate());
        assertFalse(doc.recentlyUsedWithin90Days());
        assertEquals(3, doc.gapCount());
    }

    @Test
    void inferSideEffectScopeFromRiskFactors() {
        assertEquals(McpToolPublishRiskCalculator.SideEffectScope.REMOTE_ADMIN_SCOPE,
                resolver.inferSideEffectScope(new McpRiskFactors(true, true, false, false)));
        assertEquals(McpToolPublishRiskCalculator.SideEffectScope.REMOTE_ADMIN_SCOPE,
                resolver.inferSideEffectScope(new McpRiskFactors(true, false, false, true)));
        assertEquals(McpToolPublishRiskCalculator.SideEffectScope.REMOTE_WRITE_SCOPE,
                resolver.inferSideEffectScope(new McpRiskFactors(true, false, false, false)));
        assertEquals(McpToolPublishRiskCalculator.SideEffectScope.LOCAL_ONLY,
                resolver.inferSideEffectScope(McpRiskFactors.SAFE_READ_ONLY));
        assertEquals(McpToolPublishRiskCalculator.SideEffectScope.UNKNOWN,
                resolver.inferSideEffectScope(null));
    }

    @Test
    void propertyDescriptionCoverageHandlesAllMissingAllPresent() throws Exception {
        JsonNode schemaAllDescribed = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\",\"description\":\"a\"},"
                        + "\"b\":{\"type\":\"number\",\"description\":\"b\"}}}");
        assertEquals(1.0, resolver.computePropertyDescriptionCoverage(schemaAllDescribed));

        JsonNode schemaNoneDescribed = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"},\"b\":{\"type\":\"number\"}}}");
        assertEquals(0.0, resolver.computePropertyDescriptionCoverage(schemaNoneDescribed));

        JsonNode schemaHalf = objectMapper.readTree(
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\",\"description\":\"a\"},"
                        + "\"b\":{\"type\":\"number\"}}}");
        assertEquals(0.5, resolver.computePropertyDescriptionCoverage(schemaHalf));

        assertEquals(1.0, resolver.computePropertyDescriptionCoverage(null));
    }

    @Test
    void resolveServerInputsBuildsCompleteInputs() {
        TransportSpec transport = new TransportSpec(McpTransportType.STREAMABLE_HTTP,
                "https://api.example.com/mcp", null, "OAUTH",
                List.of(), List.of(), List.of(),
                new OAuthDefaults("https://accounts.google.com", List.of("read")),
                List.of(), null);
        McpCatalogEntry entry = new McpCatalogEntry("Test", "Test", "DEV", Set.of(), 1, "v",
                true, "description", "GA", null, false, List.of(transport), "", false,
                Set.of("vendor-official"), List.of(), "");
        McpServerInfo serverInfo = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "Test", "",
                0L, 0L, "{\"url\":\"https://api.example.com/mcp\"}");
        McpServerRiskCalculator.Inputs inputs = resolver.resolveServerInputs(serverInfo, entry, false, false);
        assertNotNull(inputs);
        assertEquals(McpAuthMode.OAUTH_STANDARD, inputs.authMode());
        assertEquals("https://api.example.com/mcp", inputs.url());
        assertEquals(McpTransportType.STREAMABLE_HTTP, inputs.transportType());
    }
}
