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
package org.springaicommunity.playground.service;

import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springaicommunity.playground.service.chat.ChatService.RAG_FILTER_EXPRESSION;
import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class SpringAiPlaygroundRagAdvisor implements BaseAdvisor {
    public static final String RAG_PROCESS_MESSAGE_CONSUMER = "ragProcessMessageConsumer";
    public static final String RAG_SEARCH_COMPLETED_MESSAGE = "VectorDB document search completed.";

    public record RagRetrievedDocumentsInfo(List<String> titles, int count) {}

    private static final Logger logger = LoggerFactory.getLogger(SpringAiPlaygroundRagAdvisor.class);

    private final VectorStoreService vectorStoreService;
    private final SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader;

    public SpringAiPlaygroundRagAdvisor(VectorStoreService vectorStoreService,
            SharedDataReader<List<VectorStoreDocumentInfo>> vectorStoreDocumentsReader) {
        this.vectorStoreService = vectorStoreService;
        this.vectorStoreDocumentsReader = vectorStoreDocumentsReader;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        if (isFilterExpressionMissing(chatClientRequest))
            return chatClientRequest;
        String query = extractUserQuery(chatClientRequest);
        if (!StringUtils.hasText(query))
            return chatClientRequest;
        Optional<Consumer<Object>> ragProcessMessageConsumer = getRagProcessMessageConsumer(chatClientRequest);
        ragProcessMessageConsumer.ifPresent(consumer -> consumer.accept(formatSearchStart(chatClientRequest)));
        String filterExpression = chatClientRequest.context().get(RAG_FILTER_EXPRESSION).toString();
        List<Document> retrievedDocuments = vectorStoreService.search(query, filterExpression);
        printSearchResults(retrievedDocuments);
        Map<String, String> docInfoTitles = this.vectorStoreDocumentsReader.read().stream()
                .collect(Collectors.toMap(VectorStoreDocumentInfo::docInfoId, VectorStoreDocumentInfo::title,
                        (current, ignored) -> current));
        List<String> titles = retrievedDocuments.stream()
                .map(doc -> resolveDocumentTitle(doc, docInfoTitles)).distinct().toList();
        ragProcessMessageConsumer.ifPresent(consumer -> {
            consumer.accept(new RagRetrievedDocumentsInfo(titles, retrievedDocuments.size()));
            consumer.accept(formatRetrievedDocuments(retrievedDocuments));
            consumer.accept(RAG_SEARCH_COMPLETED_MESSAGE);
        });
        if (retrievedDocuments.isEmpty())
            return chatClientRequest.mutate().context(DOCUMENT_CONTEXT, retrievedDocuments).build();
        return buildRetrievalAugmentationAdvisor(retrievedDocuments).before(chatClientRequest, advisorChain);
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    private boolean isFilterExpressionMissing(ChatClientRequest chatClientRequest) {
        boolean isMissing = Objects.isNull(chatClientRequest.context().get(RAG_FILTER_EXPRESSION));
        if (isMissing)
            logger.debug("Document retrieval was skipped.");
        return isMissing;
    }

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(List<Document> preSearchedDocuments) {
        return RetrievalAugmentationAdvisor.builder().documentRetriever(query -> preSearchedDocuments).build();
    }

    private String extractUserQuery(ChatClientRequest chatClientRequest) {
        return chatClientRequest.prompt().getInstructions().stream()
                .filter(m -> m instanceof UserMessage).reduce((first, second) -> second)
                .map(m -> ((UserMessage) m).getText()).filter(StringUtils::hasText).orElse("");
    }

    private Optional<Consumer<Object>> getRagProcessMessageConsumer(ChatClientRequest chatClientRequest) {
        return Optional.ofNullable(chatClientRequest.context().get(RAG_PROCESS_MESSAGE_CONSUMER))
                .map(consumer -> (Consumer<Object>) consumer);
    }

    private String formatSearchStart(ChatClientRequest chatClientRequest) {
        String query = chatClientRequest.prompt().getInstructions().stream()
                .filter(m -> m instanceof UserMessage).reduce((first, second) -> second)
                .map(m -> ((UserMessage) m).getText()).filter(StringUtils::hasText).orElse("(empty)");
        String filterExpression = chatClientRequest.context().get(RAG_FILTER_EXPRESSION).toString();
        VectorStoreService.SearchRequestOption option = this.vectorStoreService.getSearchRequestOption();
        return "Searching VectorDB documents...\n" +
                "- Query: `" + query + "`\n" +
                "- Filter: `" + filterExpression + "`\n" +
                "- Top K: " + option.topK() + "\n" +
                "- Similarity Threshold: " + option.similarityThreshold();
    }

    private String formatRetrievedDocuments(List<Document> results) {
        if (results.isEmpty())
            return "No matching VectorDB documents were found.";
        Map<String, String> docInfoTitles = this.vectorStoreDocumentsReader.read().stream()
                .collect(Collectors.toMap(VectorStoreDocumentInfo::docInfoId, VectorStoreDocumentInfo::title,
                        (current, ignored) -> current));
        return "Retrieved " + results.size() + " document chunks from VectorDB.\n" +
                IntStream.range(0, results.size()).mapToObj(i ->
                                formatRetrievedDocument(results.get(i), i, docInfoTitles))
                        .collect(Collectors.joining("\n"));
    }

    private String formatRetrievedDocument(Document document, int index, Map<String, String> docInfoTitles) {
        String title = resolveDocumentTitle(document, docInfoTitles);
        String score = Optional.ofNullable(document.getScore()).map(value -> String.format(Locale.ROOT, "%.3f", value))
                .orElse("n/a");
        String excerpt = Optional.ofNullable(document.getText()).map(text -> text.replaceAll("\\s+", " ").trim())
                .filter(StringUtils::hasText)
                .map(text -> text.length() <= 140 ? text : text.substring(0, 137) + "...")
                .orElse("No excerpt available.");
        return String.format("- %d. `%s` (score: %s): %s", index + 1, title, score, excerpt);
    }

    private String resolveDocumentTitle(Document document, Map<String, String> docInfoTitles) {
        Object docInfoId = document.getMetadata().get(VectorStoreService.DOC_INFO_ID);
        if (docInfoId != null) {
            String title = docInfoTitles.get(docInfoId.toString());
            if (StringUtils.hasText(title))
                return title;
        }
        return Optional.ofNullable(document.getMetadata().get("source")).map(Object::toString)
                .filter(StringUtils::hasText)
                .orElseGet(() -> Optional.ofNullable(document.getId()).filter(StringUtils::hasText)
                        .orElse("document"));
    }

    private static void printSearchResults(List<Document> results) {
        logger.debug("Retrieved Documents Count - {}", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document document = results.get(i);
            logger.debug("Retrieved Document {}, Score: {}\n{}", i + 1, document.getScore(), document.getText());
        }
    }

}
