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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.springaicommunity.playground.service.tool.ToolManifest;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class HitlApprovalInterceptor implements AgentRoundInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(HitlApprovalInterceptor.class);
    private static final int ARGS_DISPLAY_MAX_CHARS = 400;

    private final ObjectProvider<ToolSpecService> toolSpecServiceProvider;
    private final MeterRegistry meterRegistry;

    HitlApprovalInterceptor(ObjectProvider<ToolSpecService> toolSpecServiceProvider, MeterRegistry meterRegistry) {
        this.toolSpecServiceProvider = toolSpecServiceProvider;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Map<String, Interception> intercept(List<ToolCall> calls, AgentTurn turn,
            Map<String, Object> toolContext) {
        HumanQuestionHandler handler = toolContext == null ? null
                : (HumanQuestionHandler) toolContext.get(HumanQuestionHandler.TOOL_CONTEXT_KEY);
        ToolSpecService toolSpecService = this.toolSpecServiceProvider.getIfAvailable();
        if (handler == null || toolSpecService == null) return Map.of();

        List<HumanQuestion> questions = new ArrayList<>();
        Map<String, ToolCall> gatedById = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (toolSpecService.requiresApproval(call.name())) {
                questions.add(HumanQuestion.approval(call.id(), "Tool approval required",
                        approvalQuestion(toolSpecService.humanInTheLoopFor(call.name()), call)));
                gatedById.put(call.id(), call);
            }
        }
        if (questions.isEmpty()) return Map.of();

        if (!turn.tryInteract()) {
            Map<String, Interception> deferred = new LinkedHashMap<>();
            for (ToolCall call : gatedById.values()) {
                logger.info("hitl.deferred tool={} reason=interaction-budget", call.name());
                countLoop("interaction-budget");
                deferred.put(call.id(), Interception.of(AgentTurnMessages.interactionBudget(call.name())));
            }
            return deferred;
        }

        Map<String, String> answers;
        try {
            answers = handler.ask(questions);
        } catch (RuntimeException e) {
            logger.warn("hitl.ask-failed error={}", e.getMessage());
            countDecision("ask-failed");
            answers = Map.of();
        }
        Map<String, Interception> claims = new LinkedHashMap<>();
        for (Map.Entry<String, ToolCall> entry : gatedById.entrySet()) {
            ToolCall call = entry.getValue();
            String answer = answers == null ? null : answers.get(entry.getKey());
            if (!"Approve".equalsIgnoreCase(answer)) {
                turn.markDeclined(call.name());
                claims.put(call.id(), Interception.of(AgentTurnMessages.declined(call.name())));
                logger.info("hitl.declined tool={}", call.name());
                countDecision("declined");
            } else {
                logger.info("hitl.approved tool={}", call.name());
                countDecision("approved");
            }
        }
        return claims;
    }

    private static String approvalQuestion(ToolManifest.HumanInTheLoop humanInTheLoop, ToolCall toolCall) {
        String promptTemplate = humanInTheLoop == null ? null : humanInTheLoop.promptTemplate();
        String template = (promptTemplate == null || promptTemplate.isBlank())
                ? "Run '{toolName}' with arguments {args}?" : promptTemplate;
        return template.replace("{toolName}", toolCall.name())
                .replace("{args}", displayArgs(toolCall.arguments()));
    }

    private static String displayArgs(String arguments) {
        if (arguments == null) return "{}";
        if (arguments.length() <= ARGS_DISPLAY_MAX_CHARS) return arguments;
        return arguments.substring(0, ARGS_DISPLAY_MAX_CHARS) + "...";
    }

    private void countDecision(String outcome) {
        this.meterRegistry.counter("mcp.hitl.decision", "outcome", outcome, "side", "chat").increment();
    }

    private void countLoop(String outcome) {
        this.meterRegistry.counter("chat.tool.loop", "outcome", outcome).increment();
    }
}
