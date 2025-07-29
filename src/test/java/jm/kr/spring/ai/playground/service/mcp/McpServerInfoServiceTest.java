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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class McpServerInfoServiceTest {

    @Autowired
    private McpServerInfoService mcpServerInfoService;

    @Test
    public void testMcpServerInfoService() {
        Map<McpTransportType, List<McpServerInfo>> serverInfos = mcpServerInfoService.getMcpServerInfos();
        assertTrue(serverInfos.containsKey(McpTransportType.STDIO));
        assertTrue(serverInfos.containsKey(McpTransportType.SSE));
        assertTrue(serverInfos.containsKey(McpTransportType.STREAMABLE_HTTP));
        assertEquals(1, serverInfos.get(McpTransportType.STDIO).size());
        assertEquals(0, serverInfos.get(McpTransportType.SSE).size());
        assertEquals(0, serverInfos.get(McpTransportType.STREAMABLE_HTTP).size());

        String newServer = "New MCP Server";
        McpServerInfo mcpServerInfo = mcpServerInfoService.createDefaultMcpServerInfo();
        assertNotNull(mcpServerInfo);
        assertEquals(McpTransportType.STDIO, mcpServerInfo.mcpTransportType());
        assertEquals(newServer, mcpServerInfo.serverName());
        assertEquals("Please edit the description of the MCP Server.", mcpServerInfo.description());
        assertTrue(mcpServerInfo.createTimestamp() == mcpServerInfo.updateTimestamp());
        assertEquals(null, mcpServerInfo.connectionAsJson());

        serverInfos = mcpServerInfoService.getMcpServerInfos();
        assertTrue(serverInfos.containsKey(McpTransportType.STDIO));
        assertTrue(serverInfos.containsKey(McpTransportType.SSE));
        assertTrue(serverInfos.containsKey(McpTransportType.STREAMABLE_HTTP));
        assertEquals(1, serverInfos.get(McpTransportType.STDIO).size());
        assertEquals(0, serverInfos.get(McpTransportType.SSE).size());
        assertEquals(0, serverInfos.get(McpTransportType.STREAMABLE_HTTP).size());

        mcpServerInfo = serverInfos.get(McpTransportType.STDIO).get(0);

        McpTransportType type = McpTransportType.STREAMABLE_HTTP;
        String serverName = "server1";
        String newConnectionAsJson = "{\"url\":\"newhost\",\"endpoint\":\"\"}";
        long now = System.currentTimeMillis();
        String description = "description";
        McpServerInfo updateMcpServerInfo =
                mcpServerInfo.mutate(type, serverName, description, now, newConnectionAsJson);
        McpServerInfo updatedMcpServerInfo =
                mcpServerInfoService.updateMcpServerInfo(mcpServerInfo.mcpTransportType(), mcpServerInfo.serverName(),
                        updateMcpServerInfo);
        assertNotNull(updatedMcpServerInfo);
        assertEquals(McpTransportType.STREAMABLE_HTTP, updateMcpServerInfo.mcpTransportType());
        assertEquals(serverName, updateMcpServerInfo.serverName());
        assertEquals(description, updateMcpServerInfo.description());
        assertFalse(updateMcpServerInfo.createTimestamp() == updateMcpServerInfo.updateTimestamp());
        assertEquals(newConnectionAsJson, updateMcpServerInfo.connectionAsJson());

        serverInfos = mcpServerInfoService.getMcpServerInfos();
        assertEquals(serverName, serverInfos.get(McpTransportType.STREAMABLE_HTTP).get(0).serverName());
        assertTrue(serverInfos.containsKey(McpTransportType.STDIO));
        assertTrue(serverInfos.containsKey(McpTransportType.SSE));
        assertTrue(serverInfos.containsKey(McpTransportType.STREAMABLE_HTTP));
        assertEquals(0, serverInfos.get(McpTransportType.STDIO).size());
        assertEquals(0, serverInfos.get(McpTransportType.SSE).size());
        assertEquals(1, serverInfos.get(McpTransportType.STREAMABLE_HTTP).size());

        McpServerInfo newMcpServerInfo = new McpServerInfo(McpTransportType.SSE, "new", "new description", now, now,
                "{}");
        mcpServerInfoService.updateMcpServerInfo(newMcpServerInfo.mcpTransportType(), newMcpServerInfo.serverName(),
                newMcpServerInfo);
        serverInfos = mcpServerInfoService.getMcpServerInfos();
        assertEquals(newMcpServerInfo.serverName(), serverInfos.get(McpTransportType.SSE).get(0).serverName());
        assertTrue(serverInfos.containsKey(McpTransportType.STDIO));
        assertTrue(serverInfos.containsKey(McpTransportType.SSE));
        assertTrue(serverInfos.containsKey(McpTransportType.STREAMABLE_HTTP));
        assertEquals(0, serverInfos.get(McpTransportType.STDIO).size());
        assertEquals(1, serverInfos.get(McpTransportType.SSE).size());
        assertEquals(1, serverInfos.get(McpTransportType.STREAMABLE_HTTP).size());

        McpServerInfo deletedMcpServerInfo =
                mcpServerInfoService.deleteMcpServerInfo(newMcpServerInfo.mcpTransportType(),
                        newMcpServerInfo.serverName());
        assertEquals(newMcpServerInfo, deletedMcpServerInfo);

        serverInfos = mcpServerInfoService.getMcpServerInfos();
        assertTrue(serverInfos.containsKey(McpTransportType.STDIO));
        assertTrue(serverInfos.containsKey(McpTransportType.SSE));
        assertTrue(serverInfos.containsKey(McpTransportType.STREAMABLE_HTTP));
        assertEquals(0, serverInfos.get(McpTransportType.STDIO).size());
        assertEquals(0, serverInfos.get(McpTransportType.SSE).size());
        assertEquals(1, serverInfos.get(McpTransportType.STREAMABLE_HTTP).size());

    }
}