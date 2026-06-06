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
package org.springaicommunity.playground.service.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.annotation.Nullable;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.oauth.McpOAuthAuthorizedEvent;
import org.springaicommunity.playground.service.oauth.OAuthClientRegistrations;
import org.springaicommunity.playground.service.util.SecretMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties.ClientType;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.modelcontextprotocol.spec.McpClientTransport;

@Service
public class McpClientService {

    private static final Logger logger = LoggerFactory.getLogger(McpClientService.class);

    public enum ServerStatus { OK, OFFLINE, ERROR, AWAITING_AUTHORIZATION, MISSING_CONFIG }

    public record StatusEntry(ServerStatus status, String error, String authorizationUrl,
                              Set<String> missingKeys) {
        public StatusEntry {
            missingKeys = missingKeys == null ? Set.of() : Set.copyOf(missingKeys);
        }
        public StatusEntry(ServerStatus status, String error, String authorizationUrl) {
            this(status, error, authorizationUrl, Set.of());
        }
        public static final StatusEntry OFFLINE = new StatusEntry(ServerStatus.OFFLINE, null, null);
        public static StatusEntry ok() { return new StatusEntry(ServerStatus.OK, null, null); }
        public static StatusEntry error(String error) { return new StatusEntry(ServerStatus.ERROR, error, null); }
        public static StatusEntry awaitingAuthorization(String authorizationUrl) {
            return new StatusEntry(ServerStatus.AWAITING_AUTHORIZATION, null, authorizationUrl);
        }
        public static StatusEntry missingConfig(Set<String> missingKeys) {
            String summary = missingKeys == null || missingKeys.isEmpty() ? "Missing required config"
                    : "Missing: " + String.join(", ", missingKeys);
            return new StatusEntry(ServerStatus.MISSING_CONFIG, summary, null, missingKeys);
        }
    }

    public record TestConnectionResult(boolean ok, String error, int toolCount) {
        public static TestConnectionResult success(int toolCount) {
            return new TestConnectionResult(true, null, toolCount);
        }
        public static TestConnectionResult failure(String error) {
            return new TestConnectionResult(false, error, 0);
        }
    }

    public record McpToolSource(String serverName, String transport) {}

    public static final String TEST_CLIENT_INFIX = " - test - ";

    private final McpSyncClientConfigurer mcpSyncClientConfigurer;
    private final McpAsyncClientConfigurer mcpAsyncClientConfigurer;
    private final McpClientCommonProperties mcpClientCommonProperties;
    private final ObjectMapper objectMapper;
    @Nullable private final OAuth2AuthorizedClientManager oauth2ClientManager;
    @Nullable private final OAuth2AuthorizedClientService oauth2AuthorizedClientService;
    private final ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider;
    private final McpNotificationStore notificationStore;
    private final MeterRegistry meterRegistry;
    private final Map<String, List<McpSchema.Root>> rootsByServer = new ConcurrentHashMap<>();

    private final Map<McpTransportType, McpClientPropertiesService<?>> typeMcpClientPropertiesServiceMap;
    private final BiFunction<NamedClientMcpTransport, Implementation, McpClientOps> mcpClientOpsBiFunction;
    // Keyed by transport:serverName so description/connection-only edits reuse the same live client.
    private final Map<String, McpClientOps> connectingMcpClientOpsMap;

    private final Map<String, StatusEntry> statusCache;

    private final Map<String, McpToolSource> toolNameToSource = new ConcurrentHashMap<>();

    public McpClientService(@Nullable McpSyncClientConfigurer mcpSyncClientConfigurer,
            @Nullable McpAsyncClientConfigurer mcpAsyncClientConfigurer,
            McpClientCommonProperties mcpClientCommonProperties, ObjectMapper objectMapper,
            McpClientPropertiesService<?>[] mcpClientPropertiesServices,
            @Nullable OAuth2AuthorizedClientManager oauth2ClientManager,
            @Nullable OAuth2AuthorizedClientService oauth2AuthorizedClientService,
            ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider,
            McpNotificationStore notificationStore,
            MeterRegistry meterRegistry) {
        this.mcpSyncClientConfigurer = mcpSyncClientConfigurer;
        this.mcpAsyncClientConfigurer = mcpAsyncClientConfigurer;
        this.mcpClientCommonProperties = mcpClientCommonProperties;
        this.objectMapper = objectMapper;
        this.oauth2ClientManager = oauth2ClientManager;
        this.oauth2AuthorizedClientService = oauth2AuthorizedClientService;
        this.mcpServerInfoServiceProvider = mcpServerInfoServiceProvider;
        this.notificationStore = notificationStore;
        this.meterRegistry = meterRegistry;
        this.typeMcpClientPropertiesServiceMap = Arrays.stream(mcpClientPropertiesServices)
                .collect(Collectors.toMap(McpClientPropertiesService::getTransportType, Function.identity()));
        this.mcpClientOpsBiFunction = (namedClientMcpTransport, info) ->
                mcpClientCommonProperties.getType() == ClientType.SYNC ? newSync(namedClientMcpTransport,
                        info) : newAsync(namedClientMcpTransport, info);
        this.connectingMcpClientOpsMap = new ConcurrentHashMap<>();
        this.statusCache = new ConcurrentHashMap<>();
    }

    public String selfLoopbackServerName() {
        McpServerInfoService infoService = this.mcpServerInfoServiceProvider.getIfAvailable();
        McpServerInfo defaultInfo = infoService == null ? null : infoService.getDefaultMcpServerInfo();
        return defaultInfo == null ? null : defaultInfo.serverName();
    }

    public boolean isSelfLoopback(String serverName) {
        String self = selfLoopbackServerName();
        return self != null && self.equalsIgnoreCase(serverName);
    }

    private McpSyncClientOps newSync(NamedClientMcpTransport namedClientMcpTransport, Implementation info) {
        logger.info("Creating SYNC MCP client: name={}, transport={}",
                namedClientMcpTransport.name(), namedClientMcpTransport.transport().getClass().getSimpleName());
        String serverKey = namedClientMcpTransport.name();
        McpClient.SyncSpec syncSpec = McpClient.sync(namedClientMcpTransport.transport())
                .clientInfo(info)
                .requestTimeout(mcpClientCommonProperties.getRequestTimeout())
                .loggingConsumer(n -> notificationStore.record(serverKey,
                        McpNotificationStore.Event.of(McpNotificationStore.Kind.LOGGING,
                                describeLogging(n), n)))
                .progressConsumer(n -> notificationStore.record(serverKey,
                        McpNotificationStore.Event.of(McpNotificationStore.Kind.PROGRESS,
                                describeProgress(n), n)))
                .toolsChangeConsumer(tools -> notificationStore.record(serverKey,
                        McpNotificationStore.Event.of(McpNotificationStore.Kind.TOOLS_CHANGED,
                                "Tools list changed (" + tools.size() + ")", tools)))
                .resourcesChangeConsumer(resources -> notificationStore.record(serverKey,
                        McpNotificationStore.Event.of(McpNotificationStore.Kind.RESOURCES_CHANGED,
                                "Resources list changed (" + resources.size() + ")", resources)))
                .promptsChangeConsumer(prompts -> notificationStore.record(serverKey,
                        McpNotificationStore.Event.of(McpNotificationStore.Kind.PROMPTS_CHANGED,
                                "Prompts list changed (" + prompts.size() + ")", prompts)))
                .sampling(req -> awaitSampling(serverKey, req))
                .elicitation(req -> awaitElicitation(serverKey, req));
        List<McpSchema.Root> roots = rootsByServer.getOrDefault(serverKey, List.of());
        syncSpec = syncSpec.roots(roots);
        syncSpec = mcpSyncClientConfigurer.configure(namedClientMcpTransport.name(), syncSpec);
        McpSyncClient mcpSyncClient =
                syncSpec.requestTimeout(this.mcpClientCommonProperties.getRequestTimeout()).build();
        mcpSyncClient.initialize();
        return new McpSyncClientOps(mcpSyncClient);
    }

    private McpAsyncClientOps newAsync(NamedClientMcpTransport namedClientMcpTransport, Implementation implementation) {
        logger.info("Creating ASYNC MCP client: name={}, transport={}",
                namedClientMcpTransport.name(), namedClientMcpTransport.transport().getClass().getSimpleName());
        String serverKey = namedClientMcpTransport.name();
        McpClient.AsyncSpec asyncSpec = McpClient.async(namedClientMcpTransport.transport())
                .clientInfo(implementation).requestTimeout(mcpClientCommonProperties.getRequestTimeout())
                .loggingConsumer(n -> {
                    notificationStore.record(serverKey, McpNotificationStore.Event.of(
                            McpNotificationStore.Kind.LOGGING, describeLogging(n), n));
                    return reactor.core.publisher.Mono.empty();
                })
                .progressConsumer(n -> {
                    notificationStore.record(serverKey, McpNotificationStore.Event.of(
                            McpNotificationStore.Kind.PROGRESS, describeProgress(n), n));
                    return reactor.core.publisher.Mono.empty();
                })
                .toolsChangeConsumer(tools -> {
                    notificationStore.record(serverKey, McpNotificationStore.Event.of(
                            McpNotificationStore.Kind.TOOLS_CHANGED,
                            "Tools list changed (" + tools.size() + ")", tools));
                    return reactor.core.publisher.Mono.empty();
                })
                .resourcesChangeConsumer(resources -> {
                    notificationStore.record(serverKey, McpNotificationStore.Event.of(
                            McpNotificationStore.Kind.RESOURCES_CHANGED,
                            "Resources list changed (" + resources.size() + ")", resources));
                    return reactor.core.publisher.Mono.empty();
                })
                .promptsChangeConsumer(prompts -> {
                    notificationStore.record(serverKey, McpNotificationStore.Event.of(
                            McpNotificationStore.Kind.PROMPTS_CHANGED,
                            "Prompts list changed (" + prompts.size() + ")", prompts));
                    return reactor.core.publisher.Mono.empty();
                })
                .sampling(req -> reactor.core.publisher.Mono.fromCallable(() -> awaitSampling(serverKey, req))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()))
                .elicitation(req -> reactor.core.publisher.Mono.fromCallable(() -> awaitElicitation(serverKey, req))
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()));
        List<McpSchema.Root> roots = rootsByServer.getOrDefault(serverKey, List.of());
        asyncSpec = asyncSpec.roots(roots);
        asyncSpec = mcpAsyncClientConfigurer.configure(namedClientMcpTransport.name(), asyncSpec);
        McpAsyncClient mcpAsyncClient =
                asyncSpec.requestTimeout(this.mcpClientCommonProperties.getRequestTimeout()).build();
        mcpAsyncClient.initialize().block();
        return new McpAsyncClientOps(mcpAsyncClient);
    }

    private static String describeLogging(McpSchema.LoggingMessageNotification n) {
        StringBuilder sb = new StringBuilder();
        if (n.level() != null) sb.append(n.level()).append(": ");
        if (n.logger() != null) sb.append("[").append(n.logger()).append("] ");
        sb.append(String.valueOf(n.data()));
        String s = sb.toString();
        return s.length() > 240 ? s.substring(0, 240) + "…" : s;
    }

    private static String describeProgress(McpSchema.ProgressNotification n) {
        StringBuilder sb = new StringBuilder("Progress");
        if (n.progressToken() != null) sb.append(" [").append(n.progressToken()).append("]");
        sb.append(": ").append(n.progress());
        if (n.total() != null) sb.append(" / ").append(n.total());
        return sb.toString();
    }

    private McpSchema.CreateMessageResult awaitSampling(String serverKey,
            McpSchema.CreateMessageRequest request) {
        String outcome;
        try {
            McpSchema.CreateMessageResult result =
                    notificationStore.awaitSamplingResponse(serverKey, request).get(2, TimeUnit.MINUTES);
            outcome = "success";
            recordServerHandler("sampling", serverKey, outcome);
            return result;
        } catch (TimeoutException e) {
            recordServerHandler("sampling", serverKey, "timeout");
            return McpSchema.CreateMessageResult.builder()
                    .role(McpSchema.Role.ASSISTANT)
                    .content(new McpSchema.TextContent("Timed out waiting for user response."))
                    .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                    .build();
        } catch (Exception e) {
            recordServerHandler("sampling", serverKey, "error");
            return McpSchema.CreateMessageResult.builder()
                    .role(McpSchema.Role.ASSISTANT)
                    .content(new McpSchema.TextContent("Error: " + e.getMessage()))
                    .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                    .build();
        }
    }

    private McpSchema.ElicitResult awaitElicitation(String serverKey, McpSchema.ElicitRequest request) {
        try {
            McpSchema.ElicitResult result =
                    notificationStore.awaitElicitationResponse(serverKey, request).get(2, TimeUnit.MINUTES);
            recordServerHandler("elicitation",
                    serverKey, result.action() == McpSchema.ElicitResult.Action.ACCEPT ? "accept" : "reject");
            return result;
        } catch (TimeoutException e) {
            recordServerHandler("elicitation", serverKey, "timeout");
            return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of());
        } catch (Exception e) {
            recordServerHandler("elicitation", serverKey, "error");
            return new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of());
        }
    }

    private void recordServerHandler(String kind, String serverName, String outcome) {
        meterRegistry.counter("mcp.server.handler",
                "kind", kind,
                "server", serverName,
                "outcome", outcome).increment();
    }

    public void startMcpClient(McpServerInfo mcpServerInfo) {
        logger.info("Starting MCP client connection: serverName={}, transportType={}", mcpServerInfo.serverName(),
                mcpServerInfo.mcpTransportType());
        String key = clientKey(mcpServerInfo);

        Set<String> missing = findMissingEnv(mcpServerInfo);
        if (!missing.isEmpty()) {
            logger.info("MCP client gated by missing config: serverName={}, missing={}",
                    mcpServerInfo.serverName(), missing);
            statusCache.put(key, StatusEntry.missingConfig(missing));
            return;
        }

        Implementation info =
                new Implementation(mcpClientCommonProperties.getName() + " - " + mcpServerInfo.serverName(),
                        mcpClientCommonProperties.getVersion());
        try {
            McpClientOps mcpClientOps = mcpClientOpsBiFunction.apply(buildMcpClientTransport(mcpServerInfo), info);
            McpClientOps previous = connectingMcpClientOpsMap.put(key, mcpClientOps);
            statusCache.put(key, StatusEntry.ok());
            refreshToolIndex(mcpServerInfo, mcpClientOps);
            recordLifecycle("connect", mcpServerInfo);
            McpServerInfoService infoService = mcpServerInfoServiceProvider.getIfAvailable();
            if (infoService != null) infoService.markUsed(mcpServerInfo);
            if (previous != null) {
                logger.info("Replacing existing MCP client; closing previous: serverName={}",
                        mcpServerInfo.serverName());
                try {
                    previous.close();
                } catch (RuntimeException e) {
                    logger.error("Failed to close previous MCP client: serverName={}",
                            mcpServerInfo.serverName(), e);
                }
            }
        } catch (RuntimeException e) {
            if (isAuthorizationRequired(e)) {
                String authorizationUrl = "/oauth2/authorization/" + OAuthClientRegistrations.registrationId(
                        mcpServerInfo);
                logger.info("MCP client awaiting OAuth authorization: serverName={}, authorizationUrl={}",
                        mcpServerInfo.serverName(), authorizationUrl);
                statusCache.put(key, StatusEntry.awaitingAuthorization(authorizationUrl));
                recordLifecycle("awaiting_auth", mcpServerInfo);
                return;
            }
            statusCache.put(key, StatusEntry.error(safeErrorMessage(mcpServerInfo, e)));
            recordLifecycle("error", mcpServerInfo);
            throw e;
        }
    }

    private static String safeErrorMessage(McpServerInfo info, Throwable t) {
        String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
        if (info == null) return msg;
        return SecretMasking.mask(msg, SecretMasking.collectFromTemplate(info.connectionAsJson()));
    }

    public Set<String> findMissingEnv(McpServerInfo mcpServerInfo) {
        McpClientPropertiesService<?> propertiesService =
                this.typeMcpClientPropertiesServiceMap.get(mcpServerInfo.mcpTransportType());
        if (propertiesService == null) return Set.of();
        return propertiesService.findMissingEnv(this.objectMapper, mcpServerInfo.connectionAsJson());
    }

    private void recordLifecycle(String outcome, McpServerInfo mcpServerInfo) {
        meterRegistry.counter("mcp.server.lifecycle",
                "outcome", outcome,
                "server", mcpServerInfo.serverName(),
                "transport", String.valueOf(mcpServerInfo.mcpTransportType())).increment();
    }

    private <T> T timePrimitive(String kind, McpServerInfo mcpServerInfo, Supplier<T> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return action.get();
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("mcp.primitive",
                    "kind", kind,
                    "server", mcpServerInfo.serverName(),
                    "transport", String.valueOf(mcpServerInfo.mcpTransportType()),
                    "outcome", outcome));
        }
    }

    private static boolean isAuthorizationRequired(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof ClientAuthorizationRequiredException) return true;
            if (current.getClass().getName().endsWith("ClientAuthorizationRequiredException")) return true;
            String msg = current.getMessage();
            if (msg != null && msg.contains("ClientAuthorizationRequiredException")) return true;
            Throwable next = current.getCause();
            if (next == null || next == current) break;
            current = next;
        }
        return false;
    }

    public TestConnectionResult testConnection(McpServerInfo mcpServerInfo) {
        logger.info("Testing MCP client connection: serverName={}, transportType={}",
                mcpServerInfo.serverName(), mcpServerInfo.mcpTransportType());
        McpSyncClient transientClient = null;
        try {
            McpClientTransport transport = buildMcpClientTransport(mcpServerInfo).transport();
            Implementation info =
                    new Implementation(mcpClientCommonProperties.getName() + TEST_CLIENT_INFIX
                            + mcpServerInfo.serverName(), mcpClientCommonProperties.getVersion());
            transientClient = McpClient.sync(transport).clientInfo(info)
                    .requestTimeout(mcpClientCommonProperties.getRequestTimeout()).build();
            transientClient.initialize();
            int toolCount = transientClient.listTools().tools().size();
            return TestConnectionResult.success(toolCount);
        } catch (Exception e) {
            String msg = safeErrorMessage(mcpServerInfo, e);
            logger.warn("Test connection failed: serverName={}, error={}", mcpServerInfo.serverName(), msg);
            return TestConnectionResult.failure(msg);
        } finally {
            if (transientClient != null) {
                try {
                    transientClient.close();
                } catch (RuntimeException ignore) {
                }
            }
        }
    }

    public StatusEntry getStatus(McpServerInfo mcpServerInfo) {
        return statusCache.getOrDefault(clientKey(mcpServerInfo), StatusEntry.OFFLINE);
    }

    public Map<String, StatusEntry> snapshotStatuses() {
        return Map.copyOf(statusCache);
    }

    public StatusEntry pingAndUpdateStatus(McpServerInfo mcpServerInfo) {
        String key = clientKey(mcpServerInfo);
        McpClientOps ops = connectingMcpClientOpsMap.get(key);
        if (ops == null) {
            StatusEntry existing = statusCache.get(key);
            if (existing != null && existing.status() != ServerStatus.OK
                    && existing.status() != ServerStatus.OFFLINE) {
                return existing;
            }
            statusCache.remove(key);
            return StatusEntry.OFFLINE;
        }
        return timePrimitive("ping", mcpServerInfo, () -> {
            try {
                ops.ping();
                StatusEntry ok = StatusEntry.ok();
                statusCache.put(key, ok);
                return ok;
            } catch (RuntimeException e) {
                String msg = safeErrorMessage(mcpServerInfo, e);
                StatusEntry err = StatusEntry.error(msg);
                statusCache.put(key, err);
                return err;
            }
        });
    }

    public Object pingMcpClient(McpServerInfo mcpServerInfo) {
        logger.info("Pinging MCP client: serverName={}", mcpServerInfo.serverName());
        return timePrimitive("ping", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::ping)
                        .orElseThrow());
    }

    public void stopMcpClient(McpServerInfo mcpServerInfo) {
        String key = clientKey(mcpServerInfo);
        McpClientOps mcpClientOps = connectingMcpClientOpsMap.get(key);
        logger.info("Stopping MCP client: serverName={}, mcpClientOps={}", mcpServerInfo.serverName(), mcpClientOps);
        statusCache.put(key, StatusEntry.OFFLINE);
        evictToolIndex(mcpServerInfo);
        if (Objects.nonNull(mcpClientOps)) {
            mcpClientOps.close();
            recordLifecycle("disconnect", mcpServerInfo);
        }
    }

    public Optional<McpToolSource> lookupToolSource(String toolName) {
        if (toolName == null || toolName.isBlank()) return Optional.empty();
        return Optional.ofNullable(toolNameToSource.get(toolName));
    }

    private void refreshToolIndex(McpServerInfo mcpServerInfo, McpClientOps ops) {
        String serverName = mcpServerInfo.serverName();
        String transport = String.valueOf(mcpServerInfo.mcpTransportType());
        try {
            List<McpSchema.Tool> tools = ops.listTools();
            if (tools == null) return;
            evictToolIndex(mcpServerInfo);
            McpToolSource source = new McpToolSource(serverName, transport);
            for (McpSchema.Tool tool : tools) {
                if (tool.name() == null || tool.name().isBlank()) continue;
                McpToolSource previous = toolNameToSource.put(tool.name(), source);
                if (previous != null && !previous.serverName().equals(serverName)) {
                    logger.warn("MCP tool name collision: tool={} previousServer={} newServer={}",
                            tool.name(), previous.serverName(), serverName);
                }
            }
        } catch (RuntimeException e) {
            // index is best-effort — connect itself already succeeded
            logger.warn("Failed to index MCP tools for observability: serverName={}, error={}",
                    serverName, e.getMessage());
        }
    }

    private void evictToolIndex(McpServerInfo mcpServerInfo) {
        String serverName = mcpServerInfo.serverName();
        toolNameToSource.entrySet().removeIf(e -> serverName.equals(e.getValue().serverName()));
    }

    public Optional<ServerCapabilities> getServerCapabilitiesAsOpt(McpServerInfo mcpServerInfo) {
        return timePrimitive("capabilities", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::capabilities));
    }

    public Optional<List<McpSchema.Tool>> getToolListAsOpt(McpServerInfo mcpServerInfo) {
        return timePrimitive("tools.list", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::listTools));
    }

    public Optional<List<McpSchema.Resource>> getResourceListAsOpt(McpServerInfo mcpServerInfo) {
        return timePrimitive("resources.list", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::listResources));
    }

    public Optional<McpSchema.ReadResourceResult> readResourceAsOpt(McpServerInfo mcpServerInfo, String uri) {
        logger.info("Reading MCP resource: serverName={}, uri={}", mcpServerInfo.serverName(), uri);
        return timePrimitive("resources.read", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(ops -> ops.readResource(uri)));
    }

    public Optional<List<McpSchema.ResourceTemplate>> getResourceTemplateListAsOpt(McpServerInfo mcpServerInfo) {
        return timePrimitive("resources.templates", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::listResourceTemplates));
    }

    public Optional<List<McpSchema.Prompt>> getPromptListAsOpt(McpServerInfo mcpServerInfo) {
        return timePrimitive("prompts.list", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(McpClientOps::listPrompts));
    }

    public Optional<McpSchema.GetPromptResult> getPromptAsOpt(McpServerInfo mcpServerInfo, String name,
            Map<String, Object> args) {
        logger.info("Getting MCP prompt: serverName={}, name={}", mcpServerInfo.serverName(), name);
        return timePrimitive("prompts.get", mcpServerInfo, () ->
                Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                        .map(ops -> ops.getPrompt(name, args)));
    }

    public boolean setLoggingLevel(McpServerInfo mcpServerInfo, McpSchema.LoggingLevel level) {
        return timePrimitive("logging.set_level", mcpServerInfo, () -> {
            McpClientOps ops = connectingMcpClientOpsMap.get(clientKey(mcpServerInfo));
            if (ops == null) return false;
            ops.setLoggingLevel(level);
            return true;
        });
    }

    public List<McpSchema.Root> getRoots(McpServerInfo mcpServerInfo) {
        return new java.util.ArrayList<>(rootsByServer.getOrDefault(mcpServerInfo.serverName(), List.of()));
    }

    public void addRoot(McpServerInfo mcpServerInfo, McpSchema.Root root) {
        timePrimitive("roots.add", mcpServerInfo, () -> {
            String key = mcpServerInfo.serverName();
            rootsByServer.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(root);
            McpClientOps ops = connectingMcpClientOpsMap.get(clientKey(mcpServerInfo));
            if (ops != null) {
                ops.addRoot(root);
                ops.notifyRootsListChanged();
            }
            return null;
        });
    }

    public void removeRoot(McpServerInfo mcpServerInfo, String name) {
        timePrimitive("roots.remove", mcpServerInfo, () -> {
            String key = mcpServerInfo.serverName();
            List<McpSchema.Root> rs = rootsByServer.get(key);
            if (rs != null) rs.removeIf(r -> name.equals(r.name()));
            McpClientOps ops = connectingMcpClientOpsMap.get(clientKey(mcpServerInfo));
            if (ops != null) {
                ops.removeRoot(name);
                ops.notifyRootsListChanged();
            }
            return null;
        });
    }

    public List<McpNotificationStore.Event> snapshotEvents(McpServerInfo mcpServerInfo) {
        return notificationStore.snapshot(mcpServerInfo.serverName());
    }

    public Runnable subscribeNotifications(McpServerInfo mcpServerInfo,
            Consumer<McpNotificationStore.Event> listener) {
        return notificationStore.subscribe(mcpServerInfo.serverName(), listener);
    }

    public Runnable subscribePendingChange(McpServerInfo mcpServerInfo, Runnable listener) {
        return notificationStore.subscribePendingChange(mcpServerInfo.serverName(), listener);
    }

    public void clearEvents(McpServerInfo mcpServerInfo) {
        notificationStore.clear(mcpServerInfo.serverName());
    }

    public List<McpNotificationStore.PendingSampling> snapshotPendingSamplings(McpServerInfo mcpServerInfo) {
        return notificationStore.snapshotPendingSamplings(mcpServerInfo.serverName());
    }

    public List<McpNotificationStore.PendingElicitation> snapshotPendingElicitations(McpServerInfo mcpServerInfo) {
        return notificationStore.snapshotPendingElicitations(mcpServerInfo.serverName());
    }

    public void completeSampling(McpServerInfo mcpServerInfo, String pendingId,
            McpSchema.CreateMessageResult result) {
        notificationStore.completeSampling(mcpServerInfo.serverName(), pendingId, result);
    }

    public void completeElicitation(McpServerInfo mcpServerInfo, String pendingId,
            McpSchema.ElicitResult result) {
        notificationStore.completeElicitation(mcpServerInfo.serverName(), pendingId, result);
    }

    public Optional<McpSchema.CallToolResult> callTool(McpServerInfo mcpServerInfo, String toolName,
            Map<String, Object> args, Map<String, Object> meta) {
        return timePrimitive("tools.call", mcpServerInfo, () -> {
            String cid = UUID.randomUUID().toString().substring(0, 8);
            Set<String> secrets = SecretMasking.collectFromTemplate(mcpServerInfo.connectionAsJson());
            logger.info("mcp.tool.start cid={} server={} tool={} via=inspector",
                    cid, mcpServerInfo.serverName(), toolName);
            long startNs = System.nanoTime();
            try {
                Optional<McpSchema.CallToolResult> result =
                        Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                                .map(mcpClientOps -> mcpClientOps.callTool(toolName, args, meta));
                long durMs = (System.nanoTime() - startNs) / 1_000_000L;
                boolean ok = result.map(r -> !Boolean.TRUE.equals(r.isError())).orElse(false);
                logger.info("mcp.tool.done cid={} server={} tool={} durationMs={} ok={} via=inspector",
                        cid, mcpServerInfo.serverName(), toolName, durMs, ok);
                McpServerInfoService infoService = mcpServerInfoServiceProvider.getIfAvailable();
                if (infoService != null) infoService.markUsed(mcpServerInfo);
                return result;
            } catch (RuntimeException e) {
                long durMs = (System.nanoTime() - startNs) / 1_000_000L;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                logger.warn("mcp.tool.crash cid={} server={} tool={} durationMs={} via=inspector error={}",
                        cid, mcpServerInfo.serverName(), toolName, durMs,
                        SecretMasking.mask(msg, secrets));
                throw e;
            }
        });
    }

    public List<ToolCallbackProvider> buildToolCallbackProviders(McpServerInfo... mcpServerInfos) {
        return Arrays.stream(mcpServerInfos)
                .filter(info -> connectingMcpClientOpsMap.containsKey(clientKey(info)))
                .map(info -> {
                    McpClientOps ops = connectingMcpClientOpsMap.get(clientKey(info));
                    Set<String> secrets = SecretMasking.collectFromTemplate(info.connectionAsJson());
                    ToolCallback[] wrapped = Arrays.stream(ops.toolCallbackProvider().getToolCallbacks())
                            .map(cb -> (ToolCallback) new LoggingMcpToolCallback(cb, info.serverName(), secrets))
                            .toArray(ToolCallback[]::new);
                    return (ToolCallbackProvider) () -> wrapped;
                })
                .toList();
    }

    public void deleteConnectingMcpServer(McpServerInfo mcpServerInfo) {
        logger.info("Deleting MCP client connection: serverName={}", mcpServerInfo.serverName());
        stopMcpClient(mcpServerInfo);
        String key = clientKey(mcpServerInfo);
        this.connectingMcpClientOpsMap.remove(key);
        this.statusCache.remove(key);
        this.rootsByServer.remove(mcpServerInfo.serverName());
        this.notificationStore.removeServer(mcpServerInfo.serverName());
        if (this.oauth2AuthorizedClientService != null) {
            this.oauth2AuthorizedClientService.removeAuthorizedClient(
                    OAuthClientRegistrations.registrationId(mcpServerInfo), "spring-ai-playground-mcp-client");
        }
    }

    public boolean isConnecting(McpServerInfo mcpServerInfo) {
        return this.connectingMcpClientOpsMap.containsKey(clientKey(mcpServerInfo));
    }

    private String clientKey(McpServerInfo mcpServerInfo) {
        return mcpServerInfo.mcpTransportType() + ":" + mcpServerInfo.serverName();
    }

    private NamedClientMcpTransport buildMcpClientTransport(McpServerInfo mcpServerInfo) {
        String registrationId = OAuthClientRegistrations.registrationId(mcpServerInfo);
        return new NamedClientMcpTransport(mcpServerInfo.serverName(),
                this.typeMcpClientPropertiesServiceMap.get(mcpServerInfo.mcpTransportType())
                        .buildClientTransport(this.objectMapper, mcpServerInfo.connectionAsJson(),
                                registrationId, this.oauth2ClientManager));
    }

    @EventListener
    public void onOAuthAuthorized(McpOAuthAuthorizedEvent event) {
        String regId = event.getRegistrationId();
        McpServerInfoService infoService = mcpServerInfoServiceProvider.getIfAvailable();
        if (infoService == null) return;
        infoService.read().stream()
                .filter(server -> regId.equals(OAuthClientRegistrations.registrationId(server)))
                .findFirst()
                .ifPresent(server -> {
                    meterRegistry.counter("mcp.oauth.authorized",
                            "server", server.serverName()).increment();
                    StatusEntry currentStatus = getStatus(server);
                    if (currentStatus.status() != ServerStatus.AWAITING_AUTHORIZATION) return;
                    logger.info("OAuth authorized for serverName={}; restarting MCP client",
                            server.serverName());
                    try {
                        startMcpClient(server);
                    } catch (RuntimeException e) {
                        logger.warn("Restart after OAuth authorization failed for {}: {}",
                                server.serverName(), e.getMessage());
                    }
                });
    }

    public List<OAuthSnapshot> snapshotOAuthState() {
        McpServerInfoService infoService = mcpServerInfoServiceProvider.getIfAvailable();
        if (infoService == null) return List.of();
        List<OAuthSnapshot> out = new ArrayList<>();
        for (McpServerInfo server : infoService.read()) {
            String regId = OAuthClientRegistrations.registrationId(server);
            if (regId == null) continue;
            StatusEntry status = getStatus(server);
            Long expiresAtMs = null;
            boolean hasRefresh = false;
            if (oauth2AuthorizedClientService != null) {
                OAuth2AuthorizedClient client =
                        oauth2AuthorizedClientService.loadAuthorizedClient(
                                regId, "spring-ai-playground-mcp-client");
                if (client != null) {
                    if (client.getAccessToken() != null && client.getAccessToken().getExpiresAt() != null) {
                        expiresAtMs = client.getAccessToken().getExpiresAt().toEpochMilli();
                    }
                    hasRefresh = client.getRefreshToken() != null;
                }
            }
            out.add(new OAuthSnapshot(server.serverName(),
                    String.valueOf(server.mcpTransportType()),
                    status.status().name(),
                    status.authorizationUrl(),
                    expiresAtMs, hasRefresh));
        }
        return out;
    }

    public record OAuthSnapshot(String serverName, String transport, String status,
                                String authorizationUrl, Long accessTokenExpiresAtMs,
                                boolean hasRefreshToken) {}

    @EventListener(ContextClosedEvent.class)
    public void shutdownAllMcpClients() {
        logger.info("Shutting down all MCP clients. currentActiveClientCount={}", connectingMcpClientOpsMap.size());
        this.connectingMcpClientOpsMap.forEach((key, mcpClientOps) -> {
            try {
                mcpClientOps.close();
            } catch (RuntimeException e) {
                logger.error("Error closing MCP client: key={}", key, e);
            }
        });
    }

}
