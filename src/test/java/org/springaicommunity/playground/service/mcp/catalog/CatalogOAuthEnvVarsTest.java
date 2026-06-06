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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogOAuthEnvVarsTest {

    private final McpCatalogService catalogService = new McpCatalogService(new ObjectMapper());

    @Test
    void everyOAuthEntryDeclaresPerServiceClientIdVar() {
        for (McpCatalogEntry entry : catalogService.getCatalog()) {
            for (McpCatalogEntry.TransportSpec transport : entry.transports()) {
                String auth = transport.auth();
                if (!"OAUTH".equals(auth) && !"OAUTH_OR_PAT".equals(auth)) continue;
                String expectedClientId = entry.id().replace("-", "_").toUpperCase() + "_OAUTH_CLIENT_ID";
                assertThat(transport.requiredEnv())
                        .as("OAuth entry %s declares per-service env var %s", entry.id(), expectedClientId)
                        .contains(expectedClientId);
            }
        }
    }

    @Test
    void googleSiblingsHaveDistinctClientIdVars() {
        McpCatalogEntry gmail = catalogService.findById("Gmail").orElseThrow();
        McpCatalogEntry calendar = catalogService.findById("Google-Calendar").orElseThrow();
        McpCatalogEntry drive = catalogService.findById("Google-Drive").orElseThrow();
        assertThat(gmail.transports().getFirst().requiredEnv()).contains("GMAIL_OAUTH_CLIENT_ID");
        assertThat(calendar.transports().getFirst().requiredEnv()).contains("GOOGLE_CALENDAR_OAUTH_CLIENT_ID");
        assertThat(drive.transports().getFirst().requiredEnv()).contains("GOOGLE_DRIVE_OAUTH_CLIENT_ID");
    }

    @Test
    void microsoftSiblingsHaveDistinctClientIdVarsAndKeepTenantId() {
        McpCatalogEntry outlookMail = catalogService.findById("Outlook-Mail").orElseThrow();
        McpCatalogEntry teams = catalogService.findById("Microsoft-Teams").orElseThrow();
        McpCatalogEntry oneDrive = catalogService.findById("OneDrive-SharePoint").orElseThrow();
        assertThat(outlookMail.transports().getFirst().requiredEnv())
                .contains("MS_TENANT_ID", "OUTLOOK_MAIL_OAUTH_CLIENT_ID");
        assertThat(teams.transports().getFirst().requiredEnv())
                .contains("MS_TENANT_ID", "MICROSOFT_TEAMS_OAUTH_CLIENT_ID");
        assertThat(oneDrive.transports().getFirst().requiredEnv())
                .contains("MS_TENANT_ID", "ONEDRIVE_SHAREPOINT_OAUTH_CLIENT_ID");
    }

    @Test
    void githubKeepsPersonalAccessTokenAndAddsOauthClientId() {
        McpCatalogEntry entry = catalogService.findById("GitHub").orElseThrow();
        McpCatalogEntry.TransportSpec transport = entry.transports().getFirst();
        assertThat(transport.requiredEnv())
                .contains("GITHUB_PERSONAL_ACCESS_TOKEN", "GITHUB_OAUTH_CLIENT_ID");
    }

    @Test
    void notionEntryDeclaresClientIdEnvVar() {
        McpCatalogEntry entry = catalogService.findById("Notion").orElseThrow();
        McpCatalogEntry.TransportSpec transport = entry.transports().getFirst();
        assertThat(transport.requiredEnv()).containsExactly("NOTION_OAUTH_CLIENT_ID");
    }

    @Test
    void bearerEntriesAreUntouched() {
        McpCatalogEntry kakao = catalogService.findById("Kakao-PlayMCP").orElseThrow();
        McpCatalogEntry.TransportSpec transport = kakao.transports().getFirst();
        assertThat(transport.auth()).isEqualTo("BEARER");
        assertThat(transport.requiredEnv()).containsExactly("KAKAO_PLAYMCP_TOKEN");
    }

    @Test
    void noAuthEntriesHaveNoOAuthEnvVars() {
        for (String id : new String[]{"Microsoft-Learn", "DeepWiki", "Context7", "Korean-Law-MCP"}) {
            McpCatalogEntry entry = catalogService.findById(id).orElseThrow();
            McpCatalogEntry.TransportSpec transport = entry.transports().getFirst();
            assertThat(transport.auth()).isEqualTo("NONE");
            assertThat(transport.requiredEnv()).isEmpty();
        }
    }
}
