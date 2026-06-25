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
package org.springaicommunity.playground.service.chat;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatSystemPromptPresetServiceTest {

    @TempDir Path home;

    private ChatSystemPromptPresetCatalog catalog;
    private PersistenceExecutor executor;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() throws IOException {
        catalog = new ChatSystemPromptPresetCatalog();
        executor = new PersistenceExecutor();
        mapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.awaitCompletion(Duration.ofSeconds(5));
    }

    @Test
    void presetsAreBuiltInOnlyWhenNoSaveFile() throws IOException {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(service.userPresets()).isEmpty();
        assertThat(service.presets()).hasSize(catalog.presets().size());
    }

    @Test
    void saveAppendsUserPresetAndPersists() throws Exception {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        Preset saved = service.save("My Agent", "Do the thing.");
        assertThat(saved.id()).isEqualTo("user-my-agent");
        assertThat(service.userPresets()).containsExactly(saved);
        assertThat(service.presets()).contains(saved);

        executor.awaitCompletion(Duration.ofSeconds(2));
        assertThat(Files.exists(home.resolve("chat/save/system-prompt-presets.json"))).isTrue();
    }

    @Test
    void savedUserPresetReloadsFromFile() throws Exception {
        new ChatSystemPromptPresetService(home, executor, mapper, catalog)
                .save("Reload Me", "Persisted prompt.");
        executor.awaitCompletion(Duration.ofSeconds(2));

        ChatSystemPromptPresetService reader =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(reader.userPresets()).extracting(Preset::displayName).containsExactly("Reload Me");
        assertThat(reader.userPresets()).extracting(Preset::prompt).containsExactly("Persisted prompt.");
    }

    @Test
    void dynamicToolsPresetPersistsAndReloads() throws Exception {
        new ChatSystemPromptPresetService(home, executor, mapper, catalog)
                .save("Dyn Agent", "Discover tools.", ChatSystemPromptPresetCatalog.PresetKind.EXAMPLE,
                        List.of(), true);
        executor.awaitCompletion(Duration.ofSeconds(2));

        ChatSystemPromptPresetService reader =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(reader.userPresets()).extracting(Preset::dynamicTools).containsExactly(true);
        assertThat(reader.userPresets()).extracting(Preset::tools).containsExactly(List.of());
    }

    @Test
    void savingSameNameOverwritesExistingUserPreset() throws IOException {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        service.save("Agent", "first");
        Preset second = service.save("Agent", "second");
        assertThat(service.userPresets()).containsExactly(second);
        assertThat(second.prompt()).isEqualTo("second");
    }

    @Test
    void blankNameIsRejected() throws IOException {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThatThrownBy(() -> service.save("  ", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonAlphanumericNameStillProducesAStableId() throws IOException {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(service.save("!!!", "x").id()).isEqualTo("user-preset");
    }

    @Test
    void templateKindPersistsAndReloadsIntoTemplateLists() throws Exception {
        new ChatSystemPromptPresetService(home, executor, mapper, catalog)
                .save("My Tmpl", "Hi {{x}}", ChatSystemPromptPresetCatalog.PresetKind.TEMPLATE);
        executor.awaitCompletion(Duration.ofSeconds(2));

        ChatSystemPromptPresetService reader =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(reader.userTemplates()).extracting(Preset::id).containsExactly("user-my-tmpl");
        assertThat(reader.userExamples()).isEmpty();
        assertThat(reader.variableTemplates()).extracting(Preset::id).contains("domain-expert", "user-my-tmpl");
    }

    @Test
    void savedToolsPersistAndReload() throws Exception {
        new ChatSystemPromptPresetService(home, executor, mapper, catalog)
                .save("Tooled", "Use your tools.", ChatSystemPromptPresetCatalog.PresetKind.EXAMPLE,
                        List.of("getWeather", "getCurrentTime"));
        executor.awaitCompletion(Duration.ofSeconds(2));

        ChatSystemPromptPresetService reader =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        assertThat(reader.userExamples()).singleElement()
                .satisfies(preset -> assertThat(preset.tools()).containsExactly("getWeather", "getCurrentTime"));
    }

    @Test
    void deleteRemovesOnlyTheGivenUserPreset() throws IOException {
        ChatSystemPromptPresetService service =
                new ChatSystemPromptPresetService(home, executor, mapper, catalog);
        service.save("Keep", "a");
        service.save("Gone", "b");
        assertThat(service.delete("user-gone")).isTrue();
        assertThat(service.userPresets()).extracting(Preset::id).containsExactly("user-keep");
        assertThat(service.delete("user-gone")).isFalse();
    }
}
