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
package jm.kr.spring.ai.playground.service.chat;


import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions;
import jm.kr.spring.ai.playground.service.SharedDataReader;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.mcp.McpToolCallingManager.MCP_PROCESS_MESSAGE_CONSUMER;
import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.DOC_INFO_ID;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;
import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    public static final String CHAT_META = "chatMeta";
    public static final String RAG_FILTER_EXPRESSION = "ragFilterExpression";

    public record ChatMeta(String model, Usage usage, List<Document> retrievedDocuments) {}

    private final String systemPrompt;
    private final List<String> models;
    private final ChatModel chatModel;
    private final ChatOptions chatOptions;
    private final ChatClient chatClient;
    private final SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader;
    private final SharedDataReader<List<McpServerInfo>> mcpServerInfosReader;

    public ChatService(ChatModel chatModel, ChatClient chatClient, SpringAiPlaygroundOptions playgroundOptions,
            SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader,
            SharedDataReader<List<McpServerInfo>> mcpServerInfosReader) {
        this.systemPrompt = playgroundOptions.chat().systemPrompt();
        this.models = playgroundOptions.chat().models();
        this.chatModel = chatModel;
        this.chatOptions = Optional.ofNullable((ChatOptions) playgroundOptions.chat().chatOptions())
                .orElseGet(chatModel::getDefaultOptions);
        this.chatClient = chatClient;
        this.vectorStoreDocumentsReader = vectorStoreDocumentsReader;
        this.mcpServerInfosReader = mcpServerInfosReader;
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, String filterExpression,
            Consumer<ChatHistory> completeChatHistoryConsumer, List<ToolCallback> toolCallbacks,
            Consumer<Object> mcpToolProcessMessageConsumer, Consumer<Object> thinkProcessMessageConsumer) {
        return streamWithRaw(chatHistory, prompt, filterExpression, toolCallbacks, mcpToolProcessMessageConsumer,
                thinkProcessMessageConsumer).map(
                        Generation::getOutput)
                .map(assistantMessage -> Optional.ofNullable(assistantMessage.getText()).orElse(""))
                .doFinally(signalType -> {
                    if (Objects.nonNull(completeChatHistoryConsumer) &&
                            (SignalType.ON_COMPLETE.equals(signalType) || SignalType.CANCEL.equals(signalType)))
                        completeChatHistoryConsumer.accept(chatHistory);
                });
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer,
            Consumer<Object> thinkProcessMessageConsumer) {
        AtomicReference<ChatClientResponse> lastChatResponse = new AtomicReference<>();
        return getChatClientRequestSpec(chatHistory, prompt, filterExpression, toolCallbacks,
                mcpToolProcessMessageConsumer).stream().chatClientResponse().map(chatClientResponse -> {
                    if (Objects.nonNull(thinkProcessMessageConsumer)) {
                        Generation generation = chatClientResponse.chatResponse().getResult();
                        Optional.ofNullable(Optional.ofNullable(generation.getOutput().getMetadata().get("reasoningContent"))
                                        .map(Object::toString).orElseGet(() -> generation.getMetadata().get("thinking")))
                                .filter(Predicate.not(String::isEmpty)).ifPresent(thinkProcessMessageConsumer);
                    }
                    return chatClientResponse;
                }).filter(chatClientResponse -> {
                    String text = chatClientResponse.chatResponse().getResult().getOutput().getText();
                    if (Objects.nonNull(text)) {
                        lastChatResponse.set(chatClientResponse);
                        return true;
                    }
                    return false;
                }).map(chatClientResponse -> chatClientResponse.chatResponse().getResult())
                .doFinally(signalType -> {
                    if (SignalType.ON_COMPLETE.equals(signalType))
                        applyChatResponseMetadataToLastUserMessage(chatHistory, lastChatResponse.get());
                });
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequestSpec(ChatHistory chatHistory, String prompt,
            String filterExpression, List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer) {
        DefaultChatOptions chatOptions = chatHistory.chatOptions();
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = this.chatClient.prompt().user(prompt).options(
                        DefaultToolCallingChatOptions.builder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                                .maxTokens(chatOptions.getMaxTokens())
                                .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                                .temperature(chatOptions.getTemperature())
                                .topP(chatOptions.getTopP()).build())
                .advisors(advisor -> {
                    advisor.param(CONVERSATION_ID, chatHistory.conversationId());
                    if (StringUtils.hasText(filterExpression))
                        advisor.param(RAG_FILTER_EXPRESSION, filterExpression);
                });
        if (Objects.nonNull(mcpToolProcessMessageConsumer) && Objects.nonNull(toolCallbacks) &&
                !toolCallbacks.isEmpty())
            chatClientRequestSpec.toolCallbacks(toolCallbacks)
                    .toolContext(Map.of(MCP_PROCESS_MESSAGE_CONSUMER, mcpToolProcessMessageConsumer));
        return Optional.ofNullable(chatHistory.systemPrompt()).filter(Predicate.not(String::isBlank))
                .map(chatClientRequestSpec::system).orElse(chatClientRequestSpec);
    }


    public String call(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer) {
        return callWithRaw(chatHistory, prompt, filterExpression, toolCallbacks,
                mcpToolProcessMessageConsumer).getOutput().getText();
    }

    public Generation callWithRaw(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer) {
        return applyChatResponseMetadataToLastUserMessage(chatHistory,
                getChatClientRequestSpec(chatHistory, prompt, filterExpression, toolCallbacks,
                        mcpToolProcessMessageConsumer).call()
                        .chatClientResponse()).getResult();
    }

    private ChatResponse applyChatResponseMetadataToLastUserMessage(ChatHistory chatHistory,
            ChatClientResponse chatClientResponse) {
        ChatResponse chatResponse = chatClientResponse.chatResponse();
        chatHistory.messagesSupplier().get().reversed().stream()
                .filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst()
                .map(Message::getMetadata).ifPresentOrElse(metadata -> {
                            ChatResponseMetadata chatResponseMetadata = chatResponse.getMetadata();
                            metadata.put(CHAT_META, new ChatMeta(chatResponseMetadata.getModel(), chatResponseMetadata.getUsage(),
                                    (List<Document>) chatClientResponse.context().get(DOCUMENT_CONTEXT)));
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

    public String buildFilterExpression(List<String> docInfoIds) {
        return docInfoIds.isEmpty() ? null : docInfoIds.stream()
                .collect(Collectors.joining("', '", DOC_INFO_ID + " in ['", "']"));
    }

    public List<VectorStoreDocumentInfo> getExistDocumentInfoList() {
        return this.vectorStoreDocumentsReader.read();
    }

    public List<McpServerInfo> getLiveMcpServerInfos() {
        return this.mcpServerInfosReader.read();
    }
}
