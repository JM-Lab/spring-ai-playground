package jm.kr.spring.ai.playground;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentPersistenceService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION;

@Push
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiPlaygroundApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiPlaygroundApplication.class, args);
    }

    @Bean
    public Path springAiPlaygroundHomeDir() {
        Path homeDir = Path.of(System.getProperty("user.home"), "spring-ai-playground");
        if (!homeDir.toFile().exists())
            homeDir.toFile().mkdirs();
        return homeDir;
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel,
            VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService,
            VectorStoreDocumentService vectorStoreDocumentService) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                vectorStoreDocumentPersistenceService.loads().forEach(vectorStoreDocumentInfo -> {
                    vectorStoreDocumentService.updateDocumentInfo(vectorStoreDocumentInfo,
                            vectorStoreDocumentInfo.title());
                    simpleVectorStore.add(vectorStoreDocumentInfo.documentListSupplier().get());
                    vectorStoreDocumentInfo.changeDocumentListSupplier(() -> simpleVectorStore.similaritySearch(
                            SEARCH_ALL_REQUEST_WITH_DOC_INFO_IDS_FUNCTION.apply(
                                    List.of(vectorStoreDocumentInfo.docInfoId()))));
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Runtime.getRuntime().addShutdownHook(Thread.ofVirtual().unstarted(() ->
                vectorStoreDocumentService.getDocumentList().forEach(doc -> {
                    try {
                        vectorStoreDocumentPersistenceService.save(doc);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
        ));
        return simpleVectorStore;
    }

    @Bean
    public Optional<EmbeddingOptions> embeddingOptions(ApplicationContext applicationContext) {
        return Arrays.stream(applicationContext.getBeanDefinitionNames())
                .filter(name -> name.contains("EmbeddingProperties")).findFirst()
                .map(applicationContext::getBean).map(o -> {
                    try {
                        return o.getClass().getMethod("getOptions").invoke(o);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).map(o -> (EmbeddingOptions) o);
    }

}
