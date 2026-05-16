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
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ToolSpecPersistenceService implements
        PersistenceServiceInterface<ToolSpecPersistenceService.ToolSpecsMcpServerSetting>,
        ApplicationListener<WebServerInitializedEvent> {

    public record ToolSpecsMcpServerSetting(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {}

    private static final String SAVE_FILE_NAME = "toolSpecsMcpSetting.json";
    private static final String LEGACY_OVERRIDES_FILE_NAME = "defaultToolOverrides.json";

    private static final Logger logger = LoggerFactory.getLogger(ToolSpecPersistenceService.class);

    private final Path saveDir;
    private final ToolSpecService toolSpecService;
    private final List<ToolSpec> defaultToolSpecs;
    private final List<ToolSpecsMcpServerSetting> toolSpecsMcpServerSettings;
    private final PersistenceExecutor persistenceExecutor;
    private final Set<String> defaultToolIds;
    private final DefaultToolsPreferenceService preferenceService;
    private final DefaultToolsPreferenceResolver preferenceResolver;

    public ToolSpecPersistenceService(Path springAiPlaygroundHomeDir, ToolSpecService toolSpecService,
            @Value("${spring.ai.playground.default-tool-location:}")
            String defaultToolSpecsLocation, ObjectMapper objectMapper, ResourceLoader resourceLoader,
            PersistenceExecutor persistenceExecutor,
            DefaultToolsPreferenceService preferenceService,
            DefaultToolsPreferenceResolver preferenceResolver) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("tool").resolve("save");
        Files.createDirectories(this.saveDir);
        this.toolSpecService = toolSpecService;
        this.toolSpecsMcpServerSettings = this.loads();
        this.persistenceExecutor = persistenceExecutor;
        this.defaultToolSpecs = loadDefaultToolSpecs(defaultToolSpecsLocation, objectMapper, resourceLoader);
        this.defaultToolIds = this.defaultToolSpecs.stream().map(ToolSpec::toolId)
                .filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        this.preferenceService = preferenceService;
        this.preferenceResolver = preferenceResolver;
        archiveLegacyOverridesFile();
    }

    private static List<ToolSpec> loadDefaultToolSpecs(String location, ObjectMapper objectMapper,
            ResourceLoader resourceLoader) throws IOException {
        if (location == null || location.isBlank()) return List.of();
        Resource[] resources = location.contains("*")
                ? new PathMatchingResourcePatternResolver(resourceLoader).getResources(location)
                : new Resource[] { resourceLoader.getResource(location) };
        Arrays.sort(resources, Comparator.comparing(r -> {
            try { return r.getFilename() == null ? "" : r.getFilename(); }
            catch (Exception e) { return ""; }
        }));
        List<ToolSpec> merged = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Resource resource : resources) {
            if (!resource.exists()) continue;
            List<ToolSpec> batch = objectMapper.readValue(resource.getInputStream(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
            for (ToolSpec spec : batch) {
                if (spec.toolId() != null && !seenIds.add(spec.toolId())) {
                    logger.warn("Duplicate default tool id {} from {} — skipping", spec.toolId(),
                            resource.getFilename());
                    continue;
                }
                merged.add(spec);
            }
        }
        return List.copyOf(merged);
    }

    private void archiveLegacyOverridesFile() {
        Path legacy = saveDir.resolve(LEGACY_OVERRIDES_FILE_NAME);
        if (!Files.exists(legacy)) return;
        Path archived = saveDir.resolve(LEGACY_OVERRIDES_FILE_NAME + ".deprecated");
        try {
            Files.move(legacy, archived, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            logger.info("Archived legacy overrides file {} → {}", legacy, archived);
        } catch (IOException e) {
            logger.warn("Could not archive legacy overrides file {}", legacy, e);
        }
    }

    public void saveAsync() {
        this.persistenceExecutor.submit(() -> {
            ToolSpecsMcpServerSetting snapshot = new ToolSpecsMcpServerSetting(
                    this.toolSpecService.getToolSpecList().stream()
                            .filter(toolSpec -> !this.defaultToolIds.contains(toolSpec.toolId())).toList(),
                    this.toolSpecService.getToolMcpServerSetting());
            try {
                save(snapshot);
            } catch (IOException e) {
                logger.error("Async save failed for tool specs", e);
            }
        });
    }

    public Set<String> getDefaultToolIds() {
        return this.defaultToolIds;
    }

    public List<ToolSpec> getDefaultToolSpecs() {
        return this.defaultToolSpecs;
    }

    public DefaultToolsPreferenceService getPreferenceService() {
        return this.preferenceService;
    }

    public void applyPreference(DefaultToolsPreference next) {
        preferenceService.update(next);
        Set<String> active = preferenceResolver.resolveActiveNames(next, defaultToolSpecs);
        for (ToolSpec spec : defaultToolSpecs) {
            boolean shouldBeDraft = !active.contains(spec.name());
            if (spec.draft() != shouldBeDraft) {
                spec.withDraft(shouldBeDraft);
                toolSpecService.update(spec);
            }
        }
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
    public boolean shouldLoadFile(Path path) {
        return SAVE_FILE_NAME.equals(path.getFileName().toString());
    }

    @Override
    public ToolSpecsMcpServerSetting convertTo(Map<String, Object> saveObjectMap) {
        return OBJECT_MAPPER.convertValue(saveObjectMap, ToolSpecsMcpServerSetting.class);
    }

    @Override
    public void onStart() throws IOException {
        toolSpecsMcpServerSettings.stream().findFirst()
                .map(ToolSpecsMcpServerSetting::toolMcpServerSetting)
                .filter(Objects::nonNull)
                .ifPresent(this.toolSpecService::setToolMcpServerSetting);
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        DefaultToolsPreference preference = preferenceService.current();
        Set<String> active = preferenceResolver.resolveActiveNames(preference, defaultToolSpecs);
        for (ToolSpec spec : this.defaultToolSpecs) {
            spec.withDraft(!active.contains(spec.name()));
        }
        this.toolSpecService.loadAll(() -> Stream.concat(defaultToolSpecs.stream(),
                        toolSpecsMcpServerSettings.stream().map(ToolSpecsMcpServerSetting::toolSpecs)
                                .filter(Objects::nonNull).flatMap(List::stream))
                .forEach(toolSpecService::update));
    }
}
