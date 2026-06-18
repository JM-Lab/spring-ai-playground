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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.springaicommunity.playground.service.tool.ToolManifest;
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
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolCallingManagerHitlTest {

    private final ToolCallingManager delegate = mock(ToolCallingManager.class);
    private final ToolSpecService toolSpecService = mock(ToolSpecService.class);

    private McpToolCallingManager manager() {
        return manager(12_000);
    }

    @SuppressWarnings("unchecked")
    private McpToolCallingManager manager(int toolResultMaxChars) {
        ObjectProvider<ToolSpecService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(toolSpecService);
        return new McpToolCallingManager(delegate, provider, new SimpleMeterRegistry(), toolResultMaxChars);
    }

    private Prompt promptWith(HumanQuestionHandler handler) {
        Map<String, Object> toolContext = handler == null ? Map.of()
                : Map.of(HumanQuestionHandler.TOOL_CONTEXT_KEY, handler);
        return new Prompt(List.of(new UserMessage("do it")),
                ToolCallingChatOptions.builder().toolContext(toolContext).build());
    }

    private ChatResponse responseWith(ToolCall... toolCalls) {
        AssistantMessage assistant = AssistantMessage.builder().content("").toolCalls(List.of(toolCalls)).build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private ToolCall toolCall(String id, String name) {
        return new ToolCall(id, "function", name, "{}");
    }

    private ToolExecutionResult delegateResultFor(ToolCall... toolCalls) {
        AssistantMessage assistant = AssistantMessage.builder().content("").toolCalls(List.of(toolCalls)).build();
        List<ToolResponseMessage.ToolResponse> responses = List.of(toolCalls).stream()
                .map(tc -> new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), "OK:" + tc.name())).toList();
        List<Message> history = List.of(new UserMessage("do it"), assistant,
                ToolResponseMessage.builder().responses(responses).build());
        return ToolExecutionResult.builder().conversationHistory(history).build();
    }

    @Test
    void noHandlerPassesThrough() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(null);
        ToolExecutionResult expected = delegateResultFor(toolCall("1", "writeFile"));
        when(delegate.executeToolCalls(prompt, response)).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
        verify(delegate).executeToolCalls(prompt, response);
    }

    @Test
    void toolNotRequiringApprovalPassesThrough() {
        ChatResponse response = responseWith(toolCall("1", "getTime"));
        Prompt prompt = promptWith(questions -> Map.of());
        when(toolSpecService.requiresApproval("getTime")).thenReturn(false);
        ToolExecutionResult expected = delegateResultFor(toolCall("1", "getTime"));
        when(delegate.executeToolCalls(prompt, response)).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
    }

    @Test
    void approvedToolRunsViaDelegate() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Approve"));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);
        ToolExecutionResult expected = delegateResultFor(toolCall("1", "writeFile"));
        when(delegate.executeToolCalls(prompt, response)).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
        verify(delegate).executeToolCalls(prompt, response);
    }

    @Test
    void declinedToolIsNotExecutedAndYieldsDeclinedResponse() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Decline"));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        Message last = result.conversationHistory().getLast();
        assertTrue(last instanceof ToolResponseMessage);
        ToolResponseMessage trm = (ToolResponseMessage) last;
        assertEquals(1, trm.getResponses().size());
        assertTrue(trm.getResponses().get(0).responseData().toLowerCase().contains("declined"));
    }

    @Test
    void mixedBatchExecutesApprovedAndSubstitutesDeclinedInOrder() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"), toolCall("2", "getTime"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Decline"));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);
        when(toolSpecService.requiresApproval("getTime")).thenReturn(false);
        when(delegate.executeToolCalls(any(), any())).thenReturn(delegateResultFor(toolCall("2", "getTime")));

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> captor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(any(), captor.capture());
        List<ToolCall> delegated = captor.getValue().getResult().getOutput().getToolCalls();
        assertEquals(1, delegated.size());
        assertEquals("getTime", delegated.get(0).name());

        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertEquals(2, trm.getResponses().size());
        assertEquals("writeFile", trm.getResponses().get(0).name());
        assertTrue(trm.getResponses().get(0).responseData().toLowerCase().contains("declined"));
        assertEquals("getTime", trm.getResponses().get(1).name());
        assertEquals("OK:getTime", trm.getResponses().get(1).responseData());
    }

    @Test
    void approvalQuestionUsesToolPromptTemplate() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        AtomicReference<String> captured = new AtomicReference<>();
        Prompt prompt = promptWith(questions -> {
            captured.set(questions.get(0).question());
            return answerAll(questions, "Decline");
        });
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);
        when(toolSpecService.humanInTheLoopFor("writeFile")).thenReturn(
                new ToolManifest.HumanInTheLoop(ToolManifest.HumanInTheLoop.Mode.REQUIRED, "Allow {toolName}?"));

        manager().executeToolCalls(prompt, response);

        assertEquals("Allow writeFile?", captured.get());
    }

    @Test
    void oversizedToolResponseIsTruncatedWithMarker() {
        ChatResponse response = responseWith(toolCall("1", "fetchPage"));
        Prompt prompt = promptWith(questions -> Map.of());
        when(toolSpecService.requiresApproval("fetchPage")).thenReturn(false);
        String big = "x".repeat(50);
        when(delegate.executeToolCalls(prompt, response)).thenReturn(delegateResultWith("1", "fetchPage", big));

        ToolExecutionResult result = manager(20).executeToolCalls(prompt, response);

        String data = ((ToolResponseMessage) result.conversationHistory().getLast())
                .getResponses().get(0).responseData();
        assertEquals("x".repeat(20) + "\n...[truncated 30 of 50 chars]", data);
    }

    @Test
    void underLimitToolResponsePassesThroughUnchanged() {
        ChatResponse response = responseWith(toolCall("1", "getTime"));
        Prompt prompt = promptWith(questions -> Map.of());
        when(toolSpecService.requiresApproval("getTime")).thenReturn(false);
        when(delegate.executeToolCalls(prompt, response)).thenReturn(delegateResultWith("1", "getTime", "12:00"));

        ToolExecutionResult result = manager(20).executeToolCalls(prompt, response);

        assertEquals("12:00", ((ToolResponseMessage) result.conversationHistory().getLast())
                .getResponses().get(0).responseData());
    }

    private ToolExecutionResult delegateResultWith(String id, String name, String data) {
        AssistantMessage assistant = AssistantMessage.builder().content("").toolCalls(List.of(toolCall(id, name)))
                .build();
        List<Message> history = List.of(new UserMessage("do it"), assistant, ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, data))).build());
        return ToolExecutionResult.builder().conversationHistory(history).build();
    }

    private static Map<String, String> answerAll(List<HumanQuestion> questions, String label) {
        return questions.stream().collect(Collectors.toMap(HumanQuestion::question, q -> label));
    }
}
