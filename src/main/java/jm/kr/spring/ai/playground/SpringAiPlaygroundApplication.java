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
package jm.kr.spring.ai.playground;

import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.TargetElement;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

@Push
@PWA(name = "Spring AI Playground", shortName = "Playground", offlinePath = "offline.html")
@JavaScript("./playground/pwa-installer.js")
@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiPlaygroundApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiPlaygroundApplication.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        String gtmContainerId = "GTM-PVX8227Q";
        String gtmScript = String.format(
                "(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':"
                        + "new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],"
                        + "j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src="
                        + "'https://www.googletagmanager.com/gtm.js?id='+i+dl;"
                        + "f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','%s');",
                gtmContainerId
        );
        settings.addInlineWithContents(Inline.Position.PREPEND, gtmScript, Inline.Wrapping.JAVASCRIPT);

        String gtmNoscript = String.format(
                "<noscript><iframe src=\"https://www.googletagmanager.com/ns.html?id=%s\" height=\"0\" width=\"0\" style=\"display:none;visibility:hidden\"></iframe></noscript>",
                gtmContainerId);
        settings.addInlineWithContents(TargetElement.BODY, Inline.Position.PREPEND, gtmNoscript, Inline.Wrapping.NONE);
    }

    @Bean
    public Path springAiPlaygroundHomeDir(@Value("${spring.ai.playground.user-home}") String userHomeDir,
            @Value("${spring.application.name}") String applicationName) {
        Path homeDir = Path.of(Optional.ofNullable(userHomeDir).filter(Predicate.not(String::isBlank))
                .orElse(System.getProperty("user.home")), applicationName);
        if (!homeDir.toFile().exists())
            homeDir.toFile().mkdirs();
        return homeDir;
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemoryRepository.class)
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).maxMessages(10).build();
    }

    @Bean
    @ConditionalOnMissingBean(MessageChatMemoryAdvisor.class)
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
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

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, Advisor[] advisors) {
        return chatClientBuilder.defaultAdvisors(advisors).build();
    }

}
