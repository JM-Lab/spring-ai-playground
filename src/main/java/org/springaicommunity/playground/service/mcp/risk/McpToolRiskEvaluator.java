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
package org.springaicommunity.playground.service.mcp.risk;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class McpToolRiskEvaluator {

    private final McpCatalogService catalogService;
    private final McpRiskInputsResolver inputsResolver;
    private final McpServerRiskCalculator serverCalc;
    private final McpToolPublishRiskCalculator publishCalc;
    private final McpToolRiskComposer composer;
    private final McpToolPoisoningScanner poisoningScanner;
    private final ObjectMapper objectMapper;
    private final ToolSpecService toolSpecService;
    private final SpringAiPlaygroundOptions playgroundOptions;
    private final McpRiskSignalSink sink;

    public McpToolRiskEvaluator(McpCatalogService catalogService, McpRiskInputsResolver inputsResolver,
            McpServerRiskCalculator serverCalc, McpToolPublishRiskCalculator publishCalc,
            McpToolRiskComposer composer, McpToolPoisoningScanner poisoningScanner,
            ObjectMapper objectMapper, ToolSpecService toolSpecService,
            SpringAiPlaygroundOptions playgroundOptions, McpRiskSignalSink sink) {
        this.catalogService = catalogService;
        this.inputsResolver = inputsResolver;
        this.serverCalc = serverCalc;
        this.publishCalc = publishCalc;
        this.composer = composer;
        this.poisoningScanner = poisoningScanner;
        this.objectMapper = objectMapper;
        this.toolSpecService = toolSpecService;
        this.playgroundOptions = playgroundOptions;
        this.sink = sink;
    }

    public record ServerRiskView(RiskLevel level, Map<String, Integer> axisScores, String floorTrigger) {}

    public record ToolRiskView(RiskLevel finalLevel, RiskLevel publishLevel, String floorTrigger,
            McpToolPoisoningScanner.ScanResult poisoningResult) {}

    public ServerRiskView evaluateServer(McpServerInfo info) {
        if (info == null || info.serverName() == null || info.serverName().isBlank()) {
            return new ServerRiskView(RiskLevel.L1, Map.of(), null);
        }
        if (isSelfBuiltin(info)) {
            return new ServerRiskView(RiskLevel.L0, Map.of(), null);
        }
        Optional<McpCatalogEntry> entry = this.catalogService.findByServerName(info.serverName());
        McpServerRiskCalculator.Inputs inputs = this.inputsResolver.resolveServerInputs(info, entry.orElse(null),
                entry.isEmpty(), false);
        McpServerRiskCalculator.Result result = this.serverCalc.compute(inputs);
        return new ServerRiskView(result.level(), result.axisScores(), result.floorTrigger());
    }

    public ToolRiskView evaluateTool(McpServerInfo info, String toolName, String description,
            Map<String, Map<String, Object>> propertySchemas) {
        McpToolPoisoningScanner.ScanResult scan = this.poisoningScanner.scan(description);
        if (info == null || toolName == null || info.serverName() == null) {
            return new ToolRiskView(RiskLevel.L1, RiskLevel.L1, null, scan);
        }
        emitPoisoningHit(info.serverName(), toolName, scan);
        if (isSelfBuiltin(info)) {
            RiskLevel sandbox = this.toolSpecService.getSandboxRiskLevel(toolName);
            return new ToolRiskView(sandbox, sandbox, null, scan);
        }
        Optional<McpCatalogEntry> entry = this.catalogService.findByServerName(info.serverName());
        McpServerRiskCalculator.Inputs serverInputs = this.inputsResolver.resolveServerInputs(info,
                entry.orElse(null), entry.isEmpty(), false);
        McpServerRiskCalculator.Result serverResult = this.serverCalc.compute(serverInputs);

        Optional<McpToolDescriptor> catalogTool = entry.flatMap(e -> e.tools().stream()
                .filter(t -> toolName.equals(t.name())).findFirst());
        JsonNode schemaNode = toSchemaNode(propertySchemas);
        boolean trustedServer = entry.isPresent() && !entry.get().trustSignals().isEmpty();
        McpToolPublishRiskCalculator.Inputs toolInputs = this.inputsResolver.resolveToolInputs(info.serverName(),
                toolName, description, schemaNode, catalogTool, trustedServer);
        McpToolPublishRiskCalculator.Result toolResult = this.publishCalc.compute(toolInputs);

        McpToolRiskComposer.Composed composed = this.composer.composeWrappedExternal(serverResult, toolResult);
        return new ToolRiskView(composed.finalLevel(), composed.publishLevel(), composed.floorTrigger(), scan);
    }

    public boolean isSelfBuiltin(McpServerInfo info) {
        return info != null
                && this.playgroundOptions.builtInMcpServer().name().equalsIgnoreCase(info.serverName());
    }

    private JsonNode toSchemaNode(Map<String, Map<String, Object>> propertySchemas) {
        if (propertySchemas == null || propertySchemas.isEmpty()) return null;
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", propertySchemas);
        return this.objectMapper.valueToTree(root);
    }

    private void emitPoisoningHit(String serverId, String toolName, McpToolPoisoningScanner.ScanResult scan) {
        if (scan == null || scan.clean() || scan.matches().isEmpty()) {
            return;
        }
        McpToolPoisoningScanner.Match first = scan.matches().getFirst();
        String patterns = scan.matches().stream()
                .map(m -> m.pattern().name()).distinct().collect(Collectors.joining(", "));
        this.sink.onPoisoningHit(new McpRiskEvents.PoisoningHit(Instant.now(), serverId, toolName,
                first.pattern().name(), first.matchedText(), scan.matches().size() + " match(es): " + patterns));
    }
}
