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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.DefaultToolPresetCatalog.Preset;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultToolPresetCatalogTest {

    @Test
    void loadsFiveShippedPresets() throws IOException {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog();
        assertThat(catalog.presets()).hasSize(5);
        assertThat(catalog.presets()).extracting(Preset::id)
                .containsExactly("starter-5", "dev-essentials", "korea-toolkit", "file-toolkit", "everything");
    }

    @Test
    void starter5IsTheDefaultPreset() throws IOException {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog();
        assertThat(catalog.defaultPreset().id()).isEqualTo("starter-5");
    }

    @Test
    void findByIdReturnsPresetOrEmpty() throws IOException {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog();
        assertThat(catalog.findById("korea-toolkit")).isPresent();
        assertThat(catalog.findById("nope")).isEmpty();
        assertThat(catalog.findById(null)).isEmpty();
    }

    @Test
    void starter5BundlesTheFiveStarterTools() throws IOException {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog();
        Preset starter = catalog.findById("starter-5").orElseThrow();
        assertThat(starter.tools()).containsExactlyInAnyOrder(
                "getCurrentTime", "getWeather", "searchWikipedia",
                "extractPageContent", "evalExpression");
    }

    @Test
    void everythingPresetCarriesEmptyToolListAsAllMarker() throws IOException {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog();
        Preset everything = catalog.findById("everything").orElseThrow();
        assertThat(everything.tools()).isEmpty();
    }

    @Test
    void emptyCatalogWithoutDefaultThrowsOnDefaultPreset() {
        DefaultToolPresetCatalog catalog = new DefaultToolPresetCatalog(
                List.of(new Preset("alt", "Alt", "—", false, List.of("foo"))));
        assertThatThrownBy(catalog::defaultPreset)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No default preset");
    }
}
