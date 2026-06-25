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
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas.LineSeries;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ToolsTab extends BaseDashboardTab {

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final ObservabilityRingBuffer buffer;
    private final ObservabilityTimeSeries timeSeries;
    private final SystemMetricsSnapshot systemMetrics;
    private final McpClientService mcpClientService;

    private final KpiCard tracesWithToolsCard = new KpiCard("Traces with tools");
    private final KpiCard totalToolCallsCard = new KpiCard("Total tool calls");
    private final KpiCard distinctToolsCard = new KpiCard("Distinct tools");
    private final KpiCard p95LatencyCard = new KpiCard("p95 latency");
    private final KpiCard errorRateCard = new KpiCard("Error rate");
    private final KpiCard guardBlocksCard = new KpiCard("Sandbox guard blocks");

    private final ChartCanvas toolCallsStacked = new ChartCanvas();
    private final ChartCanvas toolLatencyChart = new ChartCanvas();
    private final ChartCanvas topToolsBar = new ChartCanvas();
    private final ChartCanvas sandboxLevelBar = new ChartCanvas();
    private final ChartCanvas guardBlocksBar = new ChartCanvas();

    public ToolsTab(ObservabilityRingBuffer buffer, ObservabilityTimeSeries timeSeries,
            SystemMetricsSnapshot systemMetrics, McpClientService mcpClientService) {
        super();
        this.buffer = buffer;
        this.timeSeries = timeSeries;
        this.systemMetrics = systemMetrics;
        this.mcpClientService = mcpClientService;

        Div intro = buildIntro(
                "Built-in tools — JS sandbox functions and local @Tool methods, including " +
                "self-loopback through the in-process MCP server. External MCP server calls " +
                "live in the MCP Servers tab.");

        Div cardRow = DashboardLayout.kpiGrid(tracesWithToolsCard, totalToolCallsCard,
                distinctToolsCard, p95LatencyCard, errorRateCard, guardBlocksCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Tool calls / minute",
                        "top 4 tools over time",
                        "Bar height = tool call count per bucket. Stacked by tool name (top 4 by lifetime). " +
                                "Source: spring.ai.tool spans where network.transport is null/in-process OR " +
                                "saip.mcp.server is the self-loopback (Tool Studio's auto-exposure).",
                        toolCallsStacked),
                DashboardLayout.chartCard("Tool latency p50 / p95 / p99",
                        "ms per bucket",
                        "Wall-clock duration of each tool span, sorted per bucket then percentile-ed.",
                        toolLatencyChart),
                DashboardLayout.chartCard("Top tools",
                        "by call count",
                        "Lifetime call count per spring.ai.tool.definition.name attribute (top 8).",
                        topToolsBar),
                DashboardLayout.chartCard("Sandbox level",
                        "calls by risk level (L0-L5)",
                        "From sandbox.level span attribute (set by ToolSpecService via EffectivePolicyResolver). " +
                                "L0 = baseline, L5 = critical deny-class removal.",
                        sandboxLevelBar),
                DashboardLayout.chartCard("Sandbox guard blocks",
                        "lifetime — top reasons",
                        "Counter sandbox.guard.blocked — counts every FetchPolicyException / " +
                                "FsPolicyException thrown in SafeHttpFetch and SafeFs, tagged by reason.",
                        guardBlocksBar)
        );

        content.add(intro, cardRow, grid);
    }

    @Override
    public void refresh(Window window) {
        ToolBreakdown b = breakdown(window);
        long windowStart = Instant.now().toEpochMilli() - window.totalMs();
        List<String> labels = DashboardData.bucketLabelsFromWindow(windowStart, window.bucketMs(), window.buckets());

        long total = b.toolOk + b.toolError;
        tracesWithToolsCard.setValue(String.valueOf(b.distinctTraces),
                "Distinct trace IDs containing at least one spring.ai.tool span (in-process or self-loopback)");
        totalToolCallsCard.setValue(String.valueOf(total),
                "Σ spring.ai.tool span count (in-process + self-loopback only — external MCP excluded)");
        distinctToolsCard.setValue(String.valueOf(b.toolNameCounts.size()),
                "Distinct spring.ai.tool.definition.name values invoked in window");
        if (total == 0) {
            errorRateCard.setValue("0.0%", "no tool calls in window");
        } else {
            errorRateCard.setValue(
                    String.format(Locale.ROOT, "%.1f%%", b.toolError * 100.0 / total),
                    b.toolError + " errors of " + total + " tool calls · spring.ai.tool span status");
        }

        int buckets = window.buckets();
        double[] p50 = new double[buckets];
        double[] p95 = new double[buckets];
        double[] p99 = new double[buckets];
        long sampleCountAll = 0;
        long sumAll = 0;
        long[] allLatenciesAcc = new long[(int) Math.min(Integer.MAX_VALUE, total)];
        int allIdx = 0;
        for (int i = 0; i < buckets; i++) {
            long[] arr = b.latencyPerBucket[i].stream().mapToLong(Long::longValue).sorted().toArray();
            if (arr.length > 0) {
                p50[i] = DashboardData.percentile(arr, 50);
                p95[i] = DashboardData.percentile(arr, 95);
                p99[i] = DashboardData.percentile(arr, 99);
                for (long v : arr) {
                    if (allIdx < allLatenciesAcc.length) allLatenciesAcc[allIdx++] = v;
                    sumAll += v;
                }
                sampleCountAll += arr.length;
            }
        }
        if (sampleCountAll > 0) {
            long[] all = new long[allIdx];
            System.arraycopy(allLatenciesAcc, 0, all, 0, allIdx);
            Arrays.sort(all);
            p95LatencyCard.setValue(FormatUtils.formatLatencyMs(DashboardData.percentile(all, 95)),
                    "95th percentile across all tool spans in window (" + sampleCountAll + " samples)");
        } else {
            p95LatencyCard.setValue("—", "No tool calls in window");
        }

        List<LineSeries> stackedSeries = new ArrayList<>();
        String[] palette = ChartCanvas.DEFAULT_PALETTE;
        int idx = 0;
        List<Map.Entry<String, long[]>> topEntries = new ArrayList<>(b.callsByToolPerBucket.entrySet());
        topEntries.sort((a, e) -> Long.compare(
                b.toolNameCounts.getOrDefault(e.getKey(), 0L),
                b.toolNameCounts.getOrDefault(a.getKey(), 0L)));
        for (Map.Entry<String, long[]> e : topEntries) {
            if (idx >= 4) break;
            double[] vals = new double[e.getValue().length];
            for (int i = 0; i < vals.length; i++) vals[i] = e.getValue()[i];
            stackedSeries.add(new LineSeries(e.getKey(), vals, palette[idx % palette.length]));
            idx++;
        }
        if (stackedSeries.isEmpty()) {
            stackedSeries.add(new LineSeries("(no data)", new double[buckets], palette[0]));
        }
        toolCallsStacked.multiStackedBar(labels, stackedSeries);

        toolLatencyChart.multiLineChart(labels, List.of(
                new LineSeries("p50", p50, DashboardPalette.INFO),
                new LineSeries("p95", p95, DashboardPalette.WARN),
                new LineSeries("p99", p99, DashboardPalette.ERROR)
        ), " ms");

        topToolsBar.horizontalBarChart(b.toolNameCounts, DashboardPalette.SUCCESS, 8);
        sandboxLevelBar.horizontalBarChart(b.sandboxLevelCounts, DashboardPalette.PRIMARY, 6);

        SystemMetricsSnapshot.Snapshot snap = systemMetrics.capture();
        long totalBlocks = snap.sandboxGuardBlocked.values().stream().mapToLong(Long::longValue).sum();
        guardBlocksCard.setValue(String.valueOf(totalBlocks),
                "Σ sandbox.guard.blocked counter — FetchPolicyException + FsPolicyException blocked attempts (lifetime)");
        guardBlocksBar.horizontalBarChart(snap.sandboxGuardBlocked, DashboardPalette.ERROR, 8);

        setStatus(total + " tool calls · " + b.toolNameCounts.size() + " distinct tools · "
                + b.distinctTraces + " traces · " + totalBlocks + " guard blocks · window: "
                + window.minutes + "m");
    }


    @SuppressWarnings("unchecked")
    private ToolBreakdown breakdown(Window window) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        int buckets = window.buckets();
        ToolBreakdown b = new ToolBreakdown();
        b.latencyPerBucket = new ArrayList[buckets];
        for (int i = 0; i < buckets; i++) b.latencyPerBucket[i] = new ArrayList<>();
        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            if (t.spans() == null) continue;
            boolean traceCounted = false;
            for (SpanRecord span : t.spans()) {
                if (!"spring.ai.tool".equals(span.name())) continue;
                Map<String, String> attrs = span.attributes();
                String transport = attrs == null ? null : attrs.get("network.transport");
                String server = attrs == null ? null : attrs.get("saip.mcp.server");
                boolean directInProcess = transport == null || "in-process".equalsIgnoreCase(transport);
                boolean viaSelfLoopback = this.mcpClientService.isSelfLoopback(server);
                if (!directInProcess && !viaSelfLoopback) continue;
                String name = attrs == null ? "(unnamed)"
                        : attrs.getOrDefault("spring.ai.tool.definition.name", "(unnamed)");
                b.toolNameCounts.merge(name, 1L, Long::sum);
                int bk = (int) Math.min(buckets - 1,
                        Math.max(0, (span.startEpochMs() - windowStart) / window.bucketMs()));
                b.latencyPerBucket[bk].add(span.durationMs());
                b.callsByToolPerBucket.computeIfAbsent(name, k -> new long[buckets])[bk]++;
                if ("ERROR".equals(span.status())) {
                    b.toolError++;
                } else {
                    b.toolOk++;
                }
                if (!traceCounted) {
                    b.distinctTraces++;
                    traceCounted = true;
                }
                String level = attrs == null ? null : attrs.get("sandbox.level");
                b.sandboxLevelCounts.merge(level == null ? "unknown" : level, 1L, Long::sum);
            }
        }
        return b;
    }

    private static final class ToolBreakdown {
        Map<String, Long> toolNameCounts = new LinkedHashMap<>();
        Map<String, Long> sandboxLevelCounts = new LinkedHashMap<>();
        Map<String, long[]> callsByToolPerBucket = new LinkedHashMap<>();
        long toolOk;
        long toolError;
        long distinctTraces;
        List<Long>[] latencyPerBucket;
    }

}
