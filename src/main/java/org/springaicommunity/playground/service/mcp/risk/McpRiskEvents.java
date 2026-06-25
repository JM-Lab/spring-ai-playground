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

import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class McpRiskEvents {

    public static final class Types {

        public static final String SERVER_RISK_COMPUTED = "server-risk-computed";
        public static final String TOOL_PUBLISH_RISK_COMPUTED = "tool-publish-risk-computed";
        public static final String FLOOR_OVERRIDE_TRIGGERED = "floor-override-triggered";
        public static final String HASH_LEDGER_MISMATCH = "hash-ledger-mismatch";
        public static final String COMPOSITION_LIFECYCLE = "composition-lifecycle";
        public static final String POISONING_HIT = "poisoning-hit";

        private Types() {}
    }

    public record ServerRiskComputed(
            Instant at,
            String serverId,
            String displayName,
            Map<String, Integer> axisScores,
            int totalScore,
            RiskLevel level,
            String floorTrigger) {}

    public record ToolPublishRiskComputed(
            Instant at,
            String serverId,
            String toolName,
            Map<String, Integer> axisScores,
            int totalScore,
            RiskLevel level,
            String floorTrigger) {}

    public record FloorOverrideTriggered(
            Instant at,
            String layer,
            String trigger,
            String serverId,
            String toolName,
            RiskLevel resultingLevel,
            String details) {}

    public record HashLedgerMismatch(
            Instant at,
            String serverId,
            String toolName,
            String previousHash,
            String currentHash,
            List<String> changedFields) {}

    public record CompositionLifecycle(
            Instant at,
            String compositionId,
            String compositionName,
            Action action,
            String reason) {

        public enum Action { CREATED, UPDATED, DELETED, ENABLED, DISABLED, AUTO_DISABLED }
    }

    public record PoisoningHit(
            Instant at,
            String serverId,
            String toolName,
            String patternId,
            String matchedText,
            String details) {}

    private McpRiskEvents() {}
}
