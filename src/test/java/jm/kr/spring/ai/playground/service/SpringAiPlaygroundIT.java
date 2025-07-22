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
package jm.kr.spring.ai.playground.service;

import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.service.chat.ChatService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.chat.ChatService.CHAT_META;
import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.ALL_SEARCH_REQUEST_OPTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("ollama")
class SpringAiPlaygroundIT {

    @Autowired
    private ChatService chatService;

    @Autowired
    private VectorStoreDocumentService vectorStoreDocumentService;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private VectorStoreService vectorStoreService;

    @BeforeEach
    void setUp() throws IOException {
        Collection<Document> documents = vectorStoreService.search(
                new SearchRequest.Builder().similarityThreshold(ALL_SEARCH_REQUEST_OPTION.similarityThreshold())
                        .topK(ALL_SEARCH_REQUEST_OPTION.topK()).build());
        if (documents.size() > 0)
            return;

        Resource resource = new ClassPathResource("wikipedia-hurricane-milton-page.pdf");
        Path uploadDir = vectorStoreDocumentService.getUploadDir();
        Files.createDirectories(uploadDir);

        String filename = resource.getFilename();
        Path targetPath = uploadDir.resolve(filename);

        try (var in = resource.getInputStream()) {
            Files.copy(in, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        targetPath.toFile().deleteOnExit();
        List<String> uploadedFileNames = List.of(filename);
        Map<String, List<Document>> uploadedDocumentItems =
                this.vectorStoreDocumentService.extractDocumentItems(uploadedFileNames);
        uploadedDocumentItems.entrySet().stream()
                .map(entry -> this.vectorStoreDocumentService.putNewDocument(entry.getKey(), entry.getValue()))
                .forEach(this.vectorStoreService::add);
        documents = vectorStoreService.search(
                new SearchRequest.Builder().similarityThreshold(ALL_SEARCH_REQUEST_OPTION.similarityThreshold())
                        .topK(ALL_SEARCH_REQUEST_OPTION.topK()).build());
        assertEquals(63, documents.size());

    }

    @Test
    void testStream() {
        ChatHistory chatHistory = chatHistoryService.createChatHistory(null, new DefaultChatOptions());
        String prompt = "Hello World";
        String assistantText =
                chatService.stream(chatHistory, prompt, chatService.buildFilterExpression(List.of("*")), null)
                        .toStream().collect(Collectors.joining());

        assertTrue(chatHistory.conversationId().startsWith("Chat-"));
        assertNull(chatHistory.title());
        assertTrue(0 < chatHistory.createTimestamp());
        assertTrue(0 < chatHistory.updateTimestamp());
        assertNull(chatHistory.systemPrompt());
        List<Message> messages = chatHistory.messagesSupplier().get();
        assertEquals(2, messages.size());
        Message messagesFirst = messages.getFirst();
        assertEquals(MessageType.USER, messagesFirst.getMessageType());
        ChatService.ChatMeta chatMeta = (ChatService.ChatMeta) messagesFirst.getMetadata().get(CHAT_META);
        assertEquals("mistral", chatMeta.model());
        assertEquals(0, chatMeta.retrievedDocuments().size());
        Usage usage = chatMeta.usage();
        assertTrue(0 < usage.getCompletionTokens());
        assertTrue(0 < usage.getPromptTokens());
        assertTrue(0 < usage.getTotalTokens());

        Message last = messages.getLast();
        assertEquals(MessageType.ASSISTANT, last.getMessageType());
        assertEquals(assistantText, last.getText());
    }

    @Test
    void testCall() {
        ChatHistory chatHistory = chatHistoryService.createChatHistory(null, new DefaultChatOptions());
        String prompt = "Hello World";
        String assistantText = chatService.call(chatHistory, prompt, null);

        assertTrue(chatHistory.conversationId().startsWith("Chat-"));
        assertNull(chatHistory.title());
        assertTrue(0 < chatHistory.createTimestamp());
        assertTrue(0 < chatHistory.updateTimestamp());
        assertNull(chatHistory.systemPrompt());
        List<Message> messages = chatHistory.messagesSupplier().get();
        assertEquals(2, messages.size());
        Message messagesFirst = messages.getFirst();
        assertEquals(MessageType.USER, messagesFirst.getMessageType());
        ChatService.ChatMeta chatMeta = (ChatService.ChatMeta) messagesFirst.getMetadata().get(CHAT_META);
        assertEquals("mistral", chatMeta.model());
        assertNull(chatMeta.retrievedDocuments());
        Usage usage = chatMeta.usage();
        assertTrue(0 < usage.getCompletionTokens());
        assertTrue(0 < usage.getPromptTokens());
        assertTrue(0 < usage.getTotalTokens());

        Message last = messages.getLast();
        assertEquals(MessageType.ASSISTANT, last.getMessageType());
        assertEquals(assistantText, last.getText());
    }

}
