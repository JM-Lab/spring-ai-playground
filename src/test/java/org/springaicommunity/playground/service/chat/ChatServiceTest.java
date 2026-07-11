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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.SpringAiPlaygroundRagAdvisor;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.ai.playground.chat.models=gpt-5-nano, gpt-5-mini"
})
class ChatServiceTest {
    @Autowired
    ChatService chatService;

    @Autowired
    VectorStoreDocumentService vectorStoreDocumentService;

    @MockitoBean
    ChatModel chatModel;

    @BeforeEach
    void stubModelOptions() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void testStream() {
        long timestamp = System.currentTimeMillis();
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                (DefaultChatOptions) ChatOptions.builder().build(), () -> List.of(new UserMessage("Test Chat")));
        String prompt = "Hello World";

        when(chatModel.stream(any(Prompt.class))).thenReturn(
                Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage(prompt))))));

        assertEquals(prompt, chatService.stream(chatHistory, "Test Chat", null, null, null, null, null,
                        null).toStream()
                .collect(Collectors.joining()));
        List<Object> ragProcessMessages = new ArrayList<>();
        String filterExpression = VectorStoreDocumentRetriever.FILTER_EXPRESSION + " in ['a', 'b']";
        assertEquals(prompt,
                chatService.stream(chatHistory, "Test Chat", filterExpression, null, null, null,
                        ragProcessMessages::add, null).toStream().collect(Collectors.joining()));
        assertFalse(ragProcessMessages.isEmpty());
        String firstRagMessage = ragProcessMessages.get(0).toString();
        assertTrue(firstRagMessage.startsWith("Searching VectorDB documents..."));
        assertTrue(firstRagMessage.contains("- Query: `Test Chat`"));
        assertTrue(firstRagMessage.contains("- Filter: `" + filterExpression + "`"));
        assertTrue(firstRagMessage.contains("- Top K:"));
        assertTrue(firstRagMessage.contains("- Similarity Threshold:"));
        assertEquals(SpringAiPlaygroundRagAdvisor.RAG_SEARCH_COMPLETED_MESSAGE,
                ragProcessMessages.get(ragProcessMessages.size() - 1));
    }

    @Test
    void testStreamEmitsRoundUsage() {
        long timestamp = System.currentTimeMillis();
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                (DefaultChatOptions) ChatOptions.builder().build(), () -> List.of(new UserMessage("Test Chat")));
        String prompt = "Hello World";
        ChatResponse roundEnd = new ChatResponse(List.of(new Generation(new AssistantMessage(prompt))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(11, 320)).build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(roundEnd));

        List<ChatService.RoundUsage> rounds = new ArrayList<>();
        chatService.stream(chatHistory, "Test Chat", null, null, null, null, null, null, rounds::add, null, null,
                null, null, null, List.of()).toStream().collect(Collectors.joining());

        assertEquals(1, rounds.size());
        ChatService.RoundUsage round = rounds.get(0);
        assertEquals(11, round.promptTokens());
        assertEquals(320, round.completionTokens());
        assertEquals(331, round.totalTokens());
        assertFalse(round.toolCallRound());
        assertFalse(round.thinkRound());
    }

    @Test
    void testApplyImageRefsToLastUserMessage() {
        long timestamp = System.currentTimeMillis();
        UserMessage userMessage = new UserMessage("look at this");
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(userMessage, new AssistantMessage("ok")));
        Media media = Media.builder().id("abc123").name("photo.jpg")
                .mimeType(MimeType.valueOf("image/jpeg")).data(new byte[] {1}).build();

        ChatService.applyImageRefsToLastUserMessage(chatHistory, List.of(media));

        Object refs = userMessage.getMetadata().get(ChatService.USER_IMAGES);
        assertTrue(refs instanceof List<?>);
        Map<?, ?> ref = (Map<?, ?>) ((List<?>) refs).get(0);
        assertEquals("abc123", ref.get(ChatService.USER_IMAGE_HASH));
        assertEquals("photo.jpg", ref.get(ChatService.USER_IMAGE_FILE_NAME));
        assertEquals("image/jpeg", ref.get(ChatService.USER_IMAGE_MIME_TYPE));
    }

    @Test
    void testApplyImageRefsSkipsWhenNoMedia() {
        long timestamp = System.currentTimeMillis();
        UserMessage userMessage = new UserMessage("plain text");
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                (DefaultChatOptions) ChatOptions.builder().build(), () -> List.of(userMessage));

        ChatService.applyImageRefsToLastUserMessage(chatHistory, List.of());
        ChatService.applyImageRefsToLastUserMessage(chatHistory, null);

        assertFalse(userMessage.getMetadata().containsKey(ChatService.USER_IMAGES));
    }

    @Test
    void testCall() {
        long timestamp = System.currentTimeMillis();
        String prompt = "Hello World";
        ChatHistory chatHistory = new ChatHistory("test-chat", "Test Chat", timestamp, timestamp, "System prompt",
                (DefaultChatOptions) ChatOptions.builder().build(), () -> List.of(new UserMessage(prompt)));

        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(prompt)))));

        assertEquals(prompt, chatService.call(chatHistory, prompt, null, null, null));
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
        assertEquals("[gpt-5-nano, gpt-5-mini]", chatService.getModels().toString());
    }

    @Test
    void testGetChatModelProvider() {
        ChatModel chatModel = new MockLlmProviderChatModel();
        ChatClient chatClient = mock(ChatClient.class);
        SpringAiPlaygroundOptions playgroundOptions =
                new SpringAiPlaygroundOptions(null, true, "", new SpringAiPlaygroundOptions.Chat("systemPrompt",
                        List.of("MockLlmProvider"), null, null, null,
                        null, null, null, null, null), null, null);
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatService service = new ChatService(chatModel, chatClient, chatMemory, playgroundOptions,
                vectorStoreDocumentService, null, new ChatRequestOptionsFactory(new ObjectMapper(), null), null,
                null);
        assertEquals("MockLlmProvider", service.getChatModelProvider());
    }

    @Test
    void buildFilterExpression() {
        assertEquals("docInfoId in ['test.pdf', 'hello.docx']",
                this.chatService.buildFilterExpression(List.of("test.pdf", "hello.docx")));
    }

    private static final class MockLlmProviderChatModel implements ChatModel {
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
