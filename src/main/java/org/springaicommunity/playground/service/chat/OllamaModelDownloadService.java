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
package org.springaicommunity.playground.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OllamaModelDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaModelDownloadService.class);

    public record PullProgress(String status, Long total, Long completed) {}

    private final OllamaApi ollamaApi;
    private final boolean ollamaActive;
    private volatile Set<String> localModels = Set.of();
    private volatile boolean cacheLoaded;

    public OllamaModelDownloadService(ObjectProvider<OllamaApi> ollamaApiProvider, ChatModel chatModel) {
        this.ollamaApi = ollamaApiProvider.getIfAvailable();
        this.ollamaActive = ChatProvider.from(chatModel) == ChatProvider.OLLAMA;
    }

    public boolean isEnabled() {
        return Objects.nonNull(this.ollamaApi) && this.ollamaActive;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshLocalModels() {
        if (!isEnabled()) return;
        try {
            this.localModels = this.ollamaApi.listModels().models().stream().map(OllamaApi.Model::name)
                    .collect(Collectors.toUnmodifiableSet());
            this.cacheLoaded = true;
            logger.info("Cached {} local Ollama models", this.localModels.size());
        } catch (Exception e) {
            logger.warn("Failed to list local Ollama models: {}", e.getMessage());
        }
    }

    // Fail-open while the cache is unknown (Ollama unreachable): blocking chat on a stale answer is worse
    // than letting the request surface the real connection error.
    public boolean isDownloaded(String model) {
        if (!isEnabled() || !this.cacheLoaded || Objects.isNull(model) || model.isBlank()) return true;
        return this.localModels.contains(withDefaultTag(model));
    }

    public Flux<PullProgress> pull(String model) {
        return this.ollamaApi.pullModel(new OllamaApi.PullModelRequest(withDefaultTag(model)))
                .map(response -> new PullProgress(response.status(), response.total(), response.completed()))
                .doOnComplete(this::refreshLocalModels);
    }

    // Ollama stores untagged pulls under ":latest", so cache lookups must compare the same way.
    static String withDefaultTag(String model) {
        return model.contains(":") ? model : model + ":latest";
    }
}
