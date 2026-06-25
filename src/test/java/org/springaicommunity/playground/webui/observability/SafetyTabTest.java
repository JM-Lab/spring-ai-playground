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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SafetyTabTest {

    @Test
    void approvalRateIsDashWhenNoDecisionsElseRoundedPercent() {
        assertThat(SafetyTab.approvalRate(0, 0)).isEqualTo("—");
        assertThat(SafetyTab.approvalRate(3, 4)).isEqualTo("75%");
        assertThat(SafetyTab.approvalRate(0, 2)).isEqualTo("0%");
        assertThat(SafetyTab.approvalRate(2, 2)).isEqualTo("100%");
    }

    @Test
    void riskLevelByLevelOrdersL0ToL5AndDropsZeroCounts() {
        Map<String, Long> raw = new LinkedHashMap<>();
        raw.put("L3", 1L);
        raw.put("L0", 2L);
        raw.put("L5", 0L);
        raw.put("L2", 5L);
        assertThat(SafetyTab.riskLevelByLevel(raw)).containsExactly(
                Map.entry("L0", 2L), Map.entry("L2", 5L), Map.entry("L3", 1L));
    }

    @Test
    void riskLevelByLevelOnEmptyMapIsEmpty() {
        assertThat(SafetyTab.riskLevelByLevel(Map.of())).isEmpty();
    }
}
