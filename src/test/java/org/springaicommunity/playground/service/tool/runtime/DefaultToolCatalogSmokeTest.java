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
package org.springaicommunity.playground.service.tool.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolCatalogSmokeTest {

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]+)}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<Map<String, Object>> allSpecs = new ArrayList<>();
    private static final JsToolExecutor executor;

    @TempDir
    Path workspace;

    static {
        try {
            for (String fname : List.of("/tool/default-tool-specs.json",
                    "/tool/default-tool-specs-builtin.json",
                    "/tool/default-tool-specs-builtin-fs.json",
                    "/tool/default-tool-specs-builtin-helpers.json",
                    "/tool/default-tool-specs-kr.json",
                    "/tool/default-tool-specs-network.json")) {
                try (InputStream in = DefaultToolCatalogSmokeTest.class.getResourceAsStream(fname)) {
                    if (in == null) continue;
                    allSpecs.addAll(MAPPER.readValue(in, new TypeReference<List<Map<String, Object>>>() {}));
                }
            }
            executor = new JsToolExecutor(30L,
                    new JsSandbox(true, true, false, false, 5_000_000L,
                            Set.of("java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder",
                                    "java.lang.Process", "java.lang.Class", "java.lang.invoke.*",
                                    "java.lang.reflect.*"),
                            Set.of("java.lang.*", "java.math.*", "java.time.*", "java.util.*", "java.text.*"),
                            Map.of()),
                    null);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load tool catalog", e);
        }
    }

    @TestFactory
    Stream<DynamicTest> localToolsPassWithTestValues() {
        return allSpecs.stream()
                .filter(this::isLocal)
                .map(spec -> DynamicTest.dynamicTest((String) spec.get("name"), () -> {
                    seedWorkspaceFiles();
                    JsExecutionResult result = runFromTestValues(spec, permissiveLocalPolicy());
                    assertThat(result.isOk())
                            .as("tool %s — error: %s", spec.get("name"), result.error())
                            .isTrue();
                }));
    }

    @TestFactory
    Stream<DynamicTest> envKeyedToolsFailGracefullyWithoutKey() {
        return allSpecs.stream()
                .filter(this::isEnvKeyed)
                .map(spec -> DynamicTest.dynamicTest(spec.get("name") + " (no env)", () -> {
                    Set<String> envVars = envVarsFor(spec);
                    boolean anySet = envVars.stream().anyMatch(v -> System.getenv(v) != null);
                    Assumptions.assumeFalse(anySet, "env var present; skipping graceful-failure check");
                    JsExecutionResult result = runFromTestValues(spec, permissiveNetworkPolicy(spec));
                    assertThat(result.isOk())
                            .as("expected graceful failure for %s, but succeeded", spec.get("name"))
                            .isFalse();
                    assertThat(result.error() == null ? "" : result.error())
                            .as("tool %s should surface env-not-set / required message", spec.get("name"))
                            .containsAnyOf("env var not set", "required (set staticVariable or env var)");
                }));
    }

    @TestFactory
    Stream<DynamicTest> networkToolsOptIn() {
        boolean enabled = "true".equalsIgnoreCase(System.getenv("RUN_NETWORK_SMOKE"));
        return allSpecs.stream()
                .filter(this::isNetworkNoKey)
                .map(spec -> DynamicTest.dynamicTest(spec.get("name") + " (network)", () -> {
                    Assumptions.assumeTrue(enabled, "RUN_NETWORK_SMOKE=true not set; skipping live network test");
                    JsExecutionResult result = runFromTestValues(spec, permissiveNetworkPolicy(spec));
                    assertThat(result.isOk())
                            .as("tool %s network call failed: %s", spec.get("name"), result.error())
                            .isTrue();
                }));
    }

    private void seedWorkspaceFiles() throws IOException {
        Files.writeString(workspace.resolve("README.md"),
                "# README\n\nSample workspace seeded by smoke test.\n\n## TODO\n- one\n- two\n\n## FIXME\nplaceholder\n\nL8\nL9\nL10\nL11\nL12\n");
        Files.writeString(workspace.resolve("data.csv"),
                "id,name,score\n1,alice,87\n2,bob,93\n3,carol,72\n");
    }

    private boolean isLocal(Map<String, Object> spec) {
        return !isNetwork(spec) && !isEnvKeyed(spec);
    }

    private boolean isNetwork(Map<String, Object> spec) {
        Map<?, ?> sandbox = (Map<?, ?>) spec.get("sandboxOverrides");
        if (sandbox == null) return false;
        Object mode = sandbox.get("networkMode");
        if (mode == null) return false;
        String s = mode.toString().toLowerCase(Locale.ROOT);
        return !s.equals("blocked");
    }

    private boolean isNetworkNoKey(Map<String, Object> spec) {
        return isNetwork(spec) && !isEnvKeyed(spec);
    }

    private boolean isEnvKeyed(Map<String, Object> spec) {
        return !envVarsFor(spec).isEmpty();
    }

    private Set<String> envVarsFor(Map<String, Object> spec) {
        Object sv = spec.get("staticVariables");
        if (!(sv instanceof List<?> list)) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> entry)) continue;
            for (Object value : entry.values()) {
                if (!(value instanceof String text)) continue;
                Matcher m = ENV_PLACEHOLDER.matcher(text);
                if (m.matches()) out.add(m.group(1));
            }
        }
        return out;
    }

    private JsExecutionResult runFromTestValues(Map<String, Object> spec, EffectivePolicy policy) {
        Map<String, Object> mergeParams = new LinkedHashMap<>();
        LinkedHashSet<String> declaredNames = new LinkedHashSet<>();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) spec.getOrDefault("params", List.of());
        for (Map<String, Object> p : params) {
            String name = (String) p.get("name");
            if (name != null && !name.isBlank()) declaredNames.add(name);
            String testValue = (String) p.get("testValue");
            String type = (String) p.get("type");
            mergeParams.put(name, convertTestValue(testValue, type));
        }
        Object sv = spec.get("staticVariables");
        if (sv instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    String key = (String) e.getKey();
                    if (key != null && !key.isBlank()) declaredNames.add(key);
                    mergeParams.put(key, e.getValue());
                }
            }
        }
        Path fsBase = isFsBackedTool(spec) ? workspace : null;
        return executor.execute(
                new JsExecutionParams(mergeParams, (String) spec.get("code"), declaredNames), policy, fsBase);
    }

    private boolean isFsBackedTool(Map<String, Object> spec) {
        Map<?, ?> sandbox = (Map<?, ?>) spec.get("sandboxOverrides");
        if (sandbox == null) return false;
        Object read = sandbox.get("fileRead");
        Object write = sandbox.get("fileWrite");
        return Boolean.TRUE.equals(read) || Boolean.TRUE.equals(write);
    }

    private EffectivePolicy permissiveLocalPolicy() {
        return new EffectivePolicy(Set.of(), Set.of(), false, true, true, false, false,
                5_000_000L, 30L, true, null, null, null);
    }

    private EffectivePolicy permissiveNetworkPolicy(Map<String, Object> spec) {
        Set<String> hosts = extractHosts(spec);
        NetworkPolicy net = hosts.isEmpty()
                ? new NetworkPolicy("permissive", Set.of(), Set.of())
                : new NetworkPolicy("allowlist", hosts, Set.of());
        return new EffectivePolicy(Set.of(), Set.of(), true, true, true, false, false,
                5_000_000L, 30L, true, net, null, null);
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractHosts(Map<String, Object> spec) {
        Map<String, Object> sandbox = (Map<String, Object>) spec.get("sandboxOverrides");
        if (sandbox == null) return Set.of();
        Object hosts = sandbox.get("hostsAllow");
        if (!(hosts instanceof List<?> list)) return Set.of();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Object o : list) if (o != null) out.add(o.toString());
        return out;
    }

    private static Object convertTestValue(String raw, String type) {
        boolean blank = raw == null || raw.isBlank();
        if (type == null) return raw;
        try {
            return switch (type.toUpperCase(Locale.ROOT)) {
                case "STRING" -> raw == null ? "" : raw;
                case "NUMBER" -> blank ? null : Double.parseDouble(raw);
                case "INTEGER" -> blank ? null : Long.parseLong(raw);
                case "BOOLEAN" -> blank ? null : Boolean.parseBoolean(raw);
                case "ARRAY" -> blank ? List.of()
                        : MAPPER.readValue(raw, new TypeReference<List<Object>>() {});
                case "OBJECT" -> blank ? Map.of()
                        : MAPPER.readValue(raw, new TypeReference<Map<String, Object>>() {});
                default -> raw;
            };
        } catch (Exception e) {
            throw new RuntimeException("failed to coerce testValue=" + raw + " to type=" + type, e);
        }
    }
}
