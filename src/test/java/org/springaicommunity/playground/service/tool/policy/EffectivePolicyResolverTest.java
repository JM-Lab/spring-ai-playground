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
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.SandboxProfile;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.Overrides;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectivePolicyResolverTest {

    private final EffectivePolicyResolver resolver = new EffectivePolicyResolver();

    private static JsSandbox baseline() {
        return new JsSandbox(true, false, false, false, 50_000L,
                Set.of("java.lang.System", "java.lang.reflect.*"),
                Set.of("java.lang.*", "java.util.*"),
                Map.of());
    }

    @Test
    void resolveWithNoProfileReturnsDefaults() {
        EffectivePolicy policy = resolver.resolve(baseline(), null);

        assertEquals(Set.of("java.lang.*", "java.util.*"), policy.allowClasses());
        assertEquals(Set.of("java.lang.System", "java.lang.reflect.*"), policy.denyClasses());
        assertTrue(policy.allowNetworkIo());
        assertFalse(policy.allowFileIo());
        assertTrue(policy.javaInterop());
        assertNull(policy.profileName());
        assertNull(policy.level());
        assertNull(policy.network());
        assertEquals(50_000L, policy.maxStatements());
    }

    @Test
    void resolveWithProfileUnionsAllowAndDeny() {
        SandboxProfile webFetch = new SandboxProfile("L3", null, true,
                Set.of("java.net.http.*"), Set.of("java.io.File"),
                new NetworkPolicy("allowlist", Set.of("api.example.com"), Set.of("10.0.0.0/8")),
                null, 100_000L, 30_000L);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of("java.lang.System"), Set.of("java.lang.*"),
                Map.of("web-fetch", webFetch));

        EffectivePolicy policy = resolver.resolve(sandbox, "web-fetch");

        assertTrue(policy.allowClasses().contains("java.lang.*"));
        assertTrue(policy.allowClasses().contains("java.net.http.*"));
        assertTrue(policy.denyClasses().contains("java.lang.System"));
        assertTrue(policy.denyClasses().contains("java.io.File"));
        assertEquals("L3", policy.level());
        assertEquals("web-fetch", policy.profileName());
        assertEquals(100_000L, policy.maxStatements());
        assertEquals(30L, policy.timeoutSeconds());
        assertNotNull(policy.network());
        assertEquals("allowlist", policy.network().egressLevel());
    }

    @Test
    void resolveWithExtendsChainWalksRootToLeaf() {
        SandboxProfile root = new SandboxProfile("L0", null, false,
                Set.of("java.lang.String"), Set.of("java.io.*"), null, null, null, null);
        SandboxProfile child = new SandboxProfile("L1", "root", null,
                Set.of("java.time.*"), Set.of(), null, null, null, null);
        SandboxProfile leaf = new SandboxProfile("L2", "child", true,
                Set.of("java.util.*"), Set.of("java.net.*"), null, null, null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of(), Set.of(),
                Map.of("root", root, "child", child, "leaf", leaf));

        EffectivePolicy policy = resolver.resolve(sandbox, "leaf");

        assertTrue(policy.allowClasses().contains("java.lang.String"));
        assertTrue(policy.allowClasses().contains("java.time.*"));
        assertTrue(policy.allowClasses().contains("java.util.*"));
        assertTrue(policy.denyClasses().contains("java.io.*"));
        assertTrue(policy.denyClasses().contains("java.net.*"));
        assertTrue(policy.javaInterop());
        assertEquals("L2", policy.level());
    }

    @Test
    void resolveWithCycleThrows() {
        SandboxProfile a = new SandboxProfile("L0", "b", null, Set.of(), Set.of(), null, null, null, null);
        SandboxProfile b = new SandboxProfile("L0", "a", null, Set.of(), Set.of(), null, null, null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of(), Set.of(), Map.of("a", a, "b", b));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(sandbox, "a"));
        assertTrue(ex.getMessage().contains("cycle"));
    }

    @Test
    void resolveWithUnknownProfileThrows() {
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of(), Set.of(), Map.of());
        assertThrows(IllegalStateException.class, () -> resolver.resolve(sandbox, "missing"));
    }

    @Test
    void resolveFailsFastWhenAllowOverlapsDeny() {
        SandboxProfile bad = new SandboxProfile("L0", null, null,
                Set.of("java.lang.System"), Set.of(), null, null, null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of("java.lang.System"), Set.of("java.lang.*"),
                Map.of("bad", bad));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(sandbox, "bad"));
        assertTrue(ex.getMessage().contains("conflict"));
    }

    @Test
    void resolveHonorsJavaInteropFalseInProfile() {
        SandboxProfile pure = new SandboxProfile("L0", null, false,
                Set.of(), Set.of(), null, null, null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of("java.lang.System"), Set.of("java.lang.*"),
                Map.of("pure", pure));

        EffectivePolicy policy = resolver.resolve(sandbox, "pure");
        assertFalse(policy.javaInterop());
    }

    @Test
    void overridesAddDenyExtendsBaseline() {
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of("java.io.File"), Set.of(), null, null, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertTrue(policy.denyClasses().contains("java.io.File"));
        assertTrue(policy.denyClasses().contains("java.lang.System"), "baseline still in deny");
    }

    @Test
    void overridesRemoveDenyLiftsBaseline() {
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of(), Set.of("java.lang.reflect.*"), null, null, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertFalse(policy.denyClasses().contains("java.lang.reflect.*"),
                "per-tool unblock — reflection no longer denied");
        assertTrue(policy.denyClasses().contains("java.lang.System"), "other baseline denies stay");
    }

    @Test
    void overridesAddAllowExtendsBaseline() {
        Overrides overrides = new Overrides(Set.of("org.jsoup.*"), Set.of(), Set.of(), Set.of(), null, null, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertTrue(policy.allowClasses().contains("org.jsoup.*"));
        assertTrue(policy.allowClasses().contains("java.lang.*"), "baseline allow stays");
    }

    @Test
    void overridesRemoveAllowRevokesBaseline() {
        Overrides overrides = new Overrides(Set.of(), Set.of("java.util.*"), Set.of(), Set.of(), null, null, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertFalse(policy.allowClasses().contains("java.util.*"));
        assertTrue(policy.allowClasses().contains("java.lang.*"));
    }

    @Test
    void overridesConflictAddAllowAndAddDenyFailsFast() {
        Overrides overrides = new Overrides(Set.of("java.io.File"), Set.of(), Set.of("java.io.File"), Set.of(), null, null, null, null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.resolve(baseline(), null, overrides));
        assertTrue(ex.getMessage().contains("conflict"));
    }

    @Test
    void overridesNetworkIoFlipsBaseline() {
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of(), Set.of(),
                null, false, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertFalse(policy.allowNetworkIo());
    }

    @Test
    void overridesFileReadOnly() {
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of(), Set.of(),
                null, null, true, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertTrue(policy.fileRead());
        assertFalse(policy.fileWrite());
        assertTrue(policy.allowFileIo());
    }

    @Test
    void overridesFileReadWrite() {
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of(), Set.of(),
                null, null, true, true);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertTrue(policy.fileRead());
        assertTrue(policy.fileWrite());
    }

    @Test
    void overridesNetworkPolicyApplied() {
        NetworkPolicy net = new NetworkPolicy("allowlist",
                Set.of("api.example.com"), Set.of());
        Overrides overrides = new Overrides(Set.of(), Set.of(), Set.of(), Set.of(),
                net, null, null, null);
        EffectivePolicy policy = resolver.resolve(baseline(), null, overrides);
        assertEquals("allowlist", policy.network().egressLevel());
        assertTrue(policy.network().hostsAllow().contains("api.example.com"));
    }

    @Test
    void profileFileModeReadOnly() {
        SandboxProfile readOnly = new SandboxProfile("L3", null, false,
                Set.of(), Set.of(), null, "read", null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of(), Set.of(), Map.of("read-only", readOnly));
        EffectivePolicy policy = resolver.resolve(sandbox, "read-only");
        assertTrue(policy.fileRead());
        assertFalse(policy.fileWrite());
    }

    @Test
    void profileFileModeReadWrite() {
        SandboxProfile rw = new SandboxProfile("L4", null, false,
                Set.of(), Set.of(), null, "read+write", null, null);
        JsSandbox sandbox = new JsSandbox(true, false, false, false, 50_000L,
                Set.of(), Set.of(), Map.of("rw", rw));
        EffectivePolicy policy = resolver.resolve(sandbox, "rw");
        assertTrue(policy.fileRead());
        assertTrue(policy.fileWrite());
    }

    @Test
    void emptyOverridesEqualsNoOverrides() {
        EffectivePolicy a = resolver.resolve(baseline(), null);
        EffectivePolicy b = resolver.resolve(baseline(), null, Overrides.empty());
        assertEquals(a, b);
    }

    @Test
    void nullOverridesTreatedAsEmpty() {
        EffectivePolicy a = resolver.resolve(baseline(), null);
        EffectivePolicy b = resolver.resolve(baseline(), null, null);
        assertEquals(a, b);
    }
}
