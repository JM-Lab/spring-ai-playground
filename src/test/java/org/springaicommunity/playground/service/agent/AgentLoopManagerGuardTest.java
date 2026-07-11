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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLoopManagerGuardTest {

    private final ToolCallingManager delegate = mock(ToolCallingManager.class);

    @SuppressWarnings("unchecked")
    private AgentLoopManager manager() {
        ObjectProvider<ToolSpecService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new AgentLoopManager(delegate, provider, new SimpleMeterRegistry(), 12_000);
    }

    private static AgentTurn turn(int soft, int hard, int identical) {
        return new AgentTurn(new SpringAiPlaygroundOptions.AgentLoop(soft, hard, identical, 1, null, null));
    }

    private Prompt promptWith(AgentTurn turn) {
        return new Prompt(List.of(new UserMessage("do it")), ToolCallingChatOptions.builder()
                .toolContext(Map.of(AgentTurn.TOOL_CONTEXT_KEY, turn)).build());
    }

    private ChatResponse responseCalling(String id, String name, String arguments) {
        AssistantMessage assistant = AssistantMessage.builder().content("")
                .toolCalls(List.of(new ToolCall(id, "function", name, arguments))).build();
        return new ChatResponse(List.of(new Generation(assistant)));
    }

    private void stubDelegate() {
        when(delegate.executeToolCalls(any(), any())).thenReturn(ToolExecutionResult.builder()
                .conversationHistory(List.of(ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse("1", "x", "OK"))).build()))
                .build());
    }

    private static String lastResponseData(ToolExecutionResult result) {
        Message last = result.conversationHistory().getLast();
        return ((ToolResponseMessage) last).getResponses().get(0).responseData();
    }

    @Test
    void wrapUpRoundAnswersToolCallsWithoutExecuting() {
        stubDelegate();
        AgentTurn turn = turn(1, 3, 3);
        AgentLoopManager manager = manager();

        manager.executeToolCalls(promptWith(turn), responseCalling("1", "getTime", "{}"));
        ToolExecutionResult second = manager.executeToolCalls(promptWith(turn),
                responseCalling("2", "getTime", "{\"other\":true}"));

        verify(delegate, times(1)).executeToolCalls(any(), any());
        assertTrue(lastResponseData(second).contains("budget"));
        assertFalse(second.returnDirect());
    }

    @Test
    void hardStopEndsTheLoopWithReturnDirect() {
        stubDelegate();
        AgentTurn turn = turn(1, 2, 3);
        AgentLoopManager manager = manager();

        manager.executeToolCalls(promptWith(turn), responseCalling("1", "getTime", "{}"));
        manager.executeToolCalls(promptWith(turn), responseCalling("2", "getTime", "{\"a\":1}"));
        ToolExecutionResult third = manager.executeToolCalls(promptWith(turn),
                responseCalling("3", "getTime", "{\"b\":2}"));

        verify(delegate, times(1)).executeToolCalls(any(), any());
        assertTrue(third.returnDirect());
        assertTrue(lastResponseData(third).contains("Stopped after 3 tool rounds"));
        assertTrue(lastResponseData(third).contains("new message"));
    }

    @Test
    void cancelledTurnShortCircuitsTheRound() {
        AgentTurn turn = turn(16, 18, 3);
        turn.cancel();

        ToolExecutionResult result = manager().executeToolCalls(promptWith(turn),
                responseCalling("1", "getTime", "{}"));

        verify(delegate, never()).executeToolCalls(any(), any());
        assertTrue(result.returnDirect());
        assertTrue(lastResponseData(result).contains("stopped"));
    }

    @Test
    void identicalCallsBeyondTheLimitGetASyntheticResponse() {
        stubDelegate();
        AgentTurn turn = turn(16, 18, 1);
        AgentLoopManager manager = manager();

        manager.executeToolCalls(promptWith(turn), responseCalling("1", "getTime", "{}"));
        ToolExecutionResult second = manager.executeToolCalls(promptWith(turn),
                responseCalling("2", "getTime", "{}"));

        verify(delegate, times(1)).executeToolCalls(any(), any());
        assertTrue(lastResponseData(second).contains("identical arguments"));
    }

    @Test
    void differentArgumentsAreNotTreatedAsRepeats() {
        stubDelegate();
        AgentTurn turn = turn(16, 18, 1);
        AgentLoopManager manager = manager();

        manager.executeToolCalls(promptWith(turn), responseCalling("1", "getTime", "{\"zone\":\"UTC\"}"));
        manager.executeToolCalls(promptWith(turn), responseCalling("2", "getTime", "{\"zone\":\"KST\"}"));

        verify(delegate, times(2)).executeToolCalls(any(), any());
    }

    @Test
    void detachedTurnKeepsLegacyUnboundedBehavior() {
        stubDelegate();
        Prompt prompt = new Prompt(List.of(new UserMessage("do it")),
                ToolCallingChatOptions.builder().toolContext(Map.of()).build());
        AgentLoopManager manager = manager();

        for (int i = 0; i < 30; i++) {
            ToolExecutionResult result = manager.executeToolCalls(prompt, responseCalling("1", "getTime", "{}"));
            assertEquals("OK", lastResponseData(result));
        }
        verify(delegate, times(30)).executeToolCalls(any(), any());
    }
}
