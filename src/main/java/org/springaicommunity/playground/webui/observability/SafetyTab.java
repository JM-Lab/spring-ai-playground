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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer.RiskEvent;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;
import org.springaicommunity.playground.service.mcp.risk.McpRiskEvents.Types;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class SafetyTab extends BaseDashboardTab {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final SystemMetricsSnapshot systemMetrics;
    private final McpRiskEventRingBuffer eventBuffer;

    private final KpiCard riskSignalsCard = new KpiCard("Risk signals");
    private final KpiCard tamperCard = new KpiCard("Tamper rejects");
    private final KpiCard poisoningCard = new KpiCard("Poisoning hits");
    private final KpiCard floorCard = new KpiCard("Floor overrides");
    private final KpiCard hitlApprovalCard = new KpiCard("HITL approval rate");
    private final KpiCard sandboxBlockCard = new KpiCard("Sandbox guard blocks");

    private final ChartCanvas riskByTypeBar = new ChartCanvas();
    private final ChartCanvas riskLevelBar = new ChartCanvas();
    private final ChartCanvas hitlByOutcomeBar = new ChartCanvas();
    private final ChartCanvas sandboxByReasonBar = new ChartCanvas();
    private final Div timeline = new Div();
    private int renderedEventCount = -1;
    private RiskEvent renderedNewestEvent;

    public SafetyTab(SystemMetricsSnapshot systemMetrics, McpRiskEventRingBuffer eventBuffer) {
        super();
        this.systemMetrics = systemMetrics;
        this.eventBuffer = eventBuffer;

        Div intro = buildIntro(
                "Safety & security signals across the MCP risk model (L0–L5), the JS tool sandbox guards, " +
                "tamper detection (content-hash ledger), tool-poisoning scans, and human-in-the-loop " +
                "approvals. All counters are lifetime totals.");

        Div cardRow = DashboardLayout.kpiGrid(riskSignalsCard, tamperCard, poisoningCard,
                floorCard, hitlApprovalCard, sandboxBlockCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Risk signals by type",
                        "lifetime — counter saip.risk.signal",
                        "Counter saip.risk.signal grouped by type: server-risk-computed, " +
                                "tool-publish-risk-computed, floor-override-triggered, hash-ledger-mismatch, " +
                                "composition-lifecycle, poisoning-hit.",
                        riskByTypeBar),
                DashboardLayout.chartCard("Risk level distribution",
                        "lifetime — counter saip.tool.risk",
                        "Final composed risk level of each executed MCP tool call. " +
                                "L0 verified · L1 safe · L2 low · L3 moderate · L4 high · L5 critical.",
                        riskLevelBar),
                DashboardLayout.chartCard("HITL decisions",
                        "lifetime — counter mcp.hitl.decision",
                        "Human-in-the-loop approval-gate outcomes from both gates: approved / declined, " +
                                "plus ask-failed (chat-side) and denied / elicit-failed (MCP-server-side).",
                        hitlByOutcomeBar),
                DashboardLayout.chartCard("Sandbox guard blocks",
                        "lifetime — counter sandbox.guard.blocked",
                        "JS tool sandbox guard rejections by reason (SSRF / filesystem policy): " +
                                "host-not-in-allowlist, private-ip, too-many-redirects, body-too-large, …",
                        sandboxByReasonBar)
        );

        content.add(intro, cardRow, grid, buildTimelineSection());
    }

    private Div buildTimelineSection() {
        Div title = new Div();
        title.setText("Recent risk events");
        title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-m)")
                .set("margin", "var(--lumo-space-m) 0 var(--lumo-space-xs) 0");
        timeline.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "4px")
                .set("max-height", "320px").set("overflow-y", "auto")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)");
        Div section = new Div(title, timeline);
        section.setWidthFull();
        return section;
    }

    @Override
    public void refresh(Window window) {
        Snapshot snap = systemMetrics.capture();

        Map<String, Long> riskByType = snap.mcpRiskSignalByType;
        long riskTotal = riskByType.values().stream().mapToLong(Long::longValue).sum();
        long tamper = riskByType.getOrDefault(Types.HASH_LEDGER_MISMATCH, 0L);
        long poison = riskByType.getOrDefault(Types.POISONING_HIT, 0L);
        long floor = riskByType.getOrDefault(Types.FLOOR_OVERRIDE_TRIGGERED, 0L);

        Map<String, Long> hitl = snap.mcpHitlByOutcome;
        long approved = hitl.getOrDefault("approved", 0L);
        long hitlTotal = hitl.values().stream().mapToLong(Long::longValue).sum();

        long sandboxBlocks = snap.sandboxGuardBlocked.values().stream().mapToLong(Long::longValue).sum();

        riskSignalsCard.setValue(String.valueOf(riskTotal),
                "Σ saip.risk.signal counter across all types (lifetime)");
        tamperCard.setValue(String.valueOf(tamper),
                "hash-ledger-mismatch — a default/exposed tool's content hash changed since first seen (TOFU)");
        poisoningCard.setValue(String.valueOf(poison),
                "poisoning-hit — a tool description/schema matched a prompt-injection pattern");
        floorCard.setValue(String.valueOf(floor),
                "floor-override-triggered — a risk floor rule forced a higher level");
        hitlApprovalCard.setValue(approvalRate(approved, hitlTotal),
                hitlTotal == 0 ? "no human-in-the-loop decisions yet"
                        : approved + " approved of " + hitlTotal + " HITL decisions (lifetime)");
        sandboxBlockCard.setValue(String.valueOf(sandboxBlocks),
                "Σ sandbox.guard.blocked counter (SSRF + filesystem policy rejections)");

        riskByTypeBar.horizontalBarChart(riskByType, DashboardPalette.WARN, 8);
        riskLevelBar.horizontalBarChartInOrder(riskLevelByLevel(snap.mcpToolRiskByLevel), DashboardPalette.PRIMARY);
        hitlByOutcomeBar.horizontalBarChart(hitl, DashboardPalette.SUCCESS, 6);
        sandboxByReasonBar.horizontalBarChart(snap.sandboxGuardBlocked, DashboardPalette.INFO, 8);

        setStatus(riskTotal + " risk signals · " + tamper + " tamper · " + poison + " poisoning · "
                + hitlTotal + " HITL decisions · " + sandboxBlocks + " sandbox blocks");

        renderTimeline();
    }

    private void renderTimeline() {
        List<RiskEvent> events = eventBuffer.snapshot();
        RiskEvent newest = events.isEmpty() ? null : events.getLast();
        if (events.size() == renderedEventCount && Objects.equals(newest, renderedNewestEvent)) {
            return;
        }
        renderedEventCount = events.size();
        renderedNewestEvent = newest;
        timeline.removeAll();
        if (events.isEmpty()) {
            Span empty = new Span("No risk events recorded yet — signals appear here as MCP servers and "
                    + "tools are registered, exposed, composed, or fail an integrity/poisoning check.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic").set("padding", "var(--lumo-space-s)");
            timeline.add(empty);
            return;
        }
        events.reversed().stream()
                .limit(50)
                .forEach(e -> timeline.add(buildEventRow(e)));
    }

    private Div buildEventRow(RiskEvent e) {
        Div row = new Div();
        row.getStyle().set("display", "flex").set("align-items", "baseline").set("gap", "8px")
                .set("padding", "2px 4px").set("font-size", "var(--lumo-font-size-s)");

        Span time = new Span(e.epochMs() == 0 ? "—"
                : TIME_FMT.format(Instant.ofEpochMilli(e.epochMs()).atZone(ZoneId.systemDefault())));
        time.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-family", "var(--lumo-font-family-monospace, monospace)").set("flex", "0 0 auto");

        boolean warn = "warn".equals(e.severity());
        Span badge = new Span(e.type());
        badge.getStyle().set("flex", "0 0 auto").set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-xs)").set("padding", "1px 6px")
                .set("border-radius", "999px")
                .set("background", warn ? "var(--lumo-error-color-10pct)" : "var(--lumo-primary-color-10pct)")
                .set("color", warn ? "var(--lumo-error-text-color)" : "var(--lumo-primary-text-color)");

        Span summary = new Span(e.summary());
        summary.getStyle().set("flex", "1 1 auto").set("overflow", "hidden")
                .set("text-overflow", "ellipsis").set("white-space", "nowrap");

        row.add(time, badge, summary);
        return row;
    }

    static String approvalRate(long approved, long total) {
        return total == 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", approved * 100.0 / total);
    }

    static Map<String, Long> riskLevelByLevel(Map<String, Long> byLevel) {
        Map<String, Long> sorted = new LinkedHashMap<>();
        for (String level : List.of("L0", "L1", "L2", "L3", "L4", "L5")) {
            long count = byLevel.getOrDefault(level, 0L);
            if (count > 0) sorted.put(level, count);
        }
        return sorted;
    }
}
