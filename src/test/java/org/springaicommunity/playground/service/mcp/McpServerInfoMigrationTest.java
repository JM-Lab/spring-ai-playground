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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerInfoMigrationTest {

    @Test
    void compactConstructorDefaultsNullCategoryToCustom() {
        McpServerInfo info = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "x", "d", 1L, 2L, "{}",
                null, null);
        assertThat(info.category()).isEqualTo("CUSTOM");
        assertThat(info.tags()).isEmpty();
    }

    @Test
    void backwardCompatibleSixArgConstructorDefaultsCategoryAndTags() {
        McpServerInfo info = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "x", "d", 1L, 2L, "{}");
        assertThat(info.category()).isEqualTo("CUSTOM");
        assertThat(info.tags()).isEmpty();
    }

    @Test
    void mutatePreservesCategoryAndTagsOnFiveArgSignature() {
        McpServerInfo original = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "x", "d", 1L, 2L, "{}",
                "DEV", Set.of("global"));
        McpServerInfo mutated = original.mutate(McpTransportType.SSE, "y", "d2", 3L, "{\"u\":1}");
        assertThat(mutated.category()).isEqualTo("DEV");
        assertThat(mutated.tags()).containsExactly("global");
    }

    @Test
    void mutateWithSevenArgsUpdatesCategoryAndTags() {
        McpServerInfo original = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "x", "d", 1L, 2L, "{}",
                "DEV", Set.of("global"));
        McpServerInfo mutated = original.mutate(McpTransportType.STREAMABLE_HTTP, "x", "d", 4L, "{}",
                "PROJECT_MGMT", Set.of("kr", "private"));
        assertThat(mutated.category()).isEqualTo("PROJECT_MGMT");
        assertThat(mutated.tags()).containsExactlyInAnyOrder("kr", "private");
    }

    @Test
    void persistenceConvertToDefaultsMissingKeys() {
        McpServerInfoPersistenceService service = stubService();
        Map<String, Object> legacyMap = new HashMap<>();
        legacyMap.put("mcpTransportType", "STREAMABLE_HTTP");
        legacyMap.put("serverName", "legacy-server");
        legacyMap.put("description", "from M6");
        legacyMap.put("createTimestamp", 100L);
        legacyMap.put("updateTimestamp", 200L);
        legacyMap.put("connectionAsJson", "{}");

        McpServerInfo loaded = service.convertTo(legacyMap);

        assertThat(loaded.category()).isEqualTo("CUSTOM");
        assertThat(loaded.tags()).isEmpty();
        assertThat(loaded.serverName()).isEqualTo("legacy-server");
    }

    @Test
    void persistenceConvertToRoundTripsCategoryAndTags() {
        McpServerInfoPersistenceService service = stubService();
        Map<String, Object> map = new HashMap<>();
        map.put("mcpTransportType", "SSE");
        map.put("serverName", "modern-server");
        map.put("description", "from M7");
        map.put("createTimestamp", 100L);
        map.put("updateTimestamp", 200L);
        map.put("connectionAsJson", "{}");
        map.put("category", "DEV");
        map.put("tags", List.of("global", "free-tier"));

        McpServerInfo loaded = service.convertTo(map);

        assertThat(loaded.category()).isEqualTo("DEV");
        assertThat(loaded.tags()).containsExactlyInAnyOrder("global", "free-tier");
    }

    private static McpServerInfoPersistenceService stubService() {
        try {
            return new McpServerInfoPersistenceService(Files.createTempDirectory("mcp-migration-test"),
                    null, null) {
                @Override public void save(McpServerInfo s) {}
                @Override public void onStart() {}
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void tagsSetIsImmutableAfterConstruction() {
        Set<String> mutable = new LinkedHashSet<>();
        mutable.add("global");
        McpServerInfo info = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "x", "d", 1L, 2L, "{}",
                "DEV", mutable);
        mutable.add("kr");
        assertThat(info.tags()).containsExactly("global");
    }
}
