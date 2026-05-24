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
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolObservationFilterTest {

    @Test
    void addsMcpAttributesWhenToolIsMcpSourced() {
        McpClientService mcpClientService = mock(McpClientService.class);
        when(mcpClientService.lookupToolSource("weather"))
                .thenReturn(Optional.of(new McpToolSource("weather-server", "STDIO")));

        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);
        ToolCallingObservationContext ctx = ctx("weather");

        filter.map(ctx);

        Map<String, String> attrs = highCardMap(ctx);
        assertThat(attrs)
                .containsEntry(McpToolObservationFilter.MCP_KIND, "mcp")
                .containsEntry(McpToolObservationFilter.MCP_TRANSPORT, "STDIO")
                .containsEntry(McpToolObservationFilter.MCP_SERVER, "weather-server");
    }

    @Test
    void tagsInProcessWhenToolIsNotMcpSourced() {
        McpClientService mcpClientService = mock(McpClientService.class);
        when(mcpClientService.lookupToolSource(anyString())).thenReturn(Optional.empty());

        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);
        ToolCallingObservationContext ctx = ctx("local-tool");

        filter.map(ctx);

        Map<String, String> attrs = highCardMap(ctx);
        assertThat(attrs)
                .containsEntry(McpToolObservationFilter.MCP_KIND, "in-process")
                .doesNotContainKey(McpToolObservationFilter.MCP_TRANSPORT)
                .doesNotContainKey(McpToolObservationFilter.MCP_SERVER);
    }

    @Test
    void ignoresNonToolCallingContexts() {
        McpClientService mcpClientService = mock(McpClientService.class);
        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);

        Observation.Context other = new Observation.Context();
        other.setName("some.other.observation");

        Observation.Context result = filter.map(other);

        assertThat(result).isSameAs(other);
        assertThat(result.getHighCardinalityKeyValues()).isEmpty();
        verifyNoInteractions(mcpClientService);
    }

    private static ToolCallingObservationContext ctx(String toolName) {
        return ToolCallingObservationContext.builder()
                .toolDefinition(DefaultToolDefinition.builder()
                        .name(toolName)
                        .description("")
                        .inputSchema("{\"type\":\"object\"}")
                        .build())
                .build();
    }

    private static Map<String, String> highCardMap(Observation.Context ctx) {
        Map<String, String> out = new HashMap<>();
        for (KeyValue kv : ctx.getHighCardinalityKeyValues()) {
            out.put(kv.getKey(), kv.getValue());
        }
        return out;
    }
}
