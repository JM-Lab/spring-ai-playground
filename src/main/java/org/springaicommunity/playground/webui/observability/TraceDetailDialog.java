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
package org.springaicommunity.playground.webui.observability;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.webui.observability.components.ObservabilityNavLinks;
import org.springaicommunity.playground.webui.observability.components.SpanTimelineCanvas;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TraceDetailDialog extends Dialog {

    private static final ObjectMapper PRETTY_MAPPER = JsonMapper.builder()
            .configure(SerializationFeature.INDENT_OUTPUT, true).build();

    public TraceDetailDialog(TraceRecord trace) {
        this(trace, null);
    }

    public TraceDetailDialog(TraceRecord trace, ObservabilityRingBuffer buffer) {
        setWidth("960px");
        setHeight("720px");
        setModality(ModalityMode.STRICT);
        setDraggable(true);
        setResizable(true);
        setHeaderTitle("Trace " + shorten(trace.traceId(), 12));

        VerticalLayout body = new VerticalLayout();
        body.setSizeFull();
        body.setPadding(false);
        body.setSpacing(true);

        body.add(buildSummary(trace));
        body.add(new H3("Timeline"));
        body.add(new SpanTimelineCanvas(trace));
        body.add(new H3("Spans"));
        body.add(buildSpansList(trace));

        Button jsonButton = new Button("Show raw JSON", e -> showRawJson(trace));
        Button closeButton = new Button("Close", e -> close());
        if (buffer != null && trace.conversationId() != null && !trace.conversationId().isBlank()) {
            getFooter().add(new Button("Open conversation thread", e -> {
                close();
                new ConversationThreadDialog(trace.conversationId(), buffer, trace.traceId()).open();
            }));
        }
        if (trace.serverNames() != null) {
            for (String server : trace.serverNames()) {
                getFooter().add(new Button("Server: " + server, e -> {
                    close();
                    ObservabilityNavLinks.openMcpServer(server);
                }));
            }
        }
        if (trace.toolNames() != null) {
            for (String tool : trace.toolNames()) {
                getFooter().add(new Button("Tool: " + tool, e -> {
                    close();
                    ObservabilityNavLinks.openTool(tool);
                }));
            }
        }
        getFooter().add(jsonButton, closeButton);

        add(body);
    }

    private Div buildSummary(TraceRecord trace) {
        Div div = new Div();
        div.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(2, minmax(0, 1fr))")
                .set("gap", "var(--lumo-space-s)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        div.add(line("Provider", nullSafe(trace.provider())));
        div.add(line("Model", nullSafe(trace.model())));
        div.add(line("Status", nullSafe(trace.status())));
        div.add(line("Duration", trace.durationMs() + " ms"));
        div.add(line("In tokens", String.valueOf(orZero(trace.inputTokens()))));
        div.add(line("Out tokens", String.valueOf(orZero(trace.outputTokens()))));
        div.add(line("Tools", String.valueOf(trace.toolCallCount())));
        div.add(line("RAG", trace.hasRag() ? "yes" : "no"));
        div.add(line("Conversation", nullSafe(trace.conversationId())));
        div.add(line("Session", nullSafe(trace.sessionId())));
        div.add(line("User", nullSafe(trace.userId())));
        div.add(line("Finish reason", nullSafe(trace.finishReason())));
        return div;
    }

    private HorizontalLayout line(String label, String value) {
        Span k = new Span(label);
        k.getStyle().set("color", "var(--lumo-secondary-text-color)").set("min-width", "8rem");
        Span v = new Span(value);
        v.getStyle().set("font-weight", "500");
        HorizontalLayout row = new HorizontalLayout(k, v);
        row.setSpacing(true);
        return row;
    }

    private Div buildSpansList(TraceRecord trace) {
        Div container = new Div();
        container.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-xs)");
        if (trace.spans() == null) return container;
        for (SpanRecord s : trace.spans()) {
            container.add(buildSpanCard(s));
        }
        return container;
    }

    private Div buildSpanCard(SpanRecord s) {
        Div row = new Div();
        row.getStyle().set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border", "1px solid " + ("ERROR".equals(s.status())
                        ? "var(--lumo-error-color-50pct)"
                        : "var(--lumo-contrast-10pct)"))
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-xs)");

        Span head = new Span(String.format(Locale.ROOT, "%s  ·  %s ms  ·  status=%s",
                s.name(), s.durationMs(), s.status()));
        head.getStyle().set("font-weight", "600");
        row.add(head);

        Map<String, String> attrs = s.attributes();
        if (attrs == null || attrs.isEmpty()) return row;

        Set<String> highlightKeys = new LinkedHashSet<>();
        for (String k : new String[] {
                "gen_ai.system", "gen_ai.request.model", "gen_ai.response.model",
                "gen_ai.usage.input_tokens", "gen_ai.usage.output_tokens",
                "gen_ai.response.finish_reasons",
                "spring.ai.tool.definition.name", "saip.mcp.server",
                "mcp.method.name", "network.transport", "network.protocol.name",
                "db.system", "db.vector.query.top_k",
                "error.type", "error.message"
        }) {
            if (attrs.containsKey(k)) highlightKeys.add(k);
        }
        if (!highlightKeys.isEmpty()) {
            Div meta = new Div();
            meta.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("padding-left", "var(--lumo-space-s)");
            StringBuilder sb = new StringBuilder();
            for (String k : highlightKeys) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(k).append("=").append(attrs.get(k));
            }
            meta.setText(sb.toString());
            row.add(meta);
        }

        addContentBlocks(row, attrs);

        Set<String> remaining = new LinkedHashSet<>(attrs.keySet());
        remaining.removeAll(highlightKeys);
        remaining.removeIf(k -> k.startsWith("gen_ai.prompt.") || k.startsWith("gen_ai.completion."));
        if (!remaining.isEmpty()) {
            Div pre = new Div();
            pre.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("white-space", "pre-wrap")
                    .set("word-break", "break-word")
                    .set("padding-left", "var(--lumo-space-s)");
            StringBuilder sb = new StringBuilder();
            for (String k : remaining) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(k).append(" = ").append(attrs.get(k));
            }
            pre.setText(sb.toString());
            Details details = new Details("All attributes (" + remaining.size() + ")", pre);
            details.setOpened(false);
            row.add(details);
        }
        return row;
    }

    private void addContentBlocks(Div row, Map<String, String> attrs) {
        String countStr = attrs.get("gen_ai.prompt.count");
        int count = parseIntSafe(countStr);
        for (int i = 0; i < count; i++) {
            String role = attrs.get("gen_ai.prompt." + i + ".role");
            String content = attrs.get("gen_ai.prompt." + i + ".content");
            if (role == null && content == null) continue;
            row.add(buildContentBlock("prompt[" + i + "] " + (role == null ? "?" : role), content));
        }
        String truncated = attrs.get("gen_ai.prompt.truncated_messages");
        if (truncated != null && !"0".equals(truncated)) {
            Span hint = new Span("(+ " + truncated + " older messages not captured)");
            hint.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("padding-left", "var(--lumo-space-s)");
            row.add(hint);
        }
        String complRole = attrs.get("gen_ai.completion.0.role");
        String complContent = attrs.get("gen_ai.completion.0.content");
        String toolCalls = attrs.get("gen_ai.completion.0.tool_calls");
        if (complRole != null || complContent != null || toolCalls != null) {
            row.add(buildContentBlock("completion " + (complRole == null ? "assistant" : complRole),
                    complContent != null ? complContent : ("tool_calls: " + toolCalls)));
        }
    }

    private Div buildContentBlock(String label, String content) {
        Div block = new Div();
        block.getStyle().set("background", "var(--lumo-contrast-5pct)")
                .set("border-left", "2px solid var(--lumo-primary-color-50pct)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-s)");
        Span tag = new Span(label);
        tag.getStyle().set("color", "var(--lumo-contrast-60pct)")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "700")
                .set("letter-spacing", "0.05em")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");
        Div body = new Div();
        body.setText(content == null ? "(empty)" : content);
        body.getStyle().set("white-space", "pre-wrap")
                .set("word-break", "break-word")
                .set("max-height", "200px")
                .set("overflow-y", "auto");
        block.add(tag, body);
        return block;
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showRawJson(TraceRecord trace) {
        Dialog jsonDialog = new Dialog();
        jsonDialog.setWidth("720px");
        jsonDialog.setHeight("520px");
        jsonDialog.setHeaderTitle("Raw trace JSON");
        Pre pre = new Pre();
        pre.getStyle().set("white-space", "pre-wrap").set("font-size", "var(--lumo-font-size-xs)");
        try {
            pre.setText(PRETTY_MAPPER.writeValueAsString(trace));
        } catch (Exception e) {
            pre.setText("Failed to serialise: " + e.getMessage());
        }
        jsonDialog.add(pre);
        jsonDialog.getFooter().add(new Button("Close", e -> jsonDialog.close()));
        jsonDialog.open();
    }

    private String shorten(String s, int n) {
        if (s == null) return "—";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private String nullSafe(String s) { return s == null ? "—" : s; }

    private long orZero(Long v) { return v == null ? 0L : v; }
}
