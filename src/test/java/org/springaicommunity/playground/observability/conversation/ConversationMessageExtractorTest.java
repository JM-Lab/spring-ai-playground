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
package org.springaicommunity.playground.observability.conversation;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.ObservabilityCollector;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.conversation.ConversationMessageExtractor.TraceMessageView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMessageExtractorTest {

    @Test
    void extractsLastUserPromptAndCompletion() {
        Map<String, String> modelAttrs = new LinkedHashMap<>();
        modelAttrs.put("gen_ai.prompt.count", "3");
        modelAttrs.put("gen_ai.prompt.0.role", "system");
        modelAttrs.put("gen_ai.prompt.0.content", "You are a helpful assistant.");
        modelAttrs.put("gen_ai.prompt.1.role", "user");
        modelAttrs.put("gen_ai.prompt.1.content", "Old question");
        modelAttrs.put("gen_ai.prompt.2.role", "user");
        modelAttrs.put("gen_ai.prompt.2.content", "What's the weather?");
        modelAttrs.put("gen_ai.completion.0.role", "assistant");
        modelAttrs.put("gen_ai.completion.0.content", "It's sunny.");
        SpanRecord modelSpan = new SpanRecord("s1", null,
                ObservabilityCollector.CHAT_MODEL_SPAN_NAME, 0L, 100L, "OK", modelAttrs);
        TraceRecord trace = new TraceRecord("t1", "conv1", "msg1", "openai", "gpt-4",
                0L, 100L, "OK", 10L, 5L, 15L, "stop", false, 0, false,
                List.of(modelSpan), Map.of());

        TraceMessageView view = ConversationMessageExtractor.from(trace);

        assertThat(view.userText()).isEqualTo("What's the weather?");
        assertThat(view.assistantText()).isEqualTo("It's sunny.");
        assertThat(view.systemText()).isEqualTo("You are a helpful assistant.");
        assertThat(view.toolCalls()).isEmpty();
    }

    @Test
    void collectsToolCallsInOrder() {
        Map<String, String> modelAttrs = new LinkedHashMap<>();
        modelAttrs.put("gen_ai.prompt.count", "1");
        modelAttrs.put("gen_ai.prompt.0.role", "user");
        modelAttrs.put("gen_ai.prompt.0.content", "Compute 2+3");
        modelAttrs.put("gen_ai.completion.0.content", "The result is 5.");
        SpanRecord modelSpan = new SpanRecord("s1", null,
                ObservabilityCollector.CHAT_MODEL_SPAN_NAME, 0L, 200L, "OK", modelAttrs);

        Map<String, String> toolAttrs = new LinkedHashMap<>();
        toolAttrs.put("spring.ai.tool.definition.name", "calculator");
        toolAttrs.put("gen_ai.tool.arguments", "{\"a\":2,\"b\":3}");
        SpanRecord toolSpan = new SpanRecord("s2", "s1",
                ObservabilityCollector.TOOL_SPAN_NAME, 50L, 30L, "OK", toolAttrs);

        TraceRecord trace = new TraceRecord("t1", "conv1", "msg1", "openai", "gpt-4",
                0L, 200L, "OK", 12L, 4L, 16L, "stop", true, 1, false,
                List.of(modelSpan, toolSpan), Map.of());

        TraceMessageView view = ConversationMessageExtractor.from(trace);

        assertThat(view.toolCalls()).hasSize(1);
        assertThat(view.toolCalls().get(0).name()).isEqualTo("calculator");
        assertThat(view.toolCalls().get(0).arguments()).isEqualTo("{\"a\":2,\"b\":3}");
        assertThat(view.toolCalls().get(0).status()).isEqualTo("OK");
    }

    @Test
    void handlesMissingAttributesGracefully() {
        SpanRecord modelSpan = new SpanRecord("s1", null,
                ObservabilityCollector.CHAT_MODEL_SPAN_NAME, 0L, 100L, "OK", Map.of());
        TraceRecord trace = new TraceRecord("t1", "conv1", "msg1", null, null,
                0L, 100L, "OK", null, null, null, null, false, 0, false,
                List.of(modelSpan), Map.of());

        TraceMessageView view = ConversationMessageExtractor.from(trace);

        assertThat(view.userText()).isNull();
        assertThat(view.assistantText()).isNull();
        assertThat(view.systemText()).isNull();
        assertThat(view.toolCalls()).isEmpty();
    }

    @Test
    void handlesNullTrace() {
        TraceMessageView view = ConversationMessageExtractor.from(null);

        assertThat(view.userText()).isNull();
        assertThat(view.assistantText()).isNull();
        assertThat(view.toolCalls()).isEmpty();
    }

    @Test
    void fallsBackToToolCallsSummaryWhenNoTextCompletion() {
        Map<String, String> modelAttrs = new LinkedHashMap<>();
        modelAttrs.put("gen_ai.prompt.count", "1");
        modelAttrs.put("gen_ai.prompt.0.role", "user");
        modelAttrs.put("gen_ai.prompt.0.content", "Get weather");
        modelAttrs.put("gen_ai.completion.0.tool_calls", "getWeather({\"city\":\"Seoul\"})");
        SpanRecord modelSpan = new SpanRecord("s1", null,
                ObservabilityCollector.CHAT_MODEL_SPAN_NAME, 0L, 100L, "OK", modelAttrs);
        TraceRecord trace = new TraceRecord("t1", "conv1", "msg1", "openai", "gpt-4",
                0L, 100L, "OK", 10L, 0L, 10L, "tool_calls", true, 1, false,
                List.of(modelSpan), Map.of());

        TraceMessageView view = ConversationMessageExtractor.from(trace);

        assertThat(view.assistantText()).isEqualTo("getWeather({\"city\":\"Seoul\"})");
    }
}
