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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.DefaultChatOptions;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record ChatHistory(String conversationId, String title, long createTimestamp, long updateTimestamp, String systemPrompt,
                          DefaultChatOptions chatOptions, ChatExtraOptions extraOptions, String provider,
                          ChatToolPreferences toolPreferences,
                          @JsonIgnore Supplier<List<Message>> messagesSupplier) {

    public static final String TIMESTAMP = "timestamp";

    public ChatHistory {
        toolPreferences = toolPreferences == null ? ChatToolPreferences.defaults() : toolPreferences;
    }

    public ChatHistory(String conversationId, String title, long createTimestamp, long updateTimestamp,
            String systemPrompt, DefaultChatOptions chatOptions, Supplier<List<Message>> messagesSupplier) {
        this(conversationId, title, createTimestamp, updateTimestamp, systemPrompt, chatOptions,
                ChatExtraOptions.defaults(), null, ChatToolPreferences.defaults(), messagesSupplier);
    }

    public ChatHistory mutate(String title, long updateTimestamp) {
        updateLastMessageTimestamp(updateTimestamp);
        return new ChatHistory(this.conversationId, title, this.createTimestamp, updateTimestamp, this.systemPrompt,
                this.chatOptions, this.extraOptions, this.provider, this.toolPreferences, this.messagesSupplier);
    }

    public ChatHistory withToolPreferences(ChatToolPreferences toolPreferences) {
        return new ChatHistory(this.conversationId, this.title, this.createTimestamp, this.updateTimestamp,
                this.systemPrompt, this.chatOptions, this.extraOptions, this.provider, toolPreferences,
                this.messagesSupplier);
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
        return new ChatHistory(this.conversationId, title, this.createTimestamp, updateTimestamp, this.systemPrompt,
                this.chatOptions, this.extraOptions, this.provider, this.toolPreferences, messagesSupplier);
    }
}
