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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpHostClassifierTest {

    @Test
    void stdioIsAlwaysStdioClass() {
        assertEquals(McpHostClassifier.HostClass.STDIO,
                McpHostClassifier.classify(McpTransportType.STDIO, null));
        assertEquals(McpHostClassifier.HostClass.STDIO,
                McpHostClassifier.classify(McpTransportType.STDIO, "https://example.com/mcp"));
    }

    @Test
    void loopbackVariantsAreLoopback() {
        assertEquals(McpHostClassifier.HostClass.LOOPBACK,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://127.0.0.1:8080/mcp"));
        assertEquals(McpHostClassifier.HostClass.LOOPBACK,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://localhost:8080/mcp"));
        assertEquals(McpHostClassifier.HostClass.LOOPBACK,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://api.localhost/mcp"));
        assertEquals(McpHostClassifier.HostClass.LOOPBACK,
                McpHostClassifier.classify(McpTransportType.SSE, "http://[::1]:8080/mcp"));
    }

    @Test
    void rfc1918PrivateRangesAreLan() {
        assertEquals(McpHostClassifier.HostClass.PRIVATE_LAN,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://10.0.0.5:8080/mcp"));
        assertEquals(McpHostClassifier.HostClass.PRIVATE_LAN,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://192.168.1.100:8080/mcp"));
        assertEquals(McpHostClassifier.HostClass.PRIVATE_LAN,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://172.16.5.5:8080/mcp"));
        assertEquals(McpHostClassifier.HostClass.PRIVATE_LAN,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://172.31.255.255:8080/mcp"));
    }

    @Test
    void rangesOutsideRfc1918ArePublicNotLan() {
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://172.15.0.1/mcp"));
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://172.32.0.1/mcp"));
    }

    @Test
    void linkLocalIsLan() {
        assertEquals(McpHostClassifier.HostClass.PRIVATE_LAN,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "http://169.254.5.5:8080/mcp"));
    }

    @Test
    void publicHostsAreClassifiedPublic() {
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "https://api.githubcopilot.com/mcp/"));
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, "https://mcp.notion.com/mcp"));
    }

    @Test
    void invalidOrEmptyUrlDefaultsToPublic() {
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, ""));
        assertEquals(McpHostClassifier.HostClass.PUBLIC,
                McpHostClassifier.classify(McpTransportType.STREAMABLE_HTTP, null));
    }
}
