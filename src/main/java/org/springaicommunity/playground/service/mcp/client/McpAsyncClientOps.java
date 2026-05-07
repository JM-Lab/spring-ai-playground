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

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Map;

public class McpAsyncClientOps implements McpClientOps {

    private final McpAsyncClient mcpAsyncClient;

    public McpAsyncClientOps(McpAsyncClient mcpAsyncClient) {
        this.mcpAsyncClient = mcpAsyncClient;
    }

    @Override
    public Object ping() {return mcpAsyncClient.ping();}

    @Override
    public void close() {mcpAsyncClient.close();}

    @Override
    public ServerCapabilities capabilities() {return mcpAsyncClient.getServerCapabilities();}

    @Override
    public List<McpSchema.Tool> listTools() {return mcpAsyncClient.listTools().block().tools();}

    @Override
    public CallToolResult callTool(String name, Map<String, Object> args, Map<String, Object> meta) {
        return mcpAsyncClient.callTool(
                McpSchema.CallToolRequest.builder().name(name).arguments(args).meta(meta).build()).block();
    }

    @Override
    public List<McpSchema.Resource> listResources() {return mcpAsyncClient.listResources().block().resources();}

    @Override
    public McpSchema.ReadResourceResult readResource(String uri) {
        return mcpAsyncClient.readResource(new McpSchema.ReadResourceRequest(uri)).block();
    }

    @Override
    public List<McpSchema.ResourceTemplate> listResourceTemplates() {
        return mcpAsyncClient.listResourceTemplates().block().resourceTemplates();
    }

    @Override
    public List<McpSchema.Prompt> listPrompts() {return mcpAsyncClient.listPrompts().block().prompts();}

    @Override
    public McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> args) {
        return mcpAsyncClient.getPrompt(new McpSchema.GetPromptRequest(name, args == null ? Map.of() : args)).block();
    }

    @Override
    public void setLoggingLevel(McpSchema.LoggingLevel level) {mcpAsyncClient.setLoggingLevel(level).block();}

    @Override
    public void addRoot(McpSchema.Root root) {mcpAsyncClient.addRoot(root).block();}

    @Override
    public void removeRoot(String name) {mcpAsyncClient.removeRoot(name).block();}

    @Override
    public void notifyRootsListChanged() {mcpAsyncClient.rootsListChangedNotification().block();}

    @Override
    public ToolCallbackProvider toolCallbackProvider() {
        return new AsyncMcpToolCallbackProvider(mcpAsyncClient);
    }
}