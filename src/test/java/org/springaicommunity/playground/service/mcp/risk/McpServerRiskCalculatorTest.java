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
import org.springaicommunity.playground.service.mcp.catalog.McpTrustSignal;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.mcp.risk.McpServerRiskCalculator.DocCompleteness;
import org.springaicommunity.playground.service.mcp.risk.McpServerRiskCalculator.Inputs;
import org.springaicommunity.playground.service.mcp.risk.McpServerRiskCalculator.Result;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class McpServerRiskCalculatorTest {

    private final McpServerRiskCalculator calc = new McpServerRiskCalculator(McpRiskSignalSink.NOOP);

    private Inputs basicCatalog(McpAuthMode auth, String url, McpTransportType transport,
            Set<McpTrustSignal> trust) {
        return new Inputs("test-server", "Test Server", transport, url, auth, trust,
                false, false, DocCompleteness.fullyComplete(), McpRiskFactors.SAFE_READ_ONLY, List.of());
    }

    @Test
    void catalogVendorOfficialOauthStdResolvesToL1() {
        Result r = calc.compute(basicCatalog(McpAuthMode.OAUTH_STANDARD,
                "https://api.githubcopilot.com/mcp/", McpTransportType.STREAMABLE_HTTP,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL)));
        assertEquals(RiskLevel.L1, r.level());
        assertEquals(0, r.totalScore());
        assertNull(r.floorTrigger());
    }

    @Test
    void catalogVendorOfficialApiKeyResolvesToL2() {
        Result r = calc.compute(basicCatalog(McpAuthMode.API_KEY,
                "https://mcp.tavily.com/mcp", McpTransportType.STREAMABLE_HTTP,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL)));
        assertEquals(RiskLevel.L2, r.level());
        assertEquals(1, r.totalScore());
    }

    @Test
    void catalogVendorOfficialBearerResolvesToL2() {
        Result r = calc.compute(basicCatalog(McpAuthMode.BEARER,
                "https://playmcp.kakao.com/mcp", McpTransportType.STREAMABLE_HTTP,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL)));
        assertEquals(RiskLevel.L2, r.level(),
                "PlayMCP after M8 OAUTH→BEARER fallback should land at L2");
    }

    @Test
    void communityCuratedReadOnlyNoAuthResolvesToL2() {
        Result r = calc.compute(basicCatalog(McpAuthMode.NONE,
                "https://korean-law-mcp.fly.dev/mcp", McpTransportType.STREAMABLE_HTTP,
                Set.of(McpTrustSignal.COMMUNITY_CURATED)));
        assertEquals(RiskLevel.L2, r.level(),
                "Korean-Law-MCP (community, no-auth, read-only) should be L2 — community +1");
        assertNull(r.floorTrigger());
    }

    @Test
    void vendorOfficialNoAuthReadOnlyResolvesToL1() {
        Result r = calc.compute(basicCatalog(McpAuthMode.NONE,
                "https://learn.microsoft.com/api/mcp", McpTransportType.STREAMABLE_HTTP,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL)));
        assertEquals(RiskLevel.L1, r.level(),
                "vendor-official read-only no-auth (Microsoft Learn / DeepWiki / Context7) stays L1");
    }

    @Test
    void publicNoAuthWithWriteCapabilityTriggersFloorL5() {
        Inputs in = new Inputs("hostile", "Hostile", McpTransportType.STREAMABLE_HTTP,
                "https://hostile.example.com/mcp", McpAuthMode.NONE,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                DocCompleteness.fullyComplete(),
                new McpRiskFactors(true, false, false, false), List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L5, r.level());
        assertEquals(McpServerRiskCalculator.FLOOR_NO_AUTH_WRITE, r.floorTrigger());
    }

    @Test
    void userTypedUnknownPublicNoAuthTriggersFloorL5() {
        Inputs in = new Inputs("unknown", "Unknown", McpTransportType.STREAMABLE_HTTP,
                "https://random-mcp.example.com/mcp", McpAuthMode.NONE,
                Set.of(), true, false, DocCompleteness.fullyComplete(),
                McpRiskFactors.SAFE_READ_ONLY, List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L5, r.level());
        assertEquals(McpServerRiskCalculator.FLOOR_NO_AUTH_UNKNOWN_TRUST, r.floorTrigger());
    }

    @Test
    void privilegedOauthScopeTriggersFloorL5() {
        Inputs in = new Inputs("admin", "Admin Server", McpTransportType.STREAMABLE_HTTP,
                "https://api.example.com/mcp", McpAuthMode.OAUTH_STANDARD,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                DocCompleteness.fullyComplete(), McpRiskFactors.SAFE_READ_ONLY,
                List.of("read:user", "admin:org"));
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L5, r.level());
        assertEquals(McpServerRiskCalculator.FLOOR_PRIVILEGED_OAUTH_SCOPE, r.floorTrigger());
    }

    @Test
    void loopbackNoAuthDoesNotTriggerFloor() {
        Inputs in = new Inputs("local", "Local", McpTransportType.STREAMABLE_HTTP,
                "http://127.0.0.1:8080/mcp", McpAuthMode.NONE,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                DocCompleteness.fullyComplete(),
                new McpRiskFactors(true, false, false, false), List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L1, r.level(), "loopback with no-auth + write should still be L1 (loopback exempts auth axis)");
        assertNull(r.floorTrigger());
    }

    @Test
    void privateLanAddsTransportPoint() {
        Inputs in = new Inputs("vpn", "VPN", McpTransportType.STREAMABLE_HTTP,
                "http://10.0.0.5:8080/mcp", McpAuthMode.OAUTH_STANDARD,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                DocCompleteness.fullyComplete(), McpRiskFactors.SAFE_READ_ONLY, List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L2, r.level());
        assertEquals(1, r.totalScore());
    }

    @Test
    void docGapsAccumulateBeforeCap() {
        DocCompleteness doc = new DocCompleteness(false, false, false);
        assertEquals(3, doc.gapCount());
        Inputs in = new Inputs("doc-poor", "Doc Poor", McpTransportType.STREAMABLE_HTTP,
                "https://api.example.com/mcp", McpAuthMode.OAUTH_STANDARD,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                doc, McpRiskFactors.SAFE_READ_ONLY, List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L3, r.level(),
                "all-doc-gaps capped at +2 → total 2 → L3");
        assertEquals(2, r.totalScore());
    }

    @Test
    void stdioIsAlwaysZeroTransportAndAuth() {
        Inputs in = new Inputs("stdio-srv", "Stdio Server", McpTransportType.STDIO,
                "/usr/local/bin/some-mcp", McpAuthMode.NONE,
                Set.of(McpTrustSignal.VENDOR_OFFICIAL), false, false,
                DocCompleteness.fullyComplete(),
                new McpRiskFactors(true, true, false, true), List.of());
        Result r = calc.compute(in);
        assertEquals(RiskLevel.L1, r.level(),
                "stdio child process: transport=0, auth=0 (loopback exemption), trust=0, doc=0 → L1");
        assertNotNull(r.axisScores());
    }
}
