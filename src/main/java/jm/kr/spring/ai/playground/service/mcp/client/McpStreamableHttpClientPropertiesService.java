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
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static jm.kr.spring.ai.playground.service.mcp.client.McpTransportType.STREAMABLE_HTTP;

@Service
public class McpStreamableHttpClientPropertiesService implements McpClientPropertiesService<ConnectionParameters> {

    private final McpStreamableHttpClientProperties mcpStreamableHttpClientProperties;

    public McpStreamableHttpClientPropertiesService(
            @Nullable McpStreamableHttpClientProperties mcpStreamableHttpClientProperties) {
        this.mcpStreamableHttpClientProperties = Optional.ofNullable(mcpStreamableHttpClientProperties)
                .orElseGet(McpStreamableHttpClientProperties::new);
    }

    @Override
    public McpTransportType getTransportType() {
        return STREAMABLE_HTTP;
    }

    @Override
    public Map<String, ConnectionParameters> getDefaultConnections() {
        return this.mcpStreamableHttpClientProperties.getConnections();
    }

}
