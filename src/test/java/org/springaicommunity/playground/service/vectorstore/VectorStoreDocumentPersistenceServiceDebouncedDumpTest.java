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
package org.springaicommunity.playground.service.vectorstore;

import org.springaicommunity.playground.service.PersistenceExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorStoreDocumentPersistenceServiceDebouncedDumpTest {

    private static final Duration TEST_DEBOUNCE = Duration.ofMillis(150);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(2);

    @TempDir
    Path tempDir;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    VectorStoreDocumentService vectorStoreDocumentService;

    SimpleVectorStore simpleVectorStore;
    PersistenceExecutor persistenceExecutor;
    VectorStoreDocumentPersistenceService service;

    @BeforeEach
    void setUp() throws IOException {
        lenient().when(embeddingModel.embed(any(Document.class))).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        persistenceExecutor = new PersistenceExecutor();
        service = new VectorStoreDocumentPersistenceService(tempDir, simpleVectorStore,
                vectorStoreDocumentService, persistenceExecutor, TEST_DEBOUNCE.toMillis());
    }

    @AfterEach
    void tearDown() {
        persistenceExecutor.flushAndShutdown();
    }

    private Path dumpFile() {
        return tempDir.resolve("vectorstore").resolve("simpleVectorStore").resolve("simpleVectorStore.json");
    }

    private VectorStoreDocumentInfo sampleDocInfo() {
        String id = "doc-" + UUID.randomUUID();
        return new VectorStoreDocumentInfo(id, "t", 0L, 0L, "", "", List::of);
    }

    private void waitUntilExists(Path path) throws InterruptedException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Expected file to exist within " + POLL_TIMEOUT + ": " + path);
    }

    @Test
    void onShutdown_writesDumpWhenDocumentsExist() throws IOException {
        when(vectorStoreDocumentService.getDocumentList()).thenReturn(List.of(sampleDocInfo()));
        simpleVectorStore.add(List.of(new Document("id1", "hello", Map.of())));

        service.onShutdown();

        assertThat(dumpFile()).exists();
    }

    @Test
    void onShutdown_deletesDumpWhenNoDocuments() throws IOException {
        Files.createDirectories(dumpFile().getParent());
        Files.writeString(dumpFile(), "{\"stale\":true}");
        when(vectorStoreDocumentService.getDocumentList()).thenReturn(List.of());

        service.onShutdown();

        assertThat(dumpFile()).doesNotExist();
    }

    @Test
    void scheduleSimpleVectorStoreDump_writesFileAfterDebounceElapses() throws InterruptedException {
        when(vectorStoreDocumentService.getDocumentList()).thenReturn(List.of(sampleDocInfo()));
        simpleVectorStore.add(List.of(new Document("id1", "hello", Map.of())));

        service.scheduleSimpleVectorStoreDump();

        waitUntilExists(dumpFile());
    }

    @Test
    void scheduleSimpleVectorStoreDump_coalescesBurstIntoOneWrite()
            throws IOException, InterruptedException, TimeoutException {
        when(vectorStoreDocumentService.getDocumentList()).thenReturn(List.of(sampleDocInfo()));
        simpleVectorStore.add(List.of(new Document("id1", "hello", Map.of())));

        for (int i = 0; i < 10; i++)
            service.scheduleSimpleVectorStoreDump();

        waitUntilExists(dumpFile());
        persistenceExecutor.awaitCompletion(POLL_TIMEOUT);
        long firstModified = Files.getLastModifiedTime(dumpFile()).toMillis();
        Thread.sleep(TEST_DEBOUNCE.toMillis() * 2);
        assertThat(Files.getLastModifiedTime(dumpFile()).toMillis()).isEqualTo(firstModified);
    }

    @Test
    void scheduleSimpleVectorStoreDump_isNoOpForNonSimpleVectorStore() throws IOException, InterruptedException {
        VectorStore nonSimple = mock(VectorStore.class);
        PersistenceExecutor localExecutor = new PersistenceExecutor();
        try {
            VectorStoreDocumentPersistenceService nonSimpleService = new VectorStoreDocumentPersistenceService(
                    tempDir, nonSimple, vectorStoreDocumentService, localExecutor, TEST_DEBOUNCE.toMillis());

            nonSimpleService.scheduleSimpleVectorStoreDump();
            Thread.sleep(TEST_DEBOUNCE.toMillis() * 2);

            assertThat(dumpFile()).doesNotExist();
            nonSimpleService.onShutdown();
            assertThat(dumpFile()).doesNotExist();
        } finally {
            localExecutor.flushAndShutdown();
        }
    }

    @Test
    void onShutdown_cancelsPendingScheduledDump() throws IOException, InterruptedException {
        when(vectorStoreDocumentService.getDocumentList()).thenReturn(List.of());

        service.scheduleSimpleVectorStoreDump();
        service.onShutdown();

        Thread.sleep(TEST_DEBOUNCE.toMillis() * 2);
        assertThat(dumpFile()).doesNotExist();
    }
}
