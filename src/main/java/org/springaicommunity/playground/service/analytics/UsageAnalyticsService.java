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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.TraceRecord;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatProvider;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.ServerStatus;
import org.springaicommunity.playground.service.mcp.risk.DefaultIntegrityVerifier;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UsageAnalyticsService {

    private static final Logger logger = LoggerFactory.getLogger(UsageAnalyticsService.class);
    private static final long GIB = 1024L * 1024 * 1024;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_DAILY_MCP_SERVERS = 20;
    private static final int MAX_DAILY_MODELS = 10;

    private final ObjectProvider<ToolSpecService> toolSpecServiceProvider;
    private final DefaultIntegrityVerifier integrityVerifier;
    private final ChatSystemPromptPresetCatalog presetCatalog;
    private final McpCatalogService mcpCatalogService;
    private final ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider;
    private final ObjectProvider<McpClientService> mcpClientServiceProvider;
    private final ObjectProvider<OllamaMonitorService> ollamaMonitorServiceProvider;
    private final ObjectProvider<VectorStoreService> vectorStoreServiceProvider;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<BuildProperties> buildPropertiesProvider;
    private final ObjectProvider<ObservabilityTimeSeries> timeSeriesProvider;
    private final ObjectProvider<ObservabilityRingBuffer> ringBufferProvider;
    private final ObjectProvider<SystemMetricsSnapshot> systemMetricsSnapshotProvider;
    private final ObjectProvider<ChatHistoryService> chatHistoryServiceProvider;
    private final SpringAiPlaygroundOptions options;
    private final AtomicReference<LocalDate> lastSnapshotDate = new AtomicReference<>();

    public UsageAnalyticsService(ObjectProvider<ToolSpecService> toolSpecServiceProvider,
            DefaultIntegrityVerifier integrityVerifier, ChatSystemPromptPresetCatalog presetCatalog,
            McpCatalogService mcpCatalogService, ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider,
            ObjectProvider<McpClientService> mcpClientServiceProvider,
            ObjectProvider<OllamaMonitorService> ollamaMonitorServiceProvider,
            ObjectProvider<VectorStoreService> vectorStoreServiceProvider, ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<BuildProperties> buildPropertiesProvider,
            ObjectProvider<ObservabilityTimeSeries> timeSeriesProvider,
            ObjectProvider<ObservabilityRingBuffer> ringBufferProvider,
            ObjectProvider<SystemMetricsSnapshot> systemMetricsSnapshotProvider,
            ObjectProvider<ChatHistoryService> chatHistoryServiceProvider, SpringAiPlaygroundOptions options) {
        this.toolSpecServiceProvider = toolSpecServiceProvider;
        this.integrityVerifier = integrityVerifier;
        this.presetCatalog = presetCatalog;
        this.mcpCatalogService = mcpCatalogService;
        this.mcpServerInfoServiceProvider = mcpServerInfoServiceProvider;
        this.mcpClientServiceProvider = mcpClientServiceProvider;
        this.ollamaMonitorServiceProvider = ollamaMonitorServiceProvider;
        this.vectorStoreServiceProvider = vectorStoreServiceProvider;
        this.chatModelProvider = chatModelProvider;
        this.buildPropertiesProvider = buildPropertiesProvider;
        this.timeSeriesProvider = timeSeriesProvider;
        this.ringBufferProvider = ringBufferProvider;
        this.systemMetricsSnapshotProvider = systemMetricsSnapshotProvider;
        this.chatHistoryServiceProvider = chatHistoryServiceProvider;
        this.options = options;
    }

    public Map<String, Object> userProperties(boolean desktopSurface) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("app_version", appVersion());
        properties.put("app_surface", desktopSurface ? "desktop" : "web");
        safely("platform", () -> ollamaMonitorServiceProvider.getObject().platform().label(), properties);
        safely("chat_provider", () -> ChatProvider.from(chatModelProvider.getObject()).name()
                .toLowerCase(Locale.ROOT), properties);
        safely("embedding_provider", () -> vectorStoreServiceProvider.getObject().getEmbeddingModelServiceName()
                .toLowerCase(Locale.ROOT), properties);
        safely("embedding_model", () -> vectorStoreServiceProvider.getObject().getEmbeddingOptions().getModel(),
                properties);
        safely("tool_search_index", this::toolSearchIndexLabel, properties);
        return properties;
    }

    public Map<String, Object> toolCalledParams(String toolName) {
        Map<String, Object> params = new LinkedHashMap<>();
        ToolSpecService toolSpecService = toolSpecServiceProvider.getIfAvailable();
        String source = toolSource(toolName, toolSpecService);
        params.put("tool_name", "builtin".equals(source) ? toolName : source);
        params.put("source", source);
        if (toolSpecService != null) {
            toolSpecService.getToolSpecAsOpt(toolName)
                    .ifPresent(spec -> params.put("risk_level", toolSpecService.riskLevelOf(spec)));
            params.put("hitl", toolSpecService.requiresApproval(toolName));
        }
        safely("catalog_id", () -> mcpClientServiceProvider.getObject().lookupToolSource(toolName)
                .map(origin -> catalogIdOf(origin.serverName())).orElse(null), params);
        return params;
    }

    public String presetLabel(Preset preset) {
        if (preset == null) return null;
        return presetCatalog.findById(preset.id()).isPresent() ? preset.id() : "custom";
    }

    public Map<String, Object> mcpServerAddedParams(McpServerInfo serverInfo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("transport", serverInfo.mcpTransportType().name());
        params.put("catalog_id", catalogIdOf(serverInfo.serverName()));
        String connectionJson = serverInfo.connectionAsJson();
        params.put("oauth", connectionJson != null && connectionJson.contains("\"oauth\""));
        return params;
    }

    private String catalogIdOf(String serverName) {
        return mcpCatalogService.findByServerName(serverName).map(McpCatalogEntry::id).orElse("custom");
    }

    public Map<String, Object> documentIndexedParams(String documentFileName, int chunkCount) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("doc_type", fileExtension(documentFileName));
        params.put("chunk_count", chunkCount);
        return params;
    }

    public Optional<DailyUsage> dailyUsage() {
        LocalDate today = LocalDate.now();
        if (today.equals(lastSnapshotDate.getAndSet(today))) return Optional.empty();
        return Optional.of(new DailyUsage(snapshotParams(), activeMcpServerParams(), modelUsageParams()));
    }

    private Map<String, Object> snapshotParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        safely("recent_calls", () -> recentSeries().totalCalls(), params);
        safely("recent_tokens", () -> recentSeries().totalTokens(), params);
        safely("recent_p95_ms", () -> Math.round(recentSeries().overallP95LatencyMs()), params);
        safely("recent_errors", () -> recentSeries().statusCounts().getOrDefault("ERROR", 0L), params);
        safely("conversations", () -> chatHistoryServiceProvider.getObject().getChatHistoryList().size(), params);
        safely("installed_models", () -> ollamaStatus().installedModels().size(), params);
        safely("vram_gb", () -> ollamaStatus().totalVramBytes() / GIB, params);
        safely("top_quantization", this::topQuantization, params);
        SystemMetricsSnapshot.Snapshot snapshot = captureSystemSnapshot();
        if (snapshot != null) {
            params.put("ram_gb", snapshot.systemPhysicalMemoryTotalBytes / GIB);
            params.put("hitl_approved", snapshot.mcpHitlByOutcome.getOrDefault("approved", 0L));
            params.put("hitl_declined", snapshot.mcpHitlByOutcome.getOrDefault("declined", 0L)
                    + snapshot.mcpHitlByOutcome.getOrDefault("denied", 0L));
            params.put("risk_signals", sum(snapshot.mcpRiskSignalByType));
            params.put("sandbox_blocked", sum(snapshot.sandboxGuardBlockedByCategory));
        }
        return params;
    }

    private List<Map<String, Object>> activeMcpServerParams() {
        try {
            McpServerInfoService serverInfoService = mcpServerInfoServiceProvider.getObject();
            McpClientService clientService = mcpClientServiceProvider.getObject();
            McpServerInfo defaultServer = serverInfoService.getDefaultMcpServerInfo();
            String defaultServerName = defaultServer == null ? null : defaultServer.serverName();
            return serverInfoService.read().stream()
                    .filter(serverInfo -> !Objects.equals(serverInfo.serverName(), defaultServerName))
                    .limit(MAX_DAILY_MCP_SERVERS)
                    .map(serverInfo -> {
                        Map<String, Object> params = mcpServerAddedParams(serverInfo);
                        params.put("connected", clientService.getStatus(serverInfo).status() == ServerStatus.OK);
                        return params;
                    })
                    .toList();
        } catch (RuntimeException e) {
            logger.debug("usage analytics: skipping mcp inventory: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> modelUsageParams() {
        try {
            long since = System.currentTimeMillis() - DAY_MS;
            return ringBufferProvider.getObject().snapshot().stream()
                    .filter(trace -> trace.startEpochMs() >= since)
                    .filter(trace -> trace.model() != null && !trace.model().isBlank())
                    .collect(Collectors.groupingBy(TraceRecord::model)).entrySet().stream()
                    .sorted(Comparator.comparingInt(
                            (Map.Entry<String, List<TraceRecord>> entry) -> -entry.getValue().size()))
                    .limit(MAX_DAILY_MODELS)
                    .map(entry -> modelUsageEntry(entry.getKey(), entry.getValue()))
                    .toList();
        } catch (RuntimeException e) {
            logger.debug("usage analytics: skipping model usage: {}", e.getMessage());
            return List.of();
        }
    }

    private static Map<String, Object> modelUsageEntry(String model, List<TraceRecord> traces) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", model);
        params.put("provider", traces.stream().map(TraceRecord::provider)
                .filter(provider -> provider != null && !provider.isBlank()).findFirst().orElse("(unknown)"));
        params.put("calls", (long) traces.size());
        params.put("tokens", traces.stream()
                .mapToLong(trace -> trace.totalTokens() == null ? 0 : trace.totalTokens()).sum());
        params.put("errors", traces.stream()
                .filter(trace -> TraceRecord.STATUS_ERROR.equals(trace.status())).count());
        params.put("p95_ms", p95Of(traces));
        return params;
    }

    private static long p95Of(List<TraceRecord> traces) {
        long[] sorted = traces.stream().mapToLong(TraceRecord::durationMs).sorted().toArray();
        double rank = 0.95 * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        double weight = rank - lo;
        return Math.round(sorted[lo] * (1 - weight) + sorted[hi] * weight);
    }

    private ObservabilityTimeSeries.Series recentSeries() {
        return timeSeriesProvider.getObject().compute(Window.LAST_3H, System.currentTimeMillis());
    }

    private OllamaMonitorService.OllamaStatus ollamaStatus() {
        return ollamaMonitorServiceProvider.getObject().cachedSnapshot();
    }

    private SystemMetricsSnapshot.Snapshot captureSystemSnapshot() {
        try {
            return systemMetricsSnapshotProvider.getObject().capture();
        } catch (RuntimeException e) {
            logger.debug("usage snapshot: system metrics unavailable: {}", e.getMessage());
            return null;
        }
    }

    private String topQuantization() {
        List<OllamaMonitorService.InstalledModel> installed = ollamaStatus().installedModels();
        return installed.stream().map(OllamaMonitorService.InstalledModel::quantization)
                .filter(quantization -> quantization != null && !quantization.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private String toolSource(String toolName, ToolSpecService toolSpecService) {
        if (integrityVerifier.isReservedToolName(toolName)) return "builtin";
        if (toolSpecService == null) return "external";
        if (toolSpecService.getToolSpecAsOpt(toolName).isPresent()) return "authored";
        if (toolSpecService.getExternalMcpToolNames().contains(toolName)) return "composed";
        return "external";
    }

    private String toolSearchIndexLabel() {
        SpringAiPlaygroundOptions.ToolSearch toolSearch = options.chat() == null ? null : options.chat().toolSearch();
        if (toolSearch == null || !toolSearch.enabled()) return "off";
        return (toolSearch.indexType() + "-" + toolSearch.vectorStore()).toLowerCase(Locale.ROOT);
    }

    private String appVersion() {
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        if (buildProperties != null) return buildProperties.getVersion();
        String implementationVersion = getClass().getPackage().getImplementationVersion();
        return implementationVersion == null ? "dev" : implementationVersion;
    }

    private static String fileExtension(String fileName) {
        if (fileName == null) return "unknown";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "unknown";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static long sum(Map<String, Long> counters) {
        return counters.values().stream().mapToLong(Long::longValue).sum();
    }

    private static void safely(String key, ValueSupplier supplier, Map<String, Object> target) {
        try {
            Object value = supplier.get();
            if (value != null) target.put(key, value);
        } catch (RuntimeException e) {
            logger.debug("usage analytics: skipping {}: {}", key, e.getMessage());
        }
    }

    private interface ValueSupplier {
        Object get();
    }

    public record DailyUsage(Map<String, Object> snapshot, List<Map<String, Object>> mcpServers,
                             List<Map<String, Object>> models) {}
}
