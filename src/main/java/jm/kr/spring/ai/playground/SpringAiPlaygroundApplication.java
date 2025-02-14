package jm.kr.spring.ai.playground;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import jm.kr.spring.ai.playground.service.vectorstore.inmemory.SimpleVectorStoreWithFilterExpression;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.Optional;

@Push
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiPlaygroundApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiPlaygroundApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStoreWithFilterExpression.builder(embeddingModel).build();
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
