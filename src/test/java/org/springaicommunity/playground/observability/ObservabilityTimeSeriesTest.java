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
package org.springaicommunity.playground.observability;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries.Series;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ObservabilityTimeSeriesTest {

    @Test
    void emptyBufferReturnsZeroFilledSeriesWithCorrectBucketCount() {
        ObservabilityRingBuffer buffer = makeBuffer();
        ObservabilityTimeSeries ts = new ObservabilityTimeSeries(buffer);

        Series s = ts.compute(Window.LAST_30M);

        assertThat(s.bucketStartMs()).hasSize(30);
        assertThat(s.calls()).hasSize(30).containsOnly(0L);
        assertThat(s.totalCalls()).isZero();
        assertThat(s.totalTokens()).isZero();
        assertThat(s.overallP95LatencyMs()).isZero();
        assertThat(s.totalCallsByModel()).isEmpty();
    }

    @Test
    void tracesOutsideWindowAreExcluded() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();
        // Trace 5 hours ago — way past the 3h max window
        buffer.add(trace(now - 5L * 60 * 60 * 1000, 1_000, "ollama", "qwen3.5:9b",
                10, 20, "OK", "stop", false, false));
        // Trace inside the 30m window
        buffer.add(trace(now - 5 * 60 * 1000, 200, "ollama", "qwen3.5:9b",
                5, 15, "OK", "stop", false, false));

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        assertThat(s.totalCalls()).isEqualTo(1);
        assertThat(s.totalTokens()).isEqualTo(20L);
    }

    @Test
    void tracesAggregateIntoCorrectBucketsByMinute() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();
        long bucketSize = 60_000L;

        // 5 traces evenly spaced across last 5 minutes
        for (int i = 0; i < 5; i++) {
            long ts = now - (i * bucketSize) - 30_000;   // 30s into each minute back
            buffer.add(trace(ts, 100 + i * 10, "ollama", "qwen3.5:9b",
                    1, 1, "OK", "stop", false, false));
        }

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_5M);

        // 5 buckets, 1 call each (oldest → newest)
        assertThat(s.calls()).hasSize(5);
        long sum = 0;
        for (long v : s.calls()) sum += v;
        assertThat(sum).isEqualTo(5);
        assertThat(s.totalCalls()).isEqualTo(5);
    }

    @Test
    void perModelAndPerProviderAggregationsAreCorrect() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();

        for (int i = 0; i < 3; i++) {
            buffer.add(trace(now - i * 60_000, 100, "ollama", "qwen3.5:9b",
                    10, 20, "OK", "stop", false, false));
        }
        for (int i = 0; i < 2; i++) {
            buffer.add(trace(now - i * 60_000, 100, "openai", "gpt-5.4-mini",
                    8, 16, "OK", "stop", false, false));
        }

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        assertThat(s.totalCallsByModel()).containsEntry("qwen3.5:9b", 3L);
        assertThat(s.totalCallsByModel()).containsEntry("gpt-5.4-mini", 2L);
        assertThat(s.totalCallsByProvider()).containsEntry("ollama", 3L);
        assertThat(s.totalCallsByProvider()).containsEntry("openai", 2L);
        assertThat(s.totalTokensByModel()).containsEntry("qwen3.5:9b", 90L);
        assertThat(s.totalTokensByModel()).containsEntry("gpt-5.4-mini", 48L);
    }

    @Test
    void errorAndCancelledStatusAreTrackedSeparately() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();

        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b",
                1, 1, "OK", "stop", false, false));
        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b",
                1, 1, TraceRecord.STATUS_ERROR, "error", false, false));
        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b",
                1, 1, TraceRecord.STATUS_CANCELLED, null, false, false));

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        long errors = 0;
        for (long v : s.errors()) errors += v;
        long cancelled = 0;
        for (long v : s.cancelled()) cancelled += v;
        assertThat(errors).isEqualTo(1);
        assertThat(cancelled).isEqualTo(1);
        assertThat(s.totalCalls()).isEqualTo(3);
        assertThat(s.statusCounts()).containsEntry(TraceRecord.STATUS_OK, 1L);
        assertThat(s.statusCounts()).containsEntry(TraceRecord.STATUS_ERROR, 1L);
        assertThat(s.statusCounts()).containsEntry(TraceRecord.STATUS_CANCELLED, 1L);
    }

    @Test
    void finishReasonsAreAggregated() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();

        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b", 1, 1, "OK", "stop", false, false));
        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b", 1, 1, "OK", "stop", false, false));
        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b", 1, 1, "OK", "tool_calls", false, false));
        buffer.add(trace(now - 60_000, 100, "ollama", "qwen3.5:9b", 1, 1, "OK", "length", false, false));

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        assertThat(s.finishReasonCounts()).containsEntry("stop", 2L);
        assertThat(s.finishReasonCounts()).containsEntry("tool_calls", 1L);
        assertThat(s.finishReasonCounts()).containsEntry("length", 1L);
    }

    @Test
    void toolsAndRagFlagsDriveTracesWithToolsAndTracesWithRag() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();

        buffer.add(trace(now - 60_000, 100, "ollama", "m", 1, 1, "OK", "stop", true, false));   // tools
        buffer.add(trace(now - 60_000, 100, "ollama", "m", 1, 1, "OK", "stop", false, true));   // rag
        buffer.add(trace(now - 60_000, 100, "ollama", "m", 1, 1, "OK", "stop", true, true));    // both
        buffer.add(trace(now - 60_000, 100, "ollama", "m", 1, 1, "OK", "stop", false, false));  // plain

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        assertThat(s.tracesWithTools()).isEqualTo(2L);
        assertThat(s.tracesWithRag()).isEqualTo(2L);
    }

    @Test
    void percentileP50P95P99UseLinearInterpolation() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();
        long bucketStart = now - 60_000;

        // Latencies: 10, 20, 30, ..., 100 ms (10 samples) — within the same bucket
        for (int i = 1; i <= 10; i++) {
            buffer.add(trace(bucketStart + i, i * 10, "ollama", "m",
                    1, 1, "OK", "stop", false, false));
        }

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        // For sorted [10..100], rank-based percentiles with linear interpolation:
        //   p50 = 0.50 * 9  = 4.5 → between sorted[4]=50 and sorted[5]=60 → 55
        //   p95 = 0.95 * 9  = 8.55 → between sorted[8]=90 and sorted[9]=100 → 95.5
        //   p99 = 0.99 * 9  = 8.91 → between sorted[8]=90 and sorted[9]=100 → 99.1
        assertThat(s.overallP50LatencyMs()).isCloseTo(55.0, within(0.01));
        assertThat(s.overallP95LatencyMs()).isCloseTo(95.5, within(0.01));
        assertThat(s.overallP99LatencyMs()).isCloseTo(99.1, within(0.01));
        assertThat(s.overallAvgLatencyMs()).isCloseTo(55.0, within(0.01));
    }

    @Test
    void errorRatePerBucketIsPercent() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();
        long bucketStart = now - 60_000;

        // 4 traces in the same bucket, 1 error → 25%
        buffer.add(trace(bucketStart + 1, 100, "ollama", "m", 1, 1, "OK", "stop", false, false));
        buffer.add(trace(bucketStart + 2, 100, "ollama", "m", 1, 1, "OK", "stop", false, false));
        buffer.add(trace(bucketStart + 3, 100, "ollama", "m", 1, 1, "OK", "stop", false, false));
        buffer.add(trace(bucketStart + 4, 100, "ollama", "m", 1, 1, TraceRecord.STATUS_ERROR, "error", false, false));

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_5M);

        // The bucket containing the 4 traces should report 25.0%
        boolean foundBucket = false;
        for (double rate : s.errorRatePct()) {
            if (rate > 0) {
                assertThat(rate).isCloseTo(25.0, within(0.01));
                foundBucket = true;
            }
        }
        assertThat(foundBucket).as("at least one bucket should have non-zero error rate").isTrue();
    }

    @Test
    void windowMinutesMatchBucketCount() {
        ObservabilityRingBuffer buffer = makeBuffer();
        ObservabilityTimeSeries ts = new ObservabilityTimeSeries(buffer);

        assertThat(ts.compute(Window.LAST_5M).bucketStartMs()).hasSize(5);
        assertThat(ts.compute(Window.LAST_30M).bucketStartMs()).hasSize(30);
        assertThat(ts.compute(Window.LAST_1H).bucketStartMs()).hasSize(60);
        assertThat(ts.compute(Window.LAST_3H).bucketStartMs()).hasSize(180);
    }

    @Test
    void tokensPerBucketSplitInputAndOutput() {
        ObservabilityRingBuffer buffer = makeBuffer();
        long now = System.currentTimeMillis();
        long bucket = now - 60_000;

        buffer.add(trace(bucket + 1, 100, "ollama", "m", 100, 200, "OK", "stop", false, false));
        buffer.add(trace(bucket + 2, 100, "ollama", "m", 50, 150, "OK", "stop", false, false));

        Series s = new ObservabilityTimeSeries(buffer).compute(Window.LAST_30M);

        long inSum = 0;
        for (long v : s.inTokens()) inSum += v;
        long outSum = 0;
        for (long v : s.outTokens()) outSum += v;
        assertThat(inSum).isEqualTo(150L);
        assertThat(outSum).isEqualTo(350L);
        assertThat(s.totalTokens()).isEqualTo(500L);   // 100+200+50+150 totalTokens = 500
    }

    private static ObservabilityRingBuffer makeBuffer() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setRingBufferCapacity(2000);
        return new ObservabilityRingBuffer(props);
    }

    private static final AtomicLong TRACE_SEQ = new AtomicLong();

    private static TraceRecord trace(long startMs, long durationMs, String provider, String model,
            int inTokens, int outTokens, String status, String finishReason,
            boolean hasTools, boolean hasRag) {
        long seq = TRACE_SEQ.incrementAndGet();
        return new TraceRecord(
                "trace-" + seq, "conv-" + seq, "msg-" + seq, provider, model,
                startMs, durationMs, status,
                (long) inTokens, (long) outTokens, (long) (inTokens + outTokens),
                finishReason, hasTools, hasTools ? 1 : 0, hasRag,
                List.of(), Map.of());
    }
}
