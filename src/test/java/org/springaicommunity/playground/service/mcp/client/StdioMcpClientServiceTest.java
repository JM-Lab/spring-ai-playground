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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StdioMcpClientServiceTest {

    private static final String SERVER_NAME = "stdio-loopback";

    @Autowired
    private McpClientService mcpClientService;

    private McpServerInfo mcpServerInfo;

    @AfterEach
    void tearDown() {
        if (this.mcpServerInfo != null) {
            this.mcpClientService.stopMcpClient(this.mcpServerInfo);
        }
    }

    @Test
    void fullCycleWithStdioTransport() throws JacksonException {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        McpStdioClientProperties.Parameters parameters = new McpStdioClientProperties.Parameters(javaBin,
                List.of("-cp", System.getProperty("java.class.path"), TestStdioMcpServer.class.getName()),
                Map.of());
        long now = System.currentTimeMillis();
        this.mcpServerInfo = new McpServerInfo(McpTransportType.STDIO, SERVER_NAME, "", now, now,
                new ObjectMapper().writeValueAsString(parameters));

        this.mcpClientService.startMcpClient(this.mcpServerInfo);

        List<McpSchema.Tool> tools = this.mcpClientService.getToolListAsOpt(this.mcpServerInfo).orElseThrow();
        assertThat(tools).extracting(McpSchema.Tool::name).containsExactly("echo");

        McpSchema.CallToolResult result = this.mcpClientService
                .callTool(this.mcpServerInfo, "echo", Map.of("message", "Hello World!"), Map.of())
                .orElseThrow();
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).isEqualTo("Echo: Hello World!");

        List<ToolCallback> toolCallbacks = this.mcpClientService.buildToolCallbackProviders(this.mcpServerInfo)
                .stream().map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream).toList();
        assertThat(toolCallbacks).hasSize(1);

        assertThat(this.mcpClientService.pingMcpClient(this.mcpServerInfo)).isNotNull();
        assertThat(this.mcpClientService.getServerCapabilitiesAsOpt(this.mcpServerInfo)).isPresent();

        this.mcpClientService.stopMcpClient(this.mcpServerInfo);
        assertThat(this.mcpClientService.getServerCapabilitiesAsOpt(this.mcpServerInfo)).isEmpty();
        this.mcpServerInfo = null;
    }

}
