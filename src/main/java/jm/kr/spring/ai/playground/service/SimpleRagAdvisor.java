package jm.kr.spring.ai.playground.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@Service
public class SimpleRagAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleRagAdvisor.class);

    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public SimpleRagAdvisor(VectorStore vectorStore) {
        this.questionAnswerAdvisor = new QuestionAnswerAdvisor(vectorStore);
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        AdvisedResponse advisedResponse = this.questionAnswerAdvisor.aroundCall(advisedRequest, chain);
        loggingRetrievedDocuments(advisedRequest);
        return advisedResponse;
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        Flux<AdvisedResponse> advisedResponseFlux = this.questionAnswerAdvisor.aroundStream(advisedRequest, chain);
        loggingRetrievedDocuments(advisedRequest);
        return advisedResponseFlux;
    }

    private static void loggingRetrievedDocuments(AdvisedRequest advisedRequest) {
        printSearchResults(
                Optional.ofNullable(advisedRequest.adviseContext().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS))
                        .stream().map(documents -> (List<Document>) documents).flatMap(List::stream).toList());
    }

    private static void printSearchResults(List<Document> results) {
        logger.debug("Retrieved Documents Count - {}", results.size());
        for (int i = 0; i < results.size(); i++) {
            Document document = results.get(i);
            logger.debug("Retrieved Document %d, Score: {}\n{}", i + 1, document.getScore(), document.getText());
        }
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return this.questionAnswerAdvisor.getOrder();
    }
}
