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
package jm.kr.spring.ai.playground.service.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jm.kr.spring.ai.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "tool-studio.sandbox")
public class JsToolExecutor {

    public record JsExecutionResult(boolean isOk, Object result, String error, @JsonIgnore String debugInfo) {}

    public record JsExecutionParams(Map<String, Object> params, String code) {}

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("^\\$\\{([A-Z_]+[A-Z0-9_]*)}$");
    private static final Pattern BLACKLIST_PATTERN =
            Pattern.compile("^(java\\.lang\\.(System|Runtime|ProcessBuilder|Process)|java\\.lang\\.invoke\\..*)$");

    private final Context.Builder contextBuilder;
    private final long timeoutSeconds;
    private final ExecutorService executor;

    public JsToolExecutor(Long timeoutSeconds, JsSandbox jsSandbox) {
        this.contextBuilder = Context.newBuilder("js")
                .option("js.ecmascript-version", "2023")
                .allowAllAccess(false);
        if (Objects.nonNull(jsSandbox)) {
            HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).build();
            IOAccess ioConfig = IOAccess.newBuilder()
                    .allowHostFileAccess(jsSandbox.allowFileIo())
                    .allowHostSocketAccess(jsSandbox.allowNetworkIo())
                    .build();
            this.contextBuilder
                    .allowHostClassLookup(className -> {
                        if (BLACKLIST_PATTERN.matcher(className).matches()) {
                            return false;
                        }
                        return jsSandbox.allowClasses().stream().anyMatch(pattern -> {
                            if (pattern.endsWith(".*")) {
                                String prefix = pattern.substring(0, pattern.length() - 2);
                                return className.startsWith(prefix + ".") || className.startsWith(prefix);
                            }
                            return className.equals(pattern);
                        });
                    })
                    .allowHostAccess(hostAccess)
                    .allowIO(ioConfig)
                    .allowNativeAccess(jsSandbox.allowNativeAccess())
                    .allowCreateThread(jsSandbox.allowCreateThread())
                    .resourceLimits(
                            ResourceLimits.newBuilder().statementLimit(jsSandbox.maxStatements(), null).build());
        }
        this.timeoutSeconds = Optional.ofNullable(timeoutSeconds).orElse(30L);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public JsExecutionResult execute(JsExecutionParams jsExecutionParams) {
        String jsCode = """
                (async function() {
                    %s
                })();
                """.formatted(jsExecutionParams.code());

        List<String> logList = new ArrayList<>();
        Future<JsExecutionResult> future = executor.submit(() -> {
            try (Context context = contextBuilder.build()) {
                Value bindings = context.getBindings("js");

                Map<String, String> envBackedVariables = new HashMap<>();
                if (jsExecutionParams.params() != null) {
                    jsExecutionParams.params().forEach((name, rawValue) -> bindings.putMember(name,
                            resolveParamValue(rawValue, name, envBackedVariables)));
                }

                Map<String, String> initialState = snapshotVariables(bindings);

                logList.add("=== Execution Log ===");
                installConsoleLog(bindings, logList);

                Value jsResultValue = awaitPromise(context.eval("js", jsCode));
                Object jsResult = jsResultValue.isNull() ? "undefined" :
                        deepCopyPolyglot(jsResultValue.as(Object.class));

                Map<String, String> finalState = snapshotVariables(bindings);

                logList.add("\n=== Final State ===");
                mergeStateLogs(logList, initialState, finalState, envBackedVariables);

                return new JsExecutionResult(true, jsResult, null, buildDebugInfo(logList));
            } catch (Exception e) {
                return new JsExecutionResult(false, "", e.getMessage(), buildDebugInfo(logList));
            }
        });

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new JsExecutionResult(false, "", "Execution timed out after " + timeoutSeconds + " seconds",
                    buildDebugInfo(logList));
        } catch (Exception e) {
            return new JsExecutionResult(false, "", e.getMessage(), buildDebugInfo(logList));
        }
    }

    private Object resolveParamValue(Object rawValue, String paramName,
            Map<String, String> envBackedVariables) {
        if (!(rawValue instanceof String str)) {
            return rawValue;
        }

        Matcher matcher = ENV_VAR_PATTERN.matcher(str);
        if (!matcher.matches()) {
            return rawValue;
        }

        String envName = matcher.group(1);

        String resolved = System.getenv(envName);
        if (resolved == null) {
            resolved = System.getProperty(envName);
        }

        if (resolved == null) {
            return rawValue;
        }

        envBackedVariables.put(paramName, envName);
        return resolved;
    }

    private Object deepCopyPolyglot(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Map map -> {
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

    private Value awaitPromise(Value promise) throws Exception {
        CompletableFuture<Value> completableFuture = new CompletableFuture<>();
        promise.invokeMember("then", (ProxyExecutable) args -> {completableFuture.complete(args[0]); return null;});
        promise.invokeMember("catch", (ProxyExecutable) args -> {
            completableFuture.completeExceptionally(new RuntimeException(args[0].toString())); return null;
        });
        return completableFuture.get();
    }

    private void installConsoleLog(Value bindings, List<String> logList) {
        bindings.putMember("console", ProxyObject.fromMap(
                Map.of("log", (ProxyExecutable) args -> {
                    if (logList.size() > 1000)
                        return null;
                    String msg = Arrays.stream(args).map(v -> v == null ? "null" : v.toString())
                            .reduce((a, b) -> a + " " + b).orElse("");
                    logList.add("[LOG] " + msg);
                    return null;
                })
        ));
    }

    private Map<String, String> snapshotVariables(Value bindings) {
        return bindings.getMemberKeys().stream().filter(key -> !key.equals("console")).collect(LinkedHashMap::new,
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
                    beforeForLog = maskValueForLog(beforeForLog);
                }
                if (afterForLog != null && !"null".equals(afterForLog)) {
                    afterForLog = maskValueForLog(afterForLog);
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

    private String maskValueForLog(String value) {
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
        StringBuilder sb = new StringBuilder();
        sb.append(value, 0, startKeep);
        sb.append("*".repeat(middleLen));
        sb.append(value, n - endKeep, n);
        return sb.toString();
    }

}
