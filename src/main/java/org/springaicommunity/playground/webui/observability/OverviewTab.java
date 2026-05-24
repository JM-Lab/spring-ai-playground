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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import org.springaicommunity.playground.observability.ObservabilityProperties;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.pricing.CurrencyService;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class OverviewTab extends BaseDashboardTab {

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter OLDEST_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ObservabilityRingBuffer buffer;
    private final ObservabilityTimeSeries timeSeries;
    private final SystemMetricsSnapshot systemMetrics;
    private final ModelPricingService pricingService;
    private final CurrencyService currencyService;
    private final ObservabilityProperties obsProps;
    private final Div storageStatus = new Div();
    private final KpiCard costProjectionCard = new KpiCard("Projected cost / month");
    private Window currentWindow = Window.LAST_30M;

    // Hero KPIs
    private final KpiCard callsCard = new KpiCard("Calls");
    private final KpiCard tokensCard = new KpiCard("Tokens");
    private final KpiCard costCard = new KpiCard("Cost (USD)");
    private final KpiCard errorRateCard = new KpiCard("Error rate");
    private final KpiCard p95LatencyCard = new KpiCard("p95 latency");
    private final KpiCard heapCard = new KpiCard("Heap used");
    private final KpiCard cpuCard = new KpiCard("Process CPU");
    private final KpiCard activeOpsCard = new KpiCard("Active LLM ops");

    // LLM section
    private final ChartCanvas callsChart = new ChartCanvas();
    private final ChartCanvas latencyChart = new ChartCanvas();
    private final ChartCanvas providerDonut = new ChartCanvas();
    private final ChartCanvas topModelsBar = new ChartCanvas();

    // Tokens & Cost section (cost-over-time lives on TokensAndCostTab; here we
    // only keep a stacked token volume sparkline)
    private final ChartCanvas tokensChart = new ChartCanvas();

    // Tools & MCP section
    private final ChartCanvas toolCallsChart = new ChartCanvas();
    private final ChartCanvas transportDonut = new ChartCanvas();
    private final ChartCanvas topToolsBar = new ChartCanvas();
    private final ChartCanvas topMcpServersBar = new ChartCanvas();

    // Vector / RAG section
    private final ChartCanvas vectorOpsChart = new ChartCanvas();
    private final ChartCanvas topKBar = new ChartCanvas();

    // System section
    private final ChartCanvas heapChart = new ChartCanvas();
    private final ChartCanvas cpuChart = new ChartCanvas();

    // Logs section
    private final ChartCanvas logbackChart = new ChartCanvas();
    private final ChartCanvas outcomeChart = new ChartCanvas();

    private final Grid<TraceRecord> recentTracesGrid = new Grid<>(TraceRecord.class, false);

    // History buffers for system charts (sampled per refresh)
    private final Deque<double[]> heapHistory = new ArrayDeque<>(120);
    private final Deque<double[]> cpuHistory = new ArrayDeque<>(120);

    public OverviewTab(ObservabilityRingBuffer buffer, ObservabilityTimeSeries timeSeries,
            SystemMetricsSnapshot systemMetrics, ModelPricingService pricingService,
            CurrencyService currencyService, ObservabilityProperties obsProps,
            Consumer<String> navigateToSlug) {
        super();
        this.buffer = buffer;
        this.timeSeries = timeSeries;
        this.systemMetrics = systemMetrics;
        this.pricingService = pricingService;
        this.currencyService = currencyService;
        this.obsProps = obsProps;

        Runnable goLlm = navigateToSlug == null ? null : () -> navigateToSlug.accept("llm");
        Runnable goTokens = navigateToSlug == null ? null : () -> navigateToSlug.accept("tokens");
        Runnable goHost = navigateToSlug == null ? null : () -> navigateToSlug.accept("host");
        Runnable goTraces = navigateToSlug == null ? null : () -> navigateToSlug.accept("traces");
        Runnable goTools = navigateToSlug == null ? null : () -> navigateToSlug.accept("tools");
        Runnable goMcp = navigateToSlug == null ? null : () -> navigateToSlug.accept("mcp");
        Runnable goVector = navigateToSlug == null ? null : () -> navigateToSlug.accept("vector");
        Runnable goLogs = navigateToSlug == null ? null : () -> navigateToSlug.accept("logs");

        if (goLlm != null) {
            callsCard.setNavigationTarget(goLlm);
            p95LatencyCard.setNavigationTarget(goLlm);
            activeOpsCard.setNavigationTarget(goLlm);
        }
        if (goTokens != null) {
            tokensCard.setNavigationTarget(goTokens);
            costCard.setNavigationTarget(goTokens);
        }
        if (goTraces != null) errorRateCard.setNavigationTarget(goTraces);
        if (goHost != null) {
            heapCard.setNavigationTarget(goHost);
            cpuCard.setNavigationTarget(goHost);
        }

        Div kpiRow = DashboardLayout.kpiGrid(callsCard, tokensCard, costCard, errorRateCard,
                p95LatencyCard, heapCard, cpuCard, activeOpsCard);

        storageStatus.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("text-align", "center");

        H3 llmHeader = sectionHeader("LLM", "calls, latency, provider/model mix");
        callsChart.addClickListener(e -> openTracesTab());
        Div llmGrid = DashboardLayout.chartGrid();
        llmGrid.add(
                DashboardLayout.chartCard("Request rate", "calls/min — drag to zoom",
                        "Count of root LLM trace records per 1-minute bucket. " +
                                "Drag x-axis to zoom into a window.",
                        callsChart, goLlm),
                DashboardLayout.chartCard("Latency p50 / p95 / p99", "ms per bucket",
                        "Wall-clock duration of LLM root spans, percentile-ed per bucket (exact, not sketched).",
                        latencyChart, goLlm),
                DashboardLayout.chartCard("Provider mix", "share of calls by provider",
                        "Share of calls by gen_ai.system attribute (ollama, openai, anthropic, …).",
                        providerDonut, goLlm),
                DashboardLayout.chartCard("Top models", "by call count",
                        "Most frequently used models in this window (top 6).",
                        topModelsBar, goLlm)
        );

        H3 costHeader = sectionHeader("Tokens & Cost", "token volume (drill into Tokens & Cost tab for spend)");
        Div costGrid = DashboardLayout.chartGrid();
        costGrid.add(
                DashboardLayout.chartCard("Tokens (input + output)", "input + output per bucket",
                        "Σ gen_ai.usage.input_tokens + output_tokens per 1-min bucket. " +
                                "From TraceRecord.inputTokens / outputTokens.",
                        tokensChart, goTokens)
        );

        H3 toolHeader = sectionHeader("Tools / MCP", "in-process and external tool calls");
        Div toolGrid = DashboardLayout.chartGrid();
        toolGrid.add(
                DashboardLayout.chartCard("Tool calls / minute", "from spring.ai.tool spans",
                        "Count of spring.ai.tool spans per bucket — in-process tools + external MCP combined.",
                        toolCallsChart, goTools),
                DashboardLayout.chartCard("Transport mix", "in-process / STDIO / HTTP / SSE",
                        "spring.ai.tool spans grouped by mcp.transport attribute. " +
                                "in-process = local JS sandbox / SDK; STDIO/HTTP/SSE = external MCP.",
                        transportDonut, goMcp),
                DashboardLayout.chartCard("Top tools", "by call count",
                        "Most frequently invoked tool names (spring.ai.tool.definition.name).",
                        topToolsBar, goTools),
                DashboardLayout.chartCard("Top MCP servers", "by call count",
                        "External MCP servers ranked by tool invocations from spring.ai.tool spans.",
                        topMcpServersBar, goMcp)
        );

        H3 vectorHeader = sectionHeader("Vector / RAG", "vector store query activity");
        Div vectorGrid = DashboardLayout.chartGrid();
        vectorGrid.add(
                DashboardLayout.chartCard("Vector ops / minute", "from db.vector.client.operation spans",
                        "Count of vector store query/add/delete operations per bucket. Indicator of RAG activity.",
                        vectorOpsChart, goVector),
                DashboardLayout.chartCard("top_k distribution", "what k values were used",
                        "Histogram of db.vector.query.top_k attribute — how many documents the LLM asked for.",
                        topKBar, goVector)
        );

        H3 systemHeader = sectionHeader("System", "JVM and process resources");
        Div systemGrid = DashboardLayout.chartGrid();
        systemGrid.add(
                DashboardLayout.chartCard("Heap used (MB)", "rolling sample",
                        "jvm.memory.used (area=heap) sampled at refresh intervals (~60 samples retained).",
                        heapChart, goHost),
                DashboardLayout.chartCard("Process CPU (%)", "rolling sample",
                        "process.cpu.usage × 100, sampled at refresh intervals.",
                        cpuChart, goHost)
        );

        H3 logsHeader = sectionHeader("Logs & outcome", "logback level counts and call results");
        Div logsGrid = DashboardLayout.chartGrid();
        logsGrid.add(
                DashboardLayout.chartCard("Logback events by level", "lifetime totals",
                        "logback.events counter grouped by level (ERROR / WARN / INFO / DEBUG / TRACE). " +
                                "Lifetime cumulative, not window-scoped.",
                        logbackChart, goLogs),
                DashboardLayout.chartCard("Outcome mix", "OK · Error per bucket (cancelled is rare — see Traces filter)",
                        "Trace status counts per bucket. OK = no errors; Error = root or child span set status=ERROR.",
                        outcomeChart, goTraces)
        );

        H3 activityHeader = sectionHeader("Recent activity", "last 10 traces — click row to open");
        recentTracesGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        recentTracesGrid.setAllRowsVisible(true);
        recentTracesGrid.addColumn(t -> shortTime(t.startEpochMs())).setHeader("Time").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(t -> shorten(t.conversationId(), 10)).setHeader("Conv").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(t -> safe(t.provider())).setHeader("Provider").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(t -> safe(t.model())).setHeader("Model").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(t -> formatTokens(t.inputTokens(), t.outputTokens())).setHeader("In/Out").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(t -> FormatUtils.formatLatencyMs((double) t.durationMs())).setHeader("Duration").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addColumn(TraceRecord::status).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        recentTracesGrid.addItemClickListener(e -> {
            if (e.getItem() != null) {
                UI.getCurrent().navigate("observability?tab=traces&trace=" + e.getItem().traceId());
            }
        });

        Div secondaryKpis = DashboardLayout.kpiGrid(costProjectionCard);
        content.add(kpiRow,
                llmHeader, llmGrid,
                costHeader, costGrid, secondaryKpis,
                toolHeader, toolGrid,
                vectorHeader, vectorGrid,
                systemHeader, systemGrid,
                logsHeader, logsGrid,
                activityHeader, recentTracesGrid,
                storageStatus);
    }

    @Override
    public void refresh(Window window) {
        this.currentWindow = window;
        ObservabilityTimeSeries.Series s = timeSeries.compute(window);
        SystemMetricsSnapshot.Snapshot sys = systemMetrics.capture();
        List<String> labels = DashboardData.bucketLabelsFromStarts(s.bucketStartMs());

        // Sample rolling history for heap & CPU charts (~60 samples max)
        sampleSystemHistory(sys);
        long totalCalls = s.totalCalls();
        long errorTotal = DashboardData.sum(s.errors());
        double errPct = totalCalls == 0 ? 0 : errorTotal * 100.0 / totalCalls;

        callsCard.setValue(FormatUtils.formatNumber(totalCalls),
                "Σ LLM chat-client traces in window — sparkline shows per-bucket count");
        tokensCard.setValue(FormatUtils.formatNumber(s.totalTokens()),
                "Σ (input + output tokens) — sparkline shows per-bucket total");
        callsCard.setSparkline(DashboardData.toNumberList(s.calls()));
        tokensCard.setSparkline(addArrays(DashboardData.toNumberList(s.inTokens()), DashboardData.toNumberList(s.outTokens())));

        BigDecimal totalCost = computeWindowCost(window);
        String activeCurrency = currencyService == null ? "USD" : currencyService.getActiveCurrency();
        costCard.setLabel("Cost (" + activeCurrency + ")");
        costCard.setValue(formatUsd(totalCost),
                "Σ ModelPricingService.cost(model, in, out) · active currency (" + activeCurrency + ")");
        costCard.setSparkline(DashboardData.toNumberList(s.calls()));

        errorRateCard.setValue(String.format(Locale.ROOT, "%.1f%%", errPct),
                "errors / total calls — error = TraceRecord.status == ERROR");
        errorRateCard.setSparkline(DashboardData.toNumberListD(s.errorRatePct()));

        p95LatencyCard.setValue(FormatUtils.formatLatencyMs(s.overallP95LatencyMs()),
                "95th percentile of LLM trace duration in window (exact)");
        p95LatencyCard.setSparkline(DashboardData.toNumberListD(s.p95LatencyMs()));

        double heapPct = sys.jvmHeapMaxBytes == 0 ? 0
                : sys.jvmHeapUsedBytes * 100.0 / sys.jvmHeapMaxBytes;
        heapCard.setValue(String.format(Locale.ROOT, "%.0f%% (%s)",
                heapPct, FormatUtils.formatBytes((long) sys.jvmHeapUsedBytes)),
                "% of max heap used · jvm.memory.used{area=heap} / jvm.memory.max{area=heap}");
        heapCard.setSparkline(historyToSparkline(heapHistory, 0));

        cpuCard.setValue(String.format(Locale.ROOT, "%.1f%%", sys.processCpuUsage * 100),
                "process.cpu.usage × 100 (fraction of CPU used by this JVM)");
        cpuCard.setSparkline(historyToSparkline(cpuHistory, 0));

        long active = sys.activeChatClient + sys.activeChatModel + sys.activeAdvisor + sys.activeVector;
        activeOpsCard.setValue(String.valueOf(active),
                "Σ active LongTaskTimers · ChatClient + ChatModel + Advisor + VectorStore");

        callsChart.lineChart(labels, "Calls", DashboardData.toDouble(s.calls()), DashboardPalette.PRIMARY, "");
        latencyChart.multiLineChart(labels, List.of(
                new ChartCanvas.LineSeries("p50", s.p50LatencyMs(), DashboardPalette.INFO),
                new ChartCanvas.LineSeries("p95", s.p95LatencyMs(), DashboardPalette.WARN),
                new ChartCanvas.LineSeries("p99", s.p99LatencyMs(), DashboardPalette.ERROR)
        ), "ms");
        providerDonut.donutChart(s.totalCallsByProvider(), ChartCanvas.DEFAULT_PALETTE);
        topModelsBar.horizontalBarChart(s.totalCallsByModel(), DashboardPalette.SUCCESS, 6);

        tokensChart.stackedBarChart(labels,
                "Input", DashboardData.toDouble(s.inTokens()), DashboardPalette.INFO,
                "Output", DashboardData.toDouble(s.outTokens()), DashboardPalette.SUCCESS);

        ToolMcpBreakdown tm = collectToolMcp(window);
        toolCallsChart.lineChart(labels, "Tool calls", DashboardData.toDouble(s.toolCalls()), DashboardPalette.SUCCESS, "");
        transportDonut.donutChart(tm.transportCounts, ChartCanvas.DEFAULT_PALETTE);
        topToolsBar.horizontalBarChart(tm.toolCounts, DashboardPalette.SUCCESS, 6);
        topMcpServersBar.horizontalBarChart(tm.serverCounts, DashboardPalette.PRIMARY, 6);

        VectorBreakdown v = collectVector(window);
        vectorOpsChart.lineChart(labels, "Vector ops", v.opsPerBucketDouble(window.buckets()), DashboardPalette.ACCENT, "");
        topKBar.horizontalBarChart(v.topKHistogram, DashboardPalette.PRIMARY, 8);

        heapChart.lineChart(historyLabels(heapHistory),
                "Heap MB", historyValues(heapHistory, 0), DashboardPalette.INFO, " MB");
        cpuChart.lineChart(historyLabels(cpuHistory),
                "CPU %", historyValues(cpuHistory, 0), DashboardPalette.ERROR, "%");

        logbackChart.horizontalBarChart(sys.logbackEventsByLevel, DashboardPalette.PRIMARY, 6);
        long[] ok = new long[s.calls().length];
        for (int i = 0; i < ok.length; i++) {
            ok[i] = Math.max(0, s.calls()[i] - s.errors()[i] - s.cancelled()[i]);
        }
        outcomeChart.multiStackedBar(labels, List.of(
                new ChartCanvas.LineSeries("OK", DashboardData.toDouble(ok), DashboardPalette.SUCCESS),
                new ChartCanvas.LineSeries("Error", DashboardData.toDouble(s.errors()), DashboardPalette.ERROR)
        ));

        List<TraceRecord> recent = buffer.snapshot().stream()
                .sorted(Comparator.comparingLong(TraceRecord::startEpochMs).reversed())
                .limit(10)
                .toList();
        recentTracesGrid.setItems(recent);

        refreshCostProjection(totalCost, window);
        refreshStorageStatus();

        setStatus(totalCalls + " calls · " + s.totalCallsByModel().size() + " models · "
                + formatUsd(totalCost) + " spent · "
                + String.format(Locale.ROOT, "%.0f%% heap", heapPct) + " · "
                + active + " active LLM ops · window: " + window.minutes + "m");
    }

    private void refreshCostProjection(BigDecimal windowCost, Window window) {
        long windowMs = Math.max(1, window.totalMs());
        if (windowCost == null || windowCost.signum() == 0) {
            costProjectionCard.setValue("$0.00", "No cost recorded in current window — projection requires "
                    + "at least one priced LLM call.");
            return;
        }
        // Extrapolate per-month assuming the current window's burn rate continues.
        double perMs = windowCost.doubleValue() / (double) windowMs;
        double perDay = perMs * 86_400_000.0;
        double perMonth = perDay * 30.0;
        costProjectionCard.setValue(formatUsdProjection(perMonth),
                String.format(Locale.ROOT,
                        "Extrapolated from %s spent in last %d min: ~%s/day, ~%s/month at this rate. "
                        + "Resets when window changes.",
                        formatUsd(windowCost), window.minutes,
                        formatUsdProjection(perDay), formatUsdProjection(perMonth)));
    }

    private void refreshStorageStatus() {
        List<TraceRecord> snap = buffer.snapshot();
        int count = snap.size();
        int capacity = buffer.capacity();
        // Rough estimate: ~1.5 KB per trace (header + spans + attrs). Cheap to compute, accurate to ±50%.
        long bytes = estimateStorageBytes(snap);
        Long oldestMs = snap.stream().mapToLong(TraceRecord::startEpochMs).min().stream()
                .boxed().findFirst().orElse(null);
        int retainDays = obsProps == null ? 30 : obsProps.getRetainDays();
        String oldestStr = oldestMs == null ? "—"
                : OLDEST_FMT.format(Instant.ofEpochMilli(oldestMs));
        String nextCleanup = formatNextCleanup();
        storageStatus.setText(String.format(Locale.ROOT,
                "Trace storage: %d / %d traces · ~%s in memory · oldest %s · %d-day disk retention · next cleanup %s",
                count, capacity, FormatUtils.formatBytes(bytes), oldestStr, retainDays, nextCleanup));
    }

    private static long estimateStorageBytes(List<TraceRecord> traces) {
        long total = 0;
        for (TraceRecord t : traces) {
            // Trace-level header: ids + ~10 short fields + root attrs key/value pairs
            total += 200L;
            total += sumAttributeBytes(t.attributes());
            if (t.spans() != null) {
                for (SpanRecord s : t.spans()) {
                    total += 80L; // span header (id, parent, name, status, timestamps)
                    total += sumAttributeBytes(s.attributes());
                }
            }
        }
        return total;
    }

    private static long sumAttributeBytes(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) return 0;
        long n = 0;
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            if (e.getKey() != null) n += e.getKey().length();
            if (e.getValue() != null) n += e.getValue().length();
            n += 4; // delimiters / JSON quoting overhead estimate
        }
        return n;
    }

    private String formatUsdProjection(double usd) {
        String symbol = "$";
        double v = usd;
        if (currencyService != null) {
            symbol = currencyService.getActiveRate().symbol();
            if (symbol == null) symbol = "$";
            v = usd * currencyService.getActiveRate().rateFromUsd().doubleValue();
        }
        if (v < 0.01) return String.format(Locale.ROOT, "%s%.4f", symbol, v);
        if (v < 1) return String.format(Locale.ROOT, "%s%.3f", symbol, v);
        if (v < 1000) return String.format(Locale.ROOT, "%s%.2f", symbol, v);
        return String.format(Locale.ROOT, "%s%.0f", symbol, v);
    }

    private static String formatNextCleanup() {
        // ObservabilityPersistenceService cron: "0 0 4 * * *" — daily 04:00 local time.
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(4).withMinute(0).withSecond(0).withNano(0);
        if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1);
        long minsUntil = Duration.between(now, nextRun).toMinutes();
        if (minsUntil < 60) return nextRun.toLocalTime().toString() + " (in " + minsUntil + " min)";
        long hoursUntil = minsUntil / 60;
        return LocalTime.of(nextRun.getHour(), nextRun.getMinute()) + " (in "
                + hoursUntil + "h " + (minsUntil % 60) + "m)";
    }

    private void sampleSystemHistory(SystemMetricsSnapshot.Snapshot sys) {
        long ts = Instant.now().toEpochMilli();
        double heapMb = sys.jvmHeapUsedBytes / 1_048_576.0;
        double cpuPct = sys.processCpuUsage * 100;
        if (heapHistory.size() >= 60) heapHistory.removeFirst();
        if (cpuHistory.size() >= 60) cpuHistory.removeFirst();
        heapHistory.addLast(new double[] { ts, heapMb });
        cpuHistory.addLast(new double[] { ts, cpuPct });
    }

    private List<String> historyLabels(Deque<double[]> history) {
        List<String> out = new ArrayList<>(history.size());
        for (double[] e : history) out.add(HHMM.format(Instant.ofEpochMilli((long) e[0])));
        return out;
    }

    private double[] historyValues(Deque<double[]> history, int idx) {
        double[] out = new double[history.size()];
        int i = 0;
        for (double[] e : history) out[i++] = e[1];
        return out;
    }

    private List<Number> historyToSparkline(Deque<double[]> history, int idx) {
        List<Number> out = new ArrayList<>(history.size());
        for (double[] e : history) out.add(e[1]);
        return out;
    }

    private BigDecimal computeWindowCost(Window window) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        BigDecimal total = BigDecimal.ZERO;
        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            total = total.add(pricingService.cost(t.model() == null ? "(unknown)" : t.model(),
                    orZero(t.inputTokens()), orZero(t.outputTokens())));
        }
        return total;
    }

    private ToolMcpBreakdown collectToolMcp(Window window) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        ToolMcpBreakdown b = new ToolMcpBreakdown();
        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            if (t.spans() == null) continue;
            for (SpanRecord span : t.spans()) {
                if (!"spring.ai.tool".equals(span.name())) continue;
                Map<String, String> attrs = span.attributes();
                if (attrs == null) attrs = Map.of();
                String name = attrs.getOrDefault("spring.ai.tool.definition.name", "(unnamed)");
                b.toolCounts.merge(name, 1L, Long::sum);
                String tr = attrs.getOrDefault("mcp.transport", "in-process");
                b.transportCounts.merge(tr, 1L, Long::sum);
                if (!"in-process".equalsIgnoreCase(tr)) {
                    String server = attrs.getOrDefault("mcp.server",
                            attrs.getOrDefault("mcp.server.name", "(unknown)"));
                    b.serverCounts.merge(server, 1L, Long::sum);
                }
            }
        }
        return b;
    }

    private VectorBreakdown collectVector(Window window) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        int buckets = window.buckets();
        VectorBreakdown v = new VectorBreakdown();
        v.opsPerBucket = new long[buckets];
        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            if (t.spans() == null) continue;
            for (SpanRecord span : t.spans()) {
                if (!"db.vector.client.operation".equals(span.name())) continue;
                Map<String, String> attrs = span.attributes();
                if (attrs == null) attrs = Map.of();
                int bk = (int) Math.min(buckets - 1,
                        Math.max(0, (span.startEpochMs() - windowStart) / window.bucketMs()));
                v.opsPerBucket[bk]++;
                String topK = attrs.getOrDefault("db.vector.query.top_k",
                        attrs.getOrDefault("db.vector.query.top.k", null));
                if (topK != null) v.topKHistogram.merge("k=" + topK, 1L, Long::sum);
            }
        }
        return v;
    }

    private static final class ToolMcpBreakdown {
        Map<String, Long> toolCounts = new LinkedHashMap<>();
        Map<String, Long> serverCounts = new LinkedHashMap<>();
        Map<String, Long> transportCounts = new LinkedHashMap<>();
    }

    private static final class VectorBreakdown {
        long[] opsPerBucket;
        Map<String, Long> topKHistogram = new LinkedHashMap<>();
        double[] opsPerBucketDouble(int buckets) {
            double[] out = new double[buckets];
            for (int i = 0; i < buckets; i++) out[i] = opsPerBucket[i];
            return out;
        }
    }

    private void openTracesTab() {
        UI.getCurrent().navigate("observability?tab=traces");
    }

    private static H3 sectionHeader(String title, String subtitle) {
        H3 h = new H3(title + (subtitle == null ? "" : " — " + subtitle));
        h.getStyle().set("margin", "var(--lumo-space-l) 0 var(--lumo-space-xs) 0")
                .set("font-size", "var(--lumo-font-size-l)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "600");
        return h;
    }

    private List<Number> addArrays(List<Number> a, List<Number> b) {
        int n = Math.min(a.size(), b.size());
        List<Number> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(a.get(i).doubleValue() + b.get(i).doubleValue());
        return out;
    }

    private static String formatTokens(Long in, Long out) {
        long i = in == null ? 0 : in;
        long o = out == null ? 0 : out;
        return i + "/" + o;
    }

    private String formatUsd(BigDecimal v) {
        String symbol = "$";
        BigDecimal display = v;
        if (currencyService != null) {
            symbol = currencyService.getActiveRate().symbol();
            if (symbol == null) symbol = "$";
            display = currencyService.convertFromUsd(v == null ? BigDecimal.ZERO : v);
        }
        if (display == null || display.signum() == 0) return symbol + "0.00";
        if (display.compareTo(new BigDecimal("0.01")) < 0) {
            return String.format(Locale.ROOT, "%s%.6f", symbol, display.doubleValue());
        }
        return String.format(Locale.ROOT, "%s%.4f", symbol, display.doubleValue());
    }

    private static String safe(String s) { return s == null ? "—" : s; }

    private static String shorten(String s, int n) {
        if (s == null) return "—";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String shortTime(long epochMs) {
        return HHMM.format(Instant.ofEpochMilli(epochMs));
    }

    private static long orZero(Long v) { return v == null ? 0 : v; }
}
