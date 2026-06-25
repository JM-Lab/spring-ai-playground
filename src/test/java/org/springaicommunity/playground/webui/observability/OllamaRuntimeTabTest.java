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
package org.springaicommunity.playground.webui.observability;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.InstalledModel;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaRuntimeTabTest {

    private static InstalledModel installed(String name, String paramSize, long contextLength) {
        return new InstalledModel(name, 0L, null, paramSize, null, contextLength, List.of());
    }

    private static InstalledModel withFamily(String name, String family) {
        return new InstalledModel(name, 0L, family, null, null, 0L, List.of());
    }

    @Test
    void formatContextSwitchesToKAtOneKiloToken() {
        assertThat(OllamaRuntimeTab.formatContext(512)).isEqualTo("512");
        assertThat(OllamaRuntimeTab.formatContext(1023)).isEqualTo("1023");
        assertThat(OllamaRuntimeTab.formatContext(1024)).isEqualTo("1K");
        assertThat(OllamaRuntimeTab.formatContext(2048)).isEqualTo("2K");
        assertThat(OllamaRuntimeTab.formatContext(262144)).isEqualTo("256K");
    }

    @Test
    void unloadCountdownHandlesNullExpiredAndFuture() {
        Instant now = Instant.parse("2026-06-23T12:00:00Z");
        assertThat(OllamaRuntimeTab.unloadCountdown(null, now)).isEqualTo("—");
        assertThat(OllamaRuntimeTab.unloadCountdown(now, now)).isEqualTo("unloading");
        assertThat(OllamaRuntimeTab.unloadCountdown(now.minusSeconds(5), now)).isEqualTo("unloading");
        assertThat(OllamaRuntimeTab.unloadCountdown(now.plusSeconds(90), now)).startsWith("unloads in");
    }

    @Test
    void modelSpecsJoinsParamSizeAndContextWhenPresent() {
        assertThat(OllamaRuntimeTab.modelSpecs(null)).isEmpty();
        assertThat(OllamaRuntimeTab.modelSpecs(installed("a", "4.7B", 0))).isEqualTo("4.7B");
        assertThat(OllamaRuntimeTab.modelSpecs(installed("a", null, 262144))).isEqualTo("256K ctx");
        assertThat(OllamaRuntimeTab.modelSpecs(installed("a", "4.7B", 262144))).isEqualTo("4.7B · 256K ctx");
        assertThat(OllamaRuntimeTab.modelSpecs(installed("a", null, 0))).isEmpty();
    }

    @Test
    void countByGroupsByKeyFunction() {
        List<InstalledModel> models = List.of(
                withFamily("a", "qwen"), withFamily("b", "qwen"), withFamily("c", "llama"));
        assertThat(OllamaRuntimeTab.countBy(models, InstalledModel::family))
                .containsEntry("qwen", 2L).containsEntry("llama", 1L);
    }

    @Test
    void bytesToMbConvertsEachElement() {
        assertThat(OllamaRuntimeTab.bytesToMb(new double[] {1024.0 * 1024.0, 2.0 * 1024 * 1024, 0.0}))
                .containsExactly(1.0, 2.0, 0.0);
        assertThat(OllamaRuntimeTab.bytesToMb(new double[0])).isEmpty();
    }

    @Test
    void pressureValueWarnsAtOrAbove85PctOfUnifiedMemory() {
        assertThat(OllamaRuntimeTab.pressureValue(true, 90, 100, 85.0)).isEqualTo("90% ⚠");
        assertThat(OllamaRuntimeTab.pressureValue(true, 85, 100, 85.0)).isEqualTo("85% ⚠");
        assertThat(OllamaRuntimeTab.pressureValue(true, 84, 100, 85.0)).isEqualTo("84%");
        assertThat(OllamaRuntimeTab.pressureValue(true, 50, 100, 85.0)).isEqualTo("50%");
    }

    @Test
    void pressureValueShowsNoPercentForDiscreteGpuOrUnknownHostMemory() {
        assertThat(OllamaRuntimeTab.pressureValue(false, 90, 100, 85.0)).doesNotContain("%");
        assertThat(OllamaRuntimeTab.pressureValue(true, 90, 0, 85.0)).isEqualTo("—");
    }
}
