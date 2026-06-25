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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsToolExecutorHelpersTest {

    private static final Set<String> STANDARD_DENY = Set.of(
            "java.lang.System", "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.Process",
            "java.lang.Class", "java.lang.invoke.*", "java.lang.reflect.*");

    private static final Set<String> STANDARD_ALLOW = Set.of(
            "java.lang.*", "java.math.*", "java.time.*", "java.util.*", "java.text.*");

    private JsToolExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JsToolExecutor(10L,
                new JsSandbox(true, false, false, false, 500_000L, STANDARD_DENY, STANDARD_ALLOW, Map.of()),
                Path.of(System.getProperty("java.io.tmpdir")));
    }


    @Test
    void atobDecodesBase64ViaExecutor() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return atob('aGVsbG8=');"));
        assertTrue(result.isOk(), result.error());
        assertEquals("hello", result.result());
    }

    @Test
    void btoaEncodesBase64ViaExecutor() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return btoa('hello');"));
        assertTrue(result.isOk(), result.error());
        assertEquals("aGVsbG8=", result.result());
    }

    @Test
    void atobBtoaRoundTripUtf8() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return atob(btoa('Spring AI Playground'));"));
        assertTrue(result.isOk(), result.error());
        assertEquals("Spring AI Playground", result.result());
    }


    @Test
    void cryptoRandomUuidMatchesV4FormatViaExecutor() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return crypto.randomUUID();"));
        assertTrue(result.isOk(), result.error());
        String uuid = (String) result.result();
        assertNotNull(uuid);
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "Expected UUID format, got: " + uuid);
    }

    @Test
    void cryptoSubtleDigestSha256AsyncViaExecutor() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        String code = """
                const bytes = new TextEncoder().encode('abc');
                const hashBuf = await crypto.subtle.digest('SHA-256', bytes);
                return Array.from(new Uint8Array(hashBuf))
                    .map(b => b.toString(16).padStart(2,'0'))
                    .join('');
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.result());
    }

    @Test
    void cryptoGetRandomValuesFillsUint8ArrayViaExecutor() {
        String code = """
                const a = new Uint8Array(16);
                crypto.getRandomValues(a);
                return a.length;
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals(16, ((Number) result.result()).intValue());
    }


    @Test
    void urlAndSearchParamsParseViaExecutor() {
        String code = """
                const u = new URL('https://example.com/p?a=1&b=2#frag');
                return u.searchParams.get('a') + ':' + u.host + ':' + u.pathname + ':' + u.hash;
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals("1:example.com:/p:#frag", result.result());
    }


    @Test
    void safetyParserCsvViaExecutor() {
        String code = """
                const rows = safety.parser.csv('a,b\\n1,2\\n3,4');
                return rows.length + ':' + rows[1][0] + ':' + rows[1][1];
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals("3:1:2", result.result());
    }

    @Test
    void safetyParserXmlViaExecutor() {
        String code = """
                const doc = safety.parser.xml('<root><child id="1">hi</child></root>');
                return doc.tag + ':' + doc.children[0].tag + ':'
                        + doc.children[0].attrs.id + ':' + doc.children[0].text;
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals("root:child:1:hi", result.result());
    }

    @Test
    void safetyParserHtmlSelectorViaExecutor() {
        String code = """
                const doc = safety.parser.html('<html><body><p class="a">one</p><p class="a">two</p></body></html>');
                return doc.select('p.a').size() + ':' + doc.body().text();
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertTrue(result.isOk(), result.error());
        assertEquals("2:one two", result.result());
    }


    @Test
    void safetyFsReadTextViaExecutorWithFileReadPolicy(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("note.txt"), "hello fs");
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), "return safety.fs.readText('note.txt');"),
                fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("hello fs", result.result());
    }

    @Test
    void safetyFsThrowsWhenFsPolicyOff() {
        String code = """
                try {
                    safety.fs.readText('x.txt');
                    return 'no-throw';
                } catch (e) {
                    return 'threw:' + (typeof safety.fs);
                }
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), fsPolicy(false, false));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw:undefined", result.result());
    }


    private static EffectivePolicy withDenyAllow(boolean javaInterop) {
        return new EffectivePolicy(
                STANDARD_ALLOW, STANDARD_DENY,
                false, false, false, false, false,
                500_000L, 30L,
                javaInterop, null, null, null);
    }

    private static String javaTypeTryCatch(String className) {
        return ("""
                try {
                    Java.type('%s');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """).formatted(className);
    }

    @Test
    void javaTypeBlockedWhenJavaInteropOff() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.String")),
                withDenyAllow(false));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void javaTypeSystemBlockedByDeny() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.System")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void javaTypeRuntimeBlockedByDeny() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.Runtime")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void javaTypeProcessBuilderBlockedByDeny() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.ProcessBuilder")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void javaTypeReflectMethodBlockedByDenyPattern() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.reflect.Method")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void javaTypeClassBlockedToPreventClassForNameBypass() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("java.lang.Class")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void classOutsideAllowlistIsBlocked() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), javaTypeTryCatch("javax.imageio.ImageIO")),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void siblingClassInsideAllowlistStillWorks() {
        String code = "const S = Java.type('java.lang.String'); return S.valueOf(42);";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code),
                withDenyAllow(true));
        assertTrue(result.isOk(), result.error());
        assertEquals("42", result.result());
    }


    private static EffectivePolicy fetchPolicy(NetworkPolicy network) {
        return new EffectivePolicy(
                Set.of(), Set.of(),
                true, false, false, false, false,
                500_000L, 30L,
                true, network, null, null);
    }

    private static String fetchTryCatch(String url) {
        return ("""
                try {
                    await fetch('%s');
                    return 'no-throw';
                } catch (e) {
                    return String(e.message || e);
                }
                """).formatted(url);
    }

    @Test
    void fetchAllowlistAcceptsListedHost() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            EffectivePolicy policy = fetchPolicy(
                    new NetworkPolicy("allowlist", Set.of("127.0.0.1"), Set.of()));
            String code = ("""
                    const r = await fetch('http://127.0.0.1:%d/ok');
                    return r.status;
                    """).formatted(port);
            JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
            assertTrue(result.isOk(), result.error());
            assertEquals(200, ((Number) result.result()).intValue());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchAllowlistRejectsUnlistedHost() {
        EffectivePolicy policy = fetchPolicy(
                new NetworkPolicy("allowlist", Set.of("api.allowed.example"), Set.of()));
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://127.0.0.1/")), policy);
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.contains("not in allowlist"), "Expected allowlist-deny marker; got: " + message);
    }

    @Test
    void fetchAllowlistEmptyDeniesAll() {
        EffectivePolicy policy = fetchPolicy(
                new NetworkPolicy("allowlist", Set.of(), Set.of()));
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://example.com/")), policy);
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.contains("not in allowlist"), "Expected allowlist-deny marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksRfc1918TenSlash8Literal() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://10.0.0.1/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied") && message.contains("10.0.0.1"),
                "Expected private-IP-denied marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksRfc1918OneNineTwoSlash16Literal() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://192.168.1.1/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied"),
                "Expected denied marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksRfc1918OneSevenTwoSlash12Literal() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://172.20.0.1/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied"),
                "Expected denied marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksLinkLocalMetadataIp() {
        // 169.254.169.254 is the AWS/GCE instance metadata service IP — a common SSRF target.
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://169.254.169.254/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied"),
                "Expected metadata-IP-denied marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksCgnatRange() {
        // 100.64.0.0/10 is not caught by Java's isSiteLocalAddress; SafeHttpFetch adds an explicit check.
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://100.64.0.1/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied"),
                "Expected CGNAT-denied marker; got: " + message);
    }

    @Test
    void fetchStrictBlocksIpv6LoopbackLiteral() {
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), fetchTryCatch("http://[::1]/")));
        assertTrue(result.isOk(), result.error());
        String message = (String) result.result();
        assertTrue(message.toLowerCase().contains("denied"),
                "Expected IPv6-loopback-denied marker; got: " + message);
    }


    private static EffectivePolicy fsPolicy(boolean read, boolean write) {
        return new EffectivePolicy(
                Set.of(), Set.of(),
                false, read, write, false, false,
                500_000L, 30L, true, null, null, null);
    }

    @Test
    void safetyFsReadRejectsAbsolutePathOutsideBase(@TempDir Path tempDir) {
        String code = """
                try {
                    safety.fs.readText('/etc/passwd');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void safetyFsRejectsDeepTraversal(@TempDir Path tempDir) throws IOException {
        Files.createDirectories(tempDir.resolve("inner"));
        String code = """
                try {
                    safety.fs.readText('inner/../../../etc/passwd');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void safetyFsWriteRejectsBasePathEscape(@TempDir Path tempDir) {
        String code = """
                try {
                    safety.fs.writeText('../escape.txt', 'data');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, true), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void safetyFsListRejectsOutsideDir(@TempDir Path tempDir) {
        String code = """
                try {
                    safety.fs.list('../');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void safetyFsFindRejectsOutsideDir(@TempDir Path tempDir) {
        String code = """
                try {
                    safety.fs.find('../', '*.txt');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }

    @Test
    void safetyFsWriteAndReadWithinBasePathWorks(@TempDir Path tempDir) {
        String code = """
                safety.fs.writeText('data.txt', 'hi');
                return safety.fs.readText('data.txt');
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, true), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("hi", result.result());
    }


    @Test
    void safetyFsWriteUnavailableWhenWriteOff(@TempDir Path tempDir) {
        String code = """
                try {
                    safety.fs.writeText('x.txt', 'data');
                    return 'no-throw';
                } catch (e) {
                    return 'threw:' + (typeof safety.fs.writeText);
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw:undefined", result.result());
    }

    @Test
    void safetyFsCannotReadOutsideBasePath(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("inside.txt"), "ok");
        String code = """
                try {
                    safety.fs.readText('../../etc/passwd');
                    return 'no-throw';
                } catch (e) {
                    return 'threw';
                }
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertTrue(result.isOk(), result.error());
        assertEquals("threw", result.result());
    }


    private static EffectivePolicy limitsPolicy(long maxStatements, long timeoutSeconds) {
        return new EffectivePolicy(
                Set.of(), Set.of(),
                false, false, false, false, false,
                maxStatements, timeoutSeconds,
                true, null, null, null);
    }

    @Test
    void maxStatementsTerminatesInfiniteLoop() {
        String code = "while (true) {}; return 'never';";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), limitsPolicy(1000L, 30L));
        assertFalse(result.isOk());
        assertNotNull(result.error());
    }

    @Test
    void timeoutTerminatesLongRunningJs() {
        String code = """
                const start = Date.now();
                while (Date.now() - start < 5000) {}
                return 'done';
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), limitsPolicy(Long.MAX_VALUE, 1L));
        assertFalse(result.isOk());
        assertNotNull(result.error());
        assertTrue(result.error().toLowerCase().contains("timed out"),
                "Expected timeout marker in: " + result.error());
    }

    @Test
    void fetchBlockedSurfacesDenyReasonInError() {
        String code = "await fetch('http://10.0.0.1/'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertNotNull(result.error());
        assertTrue(result.error().toLowerCase().contains("denied"),
                "Expected user-visible 'denied' reason; got: " + result.error());
        assertTrue(result.error().contains("10.0.0.1"),
                "Expected blocked host in error; got: " + result.error());
    }

    @Test
    void allowlistRejectSurfacesReasonInError() {
        EffectivePolicy policy = fetchPolicy(
                new NetworkPolicy("allowlist", Set.of("api.allowed.example"), Set.of()));
        String code = "await fetch('http://evil.example/'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
        assertFalse(result.isOk());
        assertNotNull(result.error());
        assertTrue(result.error().contains("not in allowlist"),
                "Expected allowlist reason; got: " + result.error());
        assertTrue(result.error().contains("evil.example"),
                "Expected host name in error; got: " + result.error());
    }

    @Test
    void safetyFsEscapeSurfacesPathReasonInError(@TempDir Path tempDir) {
        String code = "safety.fs.readText('../../etc/passwd'); return 'ok';";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertFalse(result.isOk());
        assertNotNull(result.error());
        assertTrue(result.error().contains("escapes") || result.error().contains("path"),
                "Expected path-related reason; got: " + result.error());
    }

    @Test
    void javaTypeBlockedSurfacesReasonInError() {
        String code = "Java.type('java.lang.Runtime'); return 'ok';";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), withDenyAllow(true));
        assertFalse(result.isOk());
        assertNotNull(result.error());
        assertTrue(result.error().contains("Runtime") || result.error().toLowerCase().contains("class"),
                "Expected class-lookup reason; got: " + result.error());
    }

    @Test
    void consoleLogBeforeBlockIsVisibleInDebugInfo() {
        String code = """
                console.log('before-block');
                await fetch('http://10.0.0.1/');
                console.log('after-block-never-runs');
                return 'ok';
                """;
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertNotNull(result.debugInfo());
        assertTrue(result.debugInfo().contains("[LOG] before-block"),
                "Expected pre-block log in debugInfo; got: " + result.debugInfo());
        assertFalse(result.debugInfo().contains("after-block-never-runs"),
                "Post-block code must not run; got: " + result.debugInfo());
    }

    @Test
    void securityViolationFromFetchSurfacesStandardMarker() {
        String code = "await fetch('http://10.0.0.1/'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[SECURITY] fetch: literal-private-ip"),
                "Expected fetch security marker; got: " + result.debugInfo());
    }

    @Test
    void securityViolationFromSafetyFsSurfacesStandardMarker(@TempDir Path tempDir) {
        String code = "safety.fs.readText('../etc/passwd'); return 'ok';";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), fsPolicy(true, false), tempDir);
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[SECURITY] safety.fs: path-escapes-base"),
                "Expected fs security marker; got: " + result.debugInfo());
    }

    @Test
    void allowlistRejectSurfacesStandardMarker() {
        EffectivePolicy policy = fetchPolicy(
                new NetworkPolicy("allowlist", Set.of("api.allowed.example"), Set.of()));
        String code = "await fetch('http://evil.example/'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code), policy);
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[SECURITY] fetch: host-not-in-allowlist"),
                "Expected allowlist security marker; got: " + result.debugInfo());
    }

    @Test
    void invalidInputFromParserSurfacesStandardMarker() {
        String code = "safety.parser.csv(); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[INVALID_INPUT] safety.parser.csv: missing-input"),
                "Expected parser invalid-input marker; got: " + result.debugInfo());
    }

    @Test
    void invalidInputFromAtobSurfacesStandardMarker() {
        String code = "atob('!@#$%'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[INVALID_INPUT] atob: invalid-base64"),
                "Expected atob invalid-input marker; got: " + result.debugInfo());
    }

    @Test
    void helperRuntimeFromXmlParserSurfacesStandardMarker() {
        String code = "safety.parser.xml('<not-closed'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[HELPER_RUNTIME] safety.parser.xml: parse-failed"),
                "Expected xml parse-failed marker; got: " + result.debugInfo());
    }


    @Test
    void userExplicitThrowSurfacesJsErrorMarker() {
        String code = "throw new Error('boom'); return 'ok';";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[JS_ERROR] user-code: Error"),
                "Expected JS_ERROR marker for explicit throw; got: " + result.debugInfo());
    }

    @Test
    void userTypeErrorSurfacesJsErrorMarker() {
        String code = "const x = null; return x.foo;";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[JS_ERROR] user-code: TypeError"),
                "Expected TypeError marker; got: " + result.debugInfo());
    }

    @Test
    void userReferenceErrorSurfacesJsErrorMarker() {
        String code = "return undefinedVariable;";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[JS_ERROR] user-code: ReferenceError"),
                "Expected ReferenceError marker; got: " + result.debugInfo());
    }

    @Test
    void userSyntaxErrorSurfacesJsErrorMarker() {
        String code = "return (1 + );";
        JsExecutionResult result = executor.execute(new JsExecutionParams(Map.of(), code));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[JS_ERROR] user-code: SyntaxError"),
                "Expected SyntaxError marker; got: " + result.debugInfo());
    }

    @Test
    void resourceLimitSurfacesStandardMarker() {
        String code = "while (true) {}; return 'never';";
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), limitsPolicy(1000L, 30L));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[RESOURCE_LIMIT] execution: limit-exceeded"),
                "Expected RESOURCE_LIMIT marker; got: " + result.debugInfo());
    }

    @Test
    void timeoutSurfacesStandardMarker() {
        String code = """
                const start = Date.now();
                while (Date.now() - start < 5000) {}
                return 'done';
                """;
        JsExecutionResult result = executor.execute(
                new JsExecutionParams(Map.of(), code), limitsPolicy(Long.MAX_VALUE, 1L));
        assertFalse(result.isOk());
        assertTrue(result.debugInfo().contains("[TIMEOUT] execution: 1s-limit-exceeded"),
                "Expected TIMEOUT marker; got: " + result.debugInfo());
    }
}
