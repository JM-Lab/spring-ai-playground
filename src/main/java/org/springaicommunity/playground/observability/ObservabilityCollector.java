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

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class ObservabilityCollector implements ObservationHandler<Observation.Context> {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityCollector.class);

    public static final String ROOT_SPAN_NAME = "spring.ai.chat.client";
    public static final String CHAT_MODEL_SPAN_NAME = "gen_ai.client.operation";
    public static final String TOOL_SPAN_NAME = "spring.ai.tool";
    public static final String ADVISOR_SPAN_NAME = "spring.ai.advisor";
    public static final String VECTOR_STORE_SPAN_NAME = "db.vector.client.operation";

    private static final String CTX_START_NS = "obs.startNs";
    private static final String CTX_TRACE_ID = "obs.traceId";
    private static final String CTX_SPAN_ID = "obs.spanId";
    private static final String CTX_PARENT_SPAN_ID = "obs.parentSpanId";

    private final ObservabilityRingBuffer buffer;
    private final ObjectProvider<ObservabilityPersistenceService> persistenceProvider;
    private final ObjectProvider<Tracer> tracerProvider;
    private final ObservabilityProperties props;

    private final Map<String, TraceBuilder> active = new ConcurrentHashMap<>();
    private final AtomicLong fallbackTraceCounter = new AtomicLong();

    public ObservabilityCollector(ObservabilityRingBuffer buffer,
            ObjectProvider<ObservabilityPersistenceService> persistenceProvider,
            ObjectProvider<Tracer> tracerProvider,
            ObservabilityProperties props) {
        this.buffer = buffer;
        this.persistenceProvider = persistenceProvider;
        this.tracerProvider = tracerProvider;
        this.props = props;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        if (context == null) return false;
        // Spring AI sets observation name only at start time — match by class fqn first.
        String cls = context.getClass().getName();
        if (cls.startsWith("org.springframework.ai.")) return true;
        String name = context.getName();
        if (name == null) return false;
        return name.startsWith("spring.ai.")
                || CHAT_MODEL_SPAN_NAME.equals(name)
                || VECTOR_STORE_SPAN_NAME.equals(name);
    }

    @Override
    public void onStop(Observation.Context context) {
        captureSpanIds(context);
        long startNs = (long) Optional.ofNullable(context.get(CTX_START_NS))
                .orElse(System.nanoTime());
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
        long endEpochMs = Instant.now().toEpochMilli();
        long startEpochMs = endEpochMs - durationMs;

        Map<String, String> attrs = extractAttributes(context);
        Throwable err = context.getError();
        if (err != null) {
            attrs.putIfAbsent("error.type", err.getClass().getSimpleName());
            String msg = err.getMessage();
            if (msg != null && !msg.isBlank()) {
                attrs.putIfAbsent("error.message", msg.length() > 200 ? msg.substring(0, 200) : msg);
            }
        }
        String traceId = (String) context.get(CTX_TRACE_ID);
        String spanId = (String) context.get(CTX_SPAN_ID);
        String parentSpanId = (String) context.get(CTX_PARENT_SPAN_ID);

        if (traceId == null || traceId.isBlank()) {
            traceId = "local-" + fallbackTraceCounter.incrementAndGet();
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = UUID.randomUUID().toString();
        }

        String spanName = context.getName();
        if (spanName == null) spanName = context.getContextualName();
        if (spanName == null) spanName = simpleClassNameFallback(context);

        SpanRecord span = new SpanRecord(spanId, parentSpanId, spanName,
                startEpochMs, durationMs, errorStatus(context), attrs);

        TraceBuilder builder = active.computeIfAbsent(traceId, TraceBuilder::new);
        builder.addSpan(span, props.getMaxSpansPerTrace());

        boolean isRoot = ROOT_SPAN_NAME.equals(context.getName())
                || ROOT_SPAN_NAME.equals(context.getContextualName())
                || context.getClass().getName().endsWith("ChatClientObservationContext");
        if (isRoot) {
            active.remove(traceId);
            TraceRecord trace = builder.finalizeTrace(span);
            buffer.add(trace);
            ObservabilityPersistenceService persist = persistenceProvider.getIfAvailable();
            if (persist != null && props.isPersist()) {
                persist.saveAsync(trace);
            }
        }
    }

    @Override
    public void onStart(Observation.Context context) {
        context.put(CTX_START_NS, System.nanoTime());
        captureSpanIds(context);
    }

    @Override
    public void onError(Observation.Context context) {
        // attribute noted via Observation.Context.getError() — handled in errorStatus()
    }

    @Scheduled(fixedDelayString = "${spring.ai.playground.observability.active-trace-cleanup-ms:60000}")
    public void cleanupStaleActiveTraces() {
        evictActiveOlderThan(Instant.now().toEpochMilli() - props.getActiveTraceTtlSeconds() * 1000L);
    }

    int evictActiveOlderThan(long cutoffEpochMs) {
        int before = active.size();
        active.entrySet().removeIf(e -> e.getValue().createdAtMs < cutoffEpochMs);
        int evicted = before - active.size();
        if (evicted > 0) {
            logger.debug("Evicted {} stale active trace builders (cutoff={}ms)", evicted, cutoffEpochMs);
        }
        return evicted;
    }

    int activeSize() {
        return active.size();
    }

    private void captureSpanIds(Observation.Context context) {
        if (context.get(CTX_TRACE_ID) != null) {
            return;
        }
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return;
        }
        Span current = tracer.currentSpan();
        if (current == null) {
            return;
        }
        context.put(CTX_TRACE_ID, current.context().traceId());
        context.put(CTX_SPAN_ID, current.context().spanId());
        String parent = current.context().parentId();
        if (parent != null) {
            context.put(CTX_PARENT_SPAN_ID, parent);
        }
    }

    private Map<String, String> extractAttributes(Observation.Context context) {
        Map<String, String> attrs = new HashMap<>();
        for (KeyValue kv : context.getLowCardinalityKeyValues()) {
            putIfNotNull(attrs, kv);
        }
        for (KeyValue kv : context.getHighCardinalityKeyValues()) {
            putIfNotNull(attrs, kv);
        }
        // MDC fallback for conversationId/userMessageId
        String conv = MDC.get(ChatService.MDC_CONVERSATION_ID);
        if (conv != null) {
            attrs.putIfAbsent("conversation.id", conv);
        }
        String msg = MDC.get(ChatService.MDC_USER_MESSAGE_ID);
        if (msg != null) {
            attrs.putIfAbsent("user_message.id", msg);
        }
        enrichWithChatModelMessages(context, attrs);
        enrichWithChatClientResponse(context, attrs);
        return attrs;
    }

    private void enrichWithChatClientResponse(Observation.Context context, Map<String, String> attrs) {
        if (!(context instanceof ChatClientObservationContext ccoc)) return;
        try {
            ChatClientResponse ccr = ccoc.getResponse();
            if (ccr == null) return;
            ChatResponse chatResponse = ccr.chatResponse();
            if (chatResponse == null) return;
            ChatResponseMetadata meta = chatResponse.getMetadata();
            if (meta == null) return;
            String model = meta.getModel();
            if (model != null && !model.isBlank()) {
                attrs.putIfAbsent("gen_ai.response.model", model);
            }
            Usage usage = meta.getUsage();
            if (usage != null) {
                if (usage.getPromptTokens() != null) {
                    attrs.putIfAbsent("gen_ai.usage.input_tokens", usage.getPromptTokens().toString());
                }
                if (usage.getCompletionTokens() != null) {
                    attrs.putIfAbsent("gen_ai.usage.output_tokens", usage.getCompletionTokens().toString());
                }
                if (usage.getTotalTokens() != null) {
                    attrs.putIfAbsent("gen_ai.usage.total_tokens", usage.getTotalTokens().toString());
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract usage from ChatClientObservationContext", e);
        }
    }

    private void enrichWithChatModelMessages(Observation.Context context, Map<String, String> attrs) {
        if (!props.isCapturePromptContent()) return;
        if (!(context instanceof ChatModelObservationContext cmoc)) return;
        int max = Math.max(256, props.getMaxPromptContentBytes());
        int maxMessages = Math.max(1, props.getMaxCapturedMessagesPerSpan());
        try {
            Prompt prompt = cmoc.getRequest();
            if (prompt != null && prompt.getInstructions() != null) {
                List<Message> messages = prompt.getInstructions();
                attrs.putIfAbsent("gen_ai.prompt.count", String.valueOf(messages.size()));
                // Cap stored content per span — agentic loops can produce huge prompts.
                int captureCount = Math.min(messages.size(), maxMessages);
                if (captureCount < messages.size()) {
                    attrs.putIfAbsent("gen_ai.prompt.truncated_messages",
                            String.valueOf(messages.size() - captureCount));
                }
                for (int i = 0; i < captureCount; i++) {
                    Message m = messages.get(i);
                    if (m == null) continue;
                    String role = roleOf(m.getMessageType());
                    attrs.putIfAbsent("gen_ai.prompt." + i + ".role", role);
                    String text = m.getText();
                    if (text != null && !text.isEmpty()) {
                        attrs.putIfAbsent("gen_ai.prompt." + i + ".content", truncate(text, max));
                    }
                }
            }
            ChatResponse response = cmoc.getResponse();
            if (response != null) {
                Generation result = response.getResult();
                if (result != null) {
                    AssistantMessage out = result.getOutput();
                    if (out != null) {
                        attrs.putIfAbsent("gen_ai.completion.0.role", "assistant");
                        String text = out.getText();
                        if (text != null && !text.isEmpty()) {
                            attrs.putIfAbsent("gen_ai.completion.0.content", truncate(text, max));
                        }
                        if (out.hasToolCalls()) {
                            String calls = out.getToolCalls().stream()
                                    .limit(8)
                                    .map(tc -> tc.name() + "(" + truncate(tc.arguments(), 256) + ")")
                                    .collect(Collectors.joining(" · "));
                            attrs.putIfAbsent("gen_ai.completion.0.tool_calls", calls);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Defensive: capture must never break the observation pipeline. Log and move on.
            logger.debug("Failed to capture prompt/completion content", e);
        }
    }

    private static String roleOf(MessageType type) {
        if (type == null) return "unknown";
        return switch (type) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + " …(truncated " + (s.length() - max) + " chars)";
    }

    private void putIfNotNull(Map<String, String> attrs, KeyValue kv) {
        if (kv == null || kv.getKey() == null || kv.getValue() == null) return;
        attrs.put(kv.getKey(), kv.getValue());
    }

    private String errorStatus(Observation.Context context) {
        return context.getError() == null ? TraceRecord.STATUS_OK : TraceRecord.STATUS_ERROR;
    }

    private String simpleClassNameFallback(Observation.Context context) {
        String simple = context.getClass().getSimpleName();
        // strip trailing "ObservationContext" / "Context" for readability
        if (simple.endsWith("ObservationContext")) simple = simple.substring(0, simple.length() - "ObservationContext".length());
        else if (simple.endsWith("Context")) simple = simple.substring(0, simple.length() - "Context".length());
        return simple;
    }

    private static final class TraceBuilder {
        private final String traceId;
        private final List<SpanRecord> spans = new ArrayList<>();
        private final long createdAtMs = Instant.now().toEpochMilli();

        TraceBuilder(String traceId) {
            this.traceId = traceId;
        }

        synchronized void addSpan(SpanRecord span, int maxSpans) {
            if (spans.size() >= maxSpans) return;
            spans.add(span);
        }

        synchronized TraceRecord finalizeTrace(SpanRecord rootSpan) {
            Map<String, String> rootAttrs = rootSpan.attributes();
            String conversationId = rootAttrs.getOrDefault("conversation.id",
                    rootAttrs.get("spring.ai.chat.client.conversation.id"));
            String userMessageId = rootAttrs.get("user_message.id");

            String provider = null;
            String model = null;
            Long inputTokens = null;
            Long outputTokens = null;
            Long totalTokens = null;
            String finishReason = null;
            int toolCallCount = 0;
            boolean hasRag = false;

            // Reactive streaming sometimes finalizes the gen_ai child after the root —
            // also read token/model off the root span (populated via enrichWithChatClientResponse).
            for (SpanRecord s : spans) {
                Map<String, String> a = s.attributes();
                if (CHAT_MODEL_SPAN_NAME.equals(s.name()) || ROOT_SPAN_NAME.equals(s.name())) {
                    if (provider == null) provider = nonSentinel(a.get("gen_ai.system"));
                    if (model == null) {
                        String responseModel = nonSentinel(a.get("gen_ai.response.model"));
                        String requestModel = nonSentinel(a.get("gen_ai.request.model"));
                        model = firstNonBlank(responseModel, requestModel);
                    }
                    inputTokens = sumLong(inputTokens, a.get("gen_ai.usage.input_tokens"));
                    outputTokens = sumLong(outputTokens, a.get("gen_ai.usage.output_tokens"));
                    totalTokens = sumLong(totalTokens, a.get("gen_ai.usage.total_tokens"));
                    if (finishReason == null) finishReason = a.get("gen_ai.response.finish_reasons");
                } else if (TOOL_SPAN_NAME.equals(s.name())) {
                    toolCallCount++;
                } else if (VECTOR_STORE_SPAN_NAME.equals(s.name())) {
                    hasRag = true;
                }
            }

            String status = rootSpan.status();
            for (SpanRecord s : spans) {
                if (TraceRecord.STATUS_ERROR.equals(s.status())) {
                    status = TraceRecord.STATUS_ERROR;
                    break;
                }
            }

            return new TraceRecord(
                    traceId,
                    conversationId,
                    userMessageId,
                    provider,
                    model,
                    rootSpan.startEpochMs(),
                    rootSpan.durationMs(),
                    status,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    finishReason,
                    toolCallCount > 0,
                    toolCallCount,
                    hasRag,
                    new ArrayList<>(spans),
                    rootAttrs);
        }

        private String firstNonBlank(String a, String b) {
            return (a != null && !a.isBlank()) ? a : b;
        }

        private static String nonSentinel(String s) {
            if (s == null || s.isBlank()) return null;
            String lower = s.toLowerCase();
            if (lower.equals("none") || lower.equals("unknown") || lower.equals("null")) return null;
            return s;
        }

        private Long sumLong(Long current, String raw) {
            if (raw == null || raw.isBlank()) return current;
            try {
                long v = Long.parseLong(raw.trim());
                return current == null ? v : current + v;
            } catch (NumberFormatException e) {
                return current;
            }
        }
    }
}
