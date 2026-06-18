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

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.observability.conversation.ConversationMessageExtractor;
import org.springaicommunity.playground.observability.conversation.ConversationMessageExtractor.TraceMessageView;
import org.springaicommunity.playground.observability.conversation.ConversationMessageExtractor.ToolCallView;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class ConversationThreadDialog extends Dialog {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public ConversationThreadDialog(String conversationId, ObservabilityRingBuffer buffer) {
        this(conversationId, buffer, null);
    }

    public ConversationThreadDialog(String conversationId, ObservabilityRingBuffer buffer,
            String focusTraceId) {
        setWidth("960px");
        setHeight("760px");
        setModality(ModalityMode.STRICT);
        setDraggable(true);
        setResizable(true);
        setHeaderTitle("Conversation " + shorten(conversationId, 16));

        List<TraceRecord> traces = buffer.snapshot().stream()
                .filter(t -> conversationId != null && conversationId.equals(t.conversationId()))
                .sorted(Comparator.comparingLong(TraceRecord::startEpochMs))
                .toList();

        VerticalLayout body = new VerticalLayout();
        body.setSizeFull();
        body.setPadding(false);
        body.setSpacing(false);
        body.getStyle().set("gap", "var(--lumo-space-m)");

        body.add(buildSummary(traces, conversationId));

        if (traces.isEmpty()) {
            Span empty = new Span("No traces found for this conversation.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic")
                    .set("padding", "var(--lumo-space-l)");
            body.add(empty);
        } else {
            Div thread = new Div();
            thread.getStyle().set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "var(--lumo-space-m)")
                    .set("padding", "var(--lumo-space-s)");
            String lastSystem = null;
            for (int i = 0; i < traces.size(); i++) {
                TraceRecord t = traces.get(i);
                TraceMessageView view = ConversationMessageExtractor.from(t);
                if (i == 0 && view.systemText() != null && !view.systemText().isBlank()) {
                    thread.add(buildSystemBlock(view.systemText()));
                    lastSystem = view.systemText();
                } else if (view.systemText() != null
                        && !view.systemText().equals(lastSystem)
                        && !view.systemText().isBlank()) {
                    thread.add(buildSystemBlock(view.systemText()));
                    lastSystem = view.systemText();
                }
                thread.add(buildTurnBlock(t, view, focusTraceId));
            }
            body.add(thread);
        }

        Button continueButton = new Button("Continue in chat", VaadinIcon.CHAT.create(), e -> {
            close();
            UI.getCurrent().navigate("agentic-chat?conv=" + conversationId);
        });
        continueButton.setEnabled(conversationId != null && !conversationId.isBlank() && !traces.isEmpty());
        getFooter().add(continueButton, new Button("Close", e -> close()));
        add(body);
    }

    private Div buildSummary(List<TraceRecord> traces, String conversationId) {
        Div summary = new Div();
        summary.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(4, minmax(0, 1fr))")
                .set("gap", "var(--lumo-space-s)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)");

        long inTokens = 0;
        long outTokens = 0;
        long durationMs = 0;
        int toolCalls = 0;
        for (TraceRecord t : traces) {
            inTokens += t.inputTokens() == null ? 0 : t.inputTokens();
            outTokens += t.outputTokens() == null ? 0 : t.outputTokens();
            durationMs += t.durationMs();
            toolCalls += t.toolCallCount();
        }
        long span = traces.isEmpty() ? 0
                : (traces.getLast().startEpochMs()
                        + traces.getLast().durationMs())
                        - traces.getFirst().startEpochMs();

        summary.add(stat("Messages", String.valueOf(traces.size())));
        summary.add(stat("Conv span", FormatUtils.formatDurationMs(span)));
        summary.add(stat("Tokens", inTokens + " in / " + outTokens + " out"));
        summary.add(stat("Tools", String.valueOf(toolCalls)));
        return summary;
    }

    private Div stat(String label, String value) {
        Div cell = new Div();
        Span k = new Span(label);
        k.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("display", "block");
        Span v = new Span(value);
        v.getStyle().set("font-weight", "600");
        cell.add(k, v);
        return cell;
    }

    private Div buildSystemBlock(String text) {
        Div block = new Div();
        block.getStyle().set("background", "var(--lumo-contrast-5pct)")
                .set("border-left", "3px solid var(--lumo-contrast-40pct)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("white-space", "pre-wrap")
                .set("color", "var(--lumo-secondary-text-color)");
        Span label = new Span("SYSTEM");
        label.getStyle().set("color", "var(--lumo-contrast-60pct)")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "700")
                .set("letter-spacing", "0.05em")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");
        Div content = new Div();
        content.setText(text);
        block.add(label, content);
        return block;
    }

    private Div buildTurnBlock(TraceRecord trace, TraceMessageView view, String focusTraceId) {
        Div turn = new Div();
        turn.getStyle().set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)");
        boolean focus = trace.traceId() != null && trace.traceId().equals(focusTraceId);
        if (focus) {
            turn.getStyle().set("background", "var(--lumo-primary-color-10pct)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("padding", "var(--lumo-space-s)");
        }

        if (view.userText() != null && !view.userText().isBlank()) {
            turn.add(buildMessageBubble("USER", view.userText(),
                    "var(--lumo-primary-color-10pct)",
                    "var(--lumo-primary-text-color)", true));
        }

        for (ToolCallView tc : view.toolCalls()) {
            turn.add(buildToolCallChip(tc));
        }

        if (view.assistantText() != null && !view.assistantText().isBlank()) {
            turn.add(buildMessageBubble("ASSISTANT", view.assistantText(),
                    "var(--lumo-base-color)",
                    "var(--lumo-body-text-color)", false));
        } else if (view.toolCalls().isEmpty()) {
            turn.add(buildMessageBubble("ASSISTANT", "(no completion text captured — enable "
                    + "spring.ai.playground.observability.capture-prompt-content=true)",
                    "var(--lumo-base-color)",
                    "var(--lumo-secondary-text-color)", false));
        }

        turn.add(buildTurnFooter(trace));
        return turn;
    }

    private Div buildMessageBubble(String label, String text, String bg, String fg, boolean alignEnd) {
        Div wrap = new Div();
        wrap.getStyle().set("display", "flex")
                .set("justify-content", alignEnd ? "flex-end" : "flex-start");

        Div bubble = new Div();
        bubble.getStyle().set("background", bg)
                .set("color", fg)
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("max-width", "78%")
                .set("white-space", "pre-wrap")
                .set("word-wrap", "break-word")
                .set("font-size", "var(--lumo-font-size-s)");

        Span tag = new Span(label);
        tag.getStyle().set("color", "var(--lumo-contrast-60pct)")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "700")
                .set("letter-spacing", "0.05em")
                .set("display", "block")
                .set("margin-bottom", "var(--lumo-space-xs)");
        Div content = new Div();
        content.setText(text);
        bubble.add(tag, content);
        wrap.add(bubble);
        return wrap;
    }

    private Div buildToolCallChip(ToolCallView tc) {
        Div wrap = new Div();
        wrap.getStyle().set("display", "flex")
                .set("justify-content", "center")
                .set("padding-left", "var(--lumo-space-l)")
                .set("padding-right", "var(--lumo-space-l)");

        Div chip = new Div();
        boolean error = "ERROR".equals(tc.status());
        chip.getStyle().set("background", error
                        ? "var(--lumo-error-color-10pct)"
                        : "var(--lumo-contrast-5pct)")
                .set("border", "1px dashed " + (error
                        ? "var(--lumo-error-color-50pct)"
                        : "var(--lumo-contrast-20pct)"))
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("max-width", "78%")
                .set("color", error ? "var(--lumo-error-text-color)" : "var(--lumo-body-text-color)");

        Span head = new Span("🛠 " + tc.name() + " · " + tc.durationMs() + " ms"
                + (error ? " · ERROR" : ""));
        head.getStyle().set("font-weight", "600").set("display", "block");
        chip.add(head);
        if (tc.arguments() != null && !tc.arguments().isBlank()) {
            Div args = new Div();
            args.setText(truncate(tc.arguments(), 240));
            args.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("margin-top", "var(--lumo-space-xs)")
                    .set("white-space", "pre-wrap");
            chip.add(args);
        }
        wrap.add(chip);
        return wrap;
    }

    private Div buildTurnFooter(TraceRecord trace) {
        Div footer = new Div();
        footer.getStyle().set("display", "flex")
                .set("justify-content", "flex-end")
                .set("gap", "var(--lumo-space-s)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding-right", "var(--lumo-space-s)");

        long inTok = trace.inputTokens() == null ? 0 : trace.inputTokens();
        long outTok = trace.outputTokens() == null ? 0 : trace.outputTokens();
        Span meta = new Span(TIME_FMT.format(Instant.ofEpochMilli(trace.startEpochMs()))
                + " · " + (trace.model() == null ? "—" : trace.model())
                + " · " + inTok + "/" + outTok + " tok"
                + " · " + FormatUtils.formatDurationMs(trace.durationMs()));
        footer.add(meta);
        return footer;
    }

    private static String shorten(String s, int n) {
        if (s == null) return "—";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + " …";
    }

}
