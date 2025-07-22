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
package jm.kr.spring.ai.playground.service;

import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jm.kr.spring.ai.playground.service.chat.ChatService.RAG_FILTER_EXPRESSION;
import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class SpringAiPlaygroundRagAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiPlaygroundRagAdvisor.class);

    private final VectorStoreService vectorStoreService;

    public SpringAiPlaygroundRagAdvisor(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        return isFilterExpressionMissing(chatClientRequest) ? chatClientRequest :
                loggingRetrievedDocuments(buildRetrievalAugmentationAdvisor(chatClientRequest).before(chatClientRequest, advisorChain));
    }

    private ChatClientRequest loggingRetrievedDocuments(ChatClientRequest chatClientRequest) {
        printSearchResults(Optional.ofNullable(chatClientRequest.context().get(DOCUMENT_CONTEXT))
                .stream().map(documents -> (List<Document>) documents).flatMap(List::stream).toList());
        return chatClientRequest;
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

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(ChatClientRequest chatClientRequest) {
        return RetrievalAugmentationAdvisor.builder().documentRetriever(query -> vectorStoreService.search(query.text(),
                chatClientRequest.context().get(RAG_FILTER_EXPRESSION).toString())).build();
    }

    private static void printSearchResults(List<Document> results) {
        logger.debug("Retrieved Documents Count - {}", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document document = results.get(i);
            logger.debug("Retrieved Document {}, Score: {}\n{}", i + 1, document.getScore(), document.getText());
        }
    }

}
