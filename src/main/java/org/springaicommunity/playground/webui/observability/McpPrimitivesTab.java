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
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.TimerSummary;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class McpPrimitivesTab extends BaseDashboardTab {

    private final SystemMetricsSnapshot systemMetrics;

    private final KpiCard totalCallsCard = new KpiCard("Primitive calls");
    private final KpiCard distinctKindsCard = new KpiCard("Distinct kinds");
    private final KpiCard avgLatencyCard = new KpiCard("Avg latency");
    private final KpiCard maxLatencyCard = new KpiCard("Max latency");
    private final KpiCard errorRateCard = new KpiCard("Error rate");
    private final KpiCard serverHandlersCard = new KpiCard("Sampling / Elicitation");

    private final ChartCanvas callsByKindBar = new ChartCanvas();
    private final ChartCanvas avgLatencyByKindBar = new ChartCanvas();
    private final ChartCanvas topServersBar = new ChartCanvas();
    private final ChartCanvas handlerOutcomesBar = new ChartCanvas();

    public McpPrimitivesTab(SystemMetricsSnapshot systemMetrics) {
        super();
        this.systemMetrics = systemMetrics;

        Div intro = buildIntro(
                "MCP primitives — protocol-level operations (resources, prompts, ping, sampling, " +
                "elicitation, …) as defined by the Model Context Protocol spec. These are not " +
                "tool calls — tool invocations live in MCP Servers (external) or Tool Studio (built-in).");

        Div cardRow = DashboardLayout.kpiGrid(totalCallsCard, distinctKindsCard,
                avgLatencyCard, maxLatencyCard, errorRateCard, serverHandlersCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("Calls by primitive",
                        "lifetime — resources/prompts/ping/roots/…",
                        "Timer mcp.primitive grouped by kind tag. Kinds: resources.list, resources.read, " +
                                "prompts.list, prompts.get, tools.list, tools.call, ping, capabilities, " +
                                "logging.set_level, roots.add, roots.remove. Self-loopback excluded.",
                        callsByKindBar),
                DashboardLayout.chartCard("Avg latency by primitive",
                        "ms — top 8",
                        "Mean = totalTime / count per kind, derived from the same mcp.primitive Timer.",
                        avgLatencyByKindBar),
                DashboardLayout.chartCard("Top servers",
                        "by primitive call count",
                        "Server tag on mcp.primitive Timer (grouped). Lets you see which MCP servers " +
                                "you actually interact with through the Inspector.",
                        topServersBar),
                DashboardLayout.chartCard("Server-initiated handlers",
                        "sampling / elicitation outcome lifetime",
                        "Counter mcp.server.handler — when the MCP server asks the client to sample " +
                                "or elicit (LLM completion / user input), records outcome: success / " +
                                "timeout / cancel / reject / error.",
                        handlerOutcomesBar)
        );

        content.add(intro, cardRow, grid);
    }

    @Override
    public void refresh(Window window) {
        Snapshot snap = systemMetrics.capture();

        long total = snap.mcpPrimitiveCountByKind.values().stream().mapToLong(Long::longValue).sum();
        long err = snap.mcpPrimitiveOutcomeCounts.getOrDefault("error", 0L);
        long handlerTotal = snap.mcpServerHandlerCounts.values().stream().mapToLong(Long::longValue).sum();
        double overallAvgMs = avgLatencyAcross(snap);
        double overallMaxMs = maxLatencyAcross(snap);

        totalCallsCard.setValue(String.valueOf(total),
                "Σ mcp.primitive timer count across all kinds (lifetime, self-loopback excluded)");
        distinctKindsCard.setValue(String.valueOf(snap.mcpPrimitiveCountByKind.size()),
                "Distinct mcp.primitive 'kind' tags seen — resources.list, tools.call, ping, …");
        avgLatencyCard.setValue(total == 0 ? "—" : FormatUtils.formatLatencyMs(overallAvgMs),
                "Σ totalTime / Σ count across all mcp.primitive timers");
        maxLatencyCard.setValue(total == 0 ? "—" : FormatUtils.formatLatencyMs(overallMaxMs),
                "Largest max() across all mcp.primitive timer kinds");
        if (total == 0) {
            errorRateCard.setValue("0.0%", "no MCP primitive calls in lifetime");
        } else {
            errorRateCard.setValue(
                    String.format(Locale.ROOT, "%.1f%%", err * 100.0 / total),
                    err + " errors of " + total + " primitive calls · mcp.primitive outcome tag");
        }
        serverHandlersCard.setValue(String.valueOf(handlerTotal),
                "Σ mcp.server.handler counter across all kinds (sampling + elicitation) and outcomes");

        callsByKindBar.horizontalBarChart(snap.mcpPrimitiveCountByKind, DashboardPalette.PRIMARY, 8);

        Map<String, Long> avgByKind = new LinkedHashMap<>();
        for (Map.Entry<String, TimerSummary> e : snap.mcpPrimitiveTimerByKind.entrySet()) {
            avgByKind.put(e.getKey(), (long) e.getValue().meanMs);
        }
        avgLatencyByKindBar.horizontalBarChart(avgByKind, DashboardPalette.INFO, 8, " ms");

        topServersBar.horizontalBarChart(snap.mcpPrimitiveCountByServer, DashboardPalette.SUCCESS, 8);
        handlerOutcomesBar.horizontalBarChart(snap.mcpServerHandlerCounts, DashboardPalette.WARN, 8);

        setStatus(total + " primitive calls · " + snap.mcpPrimitiveCountByKind.size() + " kinds · "
                + snap.mcpPrimitiveCountByServer.size() + " servers · "
                + handlerTotal + " server-handler events");
    }

    private static double avgLatencyAcross(Snapshot snap) {
        double totalMs = 0;
        long totalCount = 0;
        for (TimerSummary t : snap.mcpPrimitiveTimerByKind.values()) {
            totalMs += t.totalMs;
            totalCount += t.count;
        }
        return totalCount == 0 ? 0 : totalMs / totalCount;
    }

    private static double maxLatencyAcross(Snapshot snap) {
        double max = 0;
        for (TimerSummary t : snap.mcpPrimitiveTimerByKind.values()) {
            if (t.maxMs > max) max = t.maxMs;
        }
        return max;
    }
}
