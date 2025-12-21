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
package jm.kr.spring.ai.playground.service.mcp;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Component
public class McpToolCallingManager implements ToolCallingManager {

    public static final String MCP_PROCESS_MESSAGE_CONSUMER = "mcpProcessMessageConsumer";
    private final ToolCallingManager toolCallingManager;

    public McpToolCallingManager() {
        this.toolCallingManager = ToolCallingManager.builder().build();
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
        ToolExecutionResult result = toolCallingManager.executeToolCalls(prompt, chatResponse);
        mcpProcessMessageConsumerAsOpt.ifPresent(
                consumer -> consumer.accept(formatToolResultForMcp(result.conversationHistory().getLast())));
        return result;
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