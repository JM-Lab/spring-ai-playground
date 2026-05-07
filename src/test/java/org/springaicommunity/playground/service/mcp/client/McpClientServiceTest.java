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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public interface McpClientServiceTest {

    default McpServerInfo testMcpClient(McpClientService mcpClientService, String serverName,
            McpTransportType mcpTransportType, Object parameters) throws JsonProcessingException {
        long currentTimeMillis = System.currentTimeMillis();
        McpServerInfo mcpServerInfo = new McpServerInfo(mcpTransportType, serverName, "", currentTimeMillis,
                currentTimeMillis, new ObjectMapper().writeValueAsString(parameters));

        mcpClientService.startMcpClient(mcpServerInfo);

        List<McpSchema.Tool> toolList =
                mcpClientService.getToolListAsOpt(mcpServerInfo).orElseThrow();
        assertThat(toolList).hasSize(8);

        List<McpSchema.Resource> resourceList =
                mcpClientService.getResourceListAsOpt(mcpServerInfo).orElseThrow();
        assertThat(resourceList).isNotEmpty();

        McpSchema.ReadResourceResult readResult =
                mcpClientService.readResourceAsOpt(mcpServerInfo, resourceList.getFirst().uri())
                        .orElseThrow();
        assertThat(readResult.contents()).isNotEmpty();

        List<McpSchema.ResourceTemplate> templates =
                mcpClientService.getResourceTemplateListAsOpt(mcpServerInfo).orElseThrow();
        assertThat(templates).isNotNull();

        List<McpSchema.Prompt> prompts =
                mcpClientService.getPromptListAsOpt(mcpServerInfo).orElseThrow();
        assertThat(prompts).isNotEmpty();

        McpSchema.Prompt firstPrompt = prompts.getFirst();
        Map<String, Object> promptArgs = new java.util.LinkedHashMap<>();
        if (firstPrompt.arguments() != null) {
            for (McpSchema.PromptArgument arg : firstPrompt.arguments()) {
                if (Boolean.TRUE.equals(arg.required())) {
                    promptArgs.put(arg.name(), "test");
                }
            }
        }
        McpSchema.GetPromptResult promptResult = mcpClientService.getPromptAsOpt(mcpServerInfo,
                firstPrompt.name(), promptArgs).orElseThrow();
        assertThat(promptResult.messages()).isNotNull();

        McpSchema.CallToolResult toolResult = mcpClientService.callTool(mcpServerInfo, "echo", Map.of("message",
                "Hello World!"), Map.of()).get();
        McpSchema.TextContent content = (McpSchema.TextContent) toolResult.content().getFirst();
        assertThat(content.text()).isEqualTo("Echo: Hello World!");

        List<ToolCallback> toolCallbacks =
                mcpClientService.buildToolCallbackProviders(mcpServerInfo).stream()
                        .map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream).toList();
        assertThat(toolCallbacks).hasSize(8);

        assertThat(mcpClientService.pingMcpClient(mcpServerInfo)).isNotNull();

        McpSchema.ServerCapabilities caps = mcpClientService.getServerCapabilitiesAsOpt(mcpServerInfo).orElseThrow();
        assertThat(caps).isNotNull();

        mcpClientService.stopMcpClient(mcpServerInfo);
        assertThat(mcpClientService.getServerCapabilitiesAsOpt(mcpServerInfo)).isEmpty();
        return mcpServerInfo;
    }
}
