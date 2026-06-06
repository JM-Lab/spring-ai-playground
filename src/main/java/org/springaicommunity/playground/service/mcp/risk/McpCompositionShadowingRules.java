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

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class McpCompositionShadowingRules {

    public enum RuleId { R1_ALIAS_COLLISION, R2_CROSS_SERVER_REFERENCE, R3_SELF_SCOPE_VIOLATION }

    public record Violation(RuleId rule, String compositionId, String memberAlias, String details) {}

    private static final Pattern COMMAND_VERB = Pattern.compile(
            "(?i)\\b(use|call|invoke|run|trigger|delegate to|then call|first call|after that)\\b");

    public List<Violation> checkAliasCollisions(List<McpComposition> enabledCompositions) {
        if (enabledCompositions == null || enabledCompositions.isEmpty()) return List.of();
        Map<String, List<String>> aliasToCompositionIds = new HashMap<>();
        for (McpComposition composition : enabledCompositions) {
            if (!composition.enabled()) continue;
            for (McpComposition.Member member : composition.members()) {
                aliasToCompositionIds
                        .computeIfAbsent(member.exposedAlias(), k -> new ArrayList<>())
                        .add(composition.id());
            }
        }
        List<Violation> violations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : aliasToCompositionIds.entrySet()) {
            if (entry.getValue().size() > 1) {
                violations.add(new Violation(RuleId.R1_ALIAS_COLLISION,
                        String.join(",", entry.getValue()), entry.getKey(),
                        "alias '" + entry.getKey() + "' used by " + entry.getValue().size() + " enabled compositions"));
            }
        }
        return violations;
    }

    public List<Violation> checkCrossServerReferences(McpComposition composition,
            Map<String, ToolMetadata> memberMetadata) {
        if (composition == null || composition.members().isEmpty()) return List.of();
        Set<String> selfServerIds = new HashSet<>();
        for (McpComposition.Member member : composition.members()) {
            selfServerIds.add(member.serverId().toLowerCase(Locale.ROOT));
        }
        List<Violation> violations = new ArrayList<>();
        for (McpComposition.Member member : composition.members()) {
            ToolMetadata metadata = memberMetadata.get(memberKey(member));
            if (metadata == null) continue;
            String description = metadata.description() == null ? "" : metadata.description().toLowerCase(Locale.ROOT);
            for (McpComposition.Member other : composition.members()) {
                if (other == member) continue;
                if (member.serverId().equalsIgnoreCase(other.serverId())) continue;
                if (mentionsTool(description, other)) {
                    boolean imperativeContext = COMMAND_VERB.matcher(description).find();
                    if (imperativeContext) {
                        violations.add(new Violation(RuleId.R2_CROSS_SERVER_REFERENCE,
                                composition.id(), member.exposedAlias(),
                                "description references '" + other.toolName() + "' (" + other.serverId()
                                        + ") with command-verb context"));
                    }
                }
            }
        }
        return violations;
    }

    public List<Violation> checkSelfScope(McpComposition composition,
            Map<String, ToolMetadata> memberMetadata) {
        if (composition == null || composition.members().isEmpty()) return List.of();
        List<Violation> violations = new ArrayList<>();
        Set<String> selfServerIds = new HashSet<>();
        for (McpComposition.Member member : composition.members()) {
            selfServerIds.add(member.serverId().toLowerCase(Locale.ROOT));
        }
        for (McpComposition.Member member : composition.members()) {
            ToolMetadata metadata = memberMetadata.get(memberKey(member));
            if (metadata == null) continue;
            String description = metadata.description() == null ? "" : metadata.description().toLowerCase(Locale.ROOT);
            for (String otherServer : selfServerIds) {
                if (otherServer.equals(member.serverId().toLowerCase(Locale.ROOT))) continue;
                if (description.contains(otherServer)) {
                    violations.add(new Violation(RuleId.R3_SELF_SCOPE_VIOLATION,
                            composition.id(), member.exposedAlias(),
                            "description mentions other member server id '" + otherServer + "'"));
                }
            }
        }
        return violations;
    }

    static boolean mentionsTool(String descriptionLower, McpComposition.Member other) {
        return descriptionLower.contains(other.toolName().toLowerCase(Locale.ROOT))
                || descriptionLower.contains(other.exposedAlias().toLowerCase(Locale.ROOT));
    }

    static String memberKey(McpComposition.Member member) {
        return member.serverId() + "::" + member.toolName();
    }

    public record ToolMetadata(String description) {}
}
