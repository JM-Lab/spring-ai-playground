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
package org.springaicommunity.playground.webui.observability.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SpanTimelineCanvas extends Div {

    private static final int LABEL_COL_PX = 280;
    private static final int ROW_HEIGHT_PX = 24;
    private static final int INDENT_PX = 14;

    public SpanTimelineCanvas(TraceRecord trace) {
        getStyle().set("width", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "2px")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)");
        render(trace);
    }

    private void render(TraceRecord trace) {
        removeAll();
        if (trace == null) return;
        List<SpanRecord> spans = trace.spans();
        if (spans == null || spans.isEmpty()) {
            Span empty = new Span("No spans recorded for this trace.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic");
            add(empty);
            return;
        }

        long traceStart = spans.stream().mapToLong(SpanRecord::startEpochMs).min()
                .orElse(trace.startEpochMs());
        long traceEnd = spans.stream().mapToLong(s -> s.startEpochMs() + s.durationMs()).max()
                .orElse(trace.startEpochMs() + trace.durationMs());
        long totalMs = Math.max(1L, traceEnd - traceStart);

        List<OrderedSpan> ordered = buildOrderedSpans(spans);

        add(buildAxis(totalMs));
        for (OrderedSpan os : ordered) {
            add(buildRow(os, traceStart, totalMs));
        }
    }

    private Div buildAxis(long totalMs) {
        Div axis = new Div();
        axis.getStyle().set("display", "flex")
                .set("align-items", "center")
                .set("padding-bottom", "var(--lumo-space-xs)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");

        Span label = new Span("Span");
        label.getStyle().set("width", LABEL_COL_PX + "px")
                .set("flex", "0 0 " + LABEL_COL_PX + "px")
                .set("padding-right", "var(--lumo-space-s)");
        axis.add(label);

        Div ticks = new Div();
        ticks.getStyle().set("flex", "1 1 auto")
                .set("display", "flex")
                .set("justify-content", "space-between");
        for (int i = 0; i <= 4; i++) {
            long ms = totalMs * i / 4;
            Span t = new Span(FormatUtils.formatDurationMs(ms));
            t.getStyle().set("font-size", "var(--lumo-font-size-xs)");
            ticks.add(t);
        }
        axis.add(ticks);
        return axis;
    }

    private Div buildRow(OrderedSpan os, long traceStart, long totalMs) {
        SpanRecord s = os.span;
        long startOffset = Math.max(0, s.startEpochMs() - traceStart);
        double leftPct = totalMs == 0 ? 0 : startOffset * 100.0 / totalMs;
        double widthPct = totalMs == 0 ? 0 : s.durationMs() * 100.0 / totalMs;
        if (widthPct < 0.5) widthPct = 0.5;

        Div row = new Div();
        row.getStyle().set("display", "flex")
                .set("align-items", "center")
                .set("min-height", ROW_HEIGHT_PX + "px");

        Div labelCell = new Div();
        labelCell.getStyle().set("width", LABEL_COL_PX + "px")
                .set("flex", "0 0 " + LABEL_COL_PX + "px")
                .set("padding-left", (os.depth * INDENT_PX) + "px")
                .set("padding-right", "var(--lumo-space-s)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-xs)");
        Span dot = new Span();
        dot.getStyle().set("width", "8px").set("height", "8px")
                .set("border-radius", "50%")
                .set("background", colorFor(s))
                .set("flex", "0 0 8px");
        Span nameLabel = new Span(s.name() == null ? "(unnamed)" : s.name());
        nameLabel.getStyle().set("overflow", "hidden").set("text-overflow", "ellipsis");
        if ("ERROR".equals(s.status())) {
            nameLabel.getStyle().set("color", "var(--lumo-error-text-color)");
        }
        Tooltip.forComponent(nameLabel).withText(buildTooltip(s))
                .withPosition(Tooltip.TooltipPosition.END);
        labelCell.add(dot, nameLabel);

        Div barTrack = new Div();
        barTrack.getStyle().set("flex", "1 1 auto")
                .set("position", "relative")
                .set("height", (ROW_HEIGHT_PX - 8) + "px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "3px");

        Div bar = new Div();
        bar.getStyle().set("position", "absolute")
                .set("top", "0")
                .set("bottom", "0")
                .set("left", String.format(Locale.ROOT, "%.2f%%", leftPct))
                .set("width", String.format(Locale.ROOT, "%.2f%%", widthPct))
                .set("background", colorFor(s))
                .set("border-radius", "3px")
                .set("opacity", "0.85");
        Tooltip.forComponent(bar).withText(buildTooltip(s))
                .withPosition(Tooltip.TooltipPosition.TOP);
        barTrack.add(bar);

        Span durLabel = new Span(FormatUtils.formatDurationMs(s.durationMs()));
        durLabel.getStyle().set("margin-left", "var(--lumo-space-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("white-space", "nowrap")
                .set("flex", "0 0 auto");

        row.add(labelCell, barTrack, durLabel);
        return row;
    }

    private static String colorFor(SpanRecord s) {
        if ("ERROR".equals(s.status())) return "var(--lumo-error-color)";
        return switch (s.name() == null ? "" : s.name()) {
            case "spring.ai.chat.client" -> "#7e57c2";
            case "gen_ai.client.operation" -> "#42a5f5";
            case "spring.ai.tool" -> "#26a69a";
            case "spring.ai.advisor" -> "#ffa726";
            case "db.vector.client.operation" -> "#66bb6a";
            default -> "#90a4ae";
        };
    }

    private static String buildTooltip(SpanRecord s) {
        StringBuilder b = new StringBuilder();
        b.append(s.name()).append(" · ").append(s.durationMs()).append(" ms · ")
                .append(s.status() == null ? "OK" : s.status());
        if (s.attributes() != null && !s.attributes().isEmpty()) {
            int shown = 0;
            for (Map.Entry<String, String> e : s.attributes().entrySet()) {
                if (shown++ >= 8) {
                    b.append("\n…");
                    break;
                }
                b.append("\n").append(e.getKey()).append(" = ").append(e.getValue());
            }
        }
        return b.toString();
    }

    private static List<OrderedSpan> buildOrderedSpans(List<SpanRecord> spans) {
        // Group children by their declared parentSpanId. Spans whose parent is
        // not in the captured set become roots (orphan-as-root) so the tree
        // always renders something.
        Map<String, SpanRecord> byId = new HashMap<>();
        for (SpanRecord s : spans) {
            if (s.spanId() != null) byId.put(s.spanId(), s);
        }
        Map<String, List<SpanRecord>> childrenOf = new LinkedHashMap<>();
        List<SpanRecord> roots = new ArrayList<>();
        for (SpanRecord s : spans) {
            String parent = s.parentSpanId();
            if (parent == null || parent.isBlank() || !byId.containsKey(parent)) {
                roots.add(s);
            } else {
                childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(s);
            }
        }
        roots.sort(Comparator.comparingLong(SpanRecord::startEpochMs));
        childrenOf.values().forEach(list -> list.sort(Comparator.comparingLong(SpanRecord::startEpochMs)));

        List<OrderedSpan> out = new ArrayList<>(spans.size());
        for (SpanRecord r : roots) walk(r, 0, childrenOf, out);
        return out;
    }

    private static void walk(SpanRecord s, int depth,
            Map<String, List<SpanRecord>> childrenOf, List<OrderedSpan> out) {
        out.add(new OrderedSpan(s, depth));
        List<SpanRecord> kids = childrenOf.get(s.spanId());
        if (kids == null) return;
        for (SpanRecord k : kids) walk(k, depth + 1, childrenOf, out);
    }

    private record OrderedSpan(SpanRecord span, int depth) {}
}
