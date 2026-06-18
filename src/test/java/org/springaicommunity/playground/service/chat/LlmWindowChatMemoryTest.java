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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmWindowChatMemoryTest {

    private static final String CONV = "Chat-1";

    @Test
    void getReturnsLastDefaultWindowWhileStoreKeepsEverything() {
        ChatMemory store = fullStore();
        seedTurns(store, 10);

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(null));

        assertThat(store.get(CONV)).hasSize(20);
        assertThat(windowed.get(CONV)).hasSize(4);
        assertThat(windowed.get(CONV).getLast().getText()).isEqualTo("a9");
    }

    @Test
    void perChatWindowOverridesDefault() {
        ChatMemory store = fullStore();
        seedTurns(store, 10);
        ChatHistoryService service = mock(ChatHistoryService.class);
        when(service.getChatHistory(CONV)).thenReturn(historyWithWindow(6));

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(service));

        assertThat(windowed.get(CONV)).hasSize(6);
    }

    @Test
    void fallsBackToDefaultWhenConversationUnknownOrWindowUnset() {
        ChatMemory store = fullStore();
        seedTurns(store, 10);
        ChatHistoryService service = mock(ChatHistoryService.class);
        when(service.getChatHistory(CONV)).thenReturn(null);

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(service));

        assertThat(windowed.get(CONV)).hasSize(4);
    }

    @Test
    void returnsEverythingWhenStoreSmallerThanWindow() {
        ChatMemory store = fullStore();
        seedTurns(store, 1);

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(null));

        assertThat(windowed.get(CONV)).hasSize(2);
    }

    @Test
    void nonPositivePerChatWindowFallsBackToDefault() {
        ChatMemory store = fullStore();
        seedTurns(store, 10);
        ChatHistoryService service = mock(ChatHistoryService.class);
        when(service.getChatHistory(CONV)).thenReturn(historyWithWindow(0));

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(service));

        assertThat(windowed.get(CONV)).hasSize(4);
    }

    @Test
    void systemMessagesArePreservedWhenWindowing() {
        ChatMemory store = fullStore();
        seedTurns(store, 6);
        store.add(CONV, List.of(new SystemMessage("sys")));

        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(null));

        List<Message> got = windowed.get(CONV);
        assertThat(got).hasSize(4);
        assertThat(got).anyMatch(SystemMessage.class::isInstance);
    }

    @Test
    void addAndClearDelegateToStore() {
        ChatMemory store = fullStore();
        LlmWindowChatMemory windowed = new LlmWindowChatMemory(store, 4, providerFor(null));

        windowed.add(CONV, List.of(new UserMessage("hi")));
        assertThat(store.get(CONV)).hasSize(1);

        windowed.clear(CONV);
        assertThat(store.get(CONV)).isEmpty();
    }

    private static ChatMemory fullStore() {
        return MessageWindowChatMemory.builder().chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(2000).build();
    }

    private static void seedTurns(ChatMemory store, int turns) {
        for (int i = 0; i < turns; i++)
            store.add(CONV, List.of(new UserMessage("u" + i), new AssistantMessage("a" + i)));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ChatHistoryService> providerFor(ChatHistoryService service) {
        ObjectProvider<ChatHistoryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }

    private static ChatHistory historyWithWindow(Integer window) {
        return new ChatHistory(CONV, "t", 0L, 0L, "", null, new ChatExtraOptions(null, null, null, window),
                "Ollama", ChatToolPreferences.defaults(), () -> new ArrayList<>());
    }
}
