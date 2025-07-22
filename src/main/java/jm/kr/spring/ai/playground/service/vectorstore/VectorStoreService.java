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


import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingOptionsBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.springframework.ai.vectorstore.SearchRequest.DEFAULT_TOP_K;
import static org.springframework.ai.vectorstore.SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL;

@Service
public class VectorStoreService {
    public static final String DOC_INFO_ID = "docInfoId";
    public static final SearchRequestOption ALL_SEARCH_REQUEST_OPTION =
            new SearchRequestOption(SIMILARITY_THRESHOLD_ACCEPT_ALL, 10000);
    public static final Function<List<String>, SearchRequest> SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION =
            docInfoIds -> new SearchRequest.Builder().similarityThreshold(
                            ALL_SEARCH_REQUEST_OPTION.similarityThreshold()).topK(ALL_SEARCH_REQUEST_OPTION.topK())
                    .filterExpression(new FilterExpressionBuilder().in(DOC_INFO_ID, docInfoIds.toArray()).build())
                    .build();

    public record SearchRequestOption(Double similarityThreshold, Integer topK) {
        public SearchRequestOption newSimilarityThreshold(Double newSimilarityThreshold) {
            return new SearchRequestOption(newSimilarityThreshold, topK);
        }

        public SearchRequestOption newTopK(Integer newTopK) {
            return new SearchRequestOption(similarityThreshold, newTopK);
        }
    }

    private final ApplicationContext applicationContext;

    private final AbstractEmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private SearchRequestOption searchRequestOption;
    private EmbeddingOptions embeddingOptions;

    public VectorStoreService(EmbeddingModel embeddingModel, VectorStore vectorStore,
            @Lazy ApplicationContext applicationContext) {
        this.embeddingModel = (AbstractEmbeddingModel) embeddingModel;
        this.vectorStore = vectorStore;
        this.searchRequestOption = new SearchRequestOption(0.6, DEFAULT_TOP_K);
        this.applicationContext = applicationContext;
    }

    public SearchRequestOption getSearchRequestOption() {
        return searchRequestOption;
    }

    public void setVectorStoreOption(SearchRequestOption searchRequestOption) {
        this.searchRequestOption = searchRequestOption;
    }

    public List<Document> search(String userPromptText, String filterExpression) {
        SearchRequest.Builder searchRequestBuilder = SearchRequest.builder();
        searchRequestBuilder.similarityThreshold(this.searchRequestOption.similarityThreshold())
                .topK(this.searchRequestOption.topK());
        if (Objects.nonNull(userPromptText) && !userPromptText.isBlank()) {
            searchRequestBuilder.query(userPromptText);
        }
        if (Objects.nonNull(filterExpression) && !filterExpression.isBlank()) {
            searchRequestBuilder.filterExpression(filterExpression);
        }
        return search(searchRequestBuilder.build());
    }

    public List<Document> search(SearchRequest searchRequest) {
        return this.vectorStore.similaritySearch(searchRequest);
    }

    public void add(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        this.vectorStore.add(vectorStoreDocumentInfo.documentListSupplier().get());
        vectorStoreDocumentInfo.changeDocumentListSupplier(() -> this.vectorStore.similaritySearch(
                SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION.apply(List.of(vectorStoreDocumentInfo.docInfoId()))));
    }

    public List<Document> add(List<Document> documents) {
        this.vectorStore.add(documents);
        return documents;
    }

    public Document update(Document document) {
        delete(List.of(document.getId()));
        add(List.of(document));
        return document;
    }

    public void delete(List<String> documentIds) {
        this.vectorStore.delete(documentIds);
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

}