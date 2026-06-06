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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCompositionTest {

    private McpComposition base() {
        return new McpComposition("c1", "dev", "desc",
                List.of(new McpComposition.Member("github", "list", null, "h")), false, RiskLevel.L3,
                100L, 200L, null);
    }

    @Test
    void requiresNonBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new McpComposition(null, "x", "", List.of(), false, RiskLevel.L3, 0L, 0L, null));
        assertThrows(IllegalArgumentException.class,
                () -> new McpComposition("", "x", "", List.of(), false, RiskLevel.L3, 0L, 0L, null));
    }

    @Test
    void defaultsApplyForNullValues() {
        McpComposition c = new McpComposition("id", null, null, null, false, null, 0L, 0L, null);
        assertEquals("id", c.name());
        assertEquals("", c.description());
        assertNotNull(c.members());
        assertEquals(RiskLevel.L3, c.maxRiskLevel());
    }

    @Test
    void withEnabledUpdatesFlagAndTimestamp() {
        McpComposition c = base().withEnabled(true, 555L);
        assertTrue(c.enabled());
        assertEquals(555L, c.updatedAtEpochMs());
        assertEquals(555L, c.lastEnabledAtEpochMs());
    }

    @Test
    void withEnabledFalsePreservesLastEnabledAt() {
        McpComposition enabled = base().withEnabled(true, 111L);
        McpComposition disabled = enabled.withEnabled(false, 222L);
        assertFalse(disabled.enabled());
        assertEquals(111L, disabled.lastEnabledAtEpochMs(), "lastEnabledAtEpochMs preserved on disable");
        assertEquals(222L, disabled.updatedAtEpochMs());
    }

    @Test
    void withMembersReplacesMembersList() {
        List<McpComposition.Member> newMembers = List.of(
                new McpComposition.Member("notion", "read", null, "h"));
        McpComposition c = base().withMembers(newMembers, 999L);
        assertEquals(1, c.members().size());
        assertEquals("notion", c.members().getFirst().serverId());
        assertEquals(999L, c.updatedAtEpochMs());
    }

    @Test
    void withMetadataOnlyOverridesNonNullArgs() {
        McpComposition c = base().withMetadata("renamed", null, RiskLevel.L4, 999L);
        assertEquals("renamed", c.name());
        assertEquals("desc", c.description(), "null description should not override");
        assertEquals(RiskLevel.L4, c.maxRiskLevel());
    }
}
