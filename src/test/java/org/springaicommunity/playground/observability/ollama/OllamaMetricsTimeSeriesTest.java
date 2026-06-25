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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.ollama.OllamaMetricsTimeSeries.Series;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaMetricsTimeSeriesTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Test
    void bucketsAndAveragesRecordedSamples() {
        OllamaMetricsRingBuffer buffer = new OllamaMetricsRingBuffer(2400);
        OllamaMetricsTimeSeries timeSeries = new OllamaMetricsTimeSeries(buffer);

        buffer.record(true, 2, 2 * GB, 12);
        buffer.record(true, 2, 2 * GB, 12);
        buffer.record(true, 2, 2 * GB, 12);

        Series series = timeSeries.compute(Window.LAST_30M);

        assertThat(timeSeries.sampleCount()).isEqualTo(3);
        assertThat(series.sampleCount()).isEqualTo(3);
        assertThat(Arrays.stream(series.runningModels()).max().orElse(0)).isEqualTo(2.0);
        assertThat(Arrays.stream(series.installedModels()).max().orElse(0)).isEqualTo(12.0);
        assertThat(Arrays.stream(series.vramBytes()).max().orElse(0)).isEqualTo((double) (2 * GB));
        assertThat(Arrays.stream(series.reachablePct()).max().orElse(0)).isEqualTo(100.0);
        assertThat(series.bucketLabels()).hasSize(series.runningModels().length);
    }

    @Test
    void unreachableSamplesAggregateToZeroReachablePct() {
        OllamaMetricsRingBuffer buffer = new OllamaMetricsRingBuffer(2400);
        OllamaMetricsTimeSeries timeSeries = new OllamaMetricsTimeSeries(buffer);

        buffer.record(false, 0, 0, 12);
        buffer.record(false, 0, 0, 12);
        buffer.record(false, 0, 0, 12);

        Series series = timeSeries.compute(Window.LAST_30M);

        assertThat(series.sampleCount()).isEqualTo(3);
        assertThat(Arrays.stream(series.reachablePct()).max().orElse(-1)).isZero();
    }

    @Test
    void emptyBufferProducesZeroSeries() {
        OllamaMetricsRingBuffer buffer = new OllamaMetricsRingBuffer(2400);
        OllamaMetricsTimeSeries timeSeries = new OllamaMetricsTimeSeries(buffer);

        Series series = timeSeries.compute(Window.LAST_30M);

        assertThat(timeSeries.hasData()).isFalse();
        assertThat(series.sampleCount()).isZero();
        assertThat(Arrays.stream(series.runningModels()).sum()).isZero();
        assertThat(Arrays.stream(series.vramBytes()).sum()).isZero();
        assertThat(series.bucketLabels()).isNotEmpty();
    }
}
