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
package org.springaicommunity.playground.observability.conversation;

import org.springaicommunity.playground.observability.ObservabilityCollector;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConversationAggregator {

    private ConversationAggregator() {}

    public static List<ConversationSummary> aggregate(
            List<TraceRecord> traces,
            ModelPricingService pricing,
            long windowStartMs) {
        Map<String, List<TraceRecord>> byConv = new HashMap<>();
        for (TraceRecord t : traces) {
            if (t.startEpochMs() < windowStartMs) continue;
            if (t.conversationId() == null || t.conversationId().isBlank()) continue;
            byConv.computeIfAbsent(t.conversationId(), k -> new ArrayList<>()).add(t);
        }
        List<ConversationSummary> out = new ArrayList<>(byConv.size());
        for (Map.Entry<String, List<TraceRecord>> e : byConv.entrySet()) {
            out.add(summarize(e.getKey(), e.getValue(), pricing));
        }
        out.sort(Comparator.comparingLong(ConversationSummary::lastEndMs).reversed());
        return out;
    }

    private static ConversationSummary summarize(String conversationId,
            List<TraceRecord> records, ModelPricingService pricing) {
        records.sort(Comparator.comparingLong(TraceRecord::startEpochMs));
        long firstStart = records.getFirst().startEpochMs();
        TraceRecord last = records.getLast();
        long lastEnd = last.startEpochMs() + last.durationMs();

        long inputTokens = 0;
        long outputTokens = 0;
        BigDecimal cost = BigDecimal.ZERO;
        int toolCalls = 0;
        int ragCalls = 0;
        int maxLoopDepth = 0;
        Set<String> models = new LinkedHashSet<>();
        Set<String> tools = new LinkedHashSet<>();
        Set<String> providers = new LinkedHashSet<>();
        String worstStatus = TraceRecord.STATUS_OK;
        String latestUserMessageId = null;
        long errorCount = 0;
        long cancelledCount = 0;

        for (TraceRecord t : records) {
            long in = t.inputTokens() == null ? 0 : t.inputTokens();
            long out = t.outputTokens() == null ? 0 : t.outputTokens();
            inputTokens += in;
            outputTokens += out;
            if (t.model() != null && !t.model().isBlank()) {
                models.add(t.model());
                if (pricing != null) {
                    cost = cost.add(pricing.cost(t.model(), in, out));
                }
            }
            if (t.provider() != null && !t.provider().isBlank()) {
                providers.add(t.provider());
            }
            toolCalls += t.toolCallCount();
            if (t.hasRag()) ragCalls++;
            if (t.toolCallCount() > maxLoopDepth) maxLoopDepth = t.toolCallCount();

            if (t.spans() != null) {
                for (SpanRecord s : t.spans()) {
                    if (ObservabilityCollector.TOOL_SPAN_NAME.equals(s.name()) && s.attributes() != null) {
                        String name = s.attributes().get("spring.ai.tool.definition.name");
                        if (name != null && !name.isBlank()) tools.add(name);
                    }
                }
            }

            if (TraceRecord.STATUS_ERROR.equals(t.status())) {
                worstStatus = TraceRecord.STATUS_ERROR;
                errorCount++;
            } else if (TraceRecord.STATUS_CANCELLED.equals(t.status())) {
                if (!TraceRecord.STATUS_ERROR.equals(worstStatus)) {
                    worstStatus = TraceRecord.STATUS_CANCELLED;
                }
                cancelledCount++;
            }
            if (t.userMessageId() != null) latestUserMessageId = t.userMessageId();
        }

        return new ConversationSummary(
                conversationId, latestUserMessageId,
                records.size(),
                firstStart, lastEnd, Math.max(0, lastEnd - firstStart),
                inputTokens, outputTokens, cost,
                toolCalls, ragCalls,
                models, tools, providers,
                worstStatus, maxLoopDepth,
                errorCount, cancelledCount
        );
    }

    public record ConversationSummary(
            String conversationId,
            String latestUserMessageId,
            long messageCount,
            long firstStartMs,
            long lastEndMs,
            long durationMs,
            long inputTokens,
            long outputTokens,
            BigDecimal cost,
            int toolCalls,
            int ragCalls,
            Set<String> modelsUsed,
            Set<String> toolsUsed,
            Set<String> providersUsed,
            String worstStatus,
            int maxLoopDepth,
            long errorMessageCount,
            long cancelledMessageCount
    ) {
        public boolean hasMultipleTurns() {
            return messageCount > 1;
        }

        public boolean usedTools() {
            return toolCalls > 0;
        }

        public boolean usedRag() {
            return ragCalls > 0;
        }

        public long totalTokens() {
            return inputTokens + outputTokens;
        }
    }
}
