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

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public enum McpTrustSignal {

    VENDOR_OFFICIAL,
    COMMUNITY_CURATED,
    AUDITED,
    WIDELY_USED,
    AGED_6MO,
    LAST_REVIEWED_RECENT;

    public static McpTrustSignal fromCatalogValue(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "vendor-official" -> VENDOR_OFFICIAL;
            case "community", "community-curated" -> COMMUNITY_CURATED;
            case "audited" -> AUDITED;
            case "widely-used" -> WIDELY_USED;
            case "aged-6mo" -> AGED_6MO;
            case "last-reviewed-recent" -> LAST_REVIEWED_RECENT;
            default -> null;
        };
    }

    public static Set<McpTrustSignal> parseAll(Set<String> raw) {
        if (raw == null || raw.isEmpty()) return Set.of();
        LinkedHashSet<McpTrustSignal> out = new LinkedHashSet<>();
        for (String v : raw) {
            McpTrustSignal parsed = fromCatalogValue(v);
            if (parsed != null) out.add(parsed);
        }
        return Set.copyOf(out);
    }
}
