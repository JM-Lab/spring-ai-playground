package jm.kr.spring.ai.playground.service.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.DefaultChatOptions;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record ChatHistory(String chatId, String title, long createTimestamp, long updateTimestamp, String systemPrompt,
                          DefaultChatOptions chatOptions, @JsonIgnore Supplier<List<Message>> messagesSupplier) {

    public static final String TIMESTAMP = "timestamp";

    public ChatHistory mutate(String title, long updateTimestamp) {
        updateLastMessageTimestamp(updateTimestamp);
        return new ChatHistory(this.chatId, title, this.createTimestamp, updateTimestamp, this.systemPrompt,
                this.chatOptions, this.messagesSupplier);
    }

    public void updateLastMessageTimestamp(long updateTimestamp) {
        List<Message> messages = messagesSupplier.get();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> metadata = messages.get(i).getMetadata();
            if (metadata.containsKey(TIMESTAMP))
                break;
            metadata.put(TIMESTAMP, updateTimestamp);
        }
    }

    public ChatHistory mutate(Supplier<List<Message>> messagesSupplier) {
        return new ChatHistory(this.chatId, title, this.createTimestamp, updateTimestamp, this.systemPrompt,
                this.chatOptions, messagesSupplier);
    }
}