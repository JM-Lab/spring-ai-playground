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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.DefaultTools;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.SelectionRule;
import org.springaicommunity.playground.service.PersistenceExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolsPreferenceServiceTest {

    @TempDir Path home;

    private DefaultToolPresetCatalog catalog;
    private PersistenceExecutor executor;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new DefaultToolPresetCatalog();
        executor = new PersistenceExecutor();
        mapper = new ObjectMapper();
    }

    @Test
    void absentFileFallsBackToStarter5() throws IOException {
        DefaultToolsPreferenceService service =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, null);
        assertThat(service.current().preset()).isEqualTo("starter-5");
        assertThat(service.fileExists()).isFalse();
    }

    @Test
    void updateWritesFileAtomicallyAndReloads() throws Exception {
        DefaultToolsPreferenceService writer =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, null);
        writer.update(DefaultToolsPreference.forPreset("korea-toolkit"));
        executor.awaitCompletion(Duration.ofSeconds(2));

        Path file = home.resolve("tool/save/default-tools-preference.json");
        assertThat(Files.exists(file)).isTrue();

        DefaultToolsPreferenceService reader =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, null);
        assertThat(reader.current().preset()).isEqualTo("korea-toolkit");
    }

    @Test
    void cliPresetOverridesFile() throws Exception {
        new DefaultToolsPreferenceService(home, executor, mapper, catalog, null)
                .update(DefaultToolsPreference.forPreset("file-toolkit"));
        executor.awaitCompletion(Duration.ofSeconds(2));

        DefaultTools cli = new DefaultTools("korea-toolkit", null, null);
        SpringAiPlaygroundOptions options = optionsWithDefaultTools(cli);
        DefaultToolsPreferenceService cliService =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, options);
        assertThat(cliService.current().preset()).isEqualTo("korea-toolkit");
    }

    @Test
    void cliIncludeRuleOverridesEvenWithoutPreset() throws IOException {
        DefaultTools cli = new DefaultTools(null,
                new SelectionRule(Set.of(), Set.of("github"), Set.of()),
                null);
        SpringAiPlaygroundOptions options = optionsWithDefaultTools(cli);
        DefaultToolsPreferenceService service =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, options);

        assertThat(service.current().preset()).isNull();
        assertThat(service.current().include().tags()).containsExactly("github");
    }

    @Test
    void cliOverrideDoesNotPersistToFile() throws Exception {
        DefaultTools cli = new DefaultTools("dev-essentials", null, null);
        SpringAiPlaygroundOptions options = optionsWithDefaultTools(cli);
        new DefaultToolsPreferenceService(home, executor, mapper, catalog, options);

        executor.awaitCompletion(Duration.ofSeconds(2));
        Path file = home.resolve("tool/save/default-tools-preference.json");
        assertThat(Files.exists(file))
                .as("CLI override is transient — must not write the preference file")
                .isFalse();
    }

    @Test
    void emptyCliOptionsFallThroughToFileOrDefault() throws IOException {
        DefaultTools cli = new DefaultTools(null, null, null);
        SpringAiPlaygroundOptions options = optionsWithDefaultTools(cli);
        DefaultToolsPreferenceService service =
                new DefaultToolsPreferenceService(home, executor, mapper, catalog, options);
        assertThat(service.current().preset()).isEqualTo("starter-5");
    }

    private static SpringAiPlaygroundOptions optionsWithDefaultTools(DefaultTools dt) {
        return new SpringAiPlaygroundOptions(
                new SpringAiPlaygroundOptions.ToolStudio(null, null, null, dt), false, null, null, null, null);
    }
}
