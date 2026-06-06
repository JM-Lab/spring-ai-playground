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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCompositionPersistenceServiceTest {

    @TempDir
    Path tempHome;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PersistenceExecutor executor;

    @AfterEach
    void drainPersistence() throws InterruptedException, TimeoutException {
        if (executor != null) executor.awaitCompletion(Duration.ofSeconds(5));
    }

    @Test
    void roundTripCompositionsAcrossInstances() throws Exception {
        executor = new PersistenceExecutor();
        McpCompositionPersistenceService writer = new McpCompositionPersistenceService(
                tempHome, objectMapper, executor);
        McpComposition c1 = new McpComposition("c1", "dev", "", List.of(
                new McpComposition.Member("github", "list_repos", null, "h1")), true,
                RiskLevel.L3, 1L, 2L, 3L);
        McpComposition c2 = new McpComposition("c2", "ops", "", List.of(
                new McpComposition.Member("github", "delete_repo", null, "h2")), false,
                RiskLevel.L5, 1L, 2L, null);
        writer.saveAllAsync(List.of(c1, c2));
        executor.awaitCompletion(Duration.ofSeconds(5));

        McpCompositionPersistenceService reader = new McpCompositionPersistenceService(
                tempHome, objectMapper, executor);
        List<McpComposition> loaded = reader.loadAll();
        assertEquals(2, loaded.size());
        McpComposition reloadedC1 = loaded.stream().filter(c -> c.id().equals("c1")).findFirst().orElseThrow();
        assertEquals("dev", reloadedC1.name());
        assertTrue(reloadedC1.enabled());
        assertEquals(RiskLevel.L3, reloadedC1.maxRiskLevel());
        assertEquals(1, reloadedC1.members().size());
        assertEquals("github_list_repos", reloadedC1.members().getFirst().exposedAlias());
    }

    @Test
    void loadAllReturnsEmptyWhenFileMissing() throws IOException {
        executor = new PersistenceExecutor();
        McpCompositionPersistenceService service = new McpCompositionPersistenceService(
                tempHome, objectMapper, executor);
        assertNotNull(service.loadAll());
        assertTrue(service.loadAll().isEmpty());
    }
}
