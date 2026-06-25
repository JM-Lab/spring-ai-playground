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
package org.springaicommunity.playground.observability.ollama;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.InstalledModel;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.OllamaStatus;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.RunningModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaMetricsCollectorTest {

    private static final long GB = 1024L * 1024L * 1024L;

    @Test
    void recordsSampleWhenOllamaActive() {
        OllamaMonitorService monitor = mock(OllamaMonitorService.class);
        OllamaMetricsRingBuffer buffer = new OllamaMetricsRingBuffer(100);
        when(monitor.snapshot()).thenReturn(new OllamaStatus(true, true, "0.5.0",
                List.of(installed("qwen3.5:2b"), installed("gemma4:e4b-mlx")),
                List.of(new RunningModel("qwen3.5:2b", 2 * GB, 2 * GB, Instant.now()))));

        new OllamaMetricsCollector(monitor, buffer).sample();

        assertThat(buffer.size()).isEqualTo(1);
        OllamaMetricsRingBuffer.Sample sample = buffer.snapshotSince(0L).get(0);
        assertThat(sample.reachable()).isTrue();
        assertThat(sample.runningModels()).isEqualTo(1);
        assertThat(sample.totalVramBytes()).isEqualTo(2 * GB);
        assertThat(sample.installedModels()).isEqualTo(2);
    }

    private static InstalledModel installed(String name) {
        return new InstalledModel(name, GB, "qwen", "2B", "Q4_K_M", 8192, List.of("completion"));
    }

    @Test
    void skipsSampleWhenOllamaInactive() {
        OllamaMonitorService monitor = mock(OllamaMonitorService.class);
        OllamaMetricsRingBuffer buffer = new OllamaMetricsRingBuffer(100);
        when(monitor.snapshot()).thenReturn(new OllamaStatus(false, false, null, List.of(), List.of()));

        new OllamaMetricsCollector(monitor, buffer).sample();

        assertThat(buffer.size()).isZero();
    }
}
