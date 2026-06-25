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
package org.springaicommunity.playground.observability.ollama;

import org.springaicommunity.playground.observability.BucketedTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.ollama.OllamaMetricsRingBuffer.Sample;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class OllamaMetricsTimeSeries extends BucketedTimeSeries<Sample, OllamaMetricsTimeSeries.Series> {

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final OllamaMetricsRingBuffer buffer;

    public OllamaMetricsTimeSeries(OllamaMetricsRingBuffer buffer) {
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

    private static class SeriesBuilder implements Aggregator<Sample, Series> {

        private final Window window;
        private final long windowStartMs;
        private final long bucketMs;
        private final int buckets;
        private final double[] runningModels;
        private final double[] vramBytes;
        private final double[] installedModels;
        private final double[] reachablePct;
        private final int[] sampleCount;
        private int totalSamples;

        SeriesBuilder(Window window, long windowStartMs, long bucketMs, int buckets) {
            this.window = window;
            this.windowStartMs = windowStartMs;
            this.bucketMs = bucketMs;
            this.buckets = buckets;
            this.runningModels = new double[buckets];
            this.vramBytes = new double[buckets];
            this.installedModels = new double[buckets];
            this.reachablePct = new double[buckets];
            this.sampleCount = new int[buckets];
        }

        @Override
        public void accept(Sample sample, int bucket) {
            runningModels[bucket] += sample.runningModels();
            vramBytes[bucket] += sample.totalVramBytes();
            installedModels[bucket] += sample.installedModels();
            if (sample.reachable()) reachablePct[bucket]++;
            sampleCount[bucket]++;
            totalSamples++;
        }

        @Override
        public Series build() {
            for (int i = 0; i < buckets; i++) {
                if (sampleCount[i] > 0) {
                    runningModels[i] /= sampleCount[i];
                    vramBytes[i] /= sampleCount[i];
                    installedModels[i] /= sampleCount[i];
                    reachablePct[i] = 100.0 * reachablePct[i] / sampleCount[i];
                }
            }
            List<String> labels = new ArrayList<>(buckets);
            for (int i = 0; i < buckets; i++) {
                labels.add(SHORT_FMT.format(Instant.ofEpochMilli(windowStartMs + i * bucketMs)));
            }
            return new Series(Collections.unmodifiableList(labels), window, totalSamples,
                    runningModels, vramBytes, installedModels, reachablePct);
        }
    }

    public record Series(
            List<String> bucketLabels,
            Window window,
            int sampleCount,
            double[] runningModels,
            double[] vramBytes,
            double[] installedModels,
            double[] reachablePct
    ) {}
}
