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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpCatalogEntry(
        String id,
        String displayName,
        String category,
        Set<String> tags,
        int tier,
        String vendor,
        boolean vendorOfficial,
        String description,
        String stability,
        String regionRestriction,
        boolean tenantIdRequired,
        List<TransportSpec> transports,
        String docsUrl,
        boolean docsAdequate,
        Set<String> trustSignals,
        List<McpToolDescriptor> tools,
        String curator) {

    public McpCatalogEntry {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        trustSignals = trustSignals == null ? Set.of() : Set.copyOf(trustSignals);
        transports = transports == null ? List.of() : List.copyOf(transports);
        tools = tools == null ? List.of() : List.copyOf(tools);
        if (stability == null || stability.isBlank()) stability = "GA";
        if (description == null) description = "";
        if (vendor == null) vendor = "";
        if (docsUrl == null) docsUrl = "";
        if (curator == null) curator = "";
    }

    public McpRiskFactors serverDerivedRiskFactors() {
        McpRiskFactors merged = McpRiskFactors.SAFE_READ_ONLY;
        for (McpToolDescriptor tool : tools) {
            merged = merged.orWith(tool.riskFactors());
        }
        return merged;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransportSpec(
            McpTransportType type,
            String urlTemplate,
            String endpoint,
            String auth,
            List<HeaderSpec> requiredHeaders,
            List<String> requiredEnv,
            List<String> urlPlaceholders,
            OAuthDefaults oauthDefaults,
            List<String> args,
            Map<String, String> env) {

        public TransportSpec {
            requiredHeaders = requiredHeaders == null ? List.of() : List.copyOf(requiredHeaders);
            requiredEnv = requiredEnv == null ? List.of() : List.copyOf(requiredEnv);
            urlPlaceholders = urlPlaceholders == null ? List.of() : List.copyOf(urlPlaceholders);
            args = args == null ? List.of() : List.copyOf(args);
            env = env == null ? Map.of() : new LinkedHashMap<>(env);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeaderSpec(String key, String value) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OAuthDefaults(String issuerUri, List<String> scopes) {
        public OAuthDefaults {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }
}
