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
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ChartCanvas;
import org.springaicommunity.playground.webui.observability.components.DashboardLayout;
import org.springaicommunity.playground.webui.observability.components.DashboardPalette;
import org.springaicommunity.playground.webui.observability.components.FormatUtils;
import org.springaicommunity.playground.webui.observability.components.KpiCard;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WebApplicationTab extends BaseDashboardTab {

    private final SystemMetricsSnapshot metricsService;

    private final KpiCard httpServerActiveCard = new KpiCard("HTTP in-flight (server)");
    private final KpiCard httpClientActiveCard = new KpiCard("HTTP in-flight (client)");
    private final KpiCard activeLlmOpsCard = new KpiCard("Active LLM ops");
    private final KpiCard sessionsActiveCard = new KpiCard("Active sessions");
    private final KpiCard longestSessionCard = new KpiCard("Longest session alive");
    private final KpiCard sessionsCreatedCard = new KpiCard("Sessions created (lifetime)");
    private final KpiCard sessionsExpiredCard = new KpiCard("Sessions expired");
    private final KpiCard sessionsRejectedCard = new KpiCard("Sessions rejected");
    private final KpiCard httpRequestsCard = new KpiCard("HTTP requests (lifetime)");
    private final KpiCard logbackTotalCard = new KpiCard("Logback events (lifetime)");
    private final KpiCard errorEventsCard = new KpiCard("Error events");
    private final KpiCard warnEventsCard = new KpiCard("Warn events");
    private final KpiCard errorRateCard = new KpiCard("Error/Warn rate");

    private final ChartCanvas httpStatusBar = new ChartCanvas();
    private final ChartCanvas httpClientByHostBar = new ChartCanvas();
    private final ChartCanvas logbackBar = new ChartCanvas();
    private final ChartCanvas activeOpsBar = new ChartCanvas();

    public WebApplicationTab(SystemMetricsSnapshot metricsService) {
        super();
        this.metricsService = metricsService;

        // Quality KPI (errorRate) leads, then active state, then session
        // lifetime, then lifetime counters. Grafana/Datadog convention: surface
        // the quality signal first, then operational state, then deep stats.
        Div kpiGrid = DashboardLayout.kpiGrid(errorRateCard, httpServerActiveCard, httpClientActiveCard,
                activeLlmOpsCard, sessionsActiveCard, longestSessionCard,
                sessionsCreatedCard, sessionsExpiredCard, sessionsRejectedCard,
                httpRequestsCard, logbackTotalCard, errorEventsCard, warnEventsCard);

        Div grid = DashboardLayout.chartGrid();
        grid.add(
                DashboardLayout.chartCard("HTTP requests by status",
                        "lifetime cumulative",
                        "http.server.requests Timer grouped by status code + outcome tag. Lifetime cumulative, not window-scoped.",
                        httpStatusBar),
                DashboardLayout.chartCard("Outbound HTTP latency by host",
                        "from http.client.requests — provider HTTP",
                        "http.client.requests Timer grouped by client.name (host). Includes outbound calls to LLM providers, MCP servers, etc.",
                        httpClientByHostBar),
                DashboardLayout.chartCard("Logback events",
                        "by level (lifetime)",
                        "logback.events counter grouped by level. ERROR / WARN should be near zero in healthy operation.",
                        logbackBar),
                DashboardLayout.chartCard("Active LLM operations",
                        "in-flight gauges",
                        "LongTaskTimer.activeTasks for ChatClient / ChatModel / Advisor / VectorStore — currently running operations.",
                        activeOpsBar)
        );

        content.add(kpiGrid, grid);
    }

    @Override
    public void refresh(Window ignored) {
        Snapshot s = metricsService.capture();

        httpServerActiveCard.setValue(String.valueOf(s.httpServerActive),
                "http.server.requests.active — LongTaskTimer of currently handling requests");
        httpClientActiveCard.setValue(String.valueOf(s.httpClientActive),
                "http.client.requests.active — outbound HTTP currently in flight");
        long activeTotal = s.activeChatClient + s.activeChatModel + s.activeAdvisor + s.activeVector;
        activeLlmOpsCard.setValue(String.valueOf(activeTotal),
                "Σ gen_ai.chat.client.operation.active + gen_ai.client.operation.active + "
                        + "spring.ai.advisor.active + db.vector.client.operation.active");
        sessionsActiveCard.setValue(String.valueOf(s.tomcatSessionsActive),
                s.tomcatSessionsActiveMax > 0
                        ? "peak " + s.tomcatSessionsActiveMax + " · tomcat.sessions.active.current"
                        : "tomcat.sessions.active.current");
        longestSessionCard.setValue(s.tomcatSessionsAliveMax <= 0 ? "—"
                : FormatUtils.formatDuration(Duration.ofSeconds(s.tomcatSessionsAliveMax)),
                "tomcat.sessions.alive.max — wall-clock age of the longest-running session");

        long totalStatus = s.httpRequestsByStatus.values().stream().mapToLong(Long::longValue).sum();
        long totalLogs = s.logbackEventsByLevel.values().stream().mapToLong(Long::longValue).sum();
        long errors = s.logbackEventsByLevel.getOrDefault("error", 0L);
        long warns = s.logbackEventsByLevel.getOrDefault("warn", 0L);
        double uptimeHours = Math.max(0.01, s.processUptimeSeconds / 3600.0);
        setLifetimeRateCard(httpRequestsCard, totalStatus, uptimeHours,
                "Σ http.server.requests.count");
        setLifetimeRateCard(logbackTotalCard, totalLogs, uptimeHours,
                "Σ logback.events across all levels");
        setLifetimeRateCard(errorEventsCard, errors, uptimeHours,
                "logback.events{level=error}");
        setLifetimeRateCard(warnEventsCard, warns, uptimeHours,
                "logback.events{level=warn}");
        setLifetimeRateCard(sessionsCreatedCard, s.tomcatSessionsCreated, uptimeHours,
                "tomcat.sessions.created");
        setLifetimeRateCard(sessionsExpiredCard, s.tomcatSessionsExpired, uptimeHours,
                "tomcat.sessions.expired");
        setLifetimeRateCard(sessionsRejectedCard, s.tomcatSessionsRejected, uptimeHours,
                "tomcat.sessions.rejected");
        if (totalLogs == 0) {
            errorRateCard.setValue("—");
        } else {
            double errPct = errors * 100.0 / totalLogs;
            double warnPct = warns * 100.0 / totalLogs;
            errorRateCard.setValue(String.format(Locale.ROOT, "%.2f%% err", errPct),
                    String.format(Locale.ROOT, "%.2f%% warn · %d / %d log events · logback.events",
                            warnPct, errors + warns, totalLogs));
        }

        httpStatusBar.horizontalBarChart(new LinkedHashMap<>(s.httpRequestsByStatus), DashboardPalette.INFO, 8);

        Map<String, Long> httpByHostCounts = new LinkedHashMap<>();
        s.httpClientByHost.forEach((host, hs) -> httpByHostCounts.put(host, hs.count));
        httpClientByHostBar.horizontalBarChart(httpByHostCounts, DashboardPalette.PRIMARY, 8);

        logbackBar.horizontalBarChart(new LinkedHashMap<>(s.logbackEventsByLevel), DashboardPalette.WARN, 6);

        Map<String, Long> active = new LinkedHashMap<>();
        active.put("ChatClient", s.activeChatClient);
        active.put("ChatModel", s.activeChatModel);
        active.put("Advisor", s.activeAdvisor);
        active.put("VectorStore", s.activeVector);
        activeOpsBar.horizontalBarChart(active, DashboardPalette.PRIMARY, 4);

        setStatus(s.httpServerActive + " in-flight server · " + s.httpClientActive
                + " in-flight client · " + totalStatus + " HTTP reqs · "
                + s.tomcatSessionsActive + " sessions · "
                + totalLogs + " log events (" + errors + " err, " + warns + " warn) · "
                + activeTotal + " active LLM ops");
    }

    private static void setLifetimeRateCard(KpiCard card, long count, double uptimeHours,
            String source) {
        if (count == 0) {
            card.setValue("0", source);
            return;
        }
        String main = FormatUtils.formatNumber(count);
        if (uptimeHours < 0.05) {
            card.setValue(main, source);
            return;
        }
        double perHour = count / uptimeHours;
        String rate = perHour < 1
                ? String.format(Locale.ROOT, "%.2f/h", perHour)
                : perHour < 100
                        ? String.format(Locale.ROOT, "%.1f/h", perHour)
                        : FormatUtils.formatNumber((long) perHour) + "/h";
        card.setValue(main, rate + " · " + source);
    }
}
