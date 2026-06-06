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

import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;

import java.util.Locale;

public final class McpAuthClassifier {

    public static McpAuthMode fromCatalogAuth(String catalogAuth, McpCatalogEntry.OAuthDefaults oauthDefaults) {
        if (catalogAuth == null || catalogAuth.isBlank()) return McpAuthMode.NONE;
        String normalized = catalogAuth.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "NONE" -> McpAuthMode.NONE;
            case "API_KEY", "APIKEY", "API-KEY" -> McpAuthMode.API_KEY;
            case "BEARER" -> McpAuthMode.BEARER;
            case "OAUTH" -> isStandardOauth(oauthDefaults) ? McpAuthMode.OAUTH_STANDARD : McpAuthMode.OAUTH_CUSTOM;
            case "OAUTH_OR_PAT" -> McpAuthMode.OAUTH_STANDARD;
            case "OAUTH_CUSTOM", "OAUTH-CUSTOM" -> McpAuthMode.OAUTH_CUSTOM;
            default -> McpAuthMode.NONE;
        };
    }

    static boolean isStandardOauth(McpCatalogEntry.OAuthDefaults oauthDefaults) {
        if (oauthDefaults == null) return true;
        String issuer = oauthDefaults.issuerUri();
        if (issuer == null || issuer.isBlank()) return true;
        return !issuer.contains("oob")
                && !issuer.contains("custom")
                && !issuer.contains("manual-paste");
    }

    private McpAuthClassifier() {}
}
