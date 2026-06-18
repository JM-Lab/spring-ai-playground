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
package org.springaicommunity.playground.service.mcp.catalog;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class McpCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(McpCatalogService.class);
    private static final String DEFAULT_RESOURCE = "mcp/default-mcp-specs.json";
    private static final String STDIO_RESOURCE_PREFIX = "mcp/default-mcp-specs-stdio-";
    private static final String STDIO_RESOURCE_SUFFIX = ".json";

    private final ObjectMapper objectMapper;
    private final Map<String, McpCatalogEntry> entriesById;
    private final List<McpCatalogEntry> entriesOrdered;

    public McpCatalogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, McpCatalogEntry> loaded = new LinkedHashMap<>();
        loadEntries(DEFAULT_RESOURCE, loaded);

        String stdioResource = resolveStdioResource();
        if (stdioResource != null) {
            loadEntries(stdioResource, loaded);
        } else {
            logger.warn("MCP stdio catalog skipped — unsupported OS: {}", System.getProperty("os.name"));
        }

        this.entriesById = Collections.unmodifiableMap(loaded);
        this.entriesOrdered = List.copyOf(loaded.values());
        logger.info("Loaded {} MCP catalog entries (stdio from {})", this.entriesOrdered.size(),
                stdioResource == null ? "none" : stdioResource);
    }

    private void loadEntries(String resourcePath, Map<String, McpCatalogEntry> sink) {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            logger.warn("MCP catalog resource missing at {}", resourcePath);
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            List<McpCatalogEntry> raw = this.objectMapper.readValue(in, new TypeReference<List<McpCatalogEntry>>() {});
            for (McpCatalogEntry entry : raw) {
                if (entry.id() == null || entry.id().isBlank()) continue;
                sink.put(entry.id(), entry);
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Failed to load MCP catalog from " + resourcePath, e);
        }
    }

    private static String resolveStdioResource() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) return STDIO_RESOURCE_PREFIX + "mac" + STDIO_RESOURCE_SUFFIX;
        if (name.contains("win")) return STDIO_RESOURCE_PREFIX + "windows" + STDIO_RESOURCE_SUFFIX;
        if (name.contains("nix") || name.contains("nux") || name.contains("linux") || name.contains("bsd")) {
            return STDIO_RESOURCE_PREFIX + "linux" + STDIO_RESOURCE_SUFFIX;
        }
        return null;
    }

    public List<McpCatalogEntry> getCatalog() {
        return this.entriesOrdered;
    }

    public Optional<McpCatalogEntry> findById(String id) {
        return Optional.ofNullable(this.entriesById.get(id));
    }

    public Optional<McpCatalogEntry> findByServerName(String serverName) {
        Optional<McpCatalogEntry> direct = findById(serverName);
        if (direct.isPresent()) return direct;
        for (String osSuffix : OS_ID_SUFFIXES) {
            Optional<McpCatalogEntry> osMatch = findById(serverName + osSuffix);
            if (osMatch.isPresent()) return osMatch;
        }
        return Optional.empty();
    }

    public static String stripOsSuffix(String id) {
        if (id == null) return null;
        for (String osSuffix : OS_ID_SUFFIXES) {
            if (id.endsWith(osSuffix)) return id.substring(0, id.length() - osSuffix.length());
        }
        return id;
    }

    private static final String[] OS_ID_SUFFIXES = {"-macOS", "-Linux", "-Windows"};

    public List<McpCatalogEntry> getByTier(int tier) {
        return this.entriesOrdered.stream().filter(e -> e.tier() == tier).toList();
    }

    public McpServerInfo instantiate(McpCatalogEntry entry, McpCatalogEntry.TransportSpec transport,
            Map<String, String> userValues) {
        if (entry == null) throw new IllegalArgumentException("entry must not be null");
        if (transport == null) {
            if (entry.transports().isEmpty()) {
                throw new IllegalArgumentException("Catalog entry has no transports: " + entry.id());
            }
            transport = entry.transports().getFirst();
        }
        Map<String, String> values = userValues == null ? Map.of() : userValues;
        String url = substitutePlaceholders(transport.urlTemplate(), values);
        long now = System.currentTimeMillis();

        String connectionJson;
        try {
            connectionJson = buildConnectionJson(transport, url, values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build connection JSON for catalog entry " + entry.id(), e);
        }

        return new McpServerInfo(transport.type(), stripOsSuffix(entry.id()), entry.description(),
                now, now, connectionJson, entry.category(), entry.tags());
    }

    private String buildConnectionJson(McpCatalogEntry.TransportSpec transport, String url,
            Map<String, String> values) throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        if (transport.requiredHeaders() != null) {
            for (McpCatalogEntry.HeaderSpec h : transport.requiredHeaders()) {
                if (h.key() != null && !h.key().isBlank()) {
                    headers.put(h.key(), h.value() == null ? "" : h.value());
                }
            }
        }
        HttpConnectionParametersWithExtras.OAuth oauth = null;
        if (transport.oauthDefaults() != null
                && transport.oauthDefaults().issuerUri() != null
                && !transport.oauthDefaults().issuerUri().isBlank()) {
            String issuer = substitutePlaceholders(transport.oauthDefaults().issuerUri(), values);
            String clientIdTemplate = pickEnvTemplate(transport.requiredEnv(), "_OAUTH_CLIENT_ID");
            String clientSecretTemplate = pickEnvTemplate(transport.requiredEnv(), "_OAUTH_CLIENT_SECRET");
            oauth = new HttpConnectionParametersWithExtras.OAuth(
                    issuer, null, null,
                    clientIdTemplate, clientSecretTemplate,
                    transport.oauthDefaults().scopes(), null);
        }
        Object record = switch (transport.type()) {
            case STREAMABLE_HTTP -> new HttpConnectionParametersWithExtras.StreamableHttp(
                    url, transport.endpoint(), headers, transport.requiredEnv(), oauth);
            case SSE -> new HttpConnectionParametersWithExtras.Sse(
                    url, transport.endpoint(), headers, transport.requiredEnv(), oauth);
            case STDIO -> {
                ObjectNode obj = objectMapper.createObjectNode();
                obj.put("command", url == null ? "" : url);
                if (transport.args() != null && !transport.args().isEmpty()) {
                    ArrayNode argsArr = objectMapper.createArrayNode();
                    for (String a : transport.args()) argsArr.add(substitutePlaceholders(a, values));
                    obj.set("args", argsArr);
                }
                if (transport.env() != null && !transport.env().isEmpty()) {
                    ObjectNode envObj = objectMapper.createObjectNode();
                    transport.env().forEach((k, v) -> envObj.put(k, substitutePlaceholders(v, values)));
                    obj.set("env", envObj);
                }
                yield obj;
            }
        };
        return objectMapper.writeValueAsString(record);
    }

    static String pickEnvTemplate(List<String> requiredEnv, String suffix) {
        if (requiredEnv == null) return null;
        for (String name : requiredEnv) {
            if (name != null && name.endsWith(suffix)) {
                return "${" + name + "}";
            }
        }
        return null;
    }

    public static String substitutePlaceholders(String template, Map<String, String> values) {
        if (template == null || template.isEmpty() || values == null || values.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey() == null) continue;
            String token = "{" + e.getKey() + "}";
            String value = e.getValue() == null ? "" : e.getValue();
            int idx = 0;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int next = result.indexOf(token, idx);
                if (next < 0) {
                    sb.append(result, idx, result.length());
                    break;
                }
                sb.append(result, idx, next).append(value);
                idx = next + token.length();
            }
            result = sb.toString();
        }
        return result;
    }
}
