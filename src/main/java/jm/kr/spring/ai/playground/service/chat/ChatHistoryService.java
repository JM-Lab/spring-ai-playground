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

    private final Map<String, ChatHistory> chatIdHistoryMap;

    public ChatHistoryService(ChatMemory chatMemory, ChatHistoryPersistenceService chatHistoryPersistenceService) {
        this.chatMemory = chatMemory;
        this.chatHistoryPersistenceService = chatHistoryPersistenceService;
        this.chatIdHistoryMap = new ConcurrentHashMap<>();
    }

    public ChatHistory updateChatHistory(ChatHistory chatHistory) {
        String chatId = chatHistory.chatId();
        ChatHistory updatedChatHistory = changeChatHistory(chatHistory);
        this.chatIdHistoryMap.put(chatId, updatedChatHistory);
        return updatedChatHistory;
    }

    private ChatHistory changeChatHistory(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory.title()) || chatHistory.title().isBlank())
            return chatHistory.mutate(extractTitle(chatHistory.messagesSupplier().get()), System.currentTimeMillis());
        return this.chatIdHistoryMap.get(chatHistory.chatId()).mutate(chatHistory.title(), System.currentTimeMillis());
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
        return this.chatIdHistoryMap.values().stream()
                .sorted(Comparator.comparingLong(ChatHistory::updateTimestamp).reversed()).toList();
    }

    private List<Message> getMessageList(String chatId) {
        return Optional.ofNullable(this.chatMemory.get(chatId, Integer.MAX_VALUE)).orElseGet(ArrayList::new);
    }

    public void deleteChatHistory(String chatId) {
        this.chatMemory.clear(chatId);
        this.chatIdHistoryMap.remove(chatId);
        this.chatHistoryPersistenceService.delete(chatId);
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions chatOptions) {
        String chatId = "Chat-" + UUID.randomUUID();
        long timestamp = System.currentTimeMillis();
        new DefaultChatOptionsBuilder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                .maxTokens(chatOptions.getMaxTokens())
                .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                .temperature(chatOptions.getTemperature())
                .topK(chatOptions.getTopK()).topP(chatOptions.getTopP());
        return new ChatHistory(chatId, null, timestamp, timestamp, systemPrompt,
                (DefaultChatOptions) new DefaultChatOptionsBuilder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                        .maxTokens(chatOptions.getMaxTokens())
                        .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                        .temperature(chatOptions.getTemperature())
                        .topK(chatOptions.getTopK()).topP(chatOptions.getTopP()).build(),
                () -> getMessageList(chatId));
    }

    public void putIfAbsentChatHistory(ChatHistory chatHistory) {
        this.chatIdHistoryMap.computeIfAbsent(chatHistory.chatId(), chatId -> {
            this.chatMemory.add(chatId, chatHistory.messagesSupplier().get());
            return chatHistory.mutate(() -> getMessageList(chatId));
        });
    }

    public ChatHistory getChatHistory(String chatId) {
        return this.chatIdHistoryMap.get(chatId);
    }
}
