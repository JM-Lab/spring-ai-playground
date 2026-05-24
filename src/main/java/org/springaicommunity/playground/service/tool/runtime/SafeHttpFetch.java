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

import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class SafeHttpFetch {

    public record FetchResponse(int status, Map<String, String> headers, byte[] body, String finalUrl) {}

    public static final class FetchPolicyException extends JsHelperException {
        public FetchPolicyException(Kind kind, String reason, String message) {
            super(kind, "fetch", reason, message);
        }
    }

    static FetchPolicyException reject(JsHelperException.Kind kind, String reason, String message) {
        SandboxGuardMetrics.recordBlock("fetch", reason);
        return new FetchPolicyException(kind, reason, message);
    }

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private SafeHttpFetch() {}

    public static FetchResponse fetch(URI uri, String method, Map<String, String> headers,
                                      byte[] body, NetworkPolicy policy) {
        return fetch(uri, method, headers, body, policy, defaultClient(), SafeHttpFetch::resolveAll);
    }

    static FetchResponse fetch(URI uri, String method, Map<String, String> headers, byte[] body,
                               NetworkPolicy policy, HttpClient client,
                               Function<String, InetAddress[]> dnsResolver) {
        URI current = uri;
        String currentMethod = method == null ? "GET" : method.toUpperCase();
        byte[] currentBody = currentMethod.equals("GET") || currentMethod.equals("HEAD") ? null : body;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            enforce(current, policy, dnsResolver);
            HttpResponse<byte[]> raw = sendOnce(client, current, currentMethod, headers, currentBody);
            int status = raw.statusCode();
            if (status >= 300 && status < 400 && status != 304) {
                String location = raw.headers().firstValue("Location").orElse(null);
                if (location == null || location.isBlank()) {
                    return toResponse(raw, current);
                }
                URI next = current.resolve(location);
                String nextMethod = redirectMethod(currentMethod, status);
                current = next;
                currentMethod = nextMethod;
                currentBody = methodAllowsBody(nextMethod) ? currentBody : null;
                continue;
            }
            return toResponse(raw, current);
        }
        throw reject(JsHelperException.Kind.SECURITY, "too-many-redirects",
                "[fetch] denied: too many redirects (max " + MAX_REDIRECTS + ")");
    }

    static void enforce(URI uri, NetworkPolicy policy, Function<String, InetAddress[]> dnsResolver) {
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw reject(JsHelperException.Kind.SECURITY, "unsupported-scheme",
                    "[fetch] denied: unsupported scheme: " + scheme);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "missing-host",
                    "[fetch] denied: missing host");
        }

        String level = policy == null || policy.egressLevel() == null
                ? "strict" : policy.egressLevel().toLowerCase();

        switch (level) {
            case "permissive" -> {
                return;
            }
            case "allowlist" -> {
                Set<String> allow = policy == null ? null : policy.hostsAllow();
                if (allow == null || allow.isEmpty() || !matchesHost(host, allow)) {
                    throw reject(JsHelperException.Kind.SECURITY, "host-not-in-allowlist",
                            "[fetch] denied: host not in allowlist: " + host);
                }
                return;
            }
            case "custom" -> {
                Set<String> deny = policy == null ? null : policy.hostsDeny();
                if (deny != null && matchesHost(host, deny)) {
                    throw reject(JsHelperException.Kind.SECURITY, "host-in-denylist",
                            "[fetch] denied: host in denylist: " + host);
                }
                return;
            }
            case "strict" -> enforceSsrfFourLayer(host, dnsResolver);
            default -> throw reject(JsHelperException.Kind.INVALID_INPUT, "unknown-egress-level",
                    "[fetch] denied: unknown egress level: " + level);
        }
    }

    private static void enforceSsrfFourLayer(String host, Function<String, InetAddress[]> dnsResolver) {
        InetAddress literal = tryParseLiteral(host);
        if (literal != null) {
            if (isPrivateOrReserved(literal)) {
                throw reject(JsHelperException.Kind.SECURITY, "literal-private-ip",
                        "[fetch] denied: literal private/reserved IP: " + host);
            }
            return;
        }
        InetAddress[] resolved;
        try {
            resolved = dnsResolver.apply(host);
        } catch (RuntimeException e) {
            throw reject(JsHelperException.Kind.SECURITY, "dns-resolution-failed",
                    "[fetch] denied: dns resolution failed: " + host);
        }
        if (resolved == null || resolved.length == 0) {
            throw reject(JsHelperException.Kind.SECURITY, "dns-no-addresses",
                    "[fetch] denied: dns returned no addresses: " + host);
        }
        for (InetAddress addr : resolved) {
            if (isPrivateOrReserved(addr)) {
                throw reject(JsHelperException.Kind.SECURITY, "resolved-private-ip",
                        "[fetch] denied: host resolves to private/reserved IP: "
                                + host + " -> " + addr.getHostAddress());
            }
        }
    }

    static boolean isPrivateOrReserved(InetAddress addr) {
        if (addr.isLoopbackAddress()) return true;
        if (addr.isLinkLocalAddress()) return true;
        if (addr.isSiteLocalAddress()) return true;
        if (addr.isAnyLocalAddress()) return true;
        if (addr.isMulticastAddress()) return true;
        byte[] raw = addr.getAddress();
        if (raw.length == 16) {
            // RFC 4193 ULA fc00::/7 (covers fc00:: and fd00::)
            if ((raw[0] & 0xFE) == 0xFC) return true;
        }
        if (raw.length == 4) {
            int first = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            // CGNAT 100.64.0.0/10 — not flagged by isSiteLocalAddress
            if (first == 100 && second >= 64 && second <= 127) return true;
        }
        return false;
    }

    static boolean matchesHost(String host, Set<String> patterns) {
        String h = host.toLowerCase();
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) continue;
            String p = pattern.toLowerCase().trim();
            if (p.startsWith("*.")) {
                String suffix = p.substring(1);
                if (h.endsWith(suffix)) return true;
                if (h.equals(suffix.substring(1))) return true;
            } else if (h.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress tryParseLiteral(String host) {
        if (host == null) return null;
        String h = host;
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }
        if (!looksLikeLiteralIp(h)) return null;
        try {
            return InetAddress.getByName(h);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean looksLikeLiteralIp(String h) {
        if (h.indexOf(':') >= 0) return true;
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (!((c >= '0' && c <= '9') || c == '.')) return false;
        }
        return h.indexOf('.') >= 0;
    }

    private static InetAddress[] resolveAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "dns-unknown-host",
                    "[fetch] dns: unknown host: " + host, e);
        }
    }

    private static HttpClient defaultClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static HttpResponse<byte[]> sendOnce(HttpClient client, URI uri, String method,
                                                 Map<String, String> headers, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT);
        HttpRequest.BodyPublisher publisher = body == null || body.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);
        builder.method(method, publisher);
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && v != null && !isRestrictedHeader(k)) {
                    builder.header(k, v);
                }
            });
        }
        HttpRequest request = builder.build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body() != null && response.body().length > MAX_BODY_BYTES) {
                throw reject(JsHelperException.Kind.SECURITY, "response-body-too-large",
                        "[fetch] denied: response body exceeds " + MAX_BODY_BYTES + " bytes");
            }
            return response;
        } catch (HttpTimeoutException e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "timeout",
                    "[fetch] timeout: " + REQUEST_TIMEOUT.toSeconds() + "s", e);
        } catch (SSLException e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "tls",
                    "[fetch] tls: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "io",
                    "[fetch] io: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JsHelperException(JsHelperException.Kind.HELPER_RUNTIME, "fetch", "interrupted",
                    "[fetch] interrupted", e);
        }
    }

    private static boolean isRestrictedHeader(String name) {
        String n = name.toLowerCase();
        return n.equals("connection") || n.equals("content-length") || n.equals("expect")
                || n.equals("host") || n.equals("upgrade");
    }

    private static String redirectMethod(String current, int status) {
        String m = current == null ? "GET" : current.toUpperCase();
        if (status == 303) return "GET";
        if ((status == 301 || status == 302)
                && (m.equals("POST") || m.equals("PUT") || m.equals("PATCH"))) {
            return "GET";
        }
        return m;
    }

    private static boolean methodAllowsBody(String method) {
        if (method == null) return false;
        String m = method.toUpperCase();
        return m.equals("POST") || m.equals("PUT") || m.equals("PATCH") || m.equals("DELETE");
    }

    private static FetchResponse toResponse(HttpResponse<byte[]> raw, URI uri) {
        Map<String, String> outHeaders = new LinkedHashMap<>();
        raw.headers().map().forEach((k, vs) -> {
            if (!vs.isEmpty()) outHeaders.put(k, String.join(", ", vs));
        });
        byte[] body = raw.body() == null ? new byte[0] : raw.body();
        return new FetchResponse(raw.statusCode(), outHeaders, body, uri.toString());
    }
}
