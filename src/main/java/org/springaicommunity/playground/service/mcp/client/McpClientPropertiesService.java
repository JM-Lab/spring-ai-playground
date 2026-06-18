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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.springaicommunity.playground.service.oauth.McpOAuth2AuthorizationCodeRequestCustomizer;
import org.springaicommunity.playground.service.util.EnvVarResolver;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties.Parameters;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface McpClientPropertiesService<P> {

    McpTransportType getTransportType();

    Map<String, P> getDefaultConnections();

    default McpClientTransport buildClientTransport(ObjectMapper objectMapper, String parametersAsJson) {
        return buildClientTransport(objectMapper, parametersAsJson, null, null);
    }

    default McpClientTransport buildClientTransport(ObjectMapper objectMapper, String parametersAsJson,
            String oauthRegistrationId, OAuth2AuthorizedClientManager oauth2ClientManager) {
        try {
            return switch (getTransportType()) {
                case SSE -> {
                    HttpConnectionParametersWithExtras.Sse params = objectMapper.readValue(
                            parametersAsJson, HttpConnectionParametersWithExtras.Sse.class);
                    requireEnv(collectHttpRefs(params.url(), params.sseEndpoint(),
                            params.headers(), params.requiredEnv()));
                    String resolvedUrl = EnvVarResolver.substitute(params.url());
                    String resolvedEndpoint = EnvVarResolver.substitute(params.sseEndpoint());
                    Map<String, String> resolvedHeaders = EnvVarResolver.substituteAll(params.headers());
                    HttpClientSseClientTransport.Builder builder =
                            HttpClientSseClientTransport.builder(resolvedUrl);
                    if (StringUtils.hasText(resolvedEndpoint)) builder.sseEndpoint(resolvedEndpoint);
                    McpSyncHttpClientRequestCustomizer sseCustomizer = combineCustomizers(resolvedHeaders,
                            oauthCustomizer(params.oauth(), oauthRegistrationId, oauth2ClientManager));
                    if (sseCustomizer != null) builder.httpRequestCustomizer(sseCustomizer);
                    yield builder.build();
                }
                case STREAMABLE_HTTP -> {
                    HttpConnectionParametersWithExtras.StreamableHttp params = objectMapper.readValue(
                            parametersAsJson, HttpConnectionParametersWithExtras.StreamableHttp.class);
                    requireEnv(collectHttpRefs(params.url(), params.endpoint(),
                            params.headers(), params.requiredEnv()));
                    String resolvedUrl = EnvVarResolver.substitute(params.url());
                    String resolvedEndpoint = EnvVarResolver.substitute(params.endpoint());
                    Map<String, String> resolvedHeaders = EnvVarResolver.substituteAll(params.headers());
                    HttpClientStreamableHttpTransport.Builder builder =
                            HttpClientStreamableHttpTransport.builder(resolvedUrl);
                    if (StringUtils.hasText(resolvedEndpoint)) builder.endpoint(resolvedEndpoint);
                    McpSyncHttpClientRequestCustomizer httpCustomizer = combineCustomizers(resolvedHeaders,
                            oauthCustomizer(params.oauth(), oauthRegistrationId, oauth2ClientManager));
                    if (httpCustomizer != null) builder.httpRequestCustomizer(httpCustomizer);
                    yield builder.build();
                }
                case STDIO -> {
                    String resolvedJson = substituteStdio(objectMapper, parametersAsJson);
                    Parameters parameters = objectMapper.readValue(resolvedJson, Parameters.class);
                    yield new StdioClientTransport(parameters.toServerParameters(),
                            new JacksonMcpJsonMapper(JsonMapper.builder().build()));
                }
            };
        } catch (JacksonException e) {
            throw new IllegalStateException(
                    "Failed to parse MCP client connection parameters for transport " + getTransportType(), e);
        }
    }

    private static McpSyncHttpClientRequestCustomizer combineCustomizers(Map<String, String> headers,
            McpSyncHttpClientRequestCustomizer oauth) {
        boolean hasHeaders = headers != null && !headers.isEmpty();
        if (!hasHeaders && oauth == null) return null;
        return (request, method, uri, body, context) -> {
            if (hasHeaders) headers.forEach(request::header);
            if (oauth != null) oauth.customize(request, method, uri, body, context);
        };
    }

    private static McpSyncHttpClientRequestCustomizer oauthCustomizer(HttpConnectionParametersWithExtras.OAuth oauth,
            String registrationId, OAuth2AuthorizedClientManager manager) {
        if (oauth == null) return null;
        if (!StringUtils.hasText(registrationId)) return null;
        if (manager == null) return null;
        if (!StringUtils.hasText(oauth.clientId())) return null;
        return new McpOAuth2AuthorizationCodeRequestCustomizer(manager, registrationId);
    }

    private static Set<String> collectHttpRefs(String url, String endpoint, Map<String, String> headers,
            List<String> declared) {
        Set<String> refs = new LinkedHashSet<>();
        if (declared != null) refs.addAll(declared);
        if (url != null) refs.addAll(EnvVarResolver.findRefs(url));
        if (endpoint != null) refs.addAll(EnvVarResolver.findRefs(endpoint));
        if (headers != null) for (String value : headers.values()) refs.addAll(EnvVarResolver.findRefs(value));
        return refs;
    }

    private static void requireEnv(Set<String> names) {
        Set<String> missing = EnvVarResolver.missing(names);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing env vars: " + String.join(", ", missing));
        }
    }

    default Set<String> findMissingEnv(ObjectMapper objectMapper, String parametersAsJson) {
        if (parametersAsJson == null || parametersAsJson.isBlank()) return Set.of();
        try {
            Set<String> refs = switch (getTransportType()) {
                case SSE -> {
                    HttpConnectionParametersWithExtras.Sse params = objectMapper.readValue(parametersAsJson,
                            HttpConnectionParametersWithExtras.Sse.class);
                    yield collectHttpRefs(params.url(), params.sseEndpoint(),
                            params.headers(), params.requiredEnv());
                }
                case STREAMABLE_HTTP -> {
                    HttpConnectionParametersWithExtras.StreamableHttp params = objectMapper.readValue(
                            parametersAsJson, HttpConnectionParametersWithExtras.StreamableHttp.class);
                    yield collectHttpRefs(params.url(), params.endpoint(),
                            params.headers(), params.requiredEnv());
                }
                case STDIO -> {
                    JsonNode root = objectMapper.readTree(parametersAsJson);
                    Set<String> r = new LinkedHashSet<>();
                    if (root.isObject()) {
                        JsonNode commandNode = root.get("command");
                        if (commandNode != null && commandNode.isTextual()) {
                            r.addAll(EnvVarResolver.findRefs(commandNode.asText()));
                        }
                        JsonNode argsNode = root.get("args");
                        if (argsNode != null && argsNode.isArray()) {
                            for (JsonNode a : argsNode) {
                                if (a.isTextual()) r.addAll(EnvVarResolver.findRefs(a.asText()));
                            }
                        }
                        JsonNode envNode = root.get("env");
                        if (envNode != null && envNode.isObject()) {
                            Iterator<Map.Entry<String, JsonNode>> it = envNode.properties().iterator();
                            while (it.hasNext()) {
                                Map.Entry<String, JsonNode> e = it.next();
                                if (!e.getValue().isNull()) {
                                    r.addAll(EnvVarResolver.findRefs(e.getValue().asText("")));
                                }
                            }
                        }
                    }
                    yield r;
                }
            };
            return EnvVarResolver.missing(refs);
        } catch (JacksonException e) {
            return Set.of();
        }
    }

    private static String substituteStdio(ObjectMapper objectMapper, String parametersAsJson)
            throws JacksonException {
        JsonNode root = objectMapper.readTree(parametersAsJson);
        if (!root.isObject()) return parametersAsJson;
        ObjectNode obj = (ObjectNode) root;

        Set<String> refs = new LinkedHashSet<>();
        JsonNode commandNode = obj.get("command");
        if (commandNode != null && commandNode.isTextual()) {
            refs.addAll(EnvVarResolver.findRefs(commandNode.asText()));
        }
        JsonNode argsNode = obj.get("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode a : argsNode) {
                if (a.isTextual()) refs.addAll(EnvVarResolver.findRefs(a.asText()));
            }
        }
        JsonNode envNode = obj.get("env");
        if (envNode != null && envNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = envNode.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!e.getValue().isNull()) refs.addAll(EnvVarResolver.findRefs(e.getValue().asText("")));
            }
        }
        requireEnv(refs);

        if (commandNode != null && commandNode.isTextual()) {
            obj.put("command", EnvVarResolver.substitute(commandNode.asText()));
        }
        if (argsNode != null && argsNode.isArray()) {
            ArrayNode newArgs = objectMapper.createArrayNode();
            for (JsonNode a : argsNode) {
                if (a.isTextual()) newArgs.add(EnvVarResolver.substitute(a.asText()));
                else newArgs.add(a);
            }
            obj.set("args", newArgs);
        }
        if (envNode != null && envNode.isObject()) {
            ObjectNode envObj = (ObjectNode) envNode;
            Map<String, String> values = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> it = envObj.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                values.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText(null));
            }
            values.forEach((k, v) -> envObj.put(k, v == null ? null : EnvVarResolver.substitute(v)));
        }
        wrapWindowsScriptIfNeeded(obj, objectMapper);
        return objectMapper.writeValueAsString(obj);
    }

    private static void wrapWindowsScriptIfNeeded(ObjectNode obj, ObjectMapper objectMapper) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("windows")) return;
        JsonNode commandNode = obj.get("command");
        if (commandNode == null || !commandNode.isTextual()) return;
        String command = commandNode.asText();
        if (command == null || command.isBlank()) return;
        if (command.endsWith(".exe") || command.contains("\\") || command.contains("/")) return;
        String lower = command.toLowerCase();
        if (!(lower.equals("npx") || lower.equals("uvx") || lower.equals("npm") || lower.equals("pnpm")
                || lower.equals("yarn") || lower.equals("bunx") || lower.endsWith(".cmd")
                || lower.endsWith(".bat") || lower.endsWith(".ps1"))) {
            return;
        }
        ArrayNode wrapped = objectMapper.createArrayNode();
        wrapped.add("/c");
        wrapped.add(command);
        JsonNode existing = obj.get("args");
        if (existing != null && existing.isArray()) existing.forEach(wrapped::add);
        obj.put("command", "cmd");
        obj.set("args", wrapped);
    }
}
