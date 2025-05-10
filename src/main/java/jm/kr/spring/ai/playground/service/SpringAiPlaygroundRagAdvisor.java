package jm.kr.spring.ai.playground.service;

import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
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
    public AdvisedRequest before(AdvisedRequest advisedRequest) {
        return isFilterExpressionMissing(advisedRequest) ? advisedRequest :
                buildRetrievalAugmentationAdvisor(advisedRequest).before(advisedRequest);
    }

    @Override
    public AdvisedResponse after(AdvisedResponse advisedResponse) {
        return loggingRetrievedDocuments(advisedResponse);
    }

    @Override
    public int getOrder() {
        return 0;
    }

    private boolean isFilterExpressionMissing(AdvisedRequest advisedRequest) {
        boolean isMissing = Objects.isNull(advisedRequest.adviseContext().get(RAG_FILTER_EXPRESSION));
        if (isMissing)
            logger.debug("Document retrieval was skipped.");
        return isMissing;
    }

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(AdvisedRequest advisedRequest) {
        return RetrievalAugmentationAdvisor.builder().documentRetriever(query -> vectorStoreService.search(query.text(),
                advisedRequest.adviseContext().get(RAG_FILTER_EXPRESSION).toString())).build();
    }

    private static AdvisedResponse loggingRetrievedDocuments(AdvisedResponse advisedResponse) {
        printSearchResults(Optional.ofNullable(advisedResponse.adviseContext().get(DOCUMENT_CONTEXT))
                .stream().map(documents -> (List<Document>) documents).flatMap(List::stream).toList());
        return advisedResponse;
    }

    private static void printSearchResults(List<Document> results) {
        logger.debug("Retrieved Documents Count - {}", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document document = results.get(i);
            logger.debug("Retrieved Document {}, Score: {}\n{}", i + 1, document.getScore(), document.getText());
        }
    }
}
