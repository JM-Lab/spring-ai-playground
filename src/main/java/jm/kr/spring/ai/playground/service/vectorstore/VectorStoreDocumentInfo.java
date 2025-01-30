package jm.kr.spring.ai.playground.service.vectorstore;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.Supplier;

public record VectorStoreDocumentInfo(String docInfoId, String title, long createTimestamp, long updateTimestamp,
                                      String documentPath, Supplier<List<Document>> documentListSupplier) {

    public VectorStoreDocumentInfo newTitle(String newTitle) {
        return new VectorStoreDocumentInfo(docInfoId, newTitle, createTimestamp, System.currentTimeMillis(),
                documentPath, documentListSupplier);
    }

    public VectorStoreDocumentInfo newUpdateTimestamp() {
        return new VectorStoreDocumentInfo(docInfoId, title, createTimestamp, System.currentTimeMillis(), documentPath,
                documentListSupplier);
    }
}