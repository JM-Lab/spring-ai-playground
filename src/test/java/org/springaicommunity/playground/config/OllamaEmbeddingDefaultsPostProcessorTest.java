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
package org.springaicommunity.playground.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaEmbeddingDefaultsPostProcessorTest {

    private final OllamaEmbeddingDefaultsPostProcessor postProcessor = new OllamaEmbeddingDefaultsPostProcessor();

    private final OllamaEmbeddingModel delegate = mock(OllamaEmbeddingModel.class);

    private final EmbeddingModel wrapped =
            (EmbeddingModel) this.postProcessor.postProcessAfterInitialization(this.delegate, "embeddingModel");

    private ArgumentCaptor<EmbeddingRequest> stubDelegateCall() {
        ArgumentCaptor<EmbeddingRequest> sent = ArgumentCaptor.forClass(EmbeddingRequest.class);
        when(this.delegate.call(any(EmbeddingRequest.class)))
                .thenReturn(new EmbeddingResponse(List.of(new Embedding(new float[] {0.1f}, 0))));
        return sent;
    }

    @Test
    void onlyOllamaEmbeddingModelsAreWrapped() {
        assertThat(this.wrapped).isNotSameAs(this.delegate);
        Object other = new Object();
        assertThat(this.postProcessor.postProcessAfterInitialization(other, "other")).isSameAs(other);
    }

    @Test
    void plainOptionsAreRetypedSoTheDelegateMergesItsDefaults() {
        ArgumentCaptor<EmbeddingRequest> sent = stubDelegateCall();
        EmbeddingRequest plain = new EmbeddingRequest(List.of("text"),
                EmbeddingOptions.builder().model("qwen3-embedding:0.6b").build());

        this.wrapped.call(plain);

        verify(this.delegate).call(sent.capture());
        assertThat(sent.getValue().getOptions()).isInstanceOf(OllamaEmbeddingOptions.class);
        assertThat(sent.getValue().getOptions().getModel()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(sent.getValue().getInstructions()).containsExactly("text");
    }

    @Test
    void nullAndOllamaTypedOptionsPassThroughUntouched() {
        ArgumentCaptor<EmbeddingRequest> sent = stubDelegateCall();
        EmbeddingRequest nullOptions = new EmbeddingRequest(List.of("a"), null);
        EmbeddingRequest ollamaOptions = new EmbeddingRequest(List.of("b"),
                OllamaEmbeddingOptions.builder().keepAlive("5m").build());

        this.wrapped.call(nullOptions);
        this.wrapped.call(ollamaOptions);

        verify(this.delegate, times(2)).call(sent.capture());
        assertThat(sent.getAllValues()).containsExactly(nullOptions, ollamaOptions);
    }

    @Test
    void modelLessOptionsAreDroppedSoTheConfiguredDefaultModelApplies() {
        ArgumentCaptor<EmbeddingRequest> sent = stubDelegateCall();

        this.wrapped.call(new EmbeddingRequest(List.of("query"), EmbeddingOptions.builder().build()));

        verify(this.delegate).call(sent.capture());
        assertThat(sent.getValue().getOptions()).isNull();
        assertThat(sent.getValue().getInstructions()).containsExactly("query");
    }

    @Test
    void modelLessOptionsWithDimensionsStayPlainSoTheDelegateStillMergesTheConfiguredModel() {
        ArgumentCaptor<EmbeddingRequest> sent = stubDelegateCall();
        EmbeddingRequest dimensionsOnly = new EmbeddingRequest(List.of("query"),
                EmbeddingOptions.builder().dimensions(512).build());

        this.wrapped.call(dimensionsOnly);

        verify(this.delegate).call(sent.capture());
        assertThat(sent.getValue()).isSameAs(dimensionsOnly);
    }

    @Test
    void documentEmbedsCarryNoInventedModel() {
        ArgumentCaptor<EmbeddingRequest> sent = stubDelegateCall();

        this.wrapped.embed(new Document("doc text"));

        verify(this.delegate).call(sent.capture());
        assertThat(sent.getValue().getOptions()).isNull();
        assertThat(sent.getValue().getInstructions()).containsExactly("doc text");
    }

    @Test
    void dimensionsComeFromTheDelegate() {
        when(this.delegate.dimensions()).thenReturn(1024);

        assertThat(this.wrapped.dimensions()).isEqualTo(1024);
    }
}
