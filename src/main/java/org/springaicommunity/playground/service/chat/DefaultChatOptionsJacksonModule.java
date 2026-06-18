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

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;

import java.util.ArrayList;
import java.util.List;

public class DefaultChatOptionsJacksonModule extends SimpleModule {

    public DefaultChatOptionsJacksonModule() {
        addDeserializer(DefaultChatOptions.class, new DefaultChatOptionsDeserializer());
    }

    private static final class DefaultChatOptionsDeserializer extends ValueDeserializer<DefaultChatOptions> {

        @Override
        public DefaultChatOptions deserialize(JsonParser parser, DeserializationContext context) {
            JsonNode node = parser.readValueAsTree();
            ChatOptions.Builder<?> builder = ChatOptions.builder();
            if (node.hasNonNull("model")) builder.model(node.get("model").asText());
            if (node.hasNonNull("temperature")) builder.temperature(node.get("temperature").asDouble());
            if (node.hasNonNull("topP")) builder.topP(node.get("topP").asDouble());
            if (node.hasNonNull("topK")) builder.topK(node.get("topK").asInt());
            if (node.hasNonNull("maxTokens")) builder.maxTokens(node.get("maxTokens").asInt());
            if (node.hasNonNull("frequencyPenalty")) builder.frequencyPenalty(node.get("frequencyPenalty").asDouble());
            if (node.hasNonNull("presencePenalty")) builder.presencePenalty(node.get("presencePenalty").asDouble());
            JsonNode stopSequences = node.get("stopSequences");
            if (stopSequences != null && stopSequences.isArray()) {
                List<String> stops = new ArrayList<>();
                stopSequences.forEach(stop -> stops.add(stop.asText()));
                builder.stopSequences(stops);
            }
            return (DefaultChatOptions) builder.build();
        }
    }
}
