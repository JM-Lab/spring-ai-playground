package jm.kr.spring.ai.playground.service.vectorstore;

import com.vaadin.flow.component.notification.Notification;
import jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;

import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
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
public class VectorStoreDocumentService {

    public static final String DOCUMENT_SELECTING_EVENT = "DOCUMENT_SELECTING_EVENT";
    public static final String DOCUMENT_ADDING_EVENT = "DOCUMENT_ADDING_EVENT";
    public static final String DOCUMENTS_DELETE_EVENT = "DOCUMENTS_DELETE_EVENT";

    public record TokenTextSplitInfo(int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
                                     int maxNumChunks, boolean keepSeparator) {}


    public final static TokenTextSplitInfo DEFAULT_TOKEN_TEXT_SPLIT_INFO =
            new TokenTextSplitInfo(800, 350, 5, 10000, true);

    private final File uploadDir;

    private final DataSize maxUploadSize;

    private final Map<String, TokenTextSplitter> splitters;
    private final TokenTextSplitter defaultTokenTextSplitter;
    private final Map<String, VectorStoreDocumentInfo> documentInfos = new ConcurrentHashMap<>();

    private final PropertyChangeSupport documentInfoChangeSupport;

    public VectorStoreDocumentService(@Value("${spring.servlet.multipart.max-file-size}") DataSize maxUploadSize) {
        this.uploadDir = new File(System.getProperty("user.home"), "spring-ai-playground/vectorstore");
        if (!uploadDir.exists())
            uploadDir.mkdirs();
        this.maxUploadSize = maxUploadSize;
        this.splitters = new WeakHashMap<>();
        this.defaultTokenTextSplitter = newTokenTextSplitter(DEFAULT_TOKEN_TEXT_SPLIT_INFO);
        this.documentInfoChangeSupport = new PropertyChangeSupport(this);
    }

    public PropertyChangeSupport getDocumentInfoChangeSupport() {
        return this.documentInfoChangeSupport;
    }

    public VectorStoreDocumentInfo putNewDocument(String documentFileName, List<Document> uploadedDocumentItems) {
        long createTimestamp = System.currentTimeMillis();
        File uploadedDocumentFile = new File(uploadDir, documentFileName);
        String docInfoId = VectorStoreView.DOC_INFO_ID + "-" + UUID.randomUUID();
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
        metadata.put(VectorStoreView.DOC_INFO_ID, docInfoId);
        return new Document(index + "-" + docInfoId, uploadedDocument.getText(), metadata);
    }

    public Map<String, List<Document>> extractDocumentItems(List<String> uploadedFileNames) {
        return uploadedFileNames.stream()
                .map(fileName -> Map.entry(fileName, split(new FileSystemResource(new File(uploadDir,
                        fileName))))).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
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
        File file = new File(uploadDir, fileName);
        if (file.exists())
            throw new FileAlreadyExistsException("Already Exists - " + file.getAbsolutePath());
        Files.copy(uploadedFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Notification.show("File uploaded successfully to: " + fileName);
    }

    public void removeUploadedDocumentFile(String fileName) throws IOException {
        Files.deleteIfExists(new File(uploadDir, fileName).toPath());
    }

    public DataSize getMaxUploadSize() {
        return maxUploadSize;
    }

    public VectorStoreDocumentInfo updateDocumentInfo(VectorStoreDocumentInfo vectorStoreDocumentInfo, String title) {
        VectorStoreDocumentInfo updateVectorStoreDocumentInfo = vectorStoreDocumentInfo.newTitle(title);
        this.documentInfos.put(vectorStoreDocumentInfo.docInfoId(), updateVectorStoreDocumentInfo);
        return updateVectorStoreDocumentInfo;
    }

    public void deleteDocumentInfo(String docId) {
        this.documentInfos.remove(docId);
    }

    public List<VectorStoreDocumentInfo> getDocumentList() {
        return this.documentInfos.values().stream()
                .sorted(Comparator.comparingLong(VectorStoreDocumentInfo::updateTimestamp).reversed()).toList();
    }

}
