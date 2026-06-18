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
package org.springaicommunity.playground.service.mcp.risk;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class CanonicalHasher {

    private final ObjectMapper mapper;

    public CanonicalHasher(ObjectMapper objectMapper) {
        this.mapper = objectMapper.rebuild()
                .disable(SerializationFeature.INDENT_OUTPUT)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    public String hashTool(ToolSpec spec) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("kind", "tool");
        canonical.put("name", nz(spec.name()));
        canonical.put("description", nz(spec.description()));
        canonical.put("codeType", spec.codeType() == null ? "" : spec.codeType().name());
        canonical.put("code", nz(spec.code()));
        canonical.put("params", canonicalParams(spec.params()));
        canonical.put("staticVariables", canonicalStaticVariables(spec.staticVariables()));
        canonical.put("sandbox", canonicalSandbox(spec.sandboxOverrides()));
        canonical.put("category", nz(spec.category()));
        canonical.put("tags", sortedList(spec.tags()));
        return hash(canonical);
    }

    public String hashServer(McpServerInfo info) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("kind", "server");
        canonical.put("name", nz(info.serverName()));
        canonical.put("description", nz(info.description()));
        canonical.put("category", nz(info.category()));
        canonical.put("transport", info.mcpTransportType() == null ? "" : info.mcpTransportType().name());
        canonical.put("tags", sortedList(info.tags()));
        canonical.put("connection", canonicalConnection(info.connectionAsJson()));
        return hash(canonical);
    }

    private List<Map<String, Object>> canonicalParams(List<ToolSpec.ToolParamSpec> params) {
        if (params == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ToolSpec.ToolParamSpec p : params) {
            if (p == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", nz(p.name()));
            entry.put("type", p.type() == null ? "" : p.type().name());
            entry.put("required", p.required());
            entry.put("description", nz(p.description()));
            out.add(entry);
        }
        out.sort(Comparator.comparing(m -> String.valueOf(m.get("name"))));
        return out;
    }

    private List<Map<String, Object>> canonicalStaticVariables(List<Map.Entry<String, String>> staticVariables) {
        if (staticVariables == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, String> e : staticVariables) {
            if (e == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", nz(e.getKey()));
            entry.put("value", nz(e.getValue()));
            out.add(entry);
        }
        out.sort(Comparator.<Map<String, Object>, String>comparing(m -> String.valueOf(m.get("key")))
                .thenComparing(m -> String.valueOf(m.get("value"))));
        return out;
    }

    private Map<String, Object> canonicalSandbox(ToolSpec.SandboxOverrides sandbox) {
        ToolSpec.SandboxOverrides s = sandbox == null ? ToolSpec.SandboxOverrides.empty() : sandbox;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("addAllowClasses", sortedList(s.addAllowClasses()));
        out.put("removeAllowClasses", sortedList(s.removeAllowClasses()));
        out.put("addDenyClasses", sortedList(s.addDenyClasses()));
        out.put("removeDenyClasses", sortedList(s.removeDenyClasses()));
        out.put("networkMode", nz(s.networkMode()));
        out.put("hostsAllow", sortedList(s.hostsAllow()));
        out.put("fileRead", s.fileRead());
        out.put("fileWrite", s.fileWrite());
        out.put("fsBasePath", nz(s.fsBasePath()));
        return out;
    }

    private Object canonicalConnection(String connectionAsJson) {
        if (connectionAsJson == null || connectionAsJson.isBlank()) return "";
        try {
            return this.mapper.readValue(connectionAsJson, Object.class);
        } catch (JacksonException e) {
            return connectionAsJson;
        }
    }

    private String hash(Object canonical) {
        try {
            return sha256Hex(this.mapper.writeValueAsBytes(canonical));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to canonicalize content for hashing", e);
        }
    }

    private static List<String> sortedList(Set<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return new ArrayList<>(new TreeSet<>(values));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes == null ? new byte[0] : bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String input) {
        return sha256Hex(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
    }
}
