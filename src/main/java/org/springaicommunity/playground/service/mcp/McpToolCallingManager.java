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
package org.springaicommunity.playground.service.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.MDC;
import org.springaicommunity.playground.config.MdcIdentityFilter;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.springaicommunity.playground.service.tool.ToolManifest;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class McpToolCallingManager implements ToolCallingManager {

    public static final String MCP_PROCESS_MESSAGE_CONSUMER = "mcpProcessMessageConsumer";
    public static final String MCP_TOOL_EXECUTION_COMPLETED_MESSAGE = "MCP tool execution completed.";
    public static final String TOOL_CONTEXT_USER_ID = "playgroundUserId";
    public static final String TOOL_CONTEXT_SESSION_ID = "playgroundSessionId";

    private static final Logger logger = LoggerFactory.getLogger(McpToolCallingManager.class);

    private final ToolCallingManager toolCallingManager;
    private final ObjectProvider<ToolSpecService> toolSpecServiceProvider;
    private final MeterRegistry meterRegistry;

    @Autowired
    public McpToolCallingManager(ObservationRegistry observationRegistry,
            ObjectProvider<ToolSpecService> toolSpecServiceProvider, MeterRegistry meterRegistry) {
        this(ToolCallingManager.builder().observationRegistry(observationRegistry).build(),
                toolSpecServiceProvider, meterRegistry);
    }

    McpToolCallingManager(ToolCallingManager delegate, ObjectProvider<ToolSpecService> toolSpecServiceProvider,
            MeterRegistry meterRegistry) {
        this.toolCallingManager = delegate;
        this.toolSpecServiceProvider = toolSpecServiceProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return this.toolCallingManager.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ToolCallingChatOptions toolCallingChatOptions = (ToolCallingChatOptions) prompt.getOptions();
        Optional<Consumer<Object>> mcpProcessMessageConsumerAsOpt = Optional.ofNullable(
                (Consumer<Object>) toolCallingChatOptions.getToolContext().get(MCP_PROCESS_MESSAGE_CONSUMER));

        if (mcpProcessMessageConsumerAsOpt.isPresent()) {
            Consumer<Object> mcpProcessMessageConsumer = mcpProcessMessageConsumerAsOpt.get();
            prompt.getInstructions().stream().filter(m -> m instanceof UserMessage).reduce((first, second) -> second)
                    .ifPresent(
                            msg -> mcpProcessMessageConsumer.accept(formatUserMessageForMcp((UserMessage) msg)));
            chatResponse.getResults().stream()
                    .flatMap(result -> result.getOutput().getToolCalls().stream())
                    .forEach(toolCall -> mcpProcessMessageConsumer.accept(formatToolCallForMcp(toolCall)));
        }
        Set<String> declinedToolCallIds = resolveDeclinedToolCalls(chatResponse,
                toolCallingChatOptions.getToolContext());
        // Restore identity so the per-tool observation keeps user/session after the reactive chain cleared MDC.
        Map<String, String> previousIdentity = pushIdentity(toolCallingChatOptions.getToolContext());
        ToolExecutionResult result;
        try {
            result = declinedToolCallIds.isEmpty()
                    ? toolCallingManager.executeToolCalls(prompt, chatResponse)
                    : executeWithDeclined(prompt, chatResponse, declinedToolCallIds);
        } finally {
            popIdentity(previousIdentity);
        }
        mcpProcessMessageConsumerAsOpt.ifPresent(consumer -> {
            consumer.accept(formatToolResultForMcp(result.conversationHistory().getLast()));
            consumer.accept(MCP_TOOL_EXECUTION_COMPLETED_MESSAGE);
        });
        return result;
    }

    private static Map<String, String> pushIdentity(Map<String, Object> toolContext) {
        Map<String, String> previous = new LinkedHashMap<>();
        putIdentity(previous, MdcIdentityFilter.USER_ID, toolContext.get(TOOL_CONTEXT_USER_ID));
        putIdentity(previous, MdcIdentityFilter.SESSION_ID, toolContext.get(TOOL_CONTEXT_SESSION_ID));
        return previous;
    }

    private static void putIdentity(Map<String, String> previous, String mdcKey, Object value) {
        if (!(value instanceof String s) || s.isBlank()) return;
        previous.put(mdcKey, MDC.get(mdcKey));
        MDC.put(mdcKey, s);
    }

    private static void popIdentity(Map<String, String> previous) {
        for (Map.Entry<String, String> e : previous.entrySet()) {
            if (e.getValue() == null) MDC.remove(e.getKey());
            else MDC.put(e.getKey(), e.getValue());
        }
    }

    private Set<String> resolveDeclinedToolCalls(ChatResponse chatResponse, Map<String, Object> toolContext) {
        HumanQuestionHandler handler = toolContext == null ? null
                : (HumanQuestionHandler) toolContext.get(HumanQuestionHandler.TOOL_CONTEXT_KEY);
        ToolSpecService toolSpecService = this.toolSpecServiceProvider.getIfAvailable();
        if (handler == null || toolSpecService == null) return Set.of();

        List<ToolCall> toolCalls = chatResponse.getResults().stream()
                .flatMap(result -> result.getOutput().getToolCalls().stream()).toList();
        List<HumanQuestion> questions = new ArrayList<>();
        Map<String, ToolCall> questionToCall = new LinkedHashMap<>();
        for (ToolCall toolCall : toolCalls) {
            if (toolSpecService.requiresApproval(toolCall.name())) {
                String question = approvalQuestion(toolSpecService.humanInTheLoopFor(toolCall.name()), toolCall);
                questions.add(HumanQuestion.approval("Tool approval required", question));
                questionToCall.put(question, toolCall);
            }
        }
        if (questions.isEmpty()) return Set.of();

        Map<String, String> answers;
        try {
            answers = handler.ask(questions);
        } catch (RuntimeException e) {
            logger.warn("hitl.ask-failed error={}", e.getMessage());
            countDecision("ask-failed");
            answers = Map.of();
        }
        Set<String> declined = new HashSet<>();
        for (Map.Entry<String, ToolCall> entry : questionToCall.entrySet()) {
            String answer = answers == null ? null : answers.get(entry.getKey());
            if (!"Approve".equalsIgnoreCase(answer)) {
                declined.add(entry.getValue().id());
                logger.info("hitl.declined tool={}", entry.getValue().name());
                countDecision("declined");
            } else {
                logger.info("hitl.approved tool={}", entry.getValue().name());
                countDecision("approved");
            }
        }
        return declined;
    }

    private void countDecision(String outcome) {
        this.meterRegistry.counter("mcp.hitl.decision", "outcome", outcome, "side", "chat").increment();
    }

    private ToolExecutionResult executeWithDeclined(Prompt prompt, ChatResponse chatResponse,
            Set<String> declinedToolCallIds) {
        AssistantMessage assistantMessage = chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> !output.getToolCalls().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tool call requested by the chat model"));
        List<ToolCall> allToolCalls = assistantMessage.getToolCalls();
        List<ToolCall> approvedToolCalls = allToolCalls.stream()
                .filter(toolCall -> !declinedToolCallIds.contains(toolCall.id())).toList();

        Map<String, ToolResponseMessage.ToolResponse> responsesById = new LinkedHashMap<>();
        if (!approvedToolCalls.isEmpty()) {
            AssistantMessage approvedAssistant = AssistantMessage.builder()
                    .content(assistantMessage.getText())
                    .properties(assistantMessage.getMetadata())
                    .toolCalls(approvedToolCalls)
                    .build();
            ToolExecutionResult approvedResult = this.toolCallingManager.executeToolCalls(prompt,
                    new ChatResponse(List.of(new Generation(approvedAssistant))));
            Message last = approvedResult.conversationHistory().getLast();
            if (last instanceof ToolResponseMessage toolResponseMessage) {
                toolResponseMessage.getResponses().forEach(response -> responsesById.put(response.id(), response));
            }
        }
        for (ToolCall toolCall : allToolCalls) {
            if (declinedToolCallIds.contains(toolCall.id())) {
                responsesById.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), declinedMessage(toolCall.name())));
            }
        }

        List<ToolResponseMessage.ToolResponse> orderedResponses = allToolCalls.stream()
                .map(toolCall -> responsesById.get(toolCall.id()))
                .filter(Objects::nonNull).toList();
        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(assistantMessage);
        conversationHistory.add(ToolResponseMessage.builder().responses(orderedResponses).build());
        return ToolExecutionResult.builder().conversationHistory(conversationHistory).build();
    }

    private static String approvalQuestion(ToolManifest.HumanInTheLoop humanInTheLoop, ToolCall toolCall) {
        String promptTemplate = humanInTheLoop == null ? null : humanInTheLoop.promptTemplate();
        String template = (promptTemplate == null || promptTemplate.isBlank())
                ? "Run '{toolName}' with arguments {args}?" : promptTemplate;
        return template.replace("{toolName}", toolCall.name()).replace("{args}", toolCall.arguments());
    }

    private static String declinedMessage(String toolName) {
        return "The user declined to approve running the tool '" + toolName + "'. It was NOT executed. "
                + "Do not call '" + toolName + "' again for this request. If another available tool can accomplish "
                + "the goal, use it instead; otherwise tell the user the action could not be completed because they "
                + "declined approval.";
    }

    private Object formatUserMessageForMcp(UserMessage msg) {
        return new McpUserMessage("user", msg.getText());
    }

    private Object formatToolCallForMcp(ToolCall toolCall) {
        return new McpAssistantToolCall("assistant",
                List.of(new McpToolCall(toolCall.id(), toolCall.name(), toolCall.arguments()))
        );
    }

    private Object formatToolResultForMcp(Message lastMessage) {
        if (lastMessage instanceof ToolResponseMessage toolResponseMessage) {
            ToolResponseMessage.ToolResponse toolResponse = toolResponseMessage.getResponses().getLast();
            return new McpToolResult("tool", toolResponse.name(), toolResponse.id(), toolResponse.responseData()
            );
        } else {
            return "MCP processing error: conversationHistory last message is not ToolResponseMessage. Actual type: " +
                    (lastMessage != null ? lastMessage.getClass().getName() : "null");
        }
    }

    public record McpUserMessage(String role, String content) {}

    public record McpAssistantToolCall(String role, List<McpToolCall> toolCalls) {}

    public record McpToolCall(String id, String name, Object arguments) {}

    public record McpToolResult(String role, String name, String id, Object responseData) {}

}
