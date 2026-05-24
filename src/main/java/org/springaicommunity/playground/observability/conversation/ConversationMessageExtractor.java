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

import org.springaicommunity.playground.observability.ObservabilityCollector;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConversationMessageExtractor {

    public record ToolCallView(String name, String arguments, long durationMs, String status) {}

    public record TraceMessageView(String userText, String assistantText,
            List<ToolCallView> toolCalls, String systemText) {}

    private ConversationMessageExtractor() {}

    public static TraceMessageView from(TraceRecord trace) {
        if (trace == null || trace.spans() == null) {
            return new TraceMessageView(null, null, List.of(), null);
        }
        String userText = null;
        String assistantText = null;
        String systemText = null;
        List<ToolCallView> tools = new ArrayList<>();
        SpanRecord lastModelSpan = null;
        for (SpanRecord s : trace.spans()) {
            if (ObservabilityCollector.CHAT_MODEL_SPAN_NAME.equals(s.name())) {
                Map<String, String> a = s.attributes();
                if (a == null) continue;
                if (userText == null) {
                    int count = parseInt(a.get("gen_ai.prompt.count"));
                    for (int i = count - 1; i >= 0; i--) {
                        String role = a.get("gen_ai.prompt." + i + ".role");
                        if ("user".equals(role)) {
                            userText = a.get("gen_ai.prompt." + i + ".content");
                            break;
                        }
                    }
                }
                if (systemText == null) {
                    int count = parseInt(a.get("gen_ai.prompt.count"));
                    for (int i = 0; i < count; i++) {
                        if ("system".equals(a.get("gen_ai.prompt." + i + ".role"))) {
                            systemText = a.get("gen_ai.prompt." + i + ".content");
                            break;
                        }
                    }
                }
                lastModelSpan = s;
            } else if (ObservabilityCollector.TOOL_SPAN_NAME.equals(s.name())) {
                Map<String, String> a = s.attributes();
                String name = a == null ? "(unnamed)"
                        : a.getOrDefault("spring.ai.tool.definition.name", "(unnamed)");
                String args = a == null ? "" : a.getOrDefault("gen_ai.tool.arguments",
                        a.getOrDefault("spring.ai.tool.call.arguments", ""));
                tools.add(new ToolCallView(name, args, s.durationMs(),
                        s.status() == null ? "OK" : s.status()));
            }
        }
        if (lastModelSpan != null && lastModelSpan.attributes() != null) {
            assistantText = lastModelSpan.attributes().get("gen_ai.completion.0.content");
            if (assistantText == null) {
                // Fall back to tool_calls summary if there's no text completion
                assistantText = lastModelSpan.attributes().get("gen_ai.completion.0.tool_calls");
            }
        }
        return new TraceMessageView(userText, assistantText, tools, systemText);
    }

    private static int parseInt(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
