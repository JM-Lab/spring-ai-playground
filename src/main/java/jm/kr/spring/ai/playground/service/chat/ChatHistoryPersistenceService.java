package jm.kr.spring.ai.playground.service.chat;

import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class ChatHistoryPersistenceService implements PersistenceServiceInterface<ChatHistory> {
    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryPersistenceService.class);

    private final Path saveDir;

    public ChatHistoryPersistenceService(Path springAiPlaygroundHomeDir) {
        this.saveDir = springAiPlaygroundHomeDir.resolve("chat").resolve("save");
        if (!this.saveDir.toFile().exists())
            this.saveDir.toFile().mkdirs();
    }

    @Override
    public Path getSaveDir() {
        return this.saveDir;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String buildSaveDataAndReturnName(ChatHistory chatHistory, Map<String, Object> saveObjectMap) {
        saveObjectMap.put("messageList", chatHistory.messagesSupplier().get());
        return chatHistory.chatId();
    }

    @Override
    public ChatHistory convertTo(Map<String, Object> saveObjectMap) {
        String chatId = saveObjectMap.get("chatId").toString();
        String title = saveObjectMap.get("title").toString();
        long createTimestamp = ((Number) saveObjectMap.get("createTimestamp")).longValue();
        long updateTimestamp = ((Number) saveObjectMap.get("updateTimestamp")).longValue();
        String systemPrompt = saveObjectMap.computeIfAbsent("systemPrompt", s -> "").toString();
        DefaultChatOptions chatOptions =
                OBJECT_MAPPER.convertValue(saveObjectMap.get("chatOptions"), DefaultChatOptions.class);
        List<Map<String, Object>> messageMapList = (List<Map<String, Object>>) saveObjectMap.get("messageList");
        return new ChatHistory(chatId, title, createTimestamp, updateTimestamp, systemPrompt, chatOptions,
                () -> messageMapList.stream().map(this::convertToMessage).toList());
    }

    private Message convertToMessage(Map<String, Object> map) {
        MessageType messageType = MessageType.valueOf(map.get("messageType").toString().toUpperCase());
        String content = map.get("text").toString();
        return switch (messageType) {
            case USER -> new UserMessage(content, List.of(), (Map<String, Object>) map.computeIfAbsent("metadata",
                    key -> Map.of()));
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
            case TOOL -> new ToolResponseMessage(List.of()); // todo check TOOL response
        };
    }

    public void delete(String chatId) {
        getSaveDir().resolve(chatId).toFile().deleteOnExit();
    }

}
