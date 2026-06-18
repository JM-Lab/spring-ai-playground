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
package org.springaicommunity.playground;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShippedApplicationYamlConfigTest {

    private static final Duration LONG_TASK_TIMER_FLOOR = Duration.ofMinutes(2);

    private static List<Map<String, Object>> documents;

    @BeforeAll
    static void loadShippedYaml() throws Exception {
        documents = new ArrayList<>();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/application.yaml"))) {
            for (Object doc : new Yaml().loadAll(in)) {
                if (doc instanceof Map<?, ?> map) {
                    documents.add(castMap(map));
                }
            }
        }
    }

    // Boot 4 applies management.metrics.distribution.maximum-expected-value to the per-observation
    // LongTaskTimer too, whose default minimum is 2m; a maximum below 2m throws when the observation
    // starts (regression: it crashed the Vector DB page and tool calls).
    @Test
    void everyMetricsDistributionMaximumIsAtLeastTheLongTaskTimerFloor() {
        Map<String, Object> maxValues = maximumExpectedValues();
        assertThat(maxValues).isNotEmpty();
        maxValues.forEach((meter, raw) -> assertThat(parseDuration(String.valueOf(raw)))
                .as("management.metrics.distribution.maximum-expected-value.%s = %s must be >= %s",
                        meter, raw, LONG_TASK_TIMER_FLOOR)
                .isGreaterThanOrEqualTo(LONG_TASK_TIMER_FLOOR));
    }

    // Spring AI 2.0 renamed the OpenAI starter (openai-sdk -> openai); the openai profile must select the
    // openai model name and the spring.ai.openai prefix, else no ChatModel/EmbeddingModel beans wire and
    // the openai profile fails to boot.
    @Test
    void openAiProfileUsesOpenAiPrefixNotOpenAiSdk() {
        Map<String, Object> openai = profileDocument("openai");
        assertThat(openai).as("openai profile document").isNotNull();
        assertThat(nested(openai, "spring", "ai", "model", "chat")).isEqualTo("openai");
        assertThat(nested(openai, "spring", "ai", "model", "embedding")).isEqualTo("openai");
        assertThat(nested(openai, "spring", "ai", "openai")).as("spring.ai.openai block").isNotNull();
        assertThat(nested(openai, "spring", "ai", "openai-sdk")).as("removed spring.ai.openai-sdk block").isNull();
    }

    private Map<String, Object> maximumExpectedValues() {
        for (Map<String, Object> doc : documents) {
            if (nested(doc, "management", "metrics", "distribution", "maximum-expected-value") instanceof Map<?, ?> map) {
                return castMap(map);
            }
        }
        return Map.of();
    }

    private Map<String, Object> profileDocument(String profile) {
        for (Map<String, Object> doc : documents) {
            if (profile.equals(nested(doc, "spring", "config", "activate", "on-profile"))) {
                return doc;
            }
        }
        return null;
    }

    private static Object nested(Map<String, Object> root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
        }
        return current;
    }

    private static Duration parseDuration(String raw) {
        String s = raw.trim();
        if (s.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
        }
        if (s.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
        }
        if (s.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
        }
        if (s.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
        }
        return Duration.parse(s);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
