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

import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HybridToolIndex implements ToolIndex {

    private final ToolIndex semantic;
    private final Map<String, Map<String, ToolReference>> byName = new ConcurrentHashMap<>();

    public HybridToolIndex(ToolIndex semantic) {
        this.semantic = semantic;
    }

    @Override
    public void indexTool(String sessionId, ToolReference reference) {
        this.byName.computeIfAbsent(sessionId, session -> new ConcurrentHashMap<>())
                .put(reference.toolName().toLowerCase(Locale.ROOT), reference);
        this.semantic.indexTool(sessionId, reference);
    }

    @Override
    public void indexTools(String sessionId, List<ToolReference> references) {
        Map<String, ToolReference> names = this.byName.computeIfAbsent(sessionId,
                session -> new ConcurrentHashMap<>());
        references.forEach(reference -> names.put(reference.toolName().toLowerCase(Locale.ROOT), reference));
        this.semantic.indexTools(sessionId, references);
    }

    @Override
    public ToolSearchResponse search(ToolSearchRequest request) {
        String query = request.query() == null ? "" : request.query().trim().toLowerCase(Locale.ROOT);
        ToolReference exact = this.byName.getOrDefault(request.sessionId(), Map.of()).get(query);
        if (exact != null) {
            return ToolSearchResponse.builder().toolReferences(List.of(exact)).totalMatches(1).build();
        }
        return this.semantic.search(request);
    }

    @Override
    public void clearIndex(String sessionId) {
        this.byName.remove(sessionId);
        this.semantic.clearIndex(sessionId);
    }
}
