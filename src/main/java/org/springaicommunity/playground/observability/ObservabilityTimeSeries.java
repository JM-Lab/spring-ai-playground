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

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ObservabilityTimeSeries extends BucketedTimeSeries<TraceRecord, ObservabilityTimeSeries.Series> {

    private final ObservabilityRingBuffer buffer;

    public ObservabilityTimeSeries(ObservabilityRingBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    protected Iterable<TraceRecord> sourceSince(long windowStartMs) {
        return buffer.snapshot();
    }

    @Override
    protected long timestampOf(TraceRecord item) {
        return item.startEpochMs();
    }

    @Override
    protected Aggregator<TraceRecord, Series> aggregator(Window window, long windowStartMs, long bucketMs, int buckets) {
        return new SeriesBuilder(window, windowStartMs, bucketMs, buckets);
    }

    private static class SeriesBuilder implements Aggregator<TraceRecord, Series> {

        private final Window window;
        private final int buckets;
        private final List<Long> bucketStarts;
        private final long[] calls;
        private final long[] errors;
        private final long[] cancelled;
        private final long[] inTokens;
        private final long[] outTokens;
        private final long[] toolCalls;
        private final List<Long>[] latencyBuckets;
        private final Map<String, long[]> callsByModel = new HashMap<>();
        private final Map<String, long[]> tokensByModel = new HashMap<>();
        private final Map<String, Long> totalCallsByModel = new HashMap<>();
        private final Map<String, Long> totalTokensByModel = new HashMap<>();
        private final Map<String, Long> totalCallsByProvider = new HashMap<>();
        private final Map<String, Long> statusCounts = new HashMap<>();
        private final Map<String, Long> finishReasonCounts = new HashMap<>();
        private final List<Long> allLatencies = new ArrayList<>();
        private long withTools;
        private long withRag;
        private long totalDurationMs;
        private long totalCallsInWindow;
        private long totalTokensInWindow;

        @SuppressWarnings("unchecked")
        SeriesBuilder(Window window, long windowStartMs, long bucketMs, int buckets) {
            this.window = window;
            this.buckets = buckets;
            this.bucketStarts = new ArrayList<>(buckets);
            for (int i = 0; i < buckets; i++) bucketStarts.add(windowStartMs + i * bucketMs);
            this.calls = new long[buckets];
            this.errors = new long[buckets];
            this.cancelled = new long[buckets];
            this.inTokens = new long[buckets];
            this.outTokens = new long[buckets];
            this.toolCalls = new long[buckets];
            this.latencyBuckets = new List[buckets];
            for (int i = 0; i < buckets; i++) latencyBuckets[i] = new ArrayList<>();
        }

        @Override
        public void accept(TraceRecord t, int bucket) {
            String model = t.model() == null ? "(unknown)" : t.model();
            String provider = t.provider() == null ? "(unknown)" : t.provider();
            calls[bucket]++;
            if (TraceRecord.STATUS_ERROR.equals(t.status())) errors[bucket]++;
            if (TraceRecord.STATUS_CANCELLED.equals(t.status())) cancelled[bucket]++;
            inTokens[bucket] += safeLong(t.inputTokens());
            outTokens[bucket] += safeLong(t.outputTokens());
            toolCalls[bucket] += t.toolCallCount();
            latencyBuckets[bucket].add(t.durationMs());
            allLatencies.add(t.durationMs());

            callsByModel.computeIfAbsent(model, k -> new long[buckets])[bucket]++;
            tokensByModel.computeIfAbsent(model, k -> new long[buckets])[bucket] += safeLong(t.totalTokens());
            totalCallsByModel.merge(model, 1L, Long::sum);
            totalTokensByModel.merge(model, safeLong(t.totalTokens()), Long::sum);
            totalCallsByProvider.merge(provider, 1L, Long::sum);
            statusCounts.merge(nullToOk(t.status()), 1L, Long::sum);
            String fr = t.finishReason();
            if (fr != null && !fr.isBlank()) finishReasonCounts.merge(fr, 1L, Long::sum);
            if (t.toolCallCount() > 0 || t.hasTools()) withTools++;
            if (t.hasRag()) withRag++;
            totalDurationMs += t.durationMs();
            totalCallsInWindow++;
            totalTokensInWindow += safeLong(t.totalTokens());
        }

        @Override
        public Series build() {
            double[] p50 = new double[buckets];
            double[] p95 = new double[buckets];
            double[] p99 = new double[buckets];
            double[] avgLatency = new double[buckets];
            double[] errorRatePct = new double[buckets];
            for (int i = 0; i < buckets; i++) {
                List<Long> ls = latencyBuckets[i];
                if (!ls.isEmpty()) {
                    long[] arr = ls.stream().mapToLong(Long::longValue).sorted().toArray();
                    p50[i] = percentile(arr, 50);
                    p95[i] = percentile(arr, 95);
                    p99[i] = percentile(arr, 99);
                    avgLatency[i] = (double) Arrays.stream(arr).sum() / arr.length;
                }
                errorRatePct[i] = calls[i] == 0 ? 0 : (errors[i] * 100.0 / calls[i]);
            }

            long[] sortedAll = allLatencies.stream().mapToLong(Long::longValue).sorted().toArray();
            double overallP50 = percentile(sortedAll, 50);
            double overallP95 = percentile(sortedAll, 95);
            double overallP99 = percentile(sortedAll, 99);
            double overallAvg = sortedAll.length == 0 ? 0 : (double) Arrays.stream(sortedAll).sum() / sortedAll.length;

            return new Series(
                    Collections.unmodifiableList(bucketStarts),
                    window,
                    calls, inTokens, outTokens, errors, cancelled, toolCalls,
                    avgLatency, p50, p95, p99, errorRatePct,
                    callsByModel, tokensByModel,
                    totalCallsByModel, totalTokensByModel,
                    totalCallsByProvider, statusCounts, finishReasonCounts,
                    withTools, withRag,
                    totalCallsInWindow, totalTokensInWindow, totalDurationMs,
                    overallAvg, overallP50, overallP95, overallP99);
        }

        private double percentile(long[] sorted, int pct) {
            if (sorted.length == 0) return 0;
            if (sorted.length == 1) return sorted[0];
            double rank = (pct / 100.0) * (sorted.length - 1);
            int lo = (int) Math.floor(rank);
            int hi = (int) Math.ceil(rank);
            if (lo == hi) return sorted[lo];
            double w = rank - lo;
            return sorted[lo] * (1 - w) + sorted[hi] * w;
        }

        private long safeLong(Long v) {
            return v == null ? 0 : v;
        }

        private String nullToOk(String s) {
            return s == null ? TraceRecord.STATUS_OK : s;
        }
    }

    public record Series(
            List<Long> bucketStartMs,
            Window window,
            long[] calls,
            long[] inTokens,
            long[] outTokens,
            long[] errors,
            long[] cancelled,
            long[] toolCalls,
            double[] avgLatencyMs,
            double[] p50LatencyMs,
            double[] p95LatencyMs,
            double[] p99LatencyMs,
            double[] errorRatePct,
            Map<String, long[]> callsByModelBuckets,
            Map<String, long[]> tokensByModelBuckets,
            Map<String, Long> totalCallsByModel,
            Map<String, Long> totalTokensByModel,
            Map<String, Long> totalCallsByProvider,
            Map<String, Long> statusCounts,
            Map<String, Long> finishReasonCounts,
            long tracesWithTools,
            long tracesWithRag,
            long totalCalls,
            long totalTokens,
            long totalDurationMs,
            double overallAvgLatencyMs,
            double overallP50LatencyMs,
            double overallP95LatencyMs,
            double overallP99LatencyMs
    ) {}
}
