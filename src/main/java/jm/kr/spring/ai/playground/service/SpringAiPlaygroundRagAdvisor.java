package jm.kr.spring.ai.playground.service;

import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jm.kr.spring.ai.playground.service.chat.ChatService.RAG_FILTER_EXPRESSION;
import static org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

@Service
public class SpringAiPlaygroundRagAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiPlaygroundRagAdvisor.class);

    private final VectorStoreService vectorStoreService;

    public SpringAiPlaygroundRagAdvisor(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        return isFilterExpressionMissing(advisedRequest) ? chain.nextAroundCall(advisedRequest) :
                loggingRetrievedDocuments(buildRetrievalAugmentationAdvisor(advisedRequest)
                        .aroundCall(advisedRequest, chain));
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        return isFilterExpressionMissing(advisedRequest) ? chain.nextAroundStream(advisedRequest) :
                buildRetrievalAugmentationAdvisor(advisedRequest)
                        .aroundStream(advisedRequest, chain).map(this::processOnFinishReason);
    }

    private boolean isFilterExpressionMissing(AdvisedRequest advisedRequest) {
        boolean isMissing = Objects.isNull(advisedRequest.advisorParams().get(RAG_FILTER_EXPRESSION));
        if (isMissing)
            logger.debug("Document retrieval was skipped.");
        return isMissing;
    }

    private RetrievalAugmentationAdvisor buildRetrievalAugmentationAdvisor(AdvisedRequest advisedRequest) {
        return RetrievalAugmentationAdvisor.builder().documentRetriever(query -> vectorStoreService.search(query.text(),
                advisedRequest.advisorParams().get(RAG_FILTER_EXPRESSION).toString())).build();
    }

    private AdvisedResponse processOnFinishReason(AdvisedResponse advisedResponse) {
        return advisedResponse.response().getResults().stream().map(Optional::ofNullable)
                .filter(opt -> opt.map(Generation::getMetadata).map(
                        ChatGenerationMetadata::getFinishReason).filter(StringUtils::hasText).isPresent())
                .anyMatch(Optional::isPresent) ? loggingRetrievedDocuments(advisedResponse) : advisedResponse;
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

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
