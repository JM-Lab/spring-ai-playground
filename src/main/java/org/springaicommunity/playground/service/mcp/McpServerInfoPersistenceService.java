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

import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class McpServerInfoPersistenceService implements PersistenceServiceInterface<McpServerInfo>,
        ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpServerInfoPersistenceService.class);

    private final Path saveDir;
    private final ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider;
    private final McpClientService mcpClientService;
    private final List<McpServerInfo> mcpServerInfos;
    private final PersistenceExecutor persistenceExecutor;

    public McpServerInfoPersistenceService(Path springAiPlaygroundHomeDir,
            ObjectProvider<McpServerInfoService> mcpServerInfoServiceProvider,
            McpClientService mcpClientService, PersistenceExecutor persistenceExecutor) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("mcp").resolve("save");
        Files.createDirectories(this.saveDir);
        this.mcpServerInfoServiceProvider = mcpServerInfoServiceProvider;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfos = this.loads();
        this.persistenceExecutor = persistenceExecutor;
    }

    public void saveAsync(McpServerInfo mcpServerInfo) {
        this.persistenceExecutor.submit(() -> {
            try {
                save(mcpServerInfo);
            } catch (IOException e) {
                logger.error("Async save failed for MCP server info {}", mcpServerInfo.serverName(), e);
            }
        });
    }

    public void deleteAsync(McpServerInfo mcpServerInfo) {
        this.persistenceExecutor.submit(() -> delete(mcpServerInfo));
    }

    @Override
    public Path getSaveDir() {
        return this.saveDir;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public String buildSaveFileName(McpServerInfo mcpServerInfo) {
        return mcpServerInfo.mcpTransportType() + "-" + mcpServerInfo.serverName();
    }

    @Override
    public McpServerInfo convertTo(Map<String, Object> saveObjectMap) {
        McpTransportType mcpTransportType = McpTransportType.valueOf((String) saveObjectMap.get("mcpTransportType"));
        String serverName = (String) saveObjectMap.get("serverName");
        String description = (String) saveObjectMap.get("description");
        long createTimestamp = ((Number) saveObjectMap.get("createTimestamp")).longValue();
        long updateTimestamp = ((Number) saveObjectMap.get("updateTimestamp")).longValue();
        String connectionAsJson = (String) saveObjectMap.get("connectionAsJson");
        return new McpServerInfo(mcpTransportType, serverName, description, createTimestamp, updateTimestamp,
                connectionAsJson);
    }

    @Override
    public void onStart() {
        McpServerInfoService mcpServerInfoService = this.mcpServerInfoServiceProvider.getObject();
        mcpServerInfoService.loadAll(() -> this.mcpServerInfos.forEach(
                mcpServerInfo -> mcpServerInfoService.updateMcpServerInfo(mcpServerInfo.mcpTransportType(),
                        mcpServerInfo.serverName(), mcpServerInfo)));
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.mcpServerInfos.forEach(mcpServerInfo -> {
            try {
                mcpClientService.startMcpClient(mcpServerInfo);
            } catch (RuntimeException e) {
                logger.error("Failed to start MCP client: serverName={}, transportType={}",
                        mcpServerInfo.serverName(), mcpServerInfo.mcpTransportType(), e);
            }
        });
    }

}
