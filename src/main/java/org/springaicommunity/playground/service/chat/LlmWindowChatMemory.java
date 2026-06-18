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

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LlmWindowChatMemory implements ChatMemory {

    private final ChatMemory delegate;
    private final int defaultWindow;
    private final ObjectProvider<ChatHistoryService> chatHistoryServiceProvider;

    public LlmWindowChatMemory(ChatMemory delegate, int defaultWindow,
            ObjectProvider<ChatHistoryService> chatHistoryServiceProvider) {
        this.delegate = delegate;
        this.defaultWindow = Math.max(1, defaultWindow);
        this.chatHistoryServiceProvider = chatHistoryServiceProvider;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        this.delegate.add(conversationId, messages);
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> all = this.delegate.get(conversationId);
        if (all == null || all.isEmpty()) return List.of();
        return window(all, resolveWindow(conversationId));
    }

    @Override
    public void clear(String conversationId) {
        this.delegate.clear(conversationId);
    }

    private int resolveWindow(String conversationId) {
        ChatHistoryService service = this.chatHistoryServiceProvider.getIfAvailable();
        if (Objects.isNull(service)) return this.defaultWindow;
        ChatHistory history = service.getChatHistory(conversationId);
        if (Objects.isNull(history) || Objects.isNull(history.extraOptions())) return this.defaultWindow;
        Integer window = history.extraOptions().memoryWindow();
        return Objects.isNull(window) || window <= 0 ? this.defaultWindow : window;
    }

    private static List<Message> window(List<Message> all, int maxMessages) {
        if (all.size() <= maxMessages) return all;
        List<Message> systemMessages = new ArrayList<>();
        List<Message> others = new ArrayList<>();
        for (Message message : all) {
            if (message instanceof SystemMessage) systemMessages.add(message);
            else others.add(message);
        }
        int keepOthers = Math.max(0, maxMessages - systemMessages.size());
        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(others.subList(others.size() - keepOthers, others.size()));
        return result;
    }
}
