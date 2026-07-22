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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.SpanRecord;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.webui.observability.ObservabilityDashboardSidebar.DashboardEntry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ObservabilityDataSweepTest extends SpringBrowserlessTest {

    private static final List<DashboardEntry> ALL_DASHBOARDS = List.of(
            new DashboardEntry("overview", "Overview", VaadinIcon.DASHBOARD),
            new DashboardEntry("agentic-chat", "Agentic Chat", VaadinIcon.CHAT),
            new DashboardEntry("tokens", "Tokens & Cost", VaadinIcon.MONEY),
            new DashboardEntry("llm", "AI Models", VaadinIcon.CUBES),
            new DashboardEntry("tools", "Tool Studio", VaadinIcon.TOOLS),
            new DashboardEntry("mcp", "MCP Servers", VaadinIcon.TOOLBOX),
            new DashboardEntry("mcp-primitives", "MCP Inspector", VaadinIcon.SEARCH),
            new DashboardEntry("safety", "Safety", VaadinIcon.SHIELD),
            new DashboardEntry("vector", "Vector Database", VaadinIcon.SEARCH_PLUS),
            new DashboardEntry("host", "Host", VaadinIcon.SERVER),
            new DashboardEntry("ollama", "Ollama", VaadinIcon.HARDDRIVE),
            new DashboardEntry("web-application", "Web Application", VaadinIcon.GLOBE_WIRE),
            new DashboardEntry("logs", "Logs", VaadinIcon.FILE_TEXT_O),
            new DashboardEntry("traces", "Traces", VaadinIcon.SITEMAP));

    @Autowired
    private ObservabilityRingBuffer buffer;

    @Autowired
    private McpRiskEventRingBuffer riskEventBuffer;

    @Test
    void dashboardsRenderSeededTrafficAndTraceDetailOpens() {
        seedTraffic();

        ObservabilityView view = navigate(ObservabilityView.class);
        ObservabilityDashboardSidebar sidebar = $(ObservabilityDashboardSidebar.class, view).first();
        for (DashboardEntry entry : ALL_DASHBOARDS) {
            sidebar.select(entry);
            roundTrip();
        }

        sidebar.select(new DashboardEntry("traces", "Traces", VaadinIcon.SITEMAP));
        roundTrip();
        Div traceCard = $(Div.class, view)
                .withCondition(div -> "pointer".equals(div.getStyle().get("cursor"))
                        && div.getElement().getTextRecursively().contains("qwen-seed"))
                .first();
        ComponentUtil.fireEvent(traceCard, new ClickEvent<>(traceCard));
        roundTrip();

        assertThat($(TraceDetailDialog.class).all()).isNotEmpty();
        assertThat($(TraceDetailDialog.class).first().getElement().getTextRecursively())
                .contains("qwen-seed:1b").contains("getWeather").contains("weather-server");
    }

    @Test
    void seededTracesAndRiskEventsSurfaceOnTheirOwnDashboards() {
        seedTraffic();

        ObservabilityView view = navigate(ObservabilityView.class);
        ObservabilityDashboardSidebar sidebar = $(ObservabilityDashboardSidebar.class, view).first();

        sidebar.select(new DashboardEntry("traces", "Traces", VaadinIcon.SITEMAP));
        roundTrip();
        assertThat($(TracesTab.class, view).first().getElement().getTextRecursively())
                .contains("qwen-seed:1b").contains("conv-seed");

        sidebar.select(new DashboardEntry("safety", "Safety", VaadinIcon.SHIELD));
        roundTrip();
        assertThat($(SafetyTab.class, view).first().getElement().getTextRecursively())
                .contains("tool-poisoning").contains("seeded risk event");
    }

    private void seedTraffic() {
        long now = System.currentTimeMillis();
        this.buffer.add(chatTrace("seed-trace-ok", now - 60_000L, "OK", "stop"));
        this.buffer.add(chatTrace("seed-trace-error", now - 30_000L, "ERROR", "error"));
        this.riskEventBuffer.record(new McpRiskEventRingBuffer.RiskEvent(now - 10_000L,
                "tool-poisoning", "HIGH", "seeded risk event"));
    }

    private static TraceRecord chatTrace(String traceId, long startEpochMs, String status, String finishReason) {
        List<SpanRecord> spans = List.of(
                new SpanRecord("root-" + traceId, null, "spring.ai.chat.client", startEpochMs, 1200L, status,
                        Map.of("conversation.id", "conv-seed")),
                new SpanRecord("model-" + traceId, "root-" + traceId, "gen_ai.client.operation",
                        startEpochMs + 5L, 900L, status,
                        Map.of("gen_ai.system", "ollama",
                                "gen_ai.request.model", "qwen-seed:1b",
                                "gen_ai.usage.input_tokens", "120",
                                "gen_ai.usage.output_tokens", "340",
                                "gen_ai.response.finish_reasons", finishReason)),
                new SpanRecord("tool-" + traceId, "root-" + traceId, "spring.ai.tool",
                        startEpochMs + 20L, 50L, status,
                        Map.of("spring.ai.tool.definition.name", "getWeather",
                                "saip.mcp.server", "weather-server")),
                new SpanRecord("vector-" + traceId, "root-" + traceId, "db.vector.client.operation",
                        startEpochMs + 40L, 30L, status, Map.of()));
        return new TraceRecord(traceId, "conv-seed", "msg-" + traceId, "ollama", "qwen-seed:1b",
                startEpochMs, 1234L, status, 120L, 340L, 460L, finishReason, true, 1, true,
                "user-seed", "session-seed", Set.of("getWeather"), Set.of("weather-server"), spans, Map.of());
    }

}
