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
import org.springaicommunity.playground.service.agent.AgentTurn.RoundVerdict;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LoopGuardInterceptor implements AgentRoundInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoopGuardInterceptor.class);

    private final MeterRegistry meterRegistry;

    LoopGuardInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Map<String, Interception> intercept(List<ToolCall> calls, AgentTurn turn,
            Map<String, Object> toolContext) {
        RoundVerdict verdict = turn.verdict();
        if (verdict != RoundVerdict.CONTINUE) {
            logger.info("agent.loop.guard verdict={} round={}", verdict, turn.rounds());
            countLoop(outcomeOf(verdict));
            Map<String, Interception> claims = new LinkedHashMap<>();
            for (ToolCall call : calls) {
                claims.put(call.id(), Interception.of(blanketMessage(verdict, turn, call)));
            }
            return claims;
        }
        Map<String, Interception> claims = new LinkedHashMap<>();
        for (ToolCall call : calls) {
            if (turn.isDeclined(call.name())) {
                logger.info("agent.loop.declined-repeat tool={}", call.name());
                countLoop("declined-repeat");
                claims.put(call.id(), Interception.of(AgentTurnMessages.declined(call.name())));
            } else if (turn.repeatedTooOften(call.name(), call.arguments())) {
                logger.info("agent.loop.repeated tool={}", call.name());
                countLoop("repeated");
                claims.put(call.id(),
                        Interception.of(AgentTurnMessages.repeated(call.name(), turn.maxIdenticalCalls())));
            }
        }
        return claims;
    }

    private static String blanketMessage(RoundVerdict verdict, AgentTurn turn, ToolCall call) {
        return switch (verdict) {
            case WRAP_UP -> AgentTurnMessages.wrapUp();
            case HARD_STOP -> AgentTurnMessages.hardStopped(turn.rounds());
            default -> AgentTurnMessages.cancelled(call.name());
        };
    }

    private static String outcomeOf(RoundVerdict verdict) {
        return switch (verdict) {
            case WRAP_UP -> "wrap-up";
            case HARD_STOP -> "hard-stop";
            default -> "cancelled";
        };
    }

    private void countLoop(String outcome) {
        this.meterRegistry.counter("chat.tool.loop", "outcome", outcome).increment();
    }
}
