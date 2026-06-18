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


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
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
    private final ChatModel chatModel;

    private final Map<String, ChatHistory> conversationIdHistoryMap;

    public ChatHistoryService(ChatMemory chatMemory, ChatHistoryPersistenceService chatHistoryPersistenceService,
            ChatModel chatModel) {
        this.chatMemory = chatMemory;
        this.chatHistoryPersistenceService = chatHistoryPersistenceService;
        this.chatModel = chatModel;
        this.conversationIdHistoryMap = new ConcurrentHashMap<>();
    }

    private String currentProvider() {
        return this.chatModel.getClass().getSimpleName().replace("ChatModel", "");
    }

    public ChatHistory updateChatHistory(ChatHistory chatHistory) {
        String conversationId = chatHistory.conversationId();
        ChatHistory updatedChatHistory = changeChatHistory(chatHistory);
        this.conversationIdHistoryMap.put(conversationId, updatedChatHistory);
        this.chatHistoryPersistenceService.saveAsync(updatedChatHistory);
        return updatedChatHistory;
    }

    private ChatHistory changeChatHistory(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory.title()) || chatHistory.title().isBlank())
            return chatHistory.mutate(extractTitle(chatHistory.messagesSupplier().get()), System.currentTimeMillis());
        ChatHistory registered = this.conversationIdHistoryMap.get(chatHistory.conversationId());
        return (Objects.isNull(registered) ? chatHistory : registered)
                .mutate(chatHistory.title(), System.currentTimeMillis());
    }

    // Stopping a stream can commit the conversation before the user turn reaches chat memory; falling back
    // keeps the conversation registered and saved instead of throwing it away inside the cancel path.
    private String extractTitle(List<Message> messageList) {
        return messageList.stream().filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getText).map(userPrompt -> buildTitle(userPrompt.trim(), 20)).orElse("New chat");
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

    public void deleteChatHistory(ChatHistory chatHistory) {
        this.chatMemory.clear(chatHistory.conversationId());
        this.conversationIdHistoryMap.remove(chatHistory.conversationId());
        this.chatHistoryPersistenceService.deleteAsync(chatHistory);
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions chatOptions) {
        return createChatHistory(systemPrompt, chatOptions, ChatExtraOptions.defaults());
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions chatOptions, ChatExtraOptions extraOptions) {
        return createChatHistory(systemPrompt, chatOptions, extraOptions, ChatToolPreferences.defaults());
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions chatOptions, ChatExtraOptions extraOptions,
            ChatToolPreferences toolPreferences) {
        String conversationId = "Chat-" + UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        DefaultChatOptions defaultChatOptions =
                (DefaultChatOptions) ChatOptions.builder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                        .maxTokens(chatOptions.getMaxTokens())
                        .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                        .temperature(chatOptions.getTemperature())
                        .topK(chatOptions.getTopK()).topP(chatOptions.getTopP()).build();
        return new ChatHistory(conversationId, null, timestamp, timestamp, systemPrompt, defaultChatOptions,
                extraOptions == null ? ChatExtraOptions.defaults() : extraOptions, currentProvider(),
                toolPreferences == null ? ChatToolPreferences.defaults() : toolPreferences,
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
