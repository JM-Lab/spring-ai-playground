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
import org.springaicommunity.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.springaicommunity.playground.service.vectorstore.VectorStoreService.SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION;

@Service
public class VectorStoreDocumentPersistenceService implements PersistenceServiceInterface<VectorStoreDocumentInfo> {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentPersistenceService.class);
    private static final String SIMPLE_VECTOR_STORE_JSON = "simpleVectorStore.json";

    private final Path saveDir;
    private final Path simpleVectorstoreSaveDir;
    private final VectorStore vectorStore;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final PersistenceExecutor persistenceExecutor;
    private final Duration dumpDebounceDelay;
    private final ScheduledExecutorService dumpScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "simplevectorstore-dump-debounce");
                t.setDaemon(false);
                return t;
            });
    private final AtomicReference<ScheduledFuture<?>> pendingDump = new AtomicReference<>();

    public VectorStoreDocumentPersistenceService(Path springAiPlaygroundHomeDir, VectorStore vectorStore,
            VectorStoreDocumentService vectorStoreDocumentService, PersistenceExecutor persistenceExecutor,
            @Value("${spring.ai.playground.vectorstore.simple-dump-debounce-ms:5000}") long dumpDebounceDelayMs)
            throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("save").resolve(
                Optional.ofNullable(vectorStore.getName()).filter(Predicate.not(String::isBlank))
                        .orElse("VectorStore"));
        Files.createDirectories(this.saveDir);
        this.simpleVectorstoreSaveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("simpleVectorStore");
        Files.createDirectories(this.simpleVectorstoreSaveDir);
        this.vectorStore = vectorStore;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.persistenceExecutor = persistenceExecutor;
        this.dumpDebounceDelay = Duration.ofMillis(dumpDebounceDelayMs);
    }

    public void saveAsync(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        this.persistenceExecutor.submit(() -> {
            try {
                save(vectorStoreDocumentInfo);
            } catch (IOException e) {
                logger.error("Async save failed for document {}", vectorStoreDocumentInfo.docInfoId(), e);
            }
        });
    }

    public void deleteAsync(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        this.persistenceExecutor.submit(() -> delete(vectorStoreDocumentInfo));
    }

    public void scheduleSimpleVectorStoreDump() {
        if (asSimpleVectorStore().isEmpty() || this.dumpScheduler.isShutdown()) return;
        ScheduledFuture<?> next = this.dumpScheduler.schedule(
                () -> this.persistenceExecutor.submit(this::flushSimpleVectorStoreDumpAndLog),
                this.dumpDebounceDelay.toMillis(), TimeUnit.MILLISECONDS);
        ScheduledFuture<?> previous = this.pendingDump.getAndSet(next);
        if (previous != null) previous.cancel(false);
    }

    private void flushSimpleVectorStoreDumpAndLog() {
        try {
            flushSimpleVectorStoreDump();
        } catch (IOException e) {
            logger.error("Failed to dump SimpleVectorStore", e);
        }
    }

    private void flushSimpleVectorStoreDump() throws IOException {
        Optional<SimpleVectorStore> svs = asSimpleVectorStore();
        if (svs.isEmpty()) return;
        Path dumpPath = this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON);
        if (this.vectorStoreDocumentService.getDocumentList().isEmpty())
            Files.deleteIfExists(dumpPath);
        else
            svs.get().save(dumpPath.toFile());
    }

    private Optional<SimpleVectorStore> asSimpleVectorStore() {
        return this.vectorStore instanceof SimpleVectorStore svs ? Optional.of(svs) : Optional.empty();
    }

    @Override
    public Path getSaveDir() {
        return this.saveDir;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void buildSaveData(VectorStoreDocumentInfo vectorStoreDocumentInfo,
            Map<String, Object> vectorStoreDocumentInfoMap) {
        vectorStoreDocumentInfoMap.put("documentList", vectorStoreDocumentInfo.documentListSupplier().get());
    }

    @Override
    public String buildSaveFileName(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        return vectorStoreDocumentInfo.docInfoId();
    }

    @Override
    public VectorStoreDocumentInfo convertTo(Map<String, Object> vectorStoreDocumentInfoMap) {
        String docInfoId = vectorStoreDocumentInfoMap.get("docInfoId").toString();
        String title = vectorStoreDocumentInfoMap.get("title").toString();
        long createTimestamp = ((Number) vectorStoreDocumentInfoMap.get("createTimestamp")).longValue();
        long updateTimestamp = ((Number) vectorStoreDocumentInfoMap.get("updateTimestamp")).longValue();
        String documentFileName = vectorStoreDocumentInfoMap.computeIfAbsent("documentFileName", key -> "").toString();
        String documentPath = vectorStoreDocumentInfoMap.computeIfAbsent("documentPath", key -> "").toString();
        List<Map<String, Object>> documentMapList =
                (List<Map<String, Object>>) vectorStoreDocumentInfoMap.get("documentList");
        List<Document> documentList =
                documentMapList.stream().map(this::convertToDocument).collect(Collectors.toList());
        return new VectorStoreDocumentInfo(docInfoId, title, createTimestamp, updateTimestamp, documentFileName,
                documentPath, () -> documentList);
    }

    @Override
    public void clear() {
        PersistenceServiceInterface.super.clear();
        try {
            Files.deleteIfExists(this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON));
        } catch (IOException e) {
            logger.error("Failed to delete SimpleVectorStore dump", e);
        }
    }

    private Document convertToDocument(Map<String, Object> documentMap) {
        return new Document(documentMap.get("id").toString(), documentMap.get("text").toString(),
                (Map<String, Object>) documentMap.get("metadata"));
    }

    @Override
    public void onStart() throws IOException {
        Path dumpPath = this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON);
        if (Files.exists(dumpPath))
            asSimpleVectorStore().ifPresent(svs -> svs.load(dumpPath.toFile()));
        List<VectorStoreDocumentInfo> loaded = loads();
        vectorStoreDocumentService.loadAll(() -> loaded.forEach(vectorStoreDocumentInfo -> {
            vectorStoreDocumentService.updateDocumentInfo(vectorStoreDocumentInfo,
                    vectorStoreDocumentInfo.title());
            vectorStoreDocumentInfo.changeDocumentListSupplier(() -> this.vectorStore.similaritySearch(
                    SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION.apply(
                            List.of(vectorStoreDocumentInfo.docInfoId()))));
        }));
    }

    @Override
    public void onShutdown() throws IOException {
        // DocumentInfos are persisted on change via saveAsync; SimpleVectorStore dumps
        // are debounced on mutation. Cancel any pending debounced dump and flush a final
        // snapshot synchronously since the persistence executor is already shut down.
        ScheduledFuture<?> pending = this.pendingDump.getAndSet(null);
        if (pending != null) pending.cancel(false);
        this.dumpScheduler.shutdownNow();
        flushSimpleVectorStoreDump();
    }

    @Override
    public void delete(VectorStoreDocumentInfo saveObject) {
        PersistenceServiceInterface.super.delete(saveObject);
        try {
            this.vectorStoreDocumentService.removeUploadedDocumentFile(saveObject.getDocumentFileName());
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to remove uploaded file for document " + saveObject.docInfoId(), e);
        }
    }

    public void delete(List<String> documentIds) {
        this.vectorStoreDocumentService.getDocumentList().stream()
                .filter(document -> documentIds.contains(document.docInfoId())).forEach(this::delete);
    }
}
