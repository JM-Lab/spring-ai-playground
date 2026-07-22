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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.tool.FileUploadHandler;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopManagerHitlTest {

    private final ToolCallingManager delegate = mock(ToolCallingManager.class);
    private final ToolSpecService toolSpecService = mock(ToolSpecService.class);

    private AgentLoopManager manager() {
        return manager(12_000);
    }

    @SuppressWarnings("unchecked")
    private AgentLoopManager manager(int toolResultMaxChars) {
        ObjectProvider<ToolSpecService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(toolSpecService);
        return new AgentLoopManager(delegate, provider, new SimpleMeterRegistry(), toolResultMaxChars);
    }

    private Prompt promptWith(HumanQuestionHandler handler) {
        Map<String, Object> toolContext = handler == null ? Map.of()
                : Map.of(HumanQuestionHandler.TOOL_CONTEXT_KEY, handler);
        return promptWithContext(toolContext);
    }

    private Prompt promptWithContext(Map<String, Object> toolContext) {
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
        when(delegate.executeToolCalls(any(), any())).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
        verify(delegate).executeToolCalls(any(), any());
    }

    @Test
    void toolNotRequiringApprovalPassesThrough() {
        ChatResponse response = responseWith(toolCall("1", "getTime"));
        Prompt prompt = promptWith(questions -> Map.of());
        when(toolSpecService.requiresApproval("getTime")).thenReturn(false);
        ToolExecutionResult expected = delegateResultFor(toolCall("1", "getTime"));
        when(delegate.executeToolCalls(any(), any())).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
    }

    @Test
    void approvedToolRunsViaDelegate() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Approve"));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);
        ToolExecutionResult expected = delegateResultFor(toolCall("1", "writeFile"));
        when(delegate.executeToolCalls(any(), any())).thenReturn(expected);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        assertEquals(expected, result);
        verify(delegate).executeToolCalls(any(), any());
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

    // Regression for the approval bypass: two identical gated calls used to collide on the question
    // text key, letting the first call execute even when the user declined both.
    @Test
    void duplicateGatedCallsAreEachDeclinedIndependently() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"), toolCall("2", "writeFile"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Decline"));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertEquals(2, trm.getResponses().size());
        assertTrue(trm.getResponses().get(0).responseData().toLowerCase().contains("declined"));
        assertTrue(trm.getResponses().get(1).responseData().toLowerCase().contains("declined"));
    }

    @Test
    void duplicateGatedCallsCanBeAnsweredIndividuallyById() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"), toolCall("2", "writeFile"));
        Prompt prompt = promptWith(questions -> questions.stream().collect(
                Collectors.toMap(HumanQuestion::id, q -> "1".equals(q.id()) ? "Approve" : "Decline")));
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);
        when(delegate.executeToolCalls(any(), any())).thenReturn(delegateResultFor(toolCall("1", "writeFile")));

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        ArgumentCaptor<ChatResponse> captor = ArgumentCaptor.forClass(ChatResponse.class);
        verify(delegate).executeToolCalls(any(), captor.capture());
        List<ToolCall> delegated = captor.getValue().getResult().getOutput().getToolCalls();
        assertEquals(1, delegated.size());
        assertEquals("1", delegated.get(0).id());

        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertEquals(2, trm.getResponses().size());
        assertEquals("OK:writeFile", trm.getResponses().get(0).responseData());
        assertTrue(trm.getResponses().get(1).responseData().toLowerCase().contains("declined"));
    }

    @Test
    void askFailureFailsClosed() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(questions -> {
            throw new IllegalStateException("dialog broke");
        });
        when(toolSpecService.requiresApproval("writeFile")).thenReturn(true);

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertTrue(trm.getResponses().get(0).responseData().toLowerCase().contains("declined"));
    }

    @Test
    void interceptorExceptionFailsClosed() {
        ChatResponse response = responseWith(toolCall("1", "writeFile"));
        Prompt prompt = promptWith(questions -> answerAll(questions, "Approve"));
        when(toolSpecService.requiresApproval("writeFile")).thenThrow(new IllegalStateException("boom"));

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertTrue(trm.getResponses().get(0).responseData().toLowerCase().contains("declined"));
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
        when(delegate.executeToolCalls(any(), any())).thenReturn(delegateResultWith("1", "fetchPage", big));

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
        when(delegate.executeToolCalls(any(), any())).thenReturn(delegateResultWith("1", "getTime", "12:00"));

        ToolExecutionResult result = manager(20).executeToolCalls(prompt, response);

        assertEquals("12:00", ((ToolResponseMessage) result.conversationHistory().getLast())
                .getResponses().get(0).responseData());
    }

    @Test
    void requestFileUploadIsInterceptedAndReturnsSavedPath() {
        ChatResponse response = responseWith(toolCall("1", "requestFileUpload"));
        Prompt prompt = promptWithUpload(request -> FileUploadHandler.Result.of("uploads/data.csv", "data.csv",
                "text/csv", 42));

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertEquals(1, trm.getResponses().size());
        assertEquals("requestFileUpload", trm.getResponses().get(0).name());
        assertTrue(trm.getResponses().get(0).responseData().contains("uploads/data.csv"));
    }

    @Test
    void requestFileUploadCancelledTellsTheModelNotToRetry() {
        ChatResponse response = responseWith(toolCall("1", "requestFileUpload"));
        Prompt prompt = promptWithUpload(request -> FileUploadHandler.Result.none("The user cancelled the upload."));

        ToolExecutionResult result = manager().executeToolCalls(prompt, response);

        verify(delegate, never()).executeToolCalls(any(), any());
        String data = ((ToolResponseMessage) result.conversationHistory().getLast())
                .getResponses().get(0).responseData();
        assertTrue(data.toLowerCase().contains("cancel"));
        assertTrue(data.contains("Do NOT call 'requestFileUpload' again"));
    }

    @Test
    void cancelledUploadIsAutoDeclinedOnRepeatCallInSameTurn() {
        AgentTurn turn = new AgentTurn(new SpringAiPlaygroundOptions.AgentLoop(16, 18, 3, 1, null, null));
        AtomicInteger uploads = new AtomicInteger();
        FileUploadHandler handler = request -> {
            uploads.incrementAndGet();
            return FileUploadHandler.Result.none("The user cancelled the upload.");
        };
        Prompt prompt = promptWithContext(Map.of(FileUploadHandler.TOOL_CONTEXT_KEY, handler,
                AgentTurn.TOOL_CONTEXT_KEY, turn));

        manager().executeToolCalls(prompt, responseWith(toolCall("1", "requestFileUpload")));
        ToolExecutionResult second = manager().executeToolCalls(prompt,
                responseWith(toolCall("2", "requestFileUpload")));

        assertEquals(1, uploads.get());
        String data = ((ToolResponseMessage) second.conversationHistory().getLast())
                .getResponses().get(0).responseData();
        assertTrue(data.toLowerCase().contains("declined"));
    }

    @Test
    void secondInteractiveCallInOneRoundIsDeferredByBudget() {
        AgentTurn turn = new AgentTurn(new SpringAiPlaygroundOptions.AgentLoop(16, 18, 3, 1, null, null));
        AtomicInteger uploads = new AtomicInteger();
        FileUploadHandler uploadHandler = request -> {
            uploads.incrementAndGet();
            return FileUploadHandler.Result.of("uploads/one.csv", "one.csv", "text/csv", 1);
        };
        Prompt prompt = promptWithContext(Map.of(FileUploadHandler.TOOL_CONTEXT_KEY, uploadHandler,
                AgentTurn.TOOL_CONTEXT_KEY, turn));

        ToolExecutionResult result = manager().executeToolCalls(prompt,
                responseWith(toolCall("1", "requestFileUpload"), toolCall("2", "requestFileUpload")));

        assertEquals(1, uploads.get());
        ToolResponseMessage trm = (ToolResponseMessage) result.conversationHistory().getLast();
        assertTrue(trm.getResponses().get(0).responseData().contains("uploads/one.csv"));
        assertTrue(trm.getResponses().get(1).responseData().contains("next step"));
    }

    private Prompt promptWithUpload(FileUploadHandler handler) {
        return promptWithContext(Map.of(FileUploadHandler.TOOL_CONTEXT_KEY, handler));
    }

    private ToolExecutionResult delegateResultWith(String id, String name, String data) {
        AssistantMessage assistant = AssistantMessage.builder().content("").toolCalls(List.of(toolCall(id, name)))
                .build();
        List<Message> history = List.of(new UserMessage("do it"), assistant, ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, name, data))).build());
        return ToolExecutionResult.builder().conversationHistory(history).build();
    }

    @Test
    void approvalQuestionCarriesGatingRiskLevel() {
        ChatResponse response = responseWith(toolCall("1", "deleteFile"));
        AtomicReference<List<HumanQuestion>> seen = new AtomicReference<>();
        Prompt prompt = promptWith(questions -> {
            seen.set(questions);
            return answerAll(questions, "Decline");
        });
        when(toolSpecService.requiresApproval("deleteFile")).thenReturn(true);
        when(toolSpecService.gatingRiskLevel("deleteFile"))
                .thenReturn(ToolManifest.Sandbox.RiskLevel.L5);

        manager().executeToolCalls(prompt, response);

        assertEquals(ToolManifest.Sandbox.RiskLevel.L5, seen.get().get(0).riskLevel());
    }

    private static Map<String, String> answerAll(List<HumanQuestion> questions, String label) {
        return questions.stream().collect(Collectors.toMap(HumanQuestion::id, q -> label));
    }
}
