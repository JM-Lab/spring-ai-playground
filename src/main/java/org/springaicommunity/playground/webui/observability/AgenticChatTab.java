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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import org.springaicommunity.playground.observability.conversation.ConversationAggregator;
import org.springaicommunity.playground.observability.conversation.ConversationAggregator.ConversationSummary;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.DashboardData;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas.LineSeries;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AgenticChatTab extends BaseDashboardTab {

    private static final DateTimeFormatter SHORT_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LIST_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final ObservabilityRingBuffer buffer;
    private final ModelPricingService pricingService;

    private final KpiCard activeConversationsCard = new KpiCard("Conversations");
    private final KpiCard avgMessagesCard = new KpiCard("Avg messages / conv");
    private final KpiCard avgCostCard = new KpiCard("Avg cost / conv");
    private final KpiCard avgDurationCard = new KpiCard("Avg duration");
    private final KpiCard multiTurnRateCard = new KpiCard("Multi-turn rate");
    private final KpiCard toolUsingRateCard = new KpiCard("Tool-using rate");
    private final KpiCard ragUsingRateCard = new KpiCard("RAG-using rate");
    private final KpiCard maxLoopDepthCard = new KpiCard("Max loop depth (p95)");

    private final ChartCanvas messagesOverTimeChart = new ChartCanvas();
    private final ChartCanvas conversationsStartedChart = new ChartCanvas();
    private final ChartCanvas loopDepthHistogram = new ChartCanvas();
    private final ChartCanvas durationHistogram = new ChartCanvas();
    private final ChartCanvas topToolsBar = new ChartCanvas();
    private final ChartCanvas modelsBar = new ChartCanvas();
    private final ChartCanvas costPerConvBar = new ChartCanvas();
    private final ChartCanvas statusDonut = new ChartCanvas();

    private final Grid<ConversationSummary> conversationsGrid = new Grid<>(ConversationSummary.class, false);

    public AgenticChatTab(ObservabilityRingBuffer buffer, ModelPricingService pricingService) {
        super();
        this.buffer = buffer;
        this.pricingService = pricingService;

        Div kpiGrid = DashboardLayout.kpiGrid(activeConversationsCard, avgMessagesCard, avgCostCard,
                avgDurationCard, multiTurnRateCard, toolUsingRateCard, ragUsingRateCard,
                maxLoopDepthCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Messages over time",
                        "direct LLM vs tool-using per bucket",
                        "Each TraceRecord = one assistant turn. Stacked by tool-call count " +
                                "buckets: 0 tools (direct), 1-2 tools (light agentic), 3+ (deep loop).",
                        messagesOverTimeChart),
                DashboardLayout.chartCard("New conversations / minute",
                        "first message in a conversation",
                        "Counts unique conversation.id with first message in each bucket.",
                        conversationsStartedChart),
                DashboardLayout.chartCard("Agentic loop depth",
                        "tool calls per message",
                        "Distribution of toolCallCount across messages. High loop depth indicates " +
                                "the agent recursed deeply (multiple tool calls before final answer).",
                        loopDepthHistogram),
                DashboardLayout.chartCard("Conversation duration",
                        "first → last message",
                        "How long conversations remain active. Short = one-shot Q&A, long = " +
                                "extended multi-turn or background-running.",
                        durationHistogram),
                DashboardLayout.chartCard("Tools used in conversations",
                        "top tools by occurrence count",
                        "Number of conversations that called each tool at least once.",
                        topToolsBar),
                DashboardLayout.chartCard("Models used in conversations",
                        "top models by conversation count",
                        "Number of conversations that used each model.",
                        modelsBar),
                DashboardLayout.chartCard("Cost per conversation",
                        "top 10 by cost (USD)",
                        "Identifies the most expensive conversations in the window.",
                        costPerConvBar),
                DashboardLayout.chartCard("Conversation outcomes",
                        "worst status per conversation — OK / Error / Cancelled",
                        "Worst status across messages in each conversation.",
                        statusDonut)
        );

        configureConversationsGrid();
        Div gridCard = new Div(conversationsGrid);
        gridCard.getStyle().set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-s)");

        H3 conversationsHeader = new H3("Conversations — click row to open thread view");
        conversationsHeader.getStyle().set("margin", "var(--lumo-space-l) 0 var(--lumo-space-xs) 0")
                .set("font-size", "var(--lumo-font-size-l)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "600");

        content.add(kpiGrid, grid, conversationsHeader, gridCard);
    }

    private void configureConversationsGrid() {
        conversationsGrid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        conversationsGrid.addColumn(c -> LIST_FMT.format(Instant.ofEpochMilli(c.firstStartMs())))
                .setHeader("Started").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> shorten(c.conversationId(), 14))
                .setHeader("Conv ID").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> String.valueOf(c.messageCount()))
                .setHeader("Msgs").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> FormatUtils.formatLatencyMs(c.durationMs()))
                .setHeader("Duration").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> String.valueOf(c.toolCalls()))
                .setHeader("Tools").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> String.valueOf(c.ragCalls()))
                .setHeader("RAG").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> FormatUtils.formatNumber(c.totalTokens()))
                .setHeader("Tokens").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> formatUsd(c.cost()))
                .setHeader("Cost").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.addColumn(c -> String.join(", ", c.modelsUsed()))
                .setHeader("Models").setAutoWidth(true).setFlexGrow(1);
        conversationsGrid.addColumn(ConversationSummary::worstStatus)
                .setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        conversationsGrid.setAllRowsVisible(true);
        conversationsGrid.addItemClickListener(e -> {
            if (e.getItem() != null) {
                new ConversationThreadDialog(e.getItem().conversationId(), buffer).open();
            }
        });
    }

    @Override
    public void refresh(Window window) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - window.totalMs();
        List<TraceRecord> traces = buffer.snapshot();
        List<ConversationSummary> conversations = ConversationAggregator.aggregate(
                traces, pricingService, windowStart);

        activeConversationsCard.setValue(String.valueOf(conversations.size()),
                "Unique conversation.id values with at least one trace in window");

        if (conversations.isEmpty()) {
            avgMessagesCard.setValue("—", "No conversations in window");
            avgCostCard.setValue("—", "No conversations in window");
            avgDurationCard.setValue("—", "No conversations in window");
            multiTurnRateCard.setValue("—", "No conversations in window");
            toolUsingRateCard.setValue("—", "No conversations in window");
            ragUsingRateCard.setValue("—", "No conversations in window");
            maxLoopDepthCard.setValue("—", "No conversations in window");
        } else {
            double avgMsgs = conversations.stream().mapToLong(ConversationSummary::messageCount).average().orElse(0);
            avgMessagesCard.setValue(String.format(Locale.ROOT, "%.1f", avgMsgs),
                    "Mean number of messages per conversation in window");

            BigDecimal totalCost = BigDecimal.ZERO;
            for (ConversationSummary c : conversations) totalCost = totalCost.add(c.cost());
            BigDecimal avgCost = totalCost.divide(BigDecimal.valueOf(conversations.size()), 6, RoundingMode.HALF_UP);
            avgCostCard.setValue(formatUsd(avgCost),
                    "Mean ModelPricingService.cost(model, in, out) summed per conversation. "
                            + "Free Ollama models = $0.");

            double avgDur = conversations.stream().mapToLong(ConversationSummary::durationMs).average().orElse(0);
            avgDurationCard.setValue(FormatUtils.formatLatencyMs(avgDur),
                    "Mean span from first to last message in each conversation");

            long multiTurn = conversations.stream().filter(ConversationSummary::hasMultipleTurns).count();
            multiTurnRateCard.setValue(String.format(Locale.ROOT, "%.0f%%",
                    multiTurn * 100.0 / conversations.size()),
                    multiTurn + " of " + conversations.size() + " conversations had >1 message");

            long toolUsing = conversations.stream().filter(ConversationSummary::usedTools).count();
            toolUsingRateCard.setValue(String.format(Locale.ROOT, "%.0f%%",
                    toolUsing * 100.0 / conversations.size()),
                    toolUsing + " of " + conversations.size()
                            + " conversations invoked at least one tool");

            long ragUsing = conversations.stream().filter(ConversationSummary::usedRag).count();
            ragUsingRateCard.setValue(String.format(Locale.ROOT, "%.0f%%",
                    ragUsing * 100.0 / conversations.size()),
                    ragUsing + " of " + conversations.size()
                            + " conversations queried a vector store");

            int[] depths = conversations.stream().mapToInt(ConversationSummary::maxLoopDepth).sorted().toArray();
            int p95Depth = depths.length == 0 ? 0 : depths[Math.min(depths.length - 1, (int) Math.ceil(0.95 * (depths.length - 1)))];
            maxLoopDepthCard.setValue(String.valueOf(p95Depth),
                    "p95 of max tool-call count per message across all conversations");
        }

        int buckets = window.buckets();
        long bucketMs = window.bucketMs();
        List<String> labels = DashboardData.bucketLabelsFromWindow(windowStart, bucketMs, buckets);

        double[] direct = new double[buckets];
        double[] lightAgentic = new double[buckets];
        double[] deepLoop = new double[buckets];
        double[] newConvPerBucket = new double[buckets];

        Map<String, Long> firstSeen = new LinkedHashMap<>();
        for (TraceRecord t : traces) {
            if (t.startEpochMs() < windowStart) continue;
            int bk = (int) Math.min(buckets - 1, Math.max(0, (t.startEpochMs() - windowStart) / bucketMs));
            int depth = t.toolCallCount();
            if (depth == 0) direct[bk]++;
            else if (depth <= 2) lightAgentic[bk]++;
            else deepLoop[bk]++;

            if (t.conversationId() != null && !t.conversationId().isBlank()) {
                Long prev = firstSeen.get(t.conversationId());
                if (prev == null || t.startEpochMs() < prev) {
                    firstSeen.put(t.conversationId(), t.startEpochMs());
                }
            }
        }
        for (Long ts : firstSeen.values()) {
            int bk = (int) Math.min(buckets - 1, Math.max(0, (ts - windowStart) / bucketMs));
            newConvPerBucket[bk]++;
        }

        messagesOverTimeChart.multiStackedBar(labels, List.of(
                new LineSeries("0 tools (direct)", direct, DashboardPalette.SUCCESS),
                new LineSeries("1-2 tools (light)", lightAgentic, DashboardPalette.INFO),
                new LineSeries("3+ tools (deep)", deepLoop, DashboardPalette.ERROR)
        ));

        conversationsStartedChart.lineChart(labels, "New conversations", newConvPerBucket, DashboardPalette.PRIMARY, "");

        Map<String, Long> depthBins = new LinkedHashMap<>();
        depthBins.put("0", 0L);
        depthBins.put("1", 0L);
        depthBins.put("2", 0L);
        depthBins.put("3-5", 0L);
        depthBins.put("6+", 0L);
        for (TraceRecord t : traces) {
            if (t.startEpochMs() < windowStart) continue;
            int d = t.toolCallCount();
            String key = d == 0 ? "0" : d == 1 ? "1" : d == 2 ? "2" : d <= 5 ? "3-5" : "6+";
            depthBins.merge(key, 1L, Long::sum);
        }
        loopDepthHistogram.horizontalBarChart(depthBins, DashboardPalette.ERROR, 5);

        Map<String, Long> durationBins = new LinkedHashMap<>();
        durationBins.put("< 5s", 0L);
        durationBins.put("5-30s", 0L);
        durationBins.put("30s-2m", 0L);
        durationBins.put("2-10m", 0L);
        durationBins.put("10m+", 0L);
        for (ConversationSummary c : conversations) {
            long d = c.durationMs() / 1000;
            String key = d < 5 ? "< 5s" : d < 30 ? "5-30s" : d < 120 ? "30s-2m" : d < 600 ? "2-10m" : "10m+";
            durationBins.merge(key, 1L, Long::sum);
        }
        durationHistogram.horizontalBarChart(durationBins, DashboardPalette.INFO, 5);

        Map<String, Long> toolCounts = new LinkedHashMap<>();
        for (ConversationSummary c : conversations) {
            for (String tool : c.toolsUsed()) {
                toolCounts.merge(tool, 1L, Long::sum);
            }
        }
        topToolsBar.horizontalBarChart(toolCounts, DashboardPalette.SUCCESS, 8);

        Map<String, Long> modelCounts = new LinkedHashMap<>();
        for (ConversationSummary c : conversations) {
            for (String m : c.modelsUsed()) {
                modelCounts.merge(m, 1L, Long::sum);
            }
        }
        modelsBar.horizontalBarChart(modelCounts, DashboardPalette.PRIMARY, 8);

        List<ConversationSummary> topByCost = new ArrayList<>(conversations);
        topByCost.sort(Comparator.comparing(ConversationSummary::cost).reversed());
        Map<String, Long> costMap = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(10, topByCost.size()); i++) {
            ConversationSummary c = topByCost.get(i);
            costMap.put(shorten(c.conversationId(), 12),
                    c.cost().setScale(6, RoundingMode.HALF_UP).movePointRight(6).longValueExact());
        }
        costPerConvBar.horizontalBarChart(costMap, DashboardPalette.WARN, 10);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ConversationSummary c : conversations) {
            statusCounts.merge(c.worstStatus(), 1L, Long::sum);
        }
        statusDonut.donutChart(statusCounts, ChartCanvas.DEFAULT_PALETTE);

        conversationsGrid.setItems(conversations);

        long totalToolCalls = conversations.stream().mapToInt(ConversationSummary::toolCalls).sum();
        long totalRagCalls = conversations.stream().mapToInt(ConversationSummary::ragCalls).sum();
        setStatus(conversations.size() + " conversations · "
                + (conversations.stream().mapToLong(ConversationSummary::messageCount).sum()) + " messages · "
                + totalToolCalls + " tool calls · " + totalRagCalls + " RAG retrievals · "
                + "window: " + window.minutes + "m");
    }

    private static String shorten(String s, int n) {
        if (s == null) return "—";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private String formatUsd(BigDecimal v) {
        if (v == null || v.signum() == 0) return "$0.00";
        if (v.compareTo(new BigDecimal("0.01")) < 0) {
            return String.format(Locale.ROOT, "$%.6f", v.doubleValue());
        }
        return String.format(Locale.ROOT, "$%.4f", v.doubleValue());
    }
}
