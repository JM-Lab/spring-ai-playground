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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpCatalogServiceTest {

    private final McpCatalogService service = new McpCatalogService(new ObjectMapper());

    @Test
    void catalogLoadsWithMinimumExpectedEntries() {
        List<McpCatalogEntry> all = service.getCatalog();
        assertThat(all).hasSizeGreaterThanOrEqualTo(40);
        // Spot-check a few high-signal Tier 1 vendors are present.
        assertThat(all).extracting(McpCatalogEntry::id)
                .contains("GitHub", "Notion", "Slack", "Tavily", "Kakao-PlayMCP", "Microsoft-Learn");
    }

    @Test
    void allEntriesCarryACategoryAndAtLeastOneTransport() {
        for (McpCatalogEntry e : service.getCatalog()) {
            assertThat(e.category()).as(e.id() + " category").isNotBlank();
            assertThat(e.transports()).as(e.id() + " transports").isNotEmpty();
            McpCatalogEntry.TransportSpec t = e.transports().get(0);
            assertThat(t.type()).as(e.id() + " transport type").isNotNull();
            assertThat(t.urlTemplate()).as(e.id() + " url").isNotBlank();
        }
    }

    @Test
    void tierSplitMatchesCatalogSeed() {
        assertThat(service.getByTier(1)).hasSize(18);
        assertThat(service.getByTier(2)).hasSize(39);
    }

    @Test
    void instantiateProducesGhostWithCorrectCategoryAndTags() {
        McpCatalogEntry github = service.findById("GitHub").orElseThrow();
        McpServerInfo ghost = service.instantiate(github, null, Map.of());
        assertThat(ghost.serverName()).isEqualTo("GitHub");
        assertThat(ghost.category()).isEqualTo("DEV");
        assertThat(ghost.tags()).contains("global");
        assertThat(ghost.mcpTransportType()).isEqualTo(McpTransportType.STREAMABLE_HTTP);
        // Connection JSON contains the catalog URL and the unresolved env-var placeholder.
        assertThat(ghost.connectionAsJson()).contains("api.githubcopilot.com")
                .contains("${GITHUB_PERSONAL_ACCESS_TOKEN}");
    }

    @Test
    void instantiateSubstitutesUrlPlaceholders() {
        McpCatalogEntry outlookMail = service.findById("Outlook-Mail").orElseThrow();
        McpServerInfo ghost = service.instantiate(outlookMail, null,
                Map.of("MS_TENANT_ID", "contoso-tenant-uuid"));
        assertThat(ghost.connectionAsJson()).contains("contoso-tenant-uuid")
                .doesNotContain("{MS_TENANT_ID}");
    }

    @Test
    void stdioEntriesLoadOnlyForCurrentOs() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String expectedSuffix;
        if (name.contains("mac") || name.contains("darwin")) expectedSuffix = "-macOS";
        else if (name.contains("win")) expectedSuffix = "-Windows";
        else expectedSuffix = "-Linux";

        List<McpCatalogEntry> stdio = service.getCatalog().stream()
                .filter(entry -> entry.transports().stream()
                        .anyMatch(transport -> transport.type() == McpTransportType.STDIO))
                .toList();
        assertThat(stdio).as("stdio entries on " + name).hasSize(8);
        assertThat(stdio).extracting(McpCatalogEntry::id)
                .allSatisfy(id -> assertThat(id).endsWith(expectedSuffix));
        assertThat(stdio).extracting(McpCatalogEntry::displayName)
                .containsExactlyInAnyOrder("Git", "Memory (Knowledge Graph)", "Sequential Thinking",
                        "Puppeteer", "MCP Everything (Reference Test Server)",
                        "Playwright", "SQLite", "Brave Search");

        for (McpCatalogEntry entry : stdio) {
            assertThat(entry.description()).as(entry.id() + " description prefix")
                    .startsWith("[" + expectedSuffix.substring(1) + "]");
            assertThat(entry.description()).as(entry.id() + " description ends with Docs:")
                    .contains("\nDocs: ");
        }
    }

    @Test
    void substitutePlaceholdersHandlesUserValuesWithSpecialChars() {
        String result = McpCatalogService.substitutePlaceholders("https://x/{API_KEY}/y",
                Map.of("API_KEY", "abc$1\\def"));
        // Literal substitution — no regex interpretation of $ or backslash.
        assertThat(result).isEqualTo("https://x/abc$1\\def/y");
    }
}
