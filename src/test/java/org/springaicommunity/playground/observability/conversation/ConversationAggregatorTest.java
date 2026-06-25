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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.conversation.ConversationAggregator.ConversationSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAggregatorTest {

    private TraceRecord trace(String convId, long startMs, long durMs, int tools, boolean rag,
            Long inTok, Long outTok, String status) {
        return new TraceRecord(
                "trace-" + startMs,
                convId,
                "msg-" + startMs,
                "openai",
                "gpt-4",
                startMs,
                durMs,
                status,
                inTok,
                outTok,
                inTok == null || outTok == null ? null : inTok + outTok,
                "stop",
                tools > 0,
                tools,
                rag,
                null, null, null, null,
                List.of(),
                Map.of()
        );
    }

    @Test
    void aggregateGroupsByConversationId() {
        List<TraceRecord> traces = List.of(
                trace("conv-1", 1000, 500, 0, false, 10L, 20L, TraceRecord.STATUS_OK),
                trace("conv-1", 2000, 700, 2, false, 30L, 40L, TraceRecord.STATUS_OK),
                trace("conv-2", 1500, 600, 1, true, 50L, 60L, TraceRecord.STATUS_OK)
        );
        List<ConversationSummary> out = ConversationAggregator.aggregate(traces, null, 0);
        assertThat(out).hasSize(2);

        ConversationSummary conv1 = out.stream().filter(c -> "conv-1".equals(c.conversationId())).findFirst().orElseThrow();
        assertThat(conv1.messageCount()).isEqualTo(2);
        assertThat(conv1.firstStartMs()).isEqualTo(1000);
        assertThat(conv1.lastEndMs()).isEqualTo(2700);
        assertThat(conv1.durationMs()).isEqualTo(1700);
        assertThat(conv1.toolCalls()).isEqualTo(2);
        assertThat(conv1.ragCalls()).isZero();
        assertThat(conv1.maxLoopDepth()).isEqualTo(2);
        assertThat(conv1.hasMultipleTurns()).isTrue();
        assertThat(conv1.totalTokens()).isEqualTo(100);

        ConversationSummary conv2 = out.stream().filter(c -> "conv-2".equals(c.conversationId())).findFirst().orElseThrow();
        assertThat(conv2.messageCount()).isEqualTo(1);
        assertThat(conv2.ragCalls()).isEqualTo(1);
        assertThat(conv2.hasMultipleTurns()).isFalse();
    }

    @Test
    void aggregateExcludesTracesBeforeWindowStart() {
        List<TraceRecord> traces = List.of(
                trace("conv-1", 500, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK),
                trace("conv-1", 2000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK)
        );
        List<ConversationSummary> out = ConversationAggregator.aggregate(traces, null, 1000);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).messageCount()).isEqualTo(1);
    }

    @Test
    void aggregateExcludesBlankConversationIds() {
        List<TraceRecord> traces = List.of(
                trace(null, 1000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK),
                trace("", 1500, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK),
                trace("conv-1", 2000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK)
        );
        List<ConversationSummary> out = ConversationAggregator.aggregate(traces, null, 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).conversationId()).isEqualTo("conv-1");
    }

    @Test
    void modelIsRecordedEvenWhenPricingServiceIsNull() {
        List<TraceRecord> traces = List.of(
                trace("conv-1", 1000, 100, 0, false, 10L, 20L, TraceRecord.STATUS_OK)
        );
        List<ConversationSummary> out = ConversationAggregator.aggregate(traces, null, 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).modelsUsed()).containsExactly("gpt-4");
        assertThat(out.get(0).cost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void worstStatusPropagatesErrorOverCancelled() {
        List<TraceRecord> traces = List.of(
                trace("conv-1", 1000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_OK),
                trace("conv-1", 2000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_CANCELLED),
                trace("conv-1", 3000, 100, 0, false, 1L, 1L, TraceRecord.STATUS_ERROR)
        );
        List<ConversationSummary> out = ConversationAggregator.aggregate(traces, null, 0);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).worstStatus()).isEqualTo(TraceRecord.STATUS_ERROR);
        assertThat(out.get(0).errorMessageCount()).isEqualTo(1);
        assertThat(out.get(0).cancelledMessageCount()).isEqualTo(1);
    }

}
