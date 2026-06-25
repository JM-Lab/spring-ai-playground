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
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.ollama.OllamaMetricsTimeSeries;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.InstalledModel;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.OllamaStatus;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.Platform;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.RunningModel;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas.LineSeries;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class OllamaRuntimeTab extends BaseDashboardTab {

    private static final long MB = 1024L * 1024L;
    private static final double PRESSURE_WARN_PCT = 85.0;

    private final OllamaMonitorService monitor;
    private final OllamaMetricsTimeSeries timeSeries;
    private final SystemMetricsSnapshot systemMetrics;
    private final Platform platform;

    private final KpiCard platformCard = new KpiCard("Platform");
    private final KpiCard statusCard = new KpiCard("Ollama runtime");
    private final KpiCard runningCard = new KpiCard("Running models");
    private final KpiCard vramCard = new KpiCard("VRAM in use");
    private final KpiCard pressureCard = new KpiCard("Memory");
    private final KpiCard installedCard = new KpiCard("Installed models");
    private final KpiCard diskCard = new KpiCard("Installed on disk");

    private final ChartCanvas reachabilityChart = new ChartCanvas();
    private final ChartCanvas vramTrendChart = new ChartCanvas();
    private final ChartCanvas modelsTrendChart = new ChartCanvas();
    private final ChartCanvas offloadChart = new ChartCanvas();
    private final ChartCanvas installedSizeChart = new ChartCanvas();
    private final ChartCanvas quantizationChart = new ChartCanvas();
    private final ChartCanvas familyChart = new ChartCanvas();
    private final ChartCanvas capabilityChart = new ChartCanvas();

    private final Div runningList = new Div();

    public OllamaRuntimeTab(OllamaMonitorService monitor, OllamaMetricsTimeSeries timeSeries,
            SystemMetricsSnapshot systemMetrics) {
        super();
        this.monitor = monitor;
        this.timeSeries = timeSeries;
        this.systemMetrics = systemMetrics;
        this.platform = monitor.platform();

        platformCard.setValue(platform.label(), "Accelerator: " + platform.accelerator());
        pressureCard.setLabel(platform.unifiedMemory() ? "Unified memory used" : "VRAM loaded");

        Div intro = buildIntro(
                "Local Ollama runtime — reachability, models loaded into memory with their VRAM "
                        + "footprint, GPU/CPU offload, idle-unload countdown (Ollama /api/ps), and the "
                        + "installed-model inventory (Ollama /api/tags). Shown when Ollama is the active "
                        + "chat provider. Detected platform: " + platform.label()
                        + (platform.unifiedMemory()
                                ? " — VRAM is a slice of unified system memory."
                                : " — VRAM is dedicated GPU memory, separate from system RAM."));

        Div cardRow = DashboardLayout.kpiGrid(platformCard, statusCard, runningCard, vramCard,
                pressureCard, installedCard, diskCard);

        Div runtimeCharts = DashboardLayout.chartGrid();
        runtimeCharts.add(
                DashboardLayout.chartCard("Reachability over time",
                        "% — Ollama server responding",
                        "% of samples where the Ollama server answered /api/version while it was the "
                                + "active provider — dips mark outages or restarts.",
                        reachabilityChart),
                DashboardLayout.chartCard("VRAM in use over time",
                        "MB — Σ VRAM across loaded models",
                        "Time-series from OllamaMetricsTimeSeries: Σ size_vram across models in Ollama "
                                + "/api/ps, sampled while Ollama is the active provider.",
                        vramTrendChart),
                DashboardLayout.chartCard("Models loaded over time",
                        "count — loaded in memory vs installed on disk",
                        "Loaded = models in Ollama /api/ps (rises on first use, falls on idle-unload); "
                                + "Installed = models pulled locally via /api/tags.",
                        modelsTrendChart),
                DashboardLayout.chartCard("GPU / CPU offload (loaded now)",
                        "MB — VRAM on GPU vs spilled to CPU/RAM",
                        "Per loaded model: size_vram on GPU vs (size − size_vram) offloaded to CPU. "
                                + "Any CPU portion means the model did not fully fit in VRAM and runs slower.",
                        offloadChart));

        Div inventoryCharts = DashboardLayout.chartGrid();
        inventoryCharts.add(
                DashboardLayout.chartCard("Installed models by size",
                        "MB on disk — largest first",
                        "Per installed model size from Ollama /api/tags, top 10 by disk footprint.",
                        installedSizeChart),
                DashboardLayout.chartCard("Installed by quantization",
                        "model count per quantization",
                        "details.quantization_level from /api/tags (Q4_K_M, Q8_0, F16, MLX nvfp4/mxfp8, …).",
                        quantizationChart),
                DashboardLayout.chartCard("Installed by family",
                        "model count per family",
                        "details.family from /api/tags (qwen, llama, bert, …); MLX builds may not report one.",
                        familyChart),
                DashboardLayout.chartCard("Installed by capability",
                        "model count per capability",
                        "capabilities from /api/tags — completion, tools, thinking, vision, embedding.",
                        capabilityChart));

        content.add(intro, cardRow,
                DashboardLayout.kpiGroupHeader("Live runtime"), runtimeCharts,
                DashboardLayout.kpiGroupHeader("Installed inventory"), inventoryCharts,
                buildRunningSection());
    }

    private Div buildRunningSection() {
        Div title = new Div();
        title.setText("Loaded models");
        title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-m)")
                .set("margin", "var(--lumo-space-m) 0 var(--lumo-space-xs) 0");
        runningList.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "4px")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)");
        Div section = new Div(title, runningList);
        section.setWidthFull();
        return section;
    }

    @Override
    public void refresh(Window window) {
        OllamaStatus status = monitor.cachedSnapshot();

        renderTrends(window);
        renderOffload(status);
        renderInventory(status);

        if (!status.active()) {
            setUnavailableKpis("Inactive", "Ollama is not the active chat provider");
            renderEmpty("Ollama is not the active chat provider — switch the chat model to Ollama to monitor it.");
            setStatus("Ollama inactive");
            return;
        }
        if (!status.reachable()) {
            setUnavailableKpis("Unreachable", "No response from the Ollama server");
            renderEmpty("Ollama server is not reachable — is `ollama serve` running?");
            setStatus("Ollama unreachable");
            return;
        }

        statusCard.setValue("Up", status.version() == null || status.version().isBlank()
                ? "Ollama server reachable" : "Ollama " + status.version());
        runningCard.setValue(String.valueOf(status.runningModels().size()),
                "models currently loaded in memory (Ollama /api/ps)");
        vramCard.setValue(FormatUtils.formatBytes(status.totalVramBytes()),
                "Σ VRAM across loaded models");
        renderPressure(status);
        installedCard.setValue(String.valueOf(status.installedModels().size()),
                "models pulled locally (Ollama /api/tags)");
        diskCard.setValue(FormatUtils.formatBytes(status.totalInstalledDiskBytes()),
                "Σ disk across installed models");

        renderRunning(status);
        setStatus(status.runningModels().size() + " loaded · "
                + FormatUtils.formatBytes(status.totalVramBytes()) + " VRAM · "
                + status.installedModels().size() + " installed");
    }

    private void setUnavailableKpis(String statusValue, String statusDetail) {
        statusCard.setValue(statusValue, statusDetail);
        runningCard.setValue("—", "");
        vramCard.setValue("—", "");
        pressureCard.setValue("—", "");
        installedCard.setValue("—", "");
        diskCard.setValue("—", "");
    }

    private void renderPressure(OllamaStatus status) {
        Snapshot sys = systemMetrics.capture();
        long hostTotal = sys.systemPhysicalMemoryTotalBytes;
        long vram = status.totalVramBytes();
        String value = pressureValue(platform.unifiedMemory(), vram, hostTotal, PRESSURE_WARN_PCT);
        if (!platform.unifiedMemory()) {
            pressureCard.setValue(value,
                    "Dedicated GPU VRAM loaded — Ollama doesn't report total VRAM capacity, so no % is "
                            + "shown. System RAM (separate pool): " + FormatUtils.formatBytes(hostTotal));
        } else if (hostTotal <= 0) {
            pressureCard.setValue(value, "Host memory size unavailable");
        } else {
            pressureCard.setValue(value,
                    FormatUtils.formatBytes(vram) + " of " + FormatUtils.formatBytes(hostTotal)
                            + " unified memory · GPU shares host RAM · warns at 85%");
        }
    }

    static String pressureValue(boolean unifiedMemory, long vram, long hostTotal, double warnPct) {
        if (!unifiedMemory) {
            return FormatUtils.formatBytes(vram);
        }
        if (hostTotal <= 0) {
            return "—";
        }
        double pct = 100.0 * vram / hostTotal;
        return String.format(Locale.ROOT, "%.0f%%", pct) + (pct >= warnPct ? " ⚠" : "");
    }

    private void renderTrends(Window window) {
        OllamaMetricsTimeSeries.Series s = timeSeries.compute(window == null ? Window.LAST_30M : window);
        reachabilityChart.lineChart(s.bucketLabels(), "Reachable", s.reachablePct(),
                DashboardPalette.SUCCESS, "%");
        vramTrendChart.lineChart(s.bucketLabels(), "VRAM", bytesToMb(s.vramBytes()),
                DashboardPalette.PRIMARY, " MB");
        modelsTrendChart.multiLineChart(s.bucketLabels(), List.of(
                new LineSeries("Loaded (in memory)", s.runningModels(), DashboardPalette.SUCCESS),
                new LineSeries("Installed (on disk)", s.installedModels(), DashboardPalette.MUTED)), "");
    }

    private void renderOffload(OllamaStatus status) {
        Map<String, long[]> offload = new LinkedHashMap<>();
        for (RunningModel model : status.runningModels()) {
            long vram = model.sizeVram();
            long cpu = Math.max(0, model.sizeBytes() - model.sizeVram());
            offload.put(model.name(), new long[] { vram / MB, cpu / MB });
        }
        offloadChart.horizontalStackedBar(offload, List.of("On GPU (VRAM)", "On CPU (RAM)"),
                List.of(DashboardPalette.SUCCESS, DashboardPalette.MUTED));
    }

    private void renderInventory(OllamaStatus status) {
        List<InstalledModel> models = status.installedModels();

        Map<String, Long> sizeMb = new LinkedHashMap<>();
        for (InstalledModel model : models) {
            sizeMb.put(model.name(), model.sizeBytes() / MB);
        }
        installedSizeChart.horizontalBarChart(sizeMb, DashboardPalette.PRIMARY, 10, " MB");

        quantizationChart.donutChart(
                countBy(models, m -> m.quantization() == null ? "(unknown)" : m.quantization()),
                ChartCanvas.DEFAULT_PALETTE);
        familyChart.donutChart(
                countBy(models, m -> m.family() == null ? "(unknown)" : m.family()),
                ChartCanvas.DEFAULT_PALETTE);

        Map<String, Long> byCapability = new LinkedHashMap<>();
        for (InstalledModel model : models) {
            for (String capability : model.capabilities()) {
                byCapability.merge(capability, 1L, Long::sum);
            }
        }
        capabilityChart.horizontalBarChart(byCapability, DashboardPalette.INFO, 8);
    }

    static Map<String, Long> countBy(List<InstalledModel> models,
            Function<InstalledModel, String> key) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (InstalledModel model : models) {
            counts.merge(key.apply(model), 1L, Long::sum);
        }
        return counts;
    }

    static double[] bytesToMb(double[] bytes) {
        double[] mb = new double[bytes.length];
        for (int i = 0; i < bytes.length; i++) mb[i] = bytes[i] / (1024.0 * 1024.0);
        return mb;
    }

    private void renderRunning(OllamaStatus status) {
        runningList.removeAll();
        if (status.runningModels().isEmpty()) {
            renderEmpty("No models loaded in memory right now — they load on first use and unload when idle.");
            return;
        }
        Map<String, InstalledModel> byName = new LinkedHashMap<>();
        for (InstalledModel model : status.installedModels()) {
            byName.put(model.name(), model);
        }
        Instant now = Instant.now();
        for (RunningModel model : status.runningModels()) {
            runningList.add(buildRunningRow(model, byName.get(model.name()), now));
        }
    }

    private Div buildRunningRow(RunningModel model, InstalledModel info, Instant now) {
        Div row = new Div();
        row.getStyle().set("display", "flex").set("align-items", "baseline").set("gap", "10px")
                .set("padding", "2px 4px").set("font-size", "var(--lumo-font-size-s)");

        Span name = new Span(model.name());
        name.getStyle().set("font-weight", "600").set("flex", "1 1 auto")
                .set("font-family", "var(--lumo-font-family-monospace, monospace)");

        Span vram = new Span(FormatUtils.formatBytes(model.sizeVram()) + " VRAM");
        vram.getStyle().set("flex", "0 0 auto").set("color", "var(--lumo-primary-text-color)");

        Span expiry = new Span(unloadCountdown(model.expiresAt(), now));
        expiry.getStyle().set("flex", "0 0 auto").set("color", "var(--lumo-secondary-text-color)");

        row.add(name, vram);
        String specs = modelSpecs(info);
        if (!specs.isEmpty()) {
            Span specsSpan = new Span(specs);
            specsSpan.getStyle().set("flex", "0 0 auto").set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)");
            row.add(specsSpan);
        }
        row.add(expiry);
        return row;
    }

    static String modelSpecs(InstalledModel info) {
        if (info == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (info.parameterSize() != null) {
            parts.add(info.parameterSize());
        }
        if (info.contextLength() > 0) {
            parts.add(formatContext(info.contextLength()) + " ctx");
        }
        return String.join(" · ", parts);
    }

    static String formatContext(long tokens) {
        return tokens >= 1024 ? (tokens / 1024) + "K" : String.valueOf(tokens);
    }

    static String unloadCountdown(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return "—";
        }
        Duration left = Duration.between(now, expiresAt);
        return left.isNegative() || left.isZero()
                ? "unloading" : "unloads in " + FormatUtils.formatDuration(left);
    }

    private void renderEmpty(String message) {
        runningList.removeAll();
        Span empty = new Span(message);
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic").set("padding", "var(--lumo-space-s)");
        runningList.add(empty);
    }
}
