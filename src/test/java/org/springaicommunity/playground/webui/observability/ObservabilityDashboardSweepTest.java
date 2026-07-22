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
import com.vaadin.flow.component.icon.VaadinIcon;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.webui.observability.ObservabilityDashboardSidebar.DashboardEntry;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ObservabilityDashboardSweepTest extends SpringBrowserlessTest {

    private static final List<Dashboard> ALL_DASHBOARDS = List.of(
            new Dashboard("overview", "Overview", VaadinIcon.DASHBOARD, OverviewTab.class),
            new Dashboard("agentic-chat", "Agentic Chat", VaadinIcon.CHAT, AgenticChatTab.class),
            new Dashboard("tokens", "Tokens & Cost", VaadinIcon.MONEY, TokensAndCostTab.class),
            new Dashboard("llm", "AI Models", VaadinIcon.CUBES, LlmTab.class),
            new Dashboard("tools", "Tool Studio", VaadinIcon.TOOLS, ToolsTab.class),
            new Dashboard("mcp", "MCP Servers", VaadinIcon.TOOLBOX, McpTab.class),
            new Dashboard("mcp-primitives", "MCP Inspector", VaadinIcon.SEARCH, McpPrimitivesTab.class),
            new Dashboard("safety", "Safety", VaadinIcon.SHIELD, SafetyTab.class),
            new Dashboard("vector", "Vector Database", VaadinIcon.SEARCH_PLUS, VectorTab.class),
            new Dashboard("host", "Host", VaadinIcon.SERVER, HostRuntimeTab.class),
            new Dashboard("ollama", "Ollama", VaadinIcon.HARDDRIVE, OllamaRuntimeTab.class),
            new Dashboard("web-application", "Web Application", VaadinIcon.GLOBE_WIRE, WebApplicationTab.class),
            new Dashboard("logs", "Logs", VaadinIcon.FILE_TEXT_O, LogsTab.class),
            new Dashboard("traces", "Traces", VaadinIcon.SITEMAP, TracesTab.class));

    record Dashboard(String slug, String label, VaadinIcon icon, Class<? extends BaseDashboardTab> tab) {
        DashboardEntry entry() {
            return new DashboardEntry(this.slug, this.label, this.icon);
        }
    }

    @Test
    void selectingEveryDashboardEntryRendersThatDashboardAloneAndNotAnEmptyShell() {
        ObservabilityView view = navigate(ObservabilityView.class);
        ObservabilityDashboardSidebar sidebar = $(ObservabilityDashboardSidebar.class, view).first();

        for (Dashboard dashboard : ALL_DASHBOARDS) {
            sidebar.select(dashboard.entry());
            roundTrip();

            assertThat($(BaseDashboardTab.class, view).all())
                    .as("dashboard %s", dashboard.slug())
                    .singleElement()
                    .isInstanceOf(dashboard.tab())
                    .extracting(tab -> tab.getElement().getTextRecursively().trim())
                    .asString().isNotEmpty();
        }
    }

}
