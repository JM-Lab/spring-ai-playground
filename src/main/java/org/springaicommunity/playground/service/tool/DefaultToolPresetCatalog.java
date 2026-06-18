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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Component
public class DefaultToolPresetCatalog {

    public record Preset(String id, String displayName, String description, boolean isDefault, List<String> tools) {
        public Preset {
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(DefaultToolPresetCatalog.class);
    private static final String RESOURCE_PATH = "tool/default-tool-presets.json";

    private final List<Preset> presets;

    public DefaultToolPresetCatalog() throws IOException {
        this(loadFromClasspath());
    }

    DefaultToolPresetCatalog(List<Preset> presets) {
        this.presets = List.copyOf(presets);
    }

    private static List<Preset> loadFromClasspath() throws IOException {
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            ObjectMapper mapper = new ObjectMapper();
            List<Preset> loaded = mapper.readValue(in,
                    mapper.getTypeFactory().constructCollectionType(List.class, Preset.class));
            logger.info("Loaded {} default-tool presets", loaded.size());
            return loaded;
        }
    }

    public List<Preset> presets() {
        return presets;
    }

    public Optional<Preset> findById(String id) {
        if (id == null) return Optional.empty();
        return presets.stream().filter(p -> id.equals(p.id())).findFirst();
    }

    public Preset defaultPreset() {
        return presets.stream().filter(Preset::isDefault).findFirst()
                .orElseThrow(() -> new IllegalStateException("No default preset declared in " + RESOURCE_PATH));
    }
}
