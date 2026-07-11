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
package org.springaicommunity.playground.service.tool.policy;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator.Inputs;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SandboxPostureCalculatorTest {

    private static final Set<String> BASELINE_DENY = Set.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder",
            "java.lang.Process", "java.lang.Class",
            "java.lang.invoke.*", "java.lang.reflect.*");
    private static final Set<String> BASELINE_ALLOW = Set.of(
            "java.lang.*", "java.util.*", "java.net.http.HttpClient");

    private final SandboxPostureCalculator calc = new SandboxPostureCalculator();

    private RiskLevel risk(String mode, Set<String> hosts, Set<String> userDeny, Set<String> userAllow) {
        return calc.compute(new Inputs(mode, hosts, false, false, false, BASELINE_DENY, userDeny, BASELINE_ALLOW, userAllow));
    }

    private RiskLevel risk(String mode, Set<String> userDeny, Set<String> userAllow) {
        return risk(mode, Set.of("api.example.com"), userDeny, userAllow);
    }

    @Test
    void blockedNetworkPlusBaselineUntouchedIsL0() {
        assertEquals(RiskLevel.L0, risk("blocked", BASELINE_DENY, BASELINE_ALLOW));
    }

    @Test
    void allowlistWithHostsPromotesToL3() {
        assertEquals(RiskLevel.L3, risk("allowlist", Set.of("*.spring.io"), BASELINE_DENY, BASELINE_ALLOW));
    }

    @Test
    void allowlistWithEmptyHostsStaysL0() {
        assertEquals(RiskLevel.L0, risk("allowlist", Set.of(), BASELINE_DENY, BASELINE_ALLOW),
                "empty allowlist means no host can be reached — effectively no network");
        assertEquals(RiskLevel.L0, risk("allowlist", null, BASELINE_DENY, BASELINE_ALLOW));
    }

    @Test
    void allowlistWithWildcardHostEscalatesToL4() {
        assertEquals(RiskLevel.L4, risk("allowlist", Set.of("*"), BASELINE_DENY, BASELINE_ALLOW),
                "wildcard host means any destination — same effective risk as open mode");
    }

    @Test
    void openNetworkAlonePromotesToL4() {
        assertEquals(RiskLevel.L4, risk("open", BASELINE_DENY, BASELINE_ALLOW));
    }

    @Test
    void strictNetworkPromotesToL3() {
        assertEquals(RiskLevel.L3, risk("strict", Set.of(), BASELINE_DENY, BASELINE_ALLOW),
                "strict mode reaches any host with SSRF 4-layer guard — no host list, same L3 risk class as allowlist");
    }

    @Test
    void strictNetworkIgnoresHostsList() {
        assertEquals(RiskLevel.L3, risk("strict", Set.of("api.example.com"), BASELINE_DENY, BASELINE_ALLOW),
                "strict does not honor an allowlist — hosts argument must not raise or lower the level");
    }

    @Test
    void removingOneNonCriticalDenyIsL3() {
        Set<String> userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.invoke.*");
        assertEquals(RiskLevel.L3, risk("blocked", userDeny, BASELINE_ALLOW));
    }

    @Test
    void removingThreeNonCriticalDeniesEscalatesToL4() {
        Set<String> userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.invoke.*");
        userDeny.remove("java.lang.reflect.*");
        userDeny.remove("java.lang.Class");
        assertEquals(RiskLevel.L4, risk("blocked", userDeny, BASELINE_ALLOW));
    }

    @Test
    void removingCriticalDenyEscalatesToL5() {
        Set<String> userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.System");
        assertEquals(RiskLevel.L5, risk("blocked", userDeny, BASELINE_ALLOW));
    }

    @Test
    void removingRuntimeOrProcessAlsoCritical() {
        Set<String> userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.Runtime");
        assertEquals(RiskLevel.L5, risk("blocked", userDeny, BASELINE_ALLOW));
        userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.ProcessBuilder");
        assertEquals(RiskLevel.L5, risk("blocked", userDeny, BASELINE_ALLOW));
    }

    @Test
    void addingHarmlessAllowIsL3() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.text.NumberFormat");
        assertEquals(RiskLevel.L3, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void addingReflectionAllowIsL4() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.lang.reflect.Method");
        assertEquals(RiskLevel.L4, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void addingNetworkAllowIsL4() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.net.Socket");
        assertEquals(RiskLevel.L4, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void addingJsoupAllowIsL4() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("org.jsoup.Jsoup");
        assertEquals(RiskLevel.L4, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void addingFileReadAllowIsL4() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.io.FileReader");
        assertEquals(RiskLevel.L4, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void addingFileWriteAllowIsL5() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.io.FileWriter");
        assertEquals(RiskLevel.L5, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void fsReadOnlyHelperIsL3() {
        RiskLevel level = calc.compute(new Inputs("blocked", null, true, false, false,
                BASELINE_DENY, BASELINE_DENY, BASELINE_ALLOW, BASELINE_ALLOW));
        assertEquals(RiskLevel.L3, level);
    }

    @Test
    void fsReadWriteHelperIsL4() {
        RiskLevel level = calc.compute(new Inputs("blocked", null, true, true, false,
                BASELINE_DENY, BASELINE_DENY, BASELINE_ALLOW, BASELINE_ALLOW));
        assertEquals(RiskLevel.L4, level);
    }

    @Test
    void destructiveFileOpIsL5() {
        RiskLevel level = calc.compute(new Inputs("blocked", null, true, true, true,
                BASELINE_DENY, BASELINE_DENY, BASELINE_ALLOW, BASELINE_ALLOW));
        assertEquals(RiskLevel.L5, level);
    }

    @Test
    void addingCriticalAllowIsL5() {
        Set<String> userAllow = new java.util.HashSet<>(BASELINE_ALLOW);
        userAllow.add("java.lang.System");
        assertEquals(RiskLevel.L5, risk("blocked", BASELINE_DENY, userAllow));
    }

    @Test
    void mostSevereSignalWins() {
        Set<String> userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.System");
        assertEquals(RiskLevel.L5, risk("open", userDeny, BASELINE_ALLOW),
                "open (L4) + critical deny removed (L5) → L5");

        userDeny = new java.util.HashSet<>(BASELINE_DENY);
        userDeny.remove("java.lang.invoke.*");
        userDeny.remove("java.lang.reflect.*");
        userDeny.remove("java.lang.Class");
        assertEquals(RiskLevel.L4, risk("allowlist", userDeny, BASELINE_ALLOW),
                "allowlist+hosts (L3) + 3 non-critical denies removed (L4) → L4");
    }

    @Test
    void nullCollectionsTreatedAsEmpty() {
        assertEquals(RiskLevel.L0, calc.compute(new Inputs("blocked", null, false, false, false, null, null, null, null)));
    }

    @Test
    void unknownNetworkModeStaysL0() {
        // Defensive — typos should not silently degrade safety
        assertEquals(RiskLevel.L0, risk("unknown-mode", BASELINE_DENY, BASELINE_ALLOW));
    }

    @Test
    void criticalClassDetection() {
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isCriticalClass("java.lang.System"));
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isCriticalClass("java.lang.Runtime"));
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isCriticalClass("java.lang.ProcessBuilder"));
        org.junit.jupiter.api.Assertions.assertFalse(SandboxPostureCalculator.isCriticalClass("java.util.HashMap"));
        org.junit.jupiter.api.Assertions.assertFalse(SandboxPostureCalculator.isCriticalClass(null));
    }

    @Test
    void reflectionClassDetection() {
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isReflectionClass("java.lang.reflect.Method"));
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isReflectionClass("java.lang.invoke.MethodHandle"));
        org.junit.jupiter.api.Assertions.assertTrue(SandboxPostureCalculator.isReflectionClass("java.lang.Class"));
        org.junit.jupiter.api.Assertions.assertFalse(SandboxPostureCalculator.isReflectionClass("java.lang.String"));
    }
}
