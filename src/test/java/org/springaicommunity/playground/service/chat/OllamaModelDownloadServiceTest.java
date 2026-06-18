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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaModelDownloadServiceTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OllamaApi> providerOf(OllamaApi api) {
        ObjectProvider<OllamaApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(api);
        return provider;
    }

    private static ChatModel ollamaModel() {
        return mock(OllamaChatModel.class);
    }

    private static OllamaApi.ListModelResponse modelList(String... names) {
        List<OllamaApi.Model> models = java.util.Arrays.stream(names)
                .map(name -> new OllamaApi.Model(name, name, null, null, null, null)).toList();
        return new OllamaApi.ListModelResponse(models);
    }

    @Test
    void testDisabledWithoutOllamaProvider() {
        OllamaModelDownloadService service = new OllamaModelDownloadService(providerOf(null), ollamaModel());
        assertFalse(service.isEnabled());
        assertTrue(service.isDownloaded("qwen3.5:2b"));
    }

    @Test
    void testDisabledWhenActiveProviderIsNotOllama() {
        OllamaApi api = mock(OllamaApi.class);
        OllamaModelDownloadService service =
                new OllamaModelDownloadService(providerOf(api), mock(OpenAiChatModel.class));
        assertFalse(service.isEnabled());
        assertTrue(service.isDownloaded("gpt-5.4-mini"));
    }

    @Test
    void testIsDownloadedAfterRefresh() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenReturn(modelList("qwen3.5:4b-mlx", "llama3:latest"));
        OllamaModelDownloadService service = new OllamaModelDownloadService(providerOf(api), ollamaModel());
        service.refreshLocalModels();

        assertTrue(service.isDownloaded("qwen3.5:4b-mlx"));
        assertTrue(service.isDownloaded("llama3"));
        assertFalse(service.isDownloaded("qwen3.6:35b-mlx"));
        assertTrue(service.isDownloaded(null));
        assertTrue(service.isDownloaded(""));
    }

    @Test
    void testFailOpenWhenListingFails() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenThrow(new IllegalStateException("ollama down"));
        OllamaModelDownloadService service = new OllamaModelDownloadService(providerOf(api), ollamaModel());
        service.refreshLocalModels();

        assertTrue(service.isDownloaded("qwen3.5:4b-mlx"));
    }

    @Test
    void testPullRefreshesCacheOnComplete() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.listModels()).thenReturn(modelList()).thenReturn(modelList("qwen3.6:35b-mlx"));
        when(api.pullModel(any(OllamaApi.PullModelRequest.class))).thenReturn(
                Flux.just(new OllamaApi.ProgressResponse("pulling", null, 100L, 50L),
                        new OllamaApi.ProgressResponse("success", null, 100L, 100L)));
        OllamaModelDownloadService service = new OllamaModelDownloadService(providerOf(api), ollamaModel());
        service.refreshLocalModels();
        assertFalse(service.isDownloaded("qwen3.6:35b-mlx"));

        List<OllamaModelDownloadService.PullProgress> progress = service.pull("qwen3.6:35b-mlx")
                .collectList().block();

        assertEquals(2, progress.size());
        assertEquals("pulling", progress.get(0).status());
        assertEquals(100L, progress.get(0).total());
        assertEquals(50L, progress.get(0).completed());
        verify(api, times(2)).listModels();
        assertTrue(service.isDownloaded("qwen3.6:35b-mlx"));
    }

    @Test
    void testWithDefaultTag() {
        assertEquals("llama3:latest", OllamaModelDownloadService.withDefaultTag("llama3"));
        assertEquals("qwen3.5:2b-mlx", OllamaModelDownloadService.withDefaultTag("qwen3.5:2b-mlx"));
    }
}
