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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.service.tool.ToolWorkspace;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.util.EnvVarResolver;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.lang.reflect.Array;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JsToolExecutor {

    public record JsExecutionResult(boolean isOk, Object result, String error, @JsonIgnore String debugInfo) {}

    public record JsExecutionParams(Map<String, Object> params, String code, Collection<String> declaredNames) {
        public JsExecutionParams(Map<String, Object> params, String code) {
            this(params, code, List.of());
        }
    }

    private static final Map<String, String> JS_OPTIONS = Map.ofEntries(
            Map.entry("js.ecmascript-version", "2024"),
            Map.entry("js.intl-402", "true"),
            Map.entry("js.text-encoding", "true"),
            Map.entry("js.temporal", "true"),
            Map.entry("js.iterator-helpers", "true"),
            Map.entry("js.new-set-methods", "true"),
            Map.entry("js.regexp-unicode-sets", "true"));

    private static final Engine ENGINE = Engine.newBuilder()
            .allowExperimentalOptions(true)
            .options(JS_OPTIONS)
            .build();

    private static final HostAccess HOST_ACCESS = HostAccess.newBuilder().allowPublicAccess(true).build();

    private static final Set<String> HOST_INJECTED_GLOBALS = JsRuntimeGlobals.INJECTED_NAMES;

    private final long timeoutSeconds;
    private final EffectivePolicy defaultPolicy;
    private final SafeFs.FsScope defaultScope;
    private final ExecutorService executor;

    public JsToolExecutor(Long timeoutSeconds, JsSandbox jsSandbox, Path workspaceBase) {
        this.timeoutSeconds = Optional.ofNullable(timeoutSeconds).orElse(30L);
        this.defaultPolicy = jsSandbox == null ? null : toDefaultPolicy(jsSandbox, this.timeoutSeconds);
        this.defaultScope = buildDefaultScope(workspaceBase);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    private static SafeFs.FsScope buildDefaultScope(Path workspaceBase) {
        Path workspace = workspaceBase.toAbsolutePath().normalize();
        Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        return new SafeFs.FsScope(workspace, dedupeRoots(List.of(home, workspace)));
    }

    private static List<Path> dedupeRoots(List<Path> roots) {
        List<Path> sorted = roots.stream().map(p -> p.toAbsolutePath().normalize()).distinct()
                .sorted(Comparator.comparingInt(Path::getNameCount)).toList();
        List<Path> out = new ArrayList<>();
        for (Path p : sorted) {
            if (out.stream().noneMatch(p::startsWith)) out.add(p);
        }
        return out;
    }

    private static EffectivePolicy toDefaultPolicy(JsSandbox sandbox, long timeoutSeconds) {
        long maxStatements = sandbox.maxStatements() == null ? 500_000L : sandbox.maxStatements();
        return new EffectivePolicy(
                sandbox.allowClasses() == null ? Set.of() : sandbox.allowClasses(),
                sandbox.denyClasses() == null ? Set.of() : sandbox.denyClasses(),
                sandbox.allowNetworkIo(), sandbox.allowFileIo(), sandbox.allowFileIo(),
                sandbox.allowNativeAccess(), sandbox.allowCreateThread(), maxStatements, timeoutSeconds,
                true, null, null, null);
    }

    static boolean isClassAllowed(String className, JsSandbox jsSandbox) {
        if (matchesAnyPattern(className, jsSandbox.denyClasses())) {
            return false;
        }
        return matchesAnyPattern(className, jsSandbox.allowClasses());
    }

    static boolean isClassAllowed(String className, EffectivePolicy policy) {
        if (matchesAnyPattern(className, policy.denyClasses())) {
            return false;
        }
        return matchesAnyPattern(className, policy.allowClasses());
    }

    private static boolean matchesAnyPattern(String className, Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return false;
        return patterns.stream().anyMatch(pattern -> {
            if (pattern.endsWith(".*")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                return className.startsWith(prefix + ".");
            }
            return className.equals(pattern);
        });
    }

    public JsExecutionResult execute(JsExecutionParams jsExecutionParams) {
        return execute(jsExecutionParams, defaultPolicy, null);
    }

    public JsExecutionResult execute(JsExecutionParams jsExecutionParams, EffectivePolicy policy) {
        return execute(jsExecutionParams, policy, null, null);
    }

    public JsExecutionResult execute(JsExecutionParams jsExecutionParams, EffectivePolicy policy,
                                     Path overrideFsBase) {
        return execute(jsExecutionParams, policy, overrideFsBase, null);
    }

    private SafeFs.FsScope resolveScope(Path overrideFsBase, String conversationId) {
        if (overrideFsBase != null) return SafeFs.FsScope.confined(overrideFsBase);
        String segment = ToolWorkspace.safeSegment(conversationId);
        if (segment != null) {
            Path writeBase = this.defaultScope.workspace().resolve(segment).normalize();
            if (writeBase.startsWith(this.defaultScope.workspace())) {
                return new SafeFs.FsScope(writeBase, this.defaultScope.readRoots());
            }
        }
        return this.defaultScope;
    }

    public JsExecutionResult execute(JsExecutionParams jsExecutionParams, EffectivePolicy policy,
                                     Path overrideFsBase, String conversationId) {
        Map<String, Object> filteredParams = new LinkedHashMap<>();
        if (jsExecutionParams.params() != null) {
            jsExecutionParams.params().forEach((name, rawValue) -> {
                if (rawValue == null) return;
                if (rawValue instanceof String s && s.isBlank()) return;
                filteredParams.put(name, rawValue);
            });
        }
        String variableDeclarations = "";
        if (jsExecutionParams.declaredNames() != null) {
            List<String> missingNames = jsExecutionParams.declaredNames().stream()
                    .filter(name -> name != null && !name.isBlank() && !filteredParams.containsKey(name))
                    .distinct()
                    .toList();
            if (!missingNames.isEmpty()) {
                variableDeclarations = "var " + String.join(", ", missingNames) + ";\n";
            }
        }
        String jsCode = """
                (async function() {
                    %s%s
                })();
                """.formatted(variableDeclarations, jsExecutionParams.code());

        long effectiveTimeout = policy == null ? this.timeoutSeconds : policy.timeoutSeconds();
        Context.Builder contextBuilder = buildContext(policy);

        List<String> logList = new ArrayList<>();
        Future<JsExecutionResult> future = executor.submit(() -> {
            try (Context context = contextBuilder.build()) {
                Value bindings = context.getBindings("js");

                Map<String, String> envBackedVariables = new HashMap<>();
                Set<String> envSecretValues = new HashSet<>();
                filteredParams.forEach((name, rawValue) -> bindings.putMember(name,
                        resolveParamValue(rawValue, name, envBackedVariables, envSecretValues)));

                Map<String, String> initialState = snapshotVariables(bindings);

                logList.add("=== Execution Log ===");
                installConsoleLog(bindings, logList, envSecretValues);
                SafeFs.FsScope scope = resolveScope(overrideFsBase, conversationId);
                JsRuntimeGlobals.installAll(bindings, policy, scope);

                Value jsResultValue = awaitPromise(context.eval("js", jsCode));
                Object jsResult = jsResultValue.isNull() ? "undefined" :
                        deepCopyPolyglot(jsResultValue.as(Object.class));

                if (!envSecretValues.isEmpty()) {
                    jsResult = maskSecretsInResult(jsResult, envSecretValues);
                }

                Map<String, String> finalState = snapshotVariables(bindings);

                logList.add("\n=== Final State ===");
                mergeStateLogs(logList, initialState, finalState, envBackedVariables);

                return new JsExecutionResult(true, jsResult, null, buildDebugInfo(logList));
            } catch (Exception e) {
                logList.add(classifyError(e));
                return new JsExecutionResult(false, "", userFacingErrorMessage(e), buildDebugInfo(logList));
            }
        });

        try {
            return future.get(effectiveTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            logList.add("[TIMEOUT] execution: " + effectiveTimeout + "s-limit-exceeded");
            return new JsExecutionResult(false, "", "Execution timed out after " + effectiveTimeout + " seconds",
                    buildDebugInfo(logList));
        } catch (Exception e) {
            logList.add(classifyError(e));
            return new JsExecutionResult(false, "", userFacingErrorMessage(e), buildDebugInfo(logList));
        }
    }

    static String userFacingErrorMessage(Throwable e) {
        if (e == null) return "unknown";
        JsHelperException helper = unwrapHelper(e);
        if (helper != null) {
            return helper.getMessage();
        }
        Throwable cur = e;
        int hops = 0;
        while (cur != null && hops++ < 16) {
            if (cur instanceof org.graalvm.polyglot.PolyglotException pe) {
                if (pe.isResourceExhausted()) return "execution: limit exceeded";
                String msg = pe.getMessage();
                return msg == null ? "guest error" : msg;
            }
            Throwable cause = cur.getCause();
            if (cause == null || cause == cur) {
                String msg = cur.getMessage();
                return msg != null ? msg : cur.getClass().getSimpleName();
            }
            cur = cause;
        }
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    private Context.Builder buildContext(EffectivePolicy policy) {
        Context.Builder builder = Context.newBuilder("js")
                .engine(ENGINE)
                .allowHostAccess(HOST_ACCESS)
                .allowAllAccess(false);
        if (policy == null) {
            return builder.allowHostClassLookup(className -> false);
        }
        IOAccess ioConfig = IOAccess.newBuilder()
                .allowHostFileAccess(policy.allowFileIo())
                .allowHostSocketAccess(policy.allowNetworkIo())
                .build();
        if (policy.javaInterop()) {
            builder.allowHostClassLookup(className -> isClassAllowed(className, policy));
        } else {
            builder.allowHostClassLookup(className -> false);
        }
        return builder
                .allowIO(ioConfig)
                .allowNativeAccess(policy.allowNativeAccess())
                .allowCreateThread(policy.allowCreateThread())
                .resourceLimits(ResourceLimits.newBuilder().statementLimit(policy.maxStatements(), null).build());
    }

    private Object resolveParamValue(Object rawValue, String paramName,
            Map<String, String> envBackedVariables, Set<String> envSecretValues) {
        if (!(rawValue instanceof String str)) return materialiseForJs(rawValue);
        Optional<String> envName = EnvVarResolver.anchoredEnvName(str);
        if (envName.isEmpty()) return rawValue;
        Optional<String> resolved = EnvVarResolver.lookup(envName.get());
        if (resolved.isEmpty()) {
            throw new IllegalStateException(paramName + " env var not set: " + str);
        }
        envBackedVariables.put(paramName, envName.get());
        envSecretValues.add(resolved.get());
        return resolved.get();
    }

    private Object materialiseForJs(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(k.toString(), materialiseForJs(v)));
            return ProxyObject.fromMap(out);
        }
        if (value instanceof List<?> list) {
            Object[] arr = new Object[list.size()];
            for (int i = 0; i < list.size(); i++) arr[i] = materialiseForJs(list.get(i));
            return ProxyArray.fromArray(arr);
        }
        return value;
    }

    private Object maskSecretsInResult(Object value, Set<String> secrets) {
        return switch (value) {
            case null -> null;
            case String s -> maskKnownSecrets(s, secrets);
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(k.toString(), maskSecretsInResult(v, secrets)));
                yield copy;
            }
            case Iterable<?> it -> {
                List<Object> copy = new ArrayList<>();
                for (Object item : it) copy.add(maskSecretsInResult(item, secrets));
                yield copy;
            }
            default -> value;
        };
    }

    private Object deepCopyPolyglot(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Map<?, ?> map -> {
                Map<String, Object> copy = new LinkedHashMap<>();
                map.forEach((k, v) -> copy.put(k.toString(), deepCopyPolyglot(v)));
                return copy;
            }
            case Iterable<?> it -> {
                List<Object> copy = new ArrayList<>();
                for (Object v : it) {
                    copy.add(deepCopyPolyglot(v));
                }
                return copy;
            }
            default -> {
            }
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> copy = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                copy.add(deepCopyPolyglot(Array.get(value, i)));
            }
            return copy;
        }
        return value;
    }

    private String buildDebugInfo(List<String> logList) {
        return String.join("\n", logList);
    }

    private static JsHelperException unwrapHelper(Throwable e) {
        Throwable cur = e;
        int hops = 0;
        while (cur != null && hops++ < 16) {
            if (cur instanceof JsHelperException he) return he;
            if (cur instanceof org.graalvm.polyglot.PolyglotException pe && pe.isHostException()) {
                Throwable host = pe.asHostException();
                if (host instanceof JsHelperException he) return he;
                cur = host;
                continue;
            }
            cur = cur.getCause();
        }
        return null;
    }

    private static final Set<String> JS_ERROR_TYPES = Set.of(
            "Error", "TypeError", "ReferenceError", "SyntaxError",
            "RangeError", "URIError", "EvalError");

    private static String classifyError(Throwable e) {
        JsHelperException he = unwrapHelper(e);
        if (he != null) {
            return "[" + he.kind().name() + "] " + he.helper() + ": " + he.reason();
        }
        Throwable cur = e;
        int hops = 0;
        while (cur != null && hops++ < 16) {
            if (cur instanceof org.graalvm.polyglot.PolyglotException pe) {
                if (pe.isResourceExhausted()) {
                    return "[RESOURCE_LIMIT] execution: limit-exceeded";
                }
                if (pe.isGuestException()) {
                    return "[JS_ERROR] user-code: " + extractJsErrorType(pe.getMessage());
                }
                break;
            }
            String jsType = matchJsErrorPrefix(cur.getMessage());
            if (jsType != null) {
                return "[JS_ERROR] user-code: " + jsType;
            }
            cur = cur.getCause();
        }
        return "[RUNTIME_ERROR] execution: " + (e == null ? "null" : e.getClass().getSimpleName());
    }

    private static String matchJsErrorPrefix(String msg) {
        if (msg == null) return null;
        int colon = msg.indexOf(':');
        if (colon <= 0 || colon >= 30) return null;
        String prefix = msg.substring(0, colon);
        return JS_ERROR_TYPES.contains(prefix) ? prefix : null;
    }

    private static String extractJsErrorType(String msg) {
        if (msg == null) return "Error";
        int colon = msg.indexOf(':');
        if (colon <= 0) return "Error";
        String type = msg.substring(0, colon);
        return JS_ERROR_TYPES.contains(type) ? type : "Error";
    }

    private Value awaitPromise(Value promise) throws Exception {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        promise.invokeMember("then", (ProxyExecutable) args -> {
            completableFuture.complete(args[0]);
            return null;
        });
        promise.invokeMember("catch", (ProxyExecutable) args -> {
            Value err = args[0];
            Throwable cause = err != null && err.isHostObject() && err.asHostObject() instanceof Throwable t
                    ? t : new RuntimeException(err == null ? "null" : err.toString());
            completableFuture.completeExceptionally(cause);
            return null;
        });
        return completableFuture.get();
    }

    private void installConsoleLog(Value bindings, List<String> logList, Set<String> envSecretValues) {
        bindings.putMember("console", ProxyObject.fromMap(
                Map.of("log", (ProxyExecutable) args -> {
                    if (logList.size() > 1000)
                        return null;
                    String msg = Arrays.stream(args).map(JsToolExecutor::stringifyConsoleArg)
                            .reduce((a, b) -> a + " " + b).orElse("");
                    msg = maskKnownSecrets(msg, envSecretValues);
                    logList.add("[LOG] " + msg);
                    return null;
                })
        ));
    }

    private static String stringifyConsoleArg(Value v) {
        if (v == null || v.isNull()) return "null";
        if (v.isString()) return v.asString();
        return v.toString();
    }

    private String maskKnownSecrets(String input, Set<String> secrets) {
        if (input == null || secrets == null || secrets.isEmpty()) return input;
        String out = input;
        for (String secret : secrets) {
            if (!secret.isEmpty() && out.contains(secret)) {
                out = out.replace(secret, maskValue(secret));
            }
        }
        return out;
    }

    private Map<String, String> snapshotVariables(Value bindings) {
        return bindings.getMemberKeys().stream()
                .filter(key -> !HOST_INJECTED_GLOBALS.contains(key))
                .collect(LinkedHashMap::new,
                        (m, key) -> {
                            Value v = bindings.getMember(key);
                            String s = (v == null) ? "null" : v.toString();
                            m.put(key, s);
                        }, Map::putAll);
    }

    private void mergeStateLogs(List<String> log,
            Map<String, String> init,
            Map<String, String> fin,
            Map<String, String> envBackedVariables) {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(init.keySet());
        all.addAll(fin.keySet());

        for (String key : all) {
            String beforeRaw = init.get(key);
            String afterRaw = fin.get(key);

            String envName = envBackedVariables.get(key);
            String envSuffix = (envName != null) ? " (env " + envName + ")" : "";

            String beforeForLog = beforeRaw;
            String afterForLog = afterRaw;

            if (envName != null) {
                if (beforeForLog != null && !"null".equals(beforeForLog)) {
                    beforeForLog = maskValue(beforeForLog);
                }
                if (afterForLog != null && !"null".equals(afterForLog)) {
                    afterForLog = maskValue(afterForLog);
                }
            }

            if (beforeRaw == null && afterRaw != null) {
                log.add(key + " = " + afterForLog + envSuffix + " (new)");
            } else if (beforeRaw != null && afterRaw == null) {
                log.add(key + " = [deleted]" + envSuffix);
            } else if (!Objects.equals(beforeRaw, afterRaw)) {
                log.add(key + " = " + afterForLog + envSuffix + " (changed from: " + beforeForLog + ")");
            } else {
                log.add(key + " = " + afterForLog + envSuffix);
            }
        }
    }

    private String maskValue(String value) {
        if (value == null) {
            return null;
        }
        int n = value.length();
        if (n <= 4) {
            return "*".repeat(n);
        }
        int keep = Math.max(1, n / 6);
        if (keep * 2 >= n) {
            keep = 1;
        }
        int startKeep = keep;
        int endKeep = keep;
        int middleLen = n - startKeep - endKeep;
        return value.substring(0, startKeep) + "*".repeat(middleLen) + value.substring(n - endKeep, n);
    }

}
