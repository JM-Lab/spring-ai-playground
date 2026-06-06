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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpComposition(
        String id,
        String name,
        String description,
        List<Member> members,
        boolean enabled,
        RiskLevel maxRiskLevel,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        Long lastEnabledAtEpochMs) {

    public McpComposition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("composition id must not be blank");
        if (name == null) name = id;
        if (description == null) description = "";
        members = members == null ? List.of() : List.copyOf(members);
        if (maxRiskLevel == null) maxRiskLevel = RiskLevel.L3;
    }

    public McpComposition withEnabled(boolean newEnabled, long nowEpochMs) {
        return new McpComposition(id, name, description, members, newEnabled, maxRiskLevel,
                createdAtEpochMs, nowEpochMs, newEnabled ? nowEpochMs : lastEnabledAtEpochMs);
    }

    public McpComposition withMembers(List<Member> newMembers, long nowEpochMs) {
        return new McpComposition(id, name, description, newMembers, enabled, maxRiskLevel,
                createdAtEpochMs, nowEpochMs, lastEnabledAtEpochMs);
    }

    public McpComposition withMetadata(String newName, String newDescription, RiskLevel newMax, long nowEpochMs) {
        return new McpComposition(id, newName == null ? name : newName,
                newDescription == null ? description : newDescription, members, enabled,
                newMax == null ? maxRiskLevel : newMax, createdAtEpochMs, nowEpochMs, lastEnabledAtEpochMs);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Member(
            String serverId,
            String toolName,
            String exposedAlias,
            String descriptionOverride,
            boolean hitl,
            String contentHash) {

        public Member {
            if (exposedAlias == null || exposedAlias.isBlank()) {
                exposedAlias = defaultAlias(serverId, toolName);
            }
            if (contentHash == null) contentHash = "";
        }

        public Member(String serverId, String toolName, String exposedAlias, String contentHash) {
            this(serverId, toolName, exposedAlias, null, false, contentHash);
        }

        public static String defaultAlias(String serverId, String toolName) {
            if (serverId == null) serverId = "unknown";
            if (toolName == null) toolName = "unknown";
            String prefix = sanitize(serverId).replaceAll("_+$", "");
            if (prefix.isEmpty()) prefix = "unknown";
            return prefix + "_" + toolName;
        }

        static String sanitize(String s) {
            return s.replaceAll("[^A-Za-z0-9_]", "_").toLowerCase();
        }
    }
}
