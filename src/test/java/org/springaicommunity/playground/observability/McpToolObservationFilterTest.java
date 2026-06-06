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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springaicommunity.playground.service.mcp.risk.McpRiskMdcKeys;
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

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

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
    void wrappedExternalOriginUsesMdcDimensionsWithoutClientLookup() {
        McpClientService mcpClientService = mock(McpClientService.class);
        MDC.put(McpRiskMdcKeys.ORIGIN, McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL);
        MDC.put(McpRiskMdcKeys.UPSTREAM_SERVER, "GitHub");
        MDC.put(McpRiskMdcKeys.UPSTREAM_TRANSPORT, "streamable_http");
        MDC.put(McpRiskMdcKeys.COMPOSITION_ID, "comp-1");
        MDC.put(McpRiskMdcKeys.COMPOSITION_NAME, "dev-toolbox");
        MDC.put(McpRiskMdcKeys.EXPOSED_ALIAS, "github__create_issue");
        MDC.put(McpRiskMdcKeys.RISK_FINAL, "L3");
        MDC.put(McpRiskMdcKeys.RISK_SERVER, "L1");
        MDC.put(McpRiskMdcKeys.RISK_PUBLISH, "L2");

        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);
        ToolCallingObservationContext ctx = ctx("github__create_issue");

        filter.map(ctx);

        Map<String, String> attrs = highCardMap(ctx);
        assertThat(attrs)
                .containsEntry(McpToolObservationFilter.MCP_KIND, "mcp")
                .containsEntry(McpToolObservationFilter.MCP_ORIGIN, McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL)
                .containsEntry(McpToolObservationFilter.MCP_TRANSPORT, "streamable_http")
                .containsEntry(McpToolObservationFilter.MCP_SERVER, "GitHub")
                .containsEntry(McpToolObservationFilter.MCP_COMPOSITION_ID, "comp-1")
                .containsEntry(McpToolObservationFilter.MCP_COMPOSITION_NAME, "dev-toolbox")
                .containsEntry(McpToolObservationFilter.MCP_TOOL_EXPOSED_ALIAS, "github__create_issue")
                .containsEntry(McpToolObservationFilter.MCP_RISK_FINAL, "L3")
                .containsEntry(McpToolObservationFilter.MCP_RISK_SERVER, "L1")
                .containsEntry(McpToolObservationFilter.MCP_RISK_PUBLISH, "L2")
                .doesNotContainKey(McpToolObservationFilter.MCP_RISK_FLOOR_TRIGGER);
        verifyNoInteractions(mcpClientService);
    }

    @Test
    void internalJsOriginTagsRiskDimensionsFromMdc() {
        McpClientService mcpClientService = mock(McpClientService.class);
        when(mcpClientService.lookupToolSource(anyString())).thenReturn(Optional.empty());
        MDC.put(McpRiskMdcKeys.ORIGIN, McpRiskMdcKeys.ORIGIN_INTERNAL_JS);
        MDC.put(McpRiskMdcKeys.RISK_FINAL, "L0");

        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);
        ToolCallingObservationContext ctx = ctx("add_numbers");

        filter.map(ctx);

        Map<String, String> attrs = highCardMap(ctx);
        assertThat(attrs)
                .containsEntry(McpToolObservationFilter.MCP_KIND, "in-process")
                .containsEntry(McpToolObservationFilter.MCP_ORIGIN, McpRiskMdcKeys.ORIGIN_INTERNAL_JS)
                .containsEntry(McpToolObservationFilter.MCP_RISK_FINAL, "L0");
    }

    @Test
    void floorTriggerEmitsWhenPresent() {
        McpClientService mcpClientService = mock(McpClientService.class);
        MDC.put(McpRiskMdcKeys.ORIGIN, McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL);
        MDC.put(McpRiskMdcKeys.UPSTREAM_SERVER, "Unknown");
        MDC.put(McpRiskMdcKeys.RISK_FINAL, "L5");
        MDC.put(McpRiskMdcKeys.RISK_SERVER, "L5");
        MDC.put(McpRiskMdcKeys.RISK_PUBLISH, "L1");
        MDC.put(McpRiskMdcKeys.FLOOR_TRIGGER, "non_loopback_no_auth_write_capability");

        McpToolObservationFilter filter = new McpToolObservationFilter(mcpClientService);
        ToolCallingObservationContext ctx = ctx("write_anything");

        filter.map(ctx);

        Map<String, String> attrs = highCardMap(ctx);
        assertThat(attrs)
                .containsEntry(McpToolObservationFilter.MCP_RISK_FLOOR_TRIGGER,
                        "non_loopback_no_auth_write_capability");
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
