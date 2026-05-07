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

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.Map;

public interface McpClientOps {

    Object ping();

    void close();

    ServerCapabilities capabilities();

    List<McpSchema.Tool> listTools();

    CallToolResult callTool(String name, Map<String, Object> args, Map<String, Object> meta);

    List<McpSchema.Resource> listResources();

    McpSchema.ReadResourceResult readResource(String uri);

    List<McpSchema.ResourceTemplate> listResourceTemplates();

    List<McpSchema.Prompt> listPrompts();

    McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> args);

    void setLoggingLevel(McpSchema.LoggingLevel level);

    void addRoot(McpSchema.Root root);

    void removeRoot(String name);

    void notifyRootsListChanged();

    ToolCallbackProvider toolCallbackProvider();
}