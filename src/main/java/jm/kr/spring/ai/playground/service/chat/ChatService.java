package jm.kr.spring.ai.playground.service.chat;


import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static jm.kr.spring.ai.playground.service.chat.ChatHistory.TIMESTAMP;

@Service
public class ChatService {

    private final List<Advisor> advisors;
    private final String systemPrompt;
    private final List<String> models;
    private final ChatModel chatModel;
    private final ChatOptions chatOptions;
    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemory chatMemory;
    private final Map<String, ChatClient> chatClientCache;
    private final List<Consumer<ChatHistory>> completeResponseConsumers;

    public ChatService(ChatModel chatModel, ChatClient.Builder chatClientBuilder,
            SpringAiPlaygroundOptions playgroundOptions, List<Advisor> advisors, ChatMemory chatMemory) {
        this.systemPrompt = playgroundOptions.chat().systemPrompt();
        this.models = playgroundOptions.chat().models();
        this.chatModel = chatModel;
        this.chatOptions =
                Optional.ofNullable(playgroundOptions.chat().chatOptions()).orElseGet(chatModel::getDefaultOptions);
        this.advisors = advisors;
        this.chatClientBuilder = chatClientBuilder;
        this.chatMemory = chatMemory;
        this.chatClientCache = new WeakHashMap<>();
        this.completeResponseConsumers = new ArrayList<>();
    }

    public ChatService registerCompleteResponseConsumer(Consumer<ChatHistory> completeResponseConsumer) {
        this.completeResponseConsumers.add(completeResponseConsumer);
        return this;
    }

    private ChatClient buildChatClient(ChatHistory chatHistory) {
        return this.chatClientCache.computeIfAbsent(chatHistory.getChatId(), id -> {
            List<Advisor> advisors = new ArrayList<>(this.advisors);
            MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(this.chatMemory, id,
                    AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
            if (advisors.isEmpty())
                advisors.add(messageChatMemoryAdvisor);
            else
                advisors.set(0, messageChatMemoryAdvisor);
            ChatClient.Builder chatClientBuilder =
                    this.chatClientBuilder.clone().defaultAdvisors(advisors)
                            .defaultOptions(chatHistory.getChatOptions());
            Optional.ofNullable(systemPrompt).filter(Predicate.not(String::isBlank))
                    .ifPresent(chatClientBuilder::defaultSystem);
            return chatClientBuilder.build();
        });
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, long timestamp) {
        return streamWithRaw(chatHistory, prompt, timestamp).map(Generation::getOutput).map(AbstractMessage::getContent)
                .doFinally(signalType -> {
                    if (SignalType.ON_COMPLETE.equals(signalType)) {
                        if (Objects.isNull(chatHistory.getTitle()) || chatHistory.getTitle().isEmpty())
                            chatHistory.setTitle(extractTitle(chatHistory.getMessagesSupplier().get()));
                        this.completeResponseConsumers.forEach(consumer -> consumer.accept(chatHistory));
                    }
                });
    }

    private String extractTitle(List<Message> messageList) {
        return messageList.stream().filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getText).map(userPrompt -> buildTitle(userPrompt.trim(), 30)).orElseThrow(() ->
                        new IllegalArgumentException("No USER Message type: " + messageList));
    }

    private String buildTitle(String userPrompt, int length) {
        return userPrompt.length() > length ? userPrompt.substring(0, length) + "..." : userPrompt;
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, long timestamp) {
        return buildChatClient(chatHistory).prompt(new Prompt(
                        new UserMessage(prompt, List.of(), Map.of("chatId", chatHistory.getChatId(), TIMESTAMP, timestamp))))
                .stream().chatResponse().map(ChatResponse::getResult);
    }

    public String call(ChatHistory chatHistory, String prompt) {
        return callWithRaw(chatHistory, prompt).getOutput().getContent();
    }

    public Generation callWithRaw(ChatHistory chatHistory, String prompt) {
        return buildChatClient(chatHistory).prompt(prompt).call().chatResponse().getResult();
    }

    public ChatOptions getDefaultOptions() {
        return this.chatOptions;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public List<String> getModels() {
        return models;
    }

    public String getChatModelServiceName() {
        return this.chatModel.getClass().getSimpleName().replace("ChatModel", "");
    }
}
