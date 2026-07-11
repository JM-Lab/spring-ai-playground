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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VisionCapabilityService {

    public enum VisionSupport { SUPPORTED, NO_CAPABILITY, CAPABILITY_WITHOUT_TENSORS }

    private static final Logger logger = LoggerFactory.getLogger(VisionCapabilityService.class);
    private static final String VISION_CAPABILITY = "vision";

    private final OllamaApi ollamaApi;
    private final ChatProvider provider;
    private final Map<String, VisionSupport> cache = new ConcurrentHashMap<>();

    public VisionCapabilityService(ObjectProvider<OllamaApi> ollamaApiProvider, ChatModel chatModel) {
        this.ollamaApi = ollamaApiProvider.getIfAvailable();
        this.provider = ChatProvider.from(chatModel);
    }

    public boolean supportsVision(String model) {
        return check(model) == VisionSupport.SUPPORTED;
    }

    public VisionSupport check(String model) {
        return switch (this.provider) {
            case OPENAI, GENERIC -> VisionSupport.SUPPORTED;
            case OLLAMA -> ollamaCheck(model);
        };
    }

    public String warningFor(VisionSupport support, String model) {
        return switch (support) {
            case SUPPORTED -> null;
            case CAPABILITY_WITHOUT_TENSORS -> "\"" + model + "\" reports vision support but ships no vision "
                    + "tensors (a known issue with mlx-converted models on Apple Silicon). Pick a non-mlx GGUF "
                    + "vision model to analyze images.";
            case NO_CAPABILITY -> "\"" + model + "\" can't process images. Select a vision-capable model.";
        };
    }

    private VisionSupport ollamaCheck(String model) {
        if (Objects.isNull(this.ollamaApi) || Objects.isNull(model) || model.isBlank())
            return VisionSupport.NO_CAPABILITY;
        String key = OllamaModelDownloadService.withDefaultTag(model);
        VisionSupport cached = this.cache.get(key);
        if (Objects.nonNull(cached)) return cached;
        try {
            OllamaApi.ShowModelResponse response = this.ollamaApi.showModel(new OllamaApi.ShowModelRequest(model));
            VisionSupport result = classify(response);
            this.cache.put(key, result);
            return result;
        } catch (Exception e) {
            logger.warn("vision-capability.query-failed model={} error={}", model, e.getMessage());
            return VisionSupport.NO_CAPABILITY;
        }
    }

    private static VisionSupport classify(OllamaApi.ShowModelResponse response) {
        if (hasVisionTensors(response.modelInfo()) || hasProjector(response.projectorInfo()))
            return VisionSupport.SUPPORTED;
        List<String> capabilities = response.capabilities();
        boolean claimsVision = Objects.nonNull(capabilities) && capabilities.contains(VISION_CAPABILITY);
        return claimsVision ? VisionSupport.CAPABILITY_WITHOUT_TENSORS : VisionSupport.NO_CAPABILITY;
    }

    private static boolean hasVisionTensors(Map<String, Object> modelInfo) {
        if (modelInfo == null) return false;
        return modelInfo.keySet().stream().anyMatch(key -> key.contains(".vision.")
                || key.contains(".clip.") || key.contains("mmproj") || key.contains(".projector."));
    }

    private static boolean hasProjector(Map<String, Object> projectorInfo) {
        return projectorInfo != null && !projectorInfo.isEmpty();
    }
}
