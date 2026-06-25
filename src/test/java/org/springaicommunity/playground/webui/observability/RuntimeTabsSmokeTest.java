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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.ollama.OllamaMetricsRingBuffer;
import org.springaicommunity.playground.observability.ollama.OllamaMetricsTimeSeries;
import org.springaicommunity.playground.observability.system.SystemMetricsRingBuffer;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsTimeSeries;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeTabsSmokeTest {

    private SystemMetricsSnapshot metrics() {
        return new SystemMetricsSnapshot(new SimpleMeterRegistry());
    }

    private SystemMetricsTimeSeries emptyTimeSeries() {
        return new SystemMetricsTimeSeries(new SystemMetricsRingBuffer(100));
    }

    private SafetyTab safetyTab() {
        return new SafetyTab(metrics(), new McpRiskEventRingBuffer());
    }

    @SuppressWarnings("unchecked")
    private OllamaRuntimeTab ollamaTab() {
        OllamaMonitorService monitor = new OllamaMonitorService(
                mock(ObjectProvider.class), new StandardEnvironment(), JsonMapper.builder().build());
        return new OllamaRuntimeTab(monitor,
                new OllamaMetricsTimeSeries(new OllamaMetricsRingBuffer(100)), metrics());
    }

    @Test
    void hostRuntimeTabPlacesAllKpisBeforeChartsAndCoversOsAndJvm() {
        HostRuntimeTab tab = new HostRuntimeTab(metrics(), emptyTimeSeries());
        tab.refresh(null);

        String html = tab.getElement().getOuterHTML();
        assertThat(html).contains("Heap used").contains("Process CPU").contains("System CPU")
                .contains("Threads (live)").contains("Uptime").contains("Classes loaded")
                .contains("Open file descriptors").contains("Disk free");
        assertThat(html).contains("JVM").contains("System").contains("Runtime");
        assertThat(html).contains("Load avg").contains("CPU cores").contains("App ready in")
                .contains("Process CPU time").contains("JIT compile time").contains("GC overhead")
                .contains("Total GC pause").contains("GC count (lifetime)").contains("Buffer pools");
        assertThat(html).contains("Heap usage over time").contains("CPU usage over time")
                .contains("Threads over time").contains("GC activity over time");
        assertThat(html).contains("Threads by state").contains("Heap pool usage")
                .contains("GC concurrent phase time").contains("Buffer pool used")
                .contains("Heap retention after GC");
        int lastKpi = html.lastIndexOf("Disk free");
        int firstChart = html.indexOf("Heap usage over time");
        assertThat(lastKpi).isLessThan(firstChart);
    }

    @Test
    void webApplicationTabCombinesHttpTomcatLogbackAndActiveLlm() {
        WebApplicationTab tab = new WebApplicationTab(metrics());
        tab.refresh(null);

        String html = tab.getElement().getOuterHTML();
        assertThat(html).contains("HTTP in-flight (server)").contains("HTTP in-flight (client)")
                .contains("Active LLM ops").contains("Active sessions")
                .contains("Error/Warn rate");
        assertThat(html).contains("Longest session alive")
                .contains("Sessions created (lifetime)").contains("Sessions expired")
                .contains("Sessions rejected").contains("HTTP requests (lifetime)")
                .contains("Logback events (lifetime)").contains("Error events").contains("Warn events");
        assertThat(html).contains("HTTP requests by status")
                .contains("Outbound HTTP latency by host")
                .contains("Logback events").contains("Active LLM operations");
    }

    @Test
    void safetyTabRendersRiskKpisAndCharts() {
        SafetyTab tab = safetyTab();
        tab.refresh(null);

        String html = tab.getElement().getOuterHTML();
        assertThat(html).contains("Risk signals").contains("Tamper rejects").contains("Poisoning hits")
                .contains("Floor overrides").contains("HITL approval rate").contains("Sandbox guard blocks");
        assertThat(html).contains("Risk signals by type").contains("Risk level distribution")
                .contains("HITL decisions").contains("Recent risk events");
    }

    @Test
    void ollamaRuntimeTabRendersKpisAndChartsWhenInactive() {
        OllamaRuntimeTab tab = ollamaTab();
        tab.refresh(null);

        String html = tab.getElement().getOuterHTML();
        assertThat(html).contains("Platform").contains("Ollama runtime").contains("Running models")
                .contains("VRAM in use").contains("Installed models").contains("Installed on disk");
        assertThat(html).contains("Reachability over time").contains("VRAM in use over time")
                .contains("Models loaded over time").contains("Loaded models");
    }

    @Test
    void allRuntimeDashboardsRefreshIdempotentlyWithNullWindow() {
        SystemMetricsSnapshot m = metrics();
        HostRuntimeTab host = new HostRuntimeTab(m, emptyTimeSeries());
        WebApplicationTab webApp = new WebApplicationTab(m);
        SafetyTab safety = safetyTab();
        OllamaRuntimeTab ollama = ollamaTab();

        for (int i = 0; i < 3; i++) {
            host.refresh(null);
            webApp.refresh(null);
            safety.refresh(null);
            ollama.refresh(null);
        }
        assertThat(host.getElement().getOuterHTML()).isNotEmpty();
        assertThat(webApp.getElement().getOuterHTML()).isNotEmpty();
        assertThat(safety.getElement().getOuterHTML()).isNotEmpty();
        assertThat(ollama.getElement().getOuterHTML()).isNotEmpty();
    }
}
