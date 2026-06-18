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
package org.springaicommunity.playground.service.mcp.catalog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpCatalogOAuthInstantiateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpCatalogService catalogService = new McpCatalogService(objectMapper);

    private JsonNode connectionNode(McpServerInfo info) throws Exception {
        return objectMapper.readTree(info.connectionAsJson());
    }

    private JsonNode oauthNode(McpServerInfo info) throws Exception {
        return connectionNode(info).get("oauth");
    }

    private boolean hasNoOauthBlock(JsonNode connection) {
        JsonNode oauth = connection.get("oauth");
        return oauth == null || oauth.isNull();
    }

    @Test
    void notionInstantiationWiresClientIdTemplate() throws Exception {
        McpCatalogEntry entry = catalogService.findById("Notion").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        JsonNode oauth = oauthNode(info);
        assertThat(oauth.get("client-id").asText()).isEqualTo("${NOTION_OAUTH_CLIENT_ID}");
        assertThat(oauth.get("client-secret").isNull()).isTrue();
    }

    @Test
    void googleCalendarInstantiationUsesPerEntryEnv() throws Exception {
        McpCatalogEntry entry = catalogService.findById("Google-Calendar").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        JsonNode oauth = oauthNode(info);
        assertThat(oauth.get("client-id").asText()).isEqualTo("${GOOGLE_CALENDAR_OAUTH_CLIENT_ID}");
    }

    @Test
    void outlookMailKeepsTenantIdRuntimeTemplateAndAddsOAuthClientIdTemplate() throws Exception {
        McpCatalogEntry entry = catalogService.findById("Outlook-Mail").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        JsonNode oauth = oauthNode(info);
        assertThat(oauth.get("issuer-uri").asText())
                .as("MS_TENANT_ID stays as ${} runtime template — EnvVarResolver substitutes at OAuth flow")
                .isEqualTo("https://login.microsoftonline.com/${MS_TENANT_ID}/v2.0");
        assertThat(oauth.get("client-id").asText())
                .as("OAUTH_CLIENT_ID env var auto-wired as ${} template from requiredEnv")
                .isEqualTo("${OUTLOOK_MAIL_OAUTH_CLIENT_ID}");
    }

    @Test
    void githubHasNoOAuthBlockBecauseNoOAuthDefaults() throws Exception {
        McpCatalogEntry entry = catalogService.findById("GitHub").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        assertThat(hasNoOauthBlock(connectionNode(info)))
                .as("GitHub has no oauthDefaults — no oauth block written")
                .isTrue();
    }

    @Test
    void bearerEntryHasNoOAuthBlock() throws Exception {
        McpCatalogEntry entry = catalogService.findById("Kakao-PlayMCP").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        assertThat(hasNoOauthBlock(connectionNode(info))).isTrue();
    }

    @Test
    void noAuthEntryHasNoOAuthBlock() throws Exception {
        McpCatalogEntry entry = catalogService.findById("Microsoft-Learn").orElseThrow();
        McpServerInfo info = catalogService.instantiate(entry, null, Map.of());
        assertThat(hasNoOauthBlock(connectionNode(info))).isTrue();
    }

    @Test
    void pickEnvTemplateMatchesSuffix() {
        assertThat(McpCatalogService.pickEnvTemplate(
                List.of("MS_TENANT_ID", "OUTLOOK_MAIL_OAUTH_CLIENT_ID"), "_OAUTH_CLIENT_ID"))
                .isEqualTo("${OUTLOOK_MAIL_OAUTH_CLIENT_ID}");
        assertThat(McpCatalogService.pickEnvTemplate(
                List.of("KAKAO_PLAYMCP_TOKEN"), "_OAUTH_CLIENT_ID"))
                .isNull();
        assertThat(McpCatalogService.pickEnvTemplate(null, "_OAUTH_CLIENT_ID")).isNull();
        assertThat(McpCatalogService.pickEnvTemplate(List.of(), "_OAUTH_CLIENT_ID")).isNull();
    }

    @Test
    void allOAuthCapableEntriesGetClientIdTemplateOnInstantiate() throws Exception {
        for (McpCatalogEntry entry : catalogService.getCatalog()) {
            McpCatalogEntry.TransportSpec transport = entry.transports().getFirst();
            String auth = transport.auth();
            if (!"OAUTH".equals(auth) && !"OAUTH_OR_PAT".equals(auth)) continue;
            if (transport.oauthDefaults() == null) continue;
            McpServerInfo info = catalogService.instantiate(entry, transport, Map.of());
            JsonNode oauth = oauthNode(info);
            String expectedClientId = "${" + entry.id().replace("-", "_").toUpperCase() + "_OAUTH_CLIENT_ID}";
            assertThat(oauth.get("client-id").asText())
                    .as("entry %s OAuth client-id auto-wired", entry.id())
                    .isEqualTo(expectedClientId);
        }
    }
}
