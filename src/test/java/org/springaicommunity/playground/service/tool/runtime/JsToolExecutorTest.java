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

import com.sun.net.httpserver.HttpServer;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import static org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsToolExecutorTest {

    private static final Set<String> STANDARD_DENY =
            Set.of("java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process",
                    "java.lang.Class", "java.lang.invoke.*", "java.lang.reflect.*");
    private JsToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JsToolExecutor(null, new JsSandbox(true, false, false, false, 50_000L,
                STANDARD_DENY,
                Set.of("java.lang.*", "java.math.*", "java.time.*", "java.util.*", "java.text.*", "java.net.*",
                        "java.io.*", "org.jsoup.*"),
                Map.of()), Path.of(System.getProperty("java.io.tmpdir")));
    }

    @Test
    void testSyncJs() {
        String code = "console.log('log'); return 1 + 2 + 3;";
        JsExecutionParams params = new JsExecutionParams(Map.of(), code);
        JsExecutionResult result = executor.execute(params);

        assertTrue(result.isOk());
        assertEquals(6, result.result());
        assertEquals("=== Execution Log ===\n" +
                "[LOG] log\n" +
                "\n" +
                "=== Final State ===", result.debugInfo());
        assertNull(result.error());
    }

    @Test
    void testAsyncJs() {
        String code = """
                    const data = await Promise.resolve({value: 42});
                    return data.value;
                """;
        JsExecutionParams params = new JsExecutionParams(Map.of(), code);
        JsExecutionResult result = executor.execute(params);

        assertTrue(result.isOk());
        assertEquals(42, result.result());
        assertNull(result.error());
    }

    @Test
    void testPureJsComputation() {
        String jsCode = """
                    let sum = 0;
                    for (let i = 1; i <= 100; i++) sum += i;
                    return sum;
                """;
        var result = executor.execute(new JsExecutionParams(Map.of(), jsCode));
        assertTrue(result.isOk());
        assertEquals(5050, result.result());
    }


    @Test
    void testNoReturn() {
        String code = """
                    const list = [];
                    for (let i = 0; i < 3; i++) {
                        list.push("Item " + i);
                    }
                    const message = "Executed " + list.length + " items.";
                    // no return
                """;
        JsExecutionParams params = new JsExecutionParams(Map.of(), code);
        JsExecutionResult result = executor.execute(params);

        assertTrue(result.isOk());
        assertEquals("undefined", result.result());
        assertNull(result.error());
    }

    @Test
    void testSyntaxError() {
        String code = "return (1 + );"; // 문법 오류
        JsExecutionParams params = new JsExecutionParams(Map.of(), code);
        JsExecutionResult result = executor.execute(params);

        assertFalse(result.isOk());
        assertEquals("", result.result());
        assertNotNull(result.error());
        assertTrue(result.error().contains("SyntaxError"));
    }

    @Test
    void testWeatherJsToolFunction() {

        String jsCode = """
                /**
                 * NOTE TO DEVELOPERS:
                 * This code runs on JavaScript (ECMAScript 2024) inside the JVM.
                 * It is NOT a browser or Node.js environment.
                 *
                 * Unavailable APIs:
                 * - Browser APIs: fetch, XMLHttpRequest, DOM (window/document), timers, etc.
                 * - Node.js APIs: require(), module, process, built-in modules, etc.
                 *
                 * Available features:
                 * - Java interop via Java.type() (e.g., java.net.*, java.io.*, etc.)
                 * - console.log (output captured by the host)
                 *
                 * Execution model:
                 * - Your script is wrapped in an async function.
                 * - The value you return becomes the final tool result.
                 */
                
                // Use Java standard HTTP classes from JavaScript
                var URL = Java.type('java.net.URL');
                var BufferedReader = Java.type('java.io.BufferedReader');
                var InputStreamReader = Java.type('java.io.InputStreamReader');
                
                // 1) Call wttr.in with JSON format (j1)
                var url = new URL('https://wttr.in/' + location + '?format=j1');
                var conn = url.openConnection();
                conn.setRequestMethod('GET');
                conn.setConnectTimeout(20000);
                var reader;
                
                try {
                    conn.setReadTimeout(20000);
                
                    // 2) Read the full JSON response as a string
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), 'UTF-8'));
                    var line;
                    var sb = '';
                    while ((line = reader.readLine()) !== null) {
                        sb += line;
                    }
                } finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
                
                // 3) Parse JSON string into a JavaScript object
                var data = JSON.parse(sb);
                
                // 4) Extract only the fields we want:
                //    - location name
                //    - temperature (Celsius)
                //    - humidity
                //    - wind speed
                //    - wind direction
                var areaName = data.nearest_area
                                && data.nearest_area[0]
                                && data.nearest_area[0].areaName
                                && data.nearest_area[0].areaName[0]
                                && data.nearest_area[0].areaName[0].value;
                
                var current = data.current_condition && data.current_condition[0];
                
                var tempC    = current && current.temp_C;
                var humidity = current && current.humidity;
                var windKmph = current && current.windspeedKmph;
                var windDir  = current && current.winddir16Point; // e.g. N, NE, E, SE, ...
                
                // Build strings for wind speed & direction
                var windSpeedText = windKmph != null ? (windKmph + ' km/h') : null;
                var windDirText   = windDir != null ? windDir : null;
                
                // 5) Build a small summary object
                var summary = {
                    location: areaName || location,
                    tempC: tempC || null,
                    humidity: humidity || null,
                    windSpeed: windSpeedText,
                    windDirection: windDirText
                };
                
                // 6) Return a compact JSON string to Java
                return JSON.stringify(summary);
                """;

        JsExecutionParams params = new JsExecutionParams(Map.of("location", "seoul"), jsCode);

        JsExecutionResult result = executor.execute(params);

        System.out.println("isOk = " + result.isOk());
        System.out.println("result = " + result.result());
        System.out.println("error = " + result.error());

        assertTrue(result.isOk());
        assertNotNull(result.result());
    }

    @Test
    void testEnvPlaceholder_maskedEverywhere() throws ClassNotFoundException {
        String PROP_NAME = "TEST_SECRET";
        String PROP_VALUE = "super-secret-value-123456";
        String MASKED_VALUE = "supe*****************3456";
        System.setProperty(PROP_NAME, PROP_VALUE);

        try {
            JsToolExecutor executor = new JsToolExecutor(5L, null, Path.of(System.getProperty("java.io.tmpdir")));

            String jsCode = """
                    return apiKey;
                    """;

            Map<String, Object> params = Map.of(
                    "apiKey", "${" + PROP_NAME + "}"
            );

            JsToolExecutor.JsExecutionParams execParams =
                    new JsToolExecutor.JsExecutionParams(params, jsCode);

            JsToolExecutor.JsExecutionResult result = executor.execute(execParams);

            assertTrue(result.isOk());
            // Return value is now masked in the same form as the state log entry.
            assertEquals(MASKED_VALUE, result.result());

            String debug = result.debugInfo();
            assertNotNull(debug);
            assertFalse(debug.contains(PROP_VALUE));
            assertTrue(debug.contains("(env " + PROP_NAME + ")"));
            assertEquals("=== Execution Log ===\n" +
                    "\n" +
                    "=== Final State ===\n" +
                    "apiKey = " + MASKED_VALUE + " (env TEST_SECRET)", debug);
        } finally {
            System.clearProperty(PROP_NAME);
        }
    }

    @Test
    void nonEnvPattern_shouldBeUsedAsLiteralValue() throws ClassNotFoundException {
        JsToolExecutor executor = new JsToolExecutor(5L, null, Path.of(System.getProperty("java.io.tmpdir")));

        String literal = "${not_uppercase}";
        String jsCode = "return value;";

        Map<String, Object> params = Map.of(
                "value", literal
        );

        JsToolExecutor.JsExecutionParams execParams =
                new JsToolExecutor.JsExecutionParams(params, jsCode);

        JsToolExecutor.JsExecutionResult result = executor.execute(execParams);

        assertTrue(result.isOk());
        assertEquals(literal, result.result());

        String debug = result.debugInfo();
        assertNotNull(debug);
        assertFalse(debug.contains("(env "));
    }


    @Test
    void isClassAllowed_blocksReflectPackage() {
        var sandbox = new JsSandbox(true, false, false, false, 50_000L, STANDARD_DENY, Set.of("java.lang.*"), Map.of());

        assertFalse(JsToolExecutor.isClassAllowed("java.lang.reflect.Method", sandbox));
        assertFalse(JsToolExecutor.isClassAllowed("java.lang.reflect.Field", sandbox));
        assertFalse(JsToolExecutor.isClassAllowed("java.lang.reflect.Constructor", sandbox));
    }

    @Test
    void isClassAllowed_blocksJavaLangClass_toPreventClassForNameBypass() {
        var sandbox = new JsSandbox(true, false, false, false, 50_000L, STANDARD_DENY, Set.of("java.lang.*"), Map.of());

        assertFalse(JsToolExecutor.isClassAllowed("java.lang.Class", sandbox));
        // Sibling classes still allowed
        assertTrue(JsToolExecutor.isClassAllowed("java.lang.String", sandbox));
        assertTrue(JsToolExecutor.isClassAllowed("java.lang.StringBuilder", sandbox));
    }

    @Test
    void isClassAllowed_wildcardRequiresDotSeparator_doesNotMatchPrefixOnly() {
        // Empty deny set — this test focuses on allow-classes wildcard semantics, not deny.
        var sandbox = new JsSandbox(true, false, false, false, 50_000L, Set.of(), Set.of("com.foo.*"), Map.of());

        // True positives — class is in the allowed package
        assertTrue(JsToolExecutor.isClassAllowed("com.foo.Bar", sandbox));
        assertTrue(JsToolExecutor.isClassAllowed("com.foo.subpkg.Baz", sandbox));

        // False positives the old code would have matched (className.startsWith(prefix) without the dot):
        // "com.foobar.Bad" or "com.fooSomething.X" must NOT match "com.foo.*".
        assertFalse(JsToolExecutor.isClassAllowed("com.foobar.Bad", sandbox));
        assertFalse(JsToolExecutor.isClassAllowed("com.fooSomething.X", sandbox));
        assertFalse(JsToolExecutor.isClassAllowed("com.foo", sandbox));
    }

    @Test
    void consoleLog_masksEnvBackedSecrets() {
        String PROP_NAME = "PHASE1_CONSOLE_SECRET";
        String PROP_VALUE = "console-secret-token-987654";
        System.setProperty(PROP_NAME, PROP_VALUE);

        try {
            JsToolExecutor executor = new JsToolExecutor(5L, null, Path.of(System.getProperty("java.io.tmpdir")));

            String jsCode = "console.log('value is', apiKey); return 'ok';";
            Map<String, Object> params = Map.of("apiKey", "${" + PROP_NAME + "}");

            JsExecutionResult result = executor.execute(new JsExecutionParams(params, jsCode));

            assertTrue(result.isOk());
            String debug = result.debugInfo();
            assertNotNull(debug);
            // [LOG] line must NOT contain the plain secret
            assertFalse(debug.contains(PROP_VALUE));
            // It should still contain a masked form
            assertTrue(debug.contains("[LOG] value is "));
            assertTrue(debug.contains("*"));
        } finally {
            System.clearProperty(PROP_NAME);
        }
    }

    @Test
    void denyClasses_fromExplicitYamlConfig_isHonored() {
        // Simulate a yaml-supplied deny-classes set (custom — does not include reflect.*)
        Set<String> customDeny = Set.of("java.lang.System", "com.evil.*");
        var sandbox = new JsSandbox(true, false, false, false, 50_000L,
                customDeny, Set.of("java.lang.*", "com.evil.*", "com.good.*"), Map.of()
        );

        // Explicit deny-classes entry is honored
        assertFalse(JsToolExecutor.isClassAllowed("java.lang.System", sandbox));
        assertFalse(JsToolExecutor.isClassAllowed("com.evil.Bad", sandbox));
        // A class NOT in the custom deny set (reflect.*) is allowed — user explicitly omitted it.
        // In production this lowers the security posture; Phase 2 SecurityPostureCalculator will flag this.
        assertTrue(JsToolExecutor.isClassAllowed("java.lang.reflect.Method", sandbox));
        // Allowed class still allowed
        assertTrue(JsToolExecutor.isClassAllowed("com.good.Util", sandbox));
    }

    @Test
    void envBackedSecret_alwaysMaskedInResult() {
        String PROP_NAME = "PHASE1_TEST_SECRET";
        String PROP_VALUE = "super-secret-token-789012";
        System.setProperty(PROP_NAME, PROP_VALUE);

        try {
            // No flag — masking is always-on (symmetric with state log + console.log).
            JsToolExecutor executor = new JsToolExecutor(5L, null, Path.of(System.getProperty("java.io.tmpdir")));
            String jsCode = "return apiKey;";
            Map<String, Object> params = Map.of("apiKey", "${" + PROP_NAME + "}");

            JsExecutionResult result = executor.execute(new JsExecutionParams(params, jsCode));

            assertTrue(result.isOk());
            assertNotNull(result.result());
            String resultStr = result.result().toString();
            assertFalse(resultStr.equals(PROP_VALUE));
            assertFalse(resultStr.contains(PROP_VALUE));
            assertTrue(resultStr.contains("*"));
        } finally {
            System.clearProperty(PROP_NAME);
        }
    }

    @Test
    void testFetchUndefinedWhenNoNetworkIo() {
        JsToolExecutor exec = new JsToolExecutor(30L, new JsSandbox(false, false, false, false, 50_000L,
                STANDARD_DENY, Set.of(), Map.of()), Path.of(System.getProperty("java.io.tmpdir")));
        String code = "return typeof fetch;";
        JsExecutionResult result = exec.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk());
        assertEquals("undefined", result.result());
    }

    @Test
    void testFileModeOffHidesFsNamespace() {
        EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), false, false, false, false, false,
                500_000L, 30L, true, null, null, null);
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return typeof safety.fs;"), policy);
        assertTrue(result.isOk());
        assertEquals("undefined", result.result());
    }

    @Test
    void testFileModeReadOnlyOmitsWriteText() {
        EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), false, true, false, false, false,
                500_000L, 30L, true, null, null, null);
        String code = "return typeof safety.fs.readText + ':' + typeof safety.fs.writeText;";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
        assertTrue(result.isOk());
        assertEquals("function:undefined", result.result());
    }

    @Test
    void testFileModeReadWriteInjectsAll() {
        EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), false, true, true, false, false,
                500_000L, 30L, true, null, null, null);
        String code = "return typeof safety.fs.readText + ':' + typeof safety.fs.writeText "
                + "+ ':' + typeof safety.fs.grep + ':' + typeof safety.fs.cut;";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
        assertTrue(result.isOk());
        assertEquals("function:function:function:function", result.result());
    }

    @Test
    void testParserAvailableEvenWhenFsOff() {
        EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), false, false, false, false, false,
                500_000L, 30L, true, null, null, null);
        String code = "return typeof safety.parser.html + ':' + typeof safety.parser.yaml;";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
        assertTrue(result.isOk());
        assertEquals("function:function", result.result());
    }

    @Test
    void testFetchUndefinedWhenNetworkBlockedByPolicy() {
        EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), true, false, false, false, false,
                500_000L, 30L, true, new NetworkPolicy("blocked", Set.of(), Set.of()), null, null);
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return typeof fetch;"), policy);
        assertTrue(result.isOk());
        assertEquals("undefined", result.result());
    }

    @Test
    void testFetchStrictBlocksLiteralIp() {
        String code = """
                try {
                    await fetch('http://127.0.0.1/');
                    return 'no-throw';
                } catch (e) {
                    return String(e.message || e);
                }
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk());
        String message = (String) result.result();
        assertTrue(message.contains("denied"), "expected denied marker in: " + message);
    }

    @Test
    void testFetchReturnsResponseShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/data", exchange -> {
            byte[] body = "{\"name\":\"alice\",\"age\":30}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), true, false, false, false, false,
                    500_000L, 30L, true, new NetworkPolicy("permissive", Set.of(), Set.of()), null, null);
            String code = ("""
                    const r = await fetch('%s/data');
                    const data = await r.json();
                    return { status: r.status, ok: r.ok, name: data.name, age: data.age,
                             contentType: r.contentType, header: r.headers.get('Content-Type') };
                    """).formatted(baseUrl);
            JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
            assertTrue(result.isOk(), result.error());
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) result.result();
            assertEquals(200, ((Number) m.get("status")).intValue());
            assertEquals(true, m.get("ok"));
            assertEquals("alice", m.get("name"));
            assertEquals(30, ((Number) m.get("age")).intValue());
            assertEquals("application/json", m.get("contentType"));
            assertEquals("application/json", m.get("header"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testFetchPaginationTruncates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/big", exchange -> {
            byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            EffectivePolicy policy = new EffectivePolicy(Set.of(), Set.of(), true, false, false, false, false,
                    500_000L, 30L, true, new NetworkPolicy("permissive", Set.of(), Set.of()), null, null);
            String code = ("""
                    const r = await fetch('%s/big', { maxLength: 4 });
                    return { text: await r.text(), truncated: r.truncated, next: r.nextStartIndex };
                    """).formatted(baseUrl);
            JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
            assertTrue(result.isOk(), result.error());
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) result.result();
            assertEquals("0123", m.get("text"));
            assertEquals(true, m.get("truncated"));
            assertEquals(4, ((Number) m.get("next")).intValue());
        } finally {
            server.stop(0);
        }
    }

}
