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
package org.springaicommunity.playground.observability.system;

import org.springaicommunity.playground.observability.BucketedTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsRingBuffer.Sample;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class SystemMetricsTimeSeries extends BucketedTimeSeries<Sample, SystemMetricsTimeSeries.Series> {

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final SystemMetricsRingBuffer buffer;

    public SystemMetricsTimeSeries(SystemMetricsRingBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    protected Iterable<Sample> sourceSince(long windowStartMs) {
        return buffer.snapshotSince(windowStartMs);
    }

    @Override
    protected long timestampOf(Sample item) {
        return item.epochMs();
    }

    @Override
    protected Aggregator<Sample, Series> aggregator(Window window, long windowStartMs, long bucketMs, int buckets) {
        return new SeriesBuilder(window, windowStartMs, bucketMs, buckets);
    }

    public boolean hasData() {
        return buffer.size() > 0;
    }

    public int sampleCount() {
        return buffer.size();
    }

    public LocalDateTime oldestSampleTime() {
        return buffer.snapshotSince(0L).stream()
                .map(s -> LocalDateTime.ofInstant(Instant.ofEpochMilli(s.epochMs()), ZoneId.systemDefault()))
                .findFirst()
                .orElse(null);
    }

    private static class SeriesBuilder implements Aggregator<Sample, Series> {

        private final Window window;
        private final long windowStartMs;
        private final long bucketMs;
        private final int buckets;
        private final double[] heapUsedBytes;
        private final double[] heapMaxBytes;
        private final double[] heapCommittedBytes;
        private final double[] processCpuPct;
        private final double[] systemCpuPct;
        private final double[] threadsLive;
        private final double[] threadsDaemon;
        private final double[] threadsPeak;
        private final double[] classesLoaded;
        private final double[] openFds;
        private final double[] gcLiveDataBytes;
        private final double[] gcPauseMsCumulative;
        private final double[] gcCountCumulative;
        private final long[] lastEpochInBucket;
        private final boolean[] bucketHasSample;
        private final int[] sampleCount;
        private int totalSamples;

        SeriesBuilder(Window window, long windowStartMs, long bucketMs, int buckets) {
            this.window = window;
            this.windowStartMs = windowStartMs;
            this.bucketMs = bucketMs;
            this.buckets = buckets;
            this.heapUsedBytes = new double[buckets];
            this.heapMaxBytes = new double[buckets];
            this.heapCommittedBytes = new double[buckets];
            this.processCpuPct = new double[buckets];
            this.systemCpuPct = new double[buckets];
            this.threadsLive = new double[buckets];
            this.threadsDaemon = new double[buckets];
            this.threadsPeak = new double[buckets];
            this.classesLoaded = new double[buckets];
            this.openFds = new double[buckets];
            this.gcLiveDataBytes = new double[buckets];
            this.gcPauseMsCumulative = new double[buckets];
            this.gcCountCumulative = new double[buckets];
            this.lastEpochInBucket = new long[buckets];
            this.bucketHasSample = new boolean[buckets];
            this.sampleCount = new int[buckets];
        }

        @Override
        public void accept(Sample sample, int bucket) {
            SystemMetricsSnapshot.Snapshot snap = sample.snapshot();
            heapUsedBytes[bucket] += snap.jvmHeapUsedBytes;
            heapMaxBytes[bucket] += snap.jvmHeapMaxBytes;
            heapCommittedBytes[bucket] += snap.jvmHeapCommittedBytes;
            processCpuPct[bucket] += Math.max(0, snap.processCpuUsage) * 100.0;
            systemCpuPct[bucket] += Math.max(0, snap.systemCpuUsage) * 100.0;
            threadsLive[bucket] += snap.jvmThreadsLive;
            threadsDaemon[bucket] += snap.jvmThreadsDaemon;
            classesLoaded[bucket] += snap.jvmClassesLoaded;
            openFds[bucket] += snap.processFilesOpen;
            sampleCount[bucket]++;
            totalSamples++;

            if (!bucketHasSample[bucket] || sample.epochMs() >= lastEpochInBucket[bucket]) {
                bucketHasSample[bucket] = true;
                lastEpochInBucket[bucket] = sample.epochMs();
                threadsPeak[bucket] = snap.jvmThreadsPeak;
                gcLiveDataBytes[bucket] = snap.gcLiveDataBytes;
                double pauseMs = 0;
                for (Double v : snap.gcPauseSecondsSum.values()) {
                    if (v != null) pauseMs += v * 1000.0;
                }
                long count = 0;
                for (Long v : snap.gcPauseCount.values()) {
                    if (v != null) count += v;
                }
                gcPauseMsCumulative[bucket] = pauseMs;
                gcCountCumulative[bucket] = count;
            }
        }

        @Override
        public Series build() {
            for (int i = 0; i < buckets; i++) {
                if (sampleCount[i] > 0) {
                    heapUsedBytes[i] /= sampleCount[i];
                    heapMaxBytes[i] /= sampleCount[i];
                    heapCommittedBytes[i] /= sampleCount[i];
                    processCpuPct[i] /= sampleCount[i];
                    systemCpuPct[i] /= sampleCount[i];
                    threadsLive[i] /= sampleCount[i];
                    threadsDaemon[i] /= sampleCount[i];
                    classesLoaded[i] /= sampleCount[i];
                    openFds[i] /= sampleCount[i];
                }
            }

            double[] gcPauseMsDelta = new double[buckets];
            double[] gcCountDelta = new double[buckets];
            double prevPauseMs = 0;
            double prevCount = 0;
            boolean havePrev = false;
            for (int i = 0; i < buckets; i++) {
                if (!bucketHasSample[i]) continue;
                if (havePrev) {
                    gcPauseMsDelta[i] = Math.max(0, gcPauseMsCumulative[i] - prevPauseMs);
                    gcCountDelta[i] = Math.max(0, gcCountCumulative[i] - prevCount);
                }
                prevPauseMs = gcPauseMsCumulative[i];
                prevCount = gcCountCumulative[i];
                havePrev = true;
            }

            List<String> labels = new ArrayList<>(buckets);
            for (int i = 0; i < buckets; i++) {
                labels.add(SHORT_FMT.format(Instant.ofEpochMilli(windowStartMs + i * bucketMs)));
            }

            return new Series(Collections.unmodifiableList(labels), window, totalSamples,
                    heapUsedBytes, heapMaxBytes, heapCommittedBytes,
                    processCpuPct, systemCpuPct,
                    threadsLive, threadsDaemon, threadsPeak,
                    classesLoaded, openFds,
                    gcLiveDataBytes, gcPauseMsDelta, gcCountDelta);
        }
    }

    public record Series(
            List<String> bucketLabels,
            Window window,
            int sampleCount,
            double[] heapUsedBytes,
            double[] heapMaxBytes,
            double[] heapCommittedBytes,
            double[] processCpuPct,
            double[] systemCpuPct,
            double[] threadsLive,
            double[] threadsDaemon,
            double[] threadsPeak,
            double[] classesLoaded,
            double[] openFds,
            double[] gcLiveDataBytes,
            double[] gcPauseMsDelta,
            double[] gcCountDelta
    ) {}
}
