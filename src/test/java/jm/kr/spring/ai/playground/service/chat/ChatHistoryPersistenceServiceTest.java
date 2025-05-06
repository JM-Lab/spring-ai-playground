package jm.kr.spring.ai.playground.service.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"spring.ai.playground.user-home=${java.io.tmpdir}"})
class ChatHistoryPersistenceServiceTest {

    @Autowired
    private ChatHistoryPersistenceService chatHistoryPersistenceService;

    @AfterEach
    void tearDown() {
        chatHistoryPersistenceService.clear();
    }

    @Test
    void testSaveAndLoadChatHistory() throws IOException {
        String conversationId = "chat-001";
        String title = "Test Chat";
        long timestamp = System.currentTimeMillis();
        String systemPrompt = "You are a helpful assistant.";
        DefaultChatOptions chatOptions = new DefaultChatOptions();

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
}