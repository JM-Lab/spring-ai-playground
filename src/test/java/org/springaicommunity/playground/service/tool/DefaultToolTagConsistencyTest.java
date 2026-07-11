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
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolTagConsistencyTest {

    private static final List<String> SHIPPED_FILES = List.of(
            "/tool/default-tool-specs.json",
            "/tool/default-tool-specs-builtin.json",
            "/tool/default-tool-specs-builtin-helpers.json",
            "/tool/default-tool-specs-builtin-fs.json",
            "/tool/default-tool-specs-network.json",
            "/tool/default-tool-specs-kr.json");

    private static final Set<String> TAG_VOCAB = Set.of(
            "korea", "example", "util", "pipeline",
            "github", "search", "finance", "weather", "geo", "visualization");

    private static final Set<String> WEB_ONLY_TAGS = Set.of("korea", "github", "search", "finance", "weather", "geo");

    @Test
    void everyTagBelongsToTheVocabulary() throws Exception {
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            for (String tag : spec.tags()) {
                if (!TAG_VOCAB.contains(tag)) {
                    violations.add(spec.name() + " tag=" + tag);
                }
            }
        }
        assertThat(violations)
                .as("tags must come from the v4 vocab {korea, example, util, pipeline, github, search, finance, weather, geo, visualization}")
                .isEmpty();
    }

    @Test
    void noSpecCarriesMoreThanTwoTags() throws Exception {
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            if (spec.tags().size() > 2) {
                violations.add(spec.name() + " tags=" + spec.tags());
            }
        }
        assertThat(violations)
                .as("each spec carries at most 2 tags")
                .isEmpty();
    }

    @Test
    void webDomainTagsLiveUnderWebCategory() throws Exception {
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            for (String tag : spec.tags()) {
                if (WEB_ONLY_TAGS.contains(tag) && !"WEB".equals(spec.category())) {
                    violations.add(spec.name() + " tag=" + tag + " category=" + spec.category());
                }
            }
        }
        assertThat(violations)
                .as("tags {korea, github, search, finance, weather, geo} require category=WEB")
                .isEmpty();
    }

    @Test
    void pipelineTagLivesUnderFileCategory() throws Exception {
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            if (!spec.tags().contains("pipeline")) continue;
            if (!"FILE".equals(spec.category())) {
                violations.add(spec.name() + " category=" + spec.category());
            }
        }
        assertThat(violations)
                .as("'pipeline' tag is reserved for FILE-category composable tools")
                .isEmpty();
    }

    @Test
    void utilTagAvoidsWebAndFileCategories() throws Exception {
        Set<String> forbidden = Set.of("WEB", "FILE");
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            if (!spec.tags().contains("util")) continue;
            if (forbidden.contains(spec.category())) {
                violations.add(spec.name() + " category=" + spec.category());
            }
        }
        assertThat(violations)
                .as("'util' tag marks local pure-compute — must not sit under WEB or FILE")
                .isEmpty();
    }

    @Test
    void allCategoryValuesMatchCatalog() throws Exception {
        Set<String> known = new ToolCategoryCatalog().categoryIds();
        List<String> violations = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            String cat = spec.category();
            if (cat == null || !known.contains(cat)) {
                violations.add(spec.name() + " category=" + cat);
            }
        }
        assertThat(violations)
                .as("every default tool's category must exist in default-tool-categories.json")
                .isEmpty();
    }

    @Test
    void toolIdsAreUniqueAcrossAllShippedFiles() throws Exception {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (ToolSpec spec : loadAllShipped()) {
            if (!seen.add(spec.toolId())) {
                duplicates.add(spec.toolId() + " (" + spec.name() + ")");
            }
        }
        assertThat(duplicates)
                .as("toolId must be unique across every shipped default-tool-specs*.json file")
                .isEmpty();
    }

    private static List<ToolSpec> loadAllShipped() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<ToolSpec> out = new ArrayList<>();
        for (String fname : SHIPPED_FILES) {
            try (InputStream in = DefaultToolTagConsistencyTest.class.getResourceAsStream(fname)) {
                if (in == null) continue;
                List<ToolSpec> batch = mapper.readValue(in,
                        mapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
                out.addAll(batch);
            }
        }
        return out;
    }
}
