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
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.testfake.FakeChatClientObservationContext;
import org.springframework.ai.testfake.FakeSpringAiObservationContext;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObservabilityCollectorTest {

    private ObservabilityProperties props;
    private ObservabilityRingBuffer buffer;

    @BeforeEach
    void setUp() {
        props = new ObservabilityProperties();
        props.setPersist(false);
        props.setRingBufferCapacity(100);
        buffer = new ObservabilityRingBuffer(props);
    }

    @Test
    void supportsContextAcceptsSpringAiAndGenaiAndVectorNamesAndRejectsOthers() {
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), absent(), absent(), props);

        Observation.Context chatClient = ctx("spring.ai.chat.client");
        Observation.Context chatModel = ctx("gen_ai.client.operation");
        Observation.Context vector = ctx("db.vector.client.operation");
        Observation.Context tool = ctx("spring.ai.tool");
        Observation.Context advisor = ctx("spring.ai.advisor");
        Observation.Context unrelated = ctx("http.server.requests");

        assertThat(collector.supportsContext(chatClient)).isTrue();
        assertThat(collector.supportsContext(chatModel)).isTrue();
        assertThat(collector.supportsContext(vector)).isTrue();
        assertThat(collector.supportsContext(tool)).isTrue();
        assertThat(collector.supportsContext(advisor)).isTrue();
        assertThat(collector.supportsContext(unrelated)).isFalse();
        assertThat(collector.supportsContext(null)).isFalse();
    }

    @Test
    void rootFinishAssemblesTraceWithProviderModelAndTokenTotals() {
        Tracer tracer = mockTracer("aabbcc", List.of("rootspan", "childspan"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        root.addLowCardinalityKeyValue(KeyValue.of("spring.ai.chat.client.conversation.id", "conv-X"));

        Observation.Context child = ctx("gen_ai.client.operation");
        child.addLowCardinalityKeyValue(KeyValue.of("gen_ai.system", "ollama"));
        child.addLowCardinalityKeyValue(KeyValue.of("gen_ai.request.model", "qwen3.5:9b"));
        child.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.input_tokens", "120"));
        child.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.output_tokens", "340"));
        child.addHighCardinalityKeyValue(KeyValue.of("gen_ai.response.finish_reasons", "stop"));

        collector.onStart(root);
        collector.onStart(child);
        collector.onStop(child);
        collector.onStop(root);

        List<TraceRecord> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(1);
        TraceRecord t = snapshot.get(0);
        assertThat(t.traceId()).isEqualTo("aabbcc");
        assertThat(t.provider()).isEqualTo("ollama");
        assertThat(t.model()).isEqualTo("qwen3.5:9b");
        assertThat(t.inputTokens()).isEqualTo(120);
        assertThat(t.outputTokens()).isEqualTo(340);
        assertThat(t.finishReason()).isEqualTo("stop");
        assertThat(t.spans()).hasSize(2);
        assertThat(t.status()).isEqualTo(TraceRecord.STATUS_OK);
    }

    @Test
    void duplicateSpanIdIsNotDoubleCounted() {
        Tracer tracer = mockTracer("duptrace", List.of("modelspan", "modelspan", "rootspan"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        Observation.Context m1 = ctx("gen_ai.client.operation");
        m1.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.input_tokens", "100"));
        m1.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.output_tokens", "200"));
        Observation.Context m2 = ctx("gen_ai.client.operation");
        m2.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.input_tokens", "100"));
        m2.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.output_tokens", "200"));

        collector.onStart(m1);
        collector.onStart(m2);
        collector.onStart(root);
        collector.onStop(m1);
        collector.onStop(m2);
        collector.onStop(root);

        List<TraceRecord> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(1);
        TraceRecord t = snapshot.get(0);
        assertThat(t.inputTokens()).isEqualTo(100);
        assertThat(t.outputTokens()).isEqualTo(200);
        assertThat(t.spans()).hasSize(2);
    }

    @Test
    void errorInAnySpanMarksTraceStatusError() {
        Tracer tracer = mockTracer("dead", List.of("r", "c"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        Observation.Context child = ctx("gen_ai.client.operation");
        child.setError(new RuntimeException("boom"));

        collector.onStart(root);
        collector.onStart(child);
        collector.onStop(child);
        collector.onStop(root);

        List<TraceRecord> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).status()).isEqualTo(TraceRecord.STATUS_ERROR);
    }

    @Test
    void nonRootObservationAloneDoesNotFinalizeTrace() {
        Tracer tracer = mockTracer("xx", List.of("only"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context child = ctx("gen_ai.client.operation");
        collector.onStart(child);
        collector.onStop(child);

        assertThat(buffer.snapshot()).isEmpty();
    }

    @Test
    void supportsContextMatchesClassInOrgSpringframeworkAiPackageWhenNameIsNull() {
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), absent(), absent(), props);
        Observation.Context fake = new FakeSpringAiObservationContext();
        assertThat(fake.getName()).isNull();
        assertThat(collector.supportsContext(fake)).isTrue();
    }

    @Test
    void chatClientClassSuffixFinalizesRootEvenWhenNameIsNull() {
        Tracer tracer = mockTracer("rootByClass", List.of("rootspan"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = new FakeChatClientObservationContext();

        collector.onStart(root);
        collector.onStop(root);

        assertThat(buffer.snapshot()).hasSize(1);
        assertThat(buffer.snapshot().get(0).traceId()).isEqualTo("rootByClass");
    }

    @Test
    void maxSpansPerTraceCapsRunawayToolLoop() {
        props.setMaxSpansPerTrace(3);
        Tracer tracer = mockTracer("cap", List.of("r", "c1", "c2", "c3", "c4", "c5"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        collector.onStart(root);
        for (int i = 0; i < 5; i++) {
            Observation.Context tool = ctx("spring.ai.tool");
            collector.onStart(tool);
            collector.onStop(tool);
        }
        collector.onStop(root);

        assertThat(buffer.snapshot()).hasSize(1);
        assertThat(buffer.snapshot().get(0).spans()).hasSize(3);
    }

    @Test
    void hasToolsAndToolCallCountDerivedFromToolSpans() {
        Tracer tracer = mockTracer("withtools", List.of("r", "t1", "t2"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        Observation.Context tool1 = ctx("spring.ai.tool");
        Observation.Context tool2 = ctx("spring.ai.tool");

        collector.onStart(root);
        collector.onStart(tool1);
        collector.onStop(tool1);
        collector.onStart(tool2);
        collector.onStop(tool2);
        collector.onStop(root);

        TraceRecord t = buffer.snapshot().get(0);
        assertThat(t.hasTools()).isTrue();
        assertThat(t.toolCallCount()).isEqualTo(2);
    }

    @Test
    void hasRagFlagIsSetWhenVectorStoreSpanPresent() {
        Tracer tracer = mockTracer("ragtrace", List.of("r", "v"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        Observation.Context vector = ctx("db.vector.client.operation");

        collector.onStart(root);
        collector.onStart(vector);
        collector.onStop(vector);
        collector.onStop(root);

        TraceRecord t = buffer.snapshot().get(0);
        assertThat(t.hasRag()).isTrue();
    }

    @Test
    void multiChatModelSpansSumTokenCounts() {
        Tracer tracer = mockTracer("sumtokens", List.of("r", "m1", "m2"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        Observation.Context m1 = ctx("gen_ai.client.operation");
        m1.addLowCardinalityKeyValue(KeyValue.of("gen_ai.system", "openai"));
        m1.addLowCardinalityKeyValue(KeyValue.of("gen_ai.request.model", "gpt-5.4-mini"));
        m1.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.input_tokens", "100"));
        m1.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.output_tokens", "50"));
        Observation.Context m2 = ctx("gen_ai.client.operation");
        m2.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.input_tokens", "30"));
        m2.addHighCardinalityKeyValue(KeyValue.of("gen_ai.usage.output_tokens", "70"));

        collector.onStart(root);
        collector.onStart(m1);
        collector.onStop(m1);
        collector.onStart(m2);
        collector.onStop(m2);
        collector.onStop(root);

        TraceRecord t = buffer.snapshot().get(0);
        assertThat(t.inputTokens()).isEqualTo(130L);
        assertThat(t.outputTokens()).isEqualTo(120L);
        assertThat(t.provider()).isEqualTo("openai");
        assertThat(t.model()).isEqualTo("gpt-5.4-mini");
    }

    @Test
    void cleanupStaleActiveTracesEvictsAbandonedBuilders() {
        Tracer tracer = mockTracer("orphan", List.of("only"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context child = ctx("gen_ai.client.operation");
        collector.onStart(child);
        collector.onStop(child);
        assertThat(collector.activeSize()).isEqualTo(1);

        int evicted = collector.evictActiveOlderThan(System.currentTimeMillis() + 60_000);
        assertThat(evicted).isEqualTo(1);
        assertThat(collector.activeSize()).isZero();
    }

    @Test
    void cleanupKeepsRecentActiveBuilders() {
        Tracer tracer = mockTracer("fresh", List.of("only"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context child = ctx("gen_ai.client.operation");
        collector.onStart(child);
        collector.onStop(child);

        int evicted = collector.evictActiveOlderThan(System.currentTimeMillis() - 60_000);
        assertThat(evicted).isZero();
        assertThat(collector.activeSize()).isEqualTo(1);
    }

    @Test
    void fallbackTraceIdWhenNoTracerUsesLocalPrefix() {
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), absent(), absent(), props);

        Observation.Context root = new FakeChatClientObservationContext();
        collector.onStart(root);
        collector.onStop(root);

        assertThat(buffer.snapshot()).hasSize(1);
        assertThat(buffer.snapshot().get(0).traceId()).startsWith("local-");
    }

    @Test
    void streamingToolSpanDoubleStopEmitsSingleSpan() {
        Tracer tracer = mockTracer("tooltrace", List.of("ts1"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        Observation.Context tool = ctx("spring.ai.tool");
        collector.onStart(tool);
        collector.onStop(tool);
        collector.onStop(tool);

        List<TraceRecord> snapshot = buffer.snapshot();
        assertThat(snapshot).hasSize(1);
        assertThat(snapshot.get(0).spans()).hasSize(1);
        assertThat(snapshot.get(0).toolCallCount()).isEqualTo(1);
    }

    @Test
    void lateSpanMergesIntoRootAndPersistsMergedRecord() {
        props.setPersist(true);
        ObservabilityPersistenceService persist = mock(ObservabilityPersistenceService.class);
        Tracer tracer = mockTracer("roottrace", List.of("r1", "late1"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                present(persist), present(tracer), absent(), props);

        Observation.Context root = ctx("spring.ai.chat.client");
        collector.onStart(root);
        collector.onStop(root);

        Observation.Context late = ctx("gen_ai.client.operation");
        collector.onStart(late);
        collector.onStop(late);

        ArgumentCaptor<TraceRecord> saved = ArgumentCaptor.forClass(TraceRecord.class);
        verify(persist, times(2)).saveAsync(saved.capture());
        assertThat(saved.getAllValues().get(1).spans()).hasSize(2);
        assertThat(buffer.snapshot()).hasSize(1);
    }

    @Test
    void lateSpanOnBufferMissDoesNotOverwritePersistedRoot() {
        ObservabilityProperties smallProps = new ObservabilityProperties();
        smallProps.setPersist(true);
        smallProps.setRingBufferCapacity(10);
        ObservabilityRingBuffer smallBuffer = new ObservabilityRingBuffer(smallProps);
        ObservabilityPersistenceService persist = mock(ObservabilityPersistenceService.class);
        Tracer tracer = mockTracer("roottrace", List.of("r1", "late1"));
        ObservabilityCollector collector = new ObservabilityCollector(smallBuffer,
                present(persist), present(tracer), absent(), smallProps);

        Observation.Context root = ctx("spring.ai.chat.client");
        collector.onStart(root);
        collector.onStop(root);
        for (int i = 0; i < 10; i++) {
            smallBuffer.add(fillerTrace("filler-" + i));
        }

        Observation.Context late = ctx("gen_ai.client.operation");
        collector.onStart(late);
        collector.onStop(late);

        ArgumentCaptor<TraceRecord> saved = ArgumentCaptor.forClass(TraceRecord.class);
        verify(persist, times(1)).saveAsync(saved.capture());
        assertThat(saved.getValue().spans()).extracting(SpanRecord::spanId).containsExactly("r1");
    }

    private TraceRecord fillerTrace(String id) {
        return new TraceRecord(id, null, null, null, null,
                System.currentTimeMillis(), 10L, TraceRecord.STATUS_OK,
                null, null, null, null, false, 0, false,
                null, null, null, null, List.of(), Map.of());
    }

    @Test
    void chatModelSpanCapturesPromptAndCompletionContent() {
        Tracer tracer = mockTracer("cmcontent", List.of("rootspan", "modelspan"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        ChatModelObservationContext child = ChatModelObservationContext.builder()
                .prompt(new Prompt(List.of(new SystemMessage("be brief"), new UserMessage("hello"))))
                .provider("ollama")
                .build();
        child.setName("gen_ai.client.operation");
        AssistantMessage output = AssistantMessage.builder()
                .content("hi there")
                .toolCalls(List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Seoul\"}")))
                .build();
        child.setResponse(new ChatResponse(List.of(new Generation(output))));

        Observation.Context root = ctx("spring.ai.chat.client");
        collector.onStart(root);
        collector.onStart(child);
        collector.onStop(child);
        collector.onStop(root);

        Map<String, String> attrs = buffer.snapshot().get(0).spans().stream()
                .map(SpanRecord::attributes)
                .filter(spanAttrs -> spanAttrs.containsKey("gen_ai.prompt.count"))
                .findFirst().orElseThrow();
        assertThat(attrs)
                .containsEntry("gen_ai.prompt.count", "2")
                .containsEntry("gen_ai.prompt.0.role", "system")
                .containsEntry("gen_ai.prompt.0.content", "be brief")
                .containsEntry("gen_ai.prompt.1.role", "user")
                .containsEntry("gen_ai.prompt.1.content", "hello")
                .containsEntry("gen_ai.completion.0.role", "assistant")
                .containsEntry("gen_ai.completion.0.content", "hi there");
        assertThat(attrs.get("gen_ai.completion.0.tool_calls")).contains("getWeather");
    }

    @Test
    void chatModelPromptCaptureHonorsMessageCapAndMarksTruncation() {
        props.setMaxCapturedMessagesPerSpan(1);
        Tracer tracer = mockTracer("cmcap", List.of("rootspan", "modelspan"));
        ObservabilityCollector collector = new ObservabilityCollector(buffer,
                absent(), present(tracer), absent(), props);

        ChatModelObservationContext child = ChatModelObservationContext.builder()
                .prompt(new Prompt(List.of(new UserMessage("first"), new UserMessage("second"))))
                .provider("ollama")
                .build();
        child.setName("gen_ai.client.operation");

        Observation.Context root = ctx("spring.ai.chat.client");
        collector.onStart(root);
        collector.onStart(child);
        collector.onStop(child);
        collector.onStop(root);

        Map<String, String> attrs = buffer.snapshot().get(0).spans().stream()
                .map(SpanRecord::attributes)
                .filter(spanAttrs -> spanAttrs.containsKey("gen_ai.prompt.count"))
                .findFirst().orElseThrow();
        assertThat(attrs)
                .containsEntry("gen_ai.prompt.count", "2")
                .containsEntry("gen_ai.prompt.truncated_messages", "1")
                .containsEntry("gen_ai.prompt.0.content", "first")
                .doesNotContainKey("gen_ai.prompt.1.content");
    }

    private Observation.Context ctx(String name) {
        Observation.Context c = new Observation.Context();
        c.setName(name);
        return c;
    }

    private Tracer mockTracer(String traceId, List<String> spanIds) {
        Tracer t = mock(Tracer.class);
        when(t.currentSpan()).thenAnswer(new Answer<Span>() {
            int idx = 0;
            @Override public Span answer(InvocationOnMock inv) {
                String spanId = spanIds.get(Math.min(idx++, spanIds.size() - 1));
                return mockSpan(traceId, spanId);
            }
        });
        return t;
    }

    private Span mockSpan(String traceId, String spanId) {
        Span s = mock(Span.class);
        TraceContext tc = mock(TraceContext.class);
        when(tc.traceId()).thenReturn(traceId);
        when(tc.spanId()).thenReturn(spanId);
        when(tc.parentId()).thenReturn(null);
        when(s.context()).thenReturn(tc);
        return s;
    }

    private <T> ObjectProvider<T> absent() {
        return new ObjectProvider<>() {
            @Override public T getObject() { throw new RuntimeException("not available"); }
            @Override public T getObject(Object... args) { throw new RuntimeException("not available"); }
            @Override public T getIfAvailable() { return null; }
            @Override public T getIfAvailable(Supplier<T> defaultSupplier) { return defaultSupplier.get(); }
            @Override public T getIfUnique() { return null; }
            @Override public T getIfUnique(Supplier<T> defaultSupplier) { return defaultSupplier.get(); }
            @Override public void ifAvailable(Consumer<T> dependencyConsumer) {}
            @Override public void ifUnique(Consumer<T> dependencyConsumer) {}
        };
    }

    private <T> ObjectProvider<T> present(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfAvailable(Supplier<T> defaultSupplier) { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getIfUnique(Supplier<T> defaultSupplier) { return value; }
            @Override public void ifAvailable(Consumer<T> dependencyConsumer) { dependencyConsumer.accept(value); }
            @Override public void ifUnique(Consumer<T> dependencyConsumer) { dependencyConsumer.accept(value); }
        };
    }
}
