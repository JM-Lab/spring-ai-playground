package jm.kr.spring.ai.playground.service.chat;


import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.chat.ChatHistory.TIMESTAMP;
import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.DOC_INFO_ID;
import static org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor.FILTER_EXPRESSION;

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
    private final VectorStoreDocumentService vectorStoreDocumentService;

    public ChatService(ChatModel chatModel, ChatClient.Builder chatClientBuilder,
            SpringAiPlaygroundOptions playgroundOptions, List<Advisor> advisors, ChatMemory chatMemory,
            VectorStoreDocumentService vectorStoreDocumentService) {
        this.systemPrompt = playgroundOptions.chat().systemPrompt();
        this.models = playgroundOptions.chat().models();
        this.chatModel = chatModel;
        this.chatOptions = Optional.ofNullable((ChatOptions) playgroundOptions.chat().chatOptions())
                .orElseGet(chatModel::getDefaultOptions);
        this.advisors = advisors;
        this.chatClientBuilder = chatClientBuilder;
        this.chatMemory = chatMemory;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.chatClientCache = new WeakHashMap<>();
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, long timestamp, String filterExpression,
            Consumer<ChatHistory> completeChatHistoryConsumer) {
        return streamWithRaw(chatHistory, prompt, timestamp, filterExpression).map(Generation::getOutput)
                .map(AssistantMessage::getText)
                .doFinally(signalType -> {
                    if (Objects.nonNull(completeChatHistoryConsumer) && SignalType.ON_COMPLETE.equals(signalType))
                        completeChatHistoryConsumer.accept(chatHistory);
                });
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, long timestamp,
            String filterExpression) {
        return getChatClientRequestSpec(chatHistory, prompt, timestamp, filterExpression).stream().chatResponse()
                .map(ChatResponse::getResult);

    }

    private ChatClient.ChatClientRequestSpec getChatClientRequestSpec(ChatHistory chatHistory, String prompt,
            long timestamp, String filterExpression) {
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = buildChatClient(chatHistory).prompt(new Prompt(
                new UserMessage(prompt, List.of(), Map.of("chatId", chatHistory.chatId(), TIMESTAMP, timestamp))));
        Optional.ofNullable(filterExpression).filter(Predicate.not(String::isBlank)).ifPresent(
                filter -> chatClientRequestSpec.advisors(
                        advisorSpec -> advisorSpec.param(FILTER_EXPRESSION, filter)));
        return chatClientRequestSpec;
    }

    private ChatClient buildChatClient(ChatHistory chatHistory) {
        return this.chatClientCache.computeIfAbsent(chatHistory.chatId(), id -> {
            List<Advisor> advisors = new ArrayList<>(this.advisors);
            MessageChatMemoryAdvisor messageChatMemoryAdvisor = new MessageChatMemoryAdvisor(this.chatMemory, id,
                    AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE);
            advisors.addFirst(messageChatMemoryAdvisor);
            ChatClient.Builder chatClientBuilder =
                    this.chatClientBuilder.clone().defaultAdvisors(advisors)
                            .defaultOptions(chatHistory.chatOptions());
            Optional.ofNullable(this.systemPrompt).filter(Predicate.not(String::isBlank))
                    .ifPresent(chatClientBuilder::defaultSystem);
            return chatClientBuilder.build();
        });
    }

    public String call(ChatHistory chatHistory, String prompt, long timestamp,
            String filterExpression) {
        return callWithRaw(chatHistory, prompt, timestamp, filterExpression).getOutput().getText();
    }

    public Generation callWithRaw(ChatHistory chatHistory, String prompt, long timestamp,
            String filterExpression) {
        return getChatClientRequestSpec(chatHistory, prompt, timestamp, filterExpression).call().chatResponse()
                .getResult();
    }

    public ChatOptions getDefaultOptions() {
        return this.chatOptions;
    }

    public String getSystemPrompt() {
        return this.systemPrompt;
    }

    public List<String> getModels() {
        return this.models;
    }

    public String getChatModelProvider() {
        return this.chatModel.getClass().getSimpleName().replace("ChatModel", "");
    }

    public List<VectorStoreDocumentInfo> getDocumentsFromVectorDB() {
        return this.vectorStoreDocumentService.getDocumentList();
    }

    public String buildFilterExpression(List<String> docInfoIds) {
        return docInfoIds.stream().collect(Collectors.joining("', '", DOC_INFO_ID + " in ['", "']"));
    }
}
