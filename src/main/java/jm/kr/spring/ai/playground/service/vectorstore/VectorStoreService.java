package jm.kr.spring.ai.playground.service.vectorstore;


import com.rometools.utils.Strings;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.springframework.ai.vectorstore.SearchRequest.DEFAULT_TOP_K;
import static org.springframework.ai.vectorstore.SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;

@Service
public class VectorStoreService {
    public record SearchRequestOption(Double similarityThreshold, Integer topK) {
        public SearchRequestOption newSimilarityThreshold(Double newSimilarityThreshold) {
            return new SearchRequestOption(newSimilarityThreshold, topK);
        }

        public SearchRequestOption newTopK(Integer newTopK) {
            return new SearchRequestOption(similarityThreshold, newTopK);
        }
    }

    private final ApplicationContext applicationContext;

    public static final SearchRequestOption ALL_SEARCH_REQUEST_OPTION =
            new SearchRequestOption(SIMILARITY_THRESHOLD_ACCEPT_ALL, 10000);
    private final AbstractEmbeddingModel embeddingModel;
    private final AbstractObservationVectorStore vectorStore;
    private SearchRequestOption searchRequestOption;
    private EmbeddingOptions embeddingOptions;

    public VectorStoreService(EmbeddingModel embeddingModel, VectorStore vectorStore,
            @Lazy ApplicationContext applicationContext) {
        this.embeddingModel = (AbstractEmbeddingModel) embeddingModel;
        this.vectorStore = (AbstractObservationVectorStore) vectorStore;
        this.searchRequestOption = new SearchRequestOption(0.6, DEFAULT_TOP_K);
        this.applicationContext = applicationContext;
    }

    public SearchRequestOption getVectorStoreOption() {
        return searchRequestOption;
    }

    public void setVectorStoreOption(SearchRequestOption searchRequestOption) {
        this.searchRequestOption = searchRequestOption;
    }

    public Collection<Document> search(String userPromptText, String filterExpression) {
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder();
        searchRequestBuilder.similarityThreshold(this.searchRequestOption.similarityThreshold())
                .topK(this.searchRequestOption.topK());
        if (!Strings.isBlank(userPromptText))
            searchRequestBuilder.query(userPromptText);
        if (!Strings.isBlank(filterExpression))
            searchRequestBuilder.filterExpression(filterExpression);
        return search(searchRequestBuilder.build());
    }

    public Collection<Document> search(SearchRequest searchRequest) {
        return this.vectorStore.doSimilaritySearch(searchRequest);
    }

    public Document add(Document document) {
        this.vectorStore.add(List.of(document));
        return document;
    }

    public Document update(Document document) {
        delete(List.of(document.getId()));
        return add(document);
    }

    public void delete(List<String> documentIds) {
        this.vectorStore.doDelete(documentIds);
    }

    public String getEmbeddingModelServiceName() {
        return this.embeddingModel.getClass().getSimpleName().replace("EmbeddingModel", "");
    }

    public EmbeddingOptions getEmbeddingOptions() {
        return Optional.ofNullable(this.embeddingOptions)
                .orElseGet(() -> this.embeddingOptions = Arrays.stream(this.applicationContext.getBeanDefinitionNames())
                        .filter(name -> name.contains("EmbeddingProperties")).findFirst()
                        .map(applicationContext::getBean).map(o -> {
                            try {
                                return o.getClass().getMethod("getOptions").invoke(o);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }).map(o -> (EmbeddingOptions) o).orElseGet(EmbeddingOptionsBuilder.builder()::build));
    }

    public void add(List<Document> documents) {
        this.vectorStore.add(documents);
    }

}