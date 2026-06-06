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
package org.springaicommunity.playground.webui.mcp;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import static org.assertj.core.api.Assertions.assertThat;

class McpRiskChipTest {

    @Test
    void rendersNumberLabelAndColor() {
        McpRiskChip chip = new McpRiskChip(RiskLevel.L3);
        assertThat(chip.getText()).isEqualTo("L3 — Moderate");
        assertThat(chip.getStyle().get("background-color")).isEqualTo("#fdd835");
    }

    @Test
    void appendsShortenedFloorTrigger() {
        McpRiskChip chip = new McpRiskChip(RiskLevel.L5, "non_loopback_no_auth_write_capability");
        assertThat(chip.getText()).isEqualTo("L5 — Critical (floor: no-auth-write)");
    }

    @Test
    void prefixIsPrepended() {
        McpRiskChip chip = new McpRiskChip(RiskLevel.L1, null, "Server");
        assertThat(chip.getText()).isEqualTo("Server: L1 — Safe");
    }

    @Test
    void nullLevelDefaultsToL1() {
        McpRiskChip chip = new McpRiskChip(null);
        assertThat(chip.getText()).isEqualTo("L1 — Safe");
    }

    @Test
    void labelForExposesPlainLabels() {
        assertThat(McpRiskChip.labelFor(RiskLevel.L0)).isEqualTo("Verified");
        assertThat(McpRiskChip.labelFor(RiskLevel.L5)).isEqualTo("Critical");
    }
}
