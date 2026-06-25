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
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolSpec.CodeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DefaultToolSpecMcpExposureTest {

    @Autowired
    ToolSpecService toolSpecService;

    @Autowired
    ToolSpecPersistenceService toolSpecPersistenceService;

    @MockitoBean
    McpServerInfoService mcpServerInfoService;

    private final List<String> registeredIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (String id : registeredIds) {
            try {
                toolSpecService.deleteToolSpec(id);
            } catch (Exception ignore) {}
        }
        registeredIds.clear();
    }

    @Test
    void everyDefaultToolFromShippedSpecFilesIsExposedToMcp() throws Exception {
        Set<String> shippedNames = new HashSet<>();
        Set<String> shippedIds = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String fname : List.of(
                "/tool/default-tool-specs.json",
                "/tool/default-tool-specs-builtin.json",
                "/tool/default-tool-specs-builtin-helpers.json",
                "/tool/default-tool-specs-builtin-fs.json",
                "/tool/default-tool-specs-network.json",
                "/tool/default-tool-specs-kr.json")) {
            try (InputStream in = DefaultToolSpecMcpExposureTest.class.getResourceAsStream(fname)) {
                if (in == null) continue;
                List<ToolSpec> batch = mapper.readValue(in,
                        mapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
                for (ToolSpec spec : batch) {
                    if (spec.draft()) continue;
                    toolSpecService.update(spec);
                    registeredIds.add(spec.toolId());
                    shippedNames.add(spec.name());
                    shippedIds.add(spec.toolId());
                }
            }
        }

        toolSpecService.setToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(true, shippedIds));
        toolSpecService.reconcileNativeExposure();

        Set<String> mcpExposed = toolSpecService.getMcpToolList().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());

        assertThat(mcpExposed)
                .as("every default tool must be visible via MCP tools/list")
                .containsAll(shippedNames);
    }

    @Value("${spring.ai.playground.tool-studio.spec-location:}")
    String resolvedLocation;

    @Autowired
    ResourceLoader injectedResourceLoader;

    @Test
    void resolvedLocationStringMatchesGlob() {
        assertThat(resolvedLocation).isEqualTo("classpath*:tool/default-tool-specs*.json");
    }

    @Test
    void resolverFindsAllShippedSpecFiles() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver(injectedResourceLoader)
                .getResources(resolvedLocation);
        Set<String> names = new HashSet<>();
        for (Resource r : resources) {
            if (r.getFilename() != null) names.add(r.getFilename());
        }
        assertThat(names).containsExactlyInAnyOrder(
                "default-tool-specs.json",
                "default-tool-specs-builtin.json",
                "default-tool-specs-builtin-helpers.json",
                "default-tool-specs-builtin-fs.json",
                "default-tool-specs-network.json",
                "default-tool-specs-kr.json");
    }

    @Test
    void globLoaderPicksUpEverySpecFile() throws Exception {
        Map<String, Integer> expectedCounts = Map.of(
                "/tool/default-tool-specs.json",                 9,
                "/tool/default-tool-specs-builtin.json",         16,
                "/tool/default-tool-specs-builtin-helpers.json", 10,
                "/tool/default-tool-specs-builtin-fs.json",      11,
                "/tool/default-tool-specs-network.json",         21,
                "/tool/default-tool-specs-kr.json",              21
        );
        int expectedTotal = expectedCounts.values().stream().mapToInt(Integer::intValue).sum();

        Set<String> loadedIds = toolSpecPersistenceService.getDefaultToolIds();
        assertThat(loadedIds.size())
                .as("glob must merge every default-tool-specs*.json file: expected %d, got %d",
                        expectedTotal, loadedIds.size())
                .isEqualTo(expectedTotal);

        Set<String> mustBeLoaded = collectAllShippedNames();
        Set<String> loadedNames = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String fname : expectedCounts.keySet()) {
            try (InputStream in = DefaultToolSpecMcpExposureTest.class.getResourceAsStream(fname)) {
                List<ToolSpec> batch = mapper.readValue(in,
                        mapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
                for (ToolSpec s : batch) {
                    if (loadedIds.contains(s.toolId())) loadedNames.add(s.name());
                }
            }
        }
        assertThat(loadedNames).containsAll(mustBeLoaded);
    }

    private static Set<String> collectAllShippedNames() throws Exception {
        Set<String> out = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String fname : List.of(
                "/tool/default-tool-specs.json",
                "/tool/default-tool-specs-builtin.json",
                "/tool/default-tool-specs-builtin-helpers.json",
                "/tool/default-tool-specs-builtin-fs.json",
                "/tool/default-tool-specs-network.json",
                "/tool/default-tool-specs-kr.json")) {
            try (InputStream in = DefaultToolSpecMcpExposureTest.class.getResourceAsStream(fname)) {
                List<ToolSpec> batch = mapper.readValue(in,
                        mapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
                for (ToolSpec s : batch) out.add(s.name());
            }
        }
        return out;
    }

    @Test
    void draftToolIsHiddenFromMcpEvenWhenRegistered() {
        ToolSpec draft = new ToolSpec("draft-x-exposure", "draftXTool", "desc",
                List.<Map.Entry<String, String>>of(),
                List.<ToolSpec.ToolParamSpec>of(),
                "return 1;", CodeType.Javascript, null).withDraft(true);
        toolSpecService.update(draft);
        registeredIds.add(draft.toolId());

        Set<String> mcpExposed = toolSpecService.getMcpToolList().stream()
                .map(McpSchema.Tool::name)
                .collect(Collectors.toSet());
        assertThat(mcpExposed).doesNotContain("draftXTool");
    }
}
