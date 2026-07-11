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

import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"spring.ai.playground.user-home=${java.io.tmpdir}"})
class ChatHistoryPersistenceServiceTest {

    @Autowired
    private ChatHistoryPersistenceService chatHistoryPersistenceService;

    @Autowired
    private PersistenceExecutor persistenceExecutor;

    @BeforeEach
    void setUp() {
        chatHistoryPersistenceService.clear();
    }

    @AfterEach
    void tearDown() {
        chatHistoryPersistenceService.clear();
    }

    private ChatHistory buildHistory(String id) {
        long now = System.currentTimeMillis();
        return new ChatHistory(id, "T", now, now, "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("Hi")));
    }

    @Test
    void testSaveAndLoadChatHistory() throws IOException {
        String conversationId = "chat-001";
        String title = "Test Chat";
        long timestamp = System.currentTimeMillis();
        String systemPrompt = "You are a helpful assistant.";
        DefaultChatOptions chatOptions = (DefaultChatOptions) ChatOptions.builder().build();

        List<Message> messages = List.of(
                new UserMessage("Hello!"),
                new AssistantMessage("Hi there! How can I help you?"),
                new SystemMessage("System initialized.")
        );

        ChatHistory history =
                new ChatHistory(conversationId, title, timestamp, timestamp, systemPrompt, chatOptions, () -> messages);

        chatHistoryPersistenceService.save(history);

        ChatHistory loadedHistory = chatHistoryPersistenceService.loads().getFirst();

        assertThat(loadedHistory.conversationId()).isEqualTo(conversationId);
        assertThat(loadedHistory.title()).isEqualTo(title);
        assertThat(loadedHistory.createTimestamp()).isEqualTo(timestamp);
        assertThat(loadedHistory.updateTimestamp()).isEqualTo(timestamp);
        assertThat(loadedHistory.systemPrompt()).isEqualTo(systemPrompt);
        assertThat(loadedHistory.chatOptions()).isNotNull();

        List<Message> loadedMessages = loadedHistory.messagesSupplier().get();
        assertThat(loadedMessages).hasSize(3);

        Message userMessage = loadedMessages.getFirst();
        assertThat(userMessage).isInstanceOf(UserMessage.class);
        assertThat(userMessage.getText()).isEqualTo("Hello!");
        assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
        assertThat(userMessage.getMetadata()).containsEntry("messageType", MessageType.USER);

        Message assistantMessage = loadedMessages.get(1);
        assertThat(assistantMessage).isInstanceOf(AssistantMessage.class);
        assertThat(assistantMessage.getText()).isEqualTo("Hi there! How can I help you?");
        assertThat(assistantMessage.getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(assistantMessage.getMetadata()).containsEntry("messageType", MessageType.ASSISTANT);

        Message systemMessage = loadedMessages.get(2);
        assertThat(systemMessage).isInstanceOf(SystemMessage.class);
        assertThat(systemMessage.getText()).isEqualTo("System initialized.");
        assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(systemMessage.getMetadata()).containsEntry("messageType", MessageType.SYSTEM);
    }

    @Test
    void toolPreferencesSurviveSaveAndLoad() throws IOException {
        ChatToolPreferences prefs = new ChatToolPreferences(true, Set.of("tool-a", "tool-b"),
                List.of("doc-1"), Map.of(McpTransportType.STREAMABLE_HTTP, List.of("server-x")),
                ReasoningEffort.MEDIUM, true);
        chatHistoryPersistenceService.save(buildHistory("chat-prefs").withToolPreferences(prefs));

        ChatToolPreferences loaded = chatHistoryPersistenceService.loads().getFirst().toolPreferences();
        assertThat(loaded.useBuiltinMcp()).isTrue();
        assertThat(loaded.exposedToolIds()).containsExactlyInAnyOrder("tool-a", "tool-b");
        assertThat(loaded.ragDocInfoIds()).containsExactly("doc-1");
        assertThat(loaded.mcpServerNames()).containsEntry(McpTransportType.STREAMABLE_HTTP, List.of("server-x"));
        assertThat(loaded.reasoningEffort()).isEqualTo(ReasoningEffort.MEDIUM);
        assertThat(loaded.dynamicTools()).isTrue();
    }

    @Test
    void nonChatFilesInSaveDirAreSkipped() throws IOException {
        chatHistoryPersistenceService.save(buildHistory("Chat-real"));
        Files.writeString(chatHistoryPersistenceService.getSaveDir().resolve("system-prompt-presets.json"),
                "[{\"id\":\"user-x\",\"displayName\":\"X\",\"prompt\":\"hi\",\"kind\":\"EXAMPLE\",\"tools\":[]}]");

        List<ChatHistory> loaded = chatHistoryPersistenceService.loads();
        assertThat(loaded).extracting(ChatHistory::conversationId).containsExactly("Chat-real");
    }

    @Test
    void oldFileWithoutToolPreferencesLoadsDefaults() {
        Map<String, Object> oldFormat = new HashMap<>();
        oldFormat.put("conversationId", "legacy-chat");
        oldFormat.put("title", "Legacy");
        oldFormat.put("createTimestamp", 1L);
        oldFormat.put("updateTimestamp", 1L);
        oldFormat.put("chatOptions", Map.of());
        oldFormat.put("messageList", List.of());

        ChatHistory loaded = chatHistoryPersistenceService.convertTo(oldFormat);
        assertThat(loaded.toolPreferences()).isEqualTo(ChatToolPreferences.defaults());
    }

    @Test
    void testSaveAsync_writesFileAfterFlush() throws InterruptedException, TimeoutException {
        ChatHistory history = buildHistory("chat-async-save");

        chatHistoryPersistenceService.saveAsync(history);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(2));

        Path expected = chatHistoryPersistenceService.getSaveDir().resolve("chat-async-save.json");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(chatHistoryPersistenceService.getSaveDir().toFile().listFiles()).hasSize(1);
    }

    @Test
    void testDeleteAsync_removesFileAfterFlush() throws IOException, InterruptedException, TimeoutException {
        ChatHistory history = buildHistory("chat-async-delete");
        chatHistoryPersistenceService.save(history);
        Path expected = chatHistoryPersistenceService.getSaveDir().resolve("chat-async-delete.json");
        assertThat(Files.exists(expected)).isTrue();

        chatHistoryPersistenceService.deleteAsync(history, true);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(2));

        assertThat(Files.exists(expected)).isFalse();
    }

    @Test
    void testSave_leavesNoTempFile() throws IOException {
        ChatHistory history = buildHistory("chat-atomic");
        chatHistoryPersistenceService.save(history);

        assertThat(chatHistoryPersistenceService.getSaveDir().toFile().listFiles())
                .extracting(java.io.File::getName)
                .allMatch(name -> name.endsWith(".json") && !name.endsWith(".tmp"));
    }

    @Test
    void testSaveAndLoad_preservesAssistantToolCalls() throws IOException {
        AssistantMessage.ToolCall toolCall =
                new AssistantMessage.ToolCall("call_1", "function", "weather", "{\"city\":\"Seoul\"}");
        AssistantMessage assistantWithToolCalls = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(toolCall))
                .build();
        ChatHistory history = new ChatHistory("chat-toolcalls", "T", 1L, 1L, "", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(assistantWithToolCalls));

        chatHistoryPersistenceService.save(history);
        ChatHistory loaded = chatHistoryPersistenceService.loads().getFirst();

        Message reloaded = loaded.messagesSupplier().get().getFirst();
        assertThat(reloaded).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) reloaded).getToolCalls()).containsExactly(toolCall);
    }

    @Test
    void testSaveAndLoad_preservesToolResponseMessage() throws IOException {
        ToolResponseMessage.ToolResponse response =
                new ToolResponseMessage.ToolResponse("call_1", "weather", "{\"temp\":15,\"sky\":\"clear\"}");
        ToolResponseMessage toolMessage = ToolResponseMessage.builder()
                .responses(List.of(response))
                .build();
        ChatHistory history = new ChatHistory("chat-toolresponse", "T", 1L, 1L, "", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(toolMessage));

        chatHistoryPersistenceService.save(history);
        ChatHistory loaded = chatHistoryPersistenceService.loads().getFirst();

        Message reloaded = loaded.messagesSupplier().get().getFirst();
        assertThat(reloaded).isInstanceOf(ToolResponseMessage.class);
        assertThat(reloaded.getMessageType()).isEqualTo(MessageType.TOOL);
        assertThat(((ToolResponseMessage) reloaded).getResponses()).containsExactly(response);
    }
}