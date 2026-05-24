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
package org.springaicommunity.playground.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class McpToolObservationFilter implements ObservationFilter {

    public static final String MCP_TRANSPORT = "mcp.transport";
    public static final String MCP_SERVER = "mcp.server";
    public static final String MCP_KIND = "mcp.kind";
    static final String MCP_KIND_VALUE = "mcp";
    static final String IN_PROCESS_KIND_VALUE = "in-process";

    private final McpClientService mcpClientService;

    public McpToolObservationFilter(McpClientService mcpClientService) {
        this.mcpClientService = mcpClientService;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        if (!(context instanceof ToolCallingObservationContext toolContext)) {
            return context;
        }
        String toolName = toolContext.getToolDefinition() == null
                ? null : toolContext.getToolDefinition().name();
        if (toolName == null || toolName.isBlank()) {
            return context;
        }
        Optional<McpToolSource> source = mcpClientService.lookupToolSource(toolName);
        if (source.isEmpty()) {
            // In-process tool — tag so dashboards can split on mcp.kind.
            context.addHighCardinalityKeyValue(KeyValue.of(MCP_KIND, IN_PROCESS_KIND_VALUE));
            return context;
        }
        McpToolSource s = source.get();
        context.addHighCardinalityKeyValue(KeyValue.of(MCP_KIND, MCP_KIND_VALUE));
        context.addHighCardinalityKeyValue(KeyValue.of(MCP_TRANSPORT, s.transport()));
        context.addHighCardinalityKeyValue(KeyValue.of(MCP_SERVER, s.serverName()));
        return context;
    }
}
