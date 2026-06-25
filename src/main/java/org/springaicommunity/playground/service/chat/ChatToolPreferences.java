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
package org.springaicommunity.playground.service.chat;

import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ChatToolPreferences(boolean useBuiltinMcp, Set<String> exposedToolIds, List<String> ragDocInfoIds,
        Map<McpTransportType, List<String>> mcpServerNames, ReasoningEffort reasoningEffort, boolean dynamicTools) {

    public ChatToolPreferences {
        exposedToolIds = exposedToolIds == null ? Set.of() : Set.copyOf(exposedToolIds);
        ragDocInfoIds = ragDocInfoIds == null ? List.of() : List.copyOf(ragDocInfoIds);
        mcpServerNames = mcpServerNames == null ? Map.of() : Map.copyOf(mcpServerNames);
        reasoningEffort = reasoningEffort == null ? ReasoningEffort.DEFAULT : reasoningEffort;
    }

    public static ChatToolPreferences defaults() {
        return new ChatToolPreferences(false, Set.of(), List.of(), Map.of(), ReasoningEffort.DEFAULT, false);
    }

    public ChatToolPreferences withDynamicTools(boolean dynamicTools) {
        return new ChatToolPreferences(useBuiltinMcp, exposedToolIds, ragDocInfoIds, mcpServerNames,
                reasoningEffort, dynamicTools);
    }
}
