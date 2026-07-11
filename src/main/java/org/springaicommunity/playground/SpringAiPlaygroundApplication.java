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
package org.springaicommunity.playground;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Inline;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.page.TargetElement;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.theme.lumo.Lumo;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.toolsearch.ToolSearchToolCallingAdvisor;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.ToolSearch.IndexType;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.ToolSearch.VectorStoreMode;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.HybridToolIndex;
import org.springaicommunity.playground.service.chat.LlmWindowChatMemory;
import org.springaicommunity.playground.service.chat.PersistentToolIndex;
import org.springaicommunity.playground.service.agent.AgentLoopManager;
import org.springaicommunity.playground.webui.GoogleAnalyticsNavigationListener;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.index.vectorstore.VectorToolIndex;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Hooks;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Predicate;

@StyleSheet(Lumo.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@Push
@PWA(name = "Spring AI Playground", shortName = "Playground", offlinePath = "offline.html")
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SpringAiPlaygroundApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(SpringAiPlaygroundApplication.class, args);
    }

    @Override
    public void configurePage(AppShellSettings settings) {
        if (!isTelemetryActive()) {
            settings.addInlineWithContents(Inline.Position.PREPEND,
                    "window['ga-disable-" + GoogleAnalyticsNavigationListener.MEASUREMENT_ID + "'] = true;",
                    Inline.Wrapping.JAVASCRIPT);
            return;
        }
        String gtmContainerId = "GTM-PVX8227Q";
        String gtmDataLayerInit = """
                window.dataLayer = window.dataLayer || [];
                window.dataLayer.push({
                  app_surface: 'web-app',
                  app_name: 'spring-ai-playground'
                });
                """;
        String gtmScript = String.format(
                "(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':"
                        + "new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],"
                        + "j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src="
                        + "'https://www.googletagmanager.com/gtm.js?id='+i+dl;"
                        + "f.parentNode.insertBefore(j,f);})(window,document,'script','dataLayer','%s');",
                gtmContainerId
        );
        settings.addInlineWithContents(Inline.Position.PREPEND, gtmDataLayerInit, Inline.Wrapping.JAVASCRIPT);
        settings.addInlineWithContents(Inline.Position.PREPEND, gtmScript, Inline.Wrapping.JAVASCRIPT);

        String gtmNoscript = String.format(
                "<noscript><iframe src=\"https://www.googletagmanager.com/ns.html?id=%s\" height=\"0\" width=\"0\" style=\"display:none;visibility:hidden\"></iframe></noscript>",
                gtmContainerId);
        settings.addInlineWithContents(TargetElement.BODY, Inline.Position.PREPEND, gtmNoscript, Inline.Wrapping.NONE);

        String ga4Snippet = String.format("""
                window.dataLayer = window.dataLayer || [];
                window.gtag = window.gtag || function(){window.dataLayer.push(arguments);};
                gtag('js', new Date());
                gtag('config', '%1$s', { send_page_view: false });
                (function(d){var s=d.createElement('script');s.async=true;\
                s.src='https://www.googletagmanager.com/gtag/js?id=%1$s';\
                d.head.appendChild(s);})(document);
                """, GoogleAnalyticsNavigationListener.MEASUREMENT_ID);
        settings.addInlineWithContents(Inline.Position.PREPEND, ga4Snippet, Inline.Wrapping.JAVASCRIPT);
    }

    private static boolean isTelemetryActive() {
        return isProductionMode() && !GoogleAnalyticsNavigationListener.isTelemetryOptedOut();
    }

    private static boolean isProductionMode() {
        try {
            return VaadinService.getCurrent().getDeploymentConfiguration().isProductionMode();
        } catch (RuntimeException e) {
            return false;
        }
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
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository, SpringAiPlaygroundOptions options) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository)
                .maxMessages(options.chat().historyMaxMessages()).build();
    }

    @Bean
    @ConditionalOnMissingBean(MessageChatMemoryAdvisor.class)
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory, SpringAiPlaygroundOptions options,
            ObjectProvider<ChatHistoryService> chatHistoryServiceProvider) {
        return MessageChatMemoryAdvisor.builder(new LlmWindowChatMemory(chatMemory,
                options.chat().memoryMaxMessages(), chatHistoryServiceProvider)).build();
    }

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public SimpleVectorStore simpleVectorStore(EmbeddingModel embeddingModel,
            ObservationRegistry observationRegistry) {
        return SimpleVectorStore.builder(embeddingModel)
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    public Optional<EmbeddingOptions> embeddingOptions(ApplicationContext applicationContext) {
        return resolveEmbeddingOptions(applicationContext);
    }

    private static Optional<EmbeddingOptions> resolveEmbeddingOptions(ApplicationContext applicationContext) {
        return Arrays.stream(applicationContext.getBeanDefinitionNames())
                .filter(name -> name.contains("EmbeddingProperties")).findFirst()
                .map(applicationContext::getBean).map(o -> {
                    try {
                        return o.getClass().getMethod("toOptions").invoke(o);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(
                                "Failed to invoke toOptions() on " + o.getClass().getName(), e);
                    }
                }).map(o -> (EmbeddingOptions) o);
    }

    @Bean
    public SimpleLoggerAdvisor simpleLoggerAdvisor() {
        return new SimpleLoggerAdvisor();
    }

    @Bean
    public ToolCallingAdvisor toolCallingAdvisor(AgentLoopManager agentLoopManager) {
        return ToolCallingAdvisor.builder()
                .toolCallingManager(agentLoopManager)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.playground.chat.tool-search", name = "enabled",
            matchIfMissing = true)
    @ConditionalOnMissingBean(ToolIndex.class)
    public ToolIndex toolIndex(SpringAiPlaygroundOptions playgroundOptions, EmbeddingModel embeddingModel,
            ObjectProvider<VectorStore> vectorStore, ObservationRegistry observationRegistry,
            ApplicationContext applicationContext, Path springAiPlaygroundHomeDir) {
        SpringAiPlaygroundOptions.ToolSearch toolSearch = playgroundOptions.chat().toolSearch();
        boolean exactName = toolSearch.indexType() != IndexType.VECTOR;
        if (toolSearch.vectorStore() == VectorStoreMode.SHARED) {
            VectorToolIndex vectorToolIndex = new VectorToolIndex(vectorStore.getObject());
            return exactName ? new HybridToolIndex(vectorToolIndex) : vectorToolIndex;
        }
        SimpleVectorStore dedicatedStore = SimpleVectorStore.builder(embeddingModel)
                .observationRegistry(observationRegistry).build();
        Optional<EmbeddingOptions> options = resolveEmbeddingOptions(applicationContext);
        String signature = PersistentToolIndex.signatureOf(embeddingModel.getClass().getSimpleName(),
                options.map(EmbeddingOptions::getModel).orElse(null),
                options.map(EmbeddingOptions::getDimensions).orElse(null));
        return new PersistentToolIndex(dedicatedStore, signature, springAiPlaygroundHomeDir, exactName, true);
    }

    private static final String DYNAMIC_TOOLS_SUFFIX = """

            You also have a `toolSearchTool`; most of your tools stay hidden until you discover them:
            1. Search with a short phrase for the capability the user needs. The result is only a LIST OF \
            TOOL NAMES — not an answer — and each named tool then becomes directly callable.
            2. Choose the tool that fits the user's request and take its arguments from the user's own \
            message; never invent values or reuse the examples in tool descriptions (sample cities, sample \
            data).
            3. If a required argument is missing, ask the user for it; once every required value is known, \
            call the tool, then read its result and answer the user.
            4. If no tool fits, tell the user that no suitable tool is available; do not force an unrelated \
            one.""";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.playground.chat.tool-search", name = "enabled",
            matchIfMissing = true)
    public ToolSearchToolCallingAdvisor dynamicToolCallingAdvisor(AgentLoopManager agentLoopManager,
            ToolIndex toolIndex, SpringAiPlaygroundOptions playgroundOptions) {
        return ToolSearchToolCallingAdvisor.builder()
                .toolCallingManager(agentLoopManager)
                .toolIndex(toolIndex)
                .maxResults(playgroundOptions.chat().toolSearch().maxResults())
                .systemMessageSuffix(DYNAMIC_TOOLS_SUFFIX)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatClientBuilder, Advisor[] advisors) {
        Advisor[] base = Arrays.stream(advisors)
                .filter(advisor -> !(advisor instanceof ToolCallingAdvisor)).toArray(Advisor[]::new);
        return chatClientBuilder.defaultAdvisors(base).build();
    }

}
