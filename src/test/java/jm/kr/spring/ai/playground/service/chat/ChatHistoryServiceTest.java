package jm.kr.spring.ai.playground.service.chat;

import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import jm.kr.spring.ai.playground.webui.chat.ChatView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptionsBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
public class ChatHistoryServiceTest {

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private SpringAiPlaygroundOptions playgroundOptions;

    @Autowired
    private ChatMemory chatMemory;

    private ChatOptions chatOptions;

    @BeforeEach
    public void setUp() {
        this.chatOptions = new DefaultChatOptionsBuilder().build();
    }

    @Test
    public void testCreateChatHistory() {
        String systemPrompt = "Test System Prompt";

        ChatHistory chatHistory = chatHistoryService.createChatHistory(systemPrompt, chatOptions);
        assertNotNull(chatHistory);
        assertEquals(systemPrompt, chatHistory.systemPrompt());
        assertEquals(chatOptions.getModel(), chatHistory.chatOptions().getModel());
        assertEquals(chatOptions.getTemperature(), chatHistory.chatOptions().getTemperature());
        assertEquals(chatOptions.getMaxTokens(), chatHistory.chatOptions().getMaxTokens());
        assertEquals(chatOptions.getTopP(), chatHistory.chatOptions().getTopP());
        assertEquals(chatOptions.getStopSequences(), chatHistory.chatOptions().getStopSequences());
        assertTrue(chatHistory.conversationId().startsWith("Chat-"));
        assertEquals(chatHistory.createTimestamp(), chatHistory.updateTimestamp());
        assertNull(chatHistory.title());
        assertTrue(chatMemory.get(chatHistory.conversationId()).isEmpty());
    }

    @Test
    public void testUpdateChatHistory() {
        ChatHistory chatHistory = chatHistoryService.createChatHistory("systemPrompt", chatOptions);
        this.chatMemory.add(chatHistory.conversationId(), new UserMessage("User Message"));
        chatHistoryService.updateChatHistory(chatHistory);
        String updatedTitle = "Updated Title";
        ChatHistory newChatHistory = chatHistory.mutate("Updated Title", chatHistory.updateTimestamp());
        try {
            sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        chatHistoryService.updateChatHistory(newChatHistory);
        ChatHistory updatedChatHistory = chatHistoryService.getChatHistoryList().stream()
                .filter(h -> h.conversationId().equals(newChatHistory.conversationId())).findFirst().orElseThrow();

        assertEquals(chatHistory.conversationId(), updatedChatHistory.conversationId());
        assertEquals(chatHistory.systemPrompt(), updatedChatHistory.systemPrompt());
        assertEquals(chatHistory.chatOptions(), updatedChatHistory.chatOptions());
        assertEquals(chatHistory.messagesSupplier(), updatedChatHistory.messagesSupplier());
        assertNull(chatHistory.title());
        assertEquals(updatedChatHistory.title(), updatedTitle);
        assertEquals(updatedChatHistory.createTimestamp(), chatHistory.createTimestamp());
        assertTrue(updatedChatHistory.updateTimestamp() > chatHistory.updateTimestamp());
    }

    @Test
    public void testGetChatHistoryList() {
        ChatHistory chatHistory = chatHistoryService.createChatHistory("systemPrompt", chatOptions);
        assertNull(chatHistoryService.getChatHistoryList().stream().filter(h -> h.conversationId().equals(chatHistory.conversationId()))
                .findFirst().orElse(null));
        this.chatMemory.add(chatHistory.conversationId(), new UserMessage("User Message"));
        chatHistoryService.updateChatHistory(chatHistory);
        assertEquals("User Message",
                chatHistoryService.getChatHistoryList().stream().filter(h -> h.conversationId().equals(chatHistory.conversationId()))
                        .findFirst().orElseThrow().title());
    }

    @Test
    public void testDeleteChatHistory() {
        ChatHistory chatHistory = chatHistoryService.createChatHistory("To Delete", chatOptions);
        String conversationId = chatHistory.conversationId();

        chatHistoryService.deleteChatHistory(conversationId);
        List<ChatHistory> historyList = chatHistoryService.getChatHistoryList();

        assertFalse(historyList.stream().anyMatch(h -> h.conversationId().equals(conversationId)));
    }

    @Test
    void testChatHistoryEvents() {
        PropertyChangeListener listener = mock(PropertyChangeListener.class);
        PropertyChangeSupport chatHistoryChangeSupport = new PropertyChangeSupport(this);
        chatHistoryChangeSupport.addPropertyChangeListener(listener);

        ChatHistory chatHistory = chatHistoryService.createChatHistory("systemPrompt", chatOptions);
        String conversationId = chatHistory.conversationId();
        chatMemory.add(conversationId, new UserMessage("User Message"));
        chatHistoryChangeSupport.firePropertyChange(ChatView.CHAT_HISTORY_CHANGE_EVENT, null, conversationId);

        chatHistoryService.updateChatHistory(chatHistory.mutate("", System.currentTimeMillis()));

        chatHistoryService.deleteChatHistory(conversationId);
        chatHistoryChangeSupport.firePropertyChange(ChatView.CHAT_HISTORY_EMPTY_EVENT, null, conversationId);

        chatHistoryChangeSupport.firePropertyChange(ChatView.CHAT_HISTORY_SELECT_EVENT, null, conversationId);

        ArgumentCaptor<PropertyChangeEvent> eventCaptor = ArgumentCaptor.forClass(PropertyChangeEvent.class);
        verify(listener, times(3)).propertyChange(eventCaptor.capture());

        List<PropertyChangeEvent> events = eventCaptor.getAllValues();

        assertTrue(events.stream().anyMatch(
                e -> e.getPropertyName().equals(ChatView.CHAT_HISTORY_CHANGE_EVENT) && e.getOldValue() == null &&
                        e.getNewValue().equals(conversationId)));

        assertTrue(events.stream().anyMatch(
                e -> e.getPropertyName().equals(ChatView.CHAT_HISTORY_SELECT_EVENT) && e.getOldValue() == null &&
                        e.getNewValue().equals(conversationId)));

        assertTrue(events.stream().anyMatch(
                e -> e.getPropertyName().equals(ChatView.CHAT_HISTORY_EMPTY_EVENT) && e.getOldValue() == null &&
                        e.getNewValue().equals(conversationId)));

    }
}