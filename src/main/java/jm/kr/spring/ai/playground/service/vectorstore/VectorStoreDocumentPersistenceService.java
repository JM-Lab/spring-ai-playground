package jm.kr.spring.ai.playground.service.vectorstore;

import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VectorStoreDocumentPersistenceService implements PersistenceServiceInterface<VectorStoreDocumentInfo> {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentPersistenceService.class);

    private final Path saveDir;

    public VectorStoreDocumentPersistenceService(Path springAiPlaygroundHomeDir) throws IOException {
        this.saveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("save");
        Files.createDirectories(this.saveDir);
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
    public String buildSaveDataAndReturnName(VectorStoreDocumentInfo vectorStoreDocumentInfo,
            Map<String, Object> vectorStoreDocumentInfoMap) {
        vectorStoreDocumentInfoMap.put("documentList", vectorStoreDocumentInfo.documentListSupplier().get());
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
}
