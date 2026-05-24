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
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.pricing.CurrencyService;
import org.springaicommunity.playground.observability.pricing.CurrencyService.CurrencyRate;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TokensAndCostTab extends BaseDashboardTab {

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final ObservabilityRingBuffer buffer;
    private final ObservabilityTimeSeries timeSeries;
    private final ModelPricingService pricingService;
    private final CurrencyService currencyService;

    private final KpiCard totalCostCard = new KpiCard("Total cost");
    private final KpiCard totalTokensCard = new KpiCard("Total tokens");
    private final KpiCard avgCostCard = new KpiCard("Avg cost / call");
    private final KpiCard costRateCard = new KpiCard("Cost rate /min");
    private final KpiCard maxCostCard = new KpiCard("Most expensive call");
    private final KpiCard paidShareCard = new KpiCard("Paid call share");
    private final KpiCard inputCostCard = new KpiCard("Input cost");
    private final KpiCard outputCostCard = new KpiCard("Output cost");

    private final ChartCanvas costOverTime = new ChartCanvas();
    private final ChartCanvas tokensStacked = new ChartCanvas();
    private final ChartCanvas costByProvider = new ChartCanvas();
    private final ChartCanvas costByModel = new ChartCanvas();
    private final ChartCanvas tokensByModel = new ChartCanvas();

    // Tables
    private final Grid<ModelStats> statsGrid = new Grid<>(ModelStats.class, false);
    private final Grid<ExpensiveTrace> topExpensive = new Grid<>(ExpensiveTrace.class, false);
    private Grid.Column<ModelStats> costColumn;

    public TokensAndCostTab(ObservabilityRingBuffer buffer, ObservabilityTimeSeries timeSeries,
            ModelPricingService pricingService, CurrencyService currencyService) {
        super();
        this.buffer = buffer;
        this.timeSeries = timeSeries;
        this.pricingService = pricingService;
        this.currencyService = currencyService;

        Div kpiRow = DashboardLayout.kpiGrid(totalCostCard, totalTokensCard, avgCostCard,
                costRateCard, maxCostCard, paidShareCard, inputCostCard, outputCostCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Cost over time",
                        "per 1-min bucket (active currency)",
                        "Σ pricingService.cost(model, in, out) per 1-min bucket, multiplied by the " +
                                "active currency rate. Missing prices count as $0 (free).",
                        costOverTime),
                DashboardLayout.chartCard("Tokens (input + output)",
                        "input + output per bucket",
                        "gen_ai.usage.input_tokens + output_tokens summed per bucket. From the trace " +
                                "ring buffer's TraceRecord.inputTokens / outputTokens fields.",
                        tokensStacked),
                DashboardLayout.chartCard("Cost by provider",
                        "share by provider (active currency)",
                        "Per-provider sum of trace cost. Provider from gen_ai.system span attribute.",
                        costByProvider),
                DashboardLayout.chartCard("Cost by model",
                        "top 8 (active currency)",
                        "Per-model cost (top 8 in window). Multiplied by active currency rate.",
                        costByModel),
                DashboardLayout.chartCard("Tokens by model",
                        "top 8 by total tokens",
                        "Total token count (input + output) per model across the window — top 8.",
                        tokensByModel)
        );

        // Per-model breakdown
        statsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        statsGrid.addColumn(ModelStats::model).setHeader("Model").setAutoWidth(true).setFlexGrow(1);
        statsGrid.addColumn(s -> String.valueOf(s.calls)).setHeader("Calls").setAutoWidth(true);
        statsGrid.addColumn(s -> FormatUtils.formatNumber(s.inputTokens)).setHeader("In tok").setAutoWidth(true);
        statsGrid.addColumn(s -> FormatUtils.formatNumber(s.outputTokens)).setHeader("Out tok").setAutoWidth(true);
        this.costColumn = statsGrid.addColumn(s -> formatUsd(s.cost)).setHeader("Cost").setAutoWidth(true);
        statsGrid.addColumn(s -> formatUsd(s.avgCostPerCall())).setHeader("Avg / call").setAutoWidth(true);
        statsGrid.setAllRowsVisible(true);

        // Top expensive traces
        topExpensive.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        topExpensive.addColumn(t -> HHMM.format(Instant.ofEpochMilli(t.startEpochMs))).setHeader("Time").setAutoWidth(true);
        topExpensive.addColumn(t -> safe(t.model)).setHeader("Model").setAutoWidth(true);
        topExpensive.addColumn(t -> FormatUtils.formatNumber(t.inputTokens)).setHeader("In").setAutoWidth(true);
        topExpensive.addColumn(t -> FormatUtils.formatNumber(t.outputTokens)).setHeader("Out").setAutoWidth(true);
        topExpensive.addColumn(t -> formatUsd(t.cost)).setHeader("Cost").setAutoWidth(true);
        topExpensive.addColumn(t -> shorten(t.traceId)).setHeader("Trace").setAutoWidth(true);
        topExpensive.addItemClickListener(e -> {
            if (e.getItem() != null) {
                UI.getCurrent().navigate("observability?tab=traces&trace=" + e.getItem().traceId);
            }
        });
        topExpensive.setAllRowsVisible(true);

        H3 perModelHeader = new H3("Per-model breakdown");
        H3 expensiveHeader = new H3("Top 10 most expensive traces");
        for (H3 h : new H3[] { perModelHeader, expensiveHeader }) {
            h.getStyle().set("margin", "var(--lumo-space-l) 0 var(--lumo-space-xs) 0")
                    .set("font-size", "var(--lumo-font-size-l)");
        }

        content.add(kpiRow, grid,
                perModelHeader, statsGrid,
                expensiveHeader, topExpensive);
    }

    @Override
    public void refresh(Window window) {
        ObservabilityTimeSeries.Series s = timeSeries.compute(window);

        // Per-trace + per-model aggregation in window
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        Map<String, ModelStats> modelMap = new HashMap<>();
        Map<String, BigDecimal> providerCost = new LinkedHashMap<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalInputCost = BigDecimal.ZERO;
        BigDecimal totalOutputCost = BigDecimal.ZERO;
        BigDecimal maxTraceCost = BigDecimal.ZERO;
        long paidCalls = 0;
        long totalCalls = 0;
        List<ExpensiveTrace> all = new ArrayList<>();

        // Cost-per-bucket for line chart
        int buckets = window.buckets();
        double[] costPerBucket = new double[buckets];

        for (TraceRecord t : buffer.snapshot()) {
            if (t.startEpochMs() < windowStart) continue;
            String model = t.model() == null ? "(unknown)" : t.model();
            String provider = t.provider() == null ? "(unknown)" : t.provider();
            long in = orZero(t.inputTokens());
            long out = orZero(t.outputTokens());
            BigDecimal traceCost = pricingService.cost(model, in, out);
            BigDecimal inCost = inputCost(model, in);
            BigDecimal outCost = outputCost(model, out);

            ModelStats stats = modelMap.computeIfAbsent(model, ModelStats::new);
            stats.calls++;
            stats.inputTokens += in;
            stats.outputTokens += out;
            stats.cost = stats.cost.add(traceCost);

            providerCost.merge(provider, traceCost, BigDecimal::add);
            totalCost = totalCost.add(traceCost);
            totalInputCost = totalInputCost.add(inCost);
            totalOutputCost = totalOutputCost.add(outCost);
            totalCalls++;
            if (traceCost.signum() > 0) paidCalls++;
            if (traceCost.compareTo(maxTraceCost) > 0) maxTraceCost = traceCost;

            int bk = (int) Math.min(buckets - 1,
                    Math.max(0, (t.startEpochMs() - windowStart) / window.bucketMs()));
            costPerBucket[bk] += traceCost.doubleValue();

            all.add(new ExpensiveTrace(t.traceId(), t.model(), t.provider(),
                    t.startEpochMs(), in, out, traceCost));
        }

        totalCostCard.setValue(formatUsd(totalCost),
                "Σ ModelPricingService.cost(model, in, out) across all traces in window · active currency");
        totalTokensCard.setValue(FormatUtils.formatNumber(s.totalTokens()),
                "Σ (input + output tokens) across all traces in window");
        BigDecimal avg = totalCalls == 0 ? BigDecimal.ZERO :
                totalCost.divide(BigDecimal.valueOf(totalCalls), 6, RoundingMode.HALF_UP);
        avgCostCard.setValue(formatUsd(avg),
                "Total cost / call count — useful baseline for spend per LLM call");
        BigDecimal rate = window.minutes == 0 ? BigDecimal.ZERO :
                totalCost.divide(BigDecimal.valueOf(window.minutes), 6, RoundingMode.HALF_UP);
        costRateCard.setValue(formatUsd(rate),
                "Total cost / window minutes — extrapolation requires steady-state traffic");
        maxCostCard.setValue(formatUsd(maxTraceCost),
                "Single most expensive trace in window — drill into Traces tab to inspect");
        double paidPct = totalCalls == 0 ? 0 : paidCalls * 100.0 / totalCalls;
        paidShareCard.setValue(String.format(Locale.ROOT, "%.0f%% (%d/%d)",
                paidPct, paidCalls, totalCalls),
                "Fraction of traces where pricingService.cost() returned > 0 (i.e. model has pricing configured)");

        // Charts — multiply by active rate so chart values match KPI display.
        double rateMultiplier = currencyService.getActiveRate().rateFromUsd().doubleValue();
        String activeSymbol = currencyService.getActiveRate().symbol();
        double[] costPerBucketActive = new double[costPerBucket.length];
        for (int i = 0; i < costPerBucket.length; i++) {
            costPerBucketActive[i] = costPerBucket[i] * rateMultiplier;
        }
        List<String> labels = DashboardData.bucketLabelsFromStarts(s.bucketStartMs());
        costOverTime.lineChart(labels, "cost", costPerBucketActive, DashboardPalette.PRIMARY, activeSymbol);
        tokensStacked.stackedBarChart(labels,
                "Input", DashboardData.toDouble(s.inTokens()), DashboardPalette.INFO,
                "Output", DashboardData.toDouble(s.outTokens()), DashboardPalette.SUCCESS);

        Map<String, Double> providerCostDouble = new LinkedHashMap<>();
        providerCost.forEach((k, v) -> providerCostDouble.put(k,
                currencyService.convertFromUsd(v).doubleValue()));
        if (providerCostDouble.values().stream().allMatch(v -> v == 0.0)) {
            // Fall back to call counts when no pricing has been configured
            costByProvider.donutChart(s.totalCallsByProvider(), ChartCanvas.DEFAULT_PALETTE);
        } else {
            costByProvider.donutChartDecimal(providerCostDouble, ChartCanvas.DEFAULT_PALETTE, activeSymbol);
        }

        Map<String, Double> costByModelDouble = new LinkedHashMap<>();
        for (ModelStats stats : modelMap.values()) {
            costByModelDouble.put(stats.model,
                    currencyService.convertFromUsd(stats.cost).doubleValue());
        }
        if (costByModelDouble.values().stream().allMatch(v -> v == 0.0)) {
            costByModel.horizontalBarChart(s.totalCallsByModel(), DashboardPalette.PRIMARY, 8);
        } else {
            costByModel.horizontalBarChartDecimal(costByModelDouble, DashboardPalette.PRIMARY, 8, activeSymbol);
        }

        inputCostCard.setValue(formatUsd(totalInputCost),
                "Σ input_tokens × model.inputPerMillionTokens / 1M · active currency");
        outputCostCard.setValue(formatUsd(totalOutputCost),
                "Σ output_tokens × model.outputPerMillionTokens / 1M · active currency");

        tokensByModel.horizontalBarChart(s.totalTokensByModel(), DashboardPalette.SUCCESS, 8);

        if (costColumn != null) costColumn.setHeader("Cost " + currencyService.getActiveCurrency());
        // Tables
        List<ModelStats> rows = new ArrayList<>(modelMap.values());
        rows.sort((a, b) -> b.cost.compareTo(a.cost));
        statsGrid.setItems(rows);

        all.sort(Comparator.comparing((ExpensiveTrace t) -> t.cost).reversed());
        topExpensive.setItems(all.size() > 10 ? all.subList(0, 10) : all);

        setStatus(totalCalls + " calls · " + formatUsd(totalCost) + " spent · "
                + paidCalls + " paid · " + modelMap.size() + " models · "
                + window.minutes + "m window");
    }

    private BigDecimal inputCost(String model, long inTokens) {
        return pricingService.find(model)
                .map(p -> nzBd(p.inputPerMillionTokens())
                        .multiply(BigDecimal.valueOf(inTokens))
                        .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal outputCost(String model, long outTokens) {
        return pricingService.find(model)
                .map(p -> nzBd(p.outputPerMillionTokens())
                        .multiply(BigDecimal.valueOf(outTokens))
                        .divide(BigDecimal.valueOf(1_000_000L), 6, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO);
    }

    private long orZero(Long v) { return v == null ? 0 : v; }
    private BigDecimal nzBd(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String safe(String s) { return s == null ? "—" : s; }

    private static String shorten(String s) {
        if (s == null) return "—";
        return s.length() <= 12 ? s : s.substring(0, 12) + "…";
    }

    private String formatUsd(BigDecimal usd) {
        CurrencyRate rate = currencyService.getActiveRate();
        BigDecimal v = currencyService.convertFromUsd(usd);
        String symbol = rate.symbol() == null ? "$" : rate.symbol();
        if (v == null || v.signum() == 0) return symbol + "0.00";
        if (v.compareTo(new BigDecimal("0.01")) < 0) {
            return String.format(Locale.ROOT, "%s%.6f", symbol, v.doubleValue());
        }
        return String.format(Locale.ROOT, "%s%.4f", symbol, v.doubleValue());
    }

    private static class ModelStats {
        final String model;
        long calls;
        long inputTokens;
        long outputTokens;
        BigDecimal cost = BigDecimal.ZERO;

        ModelStats(String model) { this.model = model; }
        public String model() { return model; }
        BigDecimal avgCostPerCall() {
            if (calls == 0) return BigDecimal.ZERO;
            return cost.divide(BigDecimal.valueOf(calls), 6, RoundingMode.HALF_UP);
        }
    }

    private record ExpensiveTrace(String traceId, String model, String provider,
            long startEpochMs, long inputTokens, long outputTokens, BigDecimal cost) {}
}
