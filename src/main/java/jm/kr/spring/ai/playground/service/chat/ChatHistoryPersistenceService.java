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

import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class ChatHistoryPersistenceService implements PersistenceServiceInterface<ChatHistory> {
    public static final String CONVERSATION_ID = "conversationId";

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryPersistenceService.class);

    private final Path saveDir;

    public ChatHistoryPersistenceService(Path springAiPlaygroundHomeDir) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("chat").resolve("save");
        Files.createDirectories(this.saveDir);
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
    public String buildSaveDataAndReturnName(ChatHistory chatHistory, Map<String, Object> saveObjectMap) {
        saveObjectMap.put("messageList", chatHistory.messagesSupplier().get());
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
        List<Map<String, Object>> messageMapList = (List<Map<String, Object>>) saveObjectMap.get("messageList");
        return new ChatHistory(conversationId, title, createTimestamp, updateTimestamp, systemPrompt, chatOptions,
                () -> messageMapList.stream().map(this::convertToMessage).toList());
    }

    private Message convertToMessage(Map<String, Object> saveObjectMap) {
        MessageType messageType = MessageType.valueOf(saveObjectMap.get("messageType").toString().toUpperCase());
        String content = saveObjectMap.get("text").toString();
        Map<String, Object> metadata =
                (Map<String, Object>) saveObjectMap.computeIfAbsent("metadata", key -> Map.of());
        return switch (messageType) {
            case USER -> UserMessage.builder().text(content).metadata(metadata).build();
            case ASSISTANT -> new AssistantMessage(content, metadata);
            case SYSTEM -> SystemMessage.builder().text(content).metadata(metadata).build();
            case TOOL -> new ToolResponseMessage(List.of()); // todo check TOOL response
        };
    }

    public void delete(String conversationId) {
        getSaveDir().resolve(conversationId).toFile().deleteOnExit();
    }

}
