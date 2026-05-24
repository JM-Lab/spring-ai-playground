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
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.TimerSummary;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas.LineSeries;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LlmTab extends BaseDashboardTab {

    private final ObservabilityTimeSeries timeSeries;
    private final SystemMetricsSnapshot systemMetrics;

    private final KpiCard totalCallsCard = new KpiCard("Total calls");
    private final KpiCard distinctModelsCard = new KpiCard("Distinct models");
    private final KpiCard distinctProvidersCard = new KpiCard("Providers");
    private final KpiCard tokensThroughputCard = new KpiCard("Tokens / min");
    private final KpiCard p50LatencyCard = new KpiCard("p50 latency");
    private final KpiCard p99LatencyCard = new KpiCard("p99 latency");
    private final KpiCard ttftCard = new KpiCard("TTFT");
    private final KpiCard perChunkCard = new KpiCard("Per-chunk");
    private final KpiCard embeddingCard = new KpiCard("Embedding");
    private final KpiCard imageCard = new KpiCard("Image");

    private final ChartCanvas callsByModelChart = new ChartCanvas();
    private final ChartCanvas tokensTrendChart = new ChartCanvas();
    private final ChartCanvas tokensByModelChart = new ChartCanvas();
    private final ChartCanvas providerDonut = new ChartCanvas();
    private final ChartCanvas providerHttpBar = new ChartCanvas();

    public LlmTab(ObservabilityTimeSeries timeSeries, SystemMetricsSnapshot systemMetrics) {
        super();
        this.timeSeries = timeSeries;
        this.systemMetrics = systemMetrics;

        Div kpiGrid = DashboardLayout.kpiGrid(totalCallsCard, distinctModelsCard, distinctProvidersCard,
                tokensThroughputCard, p50LatencyCard, p99LatencyCard,
                ttftCard, perChunkCard, embeddingCard, imageCard);

        Div chartGrid = DashboardLayout.chartGrid();
        chartGrid.add(
                DashboardLayout.chartCard("Calls per model",
                        "top 6 models over time",
                        "Count of root LLM traces per model per 1-min bucket, top 6 models by total. " +
                                "Source: TraceRecord.model from gen_ai.response.model attribute.",
                        callsByModelChart),
                DashboardLayout.chartCard("Tokens over time",
                        "input + output per bucket",
                        "Σ gen_ai.usage.input_tokens + output_tokens per bucket, stacked.",
                        tokensTrendChart),
                DashboardLayout.chartCard("Tokens per model",
                        "total in window",
                        "Σ tokens (input + output) per model in the current window, ranked.",
                        tokensByModelChart),
                DashboardLayout.chartCard("Provider mix",
                        "share of calls",
                        "Trace count grouped by provider (gen_ai.system span attribute).",
                        providerDonut),
                DashboardLayout.chartCard("Provider HTTP requests by host",
                        "from http.client.requests — outbound provider HTTP",
                        "http.client.requests Timer count by client.name. Visible only when an outbound HTTP provider is in use (OpenAI, Anthropic, …); Ollama-local is in-process.",
                        providerHttpBar)
        );

        content.add(kpiGrid, chartGrid);
    }

    @Override
    public void refresh(Window window) {
        ObservabilityTimeSeries.Series s = timeSeries.compute(window);
        List<String> labels = DashboardData.bucketLabelsFromStarts(s.bucketStartMs());

        totalCallsCard.setValue(FormatUtils.formatNumber(s.totalCalls()),
                "Total LLM chat-client traces in window — root spans with name spring.ai.chat.client");
        distinctModelsCard.setValue(String.valueOf(s.totalCallsByModel().size()),
                "Distinct gen_ai.response.model values seen in window");
        distinctProvidersCard.setValue(String.valueOf(s.totalCallsByProvider().size()),
                "Distinct gen_ai.system values (provider names) seen in window");
        double tpm = window.minutes == 0 ? 0 : (double) s.totalTokens() / window.minutes;
        tokensThroughputCard.setValue(FormatUtils.formatNumber((long) tpm),
                "Σ (input + output tokens) / window minutes");
        p50LatencyCard.setValue(FormatUtils.formatLatencyMs(s.overallP50LatencyMs()),
                "50th percentile of LLM root span duration in window (exact)");
        p99LatencyCard.setValue(FormatUtils.formatLatencyMs(s.overallP99LatencyMs()),
                "99th percentile of LLM root span duration in window (exact)");

        SystemMetricsSnapshot.Snapshot sys = systemMetrics.capture();
        setTimerCard(ttftCard, sys.genAiTtft,
                "gen_ai.client.operation.time_to_first_chunk — streaming only, provider-dependent");
        setTimerCard(perChunkCard, sys.genAiPerChunk,
                "gen_ai.client.operation.time_per_output_chunk — streaming, provider-dependent");
        setTimerCard(embeddingCard, sys.genAiEmbeddingDuration,
                "gen_ai.embedding.client.operation");
        setTimerCard(imageCard, sys.genAiImageDuration,
                "gen_ai.image.client.operation");

        List<LineSeries> callsSeries = new ArrayList<>();
        String[] palette = ChartCanvas.DEFAULT_PALETTE;
        int idx = 0;
        List<Map.Entry<String, long[]>> entries = new ArrayList<>(s.callsByModelBuckets().entrySet());
        entries.sort((a, b) -> Long.compare(
                s.totalCallsByModel().getOrDefault(b.getKey(), 0L),
                s.totalCallsByModel().getOrDefault(a.getKey(), 0L)));
        for (Map.Entry<String, long[]> e : entries) {
            if (idx >= 6) break;
            callsSeries.add(new LineSeries(e.getKey(),
                    DashboardData.toDouble(e.getValue()),
                    palette[idx % palette.length]));
            idx++;
        }
        if (callsSeries.isEmpty()) {
            callsSeries.add(new LineSeries("(no data)", new double[s.calls().length], palette[0]));
        }
        callsByModelChart.multiStackedBar(labels, callsSeries);

        tokensTrendChart.stackedBarChart(labels,
                "Input", DashboardData.toDouble(s.inTokens()), DashboardPalette.INFO,
                "Output", DashboardData.toDouble(s.outTokens()), DashboardPalette.SUCCESS);

        tokensByModelChart.horizontalBarChart(new LinkedHashMap<>(s.totalTokensByModel()), DashboardPalette.PRIMARY, 8);
        providerDonut.donutChart(new LinkedHashMap<>(s.totalCallsByProvider()), ChartCanvas.DEFAULT_PALETTE);

        Map<String, Long> httpByHost = new LinkedHashMap<>();
        sys.httpClientByHost.forEach((host, hs) -> httpByHost.put(host, hs.count));
        providerHttpBar.horizontalBarChart(httpByHost, DashboardPalette.PRIMARY, 8);

        setStatus(s.totalCalls() + " calls · " + s.totalCallsByModel().size() + " models · "
                + s.totalCallsByProvider().size() + " providers · TTFT samples: "
                + sys.genAiTtft.count + " · embedding samples: " + sys.genAiEmbeddingDuration.count
                + " · window: " + window.minutes + "m");
    }

    private static void setTimerCard(KpiCard card, TimerSummary t, String source) {
        if (t.count == 0) {
            card.setValue("—", source + " — no samples yet");
            return;
        }
        card.setValue(FormatUtils.formatLatencyMs(t.meanMs),
                "max " + FormatUtils.formatLatencyMs(t.maxMs) + " · " + t.count + " samples · " + source);
    }

}
