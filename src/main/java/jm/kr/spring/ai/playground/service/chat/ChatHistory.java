package jm.kr.spring.ai.playground.service.chat;

import org.springframework.ai.chat.prompt.ChatOptions;

public record ChatHistory(String chatId, String title, long createTimestamp, long updateTimestamp, String systemPrompt,
                          ChatOptions chatOptions) {

    public ChatHistory newTitle(String newTitle) {
        return new ChatHistory(chatId, newTitle, createTimestamp, System.currentTimeMillis(), systemPrompt,
                chatOptions);
    }

    public ChatHistory newUpdateTimestamp() {
        return new ChatHistory(chatId, title, createTimestamp, System.currentTimeMillis(), systemPrompt,
                chatOptions);
    }
}