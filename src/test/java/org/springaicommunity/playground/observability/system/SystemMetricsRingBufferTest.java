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
import org.springaicommunity.playground.observability.system.SystemMetricsRingBuffer.Sample;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemMetricsRingBufferTest {

    private Snapshot snap(double heap) {
        Snapshot s = new Snapshot();
        s.jvmHeapUsedBytes = heap;
        return s;
    }

    @Test
    void capacityFloorsAtSixtyEvenIfSmallerValueGiven() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(10);
        assertThat(b.capacity()).isEqualTo(60);
    }

    @Test
    void recordsAndReturnsSamplesSinceTimestamp() throws InterruptedException {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(100);
        long t0 = System.currentTimeMillis();
        b.record(snap(1));
        Thread.sleep(5);
        b.record(snap(2));
        Thread.sleep(5);
        b.record(snap(3));

        List<Sample> all = b.snapshotSince(0);
        assertThat(all).hasSize(3);
        assertThat(all).extracting(s -> s.snapshot().jvmHeapUsedBytes).containsExactly(1.0, 2.0, 3.0);
        assertThat(b.snapshotSince(t0 - 1000)).hasSize(3);
    }

    @Test
    void evictsOldestWhenOverCapacity() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(60);
        for (int i = 0; i < 65; i++) {
            b.record(snap(i));
        }
        assertThat(b.size()).isEqualTo(60);
        List<Sample> all = b.snapshotSince(0);
        // First 5 evicted, oldest now snap(5)
        assertThat(all.get(0).snapshot().jvmHeapUsedBytes).isEqualTo(5.0);
        assertThat(all.get(all.size() - 1).snapshot().jvmHeapUsedBytes).isEqualTo(64.0);
    }

    @Test
    void recordIgnoresNull() {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(60);
        b.record(null);
        assertThat(b.size()).isZero();
    }

    @Test
    void snapshotSinceFiltersOlderEntries() throws InterruptedException {
        SystemMetricsRingBuffer b = new SystemMetricsRingBuffer(60);
        b.record(snap(1));
        Thread.sleep(20);
        long cutoff = System.currentTimeMillis();
        Thread.sleep(20);
        b.record(snap(2));
        b.record(snap(3));

        List<Sample> recent = b.snapshotSince(cutoff);
        assertThat(recent).extracting(s -> s.snapshot().jvmHeapUsedBytes).containsExactly(2.0, 3.0);
    }
}
