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
package jm.kr.spring.ai.playground.service.chat;

import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever.FILTER_EXPRESSION;

@SpringBootTest
class ChatServiceTest {

    @Autowired
    ChatService chatService;

    @Autowired
    VectorStoreDocumentService vectorStoreDocumentService;

    @MockitoBean
    ChatModel chatModel;

    @Test
    void testStream() {
        long timestamp = System.currentTimeMillis();
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                new DefaultChatOptions(), List::of);
        String prompt = "Hello World";

        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(prompt))))));

        assertEquals(prompt,
                chatService.stream(chatHistory, "Test Chat", null, null).toStream().collect(Collectors.joining()));
        assertEquals(prompt,
                chatService.stream(chatHistory, "Test Chat", FILTER_EXPRESSION + " in ['a', 'b']", null).toStream()
                        .collect(Collectors.joining()));
    }

    @Test
    void testCall() {
        long timestamp = System.currentTimeMillis();
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                new DefaultChatOptions(), List::of);
        String prompt = "Hello World";

        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(prompt)))));

        assertEquals(prompt, chatService.call(chatHistory, prompt, null));
    }

    @Test
    void testGetDefaultOptions() {
        ChatOptions actualOptions = chatService.getDefaultOptions();
        assertEquals("gpt-4", actualOptions.getModel());
        assertEquals(0.7, actualOptions.getTemperature());
        assertEquals(1000, actualOptions.getMaxTokens());
        assertEquals(0.9, actualOptions.getTopP());
        assertEquals(0.0, actualOptions.getFrequencyPenalty());
        assertEquals(0.6, actualOptions.getPresencePenalty());
    }

    @Test
    void testGetSystemPrompt() {
        String expectedSystemPrompt = """
                systemPromptText
                you are a helpful assistant""";
        assertEquals(expectedSystemPrompt, chatService.getSystemPrompt());
    }

    @Test
    void testGetModels() {
        assertEquals(
                "[o1-mini, o1, gpt-4o, o1-preview, gpt-4o-mini, gpt-3.5-turbo, gpt-3.5-turbo-16k, gpt-4, gpt-4-32k, gpt-4-turbo]",
                chatService.getModels().toString());
    }

    @Test
    void testGetChatModelProvider() {
        ChatModel chatModel = new MockLlmProviderChatModel();
        ChatClient chatClient = mock(ChatClient.class);
        SpringAiPlaygroundOptions playgroundOptions =
                new SpringAiPlaygroundOptions(new SpringAiPlaygroundOptions.Chat("systemPrompt", List.of(
                        "MockLlmProvider"), (DefaultChatOptions) chatService.getDefaultOptions()));
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatService service = new ChatService(chatModel, chatClient, playgroundOptions, vectorStoreDocumentService);
        assertEquals("MockLlmProvider", service.getChatModelProvider());
    }

    @Test
    void buildFilterExpression() {
        assertEquals("docInfoId in ['test.pdf', 'hello.docx']",
                this.chatService.buildFilterExpression(List.of("test.pdf", "hello.docx")));
    }

    private static class MockLlmProviderChatModel implements ChatModel {
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return null;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return null;
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }

    }


}
