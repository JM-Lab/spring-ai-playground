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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springaicommunity.playground.service.mcp.risk.McpRiskMdcKeys;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class McpToolObservationFilter implements ObservationFilter {

    public static final String TOOL_RISK_COUNTER = "saip.tool.risk";
    public static final String NETWORK_TRANSPORT = "network.transport";
    public static final String NETWORK_PROTOCOL_NAME = "network.protocol.name";
    public static final String MCP_METHOD_NAME = "mcp.method.name";
    public static final String MCP_SERVER = "saip.mcp.server";
    public static final String MCP_ORIGIN = McpRiskMdcKeys.ORIGIN;
    public static final String MCP_COMPOSITION_ID = McpRiskMdcKeys.COMPOSITION_ID;
    public static final String MCP_COMPOSITION_NAME = McpRiskMdcKeys.COMPOSITION_NAME;
    public static final String MCP_TOOL_EXPOSED_ALIAS = McpRiskMdcKeys.EXPOSED_ALIAS;
    public static final String MCP_RISK_FINAL = McpRiskMdcKeys.RISK_FINAL;
    public static final String MCP_RISK_SERVER = McpRiskMdcKeys.RISK_SERVER;
    public static final String MCP_RISK_PUBLISH = McpRiskMdcKeys.RISK_PUBLISH;
    public static final String MCP_RISK_FLOOR_TRIGGER = McpRiskMdcKeys.FLOOR_TRIGGER;

    static final String TOOLS_CALL_METHOD = "tools/call";

    private final McpClientService mcpClientService;
    private final MeterRegistry meterRegistry;

    public McpToolObservationFilter(McpClientService mcpClientService, MeterRegistry meterRegistry) {
        this.mcpClientService = mcpClientService;
        this.meterRegistry = meterRegistry;
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

        String origin = MDC.get(McpRiskMdcKeys.ORIGIN);
        if (McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL.equals(origin)) {
            context.addHighCardinalityKeyValue(KeyValue.of(MCP_METHOD_NAME, TOOLS_CALL_METHOD));
            context.addHighCardinalityKeyValue(KeyValue.of(MCP_ORIGIN, origin));
            addTransport(context, MDC.get(McpRiskMdcKeys.UPSTREAM_TRANSPORT));
            addNonBlank(context, MCP_SERVER, MDC.get(McpRiskMdcKeys.UPSTREAM_SERVER));
            addCompositionAndRiskDimensions(context);
            countToolRisk(MDC.get(McpRiskMdcKeys.RISK_FINAL));
            return context;
        }

        Optional<McpToolSource> source = mcpClientService.lookupToolSource(toolName);
        if (source.isEmpty()) {
            context.addHighCardinalityKeyValue(KeyValue.of(MCP_ORIGIN,
                    origin == null || origin.isBlank() ? McpRiskMdcKeys.ORIGIN_INTERNAL_JS : origin));
            addCompositionAndRiskDimensions(context);
            return context;
        }
        McpToolSource s = source.get();
        context.addHighCardinalityKeyValue(KeyValue.of(MCP_METHOD_NAME, TOOLS_CALL_METHOD));
        addTransport(context, s.transport());
        context.addHighCardinalityKeyValue(KeyValue.of(MCP_SERVER, s.serverName()));
        if (origin != null && !origin.isBlank()) {
            context.addHighCardinalityKeyValue(KeyValue.of(MCP_ORIGIN, origin));
        }
        String rf = TraceRecord.firstNonBlank(MDC.get(McpRiskMdcKeys.RISK_FINAL), s.riskFinal());
        addNonBlank(context, MCP_RISK_FINAL, rf);
        addCompositionAndRiskDimensions(context);
        countToolRisk(rf);
        return context;
    }

    private static void addCompositionAndRiskDimensions(Observation.Context context) {
        addNonBlank(context, MCP_COMPOSITION_ID, MDC.get(McpRiskMdcKeys.COMPOSITION_ID));
        addNonBlank(context, MCP_COMPOSITION_NAME, MDC.get(McpRiskMdcKeys.COMPOSITION_NAME));
        addNonBlank(context, MCP_TOOL_EXPOSED_ALIAS, MDC.get(McpRiskMdcKeys.EXPOSED_ALIAS));
        addNonBlank(context, MCP_RISK_FINAL, MDC.get(McpRiskMdcKeys.RISK_FINAL));
        addNonBlank(context, MCP_RISK_SERVER, MDC.get(McpRiskMdcKeys.RISK_SERVER));
        addNonBlank(context, MCP_RISK_PUBLISH, MDC.get(McpRiskMdcKeys.RISK_PUBLISH));
        addNonBlank(context, MCP_RISK_FLOOR_TRIGGER, MDC.get(McpRiskMdcKeys.FLOOR_TRIGGER));
    }

    private void countToolRisk(String level) {
        if (level == null || level.isBlank()) return;
        this.meterRegistry.counter(TOOL_RISK_COUNTER, "level", level).increment();
    }

    private static void addNonBlank(Observation.Context context, String key, String value) {
        if (value == null || value.isBlank()) return;
        context.addHighCardinalityKeyValue(KeyValue.of(key, value));
    }

    private static void addTransport(Observation.Context context, String rawTransport) {
        if (rawTransport == null || rawTransport.isBlank()) return;
        String normalized = rawTransport.trim().toLowerCase().replace('-', '_');
        switch (normalized) {
            case "stdio" -> context.addHighCardinalityKeyValue(KeyValue.of(NETWORK_TRANSPORT, "pipe"));
            case "streamable_http", "sse" -> {
                context.addHighCardinalityKeyValue(KeyValue.of(NETWORK_TRANSPORT, "tcp"));
                context.addHighCardinalityKeyValue(KeyValue.of(NETWORK_PROTOCOL_NAME, "http"));
            }
            default -> context.addHighCardinalityKeyValue(KeyValue.of(NETWORK_TRANSPORT, normalized));
        }
    }
}
