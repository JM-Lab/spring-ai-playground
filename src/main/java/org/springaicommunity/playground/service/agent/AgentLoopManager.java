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
package org.springaicommunity.playground.service.agent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.agent.AgentRoundInterceptor.Interception;
import org.springaicommunity.playground.service.agent.AgentTurn.RoundVerdict;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class AgentLoopManager implements ToolCallingManager {

    public static final String DYNAMIC_TOOL_POOL = "playgroundDynamicToolPool";
    static final String ACTION_RETURN_DIRECT_MARKER = "```saip-action-return-direct";

    private static final Logger logger = LoggerFactory.getLogger(AgentLoopManager.class);
    private static final ObjectMapper ACTION_MAPPER = new ObjectMapper();
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() {};

    private final ToolCallingManager toolCallingManager;
    private final List<AgentRoundInterceptor> interceptors;
    private final int toolResultMaxChars;

    @Autowired
    public AgentLoopManager(ObservationRegistry observationRegistry,
            ObjectProvider<ToolSpecService> toolSpecServiceProvider, MeterRegistry meterRegistry,
            SpringAiPlaygroundOptions options) {
        this(ToolCallingManager.builder().observationRegistry(observationRegistry).build(),
                toolSpecServiceProvider, meterRegistry,
                options.chat() == null ? 12_000 : options.chat().toolResultMaxChars());
    }

    AgentLoopManager(ToolCallingManager delegate, ObjectProvider<ToolSpecService> toolSpecServiceProvider,
            MeterRegistry meterRegistry, int toolResultMaxChars) {
        this.toolCallingManager = delegate;
        this.toolResultMaxChars = toolResultMaxChars;
        this.interceptors = List.of(
                new LoopGuardInterceptor(meterRegistry),
                new HitlApprovalInterceptor(toolSpecServiceProvider, meterRegistry),
                new FileUploadInterceptor(meterRegistry),
                new ImageReferenceInterceptor());
    }

    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        return this.toolCallingManager.resolveToolDefinitions(chatOptions);
    }

    @Override
    public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ToolCallingChatOptions toolCallingChatOptions = (ToolCallingChatOptions) prompt.getOptions();
        prompt = resolveDynamicToolCalls(prompt, chatResponse, toolCallingChatOptions);
        prompt = guardUnknownToolCalls(prompt, chatResponse);
        Map<String, Object> toolContext = toolCallingChatOptions.getToolContext();
        Consumer<Object> processMessageConsumer = AgentLoopHarness.consumerFrom(toolContext);
        List<ToolCall> toolCalls = chatResponse.getResults().stream()
                .flatMap(result -> result.getOutput().getToolCalls().stream()).toList();
        AgentLoopHarness.emitRoundStart(processMessageConsumer, prompt.getInstructions(), toolCalls);

        AgentTurn turn = AgentTurn.from(toolContext);
        RoundVerdict verdict = turn.beginRound();
        Map<String, Interception> intercepted = new LinkedHashMap<>();
        List<ToolCall> remaining = new ArrayList<>(toolCalls);
        for (AgentRoundInterceptor interceptor : this.interceptors) {
            if (remaining.isEmpty()) break;
            intercepted.putAll(safeIntercept(interceptor, List.copyOf(remaining), turn, toolContext));
            remaining.removeIf(call -> intercepted.containsKey(call.id()));
        }

        ToolExecutionResult rawResult = intercepted.isEmpty()
                ? executeAll(prompt, chatResponse, toolContext)
                : executeWithIntercepted(prompt, chatResponse, intercepted, toolContext);
        ToolExecutionResult result = applyActionReturnDirect(truncateOversizedResponses(rawResult));
        if (verdict == RoundVerdict.HARD_STOP || verdict == RoundVerdict.CANCELLED)
            result = forceReturnDirect(result);
        AgentLoopHarness.emitRoundEnd(processMessageConsumer, result.conversationHistory());
        return result;
    }

    private Map<String, Interception> safeIntercept(AgentRoundInterceptor interceptor, List<ToolCall> calls,
            AgentTurn turn, Map<String, Object> toolContext) {
        try {
            Map<String, Interception> claims = interceptor.intercept(calls, turn, toolContext);
            return claims == null ? Map.of() : claims;
        } catch (RuntimeException e) {
            logger.warn("agent.round.interceptor-failed interceptor={} error={}",
                    interceptor.getClass().getSimpleName(), e.getMessage());
            if (!(interceptor instanceof HitlApprovalInterceptor)) return Map.of();
            // A broken approval gate must fail closed: nothing it was inspecting may execute.
            Map<String, Interception> declined = new LinkedHashMap<>();
            for (ToolCall call : calls) {
                declined.put(call.id(), Interception.of(AgentTurnMessages.declined(call.name())));
            }
            return declined;
        }
    }

    private ToolExecutionResult executeAll(Prompt prompt, ChatResponse chatResponse,
            Map<String, Object> toolContext) {
        try (AgentLoopHarness.IdentityScope scope = AgentLoopHarness.identityScope(toolContext)) {
            return this.toolCallingManager.executeToolCalls(prompt, chatResponse);
        }
    }

    private ToolExecutionResult executeWithIntercepted(Prompt prompt, ChatResponse chatResponse,
            Map<String, Interception> intercepted, Map<String, Object> toolContext) {
        AssistantMessage assistantMessage = chatResponse.getResults().stream()
                .map(Generation::getOutput)
                .filter(output -> !output.getToolCalls().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tool call requested by the chat model"));
        List<ToolCall> allToolCalls = assistantMessage.getToolCalls();
        List<ToolCall> approvedToolCalls = allToolCalls.stream()
                .filter(toolCall -> !intercepted.containsKey(toolCall.id())).toList();

        Map<String, ToolResponseMessage.ToolResponse> responsesById = new LinkedHashMap<>();
        if (!approvedToolCalls.isEmpty()) {
            AssistantMessage approvedAssistant = AssistantMessage.builder()
                    .content(assistantMessage.getText())
                    .properties(assistantMessage.getMetadata())
                    .toolCalls(approvedToolCalls)
                    .build();
            ToolExecutionResult approvedResult;
            try (AgentLoopHarness.IdentityScope scope = AgentLoopHarness.identityScope(toolContext)) {
                approvedResult = this.toolCallingManager.executeToolCalls(prompt,
                        new ChatResponse(List.of(new Generation(approvedAssistant))));
            }
            Message last = approvedResult.conversationHistory().getLast();
            if (last instanceof ToolResponseMessage toolResponseMessage) {
                toolResponseMessage.getResponses().forEach(response -> responsesById.put(response.id(), response));
            }
        }
        for (ToolCall toolCall : allToolCalls) {
            Interception interception = intercepted.get(toolCall.id());
            if (interception != null) {
                responsesById.put(toolCall.id(), new ToolResponseMessage.ToolResponse(
                        toolCall.id(), toolCall.name(), interception.response()));
            }
        }

        List<ToolResponseMessage.ToolResponse> orderedResponses = allToolCalls.stream()
                .map(toolCall -> responsesById.get(toolCall.id()))
                .filter(Objects::nonNull).toList();
        List<Message> conversationHistory = new ArrayList<>(prompt.getInstructions());
        conversationHistory.add(assistantMessage);
        conversationHistory.add(ToolResponseMessage.builder().responses(orderedResponses).build());
        for (ToolCall toolCall : allToolCalls) {
            Interception interception = intercepted.get(toolCall.id());
            if (interception != null && interception.followUp() != null)
                conversationHistory.add(interception.followUp());
        }
        return ToolExecutionResult.builder().conversationHistory(conversationHistory).build();
    }

    @SuppressWarnings("unchecked")
    private Prompt resolveDynamicToolCalls(Prompt prompt, ChatResponse chatResponse,
            ToolCallingChatOptions toolCallingChatOptions) {
        Map<String, ToolCallback> dynamicPool =
                (Map<String, ToolCallback>) toolCallingChatOptions.getToolContext().get(DYNAMIC_TOOL_POOL);
        if (dynamicPool == null) return prompt;
        Set<String> available = new HashSet<>();
        for (ToolCallback callback : toolCallingChatOptions.getToolCallbacks())
            available.add(callback.getToolDefinition().name());
        List<ToolCallback> additions = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        for (ToolCall toolCall : chatResponse.getResults().stream()
                .flatMap(result -> result.getOutput().getToolCalls().stream()).toList()) {
            String name = toolCall.name();
            if (available.contains(name) || !handled.add(name)) continue;
            ToolCallback pooled = dynamicPool.get(name);
            additions.add(pooled != null ? pooled : notLoadedCallback(name));
        }
        return withAdditionalCallbacks(prompt, toolCallingChatOptions, additions);
    }

    // An unresolvable tool name would throw in the delegate and kill the stream; answer it with a
    // recovery hint instead.
    private Prompt guardUnknownToolCalls(Prompt prompt, ChatResponse chatResponse) {
        ToolCallingChatOptions options = (ToolCallingChatOptions) prompt.getOptions();
        List<ToolCallback> callbacks = options.getToolCallbacks() == null ? List.of()
                : options.getToolCallbacks();
        Set<String> available = new HashSet<>();
        for (ToolCallback callback : callbacks)
            available.add(callback.getToolDefinition().name());
        List<ToolCallback> additions = new ArrayList<>();
        Set<String> handled = new HashSet<>();
        for (ToolCall toolCall : chatResponse.getResults().stream()
                .flatMap(result -> result.getOutput().getToolCalls().stream()).toList()) {
            String name = toolCall.name();
            if (available.contains(name) || !handled.add(name)) continue;
            logger.warn("tool.call.unknown tool={}", name);
            additions.add(unknownToolCallback(name, available));
        }
        return withAdditionalCallbacks(prompt, options, additions);
    }

    private static Prompt withAdditionalCallbacks(Prompt prompt, ToolCallingChatOptions options,
            List<ToolCallback> additions) {
        if (additions.isEmpty()) return prompt;
        List<ToolCallback> merged = options.getToolCallbacks() == null ? new ArrayList<>()
                : new ArrayList<>(options.getToolCallbacks());
        merged.addAll(additions);
        ToolCallingChatOptions augmented = ((ToolCallingChatOptions.Builder<?>) options.mutate())
                .toolCallbacks(merged).build();
        return prompt.mutate().chatOptions(augmented).build();
    }

    private static ToolCallback notLoadedCallback(String toolName) {
        return FunctionToolCallback.builder(toolName, (Function<Map<String, Object>, Object>) arguments ->
                        "Tool '" + toolName + "' is not loaded. Call toolSearchTool with a short query to discover "
                                + "the tools you need, then call the tool by its exact name.")
                .description("Discoverable tool that has not been loaded yet")
                .inputType(MAP_TYPE)
                .build();
    }

    private static ToolCallback unknownToolCallback(String toolName, Set<String> available) {
        String closest = available.stream().filter(name -> name.equalsIgnoreCase(toolName)
                || name.endsWith("_" + toolName) || name.endsWith("." + toolName)).findFirst().orElse(null);
        String hint = closest == null
                ? "Use only the tools listed for this conversation."
                : "Did you mean '" + closest + "'? Call it by that exact name.";
        return FunctionToolCallback.builder(toolName, (Function<Map<String, Object>, Object>) arguments ->
                        "Unknown tool '" + toolName + "' - it is not exposed to this chat. " + hint)
                .description("Guard for a tool name that is not exposed to this chat")
                .inputType(MAP_TYPE)
                .build();
    }

    private ToolExecutionResult truncateOversizedResponses(ToolExecutionResult result) {
        if (this.toolResultMaxChars <= 0) return result;
        List<Message> history = result.conversationHistory();
        int index = lastToolResponseIndex(history);
        if (index < 0) return result;
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) history.get(index);
        boolean changed = false;
        List<ToolResponseMessage.ToolResponse> capped = new ArrayList<>();
        for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
            String data = response.responseData();
            if (data == null || data.length() <= this.toolResultMaxChars) {
                capped.add(response);
                continue;
            }
            changed = true;
            logger.info("tool.result.truncated tool={} chars={} max={}", response.name(), data.length(),
                    this.toolResultMaxChars);
            capped.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(),
                    truncate(data, this.toolResultMaxChars)));
        }
        if (!changed) return result;
        List<Message> history2 = new ArrayList<>(history);
        history2.set(index, ToolResponseMessage.builder().responses(capped).build());
        return ToolExecutionResult.builder().conversationHistory(history2)
                .returnDirect(result.returnDirect()).build();
    }

    private static int lastToolResponseIndex(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof ToolResponseMessage) return i;
        }
        return -1;
    }

    private ToolExecutionResult applyActionReturnDirect(ToolExecutionResult result) {
        if (result.returnDirect() || result.conversationHistory().isEmpty()
                || !(result.conversationHistory().getLast() instanceof ToolResponseMessage toolResponseMessage))
            return result;
        List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
        boolean terminal = responses.size() == 1 && responses.get(0).responseData() != null
                && responses.get(0).responseData().contains(ACTION_RETURN_DIRECT_MARKER);
        if (!terminal) return result;
        ToolResponseMessage.ToolResponse only = responses.get(0);
        List<Message> history = new ArrayList<>(result.conversationHistory());
        history.set(history.size() - 1, ToolResponseMessage.builder().responses(List.of(
                new ToolResponseMessage.ToolResponse(only.id(), only.name(),
                        unwrapActionContent(only.responseData())))).build());
        return ToolExecutionResult.builder().conversationHistory(history).returnDirect(true).build();
    }

    private static ToolExecutionResult forceReturnDirect(ToolExecutionResult result) {
        if (result.returnDirect()) return result;
        return ToolExecutionResult.builder().conversationHistory(result.conversationHistory())
                .returnDirect(true).build();
    }

    static String unwrapActionContent(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{") && !trimmed.startsWith("\"")) return raw;
        try {
            JsonNode node = ACTION_MAPPER.readTree(trimmed);
            if (node.isTextual()) return node.asText();
            if (node.isObject() && node.has("text")) return node.get("text").asText();
            if (node.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode el : node) {
                    sb.append(el.isObject() && el.has("text") ? el.get("text").asText() : el.asText());
                }
                if (!sb.isEmpty()) return sb.toString();
            }
        } catch (RuntimeException ignore) {
        }
        return raw;
    }

    private static String truncate(String data, int max) {
        return data.substring(0, max) + "\n...[truncated " + (data.length() - max) + " of " + data.length()
                + " chars]";
    }
}
