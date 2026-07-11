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

import org.springaicommunity.playground.SpringAiPlaygroundOptions;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class AgentTurn {

    public static final String TOOL_CONTEXT_KEY = "playgroundAgentTurn";

    public enum RoundVerdict { CONTINUE, WRAP_UP, HARD_STOP, CANCELLED }

    private final SpringAiPlaygroundOptions.AgentLoop policy;
    private final AtomicInteger rounds = new AtomicInteger();
    private final AtomicInteger interactionsThisRound = new AtomicInteger();
    private final Map<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();
    private final Set<String> declinedTools = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile RoundVerdict verdict = RoundVerdict.CONTINUE;

    public AgentTurn(SpringAiPlaygroundOptions.AgentLoop policy) {
        this.policy = policy == null ? new SpringAiPlaygroundOptions.AgentLoop(null, null, null, null, null, null)
                : policy;
    }

    public RoundVerdict beginRound() {
        this.interactionsThisRound.set(0);
        int round = this.rounds.incrementAndGet();
        if (this.cancelled.get()) this.verdict = RoundVerdict.CANCELLED;
        else if (round > this.policy.hardMaxRounds()) this.verdict = RoundVerdict.HARD_STOP;
        else if (round > this.policy.softMaxRounds()) this.verdict = RoundVerdict.WRAP_UP;
        else this.verdict = RoundVerdict.CONTINUE;
        return this.verdict;
    }

    public RoundVerdict verdict() {
        return this.verdict;
    }

    public int rounds() {
        return this.rounds.get();
    }

    public boolean repeatedTooOften(String toolName, String arguments) {
        String fingerprint = toolName + "|" + (arguments == null ? "" : arguments);
        return this.callCounts.computeIfAbsent(fingerprint, key -> new AtomicInteger()).incrementAndGet()
                > this.policy.maxIdenticalCalls();
    }

    public void markDeclined(String toolName) {
        this.declinedTools.add(toolName);
    }

    public boolean isDeclined(String toolName) {
        return this.declinedTools.contains(toolName);
    }

    public int maxIdenticalCalls() {
        return this.policy.maxIdenticalCalls();
    }

    public boolean tryInteract() {
        return this.interactionsThisRound.incrementAndGet() <= this.policy.interactionsPerRound();
    }

    public void cancel() {
        this.cancelled.set(true);
    }

    public boolean cancelled() {
        return this.cancelled.get();
    }

    public static AgentTurn from(Map<String, Object> toolContext) {
        Object turn = toolContext == null ? null : toolContext.get(TOOL_CONTEXT_KEY);
        return turn instanceof AgentTurn agentTurn ? agentTurn : detached();
    }

    static AgentTurn detached() {
        return new AgentTurn(new SpringAiPlaygroundOptions.AgentLoop(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, null, null));
    }
}
