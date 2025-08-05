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
package jm.kr.spring.ai.playground.service.mcp.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
class StdioMcpClientServiceTest implements McpClientServiceTest {

    private static final String SERVER_NAME = "mcp-mock";
    private static final String CONTAINER_NAME = SERVER_NAME + "-stdio-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private McpClientService mcpClientService;

    @AfterEach
    void tearDown() throws Exception {
        // Force-stop the Docker container if it's still running
        new ProcessBuilder("docker", "stop", "-t", "0", CONTAINER_NAME)
                .inheritIO()       // For logging; remove if not needed
                .start()
                .waitFor();        // Waits for the command to finish; ignores errors if container already stopped
    }

    @Test
    void fullCycleWithStdioTransport() throws JsonProcessingException {
        McpStdioClientProperties.Parameters parameters = new McpStdioClientProperties.Parameters("docker", List.of(
                "run", "-i",
                "--name", CONTAINER_NAME,
                "tzolov/mcp-everything-server:v2",
                "node", "dist/index.js", "stdio"
        ), Map.of());
        testMcpClient(mcpClientService, SERVER_NAME, McpTransportType.STDIO, parameters);
    }
}