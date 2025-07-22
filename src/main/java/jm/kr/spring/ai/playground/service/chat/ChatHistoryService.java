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


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {

    private final ChatMemory chatMemory;
    private final ChatHistoryPersistenceService chatHistoryPersistenceService;

    private final Map<String, ChatHistory> conversationIdHistoryMap;

    public ChatHistoryService(ChatMemory chatMemory, ChatHistoryPersistenceService chatHistoryPersistenceService) {
        this.chatMemory = chatMemory;
        this.chatHistoryPersistenceService = chatHistoryPersistenceService;
        this.conversationIdHistoryMap = new ConcurrentHashMap<>();
    }

    public ChatHistory updateChatHistory(ChatHistory chatHistory) {
        String conversationId = chatHistory.conversationId();
        ChatHistory updatedChatHistory = changeChatHistory(chatHistory);
        this.conversationIdHistoryMap.put(conversationId, updatedChatHistory);
        return updatedChatHistory;
    }

    private ChatHistory changeChatHistory(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory.title()) || chatHistory.title().isBlank())
            return chatHistory.mutate(extractTitle(chatHistory.messagesSupplier().get()), System.currentTimeMillis());
        return this.conversationIdHistoryMap.get(chatHistory.conversationId()).mutate(chatHistory.title(), System.currentTimeMillis());
    }

    private String extractTitle(List<Message> messageList) {
        return messageList.stream().filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getText).map(userPrompt -> buildTitle(userPrompt.trim(), 20)).orElseThrow(() ->
                        new IllegalArgumentException("No USER Message type: " + messageList));
    }

    private String buildTitle(String userPrompt, int length) {
        return userPrompt.length() > length ? userPrompt.substring(0, length) + "..." : userPrompt;
    }

    public List<ChatHistory> getChatHistoryList() {
        return this.conversationIdHistoryMap.values().stream()
                .sorted(Comparator.comparingLong(ChatHistory::updateTimestamp).reversed()).toList();
    }

    private List<Message> getMessages(String conversationId) {
        return Optional.ofNullable(this.chatMemory.get(conversationId)).orElseGet(ArrayList::new);
    }

    public void deleteChatHistory(String conversationId) {
        this.chatMemory.clear(conversationId);
        this.conversationIdHistoryMap.remove(conversationId);
        this.chatHistoryPersistenceService.delete(conversationId);
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions chatOptions) {
        String conversationId = "Chat-" + UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        new DefaultChatOptionsBuilder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                .maxTokens(chatOptions.getMaxTokens())
                .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                .temperature(chatOptions.getTemperature())
                .topK(chatOptions.getTopK()).topP(chatOptions.getTopP());
        return new ChatHistory(conversationId, null, timestamp, timestamp, systemPrompt,
                (DefaultChatOptions) new DefaultChatOptionsBuilder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                        .maxTokens(chatOptions.getMaxTokens())
                        .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                        .temperature(chatOptions.getTemperature())
                        .topK(chatOptions.getTopK()).topP(chatOptions.getTopP()).build(),
                () -> getMessages(conversationId));
    }

    public void putIfAbsentChatHistory(ChatHistory chatHistory) {
        this.conversationIdHistoryMap.computeIfAbsent(chatHistory.conversationId(), conversationId -> {
            this.chatMemory.add(conversationId, chatHistory.messagesSupplier().get());
            return chatHistory.mutate(() -> getMessages(conversationId));
        });
    }

    public ChatHistory getChatHistory(String conversationId) {
        return this.conversationIdHistoryMap.get(conversationId);
    }
}
