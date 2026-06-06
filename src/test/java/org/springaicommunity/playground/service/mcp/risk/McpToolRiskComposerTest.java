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
package org.springaicommunity.playground.service.mcp.risk;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpToolRiskComposerTest {

    private final McpToolRiskComposer composer = new McpToolRiskComposer();

    @Test
    void hitlMitigationLowersLevelByOneFlooredAtL1() {
        assertEquals(RiskLevel.L4, McpToolRiskComposer.applyHitlMitigation(RiskLevel.L5, true));
        assertEquals(RiskLevel.L2, McpToolRiskComposer.applyHitlMitigation(RiskLevel.L3, true));
        assertEquals(RiskLevel.L1, McpToolRiskComposer.applyHitlMitigation(RiskLevel.L1, true), "floored at L1");
        assertEquals(RiskLevel.L5, McpToolRiskComposer.applyHitlMitigation(RiskLevel.L5, false), "no change without HITL");
    }

    @Test
    void serverL1PlusPublishL1ResolvesToL1() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L1, 0, Map.of(), null);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L1, 0, Map.of(), null);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L1, composed.finalLevel());
        assertEquals(RiskLevel.L1, composed.serverLevel());
        assertEquals(RiskLevel.L1, composed.publishLevel());
        assertNull(composed.floorTrigger());
    }

    @Test
    void serverL2PlusPublishL2EscalatesToL3() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L2, 1, Map.of(), null);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L2, 1, Map.of(), null);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L3, composed.finalLevel(),
                "additive: 1+1=2 → L3 (matrix shows L2+L2=L3)");
    }

    @Test
    void serverFloorL5PropagatesToFinal() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L5, 99, Map.of(), McpServerRiskCalculator.FLOOR_NO_AUTH_WRITE);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L1, 0, Map.of(), null);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L5, composed.finalLevel());
        assertEquals(McpServerRiskCalculator.FLOOR_NO_AUTH_WRITE, composed.floorTrigger());
    }

    @Test
    void publishFloorL5PropagatesToFinal() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L1, 0, Map.of(), null);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L5, 99, Map.of(),
                McpToolPublishRiskCalculator.FLOOR_IRREVERSIBLE_VERB);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L5, composed.finalLevel());
        assertEquals(McpToolPublishRiskCalculator.FLOOR_IRREVERSIBLE_VERB, composed.floorTrigger());
    }

    @Test
    void publishSevereFailureL4ResolvedAsL4WithoutEscalation() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L1, 0, Map.of(), null);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L4, 0, Map.of(),
                McpToolPublishRiskCalculator.FLOOR_SEVERE_FAILURE);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L4, composed.finalLevel());
    }

    @Test
    void internalJsL0StaysL0() {
        McpToolRiskComposer.Composed composed = composer.composeInternalJs(RiskLevel.L0);
        assertEquals(RiskLevel.L0, composed.finalLevel());
        assertNull(composed.serverLevel());
        assertEquals(RiskLevel.L0, composed.publishLevel());
    }

    @Test
    void internalJsL3StaysL3() {
        McpToolRiskComposer.Composed composed = composer.composeInternalJs(RiskLevel.L3);
        assertEquals(RiskLevel.L3, composed.finalLevel());
        assertNotNull(composed.publishLevel());
    }

    @Test
    void compositionMatrixL3xL3IsL5() {
        McpServerRiskCalculator.Result server = new McpServerRiskCalculator.Result(RiskLevel.L3, 2, Map.of(), null);
        McpToolPublishRiskCalculator.Result publish = new McpToolPublishRiskCalculator.Result(RiskLevel.L3, 2, Map.of(), null);
        McpToolRiskComposer.Composed composed = composer.composeWrappedExternal(server, publish);
        assertEquals(RiskLevel.L5, composed.finalLevel(),
                "matrix L3+L3 = score 4 → L5");
    }
}
