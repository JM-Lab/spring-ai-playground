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
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VectorTab extends BaseDashboardTab {

    private final ObservabilityRingBuffer buffer;
    private final ObservabilityTimeSeries timeSeries;

    private final KpiCard tracesWithRagCard = new KpiCard("Traces with RAG");
    private final KpiCard totalQueriesCard = new KpiCard("Vector queries");
    private final KpiCard avgTopKCard = new KpiCard("Avg top_k");
    private final KpiCard avgQueryLatencyCard = new KpiCard("Avg query latency");

    private final ChartCanvas opsByTypeChart = new ChartCanvas();
    private final ChartCanvas queryLatencyChart = new ChartCanvas();
    private final ChartCanvas topKHistogramChart = new ChartCanvas();
    private final ChartCanvas similarityHistogramChart = new ChartCanvas();
    private final ChartCanvas dbSystemsDonut = new ChartCanvas();
    private final ChartCanvas avgReturnedDocsBar = new ChartCanvas();

    public VectorTab(ObservabilityRingBuffer buffer, ObservabilityTimeSeries timeSeries) {
        super();
        this.buffer = buffer;
        this.timeSeries = timeSeries;

        Div cardRow = DashboardLayout.kpiGrid(tracesWithRagCard, totalQueriesCard,
                avgTopKCard, avgQueryLatencyCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Operations / minute", "query / add / delete",
                        "db.vector.client.operation spans per bucket, stacked by db.operation.name. " +
                                "Reflects RAG query traffic plus index updates.",
                        opsByTypeChart),
                DashboardLayout.chartCard("Query latency p50 / p95 / p99", "ms per bucket",
                        "Wall-clock duration of db.vector.client.operation spans (query only), percentile-ed per bucket.",
                        queryLatencyChart),
                DashboardLayout.chartCard("top_k distribution", "requested top_k buckets",
                        "db.vector.query.top_k attribute on query spans — how many docs the LLM asked the vector store for.",
                        topKHistogramChart),
                DashboardLayout.chartCard("Similarity threshold distribution", "0.0 = any, 1.0 = exact",
                        "db.vector.query.similarity_threshold attribute. Higher = stricter match.",
                        similarityHistogramChart),
                DashboardLayout.chartCard("DB systems", "from db.system",
                        "db.system span attribute (simple_vector_store, pgvector, chroma, …).",
                        dbSystemsDonut),
                DashboardLayout.chartCard("Avg returned docs by db", "from db.vector.query.response",
                        "Mean of db.vector.query.response.documents.count per backend.",
                        avgReturnedDocsBar)
        );

        content.add(cardRow, grid);
    }

    @Override
    public void refresh(Window window) {
        ObservabilityTimeSeries.Series s = timeSeries.compute(window);
        List<String> labels = DashboardData.bucketLabelsFromStarts(s.bucketStartMs());
        long windowStart = Instant.now().toEpochMilli() - window.totalMs();
        int buckets = window.buckets();

        long[] queryBucket = new long[buckets];
        long[] addBucket = new long[buckets];
        long[] deleteBucket = new long[buckets];
        @SuppressWarnings("unchecked")
        List<Long>[] latencyPerBucket = new List[buckets];
        for (int i = 0; i < buckets; i++) latencyPerBucket[i] = new ArrayList<>();

        Map<String, Long> dbSystems = new LinkedHashMap<>();
        Map<Long, Long> topKDist = new LinkedHashMap<>();
        Map<String, Long> simBands = new LinkedHashMap<>();
        Map<String, Long> returnedDocsSum = new LinkedHashMap<>();
        Map<String, Long> returnedDocsCount = new LinkedHashMap<>();

        long totalQueries = 0;
        long totalTopK = 0;
        long topKSamples = 0;
        long totalLatencyMs = 0;
        long latencySamples = 0;

        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            if (t.spans() == null) continue;
            for (SpanRecord span : t.spans()) {
                if (!"db.vector.client.operation".equals(span.name())) continue;
                int b = (int) Math.min(buckets - 1,
                        Math.max(0, (span.startEpochMs() - windowStart) / window.bucketMs()));
                String op = span.attributes().getOrDefault("db.operation.name", "?");
                switch (op) {
                    case "query" -> {
                        queryBucket[b]++;
                        totalQueries++;
                    }
                    case "add" -> addBucket[b]++;
                    case "delete" -> deleteBucket[b]++;
                    default -> { }
                }
                String sys = span.attributes().getOrDefault("db.system", "(unknown)");
                dbSystems.merge(sys, 1L, Long::sum);

                String topK = span.attributes().get("db.vector.query.top_k");
                if (topK != null) {
                    try {
                        long k = Long.parseLong(topK);
                        totalTopK += k;
                        topKSamples++;
                        topKDist.merge(k, 1L, Long::sum);
                    } catch (NumberFormatException ignored) {}
                }
                String thr = span.attributes().get("db.vector.query.similarity_threshold");
                if (thr != null) {
                    try {
                        double d = Double.parseDouble(thr);
                        simBands.merge(simBand(d), 1L, Long::sum);
                    } catch (NumberFormatException ignored) {}
                }
                String returned = span.attributes().get("db.vector.query.response.documents.count");
                if (returned == null) returned = span.attributes().get("rag.documents.count");
                if (returned != null) {
                    try {
                        long r = Long.parseLong(returned);
                        returnedDocsSum.merge(sys, r, Long::sum);
                        returnedDocsCount.merge(sys, 1L, Long::sum);
                    } catch (NumberFormatException ignored) {}
                }

                latencyPerBucket[b].add(span.durationMs());
                totalLatencyMs += span.durationMs();
                latencySamples++;
            }
        }

        tracesWithRagCard.setValue(String.valueOf(s.tracesWithRag()),
                "Distinct trace IDs containing at least one db.vector.client.operation span");
        totalQueriesCard.setValue(String.valueOf(totalQueries),
                "Σ db.vector.client.operation spans where db.operation.name = query");
        avgTopKCard.setValue(topKSamples == 0 ? "—" : String.valueOf(totalTopK / topKSamples),
                "Mean of db.vector.query.top_k attribute across query spans (" + topKSamples + " samples)");
        avgQueryLatencyCard.setValue(latencySamples == 0 ? "—"
                : FormatUtils.formatLatencyMs((double) totalLatencyMs / latencySamples),
                "Mean wall-clock duration of query spans (" + latencySamples + " samples)");

        opsByTypeChart.multiStackedBar(labels, List.of(
                new ChartCanvas.LineSeries("query", DashboardData.toDouble(queryBucket), DashboardPalette.INFO),
                new ChartCanvas.LineSeries("add", DashboardData.toDouble(addBucket), DashboardPalette.SUCCESS),
                new ChartCanvas.LineSeries("delete", DashboardData.toDouble(deleteBucket), DashboardPalette.ERROR)
        ));

        // Latency percentiles
        double[] p50 = new double[buckets];
        double[] p95 = new double[buckets];
        double[] p99 = new double[buckets];
        for (int i = 0; i < buckets; i++) {
            long[] arr = latencyPerBucket[i].stream().mapToLong(Long::longValue).sorted().toArray();
            if (arr.length > 0) {
                p50[i] = DashboardData.percentile(arr, 50);
                p95[i] = DashboardData.percentile(arr, 95);
                p99[i] = DashboardData.percentile(arr, 99);
            }
        }
        queryLatencyChart.multiLineChart(labels, List.of(
                new ChartCanvas.LineSeries("p50", p50, DashboardPalette.INFO),
                new ChartCanvas.LineSeries("p95", p95, DashboardPalette.WARN),
                new ChartCanvas.LineSeries("p99", p99, DashboardPalette.ERROR)
        ), "ms");

        // top_k histogram — sort numeric
        Map<String, Long> topKLabeled = new LinkedHashMap<>();
        topKDist.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> topKLabeled.put("k=" + e.getKey(), e.getValue()));
        topKHistogramChart.horizontalBarChart(topKLabeled, DashboardPalette.PRIMARY, 10);

        similarityHistogramChart.horizontalBarChart(simBands, DashboardPalette.INFO, 10);

        dbSystemsDonut.donutChart(dbSystems, ChartCanvas.DEFAULT_PALETTE);

        Map<String, Long> avgReturned = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : returnedDocsCount.entrySet()) {
            long count = e.getValue();
            long sum = returnedDocsSum.getOrDefault(e.getKey(), 0L);
            avgReturned.put(e.getKey(), count == 0 ? 0 : sum / count);
        }
        avgReturnedDocsBar.horizontalBarChart(avgReturned, DashboardPalette.SUCCESS, 6);

        long totalOps = 0;
        for (long v : queryBucket) totalOps += v;
        for (long v : addBucket) totalOps += v;
        for (long v : deleteBucket) totalOps += v;
        setStatus(totalOps + " vector ops · " + dbSystems.size() + " db systems · "
                + s.tracesWithRag() + " RAG traces · window: " + window.minutes + "m");
    }

    private static String simBand(double d) {
        if (d < 0.1) return "[0.0, 0.1)";
        if (d < 0.3) return "[0.1, 0.3)";
        if (d < 0.5) return "[0.3, 0.5)";
        if (d < 0.7) return "[0.5, 0.7)";
        if (d < 0.9) return "[0.7, 0.9)";
        return "[0.9, 1.0]";
    }

}
