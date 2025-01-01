package jm.kr.spring.ai.playground.service.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

public record ChatHistory(String chatId, String title, long createTimestamp, long updateTimestamp,
                          List<Message> messages, String systemPrompt, ChatOptions chatOptions) {}