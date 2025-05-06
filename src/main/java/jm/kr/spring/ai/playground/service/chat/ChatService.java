package jm.kr.spring.ai.playground.service.chat;


import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.DOC_INFO_ID;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.DEFAULT_CHAT_MEMORY_RESPONSE_SIZE;
import static org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String CHAT_META = "chatMeta";
    public static final String RAG_FILTER_EXPRESSION = "ragFilterExpression";

    public record ChatMeta(String model, Usage usage, List<Document> retrievedDocuments) {}

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
        Optional.ofNullable(this.systemPrompt).filter(Predicate.not(String::isBlank))
                .ifPresent(chatClientBuilder::defaultSystem);
        this.chatClientBuilder = chatClientBuilder;
        this.chatMemory = chatMemory;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.chatClientCache = new WeakHashMap<>();
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, String filterExpression,
            Consumer<ChatHistory> completeChatHistoryConsumer) {
        return streamWithRaw(chatHistory, prompt, filterExpression).map(Generation::getOutput)
                .map(AssistantMessage::getText).doFinally(signalType -> {
                    if (Objects.nonNull(completeChatHistoryConsumer) && SignalType.ON_COMPLETE.equals(signalType))
                        completeChatHistoryConsumer.accept(chatHistory);
                });
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, String filterExpression) {
        AtomicReference<ChatResponse> lastChatResponse = new AtomicReference<>();
        return getChatClientRequestSpec(chatHistory, prompt, filterExpression).stream().chatResponse()
                .doOnNext(lastChatResponse::set).doFinally(signalType -> {
                    if (SignalType.ON_COMPLETE.equals(signalType))
                        applyChatResponseMetadataToLastUserMessage(chatHistory, lastChatResponse.get());
                }).map(ChatResponse::getResult);
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequestSpec(ChatHistory chatHistory, String prompt,
            String filterExpression) {
        return buildChatClient(chatHistory).prompt().user(prompt).advisors(advisor -> {
            advisor.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatHistory.conversationId());
            advisor.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100);
            if (StringUtils.hasText(filterExpression))
                advisor.param(RAG_FILTER_EXPRESSION, filterExpression);
        });
    }

    private ChatClient buildChatClient(ChatHistory chatHistory) {
        return this.chatClientCache.computeIfAbsent(chatHistory.conversationId(), conversationId -> {
            List<Advisor> advisors = new ArrayList<>(this.advisors);
            advisors.addFirst(MessageChatMemoryAdvisor.builder(this.chatMemory)
                    .chatMemoryRetrieveSize(DEFAULT_CHAT_MEMORY_RESPONSE_SIZE).conversationId(conversationId).build());
            return this.chatClientBuilder.clone().defaultAdvisors(advisors).defaultOptions(chatHistory.chatOptions())
                    .build();
        });
    }

    public String call(ChatHistory chatHistory, String prompt, String filterExpression) {
        return callWithRaw(chatHistory, prompt, filterExpression).getOutput().getText();
    }

    public Generation callWithRaw(ChatHistory chatHistory, String prompt, String filterExpression) {
        return applyChatResponseMetadataToLastUserMessage(chatHistory,
                getChatClientRequestSpec(chatHistory, prompt, filterExpression).call().chatResponse()).getResult();
    }

    private ChatResponse applyChatResponseMetadataToLastUserMessage(ChatHistory chatHistory,
            ChatResponse chatResponse) {
        chatHistory.messagesSupplier().get().reversed().stream()
                .filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getMetadata).ifPresentOrElse(metadata -> {
                            ChatResponseMetadata chatResponseMetadata = chatResponse.getMetadata();
                            metadata.put(CHAT_META, new ChatMeta(chatResponseMetadata.getModel(),
                                    chatResponseMetadata.getUsage(), chatResponseMetadata.get(DOCUMENT_CONTEXT)));
                        },
                        () -> logger.error("No user message found in chat history to update metadata. [conversationId={}]",
                                chatHistory.conversationId()));
        return chatResponse;
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

    public List<VectorStoreDocumentInfo> getExistDocumentInfoList() {
        return this.vectorStoreDocumentService.getDocumentList();
    }

    public String buildFilterExpression(List<String> docInfoIds) {
        return docInfoIds.isEmpty() ? null : docInfoIds.stream()
                .collect(Collectors.joining("', '", DOC_INFO_ID + " in ['", "']"));
    }
}
