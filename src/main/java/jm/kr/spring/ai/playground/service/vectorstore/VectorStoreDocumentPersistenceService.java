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
package jm.kr.spring.ai.playground.service.vectorstore;

import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION;

@Service
public class VectorStoreDocumentPersistenceService implements PersistenceServiceInterface<VectorStoreDocumentInfo> {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentPersistenceService.class);
    private static final String SIMPLE_VECTOR_STORE_JSON = "simpleVectorStore.json";

    private final Path saveDir;
    private final Path simpleVectorstoreSaveDir;
    private final VectorStore vectorStore;
    private final VectorStoreDocumentService vectorStoreDocumentService;

    public VectorStoreDocumentPersistenceService(Path springAiPlaygroundHomeDir, VectorStore vectorStore,
            VectorStoreDocumentService vectorStoreDocumentService) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("save");
        Files.createDirectories(this.saveDir);
        this.simpleVectorstoreSaveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("simpleVectorStore");
        Files.createDirectories(this.simpleVectorstoreSaveDir);
        this.vectorStore = vectorStore;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
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
        String documentPath = vectorStoreDocumentInfoMap.computeIfAbsent("documentPath", key -> "").toString();
        List<Map<String, Object>> documentMapList =
                (List<Map<String, Object>>) vectorStoreDocumentInfoMap.get("documentList");
        List<Document> documentList =
                documentMapList.stream().map(this::convertToDocument).collect(Collectors.toList());
        return new VectorStoreDocumentInfo(docInfoId, title, createTimestamp, updateTimestamp, documentPath,
                () -> documentList);
    }

    public void clear() {
        this.saveDir.toFile().deleteOnExit();
    }

    private Document convertToDocument(Map<String, Object> documentMap) {
        return new Document(documentMap.get("id").toString(), documentMap.get("text").toString(),
                (Map<String, Object>) documentMap.get("metadata"));
    }

    @Override
    public void onStart() throws IOException {
        AtomicBoolean needToAdd = new AtomicBoolean();
        if (this.vectorStore instanceof SimpleVectorStore) {
            boolean savedFileExists = this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON).toFile().exists();
            if (savedFileExists)
                ((SimpleVectorStore) vectorStore).load(
                        this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON).toFile());
            needToAdd.set(!savedFileExists);
        }
        loads().forEach(vectorStoreDocumentInfo -> {
            vectorStoreDocumentService.updateDocumentInfo(vectorStoreDocumentInfo,
                    vectorStoreDocumentInfo.title());
            if (needToAdd.get())
                this.vectorStore.add(vectorStoreDocumentInfo.documentListSupplier().get());
            vectorStoreDocumentInfo.changeDocumentListSupplier(() -> this.vectorStore.similaritySearch(
                    SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION.apply(
                            List.of(vectorStoreDocumentInfo.docInfoId()))));
        });
    }

    @Override
    public void onShutdown() throws IOException {
        for (VectorStoreDocumentInfo vectorStoreDocumentInfo : vectorStoreDocumentService.getDocumentList())
            save(vectorStoreDocumentInfo);
        ((SimpleVectorStore) vectorStore).save(
                this.simpleVectorstoreSaveDir.resolve(SIMPLE_VECTOR_STORE_JSON).toFile());
    }

    @Override
    public void delete(VectorStoreDocumentInfo saveObject) {
        PersistenceServiceInterface.super.delete(saveObject);
        getSaveDir().resolve(saveObject.documentPath()).toFile().deleteOnExit();
    }
}
