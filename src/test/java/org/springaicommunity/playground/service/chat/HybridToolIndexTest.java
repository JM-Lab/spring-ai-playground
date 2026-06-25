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

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridToolIndexTest {

    private static ToolReference ref(String name) {
        return ToolReference.builder().toolName(name).summary(name + " summary").build();
    }

    private static ToolSearchRequest query(String text) {
        return new ToolSearchRequest("session-1", text, 3, null);
    }

    static final class RecordingSemanticIndex implements ToolIndex {

        final List<ToolReference> indexed = new ArrayList<>();
        List<ToolReference> nextResults = List.of();
        ToolSearchRequest lastRequest;

        @Override
        public void indexTool(String sessionId, ToolReference toolReference) {
            this.indexed.add(toolReference);
        }

        @Override
        public void indexTools(String sessionId, List<ToolReference> toolReferences) {
            this.indexed.addAll(toolReferences);
        }

        @Override
        public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
            this.lastRequest = toolSearchRequest;
            return ToolSearchResponse.builder().toolReferences(this.nextResults).build();
        }

        @Override
        public void clearIndex(String sessionId) {
            this.indexed.clear();
        }
    }

    @Test
    void exactNameMatchReturnsOnlyThatToolAndSkipsSemanticSearch() {
        RecordingSemanticIndex semantic = new RecordingSemanticIndex();
        HybridToolIndex index = new HybridToolIndex(semantic);
        index.indexTools("session-1", List.of(ref("getWeather"), ref("getCurrentTime")));
        semantic.nextResults = List.of(ref("unrelated"));

        ToolSearchResponse response = index.search(query("getWeather"));

        assertThat(response.toolReferences()).extracting(ToolReference::toolName).containsExactly("getWeather");
        assertThat(semantic.lastRequest).isNull();
    }

    @Test
    void exactMatchIsCaseInsensitiveAndTrimmed() {
        RecordingSemanticIndex semantic = new RecordingSemanticIndex();
        HybridToolIndex index = new HybridToolIndex(semantic);
        index.indexTools("session-1", List.of(ref("getWeather")));

        assertThat(index.search(query("  GETWEATHER  ")).toolReferences())
                .extracting(ToolReference::toolName).containsExactly("getWeather");
    }

    @Test
    void noExactMatchDelegatesToSemanticSearch() {
        RecordingSemanticIndex semantic = new RecordingSemanticIndex();
        HybridToolIndex index = new HybridToolIndex(semantic);
        index.indexTools("session-1", List.of(ref("getWeather")));
        semantic.nextResults = List.of(ref("getWeather"), ref("getForecast"));

        ToolSearchResponse response = index.search(query("what is the weather today"));

        assertThat(response.toolReferences()).extracting(ToolReference::toolName)
                .containsExactly("getWeather", "getForecast");
        assertThat(semantic.lastRequest).isNotNull();
    }

    @Test
    void clearIndexDropsExactMatchesForSession() {
        RecordingSemanticIndex semantic = new RecordingSemanticIndex();
        HybridToolIndex index = new HybridToolIndex(semantic);
        index.indexTools("session-1", List.of(ref("getWeather")));
        index.clearIndex("session-1");

        ToolSearchResponse response = index.search(query("getWeather"));

        assertThat(response.toolReferences()).isEmpty();
        assertThat(semantic.lastRequest).isNotNull();
    }
}
