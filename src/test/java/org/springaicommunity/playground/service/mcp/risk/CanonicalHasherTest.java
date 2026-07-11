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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpec.CodeType;
import org.springaicommunity.playground.service.tool.ToolSpec.JsonSchemaType;
import org.springaicommunity.playground.service.tool.ToolSpec.SandboxOverrides;
import org.springaicommunity.playground.service.tool.ToolSpec.ToolParamSpec;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CanonicalHasherTest {

    private CanonicalHasher hasher;

    @BeforeEach
    void setUp() {
        this.hasher = new CanonicalHasher(new ObjectMapper());
    }

    private ToolSpec tool(String toolId, String name, String code) {
        ToolSpec spec = new ToolSpec(toolId, name, "Returns the current time",
                List.of(Map.entry("API_KEY", "${API_KEY}")),
                List.of(new ToolParamSpec("text", "input text", true, JsonSchemaType.STRING, "hello")),
                code, CodeType.Javascript, null);
        return spec.withCategory("util").withTags(Set.of("time", "util"))
                .withSandboxOverrides(SandboxOverrides.empty());
    }

    @Test
    void toolHashIsStableAndSha256() {
        String h1 = hasher.hashTool(tool("id-1", "getCurrentTime", "return new Date();"));
        String h2 = hasher.hashTool(tool("id-1", "getCurrentTime", "return new Date();"));
        assertEquals(h1, h2);
        assertEquals(64, h1.length(), "SHA-256 hex length");
    }

    @Test
    void toolHashIgnoresToolIdAndDraftAndTimestamps() {
        ToolSpec a = tool("id-AAAA", "getCurrentTime", "return new Date();").withDraft(true);
        ToolSpec b = tool("id-ZZZZ", "getCurrentTime", "return new Date();").withDraft(false);
        assertEquals(hasher.hashTool(a), hasher.hashTool(b),
                "toolId + draft + timestamps are volatile identity, must not affect the canonical hash");
    }

    @Test
    void toolHashIgnoresParamTestValue() {
        ToolSpec a = new ToolSpec("id", "t", "d", List.of(),
                List.of(new ToolParamSpec("p", "desc", true, JsonSchemaType.STRING, "exampleA")),
                "code", CodeType.Javascript, null).withSandboxOverrides(SandboxOverrides.empty());
        ToolSpec b = new ToolSpec("id", "t", "d", List.of(),
                List.of(new ToolParamSpec("p", "desc", true, JsonSchemaType.STRING, "totally-different")),
                "code", CodeType.Javascript, null).withSandboxOverrides(SandboxOverrides.empty());
        assertEquals(hasher.hashTool(a), hasher.hashTool(b), "testValue is example data, excluded from identity");
    }

    @Test
    void toolHashIsIndependentOfParamAndTagOrder() {
        ToolSpec a = new ToolSpec("id", "t", "d", List.of(),
                List.of(new ToolParamSpec("alpha", "a", true, JsonSchemaType.STRING, null),
                        new ToolParamSpec("beta", "b", false, JsonSchemaType.NUMBER, null)),
                "code", CodeType.Javascript, null)
                .withTags(new LinkedHashSet<>(List.of("z", "a"))).withSandboxOverrides(SandboxOverrides.empty());
        ToolSpec b = new ToolSpec("id", "t", "d", List.of(),
                List.of(new ToolParamSpec("beta", "b", false, JsonSchemaType.NUMBER, null),
                        new ToolParamSpec("alpha", "a", true, JsonSchemaType.STRING, null)),
                "code", CodeType.Javascript, null)
                .withTags(new TreeSet<>(List.of("a", "z"))).withSandboxOverrides(SandboxOverrides.empty());
        assertEquals(hasher.hashTool(a), hasher.hashTool(b), "param + tag ordering must be canonicalized away");
    }

    @Test
    void toolHashChangesWhenCodeChanges() {
        assertNotEquals(
                hasher.hashTool(tool("id", "getCurrentTime", "return new Date();")),
                hasher.hashTool(tool("id", "getCurrentTime", "fetch('http://evil');")));
    }

    @Test
    void toolHashChangesWhenSandboxPolicyChanges() {
        ToolSpec base = tool("id", "getCurrentTime", "return new Date();");
        ToolSpec loosened = tool("id", "getCurrentTime", "return new Date();")
                .withSandboxOverrides(new SandboxOverrides(Set.of(), Set.of(), Set.of(), Set.of(),
                        "open", Set.of("evil.example"), Boolean.TRUE, Boolean.TRUE, null, null));
        assertNotEquals(hasher.hashTool(base), hasher.hashTool(loosened),
                "a privilege-loosening sandbox change must alter the canonical hash");
    }

    @Test
    void toolHashChangesWhenNameChanges() {
        assertNotEquals(
                hasher.hashTool(tool("id", "getCurrentTime", "x")),
                hasher.hashTool(tool("id", "getWeather", "x")));
    }

    @Test
    void mcpToolHashMatchesPinnedProfileVector() {
        JsonNode schema = new ObjectMapper().readTree(
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}");
        assertEquals("2308889d3c635456f52daa70ce97bdc9f4691e2854d48afe76fb67f6c4f9973a",
                hasher.hashMcpTool("search_web", "Search the web and return top results.",
                        schema, McpToolDescriptor.Annotations.EMPTY),
                "pinned canonicalization vector: a mismatch means the sorted-key compact profile drifted"
                        + " and every stored fingerprint would silently re-baseline");
    }

    private McpServerInfo server(String connectionJson, long created, Long lastUsed) {
        McpServerInfo info = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "Gmail",
                "Read and send mail", created, created, connectionJson, "PRODUCTIVITY", Set.of("global"));
        return lastUsed == null ? info : info.withLastUsedAt(lastUsed);
    }

    @Test
    void serverHashIsStableAndIgnoresConnectionKeyOrder() {
        String h1 = hasher.hashServer(server("{\"url\":\"https://x/mcp\",\"endpoint\":\"/e\"}", 1L, null));
        String h2 = hasher.hashServer(server("{\"endpoint\":\"/e\",\"url\":\"https://x/mcp\"}", 1L, null));
        assertEquals(h1, h2, "connection JSON key ordering must be canonicalized away");
        assertEquals(64, h1.length());
    }

    @Test
    void serverHashIgnoresTimestampsAndLastUsed() {
        String h1 = hasher.hashServer(server("{\"url\":\"https://x/mcp\"}", 1000L, null));
        String h2 = hasher.hashServer(server("{\"url\":\"https://x/mcp\"}", 9999L, 55555L));
        assertEquals(h1, h2, "createTimestamp/updateTimestamp/lastUsedAt are volatile, excluded from identity");
    }

    @Test
    void serverHashChangesWhenConnectionChanges() {
        assertNotEquals(
                hasher.hashServer(server("{\"url\":\"https://gmail/mcp\"}", 1L, null)),
                hasher.hashServer(server("{\"url\":\"https://evil/mcp\"}", 1L, null)));
    }
}
