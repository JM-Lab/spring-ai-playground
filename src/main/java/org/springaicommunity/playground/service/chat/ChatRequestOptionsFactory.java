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

import org.springaicommunity.playground.SpringAiPlaygroundOptions;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

@Component
public class ChatRequestOptionsFactory {

    private static final Logger logger = LoggerFactory.getLogger(ChatRequestOptionsFactory.class);

    private static final TypeReference<Map<String, Object>> OVERRIDE_TYPE = new TypeReference<>() {};

    private static final Set<String> PROTECTED_FIELDS = Set.of("baseUrl", "apiKey", "credential",
            "azureOpenAIServiceVersion", "organizationId", "azure", "gitHubModels", "microsoftFoundry",
            "microsoftFoundryServiceVersion", "timeout", "maxRetries", "proxy", "customHeaders", "deploymentName",
            "toolCallbacks", "toolContext");

    private final ObjectMapper overlayMapper;
    private final Integer defaultMaxTokens;

    public ChatRequestOptionsFactory(ObjectMapper objectMapper, SpringAiPlaygroundOptions playgroundOptions) {
        this.overlayMapper = objectMapper.rebuild()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).build();
        this.defaultMaxTokens = playgroundOptions != null && playgroundOptions.chat() != null
                ? playgroundOptions.chat().defaultMaxTokens() : 8_192;
    }

    public ToolCallingChatOptions build(ChatModel chatModel, DefaultChatOptions base, ChatExtraOptions extra,
            ReasoningEffort reasoning) {
        ChatExtraOptions effective = extra == null ? ChatExtraOptions.defaults() : extra;
        ToolCallingChatOptions options = buildForProvider(ChatProvider.from(chatModel), base, effective, reasoning);
        return applyJsonOverride(options, effective.providerOptionsJson());
    }

    private ToolCallingChatOptions buildForProvider(ChatProvider provider, DefaultChatOptions base,
            ChatExtraOptions extra, ReasoningEffort reasoning) {
        return switch (provider) {
            case OPENAI -> buildOpenAi(base, extra, reasoning);
            case OLLAMA -> buildOllama(base, extra, reasoning);
            case GENERIC -> buildGeneric(base);
        };
    }

    private ToolCallingChatOptions buildOpenAi(DefaultChatOptions base, ChatExtraOptions extra,
            ReasoningEffort reasoning) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(base.getModel())
                .maxTokens(maxTokensOrDefault(base))
                .temperature(base.getTemperature())
                .topP(base.getTopP())
                .frequencyPenalty(base.getFrequencyPenalty())
                .presencePenalty(base.getPresencePenalty())
                .streamUsage(true);
        if (extra.seed() != null) builder.seed(extra.seed());
        if (!CollectionUtils.isEmpty(extra.stop())) builder.stop(extra.stop());
        String effort = openAiReasoningEffort(reasoning);
        if (effort != null) builder.reasoningEffort(effort);
        return builder.build();
    }

    private ToolCallingChatOptions buildOllama(DefaultChatOptions base, ChatExtraOptions extra,
            ReasoningEffort reasoning) {
        OllamaChatOptions.Builder builder = OllamaChatOptions.builder()
                .model(base.getModel())
                .temperature(base.getTemperature())
                .topP(base.getTopP())
                .frequencyPenalty(base.getFrequencyPenalty())
                .presencePenalty(base.getPresencePenalty());
        if (base.getTopK() != null) builder.topK(base.getTopK());
        if (base.getMaxTokens() != null) builder.numPredict(base.getMaxTokens());
        if (extra.seed() != null) builder.seed(extra.seed());
        if (!CollectionUtils.isEmpty(extra.stop())) builder.stop(extra.stop());
        applyOllamaThinking(builder, reasoning);
        return builder.build();
    }

    private ToolCallingChatOptions buildGeneric(DefaultChatOptions base) {
        return DefaultToolCallingChatOptions.builder()
                .model(base.getModel())
                .maxTokens(maxTokensOrDefault(base))
                .temperature(base.getTemperature())
                .topP(base.getTopP())
                .frequencyPenalty(base.getFrequencyPenalty())
                .presencePenalty(base.getPresencePenalty())
                .build();
    }

    private Integer maxTokensOrDefault(DefaultChatOptions base) {
        return base.getMaxTokens() != null ? base.getMaxTokens() : this.defaultMaxTokens;
    }

    private String openAiReasoningEffort(ReasoningEffort effort) {
        if (effort == null) return null;
        return switch (effort) {
            case DEFAULT, OFF -> null;
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
        };
    }

    private void applyOllamaThinking(OllamaChatOptions.Builder builder, ReasoningEffort effort) {
        if (effort == null || effort == ReasoningEffort.DEFAULT) return;
        switch (effort) {
            case OFF -> builder.disableThinking();
            case LOW -> builder.thinkLow();
            case MEDIUM -> builder.thinkMedium();
            case HIGH -> builder.thinkHigh();
        }
    }

    private ToolCallingChatOptions applyJsonOverride(ToolCallingChatOptions options, String json) {
        if (!StringUtils.hasText(json)) return options;
        try {
            Map<String, Object> overrides = this.overlayMapper.readValue(json, OVERRIDE_TYPE);
            Object builder = options.mutate();
            overrides.forEach((key, value) -> applyOverride(builder, key, value));
            return (ToolCallingChatOptions) builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logger.warn("chat.options.override-failed error={}", e.getMessage());
        }
        return options;
    }

    private void applyOverride(Object builder, String key, Object value) {
        String property = toCamelCase(key);
        if (invokeMatchingSetter(builder, value, property, true)) return;
        if (invokeMatchingSetter(builder, value, property, false)) return;
        logger.debug("chat.options.override-ignored key={} (no matching builder setter)", key);
    }

    private boolean invokeMatchingSetter(Object builder, Object value, String property, boolean exact) {
        for (Method method : builder.getClass().getMethods()) {
            if (method.getParameterCount() != 1) continue;
            boolean nameMatch = exact ? method.getName().equals(property)
                    : method.getName().equalsIgnoreCase(property);
            if (!nameMatch || isProtected(method.getName())) continue;
            try {
                method.invoke(builder, this.overlayMapper.convertValue(value, method.getParameterTypes()[0]));
                return true;
            } catch (IllegalArgumentException | ReflectiveOperationException ignore) {
            }
        }
        return false;
    }

    private static boolean isProtected(String methodName) {
        for (String field : PROTECTED_FIELDS) {
            if (field.equalsIgnoreCase(methodName)) return true;
        }
        return false;
    }

    private static String toCamelCase(String key) {
        if (key.indexOf('_') < 0) return key;
        StringBuilder camel = new StringBuilder(key.length());
        boolean toUpper = false;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                toUpper = true;
            } else {
                camel.append(toUpper ? Character.toUpperCase(c) : c);
                toUpper = false;
            }
        }
        return camel.toString();
    }
}
