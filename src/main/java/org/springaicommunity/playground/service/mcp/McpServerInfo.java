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
import java.util.Objects;
import java.util.Set;

public record McpServerInfo(McpTransportType mcpTransportType, String serverName,
                            String description, long createTimestamp, long updateTimestamp, String connectionAsJson,
                            String category, Set<String> tags, Long lastUsedAtEpochMs) {

    public static final String DEFAULT_CATEGORY = "CUSTOM";

    public McpServerInfo {
        category = (category == null || category.isBlank()) ? DEFAULT_CATEGORY : category;
        tags = tags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tags));
    }

    public McpServerInfo(McpTransportType mcpTransportType, String serverName, String description,
            long createTimestamp, long updateTimestamp, String connectionAsJson,
            String category, Set<String> tags) {
        this(mcpTransportType, serverName, description, createTimestamp, updateTimestamp, connectionAsJson,
                category, tags, null);
    }

    public McpServerInfo(McpTransportType mcpTransportType, String serverName, String description,
            long createTimestamp, long updateTimestamp, String connectionAsJson) {
        this(mcpTransportType, serverName, description, createTimestamp, updateTimestamp, connectionAsJson,
                DEFAULT_CATEGORY, Set.of(), null);
    }

    public McpServerInfo mutate(McpTransportType mcpTransportType, String serverName, String description,
            long updateTimestamp, String connectionAsJson) {
        return new McpServerInfo(mcpTransportType, serverName, description, this.createTimestamp, updateTimestamp,
                connectionAsJson, this.category, this.tags, this.lastUsedAtEpochMs);
    }

    public McpServerInfo mutate(McpTransportType mcpTransportType, String serverName, String description,
            long updateTimestamp, String connectionAsJson, String category, Set<String> tags) {
        return new McpServerInfo(mcpTransportType, serverName, description, this.createTimestamp, updateTimestamp,
                connectionAsJson, category, tags, this.lastUsedAtEpochMs);
    }

    public McpServerInfo withLastUsedAt(long epochMs) {
        return new McpServerInfo(mcpTransportType, serverName, description, createTimestamp, updateTimestamp,
                connectionAsJson, category, tags, epochMs);
    }

    // lastUsedAtEpochMs is volatile usage metadata, not identity — excluding it keeps a stamped
    // instance equal to its pre-stamp self for ListBox selection, built-in detection, and dedup.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof McpServerInfo other)) return false;
        return createTimestamp == other.createTimestamp
                && updateTimestamp == other.updateTimestamp
                && mcpTransportType == other.mcpTransportType
                && Objects.equals(serverName, other.serverName)
                && Objects.equals(description, other.description)
                && Objects.equals(connectionAsJson, other.connectionAsJson)
                && Objects.equals(category, other.category)
                && Objects.equals(tags, other.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mcpTransportType, serverName, description, createTimestamp, updateTimestamp,
                connectionAsJson, category, tags);
    }
}
