package jm.kr.spring.ai.playground.service.chat;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.beans.PropertyChangeSupport;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {

    public static final String CURRENT_CHAT_HISTORY_EVENT = "CURRENT_CHAT_HISTORY_EVENT";

    private static final String TIMESTAMP = "timestamp";

    private final ChatMemory chatMemory;

    private final Map<String, ChatHistory> chatHistories;

    private final PropertyChangeSupport chatHistoryChangeSupport;

    public ChatHistoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatHistories = new ConcurrentHashMap<>();
        this.chatHistoryChangeSupport = new PropertyChangeSupport(this);
    }

    public PropertyChangeSupport getChatHistoryChangeSupport() {
        return this.chatHistoryChangeSupport;
    }

    public void updateChatHistory(ChatHistory newChatHistory) {
        Optional.ofNullable(getMessageList(newChatHistory.chatId())).filter(messages -> !messages.isEmpty())
                .ifPresent(messages -> updateChatHistory(newChatHistory, messages));
    }

    private void updateChatHistory(ChatHistory chatHistory, List<Message> messages) {
        String chatId = chatHistory.chatId();
        chatHistory = this.chatHistories.containsKey(chatId) ? this.chatHistories.get(chatId).title()
                .equals(chatHistory.title()) ? chatHistory.newUpdateTimestamp() : chatHistory :
                chatHistory.newTitle(extractTitle(messages));
        Long updateTimestamp = chatHistory.updateTimestamp();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> metadata = messages.get(i).getMetadata();

            if (metadata.containsKey(TIMESTAMP)) {break;}
            metadata.put(TIMESTAMP, updateTimestamp);
        }
        this.chatHistoryChangeSupport.firePropertyChange(CURRENT_CHAT_HISTORY_EVENT,
                this.chatHistories.put(chatId, chatHistory), chatHistory);
    }

    private String extractTitle(List<Message> messageList) {
        return messageList.stream().filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getText).map(userPrompt -> buildTitle(userPrompt, 30)).orElseThrow(() ->
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
        this.chatMemory.clear(chatId);
        this.chatHistories.remove(chatId);
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions defaultOptions) {
        long createTimestamp = System.currentTimeMillis();
        return new ChatHistory(UUID.randomUUID().toString(), null, createTimestamp, createTimestamp, systemPrompt,
                defaultOptions);
    }

}
