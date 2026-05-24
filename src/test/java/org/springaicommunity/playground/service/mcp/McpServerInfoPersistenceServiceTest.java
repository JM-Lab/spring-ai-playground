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
package org.springaicommunity.playground.service.mcp;

import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"spring.ai.playground.user-home=${java.io.tmpdir}"})
class McpServerInfoPersistenceServiceTest {
    @Autowired
    private McpServerInfoPersistenceService persistenceService;

    @Autowired
    private McpServerInfoService mcpServerInfoService;

    @Autowired
    private PersistenceExecutor persistenceExecutor;

    @BeforeEach
    void setUp() {
        this.persistenceService.clear();
    }

    @AfterEach
    void cleanUp() {
        this.persistenceService.clear();
    }

    @Test
    void saveAndLoadMcpServerInfo() throws IOException {
        long currentTimeMillis = System.currentTimeMillis();
        McpServerInfo serverInfo =
                new McpServerInfo(McpTransportType.SSE, "server-001", "Test Server", currentTimeMillis,
                        currentTimeMillis, "{\"url\":\"http://localhost:8080\"}");

        persistenceService.save(serverInfo);

        List<McpServerInfo> loaded = persistenceService.loads();
        assertThat(loaded).hasSize(1);

        McpServerInfo loadedInfo = loaded.getFirst();
        assertThat(loadedInfo.mcpTransportType()).isEqualTo(McpTransportType.SSE);
        assertThat(loadedInfo.serverName()).isEqualTo("server-001");
        assertThat(loadedInfo.description()).isEqualTo("Test Server");
        assertThat(loadedInfo.createTimestamp()).isEqualTo(currentTimeMillis);
        assertThat(loadedInfo.updateTimestamp()).isEqualTo(currentTimeMillis);
        assertThat(loadedInfo.connectionAsJson()).isEqualTo("{\"url\":\"http://localhost:8080\"}");
    }

    @Test
    void doesNotAutoStartMcpClientsAtStartup() {
        // Regression: this service used to listen for WebServerInitializedEvent and
        // call McpClientService.startMcpClient on every saved connection, which
        // raced McpServerInfoService.onApplicationReady doing the same thing —
        // the second client replaced the first mid-initialize and the spawned
        // STDIO process exited with code 1. Keep auto-start in exactly one place.
        assertThat(ApplicationListener.class.isAssignableFrom(McpServerInfoPersistenceService.class)).isFalse();

        Constructor<?>[] ctors = McpServerInfoPersistenceService.class.getDeclaredConstructors();
        assertThat(ctors).hasSize(1);
        Class<?>[] params = ctors[0].getParameterTypes();
        assertThat(params).noneMatch(p -> p.getSimpleName().equals("McpClientService"));
    }

    @Test
    void testLoadAll_doesNotPersistMutations() throws InterruptedException, TimeoutException {
        long now = System.currentTimeMillis();
        McpServerInfo skipInfo = new McpServerInfo(McpTransportType.SSE, "loadAll-skip", "desc", now, now, "{}");
        McpServerInfo persistInfo =
                new McpServerInfo(McpTransportType.SSE, "regular-persist", "desc", now, now, "{}");

        try {
            mcpServerInfoService.loadAll(() ->
                    mcpServerInfoService.updateMcpServerInfo(skipInfo.mcpTransportType(), skipInfo.serverName(),
                            skipInfo));
            persistenceExecutor.awaitCompletion(Duration.ofSeconds(2));
            assertThat(persistenceService.getSaveDir().toFile().listFiles()).isEmpty();

            mcpServerInfoService.updateMcpServerInfo(persistInfo.mcpTransportType(), persistInfo.serverName(),
                    persistInfo);
            persistenceExecutor.awaitCompletion(Duration.ofSeconds(2));
            assertThat(persistenceService.getSaveDir().toFile().listFiles()).hasSize(1);
        } finally {
            try {
                mcpServerInfoService.deleteMcpServerInfo(skipInfo.mcpTransportType(), skipInfo.serverName());
            } catch (Exception ignored) {
            }
            try {
                mcpServerInfoService.deleteMcpServerInfo(persistInfo.mcpTransportType(), persistInfo.serverName());
            } catch (Exception ignored) {
            }
            persistenceExecutor.awaitCompletion(Duration.ofSeconds(2));
        }
    }
}
