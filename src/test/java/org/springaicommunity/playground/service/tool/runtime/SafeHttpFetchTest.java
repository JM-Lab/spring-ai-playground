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
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.runtime.SafeHttpFetch.FetchPolicyException;
import org.springaicommunity.playground.service.tool.runtime.SafeHttpFetch.FetchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeHttpFetchTest {

    private static final NetworkPolicy STRICT = new NetworkPolicy("strict", Set.of(), Set.of());
    private static final NetworkPolicy PERMISSIVE = new NetworkPolicy("permissive", Set.of(), Set.of());

    private static Function<String, InetAddress[]> resolveTo(String... ips) {
        return host -> {
            try {
                InetAddress[] out = new InetAddress[ips.length];
                for (int i = 0; i < ips.length; i++) {
                    out[i] = InetAddress.getByName(ips[i]);
                }
                return out;
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        };
    }

    private static Function<String, InetAddress[]> resolveThrow() {
        return host -> { throw new RuntimeException("dns failure"); };
    }

    @Test
    void enforceBlocksLiteralLoopbackV4() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://127.0.0.1/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralPrivate10() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://10.0.0.1/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralPrivate172() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://172.16.0.1/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralPrivate192() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://192.168.1.1/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralLinkLocal169() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://169.254.169.254/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralLoopbackV6() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://[::1]/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralIpv6LinkLocal() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://[fe80::1]/"), STRICT, resolveTo()));
    }

    @Test
    void enforceBlocksLiteralIpv6Ula() {
        assertThrows(FetchPolicyException.class,
                () -> SafeHttpFetch.enforce(URI.create("http://[fc00::1]/"), STRICT, resolveTo()));
    }

    @Test
    void enforcePassesPublicLiteralIp() {
        SafeHttpFetch.enforce(URI.create("http://8.8.8.8/"), STRICT, resolveTo());
    }

    @Test
    void enforceDnsResolveBlocksPrivate() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://internal.example.com/"), STRICT, resolveTo("10.0.0.5")));
    }

    @Test
    void enforceDnsResolvePassesPublic() {
        SafeHttpFetch.enforce(URI.create("http://public.example.com/"), STRICT, resolveTo("8.8.8.8"));
    }

    @Test
    void enforceDnsResolveBlocksAnyPrivateAmongMultiple() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://mixed.example.com/"), STRICT, resolveTo("8.8.8.8", "10.0.0.1")));
    }

    @Test
    void enforceDnsFailureBlocks() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://nope.example/"), STRICT, resolveThrow()));
    }

    @Test
    void enforceRejectsFileScheme() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("file:///etc/passwd"), STRICT, resolveTo()));
    }

    @Test
    void enforceAllowlistAllowsMatchingHost() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of("api.example.com"), Set.of());
        SafeHttpFetch.enforce(URI.create("http://api.example.com/"), p, resolveTo("8.8.8.8"));
    }

    @Test
    void enforceAllowlistRejectsNonMatching() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of("api.example.com"), Set.of());
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://evil.example.com/"), p, resolveTo("8.8.8.8")));
    }

    @Test
    void enforceAllowlistRejectsEmpty() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of(), Set.of());
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://example.com/"), p, resolveTo("8.8.8.8")));
    }

    @Test
    void enforceAllowlistWildcardMatchesSubdomain() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of("*.example.com"), Set.of());
        SafeHttpFetch.enforce(URI.create("http://api.example.com/"), p, resolveTo("8.8.8.8"));
    }

    @Test
    void enforceAllowlistTrustsExplicitlyListedHostEvenIfResolvesPrivate() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of("api.example.com"), Set.of());
        SafeHttpFetch.enforce(URI.create("http://api.example.com/"), p, resolveTo("10.0.0.5"));
    }

    @Test
    void enforceAllowlistAllowsLiteralPrivateIp() {
        NetworkPolicy p = new NetworkPolicy("allowlist", Set.of("127.0.0.1"), Set.of());
        SafeHttpFetch.enforce(URI.create("http://127.0.0.1/"), p, resolveTo());
    }

    @Test
    void enforceCustomAllowsLiteralPrivateIp() {
        NetworkPolicy p = new NetworkPolicy("custom", Set.of(), Set.of("bad.com"));
        SafeHttpFetch.enforce(URI.create("http://10.0.0.1/"), p, resolveTo());
    }

    @Test
    void enforceCustomBlocksDenyHost() {
        NetworkPolicy p = new NetworkPolicy("custom", Set.of(), Set.of("metadata.internal"));
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://metadata.internal/"), p, resolveTo("8.8.8.8")));
    }

    @Test
    void enforceCustomAllowsNonDenyHost() {
        NetworkPolicy p = new NetworkPolicy("custom", Set.of(), Set.of("metadata.internal"));
        SafeHttpFetch.enforce(URI.create("http://api.example.com/"), p, resolveTo("8.8.8.8"));
    }

    @Test
    void enforcePermissiveSkipsAllChecks() {
        SafeHttpFetch.enforce(URI.create("http://127.0.0.1/"), PERMISSIVE, resolveTo());
    }

    @Test
    void enforceRejectsFileSchemeEvenInPermissive() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("file:///etc/passwd"), PERMISSIVE, resolveTo()));
    }

    @Test
    void enforceNullPolicyDefaultsToStrict() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://127.0.0.1/"), null, resolveTo()));
    }

    @Test
    void enforceNullEgressLevelDefaultsToStrict() {
        NetworkPolicy p = new NetworkPolicy(null, Set.of(), Set.of());
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://10.0.0.1/"), p, resolveTo()));
    }

    @Test
    void enforceUnknownEgressLevelRejects() {
        NetworkPolicy p = new NetworkPolicy("typo", Set.of(), Set.of());
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.enforce(
                URI.create("http://8.8.8.8/"), p, resolveTo()));
    }

    @Test
    void matchesHostExactCaseInsensitive() {
        assertTrue(SafeHttpFetch.matchesHost("Example.com", Set.of("example.com")));
        assertTrue(SafeHttpFetch.matchesHost("example.com", Set.of("EXAMPLE.COM")));
    }

    @Test
    void matchesHostWildcardSubdomain() {
        assertTrue(SafeHttpFetch.matchesHost("api.example.com", Set.of("*.example.com")));
        assertTrue(SafeHttpFetch.matchesHost("a.b.example.com", Set.of("*.example.com")));
        assertTrue(SafeHttpFetch.matchesHost("example.com", Set.of("*.example.com")));
    }

    @Test
    void matchesHostNoMatch() {
        assertFalse(SafeHttpFetch.matchesHost("evil.com", Set.of("example.com")));
        assertFalse(SafeHttpFetch.matchesHost("notexample.com", Set.of("*.example.com")));
    }

    @Test
    void isPrivateOrReservedPublicV4() throws UnknownHostException {
        assertFalse(SafeHttpFetch.isPrivateOrReserved(InetAddress.getByName("8.8.8.8")));
    }

    @Test
    void isPrivateOrReservedCgnat() throws UnknownHostException {
        assertTrue(SafeHttpFetch.isPrivateOrReserved(InetAddress.getByName("100.64.0.1")));
        assertTrue(SafeHttpFetch.isPrivateOrReserved(InetAddress.getByName("100.127.255.254")));
        assertFalse(SafeHttpFetch.isPrivateOrReserved(InetAddress.getByName("100.63.255.255")));
    }

    private HttpServer server;
    private String baseUrl;
    private AtomicInteger redirectCount;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        redirectCount = new AtomicInteger();
        server.createContext("/echo", exchange -> {
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/redirect-to-echo", exchange -> {
            exchange.getResponseHeaders().add("Location", "/echo");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-loop", exchange -> {
            int n = redirectCount.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/redirect-loop?n=" + n);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void fetchReturnsBodyStatusHeaders() {
        FetchResponse response = SafeHttpFetch.fetch(URI.create(baseUrl + "/echo"),
                "GET", Map.of(), null, PERMISSIVE);
        assertEquals(200, response.status());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), response.body());
        assertTrue(response.headers().keySet().stream()
                .anyMatch(k -> k.equalsIgnoreCase("Content-Type")));
    }

    @Test
    void fetchFollowsRedirect() {
        FetchResponse response = SafeHttpFetch.fetch(URI.create(baseUrl + "/redirect-to-echo"),
                "GET", Map.of(), null, PERMISSIVE);
        assertEquals(200, response.status());
        assertTrue(response.finalUrl().endsWith("/echo"));
    }

    @Test
    void fetchBlocksAfterMaxRedirects() {
        assertThrows(FetchPolicyException.class, () -> SafeHttpFetch.fetch(
                URI.create(baseUrl + "/redirect-loop"), "GET", Map.of(), null, PERMISSIVE));
    }
}
