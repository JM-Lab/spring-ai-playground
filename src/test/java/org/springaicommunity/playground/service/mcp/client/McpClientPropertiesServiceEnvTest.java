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
package org.springaicommunity.playground.service.mcp.client;

import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientPropertiesServiceEnvTest {

    private static final String VAR = "MCP_PROP_TEST_VAR";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clear() {
        System.clearProperty(VAR);
    }

    @Test
    void streamableMissingEnvVarThrowsWithName() {
        var svc = new McpStreamableHttpClientPropertiesService(null);
        String json = """
                {"url":"http://x","endpoint":"/mcp",
                 "headers":{"Authorization":"Bearer ${%s}"}}""".formatted(VAR);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains(VAR), "expected message to list missing var, got: " + ex.getMessage());
    }

    @Test
    void streamableDeclaredRequiredEnvNotSetThrows() {
        var svc = new McpStreamableHttpClientPropertiesService(null);
        String json = """
                {"url":"http://x","endpoint":"/mcp","requiredEnv":["%s"]}""".formatted(VAR);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains(VAR));
    }

    @Test
    void streamableEnvSetBuildsTransport() {
        System.setProperty(VAR, "secret-token");
        var svc = new McpStreamableHttpClientPropertiesService(null);
        String json = """
                {"url":"http://localhost:9999","endpoint":"/mcp",
                 "headers":{"Authorization":"Bearer ${%s}"},
                 "requiredEnv":["%s"]}""".formatted(VAR, VAR);
        McpClientTransport transport = svc.buildClientTransport(objectMapper, json);
        assertNotNull(transport);
    }

    @Test
    void streamableLegacyJsonWithoutHeadersBuildsTransport() {
        var svc = new McpStreamableHttpClientPropertiesService(null);
        String legacy = """
                {"url":"http://localhost:9999","endpoint":"/mcp"}""";
        McpClientTransport transport = svc.buildClientTransport(objectMapper, legacy);
        assertNotNull(transport);
    }

    @Test
    void sseMissingEnvVarThrows() {
        var svc = new McpSseClientPropertiesService(null);
        String json = """
                {"url":"http://x","sse-endpoint":"/sse",
                 "headers":{"X-API-Key":"${%s}"}}""".formatted(VAR);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains(VAR));
    }

    @Test
    void sseEnvSetBuildsTransport() {
        System.setProperty(VAR, "abc");
        var svc = new McpSseClientPropertiesService(null);
        String json = """
                {"url":"http://localhost:9999","sse-endpoint":"/sse",
                 "headers":{"X-API-Key":"${%s}"}}""".formatted(VAR);
        McpClientTransport transport = svc.buildClientTransport(objectMapper, json);
        assertNotNull(transport);
    }

    @Test
    void stdioMissingEnvInEnvMapThrows() {
        var svc = new McpStdioClientPropertiesService(null);
        String json = """
                {"command":"echo","args":[],"env":{"TOKEN":"${%s}"}}""".formatted(VAR);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains(VAR));
    }

    @Test
    void stdioEnvSubstitutionAppliedDoesNotThrow() {
        System.setProperty(VAR, "real-value");
        var svc = new McpStdioClientPropertiesService(null);
        String json = """
                {"command":"echo","args":[],"env":{"TOKEN":"${%s}","STATIC":"42"}}""".formatted(VAR);
        assertNotNull(svc.buildClientTransport(objectMapper, json));
    }

    @Test
    void streamableListsAllMissingNames() {
        var svc = new McpStreamableHttpClientPropertiesService(null);
        String json = """
                {"url":"http://x","endpoint":"/mcp",
                 "headers":{"H1":"${MCP_PROP_TEST_A}","H2":"Bearer ${MCP_PROP_TEST_B}"}}""";
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains("MCP_PROP_TEST_A"));
        assertTrue(ex.getMessage().contains("MCP_PROP_TEST_B"));
    }

    @Test
    void streamableInvalidJsonThrowsIllegalState() {
        var svc = new McpStreamableHttpClientPropertiesService(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, "{not-json"));
        assertTrue(ex.getMessage().contains("Failed to parse MCP client connection parameters"));
    }

    @Test
    void stdioMissingEnvVarInCommandThrowsWithName() {
        var svc = new McpStdioClientPropertiesService(null);
        String json = """
                {"command":"${%s}/bin/server","args":[],"env":{}}""".formatted(VAR);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> svc.buildClientTransport(objectMapper, json));
        assertTrue(ex.getMessage().contains(VAR));
    }

    @Test
    void stdioNpxCommandIsWrappedWithCmdOnWindows() throws Exception {
        String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 11");
        try {
            var svc = new McpStdioClientPropertiesService(null);
            String json = """
                    {"command":"npx","args":["-y","@modelcontextprotocol/server-everything"],"env":{}}""";
            ServerParameters params = serverParametersOf(svc.buildClientTransport(objectMapper, json));
            assertEquals("cmd", params.getCommand());
            assertEquals(List.of("/c", "npx", "-y", "@modelcontextprotocol/server-everything"), params.getArgs());
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    @Test
    void stdioExplicitPathCommandIsNotWrappedOnWindows() throws Exception {
        String originalOs = System.getProperty("os.name");
        System.setProperty("os.name", "Windows 11");
        try {
            var svc = new McpStdioClientPropertiesService(null);
            String json = """
                    {"command":"C:/tools/server.exe","args":["--stdio"],"env":{}}""";
            ServerParameters params = serverParametersOf(svc.buildClientTransport(objectMapper, json));
            assertEquals("C:/tools/server.exe", params.getCommand());
            assertEquals(List.of("--stdio"), params.getArgs());
        } finally {
            System.setProperty("os.name", originalOs);
        }
    }

    private static ServerParameters serverParametersOf(McpClientTransport transport) throws Exception {
        Field paramsField = StdioClientTransport.class.getDeclaredField("params");
        paramsField.setAccessible(true);
        return (ServerParameters) paramsField.get(transport);
    }
}
