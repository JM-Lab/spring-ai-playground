package jm.kr.spring.ai.playground;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryPersistenceService;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentPersistenceService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION;

@Component
public class SpringAiPlaygroundLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiPlaygroundLifecycleManager.class);

    private final ChatHistoryPersistenceService chatHistoryPersistenceService;
    private final ChatHistoryService chatHistoryService;
    private final VectorStore vectorStore;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService;

    public SpringAiPlaygroundLifecycleManager(ChatHistoryPersistenceService chatHistoryPersistenceService,
            ChatHistoryService chatHistoryService, VectorStore vectorStore,
            VectorStoreDocumentService vectorStoreDocumentService,
            VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService) {
        this.chatHistoryPersistenceService = chatHistoryPersistenceService;
        this.chatHistoryService = chatHistoryService;
        this.vectorStore = vectorStore;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.vectorStoreDocumentPersistenceService = vectorStoreDocumentPersistenceService;
    }

    @PostConstruct
    public void onStartup() throws IOException {
        logger.info("SpringAiPlaygroundLifecycleManager started.");
        if (this.vectorStore instanceof SimpleVectorStore)
            vectorStoreDocumentPersistenceService.loads().forEach(vectorStoreDocumentInfo -> {
                vectorStoreDocumentService.updateDocumentInfo(vectorStoreDocumentInfo,
                        vectorStoreDocumentInfo.title());
                this.vectorStore.add(vectorStoreDocumentInfo.documentListSupplier().get());
                vectorStoreDocumentInfo.changeDocumentListSupplier(() -> this.vectorStore.similaritySearch(
                        SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION.apply(
                                List.of(vectorStoreDocumentInfo.docInfoId()))));
            });
        chatHistoryPersistenceService.loads().forEach(chatHistoryService::putIfAbsentChatHistory);
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("SpringAiPlaygroundLifecycleManager shutting down");
        chatHistoryService.getChatHistoryList().forEach(chatHistory -> {
            try {
                chatHistoryPersistenceService.save(chatHistory);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        });
        vectorStoreDocumentService.getDocumentList().forEach(doc -> {
            try {
                vectorStoreDocumentPersistenceService.save(doc);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}