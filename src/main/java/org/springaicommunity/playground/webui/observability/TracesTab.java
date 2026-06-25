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

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.SpanTimelineCanvas;
import reactor.core.Disposable;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TracesTab extends BaseDashboardTab {

    private final ObservabilityRingBuffer buffer;
    private final Div traceList;
    private final ComboBox<String> modelFilter = new ComboBox<>("Model");
    private final ComboBox<String> statusFilter = new ComboBox<>("Status");
    private final TextField conversationFilter = new TextField("Conv id contains");
    private final ComboBox<String> sessionIdFilter = new ComboBox<>("Session");
    private final ComboBox<String> toolFilter = new ComboBox<>("Tool");
    private final ComboBox<String> serverFilter = new ComboBox<>("MCP server");

    private Disposable subscription;
    private Consumer<TraceRecord> rowClickListener;
    private boolean refreshing;
    private String expandedTraceId;

    public TracesTab(ObservabilityRingBuffer buffer) {
        super();
        this.buffer = buffer;

        statusFilter.setItems("ALL", TraceRecord.STATUS_OK, TraceRecord.STATUS_ERROR,
                TraceRecord.STATUS_CANCELLED);
        statusFilter.setValue("ALL");
        statusFilter.addValueChangeListener(e -> refresh());

        modelFilter.setAllowCustomValue(false);
        modelFilter.addValueChangeListener(e -> refresh());

        conversationFilter.setClearButtonVisible(true);
        conversationFilter.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        conversationFilter.addValueChangeListener(e -> refresh());

        configureDynamicFilter(sessionIdFilter);
        configureDynamicFilter(toolFilter);
        configureDynamicFilter(serverFilter);

        HorizontalLayout filterRow =
                new HorizontalLayout(modelFilter, statusFilter, conversationFilter,
                        sessionIdFilter, toolFilter, serverFilter);
        filterRow.setSpacing(true);
        filterRow.setPadding(false);
        filterRow.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-m)");
        content.add(filterRow);

        this.traceList = new Div();
        traceList.getStyle().set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%");
        content.add(traceList);
    }

    private void configureDynamicFilter(ComboBox<String> combo) {
        combo.setAllowCustomValue(false);
        combo.setClearButtonVisible(true);
        combo.addValueChangeListener(e -> refresh());
    }

    private void populateFilter(ComboBox<String> combo, Stream<String> rawValues) {
        List<String> values = rawValues
                .filter(v -> v != null && !v.isBlank()).distinct().sorted()
                .collect(Collectors.toList());
        values.add(0, "ALL");
        String current = combo.getValue() == null ? "ALL" : combo.getValue();
        combo.setItems(values);
        combo.setValue(values.contains(current) ? current : "ALL");
    }

    private static Stream<String> setStream(Set<String> values) {
        return values == null ? Stream.empty() : values.stream();
    }

    private static boolean isAll(String v) {
        return v == null || "ALL".equals(v);
    }

    private Div buildTraceCard(TraceRecord trace, boolean expanded) {
        Div card = new Div();
        card.getStyle().set("background", "var(--lumo-base-color)")
                .set("border", "1px solid " + (expanded
                        ? "var(--lumo-primary-color-50pct)"
                        : "var(--lumo-contrast-10pct)"))
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("cursor", "pointer")
                .set("transition", "border-color 80ms, background 80ms");
        if (!expanded) {
            card.getElement().addEventListener("mouseenter",
                    e -> card.getStyle().set("border-color", "var(--lumo-primary-color-50pct)")
                            .set("background", "var(--lumo-contrast-5pct)"));
            card.getElement().addEventListener("mouseleave",
                    e -> card.getStyle().set("border-color", "var(--lumo-contrast-10pct)")
                            .set("background", "var(--lumo-base-color)"));
        }

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setSpacing(true);

        Span statusDot = new Span();
        String statusColor = TraceRecord.STATUS_ERROR.equals(trace.status())
                ? "var(--lumo-error-color)"
                : TraceRecord.STATUS_CANCELLED.equals(trace.status())
                        ? "var(--lumo-warning-color)"
                        : "var(--lumo-success-color)";
        statusDot.getStyle().set("width", "8px").set("height", "8px")
                .set("border-radius", "50%")
                .set("background", statusColor)
                .set("flex", "0 0 8px");

        Span title = new Span(shorten(trace.traceId(), 12)
                + " · " + shorten(trace.conversationId(), 12));
        title.getStyle().set("font-weight", "600")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-s)");

        Span timeSpan = new Span(Instant.ofEpochMilli(trace.startEpochMs()).toString());
        timeSpan.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");

        Div spacer = new Div();
        spacer.getStyle().set("flex", "1 1 auto");

        Span meta = new Span(
                (trace.provider() == null ? "—" : trace.provider())
                + " · " + (trace.model() == null ? "—" : trace.model())
                + " · " + formatTokens(trace.inputTokens(), trace.outputTokens()) + " tok"
                + " · " + FormatUtils.formatDurationMs(trace.durationMs())
                + " · " + (trace.spans() == null ? 0 : trace.spans().size()) + " spans"
                + (trace.toolCallCount() > 0 ? " · " + trace.toolCallCount() + " tools" : "")
                + (trace.hasRag() ? " · RAG" : ""));
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");

        Span chevron = new Span(expanded ? "▾" : "›");
        chevron.getStyle().set("font-size", "var(--lumo-font-size-m)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding-left", "var(--lumo-space-s)")
                .set("flex", "0 0 auto");

        header.add(statusDot, title, timeSpan, spacer, meta, chevron);
        card.add(header);

        if (expanded) {
            Div waterfall = new Div(new SpanTimelineCanvas(trace));
            waterfall.getStyle().set("max-height", "168px")
                    .set("overflow-y", "auto")
                    .set("overflow-x", "hidden")
                    .set("width", "100%");
            card.add(waterfall);
            int spanCount = trace.spans() == null ? 0 : trace.spans().size();
            Span more = new Span(spanCount > 5
                    ? "+ " + (spanCount - 5) + " more spans — click card to see full timeline + raw JSON"
                    : "Click card to see full timeline + raw JSON");
            more.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("font-style", "italic")
                    .set("padding-top", "var(--lumo-space-xs)");
            card.add(more);
        }

        card.addClickListener(e -> {
            if (expanded) {
                if (rowClickListener != null) rowClickListener.accept(trace);
            } else {
                expandedTraceId = trace.traceId();
                refresh();
            }
        });
        return card;
    }

    @Override
    public void refresh(Window ignored) {
        refresh();
    }

    public void setRowClickListener(Consumer<TraceRecord> listener) {
        this.rowClickListener = listener;
    }

    public void openTraceById(String traceId) {
        if (traceId == null || rowClickListener == null) return;
        buffer.snapshot().stream()
                .filter(t -> traceId.equals(t.traceId()))
                .findFirst()
                .ifPresent(rowClickListener);
    }

    public void refresh() {
        if (refreshing) return;
        refreshing = true;
        try {
            List<TraceRecord> all = buffer.snapshot();

            List<String> models = all.stream()
                    .map(TraceRecord::model)
                    .filter(m -> m != null && !m.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
            models.add(0, "ALL");
            String currentModel = modelFilter.getValue() == null ? "ALL" : modelFilter.getValue();
            modelFilter.setItems(models);
            modelFilter.setValue(models.contains(currentModel) ? currentModel : "ALL");

            populateFilter(sessionIdFilter, all.stream().map(TraceRecord::sessionId));
            populateFilter(toolFilter, all.stream().flatMap(t -> setStream(t.toolNames())));
            populateFilter(serverFilter, all.stream().flatMap(t -> setStream(t.serverNames())));

            String statusVal = statusFilter.getValue();
            String modelVal = modelFilter.getValue();
            String convFilter = conversationFilter.getValue() == null ? "" : conversationFilter.getValue().trim();
            String sessionVal = sessionIdFilter.getValue();
            String toolVal = toolFilter.getValue();
            String serverVal = serverFilter.getValue();

            List<TraceRecord> filtered = all.stream()
                    .filter(t -> "ALL".equals(statusVal) || statusVal == null || statusVal.equals(t.status()))
                    .filter(t -> "ALL".equals(modelVal) || modelVal == null || modelVal.equals(t.model()))
                    .filter(t -> convFilter.isEmpty()
                            || (t.conversationId() != null && t.conversationId().contains(convFilter)))
                    .filter(t -> isAll(sessionVal) || sessionVal.equals(t.sessionId()))
                    .filter(t -> isAll(toolVal) || (t.toolNames() != null && t.toolNames().contains(toolVal)))
                    .filter(t -> isAll(serverVal) || (t.serverNames() != null && t.serverNames().contains(serverVal)))
                    .sorted(Comparator.comparingLong(TraceRecord::startEpochMs).reversed())
                    .collect(Collectors.toList());
            traceList.removeAll();
            if (filtered.isEmpty()) {
                Span empty = new Span("No traces match the current filters.");
                empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                        .set("font-style", "italic")
                        .set("padding", "var(--lumo-space-l)");
                traceList.add(empty);
            } else {
                boolean pinnedStillVisible = expandedTraceId != null
                        && filtered.stream().anyMatch(t -> expandedTraceId.equals(t.traceId()));
                if (!pinnedStillVisible) {
                    expandedTraceId = filtered.getFirst().traceId();
                }
                for (TraceRecord t : filtered) {
                    boolean expand = t.traceId() != null && t.traceId().equals(expandedTraceId);
                    traceList.add(buildTraceCard(t, expand));
                }
            }
            setStatus(filtered.size() + " of " + all.size() + " traces · "
                    + (models.size() - 1) + " models · status filter: " + statusVal);
        } finally {
            refreshing = false;
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        subscription = buffer.liveStream()
                .sample(Duration.ofMillis(500))
                .subscribe(t -> ui.access(this::refresh));
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (subscription != null) {
            subscription.dispose();
            subscription = null;
        }
        super.onDetach(detachEvent);
    }

    private String shorten(String s, int n) {
        if (s == null) return "—";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private String formatTokens(Long in, Long out) {
        long i = in == null ? 0 : in;
        long o = out == null ? 0 : out;
        return i + "/" + o;
    }

    private String formatTagsText(TraceRecord t) {
        StringBuilder sb = new StringBuilder();
        if (t.hasRag()) sb.append("rag ");
        if (t.toolCallCount() > 0) sb.append("tools(").append(t.toolCallCount()).append(") ");
        if (t.finishReason() != null) sb.append(t.finishReason());
        return sb.toString().trim();
    }
}
