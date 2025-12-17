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
package jm.kr.spring.ai.playground.service.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import jm.kr.spring.ai.playground.service.tool.ToolSpecService.ToolMcpServerSetting;
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

    public ToolSpecPersistenceService(Path springAiPlaygroundHomeDir, ToolSpecService toolSpecService,
            @Value("${spring.application.default-tool-location:}")
            String defaultToolSpecsLocation, ObjectMapper objectMapper, ResourceLoader resourceLoader) throws
            IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("tool").resolve("save");
        Files.createDirectories(this.saveDir);
        this.toolSpecService = toolSpecService;
        this.toolSpecsMcpServerSettings = this.loads();
        Resource resource = resourceLoader.getResource(defaultToolSpecsLocation);
        this.defaultToolSpecs = !defaultToolSpecsLocation.isBlank() && resource.exists() ?
                objectMapper.readValue(resource.getInputStream(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class)) : List.of();
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
    public void buildSaveData(ToolSpecsMcpServerSetting toolSpec, Map<String, Object> saveObjectMap) {

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
        Stream.concat(defaultToolSpecs.stream(),
                        toolSpecsMcpServerSettings.stream().map(ToolSpecsMcpServerSetting::toolSpecs).flatMap(List::stream))
                .forEach(toolSpecService::update);
    }

    @Override
    public void onShutdown() throws IOException {
        Set<String> toolIdSet = this.defaultToolSpecs.stream().map(ToolSpec::toolId).collect(Collectors.toSet());
        save(new ToolSpecsMcpServerSetting(this.toolSpecService.getToolSpecList().stream()
                .filter(toolSpec -> !toolIdSet.contains(toolSpec.toolId())).toList(),
                this.toolSpecService.getToolMcpServerSetting()));
    }
}
