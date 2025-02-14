package jm.kr.spring.ai.playground.service.chat;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;

import java.beans.PropertyChangeSupport;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {

    public static final String CHAT_HISTORY_CHANGE_EVENT = "CHAT_HISTORY_CHANGE_EVENT";
    public static final String CHAT_HISTORY_SELECT_EVENT = "CHAT_HISTORY_SELECT_EVENT";
    public static final String EMPTY_CHAT_HISTORY_EVENT = "EMPTY_CHAT_HISTORY_EVENT";

    private final ChatMemory chatMemory;

    private final Map<String, ChatHistory> chatIdHistoryMap;

    private final PropertyChangeSupport chatHistoryChangeSupport;

    public ChatHistoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatIdHistoryMap = new ConcurrentHashMap<>();
        this.chatHistoryChangeSupport = new PropertyChangeSupport(this);
    }

    public PropertyChangeSupport getChatHistoryChangeSupport() {
        return this.chatHistoryChangeSupport;
    }

    public void updateChatHistory(ChatHistory chatHistory) {
        String chatId = chatHistory.getChatId();
        this.chatIdHistoryMap.put(chatId, chatHistory.setUpdateTimestamp(System.currentTimeMillis()));
        this.chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_CHANGE_EVENT, null, chatHistory);
    }

    public List<ChatHistory> getChatHistoryList() {
        return this.chatIdHistoryMap.values().stream()
                .sorted(Comparator.comparingLong(ChatHistory::getUpdateTimestamp).reversed()).toList();
    }

    private List<Message> getMessageList(String chatId) {
        return Optional.ofNullable(this.chatMemory.get(chatId, Integer.MAX_VALUE)).orElseGet(List::of);
    }

    public void deleteChatHistory(String chatId) {
        this.chatMemory.clear(chatId);
        this.chatIdHistoryMap.remove(chatId);
    }

    public ChatHistory createChatHistory(String systemPrompt, ChatOptions defaultOptions) {
        long createTimestamp = System.currentTimeMillis();
        String chatHistoryId = "ChatHistory-" + UUID.randomUUID();
        return new ChatHistory(chatHistoryId, createTimestamp, createTimestamp, systemPrompt,
                defaultOptions, () -> getMessageList(chatHistoryId));
    }

}
