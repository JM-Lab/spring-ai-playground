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

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

public final class TestStdioMcpServer {

    private TestStdioMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        // stdout carries the JSON-RPC stream; reroute all logging to stderr before anything logs
        PrintStream protocolOut = System.out;
        System.setOut(System.err);
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
        McpSchema.Tool echoTool = McpSchema.Tool.builder()
                .name("echo")
                .description("Echoes back the given message.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of("message", Map.of("type", "string")),
                        "required", List.of("message")))
                .build();
        McpServer.sync(new StdioServerTransportProvider(jsonMapper, System.in, protocolOut))
                .serverInfo("test-stdio-mcp-server", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(new McpServerFeatures.SyncToolSpecification(echoTool,
                        (exchange, request) -> McpSchema.CallToolResult.builder()
                                .addTextContent("Echo: " + request.arguments().get("message"))
                                .build()))
                .build();
        Thread.currentThread().join();
    }

}
