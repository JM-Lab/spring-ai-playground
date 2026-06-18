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
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpTagSuggestionServiceTest {

    private final McpCatalogService catalog = new McpCatalogService(new ObjectMapper());

    @Test
    void collectTagsUnionsCatalogTagsAndSavedServerTags() {
        McpServerInfo saved1 = new McpServerInfo(McpTransportType.STDIO, "my-server", "desc",
                0L, 0L, "{}", "DEV", Set.of("custom-tag-1"));
        McpServerInfo saved2 = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "another", "desc",
                0L, 0L, "{}", "UTIL", Set.of("custom-tag-2", "global"));
        McpServerInfoService infoService = mock(McpServerInfoService.class);
        when(infoService.getMcpServerInfos()).thenReturn(Map.of(
                McpTransportType.STDIO, List.of(saved1),
                McpTransportType.STREAMABLE_HTTP, List.of(saved2)));

        McpTagSuggestionService suggestions = new McpTagSuggestionService(catalog, infoService);
        Set<String> tags = suggestions.collectTags();

        assertThat(tags).contains("global", "community", "custom-tag-1", "custom-tag-2");
    }

    @Test
    void collectTagsReturnsCatalogTagsWhenNoSavedServers() {
        McpServerInfoService infoService = mock(McpServerInfoService.class);
        when(infoService.getMcpServerInfos()).thenReturn(Map.of());

        McpTagSuggestionService suggestions = new McpTagSuggestionService(catalog, infoService);
        Set<String> tags = suggestions.collectTags();

        assertThat(tags).contains("global");
    }

    @Test
    void collectTagsTolaratesSavedServerWithNullTags() {
        McpServerInfo saved = new McpServerInfo(McpTransportType.STDIO, "x", "x",
                0L, 0L, "{}", "DEV", null);
        McpServerInfoService infoService = mock(McpServerInfoService.class);
        when(infoService.getMcpServerInfos()).thenReturn(Map.of(
                McpTransportType.STDIO, List.of(saved)));

        McpTagSuggestionService suggestions = new McpTagSuggestionService(catalog, infoService);

        assertThat(suggestions.collectTags()).isNotEmpty();
    }

    @Test
    void collectTagsPreservesInsertionOrder() {
        McpServerInfo saved = new McpServerInfo(McpTransportType.STDIO, "x", "x",
                0L, 0L, "{}", "DEV", Set.of("zzz-late-tag"));
        McpServerInfoService infoService = mock(McpServerInfoService.class);
        when(infoService.getMcpServerInfos()).thenReturn(Map.of(
                McpTransportType.STDIO, List.of(saved)));

        McpTagSuggestionService suggestions = new McpTagSuggestionService(catalog, infoService);
        List<String> ordered = List.copyOf(suggestions.collectTags());

        assertThat(ordered).endsWith("zzz-late-tag");
    }
}
