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

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTrustSignalTest {

    @Test
    void vendorOfficialParsedFromCanonicalLowerKebab() {
        assertEquals(McpTrustSignal.VENDOR_OFFICIAL, McpTrustSignal.fromCatalogValue("vendor-official"));
    }

    @Test
    void communityParsedFromTwoLabels() {
        assertEquals(McpTrustSignal.COMMUNITY_CURATED, McpTrustSignal.fromCatalogValue("community"));
        assertEquals(McpTrustSignal.COMMUNITY_CURATED, McpTrustSignal.fromCatalogValue("community-curated"));
    }

    @Test
    void underscoreVariantsParsedSameAsKebab() {
        assertEquals(McpTrustSignal.VENDOR_OFFICIAL, McpTrustSignal.fromCatalogValue("vendor_official"));
        assertEquals(McpTrustSignal.AGED_6MO, McpTrustSignal.fromCatalogValue("aged_6mo"));
    }

    @Test
    void unknownReturnsNull() {
        assertNull(McpTrustSignal.fromCatalogValue("unknown-signal"));
        assertNull(McpTrustSignal.fromCatalogValue(""));
        assertNull(McpTrustSignal.fromCatalogValue(null));
    }

    @Test
    void parseAllSkipsUnknownEntries() {
        Set<McpTrustSignal> result = McpTrustSignal.parseAll(Set.of("vendor-official", "garbage", "community"));
        assertEquals(2, result.size());
        assertTrue(result.contains(McpTrustSignal.VENDOR_OFFICIAL));
        assertTrue(result.contains(McpTrustSignal.COMMUNITY_CURATED));
    }

    @Test
    void parseAllHandlesNullAndEmpty() {
        assertTrue(McpTrustSignal.parseAll(null).isEmpty());
        assertTrue(McpTrustSignal.parseAll(Set.of()).isEmpty());
    }
}
