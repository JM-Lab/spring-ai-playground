package jm.kr.spring.ai.playground.service.vectorstore;


import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;

@Service
public class VectorStoreService {

    private final EmbeddingModel embeddingModel;
    private final AbstractObservationVectorStore vectorStore;

    public VectorStoreService(EmbeddingModel embeddingModel, VectorStore vectorStore) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = (AbstractObservationVectorStore) vectorStore;
    }

    public EmbeddingResponse test(String text) {
        return this.embeddingModel.embedForResponse(List.of(text));
    }

    public List<Document> searchAll(int page, int pageSize) {
        List<Document> documentList =
                this.vectorStore.doSimilaritySearch(SearchRequest.builder().similarityThresholdAll().build());
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, documentList.size());
        return fromIndex > documentList.size() ? Collections.emptyList() : documentList.subList(fromIndex, toIndex);
    }

    public Collection<Document> searchAll(int page, int pageSize, String contentFilter) {
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder();
        if (Optional.ofNullable(contentFilter).filter(Predicate.not(String::isBlank)).isPresent())
            searchRequestBuilder.filterExpression(contentFilter);
        List<Document> filteredDocumentList = this.vectorStore.doSimilaritySearch(searchRequestBuilder.build());
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, filteredDocumentList.size());

        if (fromIndex > filteredDocumentList.size()) {
            return new ArrayList<>();
        }
        return filteredDocumentList.subList(fromIndex, toIndex);
    }

    public void add(List<Document> documentList) {
        this.vectorStore.add(documentList);
    }

    public void add(Document document) {
        add(List.of(document));
    }

    public void update(Document document) {
        delete(document);
        add(document);
    }

    public void delete(Document document) {
        this.vectorStore.doDelete(List.of(document.getId()));
    }

    public String getVectorStoreName() {
        return vectorStore.getName();
    }

}