package jm.kr.spring.ai.playground.service.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ChatHistory {
    public static final String TIMESTAMP = "timestamp";
    private final String chatId;
    private String title;
    private final long createTimestamp;
    private long updateTimestamp;
    private final String systemPrompt;
    private final ChatOptions chatOptions;
    private final Supplier<List<Message>> messagesSupplier;

    public ChatHistory(String chatId, long createTimestamp, long updateTimestamp, String systemPrompt,
            ChatOptions chatOptions, Supplier<List<Message>> messagesSupplier) {
        this.chatId = chatId;
        this.createTimestamp = createTimestamp;
        this.updateTimestamp = updateTimestamp;
        this.systemPrompt = systemPrompt;
        this.chatOptions = chatOptions;
        this.messagesSupplier = messagesSupplier;
    }

    public String getChatId() {
        return chatId;
    }

    public String getTitle() {
        return title;
    }

    public long getCreateTimestamp() {
        return createTimestamp;
    }

    public long getUpdateTimestamp() {
        return updateTimestamp;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public ChatOptions getChatOptions() {
        return chatOptions;
    }

    public Supplier<List<Message>> getMessagesSupplier() {
        return messagesSupplier;
    }

    public ChatHistory setTitle(String title) {
        this.title = title;
        return this;
    }

    public ChatHistory setUpdateTimestamp(long updateTimestamp) {
        this.updateTimestamp = updateTimestamp;
        List<Message> messages = messagesSupplier.get();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> metadata = messages.get(i).getMetadata();
            if (metadata.containsKey(TIMESTAMP))
                break;
            metadata.put(TIMESTAMP, updateTimestamp);
        }
        return this;
    }

}