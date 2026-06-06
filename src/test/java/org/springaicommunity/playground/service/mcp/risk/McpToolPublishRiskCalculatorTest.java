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
import org.springaicommunity.playground.service.mcp.catalog.McpRiskFactors;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.mcp.risk.McpToolPublishRiskCalculator.CuratorChecklist;
import org.springaicommunity.playground.service.mcp.risk.McpToolPublishRiskCalculator.Inputs;
import org.springaicommunity.playground.service.mcp.risk.McpToolPublishRiskCalculator.Result;
import org.springaicommunity.playground.service.mcp.risk.McpToolPublishRiskCalculator.SideEffectScope;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpToolPublishRiskCalculatorTest {

    private final McpToolPublishRiskCalculator calc = new McpToolPublishRiskCalculator(McpRiskSignalSink.NOOP);

    private Inputs wellDocumented(String name, McpToolDescriptor.Annotations annotations, SideEffectScope scope) {
        return new Inputs("server", name,
                "List repositories accessible to the user. Returns owner, name, default branch and visibility.",
                true, 1.0, annotations, McpRiskFactors.SAFE_READ_ONLY, scope, false,
                CuratorChecklist.fullyComplete());
    }

    @Test
    void readOnlyAnnotatedLocalToolResolvesToL1() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, true, false, true, false);
        Result r = calc.compute(wellDocumented("list_repositories", annotations, SideEffectScope.LOCAL_ONLY));
        assertEquals(RiskLevel.L1, r.level());
        assertEquals(0, r.totalScore());
        assertNull(r.floorTrigger());
    }

    @Test
    void nonIdempotentRemoteWriteResolvesToL4() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, false, false, false, true);
        Result r = calc.compute(wellDocumented("create_issue", annotations, SideEffectScope.REMOTE_WRITE_SCOPE));
        assertEquals(RiskLevel.L4, r.level(),
                "create_issue: not read-only (+1) + open-world (+1) + remote write scope (+1) = 3 → L4");
        assertEquals(3, r.totalScore());
    }

    @Test
    void idempotentRemoteWriteResolvesToL3() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, false, false, true, true);
        Result r = calc.compute(wellDocumented("ensure_label_exists", annotations, SideEffectScope.REMOTE_WRITE_SCOPE));
        assertEquals(RiskLevel.L3, r.level(),
                "idempotent write: -1 from idempotent annotation brings total to 2 → L3");
        assertEquals(2, r.totalScore());
    }

    @Test
    void destructiveNonIdempotentTriggersFloorL5() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, false, true, false, true);
        Result r = calc.compute(wellDocumented("hard_delete_record", annotations, SideEffectScope.REMOTE_WRITE_SCOPE));
        assertEquals(RiskLevel.L5, r.level());
        assertEquals(McpToolPublishRiskCalculator.FLOOR_DESTRUCTIVE_NON_IDEMPOTENT, r.floorTrigger());
    }

    @Test
    void irreversibleVerbInNameTriggersFloorL5() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, false, true, false, true);
        Result r = calc.compute(wellDocumented("delete_repository", annotations, SideEffectScope.REMOTE_WRITE_SCOPE));
        assertEquals(RiskLevel.L5, r.level());
        assertEquals(McpToolPublishRiskCalculator.FLOOR_IRREVERSIBLE_VERB, r.floorTrigger());
    }

    @Test
    void severeFailureFloorIsL4NotL5() {
        Inputs in = new Inputs("server", "mystery_tool", "", false, 0.0,
                McpToolDescriptor.Annotations.EMPTY, McpRiskFactors.SAFE_READ_ONLY,
                SideEffectScope.UNKNOWN, false, CuratorChecklist.fullyComplete());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L4, r.level(),
                "no description + no annotations → severe failure floor at L4 (manual review path)");
        assertEquals(McpToolPublishRiskCalculator.FLOOR_SEVERE_FAILURE, r.floorTrigger());
    }

    @Test
    void docPenaltyIsCappedAtThree() {
        Inputs in = new Inputs("server", "short_tool", "short", true, 0.0,
                McpToolDescriptor.Annotations.EMPTY, McpRiskFactors.SAFE_READ_ONLY,
                SideEffectScope.LOCAL_ONLY, false,
                new CuratorChecklist(false, false, false));
        Result r = calc.compute(in);
        assertEquals(3, r.axisScores().get(McpToolPublishRiskCalculator.AXIS_DOC_PENALTY).intValue(),
                "doc gaps should be capped at 3 even though raw count is higher");
    }

    @Test
    void trustedServerWaivesDocPenaltyDroppingLevel() {
        Inputs trusted = new Inputs("server", "short_tool", "short", true, 0.0,
                McpToolDescriptor.Annotations.EMPTY, McpRiskFactors.SAFE_READ_ONLY,
                SideEffectScope.LOCAL_ONLY, false,
                new CuratorChecklist(false, false, false), true);
        Result r = calc.compute(trusted);
        assertEquals(0, r.axisScores().get(McpToolPublishRiskCalculator.AXIS_DOC_PENALTY).intValue(),
                "trusted server waives the per-tool documentation penalty entirely");
        assertEquals(RiskLevel.L2, r.level(),
                "doc penalty waived leaves only base action (+1): L2 instead of the untrusted L5");
    }

    @Test
    void idempotentDownscoreFloorsAtZero() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, true, false, true, false);
        Inputs in = new Inputs("server", "ping", "Ping the upstream and return latency", true, 1.0,
                annotations, McpRiskFactors.SAFE_READ_ONLY, SideEffectScope.LOCAL_ONLY, false,
                CuratorChecklist.fullyComplete());
        Result r = calc.compute(in);
        assertEquals(0, r.axisScores().get(McpToolPublishRiskCalculator.AXIS_BASE_ACTION).intValue());
    }

    @Test
    void boilerplateDescriptionTriggersOnePoint() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, true, false, true, false);
        Inputs in = new Inputs("server", "get_user", "Returns get user information",
                true, 1.0, annotations, McpRiskFactors.SAFE_READ_ONLY,
                SideEffectScope.LOCAL_ONLY, false, CuratorChecklist.fullyComplete());
        Result r = calc.compute(in);
        assertEquals(1, r.axisScores().get(McpToolPublishRiskCalculator.AXIS_DOC_PENALTY).intValue());
    }
}
