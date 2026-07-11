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

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.MDC;
import org.springaicommunity.playground.config.MdcIdentityFilter;

import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

// Replays the caller's conversation and session MDC as headers on the built-in loopback connection so
// per-conversation tool scoping survives the HTTP hop; external MCP servers never receive these headers.
public final class McpLoopbackIdentity {

    private McpLoopbackIdentity() {
    }

    public static McpTransportContext snapshotMdc() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, MdcIdentityFilter.CONVERSATION_HEADER, MDC.get(MdcIdentityFilter.CONVERSATION_ID));
        put(metadata, MdcIdentityFilter.SESSION_HEADER, MDC.get(MdcIdentityFilter.SESSION_ID));
        return metadata.isEmpty() ? McpTransportContext.EMPTY : McpTransportContext.create(metadata);
    }

    public static McpSyncHttpClientRequestCustomizer headerCustomizer() {
        return (builder, method, endpoint, body, context) -> {
            header(builder, context, MdcIdentityFilter.CONVERSATION_HEADER);
            header(builder, context, MdcIdentityFilter.SESSION_HEADER);
        };
    }

    private static void put(Map<String, Object> metadata, String key, String value) {
        if (value != null && !value.isBlank()) metadata.put(key, value);
    }

    private static void header(HttpRequest.Builder builder, McpTransportContext context, String name) {
        if (context.get(name) instanceof String value && !value.isBlank()) builder.header(name, value);
    }
}
