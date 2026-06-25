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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentToolIndexTest {

    private static final String SIGNATURE = "Stub|test-embed|16";

    @TempDir
    Path home;

    private final CountingEmbeddingModel embeddingModel = new CountingEmbeddingModel();

    private PersistentToolIndex index(String signature, boolean exact) {
        return new PersistentToolIndex(SimpleVectorStore.builder(this.embeddingModel).build(), signature, this.home,
                exact, true);
    }

    private static ToolReference ref(String name) {
        return ToolReference.builder().toolName(name).summary(name + " does a thing").build();
    }

    private static ToolSearchRequest query(String text) {
        return new ToolSearchRequest("any-session", text, 5, null);
    }

    @Test
    void embedsEachToolOnceAndReusesAcrossSessions() {
        PersistentToolIndex index = index(SIGNATURE, true);
        List<ToolReference> tools = List.of(ref("getWeather"), ref("getTime"));

        index.indexTools("chat-1", tools);
        index.indexTools("chat-2", tools);
        index.indexTools("chat-3", tools);

        assertThat(this.embeddingModel.documentEmbeds.get()).isEqualTo(2);
    }

    @Test
    void reembedsOnlyChangedToolsAndDropsRemovedOnes() {
        PersistentToolIndex index = index(SIGNATURE, true);
        index.indexTools("chat-1", List.of(ref("getWeather"), ref("getTime")));
        assertThat(this.embeddingModel.documentEmbeds.get()).isEqualTo(2);

        index.indexTools("chat-1", List.of(ref("getWeather"), ref("getCrypto")));

        assertThat(this.embeddingModel.documentEmbeds.get()).isEqualTo(3);
        assertThat(toolNames(index.search(query("getTime")))).doesNotContain("getTime");
    }

    @Test
    void exactNameMatchShortCircuitsWithoutVectorSearch() {
        PersistentToolIndex index = index(SIGNATURE, true);
        index.indexTools("chat-1", List.of(ref("getWeather"), ref("getTime")));

        ToolSearchResponse response = index.search(query("getWeather"));

        assertThat(toolNames(response)).containsExactly("getWeather");
    }

    @Test
    void vectorSearchReturnsMatchesWhenNotAnExactName() {
        PersistentToolIndex index = index(SIGNATURE, true);
        index.indexTools("chat-1", List.of(ref("getWeather"), ref("getTime")));

        assertThat(toolNames(index.search(query("getWeather does a thing")))).contains("getWeather");
    }

    @Test
    void clearIndexDoesNotDropTheSharedIndex() {
        PersistentToolIndex index = index(SIGNATURE, true);
        index.indexTools("chat-1", List.of(ref("getWeather")));

        index.clearIndex("chat-1");

        assertThat(toolNames(index.search(query("getWeather")))).containsExactly("getWeather");
    }

    @Test
    void restartWithSameSignatureLoadsEmbeddingsWithoutReembedding() {
        index(SIGNATURE, true).indexTools("chat-1", List.of(ref("getWeather"), ref("getTime")));
        assertThat(this.embeddingModel.documentEmbeds.get()).isEqualTo(2);

        CountingEmbeddingModel afterRestart = new CountingEmbeddingModel();
        PersistentToolIndex reloaded = new PersistentToolIndex(SimpleVectorStore.builder(afterRestart).build(),
                SIGNATURE, this.home, true, true);
        reloaded.indexTools("chat-9", List.of(ref("getWeather"), ref("getTime")));

        assertThat(afterRestart.documentEmbeds.get()).isZero();
        assertThat(toolNames(reloaded.search(query("getWeather")))).containsExactly("getWeather");
    }

    @Test
    void restartWithChangedSignatureReembedsEverything() {
        index(SIGNATURE, true).indexTools("chat-1", List.of(ref("getWeather"), ref("getTime")));

        CountingEmbeddingModel afterModelSwap = new CountingEmbeddingModel();
        PersistentToolIndex reloaded = new PersistentToolIndex(SimpleVectorStore.builder(afterModelSwap).build(),
                "Stub|different-model|16", this.home, true, true);
        reloaded.indexTools("chat-9", List.of(ref("getWeather"), ref("getTime")));

        assertThat(afterModelSwap.documentEmbeds.get()).isEqualTo(2);
    }

    @Test
    void signatureCombinesModelClassNameAndDimensions() {
        assertThat(PersistentToolIndex.signatureOf("OllamaEmbeddingModel", "qwen3-embedding:0.6b", 1024))
                .isEqualTo("OllamaEmbeddingModel|qwen3-embedding:0.6b|1024");
        assertThat(PersistentToolIndex.signatureOf("Stub", null, null)).isEqualTo("Stub||");
    }

    private static List<String> toolNames(ToolSearchResponse response) {
        return response.toolReferences().stream().map(ToolReference::toolName).toList();
    }

    static final class CountingEmbeddingModel implements EmbeddingModel {

        final AtomicInteger documentEmbeds = new AtomicInteger();

        @Override
        public float[] embed(Document document) {
            this.documentEmbeds.incrementAndGet();
            return vectorFor(document.getText());
        }

        @Override
        public float[] embed(String text) {
            return vectorFor(text);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(vectorFor(instructions.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public int dimensions() {
            return 16;
        }

        private static float[] vectorFor(String text) {
            float[] vector = new float[16];
            for (int i = 0; i < text.length(); i++) {
                vector[text.charAt(i) % 16] += 1f;
            }
            return vector;
        }
    }
}
