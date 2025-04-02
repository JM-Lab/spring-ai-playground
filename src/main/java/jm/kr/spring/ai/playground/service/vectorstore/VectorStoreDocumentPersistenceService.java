package jm.kr.spring.ai.playground.service.vectorstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VectorStoreDocumentPersistenceService {
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDocumentPersistenceService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    private final Path saveDir;
    private final ObjectMapper objectMapper;

    public VectorStoreDocumentPersistenceService(Path springAiPlaygroundHomeDir, ObjectMapper objectMapper) {
        this.saveDir = springAiPlaygroundHomeDir.resolve("vectorstore").resolve("save");
        if (!this.saveDir.toFile().exists())
            this.saveDir.toFile().mkdirs();
        this.objectMapper = objectMapper;
    }

    public void save(VectorStoreDocumentInfo vectorStoreDocumentInfo) throws IOException {
        Map<String, Object> vectorStoreDocumentInfoMap =
                objectMapper.convertValue(vectorStoreDocumentInfo, MAP_TYPE_REFERENCE);
        vectorStoreDocumentInfoMap.put("documentList", vectorStoreDocumentInfo.documentListSupplier().get());
        File file = this.saveDir.resolve(vectorStoreDocumentInfo.docInfoId() + ".json").toFile();
        logger.info("Saving document info to file: {}", file.getAbsolutePath());
        objectMapper.writeValue(file, vectorStoreDocumentInfoMap);
    }

    public List<VectorStoreDocumentInfo> loads() throws IOException {
        List<VectorStoreDocumentInfo> vectorStoreDocumentInfos = new ArrayList<>();
        for (File file : Files.list(this.saveDir).map(Path::toFile).toList()) {
            Map<String, Object> vectorStoreDocumentInfoMap = objectMapper.readValue(file, MAP_TYPE_REFERENCE);
            String docInfoId = (String) vectorStoreDocumentInfoMap.get("docInfoId");
            String title = (String) vectorStoreDocumentInfoMap.get("title");
            long createTimestamp = ((Number) vectorStoreDocumentInfoMap.get("createTimestamp")).longValue();
            long updateTimestamp = ((Number) vectorStoreDocumentInfoMap.get("updateTimestamp")).longValue();
            String documentPath = (String) vectorStoreDocumentInfoMap.get("documentPath");
            List<Map<String, Object>> documentMapList =
                    (List<Map<String, Object>>) vectorStoreDocumentInfoMap.get("documentList");
            List<Document> documentList =
                    documentMapList.stream().map(this::convertToDocument).collect(Collectors.toList());
            vectorStoreDocumentInfos.add(
                    new VectorStoreDocumentInfo(docInfoId, title, createTimestamp, updateTimestamp, documentPath,
                            () -> documentList));
        }
        return vectorStoreDocumentInfos;
    }

    private Document convertToDocument(Map<String, Object> documentMap) {
        return new Document(documentMap.get("id").toString(), documentMap.get("text").toString(),
                (Map<String, Object>) documentMap.get("metadata"));
    }
}
