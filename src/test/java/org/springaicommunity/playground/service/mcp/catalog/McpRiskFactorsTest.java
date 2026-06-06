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
package org.springaicommunity.playground.service.mcp.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRiskFactorsTest {

    @Test
    void safeReadOnlyIsAllFalse() {
        McpRiskFactors safe = McpRiskFactors.SAFE_READ_ONLY;
        assertFalse(safe.writeCapability());
        assertFalse(safe.execCapability());
        assertFalse(safe.sendsUserData());
        assertFalse(safe.irreversibleActions());
        assertFalse(safe.anyCapability());
    }

    @Test
    void anyCapabilityTrueWhenAnyFlagSet() {
        assertTrue(new McpRiskFactors(true, false, false, false).anyCapability());
        assertTrue(new McpRiskFactors(false, true, false, false).anyCapability());
        assertTrue(new McpRiskFactors(false, false, true, false).anyCapability());
        assertTrue(new McpRiskFactors(false, false, false, true).anyCapability());
    }

    @Test
    void orWithUnionsAllFlags() {
        McpRiskFactors a = new McpRiskFactors(true, false, false, false);
        McpRiskFactors b = new McpRiskFactors(false, true, false, true);
        McpRiskFactors merged = a.orWith(b);
        assertTrue(merged.writeCapability());
        assertTrue(merged.execCapability());
        assertFalse(merged.sendsUserData());
        assertTrue(merged.irreversibleActions());
    }

    @Test
    void orWithNullReturnsSelf() {
        McpRiskFactors a = new McpRiskFactors(true, false, true, false);
        assertSame(a, a.orWith(null));
    }
}
