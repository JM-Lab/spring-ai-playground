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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ToolWorkspaceBootstrap {

    private static final Logger log = LoggerFactory.getLogger(ToolWorkspaceBootstrap.class);
    private static final String SAMPLE_RESOURCES_PATTERN = "classpath*:tool/workspace-samples/*";

    private final ToolWorkspace toolWorkspace;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public ToolWorkspaceBootstrap(ToolWorkspace toolWorkspace) {
        this.toolWorkspace = toolWorkspace;
    }

    @PostConstruct
    public void seed() {
        Path basePath = toolWorkspace.base();
        migrateLegacyWorkspace(basePath);
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            log.warn("Failed to create tool workspace at {}: {}", basePath, e.getMessage());
            return;
        }
        int seeded = 0;
        try {
            Resource[] resources = resolver.getResources(SAMPLE_RESOURCES_PATTERN);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || filename.isBlank()) continue;
                Path dest = basePath.resolve(filename);
                if (Files.exists(dest)) continue;
                try (InputStream in = resource.getInputStream()) {
                    Files.copy(in, dest);
                    seeded++;
                    log.info("Seeded sample file: {}", dest);
                } catch (IOException e) {
                    log.warn("Failed to seed {}: {}", filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load sample resources for tool workspace: {}", e.getMessage());
        }
        if (seeded > 0) {
            log.info("Tool workspace bootstrap seeded {} sample file(s) at {}", seeded, basePath);
        }
    }

    private void migrateLegacyWorkspace(Path basePath) {
        if (!"workspace".equals(basePath.getFileName().toString())) return;
        Path legacy = basePath.resolveSibling("fs-tool-workspace");
        if (!Files.isDirectory(legacy) || Files.exists(basePath)) return;
        try {
            Files.move(legacy, basePath);
            log.info("Migrated tool workspace {} -> {}", legacy, basePath);
        } catch (IOException e) {
            log.warn("Failed to migrate {} -> {}: {}", legacy, basePath, e.getMessage());
        }
    }
}
