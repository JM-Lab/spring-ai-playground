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
/*
 * TODO(spring-ai-playground): TEMPORARY Jackson mixin configuration.
 * Remove when upstream Spring AI includes fix from <https://github.com/spring-projects/spring-ai/pull/5057>.
 * Based on spring-ai <849d188156bc6d827690d4d6c6609a4bf4cb4416>.
 * deserialization support for "reasoning_content"/"reasoning" fields.
 */
package jm.kr.spring.ai.playground.service.chat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    private static abstract class ChatCompletionMessageMixin {
        @JsonProperty("reasoning_content")
        @JsonAlias("reasoning")
        abstract String reasoningContent();
    }

    @Bean
    public static BeanFactoryPostProcessor springAiMixInRegistrar() {
        return beanFactory -> ModelOptionsUtils.OBJECT_MAPPER.addMixIn(
                OpenAiApi.ChatCompletionMessage.class,
                ChatCompletionMessageMixin.class
        );
    }
}