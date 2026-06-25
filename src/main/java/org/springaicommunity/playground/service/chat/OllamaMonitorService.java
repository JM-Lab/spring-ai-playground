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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class OllamaMonitorService {

    private static final Logger logger = LoggerFactory.getLogger(OllamaMonitorService.class);
    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private volatile OllamaStatus cached = OllamaStatus.inactive();

    public OllamaMonitorService(ObjectProvider<ChatModel> chatModelProvider,
            Environment environment, ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    public boolean ollamaActive() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        return chatModel != null && ChatProvider.from(chatModel) == ChatProvider.OLLAMA;
    }

    public Platform platform() {
        String override = environment.getProperty(
                "spring.ai.playground.observability.ollama.platform", "auto");
        return switch (override.toLowerCase(Locale.ROOT)) {
            case "apple-silicon" -> new Platform("Apple Silicon", true, "Metal · unified memory");
            case "mac-intel" -> new Platform("macOS (Intel)", false, "discrete/integrated GPU or CPU");
            case "linux" -> new Platform("Linux", false, "discrete GPU (CUDA/ROCm) or CPU");
            case "windows" -> new Platform("Windows", false, "discrete GPU (CUDA) or CPU");
            default -> detectPlatform();
        };
    }

    private static Platform detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return arch.equals("aarch64") || arch.equals("arm64")
                    ? new Platform("Apple Silicon", true, "Metal · unified memory")
                    : new Platform("macOS (Intel)", false, "discrete/integrated GPU or CPU");
        }
        if (os.contains("win")) {
            return new Platform("Windows", false, "discrete GPU (CUDA) or CPU");
        }
        if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            return new Platform("Linux", false, "discrete GPU (CUDA/ROCm) or CPU");
        }
        return new Platform(System.getProperty("os.name", "Unknown"), false, "GPU or CPU");
    }

    public OllamaStatus snapshot() {
        OllamaStatus status = fetchStatus();
        this.cached = status;
        return status;
    }

    public OllamaStatus cachedSnapshot() {
        return cached;
    }

    private OllamaStatus fetchStatus() {
        if (!ollamaActive()) {
            return OllamaStatus.inactive();
        }
        String baseUrl = environment.getProperty("spring.ai.ollama.base-url", DEFAULT_BASE_URL);
        String version = fetchVersion(baseUrl);
        if (version == null) {
            return new OllamaStatus(true, false, null, List.of(), List.of());
        }
        return new OllamaStatus(true, true, version, fetchInstalledModels(baseUrl), fetchRunningModels(baseUrl));
    }

    private List<InstalledModel> fetchInstalledModels(String baseUrl) {
        String body = httpGet(baseUrl + "/api/tags");
        return body == null ? List.of() : parseInstalledModels(body);
    }

    List<InstalledModel> parseInstalledModels(String body) {
        List<InstalledModel> installed = new ArrayList<>();
        try {
            for (JsonNode model : objectMapper.readTree(body).path("models")) {
                JsonNode details = model.path("details");
                List<String> capabilities = new ArrayList<>();
                for (JsonNode cap : model.path("capabilities")) {
                    capabilities.add(cap.asText());
                }
                installed.add(new InstalledModel(
                        model.path("name").asText(),
                        model.path("size").asLong(),
                        blankToNull(details.path("family").asText()),
                        blankToNull(details.path("parameter_size").asText()),
                        blankToNull(details.path("quantization_level").asText()),
                        details.path("context_length").asLong(),
                        List.copyOf(capabilities)));
            }
        } catch (RuntimeException e) {
            logger.debug("Ollama /api/tags parse failed", e);
        }
        return installed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private List<RunningModel> fetchRunningModels(String baseUrl) {
        String body = httpGet(baseUrl + "/api/ps");
        return body == null ? List.of() : parseRunningModels(body);
    }

    List<RunningModel> parseRunningModels(String body) {
        List<RunningModel> running = new ArrayList<>();
        try {
            for (JsonNode model : objectMapper.readTree(body).path("models")) {
                running.add(new RunningModel(
                        model.path("name").asText(),
                        model.path("size").asLong(),
                        model.path("size_vram").asLong(),
                        parseInstant(model.path("expires_at").asText())));
            }
        } catch (RuntimeException e) {
            logger.debug("Ollama /api/ps parse failed", e);
        }
        return running;
    }

    private String fetchVersion(String baseUrl) {
        String body = httpGet(baseUrl + "/api/version");
        if (body == null) {
            return null;
        }
        try {
            return objectMapper.readTree(body).path("version").asText();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String httpGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() < 400 ? response.body() : null;
        } catch (Exception e) {
            logger.debug("Ollama request failed: {}", url, e);
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record OllamaStatus(boolean active, boolean reachable, String version,
            List<InstalledModel> installedModels, List<RunningModel> runningModels) {

        static OllamaStatus inactive() {
            return new OllamaStatus(false, false, null, List.of(), List.of());
        }

        public long totalVramBytes() {
            return runningModels.stream().mapToLong(RunningModel::sizeVram).sum();
        }

        public long totalSizeBytes() {
            return runningModels.stream().mapToLong(RunningModel::sizeBytes).sum();
        }

        public long totalInstalledDiskBytes() {
            return installedModels.stream().mapToLong(InstalledModel::sizeBytes).sum();
        }
    }

    public record RunningModel(String name, long sizeBytes, long sizeVram, Instant expiresAt) {}

    public record InstalledModel(String name, long sizeBytes, String family,
            String parameterSize, String quantization, long contextLength, List<String> capabilities) {}

    public record Platform(String label, boolean unifiedMemory, String accelerator) {}
}
