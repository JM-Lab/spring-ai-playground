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
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class McpToolPublishRiskCalculator {

    static final String AXIS_BASE_ACTION = "base_action";
    static final String AXIS_DOC_PENALTY = "doc_penalty";

    static final String FLOOR_IRREVERSIBLE_VERB = "irreversible_verb_in_tool_name";
    static final String FLOOR_DESTRUCTIVE_NON_IDEMPOTENT = "destructive_and_non_idempotent";
    static final String FLOOR_SEVERE_FAILURE = "description_missing_and_annotations_missing";

    static final int DOC_CAP = 3;

    static final List<Pattern> IRREVERSIBLE_VERB_PATTERNS = List.of(
            Pattern.compile("(?i)^delete[_-]"),
            Pattern.compile("(?i)^drop[_-]"),
            Pattern.compile("(?i)^purge[_-]"),
            Pattern.compile("(?i)^wipe[_-]"),
            Pattern.compile("(?i)^remove[_-]"),
            Pattern.compile("(?i)force[_-]?push"));

    static final Pattern BOILERPLATE_DESC = Pattern.compile(
            "(?i)^(returns?|gets?|does|performs)\\s+");

    private final McpRiskSignalSink sink;

    public McpToolPublishRiskCalculator(McpRiskSignalSink sink) {
        this.sink = sink;
    }

    public record Inputs(
            String serverId,
            String toolName,
            String description,
            boolean inputSchemaPresent,
            double inputSchemaPropertyDescriptionCoverage,
            McpToolDescriptor.Annotations annotations,
            McpRiskFactors riskFactors,
            SideEffectScope sideEffectScope,
            boolean sendsUserData,
            CuratorChecklist curatorChecklist,
            boolean trustedServer) {

        public Inputs {
            if (annotations == null) annotations = McpToolDescriptor.Annotations.EMPTY;
            if (riskFactors == null) riskFactors = McpRiskFactors.SAFE_READ_ONLY;
            if (sideEffectScope == null) sideEffectScope = SideEffectScope.UNKNOWN;
            if (curatorChecklist == null) curatorChecklist = CuratorChecklist.fullyComplete();
            if (description == null) description = "";
            if (toolName == null) toolName = "";
        }

        public Inputs(String serverId, String toolName, String description, boolean inputSchemaPresent,
                double inputSchemaPropertyDescriptionCoverage, McpToolDescriptor.Annotations annotations,
                McpRiskFactors riskFactors, SideEffectScope sideEffectScope, boolean sendsUserData,
                CuratorChecklist curatorChecklist) {
            this(serverId, toolName, description, inputSchemaPresent, inputSchemaPropertyDescriptionCoverage,
                    annotations, riskFactors, sideEffectScope, sendsUserData, curatorChecklist, false);
        }
    }

    public enum SideEffectScope { LOCAL_ONLY, REMOTE_WRITE_SCOPE, REMOTE_ADMIN_SCOPE, UNKNOWN }

    public record CuratorChecklist(
            boolean sideEffectTypeSpecified,
            boolean externalDataFlowSpecified,
            boolean reversibilitySpecified) {

        public static CuratorChecklist fullyComplete() {
            return new CuratorChecklist(true, true, true);
        }

        public int gapCount() {
            int gaps = 0;
            if (!sideEffectTypeSpecified) gaps++;
            if (!externalDataFlowSpecified) gaps++;
            if (!reversibilitySpecified) gaps++;
            return gaps;
        }
    }

    public record Result(RiskLevel level, int totalScore, Map<String, Integer> axisScores, String floorTrigger) {

        public Result {
            axisScores = axisScores == null ? Map.of() : Map.copyOf(axisScores);
        }
    }

    public Result compute(Inputs in) {
        String floorTrigger = detectFloorOverride(in);

        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(AXIS_BASE_ACTION, baseActionScore(in));
        // Trusted servers' curation substitutes for per-tool docs, so the doc penalty is waived.
        scores.put(AXIS_DOC_PENALTY, in.trustedServer() ? 0 : Math.min(DOC_CAP, docPenalty(in)));

        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        RiskLevel level;
        if (FLOOR_SEVERE_FAILURE.equals(floorTrigger)) {
            level = RiskLevel.L4;
        } else if (floorTrigger != null) {
            level = RiskLevel.L5;
        } else {
            level = bucket(total);
        }

        if (floorTrigger != null) {
            sink.onFloorOverrideTriggered(new McpRiskEvents.FloorOverrideTriggered(
                    Instant.now(), "publish", floorTrigger, in.serverId(), in.toolName(), level, null));
        }
        sink.onToolPublishRiskComputed(new McpRiskEvents.ToolPublishRiskComputed(
                Instant.now(), in.serverId(), in.toolName(), scores, total, level, floorTrigger));
        return new Result(level, total, scores, floorTrigger);
    }

    static RiskLevel bucket(int score) {
        if (score <= 0) return RiskLevel.L1;
        if (score == 1) return RiskLevel.L2;
        if (score == 2) return RiskLevel.L3;
        if (score == 3) return RiskLevel.L4;
        return RiskLevel.L5;
    }

    static int baseActionScore(Inputs in) {
        int score = 0;
        Boolean readOnly = in.annotations().readOnlyHint();
        if (readOnly == null || !readOnly) score += 1;
        Boolean destructive = in.annotations().destructiveHint();
        if (Boolean.TRUE.equals(destructive)) score += 2;
        Boolean openWorld = in.annotations().openWorldHint();
        if (Boolean.TRUE.equals(openWorld)) score += 1;
        Boolean idempotent = in.annotations().idempotentHint();
        if (Boolean.TRUE.equals(idempotent)) score -= 1;

        score += switch (in.sideEffectScope()) {
            case LOCAL_ONLY, UNKNOWN -> 0;
            case REMOTE_WRITE_SCOPE -> 1;
            case REMOTE_ADMIN_SCOPE -> 2;
        };
        if (in.sendsUserData()) score += 1;
        return Math.max(0, score);
    }

    static int docPenalty(Inputs in) {
        int gaps = 0;
        if (in.description() == null || in.description().isBlank()) {
            gaps += 2;
        } else if (in.description().length() < 30 || isBoilerplate(in.description(), in.toolName())) {
            gaps += 1;
        }
        if (!in.inputSchemaPresent()) gaps += 2;
        if (in.inputSchemaPropertyDescriptionCoverage() < 0.5) gaps += 1;
        if (in.annotations().noneSpecified()) gaps += 1;
        gaps += in.curatorChecklist().gapCount();
        return gaps;
    }

    static boolean isBoilerplate(String description, String toolName) {
        if (description == null) return false;
        String trimmed = description.trim();
        if (!BOILERPLATE_DESC.matcher(trimmed).lookingAt()) return false;
        if (toolName == null || toolName.isBlank()) return true;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String toolLower = toolName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.contains(toolLower);
    }

    String detectFloorOverride(Inputs in) {
        if (hasIrreversibleVerb(in.toolName())) return FLOOR_IRREVERSIBLE_VERB;
        if (Boolean.TRUE.equals(in.annotations().destructiveHint())
                && !Boolean.TRUE.equals(in.annotations().idempotentHint())) {
            return FLOOR_DESTRUCTIVE_NON_IDEMPOTENT;
        }
        boolean noDescription = in.description() == null || in.description().isBlank();
        boolean noAnnotations = in.annotations().noneSpecified();
        if (noDescription && noAnnotations) return FLOOR_SEVERE_FAILURE;
        return null;
    }

    static boolean hasIrreversibleVerb(String toolName) {
        if (toolName == null) return false;
        for (Pattern p : IRREVERSIBLE_VERB_PATTERNS) {
            if (p.matcher(toolName).find()) return true;
        }
        return false;
    }
}
