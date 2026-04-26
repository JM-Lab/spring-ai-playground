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
package org.springaicommunity.playground.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ToolSpecPersistenceService implements
        PersistenceServiceInterface<ToolSpecPersistenceService.ToolSpecsMcpServerSetting>,
        ApplicationListener<WebServerInitializedEvent> {

    public record ToolSpecsMcpServerSetting(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {}

    private static final Logger logger = LoggerFactory.getLogger(ToolSpecPersistenceService.class);

    private final Path saveDir;
    private final ToolSpecService toolSpecService;
    private final List<ToolSpec> defaultToolSpecs;
    private final List<ToolSpecsMcpServerSetting> toolSpecsMcpServerSettings;
    private final PersistenceExecutor persistenceExecutor;

    public ToolSpecPersistenceService(Path springAiPlaygroundHomeDir, ToolSpecService toolSpecService,
            @Value("${spring.application.default-tool-location:}")
            String defaultToolSpecsLocation, ObjectMapper objectMapper, ResourceLoader resourceLoader,
            PersistenceExecutor persistenceExecutor) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("tool").resolve("save");
        Files.createDirectories(this.saveDir);
        this.toolSpecService = toolSpecService;
        this.toolSpecsMcpServerSettings = this.loads();
        this.persistenceExecutor = persistenceExecutor;
        Resource resource = resourceLoader.getResource(defaultToolSpecsLocation);
        this.defaultToolSpecs = !defaultToolSpecsLocation.isBlank() && resource.exists() ?
                objectMapper.readValue(resource.getInputStream(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class)) : List.of();
    }

    public void saveAsync() {
        this.persistenceExecutor.submit(() -> {
            Set<String> toolIdSet = this.defaultToolSpecs.stream().map(ToolSpec::toolId).collect(Collectors.toSet());
            ToolSpecsMcpServerSetting snapshot = new ToolSpecsMcpServerSetting(
                    this.toolSpecService.getToolSpecList().stream()
                            .filter(toolSpec -> !toolIdSet.contains(toolSpec.toolId())).toList(),
                    this.toolSpecService.getToolMcpServerSetting());
            try {
                save(snapshot);
            } catch (IOException e) {
                logger.error("Async save failed for tool specs", e);
            }
        });
    }

    public Set<String> getDefaultToolIds() {
        return this.defaultToolSpecs.stream().map(ToolSpec::toolId).collect(Collectors.toSet());
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
    public String buildSaveFileName(ToolSpecsMcpServerSetting toolSpec) {
        return "toolSpecsMcpSetting";
    }

    @Override
    public ToolSpecsMcpServerSetting convertTo(Map<String, Object> saveObjectMap) {
        return OBJECT_MAPPER.convertValue(saveObjectMap, ToolSpecsMcpServerSetting.class);
    }

    @Override
    public void onStart() throws IOException {
        if (!toolSpecsMcpServerSettings.isEmpty())
            this.toolSpecService.setToolMcpServerSetting(toolSpecsMcpServerSettings.getFirst().toolMcpServerSetting());
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.toolSpecService.loadAll(() -> Stream.concat(defaultToolSpecs.stream(),
                        toolSpecsMcpServerSettings.stream().map(ToolSpecsMcpServerSetting::toolSpecs).flatMap(List::stream))
                .forEach(toolSpecService::update));
    }
}
