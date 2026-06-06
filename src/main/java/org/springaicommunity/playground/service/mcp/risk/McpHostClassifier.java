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
package org.springaicommunity.playground.service.mcp.risk;

import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public final class McpHostClassifier {

    public enum HostClass { STDIO, LOOPBACK, PRIVATE_LAN, PUBLIC }

    private static final Pattern IPV4_PRIVATE_10 = Pattern.compile("^10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV4_PRIVATE_192 = Pattern.compile("^192\\.168\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV4_PRIVATE_172 = Pattern.compile("^172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV4_LINK_LOCAL = Pattern.compile("^169\\.254\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern IPV6_ULA = Pattern.compile("^[fF][cdCD][0-9a-fA-F]{0,2}:.*");
    private static final Pattern IPV6_LINK_LOCAL = Pattern.compile("^[fF][eE][89aAbB][0-9a-fA-F]?:.*");

    public static HostClass classify(McpTransportType transport, String url) {
        if (transport == McpTransportType.STDIO) return HostClass.STDIO;
        if (url == null || url.isBlank()) return HostClass.PUBLIC;
        String host = extractHost(url);
        if (host == null || host.isBlank()) return HostClass.PUBLIC;
        return classifyHost(host);
    }

    public static HostClass classifyHost(String rawHost) {
        if (rawHost == null) return HostClass.PUBLIC;
        String host = rawHost.toLowerCase(Locale.ROOT).trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.equals("127.0.0.1") || host.equals("::1") || host.equals("localhost")
                || host.endsWith(".localhost")) {
            return HostClass.LOOPBACK;
        }
        if (IPV4_PRIVATE_10.matcher(host).matches()
                || IPV4_PRIVATE_192.matcher(host).matches()
                || IPV4_PRIVATE_172.matcher(host).matches()
                || IPV4_LINK_LOCAL.matcher(host).matches()
                || IPV6_ULA.matcher(host).matches()
                || IPV6_LINK_LOCAL.matcher(host).matches()) {
            return HostClass.PRIVATE_LAN;
        }
        return HostClass.PUBLIC;
    }

    static String extractHost(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String h = uri.getHost();
            if (h != null) return h;
        } catch (IllegalArgumentException ignored) {
            // fall through to manual parse
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return null;
        String rest = url.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        int at = rest.indexOf('@');
        if (at >= 0) rest = rest.substring(at + 1);
        if (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            return close > 0 ? rest.substring(1, close) : rest;
        }
        int colon = rest.indexOf(':');
        return colon > 0 ? rest.substring(0, colon) : rest;
    }

    private McpHostClassifier() {}
}
