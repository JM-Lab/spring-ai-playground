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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class ChatRequestOptionsFactoryTest {

    private final ChatRequestOptionsFactory factory = new ChatRequestOptionsFactory(new ObjectMapper(), null);

    private DefaultChatOptions baseOptions() {
        return (DefaultChatOptions) ChatOptions.builder()
                .model("gpt-5-mini").temperature(0.7).topP(0.9).maxTokens(1000)
                .frequencyPenalty(0.1).presencePenalty(0.2).build();
    }

    @Test
    void openAiMapsReasoningSeedStopAndEnablesStreamUsage() {
        ChatExtraOptions extra = new ChatExtraOptions(42, List.of("STOP"), null, null);

        ToolCallingChatOptions options =
                this.factory.build(mock(OpenAiChatModel.class), baseOptions(), extra, ReasoningEffort.MEDIUM);

        assertInstanceOf(OpenAiChatOptions.class, options);
        OpenAiChatOptions openAi = (OpenAiChatOptions) options;
        assertEquals("medium", openAi.getReasoningEffort());
        assertEquals(Integer.valueOf(42), openAi.getSeed());
        assertEquals(List.of("STOP"), openAi.getStop());
        assertNotNull(openAi.getStreamOptions());
        assertEquals("gpt-5-mini", options.getModel());
    }

    @Test
    void ollamaMapsThinkLevelAndMaxTokensToNumPredict() {
        ToolCallingChatOptions options =
                this.factory.build(mock(OllamaChatModel.class), baseOptions(), ChatExtraOptions.defaults(),
                        ReasoningEffort.HIGH);

        assertInstanceOf(OllamaChatOptions.class, options);
        OllamaChatOptions ollama = (OllamaChatOptions) options;
        assertNotNull(ollama.getThinkOption());
        assertEquals(Integer.valueOf(1000), ollama.getNumPredict());
    }

    @Test
    void offDisablesOllamaThinkingButOmitsOpenAiReasoning() {
        OpenAiChatOptions openAi = (OpenAiChatOptions) this.factory.build(mock(OpenAiChatModel.class),
                baseOptions(), ChatExtraOptions.defaults(), ReasoningEffort.OFF);
        assertNull(openAi.getReasoningEffort());

        OllamaChatOptions ollama = (OllamaChatOptions) this.factory.build(mock(OllamaChatModel.class),
                baseOptions(), ChatExtraOptions.defaults(), ReasoningEffort.OFF);
        assertNotNull(ollama.getThinkOption());
    }

    @Test
    void nullReasoningLeavesProviderDefault() {
        OpenAiChatOptions openAi = (OpenAiChatOptions) this.factory.build(mock(OpenAiChatModel.class),
                baseOptions(), ChatExtraOptions.defaults(), null);
        assertNull(openAi.getReasoningEffort());

        OllamaChatOptions ollama = (OllamaChatOptions) this.factory.build(mock(OllamaChatModel.class),
                baseOptions(), ChatExtraOptions.defaults(), null);
        assertNull(ollama.getThinkOption());
    }

    @Test
    void providerJsonOverrideAppliesAndIgnoresConnectionFields() {
        // The override tweaks request params (temperature) but cannot touch connection fields - apiKey is ignored,
        // and the JPMS-inaccessible proxy field no longer blocks the overlay.
        ChatExtraOptions extra =
                new ChatExtraOptions(null, null, "{\"temperature\": 0.1, \"apiKey\": \"ignored\"}", null);

        ToolCallingChatOptions options =
                this.factory.build(mock(OpenAiChatModel.class), baseOptions(), extra, null);

        assertInstanceOf(OpenAiChatOptions.class, options);
        assertEquals(0.1, ((OpenAiChatOptions) options).getTemperature().doubleValue());
    }

    @Test
    void ollamaJsonOverrideBindsSnakeCaseKeys() {
        // OllamaChatOptions fields are @JsonProperty snake_case, so the override (and its placeholder
        // example) must use num_ctx / top_k style keys.
        ChatExtraOptions extra = new ChatExtraOptions(null, null, "{\"num_ctx\": 8192, \"top_k\": 40}", null);

        ToolCallingChatOptions options = this.factory.build(mock(OllamaChatModel.class), baseOptions(), extra, null);

        assertInstanceOf(OllamaChatOptions.class, options);
        assertEquals(8192, ((OllamaChatOptions) options).getNumCtx());
        assertEquals(40, ((OllamaChatOptions) options).getTopK());
    }

    @Test
    void unknownProviderFallsBackToGenericOptions() {
        ToolCallingChatOptions options =
                this.factory.build(mock(ChatModel.class), baseOptions(), ChatExtraOptions.defaults(), null);

        assertEquals("gpt-5-mini", options.getModel());
        assertEquals(0.7, options.getTemperature().doubleValue());
    }

    @Test
    void ollamaJsonOverrideBindsAcronymCasedKeys() {
        ChatExtraOptions extra = new ChatExtraOptions(null, null, "{\"num_gpu\": 2, \"use_mmap\": false}", null);

        OllamaChatOptions ollama =
                (OllamaChatOptions) this.factory.build(mock(OllamaChatModel.class), baseOptions(), extra, null);

        assertEquals(Integer.valueOf(2), ollama.getNumGPU());
        assertEquals(Boolean.FALSE, ollama.getUseMMap());
    }

    @Test
    void caseVariantKeyCannotReachProtectedSetter() {
        ChatExtraOptions extra =
                new ChatExtraOptions(null, null, "{\"APIKEY\": \"evil\", \"temperature\": 0.3}", null);

        OpenAiChatOptions openAi =
                (OpenAiChatOptions) this.factory.build(mock(OpenAiChatModel.class), baseOptions(), extra, null);

        assertNull(openAi.getApiKey());
        assertEquals(0.3, openAi.getTemperature().doubleValue());
    }
}
