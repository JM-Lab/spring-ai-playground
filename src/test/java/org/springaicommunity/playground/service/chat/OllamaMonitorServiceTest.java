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
import org.springaicommunity.playground.service.chat.OllamaMonitorService.InstalledModel;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.OllamaStatus;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.Platform;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.RunningModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaMonitorServiceTest {

    @SuppressWarnings("unchecked")
    private OllamaMonitorService service(ChatModel chatModel) {
        ObjectProvider<ChatModel> chatModelProvider = mock(ObjectProvider.class);
        when(chatModelProvider.getIfAvailable()).thenReturn(chatModel);
        return new OllamaMonitorService(chatModelProvider, mock(Environment.class), JsonMapper.builder().build());
    }

    @Test
    void parsesRunningModelsWithVramAndOffsetExpiry() {
        String body = """
                {"models":[
                  {"name":"qwen3.5:2b-mlx","size":3221225472,"size_vram":3221225472,
                   "expires_at":"2026-06-18T12:00:00.5-07:00"},
                  {"name":"gemma4:e4b-mlx","size":10307921510,"size_vram":5000000000,
                   "expires_at":"2026-06-18T12:05:00Z"}
                ]}""";

        List<RunningModel> models = service(null).parseRunningModels(body);

        assertThat(models).hasSize(2);
        assertThat(models).extracting(RunningModel::name)
                .containsExactly("qwen3.5:2b-mlx", "gemma4:e4b-mlx");
        assertThat(models.getFirst().sizeVram()).isEqualTo(3221225472L);
        assertThat(models.getFirst().expiresAt()).isEqualTo(Instant.parse("2026-06-18T19:00:00.5Z"));
        assertThat(models.get(1).expiresAt()).isEqualTo(Instant.parse("2026-06-18T12:05:00Z"));
    }

    @Test
    void totalVramAndSizeSumLoadedModels() {
        OllamaStatus status = new OllamaStatus(true, true, "0.5.0", List.of(), List.of(
                new RunningModel("a", 100, 2_000_000_000L, null),
                new RunningModel("b", 200, 3_000_000_000L, null)));
        assertThat(status.totalVramBytes()).isEqualTo(5_000_000_000L);
        assertThat(status.totalSizeBytes()).isEqualTo(300L);
    }

    @Test
    void parsesInstalledModelsWithDetailsAndCapabilities() {
        String body = """
                {"models":[
                  {"name":"qwen3.5:4b","size":3389983735,
                   "details":{"family":"qwen35","parameter_size":"4.7B",
                     "quantization_level":"Q4_K_M","context_length":262144},
                   "capabilities":["completion","tools","thinking"]},
                  {"name":"qwen3.5:2b-mlx","size":3221225472,
                   "details":{"quantization_level":"mxfp8"},
                   "capabilities":["completion"]}
                ]}""";

        List<InstalledModel> models = service(null).parseInstalledModels(body);

        assertThat(models).hasSize(2);
        InstalledModel first = models.getFirst();
        assertThat(first.name()).isEqualTo("qwen3.5:4b");
        assertThat(first.sizeBytes()).isEqualTo(3389983735L);
        assertThat(first.family()).isEqualTo("qwen35");
        assertThat(first.parameterSize()).isEqualTo("4.7B");
        assertThat(first.quantization()).isEqualTo("Q4_K_M");
        assertThat(first.contextLength()).isEqualTo(262144L);
        assertThat(first.capabilities()).containsExactly("completion", "tools", "thinking");
        assertThat(models.get(1).family()).isNull();
        assertThat(models.get(1).parameterSize()).isNull();
        assertThat(models.get(1).quantization()).isEqualTo("mxfp8");

        OllamaStatus status = new OllamaStatus(true, true, "0.5.0", models, List.of());
        assertThat(status.totalInstalledDiskBytes()).isEqualTo(3389983735L + 3221225472L);
    }

    @Test
    void malformedTagsBodyYieldsEmptyListNotThrow() {
        assertThat(service(null).parseInstalledModels("not json")).isEmpty();
        assertThat(service(null).parseInstalledModels("{}")).isEmpty();
        assertThat(service(null).parseInstalledModels("{\"models\":[]}")).isEmpty();
    }

    @Test
    void malformedPsBodyYieldsEmptyListNotThrow() {
        assertThat(service(null).parseRunningModels("not json")).isEmpty();
        assertThat(service(null).parseRunningModels("{}")).isEmpty();
        assertThat(service(null).parseRunningModels("{\"models\":[]}")).isEmpty();
    }

    @Test
    void snapshotIsInactiveWhenOllamaIsNotTheActiveProvider() {
        OllamaStatus status = service(null).snapshot();
        assertThat(status.active()).isFalse();
        assertThat(status.reachable()).isFalse();
        assertThat(status.runningModels()).isEmpty();
    }

    @Test
    void nonOllamaChatModelIsInactive() {
        assertThat(service(mock(ChatModel.class)).ollamaActive()).isFalse();
    }

    @Test
    void platformOverrideForcesDiscreteLinux() {
        Platform p = serviceWithPlatform("linux").platform();
        assertThat(p.label()).isEqualTo("Linux");
        assertThat(p.unifiedMemory()).isFalse();
        assertThat(p.accelerator()).contains("CUDA");
    }

    @Test
    void platformOverrideForcesUnifiedAppleSilicon() {
        Platform p = serviceWithPlatform("apple-silicon").platform();
        assertThat(p.label()).isEqualTo("Apple Silicon");
        assertThat(p.unifiedMemory()).isTrue();
    }

    @Test
    void platformAutoDetectReturnsSaneValues() {
        Platform p = serviceWithPlatform("auto").platform();
        assertThat(p.label()).isNotBlank();
        assertThat(p.accelerator()).isNotBlank();
    }

    @SuppressWarnings("unchecked")
    private OllamaMonitorService serviceWithPlatform(String override) {
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.ai.playground.observability.ollama.platform", "auto"))
                .thenReturn(override);
        return new OllamaMonitorService(mock(ObjectProvider.class), environment, JsonMapper.builder().build());
    }
}
