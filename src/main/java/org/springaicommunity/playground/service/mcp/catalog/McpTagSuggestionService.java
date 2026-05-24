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

import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class McpTagSuggestionService {

    private final McpCatalogService mcpCatalogService;
    private final McpServerInfoService mcpServerInfoService;

    public McpTagSuggestionService(McpCatalogService mcpCatalogService,
            McpServerInfoService mcpServerInfoService) {
        this.mcpCatalogService = mcpCatalogService;
        this.mcpServerInfoService = mcpServerInfoService;
    }

    public Set<String> collectTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (McpCatalogEntry entry : this.mcpCatalogService.getCatalog()) {
            tags.addAll(entry.tags());
        }
        for (List<McpServerInfo> list : this.mcpServerInfoService.getMcpServerInfos().values()) {
            for (McpServerInfo info : list) {
                if (info.tags() != null) tags.addAll(info.tags());
            }
        }
        return tags;
    }
}
