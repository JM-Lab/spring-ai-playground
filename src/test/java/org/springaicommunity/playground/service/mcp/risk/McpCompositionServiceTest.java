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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCompositionServiceTest {

    @TempDir
    Path tempHome;

    private McpCompositionService service;
    private PersistenceExecutor executor;
    private List<McpRiskEvents.CompositionLifecycle> emittedEvents;

    @BeforeEach
    void setUp() throws IOException {
        this.emittedEvents = new ArrayList<>();
        this.executor = new PersistenceExecutor();
        McpCompositionPersistenceService persistence = new McpCompositionPersistenceService(
                tempHome, new ObjectMapper(), executor);
        McpRiskSignalSink sink = new McpRiskSignalSink() {
            @Override public void onServerRiskComputed(McpRiskEvents.ServerRiskComputed event) {}
            @Override public void onToolPublishRiskComputed(McpRiskEvents.ToolPublishRiskComputed event) {}
            @Override public void onFloorOverrideTriggered(McpRiskEvents.FloorOverrideTriggered event) {}
            @Override public void onHashLedgerMismatch(McpRiskEvents.HashLedgerMismatch event) {}
            @Override public void onCompositionLifecycle(McpRiskEvents.CompositionLifecycle event) {
                emittedEvents.add(event);
            }
            @Override public void onPoisoningHit(McpRiskEvents.PoisoningHit event) {}
        };
        SpringAiPlaygroundOptions options = new SpringAiPlaygroundOptions(null, false, null, null, null, null);
        this.service = new McpCompositionService(persistence, new McpCompositionShadowingRules(), sink, options);
    }

    @AfterEach
    void drainPersistence() throws InterruptedException, TimeoutException {
        executor.awaitCompletion(Duration.ofSeconds(5));
    }

    private McpComposition.Member member(String serverId, String toolName) {
        return new McpComposition.Member(serverId, toolName, null, "h-" + toolName);
    }

    @Test
    void upsertExposedCreatesEnabledSingletonWithFixedId() {
        McpComposition exposed = service.upsertExposed(List.of(member("github", "search_issues")), RiskLevel.L3);
        assertEquals(McpCompositionService.EXPOSED_ID, exposed.id());
        assertTrue(exposed.enabled());
        assertEquals(1, exposed.members().size());
        assertEquals(RiskLevel.L3, exposed.maxRiskLevel());
        assertTrue(service.getExposed().isPresent());
    }

    @Test
    void upsertExposedReplacesMembersAndCapKeepingSingletonId() {
        service.upsertExposed(List.of(member("github", "a"), member("github", "b")), RiskLevel.L3);
        McpComposition updated = service.upsertExposed(List.of(member("tavily", "search")), RiskLevel.L2);
        assertEquals(McpCompositionService.EXPOSED_ID, updated.id());
        assertEquals(1, updated.members().size());
        assertEquals("tavily", updated.members().getFirst().serverId());
        assertEquals(RiskLevel.L2, updated.maxRiskLevel());
        assertTrue(updated.enabled());
    }

    @Test
    void createReturnsDisabledCompositionAndEmitsCreatedEvent() {
        McpComposition created = service.create("dev-toolbox", "developer tools",
                List.of(member("github", "list_repos")), RiskLevel.L3);
        assertNotNull(created.id());
        assertFalse(created.enabled());
        assertEquals(1, created.members().size());
        assertEquals(McpRiskEvents.CompositionLifecycle.Action.CREATED, emittedEvents.getFirst().action());
    }

    @Test
    void enableRefusesEmptyMemberList() {
        McpComposition empty = service.create("empty", "", List.of(), RiskLevel.L3);
        McpCompositionService.EnableResult result = service.enable(empty.id(), Map.of(), Map.of());
        assertFalse(result.accepted());
        assertNotNull(result.refusalReason());
    }

    @Test
    void enableRefusesMemberExceedingMaxRiskLevel() {
        McpComposition comp = service.create("strict", "",
                List.of(member("github", "delete_repo")), RiskLevel.L3);
        McpCompositionService.EnableResult result = service.enable(comp.id(),
                Map.of("github::delete_repo", RiskLevel.L5), Map.of());
        assertFalse(result.accepted());
        assertTrue(result.refusalReason().contains("exceeding"));
    }

    @Test
    void enableAcceptsMemberAtOrBelowMaxRiskLevel() {
        McpComposition comp = service.create("ok-comp", "",
                List.of(member("github", "list_repos")), RiskLevel.L3);
        McpCompositionService.EnableResult result = service.enable(comp.id(),
                Map.of("github::list_repos", RiskLevel.L1), Map.of());
        assertTrue(result.accepted());
        assertTrue(result.composition().enabled());
        assertTrue(emittedEvents.stream()
                .anyMatch(e -> e.action() == McpRiskEvents.CompositionLifecycle.Action.ENABLED));
    }

    @Test
    void enableRefusesWhenAliasCollidesWithOtherEnabledComposition() {
        McpComposition first = service.create("first", "", List.of(member("github", "search")), RiskLevel.L3);
        service.enable(first.id(), Map.of("github::search", RiskLevel.L1), Map.of());

        McpComposition.Member conflict = new McpComposition.Member("notion", "search",
                McpComposition.Member.defaultAlias("github", "search"), "h-x");
        McpComposition second = service.create("second", "", List.of(conflict), RiskLevel.L3);
        McpCompositionService.EnableResult result = service.enable(second.id(),
                Map.of("notion::search", RiskLevel.L1), Map.of());
        assertFalse(result.accepted());
        assertTrue(result.refusalReason().contains("shadowing"));
    }

    @Test
    void disableEmitsDisabledEvent() {
        McpComposition comp = service.create("toggle", "",
                List.of(member("github", "list_repos")), RiskLevel.L3);
        service.enable(comp.id(), Map.of("github::list_repos", RiskLevel.L1), Map.of());
        McpComposition disabled = service.disable(comp.id(), "user toggled off");
        assertFalse(disabled.enabled());
        assertTrue(emittedEvents.stream()
                .anyMatch(e -> e.action() == McpRiskEvents.CompositionLifecycle.Action.DISABLED));
    }

    @Test
    void autoDisableEmitsAutoDisabledEvent() {
        McpComposition comp = service.create("auto", "",
                List.of(member("github", "list_repos")), RiskLevel.L3);
        service.enable(comp.id(), Map.of("github::list_repos", RiskLevel.L1), Map.of());
        service.autoDisable(comp.id(), "hash mismatch");
        assertTrue(emittedEvents.stream()
                .anyMatch(e -> e.action() == McpRiskEvents.CompositionLifecycle.Action.AUTO_DISABLED));
    }

    @Test
    void deleteRemovesCompositionAndEmits() {
        McpComposition comp = service.create("gone", "", List.of(member("github", "x")), RiskLevel.L3);
        service.delete(comp.id());
        assertTrue(service.findById(comp.id()).isEmpty());
        assertTrue(emittedEvents.stream()
                .anyMatch(e -> e.action() == McpRiskEvents.CompositionLifecycle.Action.DELETED));
    }

    @Test
    void updateChangesMetadataAndMembers() {
        McpComposition comp = service.create("orig", "",
                List.of(member("github", "list_repos")), RiskLevel.L3);
        List<McpComposition.Member> newMembers = List.of(
                member("github", "list_repos"), member("notion", "read_page"));
        McpComposition updated = service.update(comp.id(), "renamed", "new desc", newMembers, RiskLevel.L4);
        assertEquals("renamed", updated.name());
        assertEquals(2, updated.members().size());
        assertEquals(RiskLevel.L4, updated.maxRiskLevel());
    }

    @Test
    void updateUnknownIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> service.update("not-real", "x", null, null, null));
    }

    @Test
    void getEnabledFiltersDisabled() {
        McpComposition a = service.create("a", "", List.of(member("github", "x")), RiskLevel.L3);
        McpComposition b = service.create("b", "", List.of(member("notion", "y")), RiskLevel.L3);
        service.enable(a.id(), Map.of("github::x", RiskLevel.L1), Map.of());
        assertEquals(1, service.getEnabled().size());
        assertEquals(a.id(), service.getEnabled().getFirst().id());
        assertNull(b.lastEnabledAtEpochMs());
    }
}
