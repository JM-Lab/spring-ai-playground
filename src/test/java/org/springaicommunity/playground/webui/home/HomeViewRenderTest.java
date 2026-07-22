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
package org.springaicommunity.playground.webui.home;

import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class HomeViewRenderTest extends SpringBrowserlessTest {

    @Test
    void homeReportsToolMcpAndExposureReadinessWithRealCounts() {
        String panel = systemPanelText();

        assertThat(panel).contains("Built-in tools").contains("External MCP").contains("Exposed on MCP");
        assertThat(panel)
                .containsPattern("\\d+ Local-Passed · \\d+ need keys")
                .containsPattern("\\d+ connected")
                .containsPattern("\\d+ of \\d+");
    }

    @Test
    void homeReportsTheEnvironmentVariableTallyForKeyGatedTools() {
        assertThat(systemPanelText())
                .contains("Environment")
                .containsPattern("none required|all \\d+ set|\\d+ of \\d+ not set");
    }

    @Test
    void homeSurfacesTheUsageKpis() {
        assertThat(systemPanelText())
                .contains("Calls").contains("Tokens").contains("Tool calls").contains("p95 latency");
    }

    @Test
    void homeLinksOutToEveryObservabilityDashboard() {
        assertThat(systemPanelText()).containsPattern("Open all \\d+ dashboards");
    }

    private String systemPanelText() {
        HomeView view = navigate(HomeView.class);
        HomeSystemPanel systemPanel = $(HomeSystemPanel.class, view).first();
        return systemPanel.getElement().getTextRecursively();
    }

}
