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
import org.springframework.ai.chat.messages.Message;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopManagerUnknownToolTest {

    private final ToolCallingManager delegate = mock(ToolCallingManager.class);

    @SuppressWarnings("unchecked")
    private AgentLoopManager manager() {
        ObjectProvider<ToolSpecService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(ToolSpecService.class));
        return new AgentLoopManager(delegate, provider, new SimpleMeterRegistry(), 12_000);
    }

    private static ToolCallback callbackNamed(String name) {
        return FunctionToolCallback.builder(name, (Function<Map<String, Object>, Object>) arguments -> "ok")
                .description("test tool")
                .inputType(Map.class)
                .build();
    }

    private Prompt promptWithCallbacks(ToolCallback... callbacks) {
        return new Prompt(List.of(new UserMessage("do it")),
                ToolCallingChatOptions.builder().toolCallbacks(callbacks).toolContext(Map.of()).build());
    }

    private ChatResponse responseWith(ToolCall... toolCalls) {
        AssistantMessage assistant = AssistantMessage.builder().content("").toolCalls(List.of(toolCalls)).build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private void stubDelegatePassThrough() {
        List<Message> history = List.of(new UserMessage("do it"),
                ToolResponseMessage.builder().responses(List.of()).build());
        when(delegate.executeToolCalls(any(), any()))
                .thenReturn(ToolExecutionResult.builder().conversationHistory(history).build());
    }

    @Test
    void testUnknownToolCallGetsGuardCallbackInsteadOfCrashing() {
        stubDelegatePassThrough();
        Prompt prompt = promptWithCallbacks(callbackNamed("spring_ai_playground_listDir"));
        ChatResponse response = responseWith(new ToolCall("1", "function", "listDir", "{}"));

        manager().executeToolCalls(prompt, response);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(delegate).executeToolCalls(captor.capture(), any());
        ToolCallingChatOptions options = (ToolCallingChatOptions) captor.getValue().getOptions();
        ToolCallback guard = options.getToolCallbacks().stream()
                .filter(callback -> "listDir".equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String reply = guard.call("{}");
        assertTrue(reply.contains("Unknown tool 'listDir'"));
        assertTrue(reply.contains("Did you mean 'spring_ai_playground_listDir'"));
    }

    @Test
    void testUnknownToolWithoutSimilarNameGetsPlainHint() {
        stubDelegatePassThrough();
        Prompt prompt = promptWithCallbacks(callbackNamed("renderTable"));
        ChatResponse response = responseWith(new ToolCall("1", "function", "imaginaryTool", "{}"));

        manager().executeToolCalls(prompt, response);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(delegate).executeToolCalls(captor.capture(), any());
        ToolCallingChatOptions options = (ToolCallingChatOptions) captor.getValue().getOptions();
        ToolCallback guard = options.getToolCallbacks().stream()
                .filter(callback -> "imaginaryTool".equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
        String reply = guard.call("{}");
        assertTrue(reply.contains("not exposed to this chat"));
        assertTrue(reply.contains("Use only the tools listed"));
    }

    @Test
    void testKnownToolCallLeavesPromptUntouched() {
        stubDelegatePassThrough();
        Prompt prompt = promptWithCallbacks(callbackNamed("renderTable"));
        ChatResponse response = responseWith(new ToolCall("1", "function", "renderTable", "{}"));

        manager().executeToolCalls(prompt, response);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(delegate).executeToolCalls(captor.capture(), any());
        assertSame(prompt, captor.getValue());
        ToolCallingChatOptions options = (ToolCallingChatOptions) captor.getValue().getOptions();
        assertEquals(1, options.getToolCallbacks().size());
    }

    // A hallucinated call with zero registered tools used to crash in the delegate; now it gets
    // the same guard callback as any other unknown name.
    @Test
    void testPromptWithoutCallbacksStillGetsTheGuard() {
        stubDelegatePassThrough();
        Prompt prompt = new Prompt(List.of(new UserMessage("do it")),
                ToolCallingChatOptions.builder().toolContext(Map.of()).build());
        ChatResponse response = responseWith(new ToolCall("1", "function", "anything", "{}"));

        manager().executeToolCalls(prompt, response);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(delegate).executeToolCalls(captor.capture(), any());
        ToolCallingChatOptions options = (ToolCallingChatOptions) captor.getValue().getOptions();
        ToolCallback guard = options.getToolCallbacks().stream()
                .filter(callback -> "anything".equals(callback.getToolDefinition().name()))
                .findFirst().orElseThrow();
        assertTrue(guard.call("{}").contains("Unknown tool 'anything'"));
    }
}
