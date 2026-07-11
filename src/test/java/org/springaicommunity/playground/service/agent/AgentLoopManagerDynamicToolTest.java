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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopManagerDynamicToolTest {

    private final ToolCallingManager delegate = mock(ToolCallingManager.class);

    @SuppressWarnings("unchecked")
    private AgentLoopManager manager() {
        ObjectProvider<ToolSpecService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new AgentLoopManager(delegate, provider, new SimpleMeterRegistry(), 12_000);
    }

    private Prompt promptWith(Map<String, Object> toolContext, ToolCallback... offered) {
        return new Prompt(List.of(new UserMessage("do it")), ToolCallingChatOptions.builder()
                .toolCallbacks(offered).toolContext(toolContext).build());
    }

    private ChatResponse responseCalling(String toolName) {
        AssistantMessage assistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(new ToolCall("1", "function", toolName, "{}"))).build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private ToolCallback tool(String name) {
        return FunctionToolCallback.builder(name, (Function<Map<String, Object>, Object>) args -> "OK:" + name)
                .description(name).inputType(new ParameterizedTypeReference<Map<String, Object>>() {}).build();
    }

    private List<String> offeredToolNames(Prompt prompt) {
        return ((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks().stream()
                .map(callback -> callback.getToolDefinition().name()).toList();
    }

    private Prompt captureDelegatedPrompt() {
        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(delegate).executeToolCalls(captor.capture(), any());
        return captor.getValue();
    }

    private void stubDelegate() {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("1", "x", "OK"))).build()))
                .build());
    }

    @Test
    void directlyCalledPoolToolIsRegisteredForTheRound() {
        stubDelegate();
        ToolCallback weather = tool("getWeather");
        Map<String, Object> toolContext = Map.of(AgentLoopManager.DYNAMIC_TOOL_POOL,
                Map.of("getWeather", weather));

        manager().executeToolCalls(promptWith(toolContext), responseCalling("getWeather"));

        assertThat(offeredToolNames(captureDelegatedPrompt())).contains("getWeather");
    }

    @Test
    void unknownToolGetsPlaceholderInsteadOfCrashing() {
        stubDelegate();
        Map<String, Object> toolContext = Map.of(AgentLoopManager.DYNAMIC_TOOL_POOL, Map.of());

        manager().executeToolCalls(promptWith(toolContext), responseCalling("frobnicate"));

        Prompt delegated = captureDelegatedPrompt();
        assertThat(offeredToolNames(delegated)).contains("frobnicate");
        ToolCallback placeholder = ((ToolCallingChatOptions) delegated.getOptions()).getToolCallbacks().stream()
                .filter(callback -> callback.getToolDefinition().name().equals("frobnicate")).findFirst().orElseThrow();
        assertThat(placeholder.call("{}")).contains("toolSearchTool");
    }

    @Test
    void alreadyOfferedToolIsNotDuplicated() {
        stubDelegate();
        ToolCallback weather = tool("getWeather");
        Map<String, Object> toolContext = Map.of(AgentLoopManager.DYNAMIC_TOOL_POOL,
                Map.of("getWeather", weather));

        manager().executeToolCalls(promptWith(toolContext, weather), responseCalling("getWeather"));

        assertThat(offeredToolNames(captureDelegatedPrompt())).containsExactly("getWeather");
    }

    @Test
    void withoutDynamicPoolUnknownCallGetsTheGuardCallback() {
        stubDelegate();
        Prompt prompt = promptWith(Map.of(), tool("getTime"));
        ChatResponse response = responseCalling("getWeather");

        manager().executeToolCalls(prompt, response);

        Prompt delegated = captureDelegatedPrompt();
        assertThat(offeredToolNames(delegated)).containsExactly("getTime", "getWeather");
        ToolCallback guard = ((ToolCallingChatOptions) delegated.getOptions()).getToolCallbacks().stream()
                .filter(callback -> callback.getToolDefinition().name().equals("getWeather")).findFirst().orElseThrow();
        assertThat(guard.call("{}")).contains("not exposed to this chat");
    }

    private void stubDelegateOutput(String toolName, String data) {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("1", toolName, data))).build()))
                .returnDirect(false).build());
    }

    @Test
    void terminalActionOutputSetsReturnDirect() {
        stubDelegateOutput("showLocation", "Shown.\n\n```saip-action-return-direct\n{\"type\":\"map\"}\n```");
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("showLocation"));
        assertThat(result.returnDirect()).isTrue();
    }

    @Test
    void plainActionOutputDoesNotSetReturnDirect() {
        stubDelegateOutput("lookup", "Here is data.\n\n```saip-action\n{\"type\":\"info\"}\n```");
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("lookup"));
        assertThat(result.returnDirect()).isFalse();
    }

    @Test
    void alreadyReturnDirectWithoutMarkerStaysReturnDirect() {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("1", "x", "plain data"))).build()))
                .returnDirect(true).build());
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("x"));
        assertThat(result.returnDirect()).isTrue();
    }

    @Test
    void mixedBatchWithTerminalMarkerDoesNotEndTurn() {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(ToolResponseMessage.builder().responses(List.of(
                        new ToolResponseMessage.ToolResponse("1", "getWeather", "sunny"),
                        new ToolResponseMessage.ToolResponse("2", "showLocation",
                                "Shown.\n\n```saip-action-return-direct\n{\"type\":\"map\"}\n```"))).build()))
                .returnDirect(false).build());
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("getWeather"));
        assertThat(result.returnDirect()).isFalse();
    }

    @Test
    void mcpWrappedTerminalOutputIsUnwrappedForDisplay() {
        String wrapped = "[{\"type\":\"text\",\"text\":\"Shown.\\n\\n```saip-action-return-direct\\n"
                + "{\\\"type\\\":\\\"map\\\"}\\n```\"}]";
        stubDelegateOutput("showLocation", wrapped);
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("showLocation"));
        assertThat(result.returnDirect()).isTrue();
        String data = ((ToolResponseMessage) result.conversationHistory().getLast())
                .getResponses().get(0).responseData();
        assertThat(data).startsWith("Shown.\n\n```saip-action-return-direct").doesNotStartWith("[");
    }

    @Test
    void nonToolResponseLastMessageLeavesReturnDirectUnchanged() {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(new AssistantMessage("done"))).returnDirect(false).build());
        ToolExecutionResult result = manager().executeToolCalls(promptWith(Map.of()), responseCalling("x"));
        assertThat(result.returnDirect()).isFalse();
    }
}
