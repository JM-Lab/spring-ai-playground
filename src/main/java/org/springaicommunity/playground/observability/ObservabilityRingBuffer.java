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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class ObservabilityRingBuffer extends BoundedRingBuffer<TraceRecord> {

    private static final int MERGE_LOOKBACK = 12;

    private final Sinks.Many<TraceRecord> sink = Sinks.many().multicast().directBestEffort();

    public ObservabilityRingBuffer(ObservabilityProperties props) {
        super(props.getRingBufferCapacity(), 10);
    }

    public synchronized TraceRecord add(TraceRecord trace) {
        TraceRecord merged = mergeWithRecent(trace);
        if (merged != null) {
            sink.tryEmitNext(merged);
            return merged;
        }
        addWithEviction(trace);
        sink.tryEmitNext(trace);
        return trace;
    }

    private TraceRecord mergeWithRecent(TraceRecord trace) {
        Iterator<TraceRecord> it = buffer.descendingIterator();
        int checked = 0;
        while (it.hasNext()) {
            TraceRecord existing = it.next();
            checked++;
            boolean match = checked <= MERGE_LOOKBACK
                    ? isDuplicate(trace, existing)
                    : sameTraceId(trace, existing);
            if (!match) continue;
            TraceRecord merged = mergeTraces(existing, trace);
            buffer.remove(existing);
            buffer.addLast(merged);
            return merged;
        }
        return null;
    }

    private static boolean isDuplicate(TraceRecord incoming, TraceRecord existing) {
        if (sameTraceId(incoming, existing)) {
            return true;
        }
        String callA = toolCallId(incoming);
        if (callA != null && callA.equals(toolCallId(existing))) {
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
        String traceId = TraceRecord.firstNonBlank(a.traceId(), b.traceId());
        String provider = TraceRecord.firstNonBlank(a.provider(), b.provider());
        String model = TraceRecord.firstNonBlank(a.model(), b.model());
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
        String finishReason = TraceRecord.firstNonBlank(a.finishReason(), b.finishReason());
        boolean hasTools = a.hasTools() || b.hasTools();
        boolean hasRag = a.hasRag() || b.hasRag();
        List<SpanRecord> spans = new ArrayList<>();
        Set<String> seenSpanIds = new HashSet<>();
        Set<String> seenToolCallIds = new HashSet<>();
        appendNewSpans(spans, seenSpanIds, seenToolCallIds, a.spans());
        appendNewSpans(spans, seenSpanIds, seenToolCallIds, b.spans());
        int toolCallCount = Math.max(countToolSpans(spans),
                Math.max(a.toolCallCount(), b.toolCallCount()));
        Map<String, String> attrs = new HashMap<>();
        if (a.attributes() != null) attrs.putAll(a.attributes());
        if (b.attributes() != null) b.attributes().forEach(attrs::putIfAbsent);
        Set<String> toolNames = new LinkedHashSet<>();
        if (a.toolNames() != null) toolNames.addAll(a.toolNames());
        if (b.toolNames() != null) toolNames.addAll(b.toolNames());
        Set<String> serverNames = new LinkedHashSet<>();
        if (a.serverNames() != null) serverNames.addAll(a.serverNames());
        if (b.serverNames() != null) serverNames.addAll(b.serverNames());
        return new TraceRecord(traceId,
                TraceRecord.firstNonBlank(a.conversationId(), b.conversationId()),
                TraceRecord.firstNonBlank(a.userMessageId(), b.userMessageId()),
                provider, model, start, duration, status,
                inTok, outTok, totalTok,
                finishReason, hasTools, toolCallCount, hasRag,
                TraceRecord.firstNonBlank(a.userId(), b.userId()),
                TraceRecord.firstNonBlank(a.sessionId(), b.sessionId()),
                toolNames, serverNames,
                spans, attrs);
    }

    private static boolean sameTraceId(TraceRecord a, TraceRecord b) {
        return a.traceId() != null && a.traceId().equals(b.traceId());
    }

    private static void appendNewSpans(List<SpanRecord> out, Set<String> seenSpanIds,
            Set<String> seenToolCallIds, List<SpanRecord> in) {
        if (in == null) return;
        for (SpanRecord span : in) {
            String callId = toolCallIdOf(span);
            if (callId != null && !seenToolCallIds.add(callId)) continue;
            if (span.spanId() == null || seenSpanIds.add(span.spanId())) {
                out.add(span);
            }
        }
    }

    private static String toolCallIdOf(SpanRecord span) {
        if (!ObservabilityCollector.TOOL_SPAN_NAME.equals(span.name())) return null;
        Map<String, String> a = span.attributes();
        if (a == null) return null;
        String id = a.get(ObservabilityCollector.TOOL_CALL_ID_ATTR);
        if (id == null || id.isBlank()) id = a.get("gen_ai.tool.call.id");
        return id == null || id.isBlank() ? null : id;
    }

    private static String toolCallId(TraceRecord trace) {
        if (trace.spans() == null) return null;
        for (SpanRecord span : trace.spans()) {
            String id = toolCallIdOf(span);
            if (id != null) return id;
        }
        return null;
    }

    private static int countToolSpans(List<SpanRecord> spans) {
        return (int) spans.stream()
                .filter(s -> ObservabilityCollector.TOOL_SPAN_NAME.equals(s.name()))
                .count();
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
