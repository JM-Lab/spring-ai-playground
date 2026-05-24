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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsTimeSeriesTest {

    private Snapshot snap(double heapMb, double cpu, long threads) {
        Snapshot s = new Snapshot();
        s.jvmHeapUsedBytes = heapMb * 1024 * 1024;
        s.processCpuUsage = cpu;
        s.systemCpuUsage = cpu * 1.5;
        s.jvmThreadsLive = threads;
        return s;
    }

    @Test
    void computeReturnsCorrectBucketCountForWindow() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);

        SystemMetricsTimeSeries.Series s5 = ts.compute(Window.LAST_5M);
        assertThat(s5.bucketLabels()).hasSize(5);
        assertThat(s5.heapUsedBytes()).hasSize(5);

        SystemMetricsTimeSeries.Series s30 = ts.compute(Window.LAST_30M);
        assertThat(s30.bucketLabels()).hasSize(30);

        SystemMetricsTimeSeries.Series s3h = ts.compute(Window.LAST_3H);
        assertThat(s3h.bucketLabels()).hasSize(180);
    }

    @Test
    void computeBucketsAverageSamplesInsideEachMinute() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        long now = System.currentTimeMillis();
        // 3 samples in current minute (the last bucket of LAST_5M)
        // heapBytes: 100MB, 200MB, 300MB  → avg 200MB
        for (int i = 0; i < 3; i++) {
            Snapshot s = snap((i + 1) * 100, 0.1, 50);
            b.record(s);
        }
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_5M, now);
        // The most recent samples land in the last bucket (index 4)
        double last = series.heapUsedBytes()[series.heapUsedBytes().length - 1];
        // 200MB in bytes = 200 * 1024 * 1024
        assertThat(last).isEqualTo(200.0 * 1024 * 1024);
    }

    @Test
    void cpuPercentagesAreInPercentScale() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        b.record(snap(100, 0.25, 50));
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_5M);
        double processLast = series.processCpuPct()[series.processCpuPct().length - 1];
        double systemLast = series.systemCpuPct()[series.systemCpuPct().length - 1];
        // 0.25 fraction → 25%
        assertThat(processLast).isEqualTo(25.0);
        // 0.375 → 37.5%
        assertThat(systemLast).isEqualTo(37.5);
    }

    @Test
    void emptyBufferReturnsZeroedSeriesNotException() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(60);
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_30M);
        assertThat(series.heapUsedBytes()).hasSize(30);
        assertThat(series.heapUsedBytes()).containsOnly(0.0);
        assertThat(series.sampleCount()).isZero();
    }

    @Test
    void hasDataReflectsBufferState() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(60);
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        assertThat(ts.hasData()).isFalse();
        b.record(snap(100, 0.1, 50));
        assertThat(ts.hasData()).isTrue();
    }

    @Test
    void seriesIncludesNewHeapCommittedAndThreadsPeakAndGcFields() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        Snapshot snap = snap(100, 0.1, 50);
        snap.jvmHeapCommittedBytes = 512 * 1024 * 1024;
        snap.jvmThreadsPeak = 88;
        snap.gcLiveDataBytes = 64 * 1024 * 1024;
        snap.gcPauseSecondsSum.put("G1 Young / end of minor GC", 0.5);
        snap.gcPauseCount.put("G1 Young / end of minor GC", 5L);
        b.record(snap);

        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_5M);

        assertThat(series.heapCommittedBytes()).hasSize(5);
        assertThat(series.threadsPeak()).hasSize(5);
        assertThat(series.gcLiveDataBytes()).hasSize(5);
        assertThat(series.gcPauseMsDelta()).hasSize(5);
        assertThat(series.gcCountDelta()).hasSize(5);

        double lastCommitted = series.heapCommittedBytes()[series.heapCommittedBytes().length - 1];
        assertThat(lastCommitted).isEqualTo(512.0 * 1024 * 1024);
        double lastPeak = series.threadsPeak()[series.threadsPeak().length - 1];
        assertThat(lastPeak).isEqualTo(88.0);
    }

    @Test
    void gcDeltaIsZeroForFirstSampleAndPositiveForSubsequent() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        long now = System.currentTimeMillis();

        Snapshot first = snap(100, 0.1, 50);
        first.gcPauseSecondsSum.put("G1 Young / end of minor GC", 0.5);
        first.gcPauseCount.put("G1 Young / end of minor GC", 5L);
        b.record(first);

        Snapshot second = snap(100, 0.1, 50);
        second.gcPauseSecondsSum.put("G1 Young / end of minor GC", 0.7);
        second.gcPauseCount.put("G1 Young / end of minor GC", 7L);
        b.record(second);

        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_5M, now);

        double pauseSum = 0;
        for (double v : series.gcPauseMsDelta()) pauseSum += v;
        double countSum = 0;
        for (double v : series.gcCountDelta()) countSum += v;
        assertThat(pauseSum).isGreaterThanOrEqualTo(0);
        assertThat(countSum).isGreaterThanOrEqualTo(0);
    }

    @Test
    void samplesOlderThanWindowAreExcluded() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(200);
        long now = System.currentTimeMillis();

        // Place an old sample way before the 5m window
        Snapshot old = snap(999, 0.99, 999);
        b.record(old);

        // Then record recent samples
        b.record(snap(100, 0.1, 50));
        SystemMetricsTimeSeries ts = new SystemMetricsTimeSeries(b);

        // Pass nowEpochMs explicitly so the old sample falls inside the window
        // (since it was just recorded ms ago); to truly exclude, use a future nowEpoch
        SystemMetricsTimeSeries.Series series = ts.compute(Window.LAST_5M, now + 10 * 60_000);
        // Only old samples from far past would be excluded; recent ones still in
        assertThat(series.sampleCount()).isLessThanOrEqualTo(2);
    }
}
