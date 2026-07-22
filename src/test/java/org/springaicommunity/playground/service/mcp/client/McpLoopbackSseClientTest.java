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

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.ai.mcp.server.protocol=SSE", "spring.ai.mcp.client.type=ASYNC",
                "vaadin.productionMode=true"})
@Import(McpLoopbackClientTestSupport.LoopbackServerFixtures.class)
class McpLoopbackSseClientTest extends McpLoopbackClientTestSupport {

    @Test
    void fullCycleAgainstBuiltInMcpServer() throws Exception {
        verifyFullCycleAgainstBuiltInServer(McpTransportType.SSE,
                new McpSseClientProperties.SseParameters(baseUrl(), null));
    }

}
