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

import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionParams;
import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JsToolExecutorTest {

    private JsToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JsToolExecutor();
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
                 * This code runs on JavaScript (ECMAScript 2023) inside the JVM.
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
    void testEnvPlaceholderandMaskOnlyInLog() {
        String PROP_NAME = "TEST_SECRET";
        String PROP_VALUE = "super-secret-value-123456";
        System.setProperty(PROP_NAME, PROP_VALUE);

        try {
            JsToolExecutor executor = new JsToolExecutor(5L);

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
            assertEquals(PROP_VALUE, result.result());

            String debug = result.debugInfo();
            assertNotNull(debug);
            assertFalse(debug.contains(PROP_VALUE));
            assertTrue(debug.contains("(env " + PROP_NAME + ")"));
            assertEquals("=== Execution Log ===\n" +
                    "\n" +
                    "=== Final State ===\n" +
                    "apiKey = supe*****************3456 (env TEST_SECRET)", debug);
        } finally {
            System.clearProperty(PROP_NAME);
        }
    }

    @Test
    void nonEnvPattern_shouldBeUsedAsLiteralValue() {
        JsToolExecutor executor = new JsToolExecutor(5L);

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


    @Disabled
    @Test
    void testGooglePseViaJavaInteropOnly() {

        String jsCode = """
                    const URI = Java.type('java.net.URI');
                    const HttpClient = Java.type('java.net.http.HttpClient');
                    const HttpRequest = Java.type('java.net.http.HttpRequest');
                    const HttpResponse = Java.type('java.net.http.HttpResponse');
                    const StandardCharsets = Java.type('java.nio.charset.StandardCharsets');
                
                    const url = "https://www.googleapis.com/customsearch/v1"
                        + "?key=" + apiKey
                        + "&cx=" + cx
                        + "&q=" + encodeURIComponent(query);
                
                    const client = HttpClient.newHttpClient();
                    const request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                    const response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    const body = response.body();
                
                    const json = JSON.parse(body);
                    if (!json.items) return "no results";
                    return json;
                """;

        JsExecutionParams params = new JsExecutionParams(Map.of(
                "apiKey", "${GOOGLE_API_KEY}",
                "cx", "${GOOGLE_CX}",
                "query", "Spring AI"), jsCode);

        JsExecutionResult result = executor.execute(params);

        System.out.println("isOk = " + result.isOk());
        System.out.println("result = " + result.result());
        System.out.println("error = " + result.error());

        assertTrue(result.isOk());
        assertNotNull(result.result());
    }

}
