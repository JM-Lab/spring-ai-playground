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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

@Service
public class DefaultToolsPreferenceService {

    private static final Logger logger = LoggerFactory.getLogger(DefaultToolsPreferenceService.class);
    private static final String PREFERENCE_FILE_NAME = "default-tools-preference.json";

    private final Path preferenceFile;
    private final PersistenceExecutor persistenceExecutor;
    private final ObjectMapper objectMapper;
    private final DefaultToolPresetCatalog presetCatalog;
    private volatile DefaultToolsPreference current;

    public DefaultToolsPreferenceService(Path springAiPlaygroundHomeDir, PersistenceExecutor persistenceExecutor,
            ObjectMapper objectMapper, DefaultToolPresetCatalog presetCatalog,
            SpringAiPlaygroundOptions options) throws IOException {
        Path saveDir = springAiPlaygroundHomeDir.resolve("tool").resolve("save");
        Files.createDirectories(saveDir);
        this.preferenceFile = saveDir.resolve(PREFERENCE_FILE_NAME);
        this.persistenceExecutor = persistenceExecutor;
        this.objectMapper = objectMapper;
        this.presetCatalog = presetCatalog;
        DefaultToolsPreference fromCli = readCliOverride(options);
        this.current = fromCli != null ? fromCli : loadOrInitial();
    }

    private DefaultToolsPreference readCliOverride(SpringAiPlaygroundOptions options) {
        if (options == null) return null;
        SpringAiPlaygroundOptions.DefaultTools dt = options.defaultTools();
        if (dt == null) return null;
        boolean hasPreset = dt.preset() != null && !dt.preset().isBlank();
        boolean hasInclude = ruleNonEmpty(dt.include());
        boolean hasExclude = ruleNonEmpty(dt.exclude());
        if (!hasPreset && !hasInclude && !hasExclude) return null;
        logger.info("Default-tools preference overridden from CLI / Spring properties (preset={})", dt.preset());
        return new DefaultToolsPreference(3, dt.preset(),
                toRule(dt.include()), toRule(dt.exclude()));
    }

    private static boolean ruleNonEmpty(SpringAiPlaygroundOptions.SelectionRule r) {
        if (r == null) return false;
        return nonEmpty(r.names()) || nonEmpty(r.tags()) || nonEmpty(r.categories());
    }

    private static boolean nonEmpty(Set<String> s) {
        return s != null && !s.isEmpty();
    }

    private static DefaultToolsPreference.Rule toRule(SpringAiPlaygroundOptions.SelectionRule r) {
        if (r == null) return DefaultToolsPreference.Rule.empty();
        return new DefaultToolsPreference.Rule(r.names(), r.tags(), r.categories());
    }

    private DefaultToolsPreference loadOrInitial() {
        if (!Files.exists(preferenceFile)) {
            String defaultPresetId = presetCatalog.defaultPreset().id();
            logger.info("No preference file found at {} — booting with default preset '{}'",
                    preferenceFile, defaultPresetId);
            return DefaultToolsPreference.forPreset(defaultPresetId);
        }
        try {
            DefaultToolsPreference loaded =
                    objectMapper.readValue(preferenceFile.toFile(), DefaultToolsPreference.class);
            logger.info("Loaded default-tools preference from {} (preset={})", preferenceFile, loaded.preset());
            return loaded;
        } catch (IOException e) {
            logger.error("Failed to read {} — falling back to default preset", preferenceFile, e);
            return DefaultToolsPreference.forPreset(presetCatalog.defaultPreset().id());
        }
    }

    public DefaultToolsPreference current() {
        return this.current;
    }

    public void update(DefaultToolsPreference next) {
        this.current = next;
        saveAsync(next);
    }

    private void saveAsync(DefaultToolsPreference snapshot) {
        this.persistenceExecutor.submit(() -> {
            Path tmp = preferenceFile.resolveSibling(PREFERENCE_FILE_NAME + ".tmp");
            try {
                logger.info("Saving default-tools preference to file: {}", preferenceFile);
                objectMapper.writeValue(tmp.toFile(), snapshot);
                try {
                    Files.move(tmp, preferenceFile,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, preferenceFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                logger.error("Failed to save default-tools preference", e);
            }
        });
    }

    public Path getPreferenceFile() {
        return this.preferenceFile;
    }

    public boolean fileExists() {
        return Files.exists(preferenceFile);
    }
}
