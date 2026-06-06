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

import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.catalog.McpRiskFactors;
import org.springaicommunity.playground.service.mcp.catalog.McpTrustSignal;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class McpRegistrationRiskPreview {

    private final McpCatalogService catalogService;
    private final McpServerRiskCalculator serverCalc;

    public McpRegistrationRiskPreview(McpCatalogService catalogService, McpServerRiskCalculator serverCalc) {
        this.catalogService = catalogService;
        this.serverCalc = serverCalc;
    }

    public record WizardInput(
            String displayName,
            McpTransportType transport,
            String url,
            McpAuthMode authMode,
            String maintainerName,
            String dataHandlingPolicyText,
            String credentialStoragePolicyText,
            boolean toolsHaveDescription,
            boolean toolsHaveInputSchema,
            McpRiskFactors aggregateRiskFactors,
            List<String> oauthScopes) {}

    public record PreviewResult(
            McpServerRiskCalculator.Result serverResult,
            Set<McpTrustSignal> resolvedTrustSignals,
            boolean catalogMatched,
            String catalogId,
            Map<String, String> rationale) {}

    public PreviewResult preview(WizardInput input) {
        Optional<McpCatalogEntry> catalogMatch = findCatalogMatch(input);
        Set<McpTrustSignal> trust = resolveTrustSignals(input, catalogMatch);
        boolean userTyped = catalogMatch.isEmpty();
        boolean userTypedKnownPattern = userTyped && hostMatchesKnownCatalog(input.url());

        boolean docsUrlPresent = catalogMatch.map(e -> notBlank(e.docsUrl())).orElse(false);
        boolean docsAdequate = catalogMatch.map(McpCatalogEntry::docsAdequate).orElse(false);
        boolean recentlyUsed = true;
        McpServerRiskCalculator.DocCompleteness doc = new McpServerRiskCalculator.DocCompleteness(
                docsUrlPresent, docsAdequate, recentlyUsed);

        McpServerRiskCalculator.Inputs calcInputs = new McpServerRiskCalculator.Inputs(
                catalogMatch.map(McpCatalogEntry::id).orElse(input.displayName()),
                input.displayName(),
                input.transport(),
                input.url(),
                input.authMode(),
                trust,
                userTyped,
                userTypedKnownPattern,
                doc,
                input.aggregateRiskFactors() == null ? McpRiskFactors.SAFE_READ_ONLY : input.aggregateRiskFactors(),
                input.oauthScopes() == null ? List.of() : input.oauthScopes());

        McpServerRiskCalculator.Result result = serverCalc.compute(calcInputs);

        Map<String, String> rationale = buildRationale(input, catalogMatch.isPresent(), result);
        return new PreviewResult(result, trust, catalogMatch.isPresent(),
                catalogMatch.map(McpCatalogEntry::id).orElse(null), rationale);
    }

    Optional<McpCatalogEntry> findCatalogMatch(WizardInput input) {
        // STDIO carries no host, so match by server name like the runtime resolver does.
        if (input.transport() == McpTransportType.STDIO) {
            return notBlank(input.displayName())
                    ? catalogService.findByServerName(input.displayName().trim())
                    : Optional.empty();
        }
        if (input.url() == null) return Optional.empty();
        String host = McpHostClassifier.extractHost(input.url());
        if (host == null) return Optional.empty();
        for (McpCatalogEntry entry : catalogService.getCatalog()) {
            for (McpCatalogEntry.TransportSpec transport : entry.transports()) {
                String catalogHost = McpHostClassifier.extractHost(transport.urlTemplate());
                if (catalogHost != null && catalogHost.equalsIgnoreCase(host)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    Set<McpTrustSignal> resolveTrustSignals(WizardInput input, Optional<McpCatalogEntry> catalogMatch) {
        if (catalogMatch.isPresent()) {
            return McpTrustSignal.parseAll(catalogMatch.get().trustSignals());
        }
        return Set.of();
    }

    boolean hostMatchesKnownCatalog(String url) {
        if (url == null) return false;
        String host = McpHostClassifier.extractHost(url);
        if (host == null) return false;
        for (McpCatalogEntry entry : catalogService.getCatalog()) {
            for (McpCatalogEntry.TransportSpec transport : entry.transports()) {
                String catalogHost = McpHostClassifier.extractHost(transport.urlTemplate());
                if (catalogHost != null && catalogHost.equalsIgnoreCase(host)) return true;
            }
        }
        return false;
    }

    Map<String, String> buildRationale(WizardInput input, boolean catalogMatched,
            McpServerRiskCalculator.Result result) {
        Map<String, String> rationale = new LinkedHashMap<>();
        McpHostClassifier.HostClass hostClass = McpHostClassifier.classify(input.transport(), input.url());
        rationale.put("transport", hostClass.name().toLowerCase() + " ("
                + result.axisScores().getOrDefault(McpServerRiskCalculator.AXIS_TRANSPORT, 0) + ")");
        rationale.put("auth", input.authMode().name().toLowerCase() + " ("
                + result.axisScores().getOrDefault(McpServerRiskCalculator.AXIS_AUTH, 0) + ")");
        rationale.put("trust", (catalogMatched ? "catalog-matched" : "user-typed") + " ("
                + result.axisScores().getOrDefault(McpServerRiskCalculator.AXIS_TRUST, 0) + ")");
        rationale.put("doc", "completeness penalty ("
                + result.axisScores().getOrDefault(McpServerRiskCalculator.AXIS_DOC, 0) + ")");
        if (result.floorTrigger() != null) {
            rationale.put("floor", result.floorTrigger() + " → " + RiskLevel.L5);
        }
        return rationale;
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
