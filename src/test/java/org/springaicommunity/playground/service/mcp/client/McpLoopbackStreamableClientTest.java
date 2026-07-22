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
package org.springaicommunity.playground.service.mcp.client;

import tools.jackson.core.JacksonException;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "vaadin.productionMode=true")
@Import(McpLoopbackClientTestSupport.LoopbackServerFixtures.class)
class McpLoopbackStreamableClientTest extends McpLoopbackClientTestSupport {

    @Test
    void fullCycleAgainstBuiltInMcpServer() throws Exception {
        verifyFullCycleAgainstBuiltInServer(McpTransportType.STREAMABLE_HTTP,
                new McpStreamableHttpClientProperties.ConnectionParameters(baseUrl(), null));
    }

    @Test
    void deadEndpointFailsGracefully() throws JacksonException {
        McpServerInfo dead = newServerInfo(McpTransportType.STREAMABLE_HTTP, "dead-loopback",
                new McpStreamableHttpClientProperties.ConnectionParameters("http://127.0.0.1:9", null));

        McpClientService.TestConnectionResult connectionResult = this.mcpClientService.testConnection(dead);
        assertThat(connectionResult.ok()).isFalse();
        assertThat(connectionResult.error()).isNotBlank();

        assertThatThrownBy(() -> this.mcpClientService.startMcpClient(dead))
                .isInstanceOf(RuntimeException.class);

        this.mcpClientService.deleteConnectingMcpServer(dead);
        assertThat(this.mcpClientService.getServerCapabilitiesAsOpt(dead)).isEmpty();
        assertThat(this.mcpClientService.getStatus(dead).status())
                .isEqualTo(McpClientService.ServerStatus.OFFLINE);
    }

}
