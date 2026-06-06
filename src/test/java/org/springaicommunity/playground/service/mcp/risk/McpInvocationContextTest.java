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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpInvocationContextTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forWrappedExternalFillsAllDimensions() {
        McpInvocationContext context = McpInvocationContext.forWrappedExternal("cid1",
                "comp1", "Composition One", "github__list_repos",
                "GitHub", "list_repos", "streamable_http",
                RiskLevel.L3, RiskLevel.L1, RiskLevel.L2, null);

        Map<String, String> mdc = context.toMdc();
        assertEquals("cid1", mdc.get(McpRiskMdcKeys.CID));
        assertEquals(McpRiskMdcKeys.VIA_MCP_SERVER, mdc.get(McpRiskMdcKeys.VIA));
        assertEquals(McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL, mdc.get(McpRiskMdcKeys.ORIGIN));
        assertEquals("comp1", mdc.get(McpRiskMdcKeys.COMPOSITION_ID));
        assertEquals("Composition One", mdc.get(McpRiskMdcKeys.COMPOSITION_NAME));
        assertEquals("github__list_repos", mdc.get(McpRiskMdcKeys.EXPOSED_ALIAS));
        assertEquals("GitHub", mdc.get(McpRiskMdcKeys.UPSTREAM_SERVER));
        assertEquals("list_repos", mdc.get(McpRiskMdcKeys.UPSTREAM_TOOL));
        assertEquals("streamable_http", mdc.get(McpRiskMdcKeys.UPSTREAM_TRANSPORT));
        assertEquals("L3", mdc.get(McpRiskMdcKeys.RISK_FINAL));
        assertEquals("L1", mdc.get(McpRiskMdcKeys.RISK_SERVER));
        assertEquals("L2", mdc.get(McpRiskMdcKeys.RISK_PUBLISH));
        assertEquals("", mdc.get(McpRiskMdcKeys.FLOOR_TRIGGER));
    }

    @Test
    void forInternalJsLeavesUpstreamFieldsEmpty() {
        McpInvocationContext context = McpInvocationContext.forInternalJs("cid2", McpRiskMdcKeys.VIA_CHAT,
                RiskLevel.L0);

        Map<String, String> mdc = context.toMdc();
        assertEquals(McpRiskMdcKeys.ORIGIN_INTERNAL_JS, mdc.get(McpRiskMdcKeys.ORIGIN));
        assertEquals(McpRiskMdcKeys.VIA_CHAT, mdc.get(McpRiskMdcKeys.VIA));
        assertEquals("", mdc.get(McpRiskMdcKeys.COMPOSITION_ID));
        assertEquals("", mdc.get(McpRiskMdcKeys.UPSTREAM_SERVER));
        assertEquals("L0", mdc.get(McpRiskMdcKeys.RISK_FINAL));
        assertEquals("L0", mdc.get(McpRiskMdcKeys.RISK_PUBLISH));
        assertEquals("", mdc.get(McpRiskMdcKeys.RISK_SERVER));
    }

    @Test
    void pushMdcSnapshotsAndPopRestoresPreviousValues() {
        MDC.put(McpRiskMdcKeys.CID, "previous-cid");
        MDC.put(McpRiskMdcKeys.VIA, "previous-via");

        McpInvocationContext context = McpInvocationContext.forWrappedExternal("inner-cid",
                "comp", "Comp", "alias", "Server", "tool", "stdio",
                RiskLevel.L1, RiskLevel.L1, RiskLevel.L1, null);

        Map<String, String> snapshot = context.pushMdc();
        assertEquals("inner-cid", MDC.get(McpRiskMdcKeys.CID));
        assertEquals(McpRiskMdcKeys.VIA_MCP_SERVER, MDC.get(McpRiskMdcKeys.VIA));
        assertEquals("comp", MDC.get(McpRiskMdcKeys.COMPOSITION_ID));

        McpInvocationContext.popMdc(snapshot);
        assertEquals("previous-cid", MDC.get(McpRiskMdcKeys.CID));
        assertEquals("previous-via", MDC.get(McpRiskMdcKeys.VIA));
        assertNull(MDC.get(McpRiskMdcKeys.COMPOSITION_ID));
    }

    @Test
    void floorTriggerPropagates() {
        McpInvocationContext context = McpInvocationContext.forWrappedExternal("c", null, null, "a",
                "s", "t", "stdio", RiskLevel.L5, RiskLevel.L5, RiskLevel.L1, "non_loopback_no_auth_write_capability");

        assertEquals("non_loopback_no_auth_write_capability", context.toMdc().get(McpRiskMdcKeys.FLOOR_TRIGGER));
    }
}
