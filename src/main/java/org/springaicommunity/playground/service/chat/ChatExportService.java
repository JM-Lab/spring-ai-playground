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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Pure builders that turn a conversation (or a single message) into shareable local artifacts. Shared by the
// per-message Export menu and the whole-conversation Export action; no UI dependency so it stays unit-testable.
@Service
public class ChatExportService {

    private final ObjectMapper objectMapper;

    public ChatExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String conversationToMarkdown(ChatHistory history) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(conversationTitle(history)).append("\n\n");
        if (StringUtils.hasText(history.systemPrompt()))
            sb.append("**System:** ").append(history.systemPrompt().trim()).append("\n\n");
        for (Message message : exportableMessages(history))
            sb.append("**").append(roleLabel(message.getMessageType())).append(":** ")
                    .append(textOf(message)).append("\n\n");
        return sb.toString().stripTrailing() + "\n";
    }

    public String conversationToPlainText(ChatHistory history) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(history.systemPrompt()))
            sb.append("System: ").append(history.systemPrompt().trim()).append("\n\n");
        for (Message message : exportableMessages(history))
            sb.append(roleLabel(message.getMessageType())).append(": ").append(textOf(message)).append("\n\n");
        return sb.toString().stripTrailing() + "\n";
    }

    public String conversationToJson(ChatHistory history) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("conversationId", history.conversationId());
        root.put("title", history.title());
        root.put("model", history.chatOptions() == null ? null : history.chatOptions().getModel());
        root.put("provider", history.provider());
        root.put("systemPrompt", history.systemPrompt());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : exportableMessages(history))
            messages.add(messageMap(message));
        root.put("messages", messages);
        return writeJson(root);
    }

    public String messageToMarkdown(Message message) {
        return textOf(message);
    }

    public String messageToPlainText(Message message) {
        return textOf(message);
    }

    public String messageToJson(Message message) {
        return writeJson(messageMap(message));
    }

    private Map<String, Object> messageMap(Message message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("role", message.getMessageType().name().toLowerCase(Locale.ROOT));
        map.put("content", textOf(message));
        return map;
    }

    private String conversationTitle(ChatHistory history) {
        return StringUtils.hasText(history.title()) ? history.title() : history.conversationId();
    }

    private List<Message> exportableMessages(ChatHistory history) {
        List<Message> result = new ArrayList<>();
        for (Message message : history.messagesSupplier().get()) {
            MessageType type = message.getMessageType();
            if (type == MessageType.USER || type == MessageType.ASSISTANT)
                result.add(message);
        }
        return result;
    }

    private String textOf(Message message) {
        String text = message.getText();
        return text == null ? "" : text;
    }

    private String roleLabel(MessageType type) {
        return switch (type) {
            case USER -> "User";
            case ASSISTANT -> "Assistant";
            case SYSTEM -> "System";
            case TOOL -> "Tool";
        };
    }

    private String writeJson(Object value) {
        try {
            return this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chat export", e);
        }
    }
}
