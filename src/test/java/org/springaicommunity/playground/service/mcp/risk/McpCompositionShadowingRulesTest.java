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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCompositionShadowingRulesTest {

    private final McpCompositionShadowingRules rules = new McpCompositionShadowingRules();

    private McpComposition composition(String id, List<McpComposition.Member> members, boolean enabled) {
        return new McpComposition(id, id, "", members, enabled, RiskLevel.L3, 0L, 0L, null);
    }

    @Test
    void aliasCollisionAcrossEnabledCompositionsDetected() {
        McpComposition.Member a = new McpComposition.Member("github", "search", "github__search", "h1");
        McpComposition.Member b = new McpComposition.Member("notion", "search", "github__search", "h2");
        McpComposition c1 = composition("c1", List.of(a), true);
        McpComposition c2 = composition("c2", List.of(b), true);
        var violations = rules.checkAliasCollisions(List.of(c1, c2));
        assertEquals(1, violations.size());
        assertEquals(McpCompositionShadowingRules.RuleId.R1_ALIAS_COLLISION, violations.getFirst().rule());
    }

    @Test
    void aliasCollisionIgnoresDisabledCompositions() {
        McpComposition.Member a = new McpComposition.Member("github", "search", "github__search", "h1");
        McpComposition.Member b = new McpComposition.Member("notion", "search", "github__search", "h2");
        McpComposition c1 = composition("c1", List.of(a), true);
        McpComposition c2 = composition("c2", List.of(b), false);
        var violations = rules.checkAliasCollisions(List.of(c1, c2));
        assertTrue(violations.isEmpty());
    }

    @Test
    void crossServerImperativeReferenceDetected() {
        McpComposition.Member a = new McpComposition.Member("github", "search", "github__search", "h1");
        McpComposition.Member b = new McpComposition.Member("notion", "create_page", "notion__create_page", "h2");
        McpComposition comp = composition("c1", List.of(a, b), false);
        Map<String, McpCompositionShadowingRules.ToolMetadata> meta = Map.of(
                "github::search", new McpCompositionShadowingRules.ToolMetadata(
                        "Search repos and then call create_page with results"),
                "notion::create_page", new McpCompositionShadowingRules.ToolMetadata(
                        "Create a Notion page"));
        var violations = rules.checkCrossServerReferences(comp, meta);
        assertEquals(1, violations.size());
        assertEquals(McpCompositionShadowingRules.RuleId.R2_CROSS_SERVER_REFERENCE, violations.getFirst().rule());
    }

    @Test
    void selfScopeViolationWhenMentioningOtherServerId() {
        McpComposition.Member a = new McpComposition.Member("github", "search", "github__search", "h1");
        McpComposition.Member b = new McpComposition.Member("notion", "create_page", "notion__create_page", "h2");
        McpComposition comp = composition("c1", List.of(a, b), false);
        Map<String, McpCompositionShadowingRules.ToolMetadata> meta = Map.of(
                "github::search", new McpCompositionShadowingRules.ToolMetadata(
                        "Search repos for notion-related data"),
                "notion::create_page", new McpCompositionShadowingRules.ToolMetadata(
                        "Create a page"));
        var violations = rules.checkSelfScope(comp, meta);
        assertEquals(1, violations.size());
        assertEquals(McpCompositionShadowingRules.RuleId.R3_SELF_SCOPE_VIOLATION, violations.getFirst().rule());
    }
}
