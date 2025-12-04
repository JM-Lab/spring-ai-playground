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
package jm.kr.spring.ai.playground.service.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jm.kr.spring.ai.playground.service.SharedDataReader;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientPropertiesService;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class McpServerInfoService implements SharedDataReader<List<McpServerInfo>>,
        ApplicationListener<WebServerInitializedEvent> {

    private final ObjectMapper objectMapper;
    private final Map<McpTransportType, Map<String, McpServerInfo>> typeMcpServerInfosMap;
    private final McpClientService mcpClientService;
    private final McpServerInfoPersistenceService mcpServerInfoPersistenceService;
    private final McpServerInfo defaultMcpServerInfo;

    public McpServerInfoService(ObjectMapper objectMapper, McpClientPropertiesService<?>[] mcpClientPropertiesServices,
            McpClientService mcpClientService, @Lazy McpServerInfoPersistenceService mcpServerInfoPersistenceService,
            @Value("${server.port:8080}") int serverPort, McpServerProperties mcpServerProperties) {
        this.objectMapper = objectMapper;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfoPersistenceService = mcpServerInfoPersistenceService;
        this.typeMcpServerInfosMap = Arrays.stream(mcpClientPropertiesServices)
                .collect(Collectors.toMap(McpClientPropertiesService::getTransportType,
                        mcpClientPropertiesService -> mcpClientPropertiesService.getDefaultConnections().entrySet()
                                .stream().map(entry -> Map.entry(entry.getKey(),
                                        buildMcpServerInfo(mcpClientPropertiesService.getTransportType(),
                                                entry.getKey(), entry.getValue())))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        this.defaultMcpServerInfo =
                buildMcpServerInfo(McpTransportType.STREAMABLE_HTTP, mcpServerProperties.getName(),
                        new McpStreamableHttpClientProperties.ConnectionParameters("http://localhost:" + serverPort,
                                "/mcp"));
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (Objects.nonNull(this.defaultMcpServerInfo)) {
            updateMcpServerInfo(defaultMcpServerInfo.mcpTransportType(), defaultMcpServerInfo.serverName(),
                    defaultMcpServerInfo);
            updateDefaultMcpTool();
        }
    }

    public McpServerInfo getDefaultMcpServerInfo() {
        return defaultMcpServerInfo;
    }

    private McpServerInfo buildMcpServerInfo(McpTransportType transportType, String serverName, Object connection) {
        try {
            long timestamp = System.currentTimeMillis();
            return new McpServerInfo(transportType, serverName, "[Default Connection] " + serverName,
                    timestamp, timestamp, transformAsJson(connection));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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

    public McpServerInfo createDefaultMcpServerInfo() {
        String defaultDescription = "Please edit the description of the MCP Server.";
        long timestamp = System.currentTimeMillis();
        return new McpServerInfo(McpTransportType.STDIO, "New MCP Server", defaultDescription, timestamp, timestamp,
                null);
    }

    public void deleteMcpServerInfo(McpTransportType transportType, String serverName) {
        McpServerInfo mcpServerInfo = this.typeMcpServerInfosMap.get(transportType).remove(serverName);
        if (mcpServerInfo != null)
            this.mcpServerInfoPersistenceService.delete(mcpServerInfo);
    }

    public McpServerInfo updateMcpServerInfo(McpTransportType transportType, String serverName,
            McpServerInfo updateMcpServerInfo) {
        if (this.typeMcpServerInfosMap.get(transportType).containsKey(updateMcpServerInfo.serverName()))
            throw new RuntimeException("MCP Server already exists with name " + updateMcpServerInfo.serverName());
        if (transportType.equals(updateMcpServerInfo.mcpTransportType()) &&
                serverName.equals(updateMcpServerInfo.serverName()))
            deleteMcpServerInfo(transportType, serverName);
        this.typeMcpServerInfosMap.get(updateMcpServerInfo.mcpTransportType())
                .put(updateMcpServerInfo.serverName(), updateMcpServerInfo);
        return updateMcpServerInfo;
    }

    @Override
    public List<McpServerInfo> read() {
        return getMcpServerInfos().values().stream().flatMap(List::stream).toList();
    }

    public void updateDefaultMcpTool() {
        this.mcpClientService.startMcpClient(this.defaultMcpServerInfo);
    }
}
