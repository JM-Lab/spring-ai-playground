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


import com.openai.core.JsonValue;
import com.openai.models.chat.completions.ChatCompletionChunk;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.config.MdcIdentityFilter;
import org.springaicommunity.playground.service.SharedDataReader;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
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
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.springaicommunity.playground.service.SpringAiPlaygroundRagAdvisor.RAG_PROCESS_MESSAGE_CONSUMER;
import static org.springaicommunity.playground.service.mcp.McpToolCallingManager.MCP_PROCESS_MESSAGE_CONSUMER;
import static org.springaicommunity.playground.service.mcp.McpToolCallingManager.TOOL_CONTEXT_SESSION_ID;
import static org.springaicommunity.playground.service.mcp.McpToolCallingManager.TOOL_CONTEXT_USER_ID;
import static org.springaicommunity.playground.service.vectorstore.VectorStoreService.DOC_INFO_ID;
import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;
import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class ChatService {
    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private static final Set<String> REASONING_KEYS = Set.of("reasoning", "reasoning_content", "reasoningContent",
            "thinking");

    public static final String CHAT_META = "chatMeta";
    public static final String RAG_FILTER_EXPRESSION = "ragFilterExpression";
    public static final String MDC_CONVERSATION_ID = "conversationId";
    public static final String MDC_USER_MESSAGE_ID = "userMessageId";

    public record ChatMeta(String model, Usage usage, List<Document> retrievedDocuments) {}

    private final String systemPrompt;
    private final List<String> models;
    private final ChatModel chatModel;
    private final ChatOptions chatOptions;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader;
    private final SharedDataReader<List<McpServerInfo>> mcpServerInfosReader;

    public ChatService(ChatModel chatModel, ChatClient chatClient, ChatMemory chatMemory,
            SpringAiPlaygroundOptions playgroundOptions,
            SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader,
            SharedDataReader<List<McpServerInfo>> mcpServerInfosReader) {
        this.systemPrompt = playgroundOptions.chat().systemPrompt();
        this.models = playgroundOptions.chat().models();
        this.chatModel = chatModel;
        this.chatOptions = Optional.ofNullable((ChatOptions) playgroundOptions.chat().chatOptions())
                .orElseGet(chatModel::getDefaultOptions);
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.vectorStoreDocumentsReader = vectorStoreDocumentsReader;
        this.mcpServerInfosReader = mcpServerInfosReader;
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, String filterExpression,
            Consumer<ChatHistory> completeChatHistoryConsumer, List<ToolCallback> toolCallbacks,
            Consumer<Object> mcpToolProcessMessageConsumer, Consumer<Object> ragProcessMessageConsumer,
            Consumer<Object> thinkProcessMessageConsumer) {
        return stream(chatHistory, prompt, filterExpression, completeChatHistoryConsumer, toolCallbacks,
                mcpToolProcessMessageConsumer, ragProcessMessageConsumer, thinkProcessMessageConsumer, null, null);
    }

    public Flux<String> stream(ChatHistory chatHistory, String prompt, String filterExpression,
            Consumer<ChatHistory> completeChatHistoryConsumer, List<ToolCallback> toolCallbacks,
            Consumer<Object> mcpToolProcessMessageConsumer, Consumer<Object> ragProcessMessageConsumer,
            Consumer<Object> thinkProcessMessageConsumer, Consumer<SignalType> beforeHistoryCommit,
            HumanQuestionHandler humanQuestionHandler) {
        return streamWithRaw(chatHistory, prompt, filterExpression, toolCallbacks, mcpToolProcessMessageConsumer,
                ragProcessMessageConsumer, thinkProcessMessageConsumer, humanQuestionHandler).map(
                        Generation::getOutput)
                .map(assistantMessage -> Optional.ofNullable(assistantMessage.getText()).orElse(""))
                .doFinally(signalType -> {
                    if (SignalType.ON_COMPLETE.equals(signalType) || SignalType.CANCEL.equals(signalType)) {
                        if (Objects.nonNull(beforeHistoryCommit)) beforeHistoryCommit.accept(signalType);
                        if (Objects.nonNull(completeChatHistoryConsumer))
                            completeChatHistoryConsumer.accept(chatHistory);
                    }
                });
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer,
            Consumer<Object> ragProcessMessageConsumer, Consumer<Object> thinkProcessMessageConsumer) {
        return streamWithRaw(chatHistory, prompt, filterExpression, toolCallbacks, mcpToolProcessMessageConsumer,
                ragProcessMessageConsumer, thinkProcessMessageConsumer, null);
    }

    public Flux<Generation> streamWithRaw(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer,
            Consumer<Object> ragProcessMessageConsumer, Consumer<Object> thinkProcessMessageConsumer,
            HumanQuestionHandler humanQuestionHandler) {
        AtomicReference<ChatClientResponse> lastChatResponse = new AtomicReference<>();
        StringBuilder accumulatedText = new StringBuilder();
        String userMessageId = UUID.randomUUID().toString();
        // Captured on the servlet thread (MdcIdentityFilter set it) to propagate into the reactive chain below.
        String userId = MDC.get(MdcIdentityFilter.USER_ID);
        String sessionId = MDC.get(MdcIdentityFilter.SESSION_ID);
        return getChatClientRequestSpec(chatHistory, prompt, filterExpression, toolCallbacks,
                mcpToolProcessMessageConsumer, ragProcessMessageConsumer, humanQuestionHandler)
                .stream().chatClientResponse().map(
                        chatClientResponse -> {
                    if (Objects.nonNull(thinkProcessMessageConsumer)) {
                        Generation generation = chatClientResponse.chatResponse().getResult();
                        extractThinking(generation).ifPresent(thinkProcessMessageConsumer);
                    }
                    return chatClientResponse;
                }).filter(chatClientResponse -> {
                    String text = chatClientResponse.chatResponse().getResult().getOutput().getText();
                    if (Objects.nonNull(text)) {
                        lastChatResponse.set(chatClientResponse);
                        accumulatedText.append(text);
                        return true;
                    }
                    return false;
                }).map(chatClientResponse -> chatClientResponse.chatResponse().getResult())
                .contextWrite(ctx -> {
                    ctx = ctx.put(MDC_CONVERSATION_ID, chatHistory.conversationId())
                            .put(MDC_USER_MESSAGE_ID, userMessageId);
                    if (userId != null) ctx = ctx.put(MdcIdentityFilter.USER_ID, userId);
                    if (sessionId != null) ctx = ctx.put(MdcIdentityFilter.SESSION_ID, sessionId);
                    return ctx;
                })
                .doFirst(() -> {
                    MDC.put(MDC_CONVERSATION_ID, chatHistory.conversationId());
                    MDC.put(MDC_USER_MESSAGE_ID, userMessageId);
                    if (userId != null) MDC.put(MdcIdentityFilter.USER_ID, userId);
                    if (sessionId != null) MDC.put(MdcIdentityFilter.SESSION_ID, sessionId);
                })
                .doFinally(signalType -> {
                    try {
                        if ((SignalType.ON_COMPLETE.equals(signalType) || SignalType.CANCEL.equals(signalType)) &&
                                Objects.nonNull(lastChatResponse.get()))
                            applyChatResponseMetadataToLastUserMessage(chatHistory, lastChatResponse.get());
                        if (SignalType.CANCEL.equals(signalType) && accumulatedText.length() > 0)
                            commitPartialAssistantMessage(chatHistory, accumulatedText.toString());
                    } finally {
                        MDC.remove(MDC_CONVERSATION_ID);
                        MDC.remove(MDC_USER_MESSAGE_ID);
                        if (userId != null) MDC.remove(MdcIdentityFilter.USER_ID);
                        if (sessionId != null) MDC.remove(MdcIdentityFilter.SESSION_ID);
                    }
                });
    }

    private void commitPartialAssistantMessage(ChatHistory chatHistory, String text) {
        List<Message> messages = chatHistory.messagesSupplier().get();
        if (!messages.isEmpty() && MessageType.ASSISTANT.equals(messages.getLast().getMessageType()))
            return;
        this.chatMemory.add(chatHistory.conversationId(), new AssistantMessage(text));
    }

    private ChatClient.ChatClientRequestSpec getChatClientRequestSpec(ChatHistory chatHistory, String prompt,
            String filterExpression, List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer,
            Consumer<Object> ragProcessMessageConsumer, HumanQuestionHandler humanQuestionHandler) {
        DefaultChatOptions chatOptions = chatHistory.chatOptions();
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = this.chatClient.prompt().user(prompt).options(
                        DefaultToolCallingChatOptions.builder().frequencyPenalty(chatOptions.getFrequencyPenalty())
                                .maxTokens(chatOptions.getMaxTokens())
                                .model(chatOptions.getModel()).presencePenalty(chatOptions.getPresencePenalty())
                                .temperature(chatOptions.getTemperature())
                                .topP(chatOptions.getTopP()).build())
                .advisors(advisor -> {
                    advisor.param(CONVERSATION_ID, chatHistory.conversationId());
                    if (StringUtils.hasText(filterExpression)) {
                        advisor.param(RAG_FILTER_EXPRESSION, filterExpression);
                        if (Objects.nonNull(ragProcessMessageConsumer))
                            advisor.param(RAG_PROCESS_MESSAGE_CONSUMER, ragProcessMessageConsumer);
                    }
                });
        if (Objects.nonNull(mcpToolProcessMessageConsumer) && Objects.nonNull(toolCallbacks) &&
                !toolCallbacks.isEmpty()) {
            Map<String, Object> toolContext = new HashMap<>();
            toolContext.put(MCP_PROCESS_MESSAGE_CONSUMER, mcpToolProcessMessageConsumer);
            if (Objects.nonNull(humanQuestionHandler))
                toolContext.put(HumanQuestionHandler.TOOL_CONTEXT_KEY, humanQuestionHandler);
            // Carry identity via tool context so McpToolCallingManager can restore it on the tool-exec thread.
            putIfPresent(toolContext, TOOL_CONTEXT_USER_ID, MDC.get(MdcIdentityFilter.USER_ID));
            putIfPresent(toolContext, TOOL_CONTEXT_SESSION_ID, MDC.get(MdcIdentityFilter.SESSION_ID));
            chatClientRequestSpec.toolCallbacks(toolCallbacks).toolContext(toolContext);
        }
        return Optional.ofNullable(chatHistory.systemPrompt()).filter(Predicate.not(String::isBlank))
                .map(chatClientRequestSpec::system).orElse(chatClientRequestSpec);
    }

    private static void putIfPresent(Map<String, Object> toolContext, String key, String value) {
        if (value != null && !value.isBlank()) toolContext.put(key, value);
    }

    public String call(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer) {
        return callWithRaw(chatHistory, prompt, filterExpression, toolCallbacks,
                mcpToolProcessMessageConsumer).getOutput().getText();
    }

    public Generation callWithRaw(ChatHistory chatHistory, String prompt, String filterExpression,
            List<ToolCallback> toolCallbacks, Consumer<Object> mcpToolProcessMessageConsumer) {
        String userMessageId = UUID.randomUUID().toString();
        MDC.put(MDC_CONVERSATION_ID, chatHistory.conversationId());
        MDC.put(MDC_USER_MESSAGE_ID, userMessageId);
        try {
            return applyChatResponseMetadataToLastUserMessage(chatHistory,
                    getChatClientRequestSpec(chatHistory, prompt, filterExpression, toolCallbacks,
                            mcpToolProcessMessageConsumer, null, null).call()
                            .chatClientResponse()).getResult();
        } finally {
            MDC.remove(MDC_CONVERSATION_ID);
            MDC.remove(MDC_USER_MESSAGE_ID);
        }
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

    private Optional<String> extractThinking(Generation generation) {
        return Optional.ofNullable(generation)
                .map(Generation::getOutput)
                .map(assistantMessage -> {
                    Map<String, Object> metadata = assistantMessage.getMetadata();
                    String directReasoning = Optional.ofNullable(metadata.get("reasoningContent"))
                            .map(Object::toString)
                            .filter(StringUtils::hasText)
                            .orElseGet(() -> Optional.ofNullable(generation.getMetadata().get("thinking"))
                                    .map(Object::toString)
                                    .filter(StringUtils::hasText)
                                    .orElse(null));
                    if (StringUtils.hasText(directReasoning)) {
                        return directReasoning;
                    }
                    return extractThinkingFromChunkChoice(metadata.get("chunkChoice")).orElse(null);
                })
                .filter(StringUtils::hasText);
    }

    private Optional<String> extractThinkingFromChunkChoice(Object chunkChoice) {
        if (!(chunkChoice instanceof ChatCompletionChunk.Choice choice)) {
            return Optional.empty();
        }
        return extractThinkingFromJsonMap(choice._additionalProperties())
                .or(() -> extractThinkingFromJsonMap(choice.delta()._additionalProperties()));
    }

    private Optional<String> extractThinkingFromJsonMap(Map<String, JsonValue> properties) {
        if (properties == null || properties.isEmpty()) {
            return Optional.empty();
        }
        return properties.entrySet().stream()
                .filter(entry -> REASONING_KEYS.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(this::extractTextFromJsonValue)
                .filter(StringUtils::hasText)
                .findFirst();
    }

    private String extractTextFromJsonValue(JsonValue jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        Optional<?> rawStringValue = jsonValue.asString();
        if (rawStringValue.isPresent()) {
            String stringValue = Objects.toString(rawStringValue.get(), null);
            if (StringUtils.hasText(stringValue)) {
                return stringValue;
            }
        }

        Optional<List<JsonValue>> arrayValue = jsonValue.asArray();
        if (arrayValue.isPresent()) {
            String joined = arrayValue.get().stream()
                    .map(this::extractTextFromJsonValue)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));
            if (StringUtils.hasText(joined)) {
                return joined;
            }
        }

        Optional<Map<String, JsonValue>> objectValue = jsonValue.asObject();
        return objectValue.flatMap(this::extractThinkingFromJsonMap)
                .orElse(null);
    }
}
