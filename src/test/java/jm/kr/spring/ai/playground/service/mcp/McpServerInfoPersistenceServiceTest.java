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
package jm.kr.spring.ai.playground.service.mcp;

import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"spring.ai.playground.user-home=${java.io.tmpdir}"})
class McpServerInfoPersistenceServiceTest {
    @Autowired
    private McpServerInfoPersistenceService persistenceService;

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
}