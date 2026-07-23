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

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// OllamaEmbeddingModel merges its configured defaults (keep-alive, truncate, ...) only into requests
// that already carry OllamaEmbeddingOptions; the plain options built by the vector store and the tool
// index skip that merge. Retyping them puts every embedding call on the merging path. Delete this
// workaround once upstream mergeOptions merges defaults into plain-typed requests.
@Component
public class OllamaEmbeddingDefaultsPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        return bean instanceof OllamaEmbeddingModel ollama ? new OllamaTypedOptionsEmbeddingModel(ollama) : bean;
    }

    public static final class OllamaTypedOptionsEmbeddingModel implements EmbeddingModel {

        private final OllamaEmbeddingModel delegate;

        OllamaTypedOptionsEmbeddingModel(OllamaEmbeddingModel delegate) {
            this.delegate = delegate;
        }

        public OllamaEmbeddingModel delegate() {
            return this.delegate;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return this.delegate.call(retyped(request));
        }

        @Override
        public float[] embed(Document document) {
            String text = document.getText();
            Assert.state(text != null, "text must not be null");
            return embed(text);
        }

        @Override
        public int dimensions() {
            return this.delegate.dimensions();
        }

        private static EmbeddingRequest retyped(EmbeddingRequest request) {
            EmbeddingOptions options = request.getOptions();
            if (options == null || options instanceof OllamaEmbeddingOptions) return request;
            if (!StringUtils.hasText(options.getModel())) {
                return options.getDimensions() == null
                        ? new EmbeddingRequest(request.getInstructions(), null) : request;
            }
            return new EmbeddingRequest(request.getInstructions(), OllamaEmbeddingOptions.builder()
                    .model(options.getModel()).dimensions(options.getDimensions()).build());
        }
    }
}
