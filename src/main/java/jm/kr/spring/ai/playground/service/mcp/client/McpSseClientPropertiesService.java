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

import jakarta.annotation.Nullable;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties.SseParameters;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static jm.kr.spring.ai.playground.service.mcp.client.McpTransportType.SSE;

@Service
public class McpSseClientPropertiesService implements McpClientPropertiesService<SseParameters> {

    private final McpSseClientProperties mcpSseClientProperties;

    public McpSseClientPropertiesService(@Nullable McpSseClientProperties mcpSseClientProperties) {
        this.mcpSseClientProperties =
                Optional.ofNullable(mcpSseClientProperties).orElseGet(McpSseClientProperties::new);
    }

    @Override
    public McpTransportType getTransportType() {
        return SSE;
    }

    @Override
    public Map<String, SseParameters> getDefaultConnections() {
        return this.mcpSseClientProperties.getConnections();
    }

}
