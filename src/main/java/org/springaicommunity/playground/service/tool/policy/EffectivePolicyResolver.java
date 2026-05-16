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

import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.SandboxProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class EffectivePolicyResolver {

    public record EffectivePolicy(Set<String> allowClasses, Set<String> denyClasses, boolean allowNetworkIo,
                                  boolean fileRead, boolean fileWrite, boolean allowNativeAccess,
                                  boolean allowCreateThread, long maxStatements, long timeoutSeconds,
                                  boolean javaInterop, NetworkPolicy network,
                                  String profileName, String level) {
        public boolean allowFileIo() {
            return fileRead || fileWrite;
        }
    }

    public record Overrides(Set<String> addAllowClasses, Set<String> removeAllowClasses,
                            Set<String> addDenyClasses, Set<String> removeDenyClasses,
                            NetworkPolicy networkOverride, Boolean networkIoOverride,
                            Boolean fileReadOverride, Boolean fileWriteOverride) {
        public static Overrides empty() {
            return new Overrides(Set.of(), Set.of(), Set.of(), Set.of(), null, null, null, null);
        }
    }

    private static final int MAX_EXTENDS_DEPTH = 8;

    public EffectivePolicy resolve(JsSandbox sandbox, String profileName) {
        return resolve(sandbox, profileName, Overrides.empty());
    }

    public EffectivePolicy resolve(JsSandbox sandbox, String profileName, Overrides overrides) {
        Objects.requireNonNull(sandbox, "sandbox config required");

        Set<String> allow = new LinkedHashSet<>(sandbox.allowClasses() == null ? Set.of() : sandbox.allowClasses());
        Set<String> deny = new LinkedHashSet<>(sandbox.denyClasses() == null ? Set.of() : sandbox.denyClasses());
        boolean networkIo = sandbox.allowNetworkIo();
        boolean fileRead = sandbox.allowFileIo();
        boolean fileWrite = sandbox.allowFileIo();
        boolean nativeAccess = sandbox.allowNativeAccess();
        boolean createThread = sandbox.allowCreateThread();
        long maxStatements = sandbox.maxStatements() == null ? 500_000L : sandbox.maxStatements();
        long timeoutSeconds = 30L;
        boolean javaInterop = true;
        NetworkPolicy network = null;
        String level = null;

        if (profileName != null && !profileName.isBlank()) {
            for (SandboxProfile profile : resolveProfileChain(sandbox, profileName)) {
                if (profile.allowClasses() != null) allow.addAll(profile.allowClasses());
                if (profile.denyClasses() != null) deny.addAll(profile.denyClasses());
                if (profile.javaInterop() != null) javaInterop = profile.javaInterop();
                if (profile.network() != null) network = profile.network();
                if (profile.fileMode() != null) {
                    String fm = profile.fileMode().toLowerCase();
                    fileRead = !"off".equals(fm);
                    fileWrite = "read+write".equals(fm) || "readwrite".equals(fm);
                }
                if (profile.level() != null) level = profile.level();
                if (profile.maxStatements() != null) maxStatements = profile.maxStatements();
                if (profile.timeoutMs() != null) timeoutSeconds = Math.max(1L, profile.timeoutMs() / 1000L);
            }
        }

        Overrides safeOverrides = overrides == null ? Overrides.empty() : overrides;
        if (safeOverrides.addAllowClasses() != null) allow.addAll(safeOverrides.addAllowClasses());
        if (safeOverrides.removeAllowClasses() != null) allow.removeAll(safeOverrides.removeAllowClasses());
        if (safeOverrides.addDenyClasses() != null) deny.addAll(safeOverrides.addDenyClasses());
        if (safeOverrides.removeDenyClasses() != null) deny.removeAll(safeOverrides.removeDenyClasses());
        if (safeOverrides.networkOverride() != null) network = safeOverrides.networkOverride();
        if (safeOverrides.networkIoOverride() != null) networkIo = safeOverrides.networkIoOverride();
        if (safeOverrides.fileReadOverride() != null) fileRead = safeOverrides.fileReadOverride();
        if (safeOverrides.fileWriteOverride() != null) fileWrite = safeOverrides.fileWriteOverride();

        Set<String> conflict = new HashSet<>(allow);
        conflict.retainAll(deny);
        if (!conflict.isEmpty()) {
            throw new IllegalStateException(
                    "Sandbox policy conflict — allow-classes and deny-classes overlap: " + conflict);
        }

        return new EffectivePolicy(Set.copyOf(allow), Set.copyOf(deny), networkIo, fileRead, fileWrite,
                nativeAccess, createThread, maxStatements, timeoutSeconds, javaInterop, network,
                profileName, level);
    }

    private List<SandboxProfile> resolveProfileChain(JsSandbox sandbox, String leafName) {
        List<SandboxProfile> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String current = leafName;
        int depth = 0;
        while (current != null && !current.isBlank()) {
            if (depth++ > MAX_EXTENDS_DEPTH) {
                throw new IllegalStateException(
                        "Sandbox profile extends chain exceeds depth " + MAX_EXTENDS_DEPTH + " starting at " + leafName);
            }
            if (!visited.add(current)) {
                throw new IllegalStateException("Sandbox profile extends cycle detected: " + visited + " → " + current);
            }
            SandboxProfile profile = sandbox.profiles() == null ? null : sandbox.profiles().get(current);
            if (profile == null) {
                throw new IllegalStateException("Unknown sandbox profile: " + current);
            }
            chain.add(0, profile);
            current = profile.extendsProfile();
        }
        return chain;
    }
}
