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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ObservabilityRingBuffer extends BoundedRingBuffer<TraceRecord> {

    private static final int MERGE_LOOKBACK = 12;

    private final Sinks.Many<TraceRecord> sink = Sinks.many().multicast().directBestEffort();

    public ObservabilityRingBuffer(ObservabilityProperties props) {
        super(props.getRingBufferCapacity(), 10);
    }

    public synchronized void add(TraceRecord trace) {
        TraceRecord merged = mergeWithRecent(trace);
        if (merged != null) {
            sink.tryEmitNext(merged);
            return;
        }
        addWithEviction(trace);
        sink.tryEmitNext(trace);
    }

    private TraceRecord mergeWithRecent(TraceRecord trace) {
        Iterator<TraceRecord> it = buffer.descendingIterator();
        int checked = 0;
        while (it.hasNext() && checked < MERGE_LOOKBACK) {
            TraceRecord existing = it.next();
            checked++;
            if (!isDuplicate(trace, existing)) continue;
            TraceRecord merged = mergeTraces(existing, trace);
            buffer.remove(existing);
            buffer.addLast(merged);
            return merged;
        }
        return null;
    }

    private static boolean isDuplicate(TraceRecord incoming, TraceRecord existing) {
        if (incoming.traceId() != null && incoming.traceId().equals(existing.traceId())) {
            return true;
        }
        String userA = incoming.userMessageId();
        String userB = existing.userMessageId();
        if (userA != null && !userA.isBlank() && userA.equals(userB)
                && Objects.equals(incoming.conversationId(), existing.conversationId())) {
            return true;
        }
        String convA = incoming.conversationId();
        String convB = existing.conversationId();
        if (convA != null && !convA.isBlank() && convA.equals(convB)) {
            long delta = Math.abs(incoming.startEpochMs() - existing.startEpochMs());
            if (delta <= 200) return true;
        }
        return false;
    }

    private static TraceRecord mergeTraces(TraceRecord a, TraceRecord b) {
        String traceId = firstNonBlank(a.traceId(), b.traceId());
        String provider = firstNonBlank(a.provider(), b.provider());
        String model = firstNonBlank(a.model(), b.model());
        long start = Math.min(a.startEpochMs(), b.startEpochMs());
        long end = Math.max(a.startEpochMs() + a.durationMs(), b.startEpochMs() + b.durationMs());
        long duration = end - start;
        String status = TraceRecord.STATUS_ERROR.equals(a.status())
                || TraceRecord.STATUS_ERROR.equals(b.status())
                ? TraceRecord.STATUS_ERROR
                : (TraceRecord.STATUS_CANCELLED.equals(a.status())
                        || TraceRecord.STATUS_CANCELLED.equals(b.status())
                        ? TraceRecord.STATUS_CANCELLED : TraceRecord.STATUS_OK);
        Long inTok = pickLarger(a.inputTokens(), b.inputTokens());
        Long outTok = pickLarger(a.outputTokens(), b.outputTokens());
        Long totalTok = pickLarger(a.totalTokens(), b.totalTokens());
        String finishReason = firstNonBlank(a.finishReason(), b.finishReason());
        int toolCallCount = Math.max(a.toolCallCount(), b.toolCallCount());
        boolean hasTools = a.hasTools() || b.hasTools();
        boolean hasRag = a.hasRag() || b.hasRag();
        List<SpanRecord> spans = new ArrayList<>();
        if (a.spans() != null) spans.addAll(a.spans());
        if (b.spans() != null) spans.addAll(b.spans());
        Map<String, String> attrs = new HashMap<>();
        if (a.attributes() != null) attrs.putAll(a.attributes());
        if (b.attributes() != null) b.attributes().forEach(attrs::putIfAbsent);
        return new TraceRecord(traceId,
                firstNonBlank(a.conversationId(), b.conversationId()),
                firstNonBlank(a.userMessageId(), b.userMessageId()),
                provider, model, start, duration, status,
                inTok, outTok, totalTok,
                finishReason, hasTools, toolCallCount, hasRag,
                spans, attrs);
    }

    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    private static Long pickLarger(Long a, Long b) {
        if (a == null) return b;
        if (b == null) return a;
        return a >= b ? a : b;
    }

    public Flux<TraceRecord> liveStream() {
        return sink.asFlux();
    }
}
