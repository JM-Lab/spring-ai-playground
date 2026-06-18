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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSystemPromptPresetCatalogTest {

    @Test
    void loadsShippedPresetsInOrder() throws IOException {
        ChatSystemPromptPresetCatalog catalog = new ChatSystemPromptPresetCatalog();
        assertThat(catalog.presets()).extracting(Preset::id)
                .containsExactly("skill-agent", "domain-expert", "custom-role", "structured-output",
                        "research-brief", "summarizer", "translator", "socratic-tutor", "decision-matrix",
                        "general-assistant", "coding-agent", "research-agent", "tool-using-agent",
                        "data-wrangler", "korea-concierge", "github-repo-analyst",
                        "release-notes-writer", "log-detective", "crypto-market-watch",
                        "trip-planner", "tech-pulse");
    }

    @Test
    void everyPresetCarriesANonBlankPrompt() throws IOException {
        ChatSystemPromptPresetCatalog catalog = new ChatSystemPromptPresetCatalog();
        assertThat(catalog.presets()).allSatisfy(preset -> assertThat(preset.prompt()).isNotBlank());
    }

    @Test
    void findByIdReturnsPresetOrEmpty() throws IOException {
        ChatSystemPromptPresetCatalog catalog = new ChatSystemPromptPresetCatalog();
        assertThat(catalog.findById("coding-agent")).isPresent();
        assertThat(catalog.findById("nope")).isEmpty();
        assertThat(catalog.findById(null)).isEmpty();
    }

    @Test
    void nullPromptIsNormalizedToEmpty() {
        assertThat(new Preset("x", "X", "desc", null, null, null).prompt()).isEmpty();
        assertThat(new Preset("x", "X", "desc", null, null, null).tools()).isEmpty();
    }

    @Test
    void presetsCarryTheirRequiredTools() throws IOException {
        ChatSystemPromptPresetCatalog catalog = new ChatSystemPromptPresetCatalog();
        assertThat(catalog.findById("log-detective").orElseThrow().tools())
                .contains("findFiles", "grepFile", "sliceFile", "stats");
        assertThat(catalog.findById("skill-agent").orElseThrow().tools()).isEmpty();
    }
}
