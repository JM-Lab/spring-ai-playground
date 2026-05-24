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
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityRingBufferTest {

    @Test
    void enforcesMinimumCapacityFloorOf10() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setRingBufferCapacity(3);
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(props);
        assertThat(buffer.capacity()).isEqualTo(10);
    }

    @Test
    void evictsOldestWhenCapacityExceeded() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setRingBufferCapacity(50);
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(props);

        for (int i = 0; i < 60; i++) {
            buffer.add(makeTrace("trace-" + i));
        }

        assertThat(buffer.size()).isEqualTo(50);
        List<TraceRecord> snapshot = buffer.snapshot();
        // oldest 10 should be gone; first remaining is trace-10
        assertThat(snapshot.get(0).traceId()).isEqualTo("trace-10");
        assertThat(snapshot.get(snapshot.size() - 1).traceId()).isEqualTo("trace-59");
    }

    @Test
    void liveStreamBroadcastsAddedTracesToSubscribers() throws InterruptedException {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setRingBufferCapacity(100);
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(props);

        List<TraceRecord> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);
        Disposable subscription = buffer.liveStream().subscribe(t -> {
            received.add(t);
            latch.countDown();
        });
        try {
            buffer.add(makeTrace("a"));
            buffer.add(makeTrace("b"));
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(received).extracting(TraceRecord::traceId).containsExactly("a", "b");
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void snapshotReturnsIndependentCopy() {
        ObservabilityProperties props = new ObservabilityProperties();
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(props);
        buffer.add(makeTrace("x"));
        List<TraceRecord> snap = buffer.snapshot();
        buffer.add(makeTrace("y"));
        assertThat(snap).hasSize(1);
        assertThat(buffer.snapshot()).hasSize(2);
    }

    @Test
    void emptyBufferSnapshotIsEmptyAndSizeIsZero() {
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(new ObservabilityProperties());
        assertThat(buffer.snapshot()).isEmpty();
        assertThat(buffer.size()).isZero();
    }

    @Test
    void isEmptyReflectsBufferState() {
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(new ObservabilityProperties());
        assertThat(buffer.isEmpty()).isTrue();
        buffer.add(makeTrace("x"));
        assertThat(buffer.isEmpty()).isFalse();
    }

    @Test
    void isFullBecomesTrueOnceCapacityReached() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.setRingBufferCapacity(10);
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(props);
        assertThat(buffer.isFull()).isFalse();
        for (int i = 0; i < 10; i++) buffer.add(makeTrace("t-" + i));
        assertThat(buffer.isFull()).isTrue();
        buffer.add(makeTrace("overflow"));
        assertThat(buffer.isFull()).isTrue();
        assertThat(buffer.size()).isEqualTo(buffer.capacity());
    }

    @Test
    void multipleSubscribersEachReceiveEveryAddedTrace() throws InterruptedException {
        ObservabilityRingBuffer buffer = new ObservabilityRingBuffer(new ObservabilityProperties());

        List<TraceRecord> a = new CopyOnWriteArrayList<>();
        List<TraceRecord> b = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(4); // 2 subscribers × 2 traces
        Disposable subA = buffer.liveStream().subscribe(t -> {
            a.add(t);
            latch.countDown();
        });
        Disposable subB = buffer.liveStream().subscribe(t -> {
            b.add(t);
            latch.countDown();
        });
        try {
            buffer.add(makeTrace("p"));
            buffer.add(makeTrace("q"));
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(a).extracting(TraceRecord::traceId).containsExactly("p", "q");
            assertThat(b).extracting(TraceRecord::traceId).containsExactly("p", "q");
        } finally {
            subA.dispose();
            subB.dispose();
        }
    }

    private TraceRecord makeTrace(String id) {
        return new TraceRecord(
                id, "conv-" + id, "msg-" + id, "ollama", "qwen3.5:9b",
                System.currentTimeMillis(), 100L, TraceRecord.STATUS_OK,
                10L, 20L, 30L, "stop", false, 0, false,
                List.of(), Map.of());
    }
}
