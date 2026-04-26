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
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.annotation.Nullable;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties.ClientType;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class McpClientService {

    private static final Logger logger = LoggerFactory.getLogger(McpClientService.class);

    private final McpSyncClientConfigurer mcpSyncClientConfigurer;
    private final McpAsyncClientConfigurer mcpAsyncClientConfigurer;
    private final McpClientCommonProperties mcpClientCommonProperties;
    private final ObjectMapper objectMapper;

    private final Map<McpTransportType, McpClientPropertiesService<?>> typeMcpClientPropertiesServiceMap;
    private final BiFunction<NamedClientMcpTransport, Implementation, McpClientOps> mcpClientOpsBiFunction;
    /**
     * Keyed by transportType + ":" + serverName so updates that only change description / connection
     * payload still resolve to the same live client. Using the full {@link McpServerInfo} record as
     * the key would treat updated entries as different servers and leak the previous client.
     */
    private final Map<String, McpClientOps> connectingMcpClientOpsMap;

    public McpClientService(@Nullable McpSyncClientConfigurer mcpSyncClientConfigurer,
            @Nullable McpAsyncClientConfigurer mcpAsyncClientConfigurer,
            McpClientCommonProperties mcpClientCommonProperties, ObjectMapper objectMapper,
            McpClientPropertiesService<?>[] mcpClientPropertiesServices) {
        this.mcpSyncClientConfigurer = mcpSyncClientConfigurer;
        this.mcpAsyncClientConfigurer = mcpAsyncClientConfigurer;
        this.mcpClientCommonProperties = mcpClientCommonProperties;
        this.objectMapper = objectMapper;
        this.typeMcpClientPropertiesServiceMap = Arrays.stream(mcpClientPropertiesServices)
                .collect(Collectors.toMap(McpClientPropertiesService::getTransportType, Function.identity()));
        this.mcpClientOpsBiFunction = (namedClientMcpTransport, info) ->
                mcpClientCommonProperties.getType() == ClientType.SYNC ? newSync(namedClientMcpTransport,
                        info) : newAsync(namedClientMcpTransport, info);
        this.connectingMcpClientOpsMap = new ConcurrentHashMap<>();
    }

    private McpSyncClientOps newSync(NamedClientMcpTransport namedClientMcpTransport, Implementation info) {
        logger.info("Creating SYNC MCP client: name={}, transport={}",
                namedClientMcpTransport.name(), namedClientMcpTransport.transport().getClass().getSimpleName());
        McpClient.SyncSpec syncSpec = McpClient.sync(namedClientMcpTransport.transport())
                .clientInfo(info)
                .requestTimeout(mcpClientCommonProperties.getRequestTimeout());
        syncSpec = mcpSyncClientConfigurer.configure(namedClientMcpTransport.name(), syncSpec);
        McpSyncClient mcpSyncClient =
                syncSpec.requestTimeout(this.mcpClientCommonProperties.getRequestTimeout()).build();
        mcpSyncClient.initialize();
        return new McpSyncClientOps(mcpSyncClient);
    }

    private McpAsyncClientOps newAsync(NamedClientMcpTransport namedClientMcpTransport, Implementation implementation) {
        logger.info("Creating ASYNC MCP client: name={}, transport={}",
                namedClientMcpTransport.name(), namedClientMcpTransport.transport().getClass().getSimpleName());
        McpClient.AsyncSpec asyncSpec = McpClient.async(namedClientMcpTransport.transport())
                .clientInfo(implementation).requestTimeout(mcpClientCommonProperties.getRequestTimeout());
        asyncSpec = mcpAsyncClientConfigurer.configure(namedClientMcpTransport.name(), asyncSpec);
        McpAsyncClient mcpAsyncClient =
                asyncSpec.requestTimeout(this.mcpClientCommonProperties.getRequestTimeout()).build();
        mcpAsyncClient.initialize().block();
        return new McpAsyncClientOps(mcpAsyncClient);
    }

    public void startMcpClient(McpServerInfo mcpServerInfo) {
        logger.info("Starting MCP client connection: serverName={}, transportType={}", mcpServerInfo.serverName(),
                mcpServerInfo.mcpTransportType());
        Implementation info =
                new Implementation(mcpClientCommonProperties.getName() + " - " + mcpServerInfo.serverName(),
                        mcpClientCommonProperties.getVersion());
        McpClientOps mcpClientOps = mcpClientOpsBiFunction.apply(buildMcpClientTransport(mcpServerInfo), info);
        McpClientOps previous = connectingMcpClientOpsMap.put(clientKey(mcpServerInfo), mcpClientOps);
        if (previous != null) {
            logger.info("Replacing existing MCP client; closing previous: serverName={}", mcpServerInfo.serverName());
            try {
                previous.close();
            } catch (RuntimeException e) {
                logger.error("Failed to close previous MCP client: serverName={}", mcpServerInfo.serverName(), e);
            }
        }
    }

    public Object pingMcpClient(McpServerInfo mcpServerInfo) {
        logger.info("Pinging MCP client: serverName={}", mcpServerInfo.serverName());
        return Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo))).map(McpClientOps::ping)
                .orElseThrow();
    }

    public void stopMcpClient(McpServerInfo mcpServerInfo) {
        McpClientOps mcpClientOps = connectingMcpClientOpsMap.get(clientKey(mcpServerInfo));
        logger.info("Stopping MCP client: serverName={}, mcpClientOps={}", mcpServerInfo.serverName(), mcpClientOps);
        if (Objects.nonNull(mcpClientOps))
            mcpClientOps.close();
    }

    public Optional<ServerCapabilities> getServerCapabilitiesAsOpt(McpServerInfo mcpServerInfo) {
        return Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                .map(McpClientOps::capabilities);
    }

    public Optional<List<McpSchema.Tool>> getToolListAsOpt(McpServerInfo mcpServerInfo) {
        return Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                .map(McpClientOps::listTools);
    }

    public Optional<McpSchema.CallToolResult> callTool(McpServerInfo mcpServerInfo, String toolName,
            Map<String, Object> args, Map<String, Object> meta) {
        logger.info("Calling MCP tool: serverName={}, toolName={}", mcpServerInfo.serverName(), toolName);
        return Optional.ofNullable(connectingMcpClientOpsMap.get(clientKey(mcpServerInfo)))
                .map(mcpClientOps -> mcpClientOps.callTool(toolName, args, meta));
    }

    public List<ToolCallbackProvider> buildToolCallbackProviders(McpServerInfo... mcpServerInfos) {
        return Arrays.stream(mcpServerInfos).map(this::clientKey).map(connectingMcpClientOpsMap::get)
                .filter(Objects::nonNull).map(McpClientOps::toolCallbackProvider).toList();
    }

    public void deleteConnectingMcpServer(McpServerInfo mcpServerInfo) {
        logger.info("Deleting MCP client connection: serverName={}", mcpServerInfo.serverName());
        stopMcpClient(mcpServerInfo);
        this.connectingMcpClientOpsMap.remove(clientKey(mcpServerInfo));
    }

    public boolean isConnecting(McpServerInfo mcpServerInfo) {
        return this.connectingMcpClientOpsMap.containsKey(clientKey(mcpServerInfo));
    }

    private String clientKey(McpServerInfo mcpServerInfo) {
        return mcpServerInfo.mcpTransportType() + ":" + mcpServerInfo.serverName();
    }

    private NamedClientMcpTransport buildMcpClientTransport(McpServerInfo mcpServerInfo) {
        return new NamedClientMcpTransport(mcpServerInfo.serverName(),
                this.typeMcpClientPropertiesServiceMap.get(mcpServerInfo.mcpTransportType())
                        .buildClientTransport(this.objectMapper, mcpServerInfo.connectionAsJson()));
    }

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
