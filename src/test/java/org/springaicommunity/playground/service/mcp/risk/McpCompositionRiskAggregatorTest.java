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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpCompositionRiskAggregatorTest {

    private final McpCompositionRiskAggregator aggregator = new McpCompositionRiskAggregator();

    @Test
    void emptyExposureIsL0() {
        assertEquals(RiskLevel.L0, aggregator.builtinServerLevel(List.of()));
    }

    @Test
    void maxOverMembersIsCorrect() {
        assertEquals(RiskLevel.L4,
                aggregator.builtinServerLevel(List.of(RiskLevel.L1, RiskLevel.L4, RiskLevel.L2)));
    }

    @Test
    void l5ShortCircuitsToL5() {
        assertEquals(RiskLevel.L5,
                aggregator.builtinServerLevel(List.of(RiskLevel.L1, RiskLevel.L5, RiskLevel.L0)));
    }

    @Test
    void nullEntriesIgnored() {
        assertEquals(RiskLevel.L2,
                aggregator.builtinServerLevel(Arrays.asList(null, RiskLevel.L2, null)));
    }
}
