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
import org.springaicommunity.playground.service.chat.VisionCapabilityService.VisionSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionCapabilityServiceTest {

    private static final Map<String, Object> VISION_TENSORS =
            Map.of("qwen35.vision.attention.head_count", 8, "qwen35.vision.block_count", 24);
    private static final Map<String, Object> NO_TENSORS = Map.of("qwen35.block_count", 24);

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OllamaApi> providerOf(OllamaApi api) {
        ObjectProvider<OllamaApi> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(api);
        return provider;
    }

    private static OllamaApi.ShowModelResponse showResponse(List<String> capabilities, Map<String, Object> modelInfo) {
        return new OllamaApi.ShowModelResponse(null, null, null, null, null, null, null, modelInfo, null,
                capabilities, null);
    }

    private static OllamaApi.ShowModelResponse showResponseWithProjector(List<String> capabilities,
            Map<String, Object> modelInfo, Map<String, Object> projectorInfo) {
        return new OllamaApi.ShowModelResponse(null, null, null, null, null, null, null, modelInfo, projectorInfo,
                capabilities, null);
    }

    @Test
    void testOpenAiAlwaysSupportsVision() {
        VisionCapabilityService service =
                new VisionCapabilityService(providerOf(mock(OllamaApi.class)), mock(OpenAiChatModel.class));
        assertTrue(service.supportsVision("gpt-5.4-mini"));
        assertEquals(VisionSupport.SUPPORTED, service.check("gpt-5.4-mini"));
    }

    @Test
    void testGenericProviderSupportsVision() {
        VisionCapabilityService service = new VisionCapabilityService(providerOf(null), mock(ChatModel.class));
        assertTrue(service.supportsVision("some-model"));
    }

    @Test
    void testOllamaVisionModelWithTensorsIsSupported() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponse(List.of("completion", "vision"), VISION_TENSORS));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertTrue(service.supportsVision("qwen3.5:4b"));
        assertEquals(VisionSupport.SUPPORTED, service.check("qwen3.5:4b"));
    }

    @Test
    void testSeparateProjectorVisionModelIsSupported() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponseWithProjector(List.of("completion", "vision"), NO_TENSORS,
                Map.of("clip.has_vision_encoder", true, "clip.vision.embedding_length", 1152)));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertTrue(service.supportsVision("llava"));
        assertEquals(VisionSupport.SUPPORTED, service.check("llava"));
    }

    @Test
    void testMlxVisionFlagWithoutTensorsIsCapabilityWithoutTensors() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponse(List.of("completion", "vision", "thinking"), NO_TENSORS));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertFalse(service.supportsVision("qwen3.5:4b-mlx"));
        assertEquals(VisionSupport.CAPABILITY_WITHOUT_TENSORS, service.check("qwen3.5:4b-mlx"));
    }

    @Test
    void testPlainModelIsNoCapability() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponse(List.of("completion", "tools"), NO_TENSORS));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertFalse(service.supportsVision("qwen3.5:2b"));
        assertEquals(VisionSupport.NO_CAPABILITY, service.check("qwen3.5:2b"));
    }

    @Test
    void testNullModelInfoWithVisionFlagIsCapabilityWithoutTensors() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponse(List.of("vision"), null));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertEquals(VisionSupport.CAPABILITY_WITHOUT_TENSORS, service.check("x"));
    }

    @Test
    void testShowModelFailureIsNoCapability() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenThrow(new IllegalStateException("ollama down"));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertEquals(VisionSupport.NO_CAPABILITY, service.check("qwen3.5:4b"));
    }

    @Test
    void testUnavailableApiAndBlankModel() {
        VisionCapabilityService service = new VisionCapabilityService(providerOf(null), mock(OllamaChatModel.class));
        assertFalse(service.supportsVision("qwen3.5:4b"));
        OllamaApi api = mock(OllamaApi.class);
        VisionCapabilityService withApi = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertEquals(VisionSupport.NO_CAPABILITY, withApi.check(null));
        assertEquals(VisionSupport.NO_CAPABILITY, withApi.check(""));
    }

    @Test
    void testCachesResult() {
        OllamaApi api = mock(OllamaApi.class);
        when(api.showModel(any())).thenReturn(showResponse(List.of("vision"), VISION_TENSORS));
        VisionCapabilityService service = new VisionCapabilityService(providerOf(api), mock(OllamaChatModel.class));
        assertTrue(service.supportsVision("qwen3.5:4b"));
        assertTrue(service.supportsVision("qwen3.5:4b"));
        verify(api, times(1)).showModel(any());
    }

    @Test
    void testWarningMentionsVisionTensorsForMlx() {
        VisionCapabilityService service = new VisionCapabilityService(providerOf(null), mock(OllamaChatModel.class));
        String warning = service.warningFor(VisionSupport.CAPABILITY_WITHOUT_TENSORS, "qwen3.5:4b-mlx");
        assertTrue(warning.contains("vision tensors"));
        assertTrue(warning.contains("mlx"));
        assertNull(service.warningFor(VisionSupport.SUPPORTED, "x"));
    }
}
