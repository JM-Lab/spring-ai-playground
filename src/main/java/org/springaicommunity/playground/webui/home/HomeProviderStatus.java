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
package org.springaicommunity.playground.webui.home;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.springaicommunity.playground.webui.home.HomeUi.divider;
import static org.springaicommunity.playground.webui.home.HomeUi.mutedLabel;

class HomeProviderStatus extends Div {

    private static final Logger logger = LoggerFactory.getLogger(HomeProviderStatus.class);
    private static final String CONFIGURE_URL =
            "https://spring-ai-community.github.io/spring-ai-playground/getting-started/";

    private static volatile Integer cachedEmbeddingDimensions;
    private static volatile boolean embeddingProbeAttempted;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final Optional<EmbeddingOptions> embeddingOptions;
    private final Environment environment;

    private Div chatReadinessDot;
    private Span chatReadinessLabel;
    private Span embeddingDimensionsLabel;

    HomeProviderStatus(ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            Optional<EmbeddingOptions> embeddingOptions,
            Environment environment) {
        this.chatModelProvider = chatModelProvider;
        this.embeddingModelProvider = embeddingModelProvider;
        this.embeddingOptions = embeddingOptions;
        this.environment = environment;

        setWidthFull();
        getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "0.6rem");
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        render();
        UI ui = event.getUI();
        probeChatReadiness(ui);
        probeEmbeddingDimensions(ui);
    }

    private void render() {
        removeAll();
        add(buildChatPill());
        Div embeddingPill = buildEmbeddingPill();
        if (embeddingPill != null) {
            add(embeddingPill);
        }
    }

    private Div buildChatPill() {
        Div pill = pillContainer();
        ChatModel chatModel = chatModelProvider.getIfAvailable();

        if (chatModel == null) {
            pill.add(statusDot("var(--lumo-error-color)"));
            Span warn = new Span("No model provider configured");
            warn.getStyle().set("font-weight", "500");
            pill.add(warn);
            Anchor configureLink = new Anchor(CONFIGURE_URL, "Set up →");
            configureLink.setTarget("_blank");
            configureLink.getElement().setAttribute("rel", "noopener");
            configureLink.getStyle()
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("text-decoration", "none")
                    .set("font-weight", "500");
            pill.add(configureLink);
            this.chatReadinessDot = null;
            this.chatReadinessLabel = null;
            return pill;
        }

        this.chatReadinessDot = statusDot("var(--lumo-contrast-30pct)");
        pill.add(this.chatReadinessDot);

        String provider = humanProvider(chatModel.getClass().getSimpleName(), "ChatModel");
        Span providerLabel = new Span(provider);
        providerLabel.getStyle().set("font-weight", "600");
        pill.add(providerLabel);

        String model = safeGetChatModel(chatModel);
        if (model != null && !model.isBlank()) {
            pill.add(divider(), mutedLabel(model));
        }

        this.chatReadinessLabel = mutedLabel("Checking…");
        pill.add(divider(), this.chatReadinessLabel);
        return pill;
    }

    private Div buildEmbeddingPill() {
        if (embeddingModelProvider.getIfAvailable() == null && embeddingOptions.isEmpty()) {
            return null;
        }
        Div pill = pillContainer();
        pill.add(statusDot("var(--lumo-primary-color)"));

        Span label = new Span("Embedding");
        label.getStyle().set("font-weight", "600");
        pill.add(label);

        String model = resolveEmbeddingModel();
        if (model != null && !model.isBlank()) {
            pill.add(divider(), mutedLabel(model));
        }

        this.embeddingDimensionsLabel = mutedLabel(cachedEmbeddingDimensions != null
                ? cachedEmbeddingDimensions + "d"
                : "…d");
        pill.add(divider(), this.embeddingDimensionsLabel);
        return pill;
    }

    private String resolveEmbeddingModel() {
        String name = embeddingOptions.map(EmbeddingOptions::getModel).orElse(null);
        if (name != null && !name.isBlank()) return name;

        EmbeddingModel em = embeddingModelProvider.getIfAvailable();
        if (em != null) {
            try {
                Object opts = em.getClass().getMethod("getDefaultOptions").invoke(em);
                if (opts != null) {
                    Object model = opts.getClass().getMethod("getModel").invoke(opts);
                    if (model != null) {
                        String str = model.toString();
                        if (!str.isBlank()) return str;
                    }
                }
            } catch (Exception ignored) {
                // fall through to property lookup
            }
            String className = em.getClass().getSimpleName().toLowerCase();
            String[] keys;
            if (className.contains("ollama")) {
                keys = new String[] {"spring.ai.ollama.embedding.options.model"};
            } else if (className.contains("openai")) {
                keys = new String[] {"spring.ai.openai.embedding.options.model"};
            } else {
                keys = new String[0];
            }
            for (String key : keys) {
                String value = environment.getProperty(key);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return null;
    }

    private void probeChatReadiness(UI ui) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) return;
        String className = chatModel.getClass().getSimpleName().toLowerCase();

        CompletableFuture.supplyAsync(() -> {
            if (className.contains("ollama")) {
                String baseUrl = environment.getProperty("spring.ai.ollama.base-url",
                        "http://localhost:11434");
                return ping(baseUrl);
            }
            if (className.contains("openai")) {
                String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
                return apiKey != null && !apiKey.isBlank();
            }
            return true;
        }).thenAccept(ready -> ui.access(() -> applyChatReadiness(ready)))
                .exceptionally(ex -> {
                    logger.debug("chat readiness probe failed", ex);
                    ui.access(() -> applyChatReadiness(false));
                    return null;
                });
    }

    private void applyChatReadiness(boolean ready) {
        if (chatReadinessDot == null || chatReadinessLabel == null) return;
        chatReadinessDot.getStyle().set("background-color",
                ready ? "var(--lumo-success-color)" : "var(--lumo-error-color)");
        chatReadinessLabel.setText(ready ? "Ready" : "Not reachable");
    }

    private void probeEmbeddingDimensions(UI ui) {
        if (cachedEmbeddingDimensions != null) return;
        if (embeddingProbeAttempted) return;
        EmbeddingModel em = embeddingModelProvider.getIfAvailable();
        if (em == null) return;
        embeddingProbeAttempted = true;
        CompletableFuture.supplyAsync(() -> {
            try {
                return em.dimensions();
            } catch (Exception e) {
                logger.debug("embedding dimensions probe failed", e);
                return null;
            }
        }).thenAccept(dims -> {
            if (dims != null) {
                cachedEmbeddingDimensions = dims;
            }
            ui.access(() -> {
                if (embeddingDimensionsLabel != null) {
                    embeddingDimensionsLabel.setText(dims != null ? dims + "d" : "dim n/a");
                }
            });
        });
    }

    private static boolean ping(String baseUrl) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private static Div pillContainer() {
        Div pill = new Div();
        pill.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "0.55rem")
                .set("padding", "0.4rem 0.9rem")
                .set("border-radius", "999px")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("background-color", "var(--lumo-base-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("width", "fit-content");
        return pill;
    }

    private static Div statusDot(String color) {
        Div dot = new Div();
        dot.getStyle()
                .set("width", "8px")
                .set("height", "8px")
                .set("border-radius", "50%")
                .set("background-color", color)
                .set("flex-shrink", "0");
        return dot;
    }

    private static String humanProvider(String simpleName, String suffix) {
        String base = simpleName.replace(suffix, "");
        if (base.equalsIgnoreCase("OpenAi")) return "OpenAI";
        return base;
    }

    private static String safeGetChatModel(ChatModel chatModel) {
        try {
            var options = chatModel.getOptions();
            return options != null ? options.getModel() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
