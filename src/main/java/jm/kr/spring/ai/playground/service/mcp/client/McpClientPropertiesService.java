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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties.SseParameters;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties.ConnectionParameters;
import org.springframework.util.StringUtils;

import java.util.Map;

public interface McpClientPropertiesService<P> {

    McpTransportType getTransportType();

    Map<String, P> getDefaultConnections();

    default McpClientTransport buildClientTransport(ObjectMapper objectMapper, String parametersAsJson) {
        try {
            return switch (getTransportType()) {
                case SSE -> {
                    SseParameters sseParameters = objectMapper.readValue(parametersAsJson, SseParameters.class);
                    HttpClientSseClientTransport.Builder builder =
                            HttpClientSseClientTransport.builder(sseParameters.url());
                    if (StringUtils.hasText(sseParameters.sseEndpoint()))
                        builder.sseEndpoint(sseParameters.sseEndpoint());
                    yield builder.build();
                }
                case STREAMABLE_HTTP -> {
                    ConnectionParameters connectionParameters =
                            objectMapper.readValue(parametersAsJson, ConnectionParameters.class);
                    HttpClientStreamableHttpTransport.Builder builder =
                            HttpClientStreamableHttpTransport.builder(connectionParameters.url());
                    if (StringUtils.hasText(connectionParameters.endpoint()))
                        builder.endpoint(connectionParameters.endpoint());
                    yield builder.build();
                }
                case STDIO -> {
                    Parameters parameters = objectMapper.readValue(parametersAsJson, Parameters.class);
                    yield new StdioClientTransport(parameters.toServerParameters(), new JacksonMcpJsonMapper(objectMapper));
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}