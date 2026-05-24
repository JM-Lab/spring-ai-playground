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
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsTimeSeries;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas.LineSeries;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;
import org.springaicommunity.playground.webui.observability.components.StatsTable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HostRuntimeTab extends BaseDashboardTab {

    private static final DateTimeFormatter HUMAN_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final String ROW_JAVA = "Java";
    private static final String ROW_OS = "OS / arch";
    private static final String ROW_CORES = "CPU cores";
    private static final String ROW_STARTED_AT = "Started at";
    private static final String ROW_APP_READY = "App ready in";
    private static final String ROW_JIT = "JIT compile time";

    private final SystemMetricsSnapshot metricsService;
    private final SystemMetricsTimeSeries timeSeries;

    private final ChartCanvas heapTrendChart = new ChartCanvas();
    private final ChartCanvas cpuTrendChart = new ChartCanvas();
    private final ChartCanvas threadsTrendChart = new ChartCanvas();
    private final ChartCanvas gcTrendChart = new ChartCanvas();

    private final ChartCanvas threadsByStateDonut = new ChartCanvas();
    private final ChartCanvas heapPoolBar = new ChartCanvas();
    private final ChartCanvas memAfterGcBar = new ChartCanvas();
    private final ChartCanvas gcConcurrentBar = new ChartCanvas();
    private final ChartCanvas bufferUsedBar = new ChartCanvas();

    private final KpiCard heapUsedCard = new KpiCard("Heap used");
    private final KpiCard cpuCard = new KpiCard("Process CPU");
    private final KpiCard sysCpuCard = new KpiCard("System CPU");
    private final KpiCard loadAvgCard = new KpiCard("Load avg (1m)");
    private final KpiCard threadsCard = new KpiCard("Threads (live)");
    private final KpiCard uptimeCard = new KpiCard("Uptime");
    private final KpiCard classesCard = new KpiCard("Classes loaded");
    private final KpiCard fdCard = new KpiCard("Open file descriptors");
    private final KpiCard diskCard = new KpiCard("Disk free");
    private final KpiCard gcOverheadCard = new KpiCard("GC overhead");
    private final KpiCard gcPauseCard = new KpiCard("Total GC pause");
    private final KpiCard gcCountCard = new KpiCard("GC count (lifetime)");
    private final KpiCard processCpuTimeCard = new KpiCard("Process CPU time (cum.)");
    private final KpiCard bufferPoolsCard = new KpiCard("Buffer pools");

    private final StatsTable stats;

    public HostRuntimeTab(SystemMetricsSnapshot metricsService, SystemMetricsTimeSeries timeSeries) {
        super();
        this.metricsService = metricsService;
        this.timeSeries = timeSeries;

        // Host-centric reading order (JConsole VM Summary / VisualVM Overview /
        // Spring Boot Actuator /info convention): System (what machine) → JVM
        // (what VM is running on it) → Runtime (when it started).
        this.stats = new StatsTable()
                .addSection("System")
                .addRow(ROW_OS, "—")
                .addRow(ROW_CORES, "—")
                .addSection("JVM")
                .addRow(ROW_JAVA, "—")
                .addRow(ROW_JIT, "—")
                .addSection("Runtime")
                .addRow(ROW_STARTED_AT, "—")
                .addRow(ROW_APP_READY, "—");

        // KPI grouping (Grafana JVM ID 4701 convention): Hero (live resource state)
        // → System (capacity / lifecycle) → GC (collector activity).
        Div heroKpis = DashboardLayout.kpiGrid(heapUsedCard, cpuCard, sysCpuCard, loadAvgCard,
                threadsCard, uptimeCard);
        Div systemKpis = DashboardLayout.kpiGrid(classesCard, fdCard, diskCard);
        Div gcKpis = DashboardLayout.kpiGrid(gcOverheadCard, gcPauseCard, gcCountCard,
                processCpuTimeCard, bufferPoolsCard);

        Div groupedKpis = new Div();
        groupedKpis.getStyle().set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-xs)")
                .set("width", "100%");
        groupedKpis.add(DashboardLayout.kpiGroupHeader("Hero — live resource state"), heroKpis);
        groupedKpis.add(DashboardLayout.kpiGroupHeader("System — capacity & lifecycle"), systemKpis);
        groupedKpis.add(DashboardLayout.kpiGroupHeader("GC — collector activity"), gcKpis);

        Div topRow = new Div();
        topRow.getStyle().set("display", "grid")
                .set("grid-template-columns", "minmax(260px, 1fr) minmax(420px, 3fr)")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("align-items", "stretch");
        topRow.add(stats, groupedKpis);

        Div chartGrid = DashboardLayout.chartGrid();
        chartGrid.add(
                DashboardLayout.chartCard("Heap usage over time",
                        "MB — used vs committed vs max + live data after GC",
                        "Time-series from SystemMetricsTimeSeries. used / committed / max from " +
                                "jvm.memory.{used,committed,max}{area=heap}; live data after GC from jvm.gc.live.data.size.",
                        heapTrendChart),
                DashboardLayout.chartCard("CPU usage over time",
                        "% — process vs system",
                        "process.cpu.usage × 100 vs system.cpu.usage × 100, averaged per bucket.",
                        cpuTrendChart),
                DashboardLayout.chartCard("Threads over time",
                        "live / daemon / peak",
                        "jvm.threads.live (current), jvm.threads.daemon (subset), jvm.threads.peak (max-since-start).",
                        threadsTrendChart),
                DashboardLayout.chartCard("GC activity over time",
                        "pause ms + GC count per bucket",
                        "Δ jvm.gc.pause.totalTime (ms) and Δ jvm.gc.pause.count between consecutive samples in each bucket.",
                        gcTrendChart),
                DashboardLayout.chartCard("Threads by state",
                        "from jvm.threads.states",
                        "jvm.threads.states gauge grouped by 'state' tag (runnable / blocked / waiting / timed-waiting / new / terminated).",
                        threadsByStateDonut),
                DashboardLayout.chartCard("Heap pool usage",
                        "per pool: Eden / Old Gen / Survivor / Metaspace",
                        "jvm.memory.used{area=heap} grouped by id (pool name). Converted to MB for readability.",
                        heapPoolBar),
                DashboardLayout.chartCard("Heap retention after GC (%)",
                        "from jvm.memory.usage.after.gc — leak signal",
                        "Per-pool % of heap retained immediately after a GC cycle. Steady upward trend = potential leak.",
                        memAfterGcBar),
                DashboardLayout.chartCard("GC concurrent phase time",
                        "G1 background phases — jvm.gc.concurrent.phase.time",
                        "Total time spent in G1 concurrent phases (mark, cleanup, etc.) — not stop-the-world.",
                        gcConcurrentBar),
                DashboardLayout.chartCard("Buffer pool used vs capacity",
                        "from jvm.buffer.memory.used + jvm.buffer.total.capacity",
                        "Per buffer pool (direct, mapped, …): used bytes + free capacity (total − used) stacked.",
                        bufferUsedBar)
        );

        content.add(topRow, chartGrid);
    }

    @Override
    public void refresh(Window window) {
        Snapshot s = metricsService.capture();

        heapUsedCard.setValue(FormatUtils.formatBytes(s.jvmHeapUsedBytes),
                s.jvmHeapMaxBytes > 0
                        ? "of " + FormatUtils.formatBytes(s.jvmHeapMaxBytes) + " max heap"
                        : null);
        cpuCard.setValue(FormatUtils.formatPercent(s.processCpuUsage),
                "process.cpu.usage — fraction of CPU used by this JVM process");
        sysCpuCard.setValue(FormatUtils.formatPercent(s.systemCpuUsage),
                "system.cpu.usage — total CPU load on the host (all processes)");
        if (s.systemLoadAverage1m >= 0 && s.systemCpuCount > 0) {
            double normalized = s.systemLoadAverage1m / s.systemCpuCount * 100.0;
            loadAvgCard.setValue(String.format(Locale.ROOT, "%.2f", s.systemLoadAverage1m),
                    String.format(Locale.ROOT, "%.0f%% of %d logical cores · system.load.average.1m",
                            normalized, s.systemCpuCount));
        } else if (s.systemLoadAverage1m >= 0) {
            loadAvgCard.setValue(String.format(Locale.ROOT, "%.2f", s.systemLoadAverage1m),
                    "system.load.average.1m");
        } else {
            loadAvgCard.setValue("—");
        }
        threadsCard.setValue(String.valueOf(s.jvmThreadsLive),
                "peak " + s.jvmThreadsPeak + " · " + s.jvmThreadsDaemon + " daemon · jvm.threads.live");
        uptimeCard.setValue(FormatUtils.formatDuration(Duration.ofSeconds((long) s.processUptimeSeconds)),
                "process.uptime");
        classesCard.setValue(FormatUtils.formatNumber(s.jvmClassesLoaded),
                s.jvmClassesUnloaded + " unloaded · jvm.classes.loaded");
        fdCard.setValue(String.valueOf(s.processFilesOpen),
                s.processFilesMax > 0
                        ? "of " + FormatUtils.formatNumber(s.processFilesMax) + " allowed · process.files.open"
                        : "process.files.open");
        diskCard.setValue(FormatUtils.formatBytes(s.diskFreeBytes),
                s.diskTotalBytes > 0
                        ? "of " + FormatUtils.formatBytes(s.diskTotalBytes) + " total · disk.free"
                        : "disk.free");

        gcOverheadCard.setValue(s.gcOverheadFraction <= 0 ? "—"
                : String.format(Locale.ROOT, "%.2f%%", s.gcOverheadFraction * 100),
                "jvm.gc.overhead — fraction of CPU time spent in GC");
        double gcPauseTotalMs = 0;
        long gcCountTotal = 0;
        for (Double v : s.gcPauseSecondsSum.values()) if (v != null) gcPauseTotalMs += v * 1000.0;
        for (Long v : s.gcPauseCount.values()) if (v != null) gcCountTotal += v;
        double uptimeHours = Math.max(0.01, s.processUptimeSeconds / 3600.0);
        gcPauseCard.setValue(FormatUtils.formatLatencyMs(gcPauseTotalMs),
                "Σ jvm.gc.pause.totalTime across all GC actions (lifetime)");
        if (gcCountTotal > 0 && s.processUptimeSeconds > 60) {
            double gcPerHour = gcCountTotal / uptimeHours;
            gcCountCard.setValue(String.valueOf(gcCountTotal),
                    String.format(Locale.ROOT, "%.1f / hour · Σ jvm.gc.pause.count", gcPerHour));
        } else {
            gcCountCard.setValue(String.valueOf(gcCountTotal),
                    "Σ jvm.gc.pause.count (lifetime)");
        }
        processCpuTimeCard.setValue(formatNanosDuration(s.processCpuTimeNs),
                "process.cpu.time — cumulative CPU time used by this JVM (ns → human)");

        StringBuilder bufferTooltip = new StringBuilder();
        long totalBuffers = 0;
        for (Map.Entry<String, Long> e : s.jvmBufferCount.entrySet()) {
            if (bufferTooltip.length() > 0) bufferTooltip.append(" · ");
            bufferTooltip.append(e.getValue()).append(" ").append(e.getKey());
            totalBuffers += e.getValue();
        }
        bufferPoolsCard.setValue(totalBuffers == 0 ? "—" : String.valueOf(totalBuffers),
                bufferTooltip.length() == 0 ? null : bufferTooltip + " · jvm.buffer.count by pool");

        String javaInfo = "Java info unavailable";
        if (!s.jvmInfo.isEmpty()) {
            String runtime = s.jvmInfo.getOrDefault("runtime", "");
            String vendor = s.jvmInfo.getOrDefault("vendor", "");
            String version = s.jvmInfo.getOrDefault("version", "");
            javaInfo = ("Java " + version + " · " + runtime + " · " + vendor).trim();
        }
        stats.setValue(ROW_JAVA, javaInfo);

        String osName = System.getProperty("os.name", "");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        stats.setValue(ROW_OS, (osName + " " + osVersion + " · " + osArch).trim());
        stats.setValue(ROW_CORES, s.systemCpuCount + " logical");

        if (s.processStartTimeSeconds > 0) {
            stats.setValue(ROW_STARTED_AT,
                    HUMAN_TIME.format(Instant.ofEpochSecond((long) s.processStartTimeSeconds)));
        }
        stats.setValue(ROW_APP_READY, s.applicationReadySeconds <= 0 ? "—"
                : String.format(Locale.ROOT, "%.2f s", s.applicationReadySeconds));
        stats.setValue(ROW_JIT, FormatUtils.formatLatencyMs(s.jvmCompilationTimeMs));

        threadsByStateDonut.donutChart(new LinkedHashMap<>(s.jvmThreadsByState),
                ChartCanvas.DEFAULT_PALETTE);

        Map<String, Long> heapPoolMb = new LinkedHashMap<>();
        s.jvmHeapPoolUsed.forEach((k, v) -> heapPoolMb.put(k, v / (1024L * 1024L)));
        heapPoolBar.horizontalBarChart(heapPoolMb, DashboardPalette.PRIMARY, 6, " MB");

        Map<String, Long> memAfterGcTenths = new LinkedHashMap<>();
        s.jvmMemoryUsageAfterGc.forEach((k, v) -> memAfterGcTenths.put(k, Math.round(v * 1000)));
        memAfterGcBar.horizontalBarChart(memAfterGcTenths, DashboardPalette.ERROR, 6);

        Map<String, Long> concurrentMs = new LinkedHashMap<>();
        s.gcConcurrentPhaseSecondsSum.forEach((k, v) -> concurrentMs.put(k, Math.round(v * 1000)));
        gcConcurrentBar.horizontalBarChart(concurrentMs, DashboardPalette.WARN, 6, " ms");

        Map<String, long[]> bufferStacked = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : s.jvmBufferUsedBytes.entrySet()) {
            long used = e.getValue();
            long capacity = s.jvmBufferTotalCapacity.getOrDefault(e.getKey(), used);
            long free = Math.max(0, capacity - used);
            bufferStacked.put(e.getKey(), new long[] { used, free });
        }
        bufferUsedBar.horizontalStackedBar(bufferStacked,
                List.of("Used", "Free capacity"),
                List.of(DashboardPalette.INFO, "#cfd8dc"));

        SystemMetricsTimeSeries.Series series = timeSeries.compute(
                window == null ? Window.LAST_30M : window);

        heapTrendChart.multiLineChart(series.bucketLabels(), List.of(
                new LineSeries("Used", bytesToMb(series.heapUsedBytes()), DashboardPalette.PRIMARY),
                new LineSeries("Committed", bytesToMb(series.heapCommittedBytes()), DashboardPalette.INFO),
                new LineSeries("Max", bytesToMb(series.heapMaxBytes()), DashboardPalette.MUTED),
                new LineSeries("Live after GC", bytesToMb(series.gcLiveDataBytes()), DashboardPalette.SUCCESS)
        ), " MB");

        cpuTrendChart.multiLineChart(series.bucketLabels(), List.of(
                new LineSeries("Process", series.processCpuPct(), DashboardPalette.INFO),
                new LineSeries("System", series.systemCpuPct(), DashboardPalette.ERROR)
        ), " %");

        threadsTrendChart.multiLineChart(series.bucketLabels(), List.of(
                new LineSeries("Live", series.threadsLive(), DashboardPalette.SUCCESS),
                new LineSeries("Daemon", series.threadsDaemon(), DashboardPalette.INFO),
                new LineSeries("Peak", series.threadsPeak(), DashboardPalette.ERROR)
        ), "");

        gcTrendChart.multiLineChart(series.bucketLabels(), List.of(
                new LineSeries("Pause ms / bucket", series.gcPauseMsDelta(), DashboardPalette.WARN),
                new LineSeries("Count / bucket", series.gcCountDelta(), DashboardPalette.PRIMARY)
        ), "");

        setStatus("Heap " + FormatUtils.formatBytes(s.jvmHeapUsedBytes)
                + " · CPU " + FormatUtils.formatPercent(s.processCpuUsage)
                + " · " + s.jvmThreadsLive + " threads"
                + " · " + s.systemCpuCount + " cores"
                + " · uptime " + FormatUtils.formatDuration(Duration.ofSeconds((long) s.processUptimeSeconds))
                + " · " + timeSeries.sampleCount() + " samples buffered");
    }

    private static double[] bytesToMb(double[] bytes) {
        double[] mb = new double[bytes.length];
        for (int i = 0; i < bytes.length; i++) mb[i] = bytes[i] / (1024.0 * 1024.0);
        return mb;
    }

    private static String formatNanosDuration(long ns) {
        if (ns <= 0) return "—";
        return FormatUtils.formatLatencyMs(ns / 1_000_000.0);
    }
}
