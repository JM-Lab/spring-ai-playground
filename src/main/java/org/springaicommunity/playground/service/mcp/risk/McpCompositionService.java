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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.ComposedTool;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpCompositionService {

    private static final Logger logger = LoggerFactory.getLogger(McpCompositionService.class);

    public static final String EXPOSED_ID = "__exposed__";

    private final McpCompositionPersistenceService persistence;
    private final McpCompositionShadowingRules shadowingRules;
    private final McpRiskSignalSink sink;
    private final Map<String, McpComposition> compositionsById = new ConcurrentHashMap<>();

    public McpCompositionService(McpCompositionPersistenceService persistence,
            McpCompositionShadowingRules shadowingRules, McpRiskSignalSink sink,
            SpringAiPlaygroundOptions playgroundOptions) {
        this.persistence = persistence;
        this.shadowingRules = shadowingRules;
        this.sink = sink;
        for (McpComposition composition : persistence.loadAll()) {
            this.compositionsById.put(composition.id(), composition);
        }
        this.compositionsById.put(EXPOSED_ID, seedExposedFromConfig(playgroundOptions.mcpServer().composedTools(),
                playgroundOptions.mcpServer().composedToolsMaxRisk()));
        logger.info("Loaded {} compositions; exposed set seeded with {} member(s) from application.yaml",
                this.compositionsById.size(), this.compositionsById.get(EXPOSED_ID).members().size());
    }

    private static McpComposition seedExposedFromConfig(List<ComposedTool> composedTools, RiskLevel maxRisk) {
        long now = Instant.now().toEpochMilli();
        List<McpComposition.Member> members = composedTools.stream()
                .filter(ct -> ct.server() != null && !ct.server().isBlank()
                        && ct.tool() != null && !ct.tool().isBlank())
                .map(ct -> new McpComposition.Member(ct.server(), ct.tool(), ct.alias(),
                        ct.description(), ct.hitl(), ""))
                .toList();
        return new McpComposition(EXPOSED_ID, "Exposed Tools",
                "External tools exposed on the built-in MCP server (from application.yaml)",
                members, !members.isEmpty(), maxRisk == null ? RiskLevel.L5 : maxRisk, now, now, now);
    }

    public Collection<McpComposition> getAll() {
        return List.copyOf(this.compositionsById.values());
    }

    public Optional<McpComposition> findById(String id) {
        return Optional.ofNullable(this.compositionsById.get(id));
    }

    public List<McpComposition> getEnabled() {
        return this.compositionsById.values().stream().filter(McpComposition::enabled).toList();
    }

    public Optional<McpComposition> getExposed() {
        return findById(EXPOSED_ID);
    }

    public synchronized McpComposition upsertExposed(List<McpComposition.Member> members, RiskLevel maxRiskLevel) {
        long now = Instant.now().toEpochMilli();
        RiskLevel cap = maxRiskLevel == null ? RiskLevel.L5 : maxRiskLevel;
        McpComposition existing = this.compositionsById.get(EXPOSED_ID);
        McpComposition exposed = existing == null
                ? new McpComposition(EXPOSED_ID, "Exposed Tools",
                        "External tools exposed on the built-in MCP server", members, true, cap, now, now, now)
                : existing.withMembers(members, now).withEnabled(true, now).withMetadata(null, null, cap, now);
        this.compositionsById.put(EXPOSED_ID, exposed);
        emit(exposed, McpRiskEvents.CompositionLifecycle.Action.UPDATED, null);
        return exposed;
    }

    public synchronized McpComposition create(String name, String description, List<McpComposition.Member> members,
            RiskLevel maxRiskLevel) {
        String id = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        McpComposition composition = new McpComposition(id, name, description, members, false,
                maxRiskLevel == null ? RiskLevel.L3 : maxRiskLevel, now, now, null);
        this.compositionsById.put(id, composition);
        persistSnapshot();
        emit(composition, McpRiskEvents.CompositionLifecycle.Action.CREATED, null);
        return composition;
    }

    public synchronized McpComposition update(String id, String newName, String newDescription,
            List<McpComposition.Member> newMembers, RiskLevel newMax) {
        McpComposition existing = require(id);
        long now = Instant.now().toEpochMilli();
        McpComposition updated = existing
                .withMetadata(newName, newDescription, newMax, now)
                .withMembers(newMembers == null ? existing.members() : newMembers, now);
        this.compositionsById.put(id, updated);
        persistSnapshot();
        emit(updated, McpRiskEvents.CompositionLifecycle.Action.UPDATED, null);
        return updated;
    }

    public synchronized void delete(String id) {
        McpComposition existing = this.compositionsById.remove(id);
        if (existing != null) {
            persistSnapshot();
            emit(existing, McpRiskEvents.CompositionLifecycle.Action.DELETED, null);
        }
    }

    public synchronized EnableResult enable(String id, Map<String, RiskLevel> memberFinalLevels,
            Map<String, McpCompositionShadowingRules.ToolMetadata> memberMetadata) {
        McpComposition existing = require(id);
        if (existing.members().isEmpty()) {
            return EnableResult.refused("composition has no members");
        }
        for (McpComposition.Member member : existing.members()) {
            RiskLevel level = memberFinalLevels.get(memberKey(member));
            if (level == null) continue;
            if (level.ordinal() > existing.maxRiskLevel().ordinal()) {
                return EnableResult.refused("member '" + member.exposedAlias() + "' has finalLevel "
                        + level + " exceeding composition maxRiskLevel " + existing.maxRiskLevel());
            }
        }
        List<McpComposition> futureEnabled = new ArrayList<>(getEnabled());
        futureEnabled.removeIf(c -> c.id().equals(id));
        futureEnabled.add(existing.withEnabled(true, Instant.now().toEpochMilli()));

        List<McpCompositionShadowingRules.Violation> violations = new ArrayList<>(
                this.shadowingRules.checkAliasCollisions(futureEnabled));
        violations.addAll(this.shadowingRules.checkCrossServerReferences(existing, memberMetadata));
        violations.addAll(this.shadowingRules.checkSelfScope(existing, memberMetadata));
        if (!violations.isEmpty()) {
            return EnableResult.refused("shadowing rule violations: " + summarize(violations));
        }

        long now = Instant.now().toEpochMilli();
        McpComposition enabled = existing.withEnabled(true, now);
        this.compositionsById.put(id, enabled);
        persistSnapshot();
        emit(enabled, McpRiskEvents.CompositionLifecycle.Action.ENABLED, null);
        return EnableResult.ok(enabled);
    }

    public synchronized McpComposition disable(String id, String reason) {
        McpComposition existing = require(id);
        if (!existing.enabled()) return existing;
        long now = Instant.now().toEpochMilli();
        McpComposition disabled = existing.withEnabled(false, now);
        this.compositionsById.put(id, disabled);
        persistSnapshot();
        emit(disabled, McpRiskEvents.CompositionLifecycle.Action.DISABLED, reason);
        return disabled;
    }

    public synchronized McpComposition autoDisable(String id, String reason) {
        McpComposition existing = require(id);
        if (!existing.enabled()) return existing;
        long now = Instant.now().toEpochMilli();
        McpComposition disabled = existing.withEnabled(false, now);
        this.compositionsById.put(id, disabled);
        persistSnapshot();
        emit(disabled, McpRiskEvents.CompositionLifecycle.Action.AUTO_DISABLED, reason);
        return disabled;
    }

    public record EnableResult(boolean accepted, McpComposition composition, String refusalReason) {

        public static EnableResult ok(McpComposition composition) {
            return new EnableResult(true, composition, null);
        }

        public static EnableResult refused(String reason) {
            return new EnableResult(false, null, reason);
        }
    }

    private McpComposition require(String id) {
        McpComposition existing = this.compositionsById.get(id);
        if (existing == null) throw new IllegalArgumentException("composition not found: " + id);
        return existing;
    }

    private void persistSnapshot() {
        List<McpComposition> toPersist = this.compositionsById.values().stream()
                .filter(composition -> !EXPOSED_ID.equals(composition.id())).toList();
        this.persistence.saveAllAsync(new ArrayList<>(toPersist));
    }

    private void emit(McpComposition composition, McpRiskEvents.CompositionLifecycle.Action action, String reason) {
        this.sink.onCompositionLifecycle(new McpRiskEvents.CompositionLifecycle(
                Instant.now(), composition.id(), composition.name(), action, reason));
    }

    static String memberKey(McpComposition.Member member) {
        return member.serverId() + "::" + member.toolName();
    }

    static String summarize(List<McpCompositionShadowingRules.Violation> violations) {
        Map<McpCompositionShadowingRules.RuleId, Integer> byRule = new LinkedHashMap<>();
        for (McpCompositionShadowingRules.Violation v : violations) {
            byRule.merge(v.rule(), 1, Integer::sum);
        }
        return byRule.toString();
    }
}
