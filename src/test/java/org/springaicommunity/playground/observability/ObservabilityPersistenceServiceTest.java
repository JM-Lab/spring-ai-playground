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
package org.springaicommunity.playground.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityPersistenceServiceTest {

    @TempDir Path tempHome;

    private PersistenceExecutor executor;
    private ObservabilityProperties props;
    private ObservabilityPersistenceService service;

    @BeforeEach
    void setUp() throws IOException {
        executor = new PersistenceExecutor();
        props = new ObservabilityProperties();
        props.setRetainDays(30);
        service = new ObservabilityPersistenceService(tempHome, executor, props);
        service.onStart();
    }

    @AfterEach
    void tearDown() {
        executor.flushAndShutdown();
    }

    @Test
    void getSaveDirResolvesToTodayIsoDateDir() {
        Path expected = tempHome.resolve("observability").resolve(LocalDate.now().toString());
        assertThat(service.getSaveDir()).isEqualTo(expected);
    }

    @Test
    void saveAsyncWritesJsonFileUnderTodayDir() throws Exception {
        TraceRecord trace = sample("trace-abc");
        service.saveAsync(trace);
        executor.awaitCompletion(Duration.ofSeconds(5));

        Path expected = service.getSaveDir().resolve("trace-abc.json");
        assertThat(Files.exists(expected)).isTrue();
        String content = Files.readString(expected);
        assertThat(content).contains("\"traceId\"").contains("trace-abc")
                .contains("\"model\"").contains("qwen3.5:9b");
    }

    @Test
    void retentionCleanupRemovesOldDateDirsOnly() throws IOException {
        Path baseDir = tempHome.resolve("observability");
        Path freshDir = baseDir.resolve(LocalDate.now().toString());
        Path oldDir = baseDir.resolve(LocalDate.now().minusDays(60).toString());
        Path veryOldDir = baseDir.resolve("2020-01-01");
        Path notADateDir = baseDir.resolve("not-a-date");
        Files.createDirectories(freshDir);
        Files.createDirectories(oldDir);
        Files.createDirectories(veryOldDir);
        Files.createDirectories(notADateDir);
        Files.writeString(oldDir.resolve("foo.json"), "{}");
        Files.writeString(freshDir.resolve("bar.json"), "{}");

        service.scheduledCleanup();

        assertThat(Files.exists(freshDir)).isTrue();
        assertThat(Files.exists(notADateDir)).isTrue();
        assertThat(Files.exists(oldDir)).isFalse();
        assertThat(Files.exists(veryOldDir)).isFalse();
    }

    @Test
    void retentionCleanupIsSafeWhenBaseDirMissing() throws IOException {
        Path baseDir = tempHome.resolve("observability");
        if (Files.exists(baseDir)) {
            try (var paths = Files.walk(baseDir)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(p -> p.toFile().delete());
            }
        }
        service.scheduledCleanup();
    }

    @Test
    void saveFilenameUsesTraceIdAsBasename() throws Exception {
        TraceRecord trace = sample("e2c45a4f9b3d");
        service.saveAsync(trace);
        executor.awaitCompletion(Duration.ofSeconds(5));

        Path saveDir = service.getSaveDir();
        try (var paths = Files.list(saveDir)) {
            List<String> names = paths.map(p -> p.getFileName().toString()).toList();
            assertThat(names).containsExactly("e2c45a4f9b3d.json");
        }
    }

    @Test
    void serializedJsonOmitsNullOptionalFields() throws Exception {
        TraceRecord sparse = new TraceRecord(
                "sparse-trace",
                null,
                null,
                null,
                null,
                System.currentTimeMillis(),
                25L,
                TraceRecord.STATUS_OK,
                null,
                null,
                null,
                null,
                false, 0, false,
                null, null, null, null,
                List.of(), Map.of());
        service.saveAsync(sparse);
        executor.awaitCompletion(Duration.ofSeconds(5));

        Path saved = service.getSaveDir().resolve("sparse-trace.json");
        String json = Files.readString(saved);
        assertThat(json)
                .contains("\"traceId\"")
                .contains("\"status\"");
        assertThat(json)
                .doesNotContain("\"conversationId\"")
                .doesNotContain("\"userMessageId\"")
                .doesNotContain("\"provider\"")
                .doesNotContain("\"model\"")
                .doesNotContain("\"inputTokens\"")
                .doesNotContain("\"outputTokens\"")
                .doesNotContain("\"totalTokens\"")
                .doesNotContain("\"finishReason\"");
    }

    private TraceRecord sample(String id) {
        return new TraceRecord(
                id, "conv-1", "msg-1", "ollama", "qwen3.5:9b",
                System.currentTimeMillis(), 100L, TraceRecord.STATUS_OK,
                10L, 20L, 30L, "stop", false, 0, false,
                null, null, null, null,
                List.of(), Map.of("gen_ai.system", "ollama"));
    }
}
