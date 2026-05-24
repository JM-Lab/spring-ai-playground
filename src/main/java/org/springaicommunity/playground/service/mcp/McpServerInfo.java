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
package org.springaicommunity.playground.service.mcp;

import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.LinkedHashSet;
import java.util.Set;

public record McpServerInfo(McpTransportType mcpTransportType, String serverName,
                            String description, long createTimestamp, long updateTimestamp, String connectionAsJson,
                            String category, Set<String> tags) {

    public static final String DEFAULT_CATEGORY = "CUSTOM";

    public McpServerInfo {
        category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
        tags = tags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tags));
    }

    public McpServerInfo(McpTransportType mcpTransportType, String serverName, String description,
            long createTimestamp, long updateTimestamp, String connectionAsJson) {
        this(mcpTransportType, serverName, description, createTimestamp, updateTimestamp, connectionAsJson,
                DEFAULT_CATEGORY, Set.of());
    }

    public McpServerInfo mutate(McpTransportType mcpTransportType, String serverName, String description,
            long updateTimestamp, String connectionAsJson) {
        return new McpServerInfo(mcpTransportType, serverName, description, this.createTimestamp, updateTimestamp,
                connectionAsJson, this.category, this.tags);
    }

    public McpServerInfo mutate(McpTransportType mcpTransportType, String serverName, String description,
            long updateTimestamp, String connectionAsJson, String category, Set<String> tags) {
        return new McpServerInfo(mcpTransportType, serverName, description, this.createTimestamp, updateTimestamp,
                connectionAsJson, category, tags);
    }
}
