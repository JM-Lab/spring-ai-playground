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
package org.springaicommunity.playground.service.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.SharedDataReader;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springaicommunity.playground.service.mcp.client.McpClientPropertiesService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.oauth.McpClientRegistrationRepository;
import org.springaicommunity.playground.service.oauth.OAuthClientRegistrations;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.EventListener;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class McpServerInfoService implements SharedDataReader<List<McpServerInfo>>,
        ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpServerInfoService.class);

    private final ObjectMapper objectMapper;
    private final Map<McpTransportType, Map<String, McpServerInfo>> typeMcpServerInfosMap;
    private final McpClientService mcpClientService;
    private final McpCatalogService mcpCatalogService;
    private final ObjectProvider<McpServerInfoPersistenceService> mcpServerInfoPersistenceServiceProvider;
    private final McpClientRegistrationRepository mcpClientRegistrationRepository;
    private final String defaultMcpServerName;
    private final String builtInServerDescription;
    private final boolean stdioServerMode;
    private final ThreadLocal<Boolean> skipPersist = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private volatile boolean applicationReady = false;
    private McpServerInfo defaultMcpServerInfo;

    public McpServerInfoService(ObjectMapper objectMapper, McpClientPropertiesService<?>[] mcpClientPropertiesServices,
            McpClientService mcpClientService, McpCatalogService mcpCatalogService,
            ObjectProvider<McpServerInfoPersistenceService> mcpServerInfoPersistenceServiceProvider,
            McpClientRegistrationRepository mcpClientRegistrationRepository,
            @Value("${server.port:8282}") int serverPort, McpServerProperties mcpServerProperties,
            SpringAiPlaygroundOptions playgroundOptions) {
        this.objectMapper = objectMapper;
        this.mcpClientService = mcpClientService;
        this.mcpCatalogService = mcpCatalogService;
        this.mcpServerInfoPersistenceServiceProvider = mcpServerInfoPersistenceServiceProvider;
        this.mcpClientRegistrationRepository = mcpClientRegistrationRepository;
        this.defaultMcpServerName = playgroundOptions.builtInMcpServer().name();
        this.builtInServerDescription = playgroundOptions.builtInMcpServer().description();
        // Stdio transport has no HTTP /mcp to self-introspect; the loopback client must no-op (else boot crashes).
        this.stdioServerMode = mcpServerProperties.isStdio();
        this.typeMcpServerInfosMap = Arrays.stream(mcpClientPropertiesServices)
                .collect(Collectors.toMap(McpClientPropertiesService::getTransportType,
                        mcpClientPropertiesService -> mcpClientPropertiesService.getDefaultConnections().entrySet()
                                .stream().map(entry -> Map.entry(entry.getKey(),
                                        buildMcpServerInfo(mcpClientPropertiesService.getTransportType(),
                                                entry.getKey(), entry.getValue())))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        this.defaultMcpServerInfo = buildDefaultMcpServerInfo(serverPort);
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (Objects.nonNull(this.defaultMcpServerInfo)) {
            this.defaultMcpServerInfo = buildDefaultMcpServerInfo(event.getWebServer().getPort());
            updateMcpServerInfo(defaultMcpServerInfo.mcpTransportType(), defaultMcpServerInfo.serverName(),
                    defaultMcpServerInfo);
        }
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        this.applicationReady = true;
        updateDefaultMcpTool();
        autoStartConfiguredMcpClients();
    }

    private void autoStartConfiguredMcpClients() {
        this.typeMcpServerInfosMap.values().forEach(byName -> byName.values().forEach(info -> {
            if (info.equals(this.defaultMcpServerInfo)) return;
            try {
                this.mcpClientService.startMcpClient(info);
            } catch (RuntimeException e) {
                logger.warn("Failed to auto-start configured MCP client: serverName={}, error={}",
                        info.serverName(), e.getMessage());
            }
        }));
    }

    public McpServerInfo getDefaultMcpServerInfo() {
        return defaultMcpServerInfo;
    }

    private McpServerInfo buildDefaultMcpServerInfo(int serverPort) {
        return buildMcpServerInfo(McpTransportType.STREAMABLE_HTTP, this.defaultMcpServerName,
                new McpStreamableHttpClientProperties.ConnectionParameters("http://127.0.0.1:" + serverPort,
                        "/mcp"), this.builtInServerDescription);
    }

    private McpServerInfo buildMcpServerInfo(McpTransportType transportType, String serverName, Object connection) {
        return buildMcpServerInfo(transportType, serverName, connection, null);
    }

    private McpServerInfo buildMcpServerInfo(McpTransportType transportType, String serverName, Object connection,
            String descriptionOverride) {
        try {
            long timestamp = System.currentTimeMillis();
            Optional<McpCatalogEntry> entry = this.mcpCatalogService.findByServerName(serverName);
            String description;
            if (descriptionOverride != null && !descriptionOverride.isBlank()) {
                description = descriptionOverride;
            } else {
                description = entry.map(McpCatalogEntry::description)
                        .filter(d -> d != null && !d.isBlank())
                        .orElse("[Default Connection] " + serverName);
            }
            String category = entry.map(McpCatalogEntry::category).orElse(McpServerInfo.DEFAULT_CATEGORY);
            Set<String> tags = entry.<Set<String>>map(McpCatalogEntry::tags).orElseGet(Set::of);
            return new McpServerInfo(transportType, serverName, description,
                    timestamp, timestamp, transformAsJson(connection), category, tags);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize default MCP server connection for " + serverName, e);
        }
    }

    private String transformAsJson(Object value) throws JsonProcessingException {
        return this.objectMapper.writeValueAsString(value);
    }

    public Map<McpTransportType, List<McpServerInfo>> getMcpServerInfos() {
        return this.typeMcpServerInfosMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(Map.Entry::getValue).toList()));
    }

    public McpServerInfo createBlankMcpServerInfo() {
        String defaultDescription = "Please edit the description of the MCP Server.";
        long timestamp = System.currentTimeMillis();
        return new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "New MCP Server", defaultDescription, timestamp,
                timestamp, null);
    }

    public void deleteMcpServerInfo(McpTransportType transportType, String serverName) {
        McpServerInfo mcpServerInfo = this.typeMcpServerInfosMap.get(transportType).remove(serverName);
        if (mcpServerInfo != null) {
            this.mcpClientRegistrationRepository.unregister(OAuthClientRegistrations.registrationId(mcpServerInfo));
            this.mcpServerInfoPersistenceServiceProvider.getObject().deleteAsync(mcpServerInfo);
        }
    }

    public void loadAll(Runnable loadAction) {
        this.skipPersist.set(Boolean.TRUE);
        try {
            loadAction.run();
        } finally {
            this.skipPersist.remove();
        }
    }

    public McpServerInfo markUsed(McpServerInfo info) {
        if (info == null || info.mcpTransportType() == null || info.serverName() == null) return info;
        Map<String, McpServerInfo> infos = this.typeMcpServerInfosMap.get(info.mcpTransportType());
        if (infos == null) return info;
        McpServerInfo current = infos.get(info.serverName());
        if (current == null) return info;
        long now = System.currentTimeMillis();
        if (current.lastUsedAtEpochMs() != null && now - current.lastUsedAtEpochMs() < 60_000L) {
            return current;
        }
        McpServerInfo stamped = current.withLastUsedAt(now);
        infos.put(info.serverName(), stamped);
        if (!Boolean.TRUE.equals(this.skipPersist.get()) && !stamped.equals(this.defaultMcpServerInfo)) {
            this.mcpServerInfoPersistenceServiceProvider.getObject().saveAsync(stamped);
        }
        return stamped;
    }

    public McpServerInfo updateMcpServerInfo(McpTransportType transportType, String serverName,
            McpServerInfo updateMcpServerInfo) {
        boolean sameKey = transportType.equals(updateMcpServerInfo.mcpTransportType()) &&
                serverName.equals(updateMcpServerInfo.serverName());
        if (!sameKey && this.typeMcpServerInfosMap.get(updateMcpServerInfo.mcpTransportType())
                .containsKey(updateMcpServerInfo.serverName()))
            throw new IllegalStateException(
                    "MCP Server already exists with name " + updateMcpServerInfo.serverName());
        if (sameKey)
            deleteMcpServerInfo(transportType, serverName);
        this.typeMcpServerInfosMap.get(updateMcpServerInfo.mcpTransportType())
                .put(updateMcpServerInfo.serverName(), updateMcpServerInfo);
        syncOAuthRegistration(updateMcpServerInfo);
        if (!Boolean.TRUE.equals(this.skipPersist.get()) &&
                !updateMcpServerInfo.equals(this.defaultMcpServerInfo))
            this.mcpServerInfoPersistenceServiceProvider.getObject().saveAsync(updateMcpServerInfo);
        return updateMcpServerInfo;
    }

    private void syncOAuthRegistration(McpServerInfo server) {
        if (server.equals(this.defaultMcpServerInfo)) return;
        String regId = OAuthClientRegistrations.registrationId(server);
        try {
            HttpConnectionParametersWithExtras.OAuth oauth = parseOAuthConfig(server);
            ClientRegistration registration = OAuthClientRegistrations.toClientRegistration(server, oauth);
            if (registration != null) {
                this.mcpClientRegistrationRepository.register(registration);
            } else {
                this.mcpClientRegistrationRepository.unregister(regId);
            }
        } catch (Exception e) {
            logger.warn("OAuth registration sync failed for {}: {}", server.serverName(), e.getMessage());
            this.mcpClientRegistrationRepository.unregister(regId);
        }
    }

    private HttpConnectionParametersWithExtras.OAuth parseOAuthConfig(McpServerInfo server)
            throws JsonProcessingException {
        if (server.connectionAsJson() == null || server.connectionAsJson().isBlank()) return null;
        return switch (server.mcpTransportType()) {
            case STREAMABLE_HTTP -> objectMapper.readValue(server.connectionAsJson(),
                    HttpConnectionParametersWithExtras.StreamableHttp.class).oauth();
            case SSE -> objectMapper.readValue(server.connectionAsJson(),
                    HttpConnectionParametersWithExtras.Sse.class).oauth();
            case STDIO -> null;
        };
    }

    @Override
    public List<McpServerInfo> read() {
        return getMcpServerInfos().values().stream().flatMap(List::stream).toList();
    }

    public void updateDefaultMcpTool() {
        if (this.stdioServerMode || this.defaultMcpServerInfo == null) {
            return;
        }
        if (!this.applicationReady) return;
        try {
            this.mcpClientService.startMcpClient(this.defaultMcpServerInfo);
        } catch (RuntimeException e) {
            logger.error("Failed to start default loopback MCP client (serverName={}): {}",
                    this.defaultMcpServerInfo.serverName(), e.getMessage(), e);
        }
    }
}
