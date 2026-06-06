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

import org.springaicommunity.playground.service.mcp.catalog.McpRiskFactors;
import org.springaicommunity.playground.service.mcp.catalog.McpTrustSignal;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class McpServerRiskCalculator {

    static final String AXIS_TRANSPORT = "transport";
    static final String AXIS_AUTH = "auth";
    static final String AXIS_TRUST = "trust";
    static final String AXIS_DOC = "doc";

    static final String FLOOR_NO_AUTH_WRITE = "non_loopback_no_auth_write_capability";
    static final String FLOOR_NO_AUTH_UNKNOWN_TRUST = "non_loopback_no_auth_trust_unknown";
    static final String FLOOR_PRIVILEGED_OAUTH_SCOPE = "privileged_oauth_scope";

    static final int DOC_CAP = 2;

    private final McpRiskSignalSink sink;

    public McpServerRiskCalculator(McpRiskSignalSink sink) {
        this.sink = sink;
    }

    public record Inputs(
            String serverId,
            String displayName,
            McpTransportType transportType,
            String url,
            McpAuthMode authMode,
            Set<McpTrustSignal> trustSignals,
            boolean userTypedUrl,
            boolean userTypedHostMatchesCatalogPattern,
            DocCompleteness docCompleteness,
            McpRiskFactors aggregateRiskFactors,
            List<String> oauthScopes) {

        public Inputs {
            trustSignals = trustSignals == null ? Set.of() : Set.copyOf(trustSignals);
            oauthScopes = oauthScopes == null ? List.of() : List.copyOf(oauthScopes);
            if (aggregateRiskFactors == null) aggregateRiskFactors = McpRiskFactors.SAFE_READ_ONLY;
            if (docCompleteness == null) docCompleteness = DocCompleteness.fullyComplete();
            if (authMode == null) authMode = McpAuthMode.NONE;
        }
    }

    public record DocCompleteness(
            boolean docsUrlPresent,
            boolean docsAdequate,
            boolean recentlyUsedWithin90Days) {

        public static DocCompleteness fullyComplete() {
            return new DocCompleteness(true, true, true);
        }

        public int gapCount() {
            int gaps = 0;
            if (!docsUrlPresent) gaps++;
            if (!docsAdequate) gaps++;
            if (!recentlyUsedWithin90Days) gaps++;
            return gaps;
        }
    }

    public record Result(RiskLevel level, int totalScore, Map<String, Integer> axisScores, String floorTrigger) {

        public Result {
            axisScores = axisScores == null ? Map.of() : Map.copyOf(axisScores);
        }
    }

    public Result compute(Inputs inputs) {
        String floorTrigger = detectFloorOverride(inputs);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(AXIS_TRANSPORT, transportScore(inputs));
        scores.put(AXIS_AUTH, authScore(inputs));
        scores.put(AXIS_TRUST, trustScore(inputs));
        scores.put(AXIS_DOC, Math.min(DOC_CAP, inputs.docCompleteness().gapCount()));

        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        RiskLevel level = floorTrigger != null ? RiskLevel.L5 : bucket(total);

        if (floorTrigger != null) {
            sink.onFloorOverrideTriggered(new McpRiskEvents.FloorOverrideTriggered(
                    Instant.now(), AXIS_AUTH, floorTrigger, inputs.serverId(), null, level, null));
        }
        sink.onServerRiskComputed(new McpRiskEvents.ServerRiskComputed(
                Instant.now(), inputs.serverId(), inputs.displayName(),
                scores, total, level, floorTrigger));
        return new Result(level, total, scores, floorTrigger);
    }

    static RiskLevel bucket(int score) {
        if (score <= 0) return RiskLevel.L1;
        if (score == 1) return RiskLevel.L2;
        if (score == 2) return RiskLevel.L3;
        if (score == 3) return RiskLevel.L4;
        return RiskLevel.L5;
    }

    static int transportScore(Inputs in) {
        McpHostClassifier.HostClass hostClass = McpHostClassifier.classify(in.transportType(), in.url());
        return hostClass == McpHostClassifier.HostClass.PRIVATE_LAN ? 1 : 0;
    }

    static int authScore(Inputs in) {
        McpHostClassifier.HostClass hostClass = McpHostClassifier.classify(in.transportType(), in.url());
        if (hostClass == McpHostClassifier.HostClass.STDIO || hostClass == McpHostClassifier.HostClass.LOOPBACK) {
            return 0;
        }
        return switch (in.authMode()) {
            case OAUTH_STANDARD, NONE -> 0;
            case API_KEY, BEARER -> 1;
            case OAUTH_CUSTOM -> 2;
        };
    }

    static int trustScore(Inputs in) {
        Set<McpTrustSignal> signals = in.trustSignals();
        if (in.userTypedUrl()) {
            return in.userTypedHostMatchesCatalogPattern() ? 1 : 2;
        }
        if (signals.contains(McpTrustSignal.VENDOR_OFFICIAL)) return 0;
        if (signals.contains(McpTrustSignal.COMMUNITY_CURATED)) return 1;
        return 2;
    }

    String detectFloorOverride(Inputs in) {
        McpHostClassifier.HostClass hostClass = McpHostClassifier.classify(in.transportType(), in.url());
        boolean nonLoopback = hostClass != McpHostClassifier.HostClass.STDIO
                && hostClass != McpHostClassifier.HostClass.LOOPBACK;
        if (nonLoopback && in.authMode() == McpAuthMode.NONE) {
            if (in.aggregateRiskFactors() != null && in.aggregateRiskFactors().writeCapability()) {
                return FLOOR_NO_AUTH_WRITE;
            }
            boolean trustUnknown = !in.trustSignals().contains(McpTrustSignal.VENDOR_OFFICIAL)
                    && !in.trustSignals().contains(McpTrustSignal.COMMUNITY_CURATED)
                    && (in.userTypedUrl() && !in.userTypedHostMatchesCatalogPattern());
            if (trustUnknown) return FLOOR_NO_AUTH_UNKNOWN_TRUST;
        }
        if (hasPrivilegedScope(in.oauthScopes())) return FLOOR_PRIVILEGED_OAUTH_SCOPE;
        return null;
    }

    static boolean hasPrivilegedScope(List<String> scopes) {
        if (scopes == null) return false;
        for (String scope : scopes) {
            if (scope == null) continue;
            String s = scope.toLowerCase(Locale.ROOT);
            if (s.contains("admin") || s.contains("write_all") || s.contains("delete_")
                    || s.contains(".all") || s.endsWith(".admin")) {
                return true;
            }
        }
        return false;
    }
}
