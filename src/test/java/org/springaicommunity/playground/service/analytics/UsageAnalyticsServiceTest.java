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
package org.springaicommunity.playground.service.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.mcp.risk.DefaultIntegrityVerifier;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageAnalyticsServiceTest {

    private ToolSpecService toolSpecService;
    private DefaultIntegrityVerifier integrityVerifier;
    private ChatSystemPromptPresetCatalog presetCatalog;
    private McpCatalogService mcpCatalogService;
    private UsageAnalyticsService service;

    @BeforeEach
    void setUp() {
        this.toolSpecService = mock(ToolSpecService.class);
        this.integrityVerifier = mock(DefaultIntegrityVerifier.class);
        this.presetCatalog = mock(ChatSystemPromptPresetCatalog.class);
        this.mcpCatalogService = mock(McpCatalogService.class);
        this.service = new UsageAnalyticsService(providerOf(this.toolSpecService), this.integrityVerifier,
                this.presetCatalog, this.mcpCatalogService, emptyProvider(), emptyProvider(), emptyProvider(),
                emptyProvider(), emptyProvider(), emptyProvider(), emptyProvider(),
                new SpringAiPlaygroundOptions(null, false, null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        when(provider.getObject()).thenReturn(instance);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenThrow(new IllegalStateException("unavailable"));
        return provider;
    }

    @Test
    void builtinToolKeepsRealName() {
        when(this.integrityVerifier.isReservedToolName("renderChart")).thenReturn(true);
        when(this.toolSpecService.getToolSpecAsOpt("renderChart")).thenReturn(Optional.empty());
        when(this.toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of());

        Map<String, Object> params = this.service.toolCalledParams("renderChart");

        assertEquals("renderChart", params.get("tool_name"));
        assertEquals("builtin", params.get("source"));
    }

    @Test
    void authoredToolNameIsMasked() {
        when(this.integrityVerifier.isReservedToolName("mySecretTool")).thenReturn(false);
        when(this.toolSpecService.getToolSpecAsOpt("mySecretTool")).thenReturn(Optional.of(mock(ToolSpec.class)));
        when(this.toolSpecService.riskLevelOf(any())).thenReturn("L2");
        when(this.toolSpecService.requiresApproval("mySecretTool")).thenReturn(true);

        Map<String, Object> params = this.service.toolCalledParams("mySecretTool");

        assertEquals("authored", params.get("tool_name"));
        assertEquals("authored", params.get("source"));
        assertEquals("L2", params.get("risk_level"));
        assertEquals(true, params.get("hitl"));
    }

    @Test
    void composedAndExternalToolNamesAreMasked() {
        when(this.toolSpecService.getToolSpecAsOpt(any())).thenReturn(Optional.empty());
        when(this.toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("wrappedTool"));

        assertEquals("composed", this.service.toolCalledParams("wrappedTool").get("source"));
        assertEquals("external", this.service.toolCalledParams("upstreamOnlyTool").get("source"));
        assertEquals("external", this.service.toolCalledParams("upstreamOnlyTool").get("tool_name"));
    }

    @Test
    void presetLabelIsCatalogIdOrCustom() {
        Preset catalogPreset = new Preset("data-visualizer", "Data Visualizer", "", "p", null, null, null);
        Preset userPreset = new Preset("my-preset", "Mine", "", "p", null, null, null);
        when(this.presetCatalog.findById("data-visualizer")).thenReturn(Optional.of(catalogPreset));
        when(this.presetCatalog.findById("my-preset")).thenReturn(Optional.empty());

        assertEquals("data-visualizer", this.service.presetLabel(catalogPreset));
        assertEquals("custom", this.service.presetLabel(userPreset));
        assertNull(this.service.presetLabel(null));
    }

    @Test
    void mcpServerParamsMaskNonCatalogServers() {
        when(this.mcpCatalogService.findByServerName("my-private-server")).thenReturn(Optional.empty());
        McpServerInfo custom = new McpServerInfo(McpTransportType.STDIO, "my-private-server", "", 0L, 0L,
                "{\"command\":\"npx\"}");

        Map<String, Object> params = this.service.mcpServerAddedParams(custom);

        assertEquals("STDIO", params.get("transport"));
        assertEquals("custom", params.get("catalog_id"));
        assertEquals(false, params.get("oauth"));
        assertFalse(params.containsValue("my-private-server"));
    }

    @Test
    void mcpServerParamsUseCatalogIdAndDetectOauth() {
        McpCatalogEntry entry = mock(McpCatalogEntry.class);
        when(entry.id()).thenReturn("github");
        when(this.mcpCatalogService.findByServerName("github")).thenReturn(Optional.of(entry));
        McpServerInfo catalog = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "github", "", 0L, 0L,
                "{\"url\":\"https://api.example.com/mcp\",\"oauth\":{\"scope\":\"read\"}}");

        Map<String, Object> params = this.service.mcpServerAddedParams(catalog);

        assertEquals("github", params.get("catalog_id"));
        assertEquals(true, params.get("oauth"));
    }

    @Test
    void documentParamsCarryExtensionOnly() {
        Map<String, Object> params = this.service.documentIndexedParams("Q3 Financial Report.PDF", 42);
        assertEquals("pdf", params.get("doc_type"));
        assertEquals(42, params.get("chunk_count"));
        assertFalse(params.containsValue("Q3 Financial Report.PDF"));

        assertEquals("unknown", this.service.documentIndexedParams("noextension", 1).get("doc_type"));
        assertEquals("unknown", this.service.documentIndexedParams(null, 1).get("doc_type"));
    }

    @Test
    void dailySnapshotFiresOncePerDay() {
        assertTrue(this.service.dailyUsageSnapshot().isPresent());
        assertTrue(this.service.dailyUsageSnapshot().isEmpty());
    }

    @Test
    void userPropertiesSurviveUnavailableServices() {
        Map<String, Object> properties = this.service.userProperties(true);
        assertEquals("desktop", properties.get("app_surface"));
        assertTrue(properties.containsKey("app_version"));
        assertFalse(properties.containsKey("platform"));
    }
}
