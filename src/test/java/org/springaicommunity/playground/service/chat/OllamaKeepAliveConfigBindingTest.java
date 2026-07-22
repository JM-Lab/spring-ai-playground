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
import org.springaicommunity.playground.config.OllamaEmbeddingDefaultsPostProcessor;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.ai.ollama.chat.keep-alive=45m")
class OllamaKeepAliveConfigBindingTest {

    @Autowired
    OllamaChatModel chatModel;

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    ChatRequestOptionsFactory factory;

    @Test
    void keepAliveBindsIntoTheChatModelDefaults() {
        assertThat(this.chatModel.getOptions().getKeepAlive()).isEqualTo("45m");
    }

    @Test
    void everyChatRequestCarriesTheBoundKeepAlive() {
        DefaultChatOptions base = (DefaultChatOptions) ChatOptions.builder().model("qwen3.5:4b").build();

        OllamaChatOptions ollama = (OllamaChatOptions) this.factory.build(this.chatModel, base,
                ChatExtraOptions.defaults(), null);

        assertThat(ollama.getKeepAlive()).isEqualTo("45m");
    }

    @Test
    void theEmbeddingModelIsWrappedSoPlainOptionsStillMergeItsDefaults() {
        assertThat(this.embeddingModel)
                .isInstanceOf(OllamaEmbeddingDefaultsPostProcessor.OllamaTypedOptionsEmbeddingModel.class);
    }

}
