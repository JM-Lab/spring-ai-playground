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

import com.fasterxml.jackson.core.type.TypeReference;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ChatHistoryPersistenceService implements PersistenceServiceInterface<ChatHistory> {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryPersistenceService.class);
    public static final String CONVERSATION_ID = "conversationId";
    private static final String MESSAGE_LIST = "messageList";
    private static final TypeReference<List<AssistantMessage.ToolCall>> TOOL_CALL_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ToolResponseMessage.ToolResponse>> TOOL_RESPONSE_LIST_TYPE =
            new TypeReference<>() {};

    private final Path saveDir;
    private final ObjectProvider<ChatHistoryService> chatHistoryServiceProvider;
    private final PersistenceExecutor persistenceExecutor;

    public ChatHistoryPersistenceService(Path springAiPlaygroundHomeDir,
            ObjectProvider<ChatHistoryService> chatHistoryServiceProvider,
            PersistenceExecutor persistenceExecutor) throws IOException {
        this.chatHistoryServiceProvider = chatHistoryServiceProvider;
        this.persistenceExecutor = persistenceExecutor;
        this.saveDir = springAiPlaygroundHomeDir.resolve("chat").resolve("save");
        Files.createDirectories(this.saveDir);
    }

    public void saveAsync(ChatHistory chatHistory) {
        this.persistenceExecutor.submit(() -> {
            try {
                save(chatHistory);
            } catch (IOException e) {
                logger.error("Async save failed for chat history {}", chatHistory.conversationId(), e);
            }
        });
    }

    public void deleteAsync(ChatHistory chatHistory) {
        this.persistenceExecutor.submit(() -> delete(chatHistory));
    }

    @Override
    public Path getSaveDir() {
        return this.saveDir;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void buildSaveData(ChatHistory chatHistory, Map<String, Object> saveObjectMap) {
        saveObjectMap.put(MESSAGE_LIST, chatHistory.messagesSupplier().get());
    }

    @Override
    public String buildSaveFileName(ChatHistory chatHistory) {
        return chatHistory.conversationId();
    }

    @Override
    public ChatHistory convertTo(Map<String, Object> saveObjectMap) {
        String conversationId = saveObjectMap.get(CONVERSATION_ID).toString();
        String title = saveObjectMap.get("title").toString();
        long createTimestamp = ((Number) saveObjectMap.get("createTimestamp")).longValue();
        long updateTimestamp = ((Number) saveObjectMap.get("updateTimestamp")).longValue();
        String systemPrompt = saveObjectMap.computeIfAbsent("systemPrompt", s -> "").toString();
        DefaultChatOptions chatOptions =
                OBJECT_MAPPER.convertValue(saveObjectMap.get("chatOptions"), DefaultChatOptions.class);
        List<Map<String, Object>> messageMapList = (List<Map<String, Object>>) saveObjectMap.get(MESSAGE_LIST);
        return new ChatHistory(conversationId, title, createTimestamp, updateTimestamp, systemPrompt, chatOptions,
                () -> messageMapList.stream().map(this::convertToMessage).toList());
    }

    private Message convertToMessage(Map<String, Object> saveObjectMap) {
        MessageType messageType = MessageType.valueOf(saveObjectMap.get("messageType").toString().toUpperCase());
        String content = Objects.toString(saveObjectMap.get("text"), "");
        Map<String, Object> metadata =
                (Map<String, Object>) saveObjectMap.computeIfAbsent("metadata", key -> Map.of());
        return switch (messageType) {
            case USER -> UserMessage.builder().text(content).metadata(metadata).build();
            case ASSISTANT -> AssistantMessage.builder().content(content).properties(metadata)
                    .toolCalls(restoreToolCalls(saveObjectMap)).build();
            case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
            case TOOL -> ToolResponseMessage.builder().responses(restoreToolResponses(saveObjectMap))
                    .metadata(metadata).build();
        };
    }

    private List<AssistantMessage.ToolCall> restoreToolCalls(Map<String, Object> saveObjectMap) {
        return OBJECT_MAPPER.convertValue(saveObjectMap.getOrDefault("toolCalls", List.of()), TOOL_CALL_LIST_TYPE);
    }

    private List<ToolResponseMessage.ToolResponse> restoreToolResponses(Map<String, Object> saveObjectMap) {
        return OBJECT_MAPPER.convertValue(saveObjectMap.getOrDefault("responses", List.of()), TOOL_RESPONSE_LIST_TYPE);
    }

    @Override
    public void onStart() throws IOException {
        ChatHistoryService chatHistoryService = this.chatHistoryServiceProvider.getObject();
        this.loads().forEach(chatHistoryService::putIfAbsentChatHistory);
    }
}
