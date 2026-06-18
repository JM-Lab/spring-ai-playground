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
package org.springaicommunity.playground.service.mcp.risk;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.tool.DefaultToolsPreferenceResolver;
import org.springaicommunity.playground.service.tool.DefaultToolsPreferenceService;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DefaultIntegrityManifestTest {

    private static final String SPEC_LOCATION = "classpath*:tool/default-tool-specs*.json";
    private static final String MANIFEST_RESOURCE = "integrity/default-integrity.json";
    private static final Path MANIFEST_SOURCE_PATH =
            Path.of("src/main/resources/integrity/default-integrity.json");

    @TempDir
    Path home;

    private Map<String, String> computeRuntimeToolHashes() throws IOException {
        PersistenceExecutor executor = new PersistenceExecutor();
        try {
            ToolSpecPersistenceService persistence = new ToolSpecPersistenceService(home,
                    mock(ToolSpecService.class), SPEC_LOCATION, new ObjectMapper(),
                    new PathMatchingResourcePatternResolver(), executor,
                    mock(DefaultToolsPreferenceService.class), mock(DefaultToolsPreferenceResolver.class),
                    new ToolActivationCalculator());
            CanonicalHasher hasher = new CanonicalHasher(new ObjectMapper());
            Map<String, String> hashes = new TreeMap<>();
            for (ToolSpec spec : persistence.getDefaultToolSpecs()) {
                if (spec == null || spec.name() == null || spec.name().isBlank()) continue;
                String previous = hashes.put(spec.name(), hasher.hashTool(spec));
                assertNull(previous, "two shipped default tools share the name '" + spec.name()
                        + "' — reserved names must be unique");
            }
            return hashes;
        } finally {
            executor.flushAndShutdown();
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "integrity.generate", matches = "true")
    void regenerateManifest() throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", 1);
        manifest.put("tools", computeRuntimeToolHashes());
        ObjectMapper writer = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true).build();
        Files.createDirectories(MANIFEST_SOURCE_PATH.getParent());
        writer.writeValue(MANIFEST_SOURCE_PATH.toFile(), manifest);
    }

    @Test
    void runtimeBaselineMatchesCommittedManifest() throws IOException {
        ClassPathResource resource = new ClassPathResource(MANIFEST_RESOURCE);
        assertTrue(resource.exists(),
                "committed integrity manifest is missing — regenerate with -Dintegrity.generate=true");
        Map<String, String> committed = new TreeMap<>();
        try (var in = resource.getInputStream()) {
            JsonNode tools = new ObjectMapper().readTree(in).path("tools");
            tools.properties().forEach(e -> committed.put(e.getKey(), e.getValue().asText()));
        }
        assertEquals(computeRuntimeToolHashes(), committed,
                "shipped default tool hashes drifted from the committed integrity manifest — a default tool's "
                        + "canonical content changed; if intentional, regenerate with -Dintegrity.generate=true");
    }
}
