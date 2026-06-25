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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.PresetKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ChatSystemPromptPresetService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSystemPromptPresetService.class);
    private static final String SAVE_FILE_NAME = "system-prompt-presets.json";
    private static final String USER_ID_PREFIX = "user-";

    private final Path saveFile;
    private final PersistenceExecutor persistenceExecutor;
    private final ObjectMapper objectMapper;
    private final ChatSystemPromptPresetCatalog catalog;
    private volatile List<Preset> userPresets;

    public ChatSystemPromptPresetService(Path springAiPlaygroundHomeDir, PersistenceExecutor persistenceExecutor,
            ObjectMapper objectMapper, ChatSystemPromptPresetCatalog catalog) throws IOException {
        Path saveDir = springAiPlaygroundHomeDir.resolve("chat").resolve("save");
        Files.createDirectories(saveDir);
        this.saveFile = saveDir.resolve(SAVE_FILE_NAME);
        this.persistenceExecutor = persistenceExecutor;
        this.objectMapper = objectMapper;
        this.catalog = catalog;
        this.userPresets = load();
    }

    private List<Preset> load() {
        if (!Files.exists(saveFile)) return List.of();
        try {
            List<Preset> loaded = objectMapper.readValue(saveFile.toFile(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Preset.class));
            logger.info("Loaded {} saved system-prompt presets from {}", loaded.size(), saveFile);
            return List.copyOf(loaded);
        } catch (JacksonException e) {
            logger.error("Failed to read {} — ignoring saved system-prompt presets", saveFile, e);
            return List.of();
        }
    }

    public List<Preset> presets() {
        List<Preset> all = new ArrayList<>(catalog.presets());
        all.addAll(userPresets);
        return List.copyOf(all);
    }

    public List<Preset> userPresets() {
        return userPresets;
    }

    public List<Preset> builtInExamples() {
        return catalog.presets().stream().filter(preset -> !preset.isTemplate()).toList();
    }

    public List<Preset> userExamples() {
        return userPresets.stream().filter(preset -> !preset.isTemplate()).toList();
    }

    public List<Preset> userTemplates() {
        return userPresets.stream().filter(Preset::isTemplate).toList();
    }

    public List<Preset> variableTemplates() {
        List<Preset> all = new ArrayList<>(catalog.presets().stream().filter(Preset::isTemplate).toList());
        all.addAll(userTemplates());
        return List.copyOf(all);
    }

    public boolean delete(String id) {
        List<Preset> next = userPresets.stream().filter(preset -> !preset.id().equals(id)).toList();
        if (next.size() == userPresets.size()) return false;
        this.userPresets = List.copyOf(next);
        saveAsync(this.userPresets);
        return true;
    }

    public Preset save(String displayName, String prompt) {
        return save(displayName, prompt, PresetKind.EXAMPLE, List.of());
    }

    public Preset save(String displayName, String prompt, PresetKind kind) {
        return save(displayName, prompt, kind, List.of());
    }

    public Preset save(String displayName, String prompt, PresetKind kind, List<String> tools) {
        return save(displayName, prompt, kind, tools, false);
    }

    public Preset save(String displayName, String prompt, PresetKind kind, List<String> tools,
            boolean dynamicTools) {
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("Preset name must not be blank");
        Preset preset = new Preset(userId(displayName), displayName.trim(), "", prompt, kind, tools, dynamicTools);
        List<Preset> next = new ArrayList<>(userPresets.size() + 1);
        for (Preset existing : userPresets) {
            if (!existing.id().equals(preset.id())) next.add(existing);
        }
        next.add(preset);
        this.userPresets = List.copyOf(next);
        saveAsync(this.userPresets);
        return preset;
    }

    private static String userId(String displayName) {
        String slug = displayName.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        return USER_ID_PREFIX + (slug.isEmpty() ? "preset" : slug);
    }

    private void saveAsync(List<Preset> snapshot) {
        this.persistenceExecutor.submit(() -> {
            Path tmp = saveFile.resolveSibling(SAVE_FILE_NAME + ".tmp");
            try {
                logger.info("Saving {} system-prompt presets to file: {}", snapshot.size(), saveFile);
                objectMapper.writeValue(tmp.toFile(), snapshot);
                try {
                    Files.move(tmp, saveFile,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, saveFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException | JacksonException e) {
                logger.error("Failed to save system-prompt presets", e);
            }
        });
    }

    public Path getSaveFile() {
        return this.saveFile;
    }
}
