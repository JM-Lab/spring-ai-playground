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
package org.springaicommunity.playground;

import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpecService.ExposureMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "spring.ai.playground")
public record SpringAiPlaygroundOptions(@NestedConfigurationProperty ToolStudio toolStudio, boolean persistence,
                                        String userHome, @NestedConfigurationProperty Chat chat,
                                        @NestedConfigurationProperty BuiltInMcpServer builtInMcpServer,
                                        @NestedConfigurationProperty McpServer mcpServer) {

    public SpringAiPlaygroundOptions {
        if (builtInMcpServer == null) builtInMcpServer = new BuiltInMcpServer(null, null, null);
        if (mcpServer == null) mcpServer = new McpServer(null, null);
    }

    public record BuiltInMcpServer(String name, String description, ExposureMode exposureMode) {
        public BuiltInMcpServer {
            if (name == null || name.isBlank()) name = "spring-ai-playground-built-in-mcp";
            if (description == null || description.isBlank()) {
                description = "Spring AI Playground's built-in MCP server.";
            }
            if (exposureMode == null) exposureMode = ExposureMode.BOTH;
        }
    }

    public record McpServer(RiskLevel composedToolsMaxRisk, List<ComposedTool> composedTools) {
        public McpServer {
            if (composedToolsMaxRisk == null) composedToolsMaxRisk = RiskLevel.L5;
            composedTools = composedTools == null ? List.of() : List.copyOf(composedTools);
        }
    }

    public record ComposedTool(String server, String tool, String alias, String description, boolean hitl) {}

    public record DefaultTools(String preset,
                               @NestedConfigurationProperty SelectionRule include,
                               @NestedConfigurationProperty SelectionRule exclude) {}

    public record SelectionRule(Set<String> names, Set<String> tags, Set<String> categories) {}

    public record ToolStudio(Long timeoutSeconds, @NestedConfigurationProperty JsSandbox jsSandbox,
                             @NestedConfigurationProperty FsConfig fs,
                             @NestedConfigurationProperty DefaultTools defaultTools) {}

    public record JsSandbox(boolean allowNetworkIo, boolean allowFileIo, boolean allowNativeAccess,
                            boolean allowCreateThread, Long maxStatements, Set<String> denyClasses,
                            Set<String> allowClasses, Map<String, SandboxProfile> profiles) {}

    public record SandboxProfile(String level, String extendsProfile, Boolean javaInterop,
                                 Set<String> allowClasses, Set<String> denyClasses,
                                 @NestedConfigurationProperty NetworkPolicy network,
                                 String fileMode,
                                 Long maxStatements, Long timeoutMs) {}

    public record NetworkPolicy(String egressLevel, Set<String> hostsAllow, Set<String> hostsDeny) {}

    public record FsConfig(String basePath) {}

    public record Chat(String systemPrompt, List<String> models,
                       @NestedConfigurationProperty ChatOptionsConfig chatOptions, Integer toolResultMaxChars,
                       Integer memoryMaxMessages, Integer historyMaxMessages, Integer defaultMaxTokens,
                       @NestedConfigurationProperty ToolSearch toolSearch) {
        public Chat {
            if (toolResultMaxChars == null) toolResultMaxChars = 12_000;
            if (memoryMaxMessages == null || memoryMaxMessages <= 0) memoryMaxMessages = 10;
            if (historyMaxMessages == null || historyMaxMessages <= 0) historyMaxMessages = 2_000;
            if (defaultMaxTokens == null || defaultMaxTokens <= 0) defaultMaxTokens = 8_192;
            if (toolSearch == null) toolSearch = new ToolSearch(null, null, null, null, null, null);
        }
    }

    public record ChatOptionsConfig(String model, Double temperature, Integer maxTokens, Double topP,
                                    Integer topK, Double frequencyPenalty, Double presencePenalty,
                                    List<String> stopSequences) {}

    public record ToolSearch(Boolean enabled, Boolean defaultOn, Integer minTools, Integer maxResults,
                             IndexType indexType, VectorStoreMode vectorStore) {
        public enum IndexType { HYBRID, VECTOR }

        public enum VectorStoreMode { DEDICATED, SHARED }

        public ToolSearch {
            if (enabled == null) enabled = true;
            if (defaultOn == null) defaultOn = false;
            if (minTools == null || minTools < 0) minTools = 10;
            if (maxResults == null || maxResults <= 0) maxResults = 3;
            if (indexType == null) indexType = IndexType.HYBRID;
            if (vectorStore == null) vectorStore = VectorStoreMode.DEDICATED;
        }
    }
}
