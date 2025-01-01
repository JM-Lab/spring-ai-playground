package jm.kr.spring.ai.playground.service.chat;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {

    private static final String TIMESTAMP = "timestamp";


    private final ChatMemory chatMemory;

    private final Map<String, ChatHistory> chatHistories;

    public ChatHistoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatHistories = new ConcurrentHashMap<>();
    }

    public ChatHistory updateChatHistory(ChatHistory chatHistory, Long timestamp) {
        return Optional.ofNullable(getMessageList(chatHistory.chatId())).filter(messages -> !messages.isEmpty())
                .map(messages -> updateChatHistory(chatHistory, timestamp, messages)).orElseThrow();
    }

    private ChatHistory updateChatHistory(ChatHistory chatHistory, long timestamp, List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> metadata = messages.get(i).getMetadata();
            if (metadata.containsKey(TIMESTAMP))
                break;
            metadata.put(TIMESTAMP, timestamp);
        }
        String chatId = chatHistory.chatId();
        chatHistory = this.chatHistories.containsKey(chatId) ? new ChatHistory(chatId, chatHistory.title(),
                chatHistory.createTimestamp(), timestamp, messages, chatHistory.systemPrompt(),
                chatHistory.chatOptions()) : new ChatHistory(chatId, extractTitle(messages), timestamp,
                timestamp, messages, chatHistory.systemPrompt(), chatHistory.chatOptions());
        this.chatHistories.put(chatId, chatHistory);
        return chatHistory;
    }

    public void updateChatHistory(String chatId, String title, long updateTimestamp) {
        ChatHistory chatHistory = this.chatHistories.get(chatId);
        this.chatHistories.put(chatId, new ChatHistory(chatId, title, chatHistory.createTimestamp(), updateTimestamp,
                chatHistory.messages(), chatHistory.systemPrompt(), chatHistory.chatOptions()));

    }

    private String extractTitle(List<Message> messageList) {
        return messageList.stream().filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getText).map(userPrompt -> buildTitle(userPrompt, 20)).orElseThrow(() ->
                        new IllegalArgumentException("No USER Message type: " + messageList));
    }

    private String buildTitle(String userPrompt, int length) {
        return userPrompt.length() > length ? userPrompt.substring(0, length) + "..." : userPrompt;
    }

    public List<ChatHistory> getChatHistories() {
        return this.chatHistories.values().stream()
                .sorted(Comparator.comparingLong(ChatHistory::updateTimestamp).reversed()).toList();
    }

    public List<Message> getMessageList(String chatId) {
        return this.chatMemory.get(chatId, Integer.MAX_VALUE);
    }

    public void deleteChatHistory(String chatId) {
        this.chatHistories.remove(chatId);
    }

    public int getChatHistoriesTotal() {
        return this.chatHistories.size();
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions defaultOptions) {
        long createTimestamp = System.currentTimeMillis();
        return new ChatHistory(UUID.randomUUID().toString(), null, createTimestamp, createTimestamp,
                null, systemPrompt, defaultOptions);
    }

    public ChatHistory getChatHistory(String chatId) {
        return this.chatHistories.get(chatId);
    }
}
