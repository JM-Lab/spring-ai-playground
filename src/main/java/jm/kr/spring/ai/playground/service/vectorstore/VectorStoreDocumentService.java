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

import com.vaadin.flow.component.notification.Notification;
import jm.kr.spring.ai.playground.service.SharedDataReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class VectorStoreDocumentService implements SharedDataReader<List<VectorStoreDocumentInfo>> {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentService.class);

    @Override
    public List<VectorStoreDocumentInfo> read() {
        return getDocumentList();
    }

    public record TokenTextSplitInfo(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
                                     int maxNumChunks, boolean keepSeparator) {}


    public final static TokenTextSplitInfo DEFAULT_TOKEN_TEXT_SPLIT_INFO =
            new TokenTextSplitInfo(800, 350, 5, 10000, true);

    private final ResourceLoader resourceLoader;

    private final Path uploadDir;

    private final DataSize maxUploadSize;

    private final Map<String, TokenTextSplitter> splitters;
    private final TokenTextSplitter defaultTokenTextSplitter;
    private final VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService;
    private final Map<String, VectorStoreDocumentInfo> documentInfos;

    public VectorStoreDocumentService(Path springAiPlaygroundHomeDir,
            @Value("${spring.servlet.multipart.max-file-size}") DataSize maxUploadSize, ResourceLoader resourceLoader,
            @Lazy VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService) throws IOException {
        this.uploadDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("docs");
        this.resourceLoader = resourceLoader;
        this.vectorStoreDocumentPersistenceService = vectorStoreDocumentPersistenceService;
        Files.createDirectories(uploadDir);
        this.maxUploadSize = maxUploadSize;
        this.splitters = new WeakHashMap<>();
        this.defaultTokenTextSplitter = newTokenTextSplitter(DEFAULT_TOKEN_TEXT_SPLIT_INFO);
        this.documentInfos = new ConcurrentHashMap<>();
    }

    public VectorStoreDocumentInfo putNewDocument(String documentFileName, List<Document> uploadedDocumentItems) {
        long createTimestamp = System.currentTimeMillis();
        File uploadedDocumentFile = uploadDir.resolve(documentFileName).toFile();
        String docInfoId = VectorStoreService.DOC_INFO_ID + "-" + UUID.randomUUID();
        List<Document> documentList = IntStream.range(0, uploadedDocumentItems.size()).boxed()
                .map(i -> copyNewDocument(docInfoId, i, uploadedDocumentItems.get(i))).toList();
        VectorStoreDocumentInfo vectorStoreDocumentInfo =
                new VectorStoreDocumentInfo(docInfoId, uploadedDocumentFile.getName(), createTimestamp, createTimestamp,
                        uploadedDocumentFile.getPath(), () -> documentList);
        this.documentInfos.put(docInfoId, vectorStoreDocumentInfo);
        return vectorStoreDocumentInfo;
    }

    private Document copyNewDocument(String docInfoId, Integer index, Document uploadedDocument) {
        Map<String, Object> metadata = new HashMap<>(uploadedDocument.getMetadata());
        metadata.put(VectorStoreService.DOC_INFO_ID, docInfoId);
        return new Document(index + "-" + docInfoId, uploadedDocument.getText(), metadata);
    }

    public Map<String, List<Document>> extractDocumentItems(List<String> uploadedFileNames) {
        return uploadedFileNames.stream().map(fileName -> Map.entry(fileName,
                        split(resolveResource(this.uploadDir.resolve(fileName).toFile().getPath()))))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Resource resolveResource(String path) {
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            return resourceLoader.getResource(path);
        }
        return resourceLoader.getResource("file:" + path);
    }

    private List<Document> split(Resource resource) {
        return split(this.defaultTokenTextSplitter, new TikaDocumentReader(resource));
    }

    private List<Document> split(TextSplitter textSplitter, DocumentReader documentReader) {
        return textSplitter.split(documentReader.read());
    }

    public List<Document> split(Resource resource, TokenTextSplitInfo tokenTextSplitInfo) {
        return split(this.splitters.computeIfAbsent(tokenTextSplitInfo.toString(),
                key -> newTokenTextSplitter(tokenTextSplitInfo)), new TikaDocumentReader(resource));
    }

    private TokenTextSplitter newTokenTextSplitter(TokenTextSplitInfo tokenTextSplitInfo) {
        return new TokenTextSplitter(tokenTextSplitInfo.chunkSize(), tokenTextSplitInfo.minChunkSizeChars(),
                tokenTextSplitInfo.minChunkLengthToEmbed(), tokenTextSplitInfo.maxNumChunks(),
                tokenTextSplitInfo.keepSeparator());
    }

    public void addUploadedDocumentFile(String fileName, File uploadedFile) throws Exception {
        File file = this.uploadDir.resolve(fileName).toFile();
        if (file.exists())
            throw new FileAlreadyExistsException("Already Exists - " + file.getAbsolutePath());
        Files.copy(uploadedFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Notification.show("File uploaded successfully to: " + fileName);
    }

    public void removeUploadedDocumentFile(String fileName) throws IOException {
        Files.deleteIfExists(this.uploadDir.resolve(fileName));
    }

    public DataSize getMaxUploadSize() {
        return this.maxUploadSize;
    }

    public VectorStoreDocumentInfo updateDocumentInfo(VectorStoreDocumentInfo vectorStoreDocumentInfo, String title) {
        logger.info("Updating document info: {}", title);
        VectorStoreDocumentInfo updateVectorStoreDocumentInfo = vectorStoreDocumentInfo.newTitle(title);
        this.documentInfos.put(vectorStoreDocumentInfo.docInfoId(), updateVectorStoreDocumentInfo);
        return updateVectorStoreDocumentInfo;
    }

    public void deleteDocumentInfo(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        this.documentInfos.remove(vectorStoreDocumentInfo.docInfoId());
        this.vectorStoreDocumentPersistenceService.delete(vectorStoreDocumentInfo);
    }

    public List<VectorStoreDocumentInfo> getDocumentList() {
        return this.documentInfos.values().stream()
                .sorted(Comparator.comparingLong(VectorStoreDocumentInfo::updateTimestamp).reversed()).toList();
    }

    public Path getUploadDir() {
        return this.uploadDir;
    }

}
