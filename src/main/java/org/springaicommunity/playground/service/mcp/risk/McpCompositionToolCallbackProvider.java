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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.util.SecretMasking;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class McpCompositionToolCallbackProvider implements ToolCallbackProvider {

    private static final Logger logger = LoggerFactory.getLogger(McpCompositionToolCallbackProvider.class);

    private final McpCompositionService compositionService;
    private final McpServerInfoService serverInfoService;
    private final McpClientService mcpClientService;
    private final McpCatalogService catalogService;
    private final McpRiskInputsResolver inputsResolver;
    private final McpServerRiskCalculator serverCalc;
    private final McpToolPublishRiskCalculator publishCalc;
    private final McpToolRiskComposer composer;
    private final McpToolHashLedger hashLedger;
    private final ObjectMapper objectMapper;

    public McpCompositionToolCallbackProvider(McpCompositionService compositionService,
            McpServerInfoService serverInfoService, McpClientService mcpClientService,
            McpCatalogService catalogService, McpRiskInputsResolver inputsResolver,
            McpServerRiskCalculator serverCalc, McpToolPublishRiskCalculator publishCalc,
            McpToolRiskComposer composer, McpToolHashLedger hashLedger, ObjectMapper objectMapper) {
        this.compositionService = compositionService;
        this.serverInfoService = serverInfoService;
        this.mcpClientService = mcpClientService;
        this.catalogService = catalogService;
        this.inputsResolver = inputsResolver;
        this.serverCalc = serverCalc;
        this.publishCalc = publishCalc;
        this.composer = composer;
        this.hashLedger = hashLedger;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> wrapped = new ArrayList<>();
        for (McpComposition composition : this.compositionService.getEnabled()) {
            for (McpComposition.Member member : composition.members()) {
                wrapMember(composition, member).ifPresent(wrapped::add);
            }
        }
        return wrapped.toArray(new ToolCallback[0]);
    }

    public ToolCallback[] getToolCallbacksFor(McpComposition composition) {
        List<ToolCallback> wrapped = new ArrayList<>();
        for (McpComposition.Member member : composition.members()) {
            wrapMember(composition, member).ifPresent(wrapped::add);
        }
        return wrapped.toArray(new ToolCallback[0]);
    }

    private Optional<ToolCallback> wrapMember(McpComposition composition, McpComposition.Member member) {
        Optional<McpServerInfo> serverInfo = findServerByName(member.serverId());
        if (serverInfo.isEmpty()) {
            logger.debug("Composition {} member {} skipped — server not registered",
                    composition.id(), member.serverId());
            return Optional.empty();
        }
        if (!this.mcpClientService.isConnecting(serverInfo.get())) {
            logger.debug("Composition {} member {} skipped — upstream not connected",
                    composition.id(), member.serverId());
            return Optional.empty();
        }
        Optional<ToolCallback> upstream = findUpstreamCallback(serverInfo.get(), member.toolName());
        if (upstream.isEmpty()) {
            logger.debug("Composition {} member {} skipped — tool {} not present upstream",
                    composition.id(), member.serverId(), member.toolName());
            return Optional.empty();
        }

        if (!integrityVerified(member.serverId(), member.toolName(), upstream.get())) {
            return Optional.empty();
        }

        McpToolRiskComposer.Composed risk = computeRisk(serverInfo.get(), member, upstream.get());
        Set<String> secrets = SecretMasking.collectFromTemplate(serverInfo.get().connectionAsJson());
        String transport = serverInfo.get().mcpTransportType() == null
                ? "" : serverInfo.get().mcpTransportType().name();

        return Optional.of(new WrappedExternalToolCallback(upstream.get(),
                composition.id(), composition.name(), member.exposedAlias(), member.descriptionOverride(),
                member.serverId(), transport, risk, secrets));
    }

    private Optional<McpServerInfo> findServerByName(String serverName) {
        if (serverName == null) return Optional.empty();
        return this.serverInfoService.read().stream()
                .filter(info -> serverName.equalsIgnoreCase(info.serverName()))
                .findFirst();
    }

    private Optional<ToolCallback> findUpstreamCallback(McpServerInfo serverInfo, String toolName) {
        if (toolName == null) return Optional.empty();
        List<ToolCallbackProvider> providers = this.mcpClientService.buildToolCallbackProviders(serverInfo);
        for (ToolCallbackProvider provider : providers) {
            for (ToolCallback cb : provider.getToolCallbacks()) {
                if (cb.getToolDefinition() != null && toolName.equals(cb.getToolDefinition().name())) {
                    return Optional.of(cb);
                }
            }
        }
        return Optional.empty();
    }

    private McpToolRiskComposer.Composed computeRisk(McpServerInfo serverInfo, McpComposition.Member member,
            ToolCallback upstream) {
        Optional<McpCatalogEntry> entry = this.catalogService.findByServerName(serverInfo.serverName());
        McpServerRiskCalculator.Inputs serverInputs = this.inputsResolver.resolveServerInputs(
                serverInfo, entry.orElse(null), entry.isEmpty(), false);
        McpServerRiskCalculator.Result serverResult = this.serverCalc.compute(serverInputs);

        Optional<McpToolDescriptor> catalogTool = entry.flatMap(e ->
                e.tools().stream().filter(t -> member.toolName().equals(t.name())).findFirst());
        String description = upstream.getToolDefinition() == null ? "" : upstream.getToolDefinition().description();
        McpToolPublishRiskCalculator.Inputs toolInputs = this.inputsResolver.resolveToolInputs(
                serverInfo.serverName(), member.toolName(), description, null, catalogTool);
        McpToolPublishRiskCalculator.Result toolResult = this.publishCalc.compute(toolInputs);

        return this.composer.composeWrappedExternal(serverResult, toolResult);
    }

    // TOFU: a re-published tool whose definition changed is withheld (AWAITING_REREVIEW) until explicit re-review.
    private boolean integrityVerified(String serverId, String toolName, ToolCallback upstream) {
        ToolDefinition def = upstream.getToolDefinition();
        JsonNode schema = parseSchema(def == null ? null : def.inputSchema());
        String hash = this.hashLedger.computeContentHash(def == null ? null : def.name(),
                def == null ? null : def.description(), schema, null);
        this.hashLedger.checkAndRecord(serverId, toolName, hash);
        McpToolHashLedger.Fingerprint.LifecycleStatus status = this.hashLedger.get(serverId, toolName)
                .map(McpToolHashLedger.Fingerprint::status)
                .orElse(McpToolHashLedger.Fingerprint.LifecycleStatus.ACTIVE);
        if (status != McpToolHashLedger.Fingerprint.LifecycleStatus.ACTIVE) {
            logger.warn("Exposed tool withheld — definition changed since approved (awaiting re-review): "
                    + "server={} tool={}", serverId, toolName);
            return false;
        }
        return true;
    }

    private JsonNode parseSchema(String inputSchema) {
        if (inputSchema == null || inputSchema.isBlank()) return null;
        try {
            return this.objectMapper.readTree(inputSchema);
        } catch (JacksonException e) {
            logger.debug("Failed to parse upstream tool inputSchema for hashing; treating as empty", e);
            return null;
        }
    }
}
