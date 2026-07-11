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

import org.slf4j.MDC;
import org.springaicommunity.playground.config.MdcIdentityFilter;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AgentLoopHarness {

    public static final String MCP_PROCESS_MESSAGE_CONSUMER = "mcpProcessMessageConsumer";
    public static final String MCP_TOOL_EXECUTION_COMPLETED_MESSAGE = "MCP tool execution completed.";
    public static final String TOOL_CONTEXT_USER_ID = "playgroundUserId";
    public static final String TOOL_CONTEXT_SESSION_ID = "playgroundSessionId";
    public static final String TOOL_CONTEXT_CONVERSATION_ID = "playgroundConversationId";

    private AgentLoopHarness() {
    }

    @SuppressWarnings("unchecked")
    static Consumer<Object> consumerFrom(Map<String, Object> toolContext) {
        return toolContext == null ? null : (Consumer<Object>) toolContext.get(MCP_PROCESS_MESSAGE_CONSUMER);
    }

    static void emitRoundStart(Consumer<Object> consumer, List<Message> instructions, List<ToolCall> toolCalls) {
        if (consumer == null) return;
        instructions.stream().filter(message -> message instanceof UserMessage)
                .reduce((first, second) -> second)
                .ifPresent(message -> consumer.accept(new McpUserMessage("user",
                        ((UserMessage) message).getText())));
        toolCalls.forEach(toolCall -> consumer.accept(new McpAssistantToolCall("assistant",
                List.of(new McpToolCall(toolCall.id(), toolCall.name(), toolCall.arguments())))));
    }

    static void emitRoundEnd(Consumer<Object> consumer, List<Message> conversationHistory) {
        if (consumer == null) return;
        consumer.accept(formatLastToolResult(conversationHistory));
        consumer.accept(MCP_TOOL_EXECUTION_COMPLETED_MESSAGE);
    }

    private static Object formatLastToolResult(List<Message> conversationHistory) {
        for (int i = conversationHistory.size() - 1; i >= 0; i--) {
            if (conversationHistory.get(i) instanceof ToolResponseMessage toolResponseMessage) {
                ToolResponseMessage.ToolResponse toolResponse = toolResponseMessage.getResponses().getLast();
                return new McpToolResult("tool", toolResponse.name(), toolResponse.id(),
                        toolResponse.responseData());
            }
        }
        Message last = conversationHistory.isEmpty() ? null : conversationHistory.getLast();
        return "MCP processing error: conversationHistory has no ToolResponseMessage. Last type: "
                + (last != null ? last.getClass().getName() : "null");
    }

    static IdentityScope identityScope(Map<String, Object> toolContext) {
        return new IdentityScope(toolContext);
    }

    static final class IdentityScope implements AutoCloseable {

        private final Map<String, String> previous = new LinkedHashMap<>();

        private IdentityScope(Map<String, Object> toolContext) {
            push(MdcIdentityFilter.USER_ID, toolContext.get(TOOL_CONTEXT_USER_ID));
            push(MdcIdentityFilter.SESSION_ID, toolContext.get(TOOL_CONTEXT_SESSION_ID));
            push(MdcIdentityFilter.CONVERSATION_ID, toolContext.get(TOOL_CONTEXT_CONVERSATION_ID));
        }

        private void push(String mdcKey, Object value) {
            if (!(value instanceof String s) || s.isBlank()) return;
            this.previous.put(mdcKey, MDC.get(mdcKey));
            MDC.put(mdcKey, s);
        }

        @Override
        public void close() {
            for (Map.Entry<String, String> entry : this.previous.entrySet()) {
                if (entry.getValue() == null) MDC.remove(entry.getKey());
                else MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public record McpUserMessage(String role, String content) {}

    public record McpAssistantToolCall(String role, List<McpToolCall> toolCalls) {}

    public record McpToolCall(String id, String name, Object arguments) {}

    public record McpToolResult(String role, String name, String id, Object responseData) {}
}
