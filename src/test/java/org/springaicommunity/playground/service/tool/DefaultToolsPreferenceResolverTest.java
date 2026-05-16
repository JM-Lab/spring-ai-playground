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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.DefaultToolPresetCatalog.Preset;
import org.springaicommunity.playground.service.tool.DefaultToolsPreference.Rule;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolsPreferenceResolverTest {

    private static DefaultToolPresetCatalog catalog;
    private static DefaultToolsPreferenceResolver resolver;
    private static List<ToolSpec> defaults;

    @BeforeAll
    static void setUp() throws IOException {
        catalog = new DefaultToolPresetCatalog(List.of(
                new Preset("starter-5", "Starter 5", "min set", true,
                        List.of("getCurrentTime", "getWeather", "searchWikipedia",
                                "extractPageContent", "evalExpression")),
                new Preset("everything", "Everything", "all", false, List.of())
        ));
        resolver = new DefaultToolsPreferenceResolver(catalog);
        defaults = List.of(
                fixture("getCurrentTime", "DATETIME", Set.of("util", "example")),
                fixture("getWeather", "WEB", Set.of("weather", "example")),
                fixture("searchWikipedia", "WEB", Set.of("search")),
                fixture("extractPageContent", "WEB", Set.of("example")),
                fixture("evalExpression", "MATH", Set.of("util")),
                fixture("uuid", "CRYPTO", Set.of("util")),
                fixture("hash", "CRYPTO", Set.of("util")),
                fixture("getGithubRepo", "WEB", Set.of("github")),
                fixture("searchNaver", "WEB", Set.of("korea", "search")),
                fixture("readTextFile", "FILE", Set.of("pipeline")),
                fixture("sendSlackMessage", "MESSAGING", Set.of("example"))
        );
    }

    @Test
    void presetOnlyResolvesPresetTools() {
        DefaultToolsPreference pref = DefaultToolsPreference.forPreset("starter-5");
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .containsExactlyInAnyOrder("getCurrentTime", "getWeather", "searchWikipedia",
                        "extractPageContent", "evalExpression");
    }

    @Test
    void everythingPresetResolvesAllDefaults() {
        DefaultToolsPreference pref = DefaultToolsPreference.forPreset("everything");
        Set<String> active = resolver.resolveActiveNames(pref, defaults);
        assertThat(active).hasSize(defaults.size());
    }

    @Test
    void includeByNameAddsTools() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, "starter-5",
                new Rule(Set.of("uuid", "hash"), Set.of(), Set.of()),
                Rule.empty());
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .contains("uuid", "hash", "getCurrentTime");
    }

    @Test
    void includeByTagPullsInMatching() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, null,
                new Rule(Set.of(), Set.of("github"), Set.of()),
                Rule.empty());
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .containsExactly("getGithubRepo");
    }

    @Test
    void includeByCategoryPullsInWholeCategory() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, null,
                new Rule(Set.of(), Set.of(), Set.of("CRYPTO")),
                Rule.empty());
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .containsExactlyInAnyOrder("uuid", "hash");
    }

    @Test
    void excludeByNameSubtractsFromPreset() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, "starter-5",
                Rule.empty(),
                new Rule(Set.of("getWeather"), Set.of(), Set.of()));
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .doesNotContain("getWeather")
                .contains("getCurrentTime", "searchWikipedia", "extractPageContent", "evalExpression");
    }

    @Test
    void excludeByTagWins_over_includeByName() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, null,
                new Rule(Set.of("searchNaver"), Set.of(), Set.of()),
                new Rule(Set.of(), Set.of("korea"), Set.of()));
        assertThat(resolver.resolveActiveNames(pref, defaults))
                .doesNotContain("searchNaver");
    }

    @Test
    void everythingMinusKoreaAndPipeline() {
        DefaultToolsPreference pref = new DefaultToolsPreference(3, "everything",
                Rule.empty(),
                new Rule(Set.of(), Set.of("korea", "pipeline"), Set.of()));
        Set<String> active = resolver.resolveActiveNames(pref, defaults);
        assertThat(active)
                .doesNotContain("searchNaver", "readTextFile")
                .contains("getCurrentTime", "uuid", "getGithubRepo");
    }

    private static ToolSpec fixture(String name, String category, Set<String> tags) {
        ToolSpec spec = new ToolSpec(name + "-id", name, "", List.of(), List.of(),
                "", ToolSpec.CodeType.Javascript, null);
        spec.withCategory(category).withTags(tags);
        return spec;
    }
}
