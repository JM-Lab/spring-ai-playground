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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpRiskFactors;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.mcp.catalog.McpTrustSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class McpRiskInputsResolver {

    static final Duration FRESH_USAGE_WINDOW = Duration.ofDays(90);

    private final ObjectMapper objectMapper;

    public McpRiskInputsResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public McpServerRiskCalculator.Inputs resolveServerInputs(McpServerInfo serverInfo, McpCatalogEntry entry,
            boolean userTyped, boolean userTypedMatchesCatalog) {
        String url = extractUrl(serverInfo.connectionAsJson());
        McpAuthMode authMode = resolveAuthMode(serverInfo, entry);
        Set<McpTrustSignal> trustSignals = entry == null ? Set.of() : McpTrustSignal.parseAll(entry.trustSignals());
        McpRiskFactors aggregate = entry == null ? McpRiskFactors.SAFE_READ_ONLY : entry.serverDerivedRiskFactors();
        McpServerRiskCalculator.DocCompleteness doc = docCompletenessOf(serverInfo, entry);
        List<String> oauthScopes = resolveOauthScopes(entry);
        return new McpServerRiskCalculator.Inputs(
                serverInfo.serverName(),
                serverInfo.serverName(),
                serverInfo.mcpTransportType(),
                url,
                authMode,
                trustSignals,
                userTyped,
                userTypedMatchesCatalog,
                doc,
                aggregate,
                oauthScopes);
    }

    public McpToolPublishRiskCalculator.Inputs resolveToolInputs(String serverId, String toolName,
            String toolDescription, JsonNode inputSchema, Optional<McpToolDescriptor> catalogToolOpt) {
        return resolveToolInputs(serverId, toolName, toolDescription, inputSchema, catalogToolOpt, false);
    }

    public McpToolPublishRiskCalculator.Inputs resolveToolInputs(String serverId, String toolName,
            String toolDescription, JsonNode inputSchema, Optional<McpToolDescriptor> catalogToolOpt,
            boolean trustedServer) {
        McpToolDescriptor catalogTool = catalogToolOpt.orElse(null);
        McpToolDescriptor.Annotations annotations = catalogTool == null
                ? McpToolDescriptor.Annotations.EMPTY : catalogTool.annotations();
        McpRiskFactors riskFactors = catalogTool == null
                ? McpRiskFactors.SAFE_READ_ONLY : catalogTool.riskFactors();

        boolean schemaPresent = inputSchema != null && !inputSchema.isMissingNode();
        double propCoverage = computePropertyDescriptionCoverage(inputSchema);

        McpToolPublishRiskCalculator.SideEffectScope scope = catalogTool == null
                ? McpToolPublishRiskCalculator.SideEffectScope.UNKNOWN
                : inferSideEffectScope(riskFactors);
        boolean sendsUserData = riskFactors.sendsUserData();

        // Curator checklist is a catalog-author metric; waived for live external tools (no descriptor).
        McpToolPublishRiskCalculator.CuratorChecklist checklist =
                McpToolPublishRiskCalculator.CuratorChecklist.fullyComplete();

        return new McpToolPublishRiskCalculator.Inputs(
                serverId,
                toolName,
                toolDescription,
                schemaPresent,
                propCoverage,
                annotations,
                riskFactors,
                scope,
                sendsUserData,
                checklist,
                trustedServer);
    }

    String extractUrl(String connectionJson) {
        if (connectionJson == null || connectionJson.isBlank()) return null;
        try {
            JsonNode node = this.objectMapper.readTree(connectionJson);
            JsonNode url = node.get("url");
            if (url != null && !url.isNull()) return url.asText();
            JsonNode endpoint = node.get("endpoint");
            if (endpoint != null && !endpoint.isNull()) return endpoint.asText();
        } catch (Exception ignored) {
            // best effort
        }
        return null;
    }

    McpAuthMode resolveAuthMode(McpServerInfo serverInfo, McpCatalogEntry entry) {
        if (entry == null || entry.transports().isEmpty()) {
            return inferFromConnectionJson(serverInfo.connectionAsJson());
        }
        McpCatalogEntry.TransportSpec transport = entry.transports().getFirst();
        return McpAuthClassifier.fromCatalogAuth(transport.auth(), transport.oauthDefaults());
    }

    McpAuthMode inferFromConnectionJson(String connectionJson) {
        if (connectionJson == null) return McpAuthMode.NONE;
        try {
            JsonNode node = this.objectMapper.readTree(connectionJson);
            if (node.has("oauth") && !node.get("oauth").isNull()) {
                return McpAuthMode.OAUTH_STANDARD;
            }
            JsonNode headers = node.get("headers");
            if (headers != null && headers.fieldNames().hasNext()) {
                return McpAuthMode.BEARER;
            }
        } catch (Exception ignored) {
            // best effort
        }
        return McpAuthMode.NONE;
    }

    McpServerRiskCalculator.DocCompleteness docCompletenessOf(McpServerInfo serverInfo, McpCatalogEntry entry) {
        boolean docsUrlPresent = entry != null && notBlank(entry.docsUrl());
        boolean docsAdequate = entry != null && entry.docsAdequate();
        boolean recentlyUsed = serverInfo != null
                && serverInfo.lastUsedAtEpochMs() != null
                && Instant.ofEpochMilli(serverInfo.lastUsedAtEpochMs())
                .isAfter(Instant.now().minus(FRESH_USAGE_WINDOW));
        if (serverInfo != null && serverInfo.lastUsedAtEpochMs() == null) {
            recentlyUsed = true;
        }
        return new McpServerRiskCalculator.DocCompleteness(docsUrlPresent, docsAdequate, recentlyUsed);
    }

    List<String> resolveOauthScopes(McpCatalogEntry entry) {
        if (entry == null) return List.of();
        List<String> scopes = new ArrayList<>();
        for (McpCatalogEntry.TransportSpec t : entry.transports()) {
            if (t.oauthDefaults() != null && t.oauthDefaults().scopes() != null) {
                scopes.addAll(t.oauthDefaults().scopes());
            }
        }
        return scopes;
    }

    McpToolPublishRiskCalculator.SideEffectScope inferSideEffectScope(McpRiskFactors riskFactors) {
        if (riskFactors == null) return McpToolPublishRiskCalculator.SideEffectScope.UNKNOWN;
        if (riskFactors.execCapability() || riskFactors.irreversibleActions()) {
            return McpToolPublishRiskCalculator.SideEffectScope.REMOTE_ADMIN_SCOPE;
        }
        if (riskFactors.writeCapability()) {
            return McpToolPublishRiskCalculator.SideEffectScope.REMOTE_WRITE_SCOPE;
        }
        return McpToolPublishRiskCalculator.SideEffectScope.LOCAL_ONLY;
    }

    double computePropertyDescriptionCoverage(JsonNode inputSchema) {
        if (inputSchema == null) return 1.0;
        JsonNode properties = inputSchema.get("properties");
        if (properties == null || !properties.isObject()) return 1.0;
        int total = 0;
        int withDescription = 0;
        var fields = properties.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            total++;
            JsonNode desc = entry.getValue().get("description");
            if (desc != null && !desc.isNull() && !desc.asText().isBlank()) withDescription++;
        }
        return total == 0 ? 1.0 : (double) withDescription / total;
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
