package jm.kr.spring.ai.playground.service.vectorstore;

import org.springframework.ai.embedding.EmbeddingOptions;

public record VectorStoreDocumentInfo(String docId, String title, long createTimestamp, long updateTimestamp,
                                      String documentPath, EmbeddingOptions embeddingOptions) {
    public VectorStoreDocumentInfo newTitle(String newTitle) {
        return new VectorStoreDocumentInfo(docId, newTitle, createTimestamp, System.currentTimeMillis(), documentPath,
                embeddingOptions);
    }

    public VectorStoreDocumentInfo newUpdateTimestamp() {
        return new VectorStoreDocumentInfo(docId, title, createTimestamp, System.currentTimeMillis(), documentPath,
                embeddingOptions);
    }
}