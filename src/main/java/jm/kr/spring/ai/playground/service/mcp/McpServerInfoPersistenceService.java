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

import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Service
public class McpServerInfoPersistenceService implements PersistenceServiceInterface<McpServerInfo>,
        ApplicationListener<WebServerInitializedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(McpServerInfoPersistenceService.class);

    private final Path saveDir;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final List<McpServerInfo> mcpServerInfos;

    public McpServerInfoPersistenceService(Path springAiPlaygroundHomeDir, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("mcp").resolve("save");
        Files.createDirectories(this.saveDir);
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfos = this.loads();
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
    public void buildSaveData(McpServerInfo mcpServerInfo, Map<String, Object> saveObjectMap) {

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
    public void onStart() throws IOException {
        this.mcpServerInfos.forEach(
                mcpServerInfo -> this.mcpServerInfoService.updateMcpServerInfo(mcpServerInfo.mcpTransportType(),
                        mcpServerInfo.serverName(), mcpServerInfo));
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.mcpServerInfos.parallelStream().forEach(mcpClientService::startMcpClient);
    }

    @Override
    public void onShutdown() throws IOException {
        for (McpServerInfo mcpServerInfo : this.mcpServerInfoService.getMcpServerInfos().values().stream()
                .flatMap(List::stream)
                .filter(Predicate.not(this.mcpServerInfoService.getDefaultMcpServerInfo()::equals)).toList())
            save(mcpServerInfo);
    }

}
