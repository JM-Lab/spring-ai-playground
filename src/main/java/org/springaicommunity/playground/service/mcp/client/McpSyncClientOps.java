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

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Map;

public class McpSyncClientOps implements McpClientOps {

    private final McpSyncClient mcpSyncClient;

    public McpSyncClientOps(McpSyncClient mcpSyncClient) {
        this.mcpSyncClient = mcpSyncClient;
    }

    @Override
    public Object ping() {return mcpSyncClient.ping();}

    @Override
    public void close() {mcpSyncClient.close();}

    @Override
    public ServerCapabilities capabilities() {return mcpSyncClient.getServerCapabilities();}

    @Override
    public List<McpSchema.Tool> listTools() {return mcpSyncClient.listTools().tools();}

    @Override
    public McpSchema.CallToolResult callTool(String name, Map<String, Object> args, Map<String, Object> meta) {
        return mcpSyncClient.callTool(
                McpSchema.CallToolRequest.builder().name(name).arguments(args).meta(meta).build());
    }

    @Override
    public List<McpSchema.Resource> listResources() {return mcpSyncClient.listResources().resources();}

    @Override
    public McpSchema.ReadResourceResult readResource(String uri) {
        return mcpSyncClient.readResource(new McpSchema.ReadResourceRequest(uri));
    }

    @Override
    public List<McpSchema.ResourceTemplate> listResourceTemplates() {
        return mcpSyncClient.listResourceTemplates().resourceTemplates();
    }

    @Override
    public List<McpSchema.Prompt> listPrompts() {return mcpSyncClient.listPrompts().prompts();}

    @Override
    public McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> args) {
        return mcpSyncClient.getPrompt(new McpSchema.GetPromptRequest(name, args == null ? Map.of() : args));
    }

    @Override
    public void setLoggingLevel(McpSchema.LoggingLevel level) {mcpSyncClient.setLoggingLevel(level);}

    @Override
    public void addRoot(McpSchema.Root root) {mcpSyncClient.addRoot(root);}

    @Override
    public void removeRoot(String name) {mcpSyncClient.removeRoot(name);}

    @Override
    public void notifyRootsListChanged() {mcpSyncClient.rootsListChangedNotification();}

    @Override
    public ToolCallbackProvider toolCallbackProvider() {
        return new SyncMcpToolCallbackProvider(mcpSyncClient);
    }
}