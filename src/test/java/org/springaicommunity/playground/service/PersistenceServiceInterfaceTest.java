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
package org.springaicommunity.playground.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceServiceInterfaceTest {

    @TempDir Path saveDir;

    private PersistenceServiceInterface<Map<String, Object>> newService() {
        return new PersistenceServiceInterface<>() {
            @Override
            public Path getSaveDir() {
                return saveDir;
            }

            @Override
            public Logger getLogger() {
                return LoggerFactory.getLogger(PersistenceServiceInterfaceTest.class);
            }

            @Override
            public String buildSaveFileName(Map<String, Object> saveObject) {
                return "ignored";
            }

            @Override
            public Map<String, Object> convertTo(Map<String, Object> saveObjectMap) {
                return saveObjectMap;
            }

            @Override
            public void onStart() { }
        };
    }

    @Test
    void shouldLoadFileExcludesTmpAndDeprecatedAndNonJson() {
        PersistenceServiceInterface<?> service = newService();
        assertThat(service.shouldLoadFile(saveDir.resolve("data.json"))).isTrue();
        assertThat(service.shouldLoadFile(saveDir.resolve("data.json.tmp"))).isFalse();
        assertThat(service.shouldLoadFile(saveDir.resolve("data.tmp.json"))).isFalse();
        assertThat(service.shouldLoadFile(saveDir.resolve("data.json.deprecated"))).isFalse();
        assertThat(service.shouldLoadFile(saveDir.resolve("notes.txt"))).isFalse();
        assertThat(service.shouldLoadFile(saveDir.resolve(".hidden.json"))).isTrue();
    }

    @Test
    void loadsIgnoresTmpAndDeprecatedFiles() throws IOException {
        Files.writeString(saveDir.resolve("good.json"), "{\"k\":\"v\"}");
        Files.writeString(saveDir.resolve("partial.json.tmp"), "{ corrupt");
        Files.writeString(saveDir.resolve("partial.tmp.json"), "{ corrupt");
        Files.writeString(saveDir.resolve("legacy.json.deprecated"), "{ corrupt");
        Files.writeString(saveDir.resolve("notes.txt"), "not json");

        List<Map<String, Object>> loaded = newService().loads();

        assertThat(loaded).singleElement().extracting(m -> m.get("k")).isEqualTo("v");
    }
}
