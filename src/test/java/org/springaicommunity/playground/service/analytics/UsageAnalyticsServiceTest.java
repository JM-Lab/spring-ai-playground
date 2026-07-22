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
package org.springaicommunity.playground.service.analytics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.service.analytics.UsageAnalyticsService.DailyUsage;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springaicommunity.playground.service.mcp.client.McpClientService.ServerStatus;
import org.springaicommunity.playground.service.mcp.client.McpClientService.StatusEntry;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.mcp.risk.DefaultIntegrityVerifier;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UsageAnalyticsServiceTest {

    private ToolSpecService toolSpecService;
    private DefaultIntegrityVerifier integrityVerifier;
    private ChatSystemPromptPresetCatalog presetCatalog;
    private McpCatalogService mcpCatalogService;
    private McpServerInfoService mcpServerInfoService;
    private McpClientService mcpClientService;
    private ObservabilityRingBuffer ringBuffer;
    private UsageAnalyticsService service;

    @BeforeEach
    void setUp() {
        this.toolSpecService = mock(ToolSpecService.class);
        this.integrityVerifier = mock(DefaultIntegrityVerifier.class);
        this.presetCatalog = mock(ChatSystemPromptPresetCatalog.class);
        this.mcpCatalogService = mock(McpCatalogService.class);
        this.mcpServerInfoService = mock(McpServerInfoService.class);
        this.mcpClientService = mock(McpClientService.class);
        this.ringBuffer = mock(ObservabilityRingBuffer.class);
        this.service = new UsageAnalyticsService(providerOf(this.toolSpecService), this.integrityVerifier,
                this.presetCatalog, this.mcpCatalogService, providerOf(this.mcpServerInfoService),
                providerOf(this.mcpClientService), emptyProvider(), emptyProvider(), emptyProvider(),
                emptyProvider(), emptyProvider(), providerOf(this.ringBuffer), emptyProvider(), emptyProvider(),
                new SpringAiPlaygroundOptions(null, false, null, null, null, null));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        when(provider.getObject()).thenReturn(instance);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> emptyProvider() {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenThrow(new IllegalStateException("unavailable"));
        return provider;
    }

    @Test
    void builtinToolKeepsRealName() {
        when(this.integrityVerifier.isReservedToolName("renderChart")).thenReturn(true);
        when(this.toolSpecService.getToolSpecAsOpt("renderChart")).thenReturn(Optional.empty());
        when(this.toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of());

        Map<String, Object> params = this.service.toolCalledParams("renderChart");

        assertEquals("renderChart", params.get("tool_name"));
        assertEquals("builtin", params.get("source"));
    }

    @Test
    void authoredToolNameIsMasked() {
        when(this.integrityVerifier.isReservedToolName("mySecretTool")).thenReturn(false);
        when(this.toolSpecService.getToolSpecAsOpt("mySecretTool")).thenReturn(Optional.of(mock(ToolSpec.class)));
        when(this.toolSpecService.riskLevelOf(any())).thenReturn("L2");
        when(this.toolSpecService.requiresApproval("mySecretTool")).thenReturn(true);

        Map<String, Object> params = this.service.toolCalledParams("mySecretTool");

        assertEquals("authored", params.get("tool_name"));
        assertEquals("authored", params.get("source"));
        assertEquals("L2", params.get("risk_level"));
        assertEquals(true, params.get("hitl"));
    }

    @Test
    void composedAndExternalToolNamesAreMasked() {
        when(this.toolSpecService.getToolSpecAsOpt(any())).thenReturn(Optional.empty());
        when(this.toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("wrappedTool"));

        assertEquals("composed", this.service.toolCalledParams("wrappedTool").get("source"));
        assertEquals("external", this.service.toolCalledParams("upstreamOnlyTool").get("source"));
        assertEquals("external", this.service.toolCalledParams("upstreamOnlyTool").get("tool_name"));
    }

    @Test
    void presetLabelIsCatalogIdOrCustom() {
        Preset catalogPreset = new Preset("data-visualizer", "Data Visualizer", "", "p", null, null, null);
        Preset userPreset = new Preset("my-preset", "Mine", "", "p", null, null, null);
        when(this.presetCatalog.findById("data-visualizer")).thenReturn(Optional.of(catalogPreset));
        when(this.presetCatalog.findById("my-preset")).thenReturn(Optional.empty());

        assertEquals("data-visualizer", this.service.presetLabel(catalogPreset));
        assertEquals("custom", this.service.presetLabel(userPreset));
        assertNull(this.service.presetLabel(null));
    }

    @Test
    void mcpServerParamsMaskNonCatalogServers() {
        when(this.mcpCatalogService.findByServerName("my-private-server")).thenReturn(Optional.empty());
        McpServerInfo custom = new McpServerInfo(McpTransportType.STDIO, "my-private-server", "", 0L, 0L,
                "{\"command\":\"npx\"}");

        Map<String, Object> params = this.service.mcpServerAddedParams(custom);

        assertEquals("STDIO", params.get("transport"));
        assertEquals("custom", params.get("catalog_id"));
        assertEquals(false, params.get("oauth"));
        assertFalse(params.containsValue("my-private-server"));
    }

    @Test
    void mcpServerParamsUseCatalogIdAndDetectOauth() {
        McpCatalogEntry entry = mock(McpCatalogEntry.class);
        when(entry.id()).thenReturn("github");
        when(this.mcpCatalogService.findByServerName("github")).thenReturn(Optional.of(entry));
        McpServerInfo catalog = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "github", "", 0L, 0L,
                "{\"url\":\"https://api.example.com/mcp\",\"oauth\":{\"scope\":\"read\"}}");

        Map<String, Object> params = this.service.mcpServerAddedParams(catalog);

        assertEquals("github", params.get("catalog_id"));
        assertEquals(true, params.get("oauth"));
    }

    @Test
    void documentParamsCarryExtensionOnly() {
        Map<String, Object> params = this.service.documentIndexedParams("Q3 Financial Report.PDF", 42);
        assertEquals("pdf", params.get("doc_type"));
        assertEquals(42, params.get("chunk_count"));
        assertFalse(params.containsValue("Q3 Financial Report.PDF"));

        assertEquals("unknown", this.service.documentIndexedParams("noextension", 1).get("doc_type"));
        assertEquals("unknown", this.service.documentIndexedParams(null, 1).get("doc_type"));
    }

    @Test
    void dailyUsageFiresOncePerDay() {
        assertTrue(this.service.dailyUsage().isPresent());
        assertTrue(this.service.dailyUsage().isEmpty());
    }

    @Test
    void dailySnapshotIncludesOllamaAndSafetyDerivedFields() {
        OllamaMonitorService ollama = mock(OllamaMonitorService.class);
        when(ollama.cachedSnapshot()).thenReturn(new OllamaMonitorService.OllamaStatus(true, true, "0.9.0",
                List.of(new OllamaMonitorService.InstalledModel("qwen3.5:9b", 5_000_000_000L, "qwen", "9B",
                        "Q4_K_M", 32768, List.of())),
                List.of()));
        UsageAnalyticsService snapshotService = new UsageAnalyticsService(providerOf(this.toolSpecService),
                this.integrityVerifier, this.presetCatalog, this.mcpCatalogService, emptyProvider(),
                emptyProvider(), providerOf(ollama), emptyProvider(), emptyProvider(), emptyProvider(),
                emptyProvider(), emptyProvider(), providerOf(new SystemMetricsSnapshot(new SimpleMeterRegistry())),
                emptyProvider(), new SpringAiPlaygroundOptions(null, false, null, null, null, null));

        Map<String, Object> params = snapshotService.dailyUsage().orElseThrow().snapshot();

        assertEquals(1, params.get("installed_models"));
        assertEquals("Q4_K_M", params.get("top_quantization"));
        assertTrue(params.containsKey("ram_gb"));
        assertEquals(0L, params.get("hitl_approved"));
        assertEquals(0L, params.get("risk_signals"));
    }

    @Test
    void toolCalledParamsResolveCatalogIdForMcpTools() {
        when(this.toolSpecService.getToolSpecAsOpt(any())).thenReturn(Optional.empty());
        when(this.toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of());
        when(this.mcpClientService.lookupToolSource("create_issue"))
                .thenReturn(Optional.of(new McpToolSource("github", "STREAMABLE_HTTP", "L3")));
        when(this.mcpClientService.lookupToolSource("private_tool"))
                .thenReturn(Optional.of(new McpToolSource("my-private-server", "STDIO", null)));
        when(this.mcpClientService.lookupToolSource("renderChart")).thenReturn(Optional.empty());
        McpCatalogEntry entry = mock(McpCatalogEntry.class);
        when(entry.id()).thenReturn("github");
        when(this.mcpCatalogService.findByServerName("github")).thenReturn(Optional.of(entry));
        when(this.mcpCatalogService.findByServerName("my-private-server")).thenReturn(Optional.empty());

        Map<String, Object> params = this.service.toolCalledParams("create_issue");

        assertEquals("external", params.get("tool_name"));
        assertEquals("github", params.get("catalog_id"));
        assertFalse(params.containsValue("create_issue"));
        assertEquals("custom", this.service.toolCalledParams("private_tool").get("catalog_id"));
        assertFalse(this.service.toolCalledParams("renderChart").containsKey("catalog_id"));
    }

    @Test
    void dailyUsageReportsMcpServerInventoryWithoutDefaultServer() {
        McpServerInfo defaultServer = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "spring-ai-playground",
                "", 0L, 0L, null);
        McpServerInfo github = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "github", "", 0L, 0L,
                "{\"url\":\"https://api.example.com/mcp\",\"oauth\":{\"scope\":\"read\"}}");
        McpServerInfo custom = new McpServerInfo(McpTransportType.STDIO, "my-private-server", "", 0L, 0L,
                "{\"command\":\"npx\"}");
        when(this.mcpServerInfoService.getDefaultMcpServerInfo()).thenReturn(defaultServer);
        when(this.mcpServerInfoService.read()).thenReturn(List.of(defaultServer, github, custom));
        when(this.mcpClientService.getStatus(github)).thenReturn(new StatusEntry(ServerStatus.OK, null, null, null));
        when(this.mcpClientService.getStatus(custom)).thenReturn(StatusEntry.OFFLINE);
        McpCatalogEntry entry = mock(McpCatalogEntry.class);
        when(entry.id()).thenReturn("github");
        when(this.mcpCatalogService.findByServerName("github")).thenReturn(Optional.of(entry));
        when(this.mcpCatalogService.findByServerName("my-private-server")).thenReturn(Optional.empty());

        List<Map<String, Object>> servers = this.service.dailyUsage().orElseThrow().mcpServers();

        assertEquals(2, servers.size());
        assertEquals("github", servers.get(0).get("catalog_id"));
        assertEquals(true, servers.get(0).get("connected"));
        assertEquals(true, servers.get(0).get("oauth"));
        assertEquals("custom", servers.get(1).get("catalog_id"));
        assertEquals(false, servers.get(1).get("connected"));
        assertFalse(servers.get(1).containsValue("my-private-server"));
    }

    @Test
    void dailyUsageRollsUpModelStatsFromRecentTraces() {
        long now = System.currentTimeMillis();
        when(this.ringBuffer.snapshot()).thenReturn(List.of(
                trace("qwen3:8b", "ollama", now - 1_000, 100, TraceRecord.STATUS_OK, 200L),
                trace("qwen3:8b", "ollama", now - 2_000, 300, TraceRecord.STATUS_ERROR, 100L),
                trace("llama3:8b", "ollama", now - 3_000, 50, TraceRecord.STATUS_OK, 50L),
                trace("stale-model", "ollama", now - 25L * 3_600_000, 50, TraceRecord.STATUS_OK, 50L)));

        DailyUsage daily = this.service.dailyUsage().orElseThrow();
        List<Map<String, Object>> models = daily.models();

        assertEquals(2, models.size());
        Map<String, Object> top = models.get(0);
        assertEquals("qwen3:8b", top.get("model"));
        assertEquals("ollama", top.get("provider"));
        assertEquals(2L, top.get("calls"));
        assertEquals(300L, top.get("tokens"));
        assertEquals(1L, top.get("errors"));
        assertEquals(290L, top.get("p95_ms"));
        assertEquals("llama3:8b", models.get(1).get("model"));
    }

    private static TraceRecord trace(String model, String provider, long startEpochMs, long durationMs, String status,
            Long totalTokens) {
        return new TraceRecord("trace-" + startEpochMs, null, null, provider, model, startEpochMs, durationMs,
                status, null, null, totalTokens, null, false, 0, false, null, null, Set.of(), Set.of(), List.of(),
                Map.of());
    }

    @Test
    void userPropertiesSurviveUnavailableServices() {
        Map<String, Object> properties = this.service.userProperties(true);
        assertEquals("desktop", properties.get("app_surface"));
        assertTrue(properties.containsKey("app_version"));
        assertFalse(properties.containsKey("platform"));
    }
}
