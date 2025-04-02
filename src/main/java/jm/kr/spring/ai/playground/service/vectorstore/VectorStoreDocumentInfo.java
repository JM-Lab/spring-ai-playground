package jm.kr.spring.ai.playground.service.vectorstore;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.Supplier;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class VectorStoreDocumentInfo {
    private final String docInfoId;
    private final String title;
    private final long createTimestamp;
    private final long updateTimestamp;
    private final String documentPath;
    @JsonIgnore
    private Supplier<List<Document>> documentListSupplier;

    public VectorStoreDocumentInfo(String docInfoId, String title, long createTimestamp, long updateTimestamp,
            String documentPath, Supplier<List<Document>> documentListSupplier) {
        this.docInfoId = docInfoId;
        this.title = title;
        this.createTimestamp = createTimestamp;
        this.updateTimestamp = updateTimestamp;
        this.documentPath = documentPath;
        this.documentListSupplier = documentListSupplier;
    }

    // 필드명과 동일한 메서드 제공 (record 스타일)
    public String docInfoId() {
        return docInfoId;
    }

    public String title() {
        return title;
    }

    public long createTimestamp() {
        return createTimestamp;
    }

    public long updateTimestamp() {
        return updateTimestamp;
    }

    public String documentPath() {
        return documentPath;
    }

    public Supplier<List<Document>> documentListSupplier() {
        return documentListSupplier;
    }

    public void changeDocumentListSupplier(Supplier<List<Document>> documentListSupplier) {
        this.documentListSupplier = documentListSupplier;
    }

    // 새로운 제목으로 변경하는 메서드
    public VectorStoreDocumentInfo newTitle(String newTitle) {
        return new VectorStoreDocumentInfo(docInfoId, newTitle, createTimestamp, System.currentTimeMillis(),
                documentPath, documentListSupplier);
    }
}