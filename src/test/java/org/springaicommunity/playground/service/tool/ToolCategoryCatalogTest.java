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
package org.springaicommunity.playground.service.tool;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolCategoryCatalog.CategoryDef;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCategoryCatalogTest {

    @Test
    void loadsAllThirteenCategoryIdsInOrder() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        List<CategoryDef> defs = catalog.categories();
        assertEquals(13, defs.size());
        assertEquals("TEXT",   defs.get(0).id());
        assertEquals("DATA",   defs.get(1).id());
        assertEquals("CUSTOM", defs.get(defs.size() - 1).id(),
                "CUSTOM must be last (order=999) so user-defined tools sink to bottom");
    }

    @Test
    void exposesExactlyTheToolCatalogIds() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        Set<String> expected = Set.of(
                "TEXT", "DATA", "DATETIME", "MATH", "ENCODING", "CRYPTO", "SECURITY", "FILE",
                "WEB", "PRODUCTIVITY", "MESSAGING", "AI", "CUSTOM");
        assertEquals(expected, catalog.categoryIds());
    }

    @Test
    void resolveKnownIdReturnsDef() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        CategoryDef def = catalog.resolveOrFallback("CRYPTO");
        assertEquals("CRYPTO", def.id());
        assertEquals("Crypto & Random", def.displayName());
        assertEquals("key", def.icon());
    }

    @Test
    void resolveUnknownIdFallsBackToCustom() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        CategoryDef def = catalog.resolveOrFallback("BOGUS_ID");
        assertEquals("CUSTOM", def.id());
    }

    @Test
    void resolveNullIdFallsBackToCustom() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        CategoryDef def = catalog.resolveOrFallback(null);
        assertEquals("CUSTOM", def.id());
    }

    @Test
    void findReturnsEmptyForUnknown() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        assertTrue(catalog.find("NOPE").isEmpty());
        assertTrue(catalog.find("CRYPTO").isPresent());
    }

    @Test
    void isKnownAcceptsCatalogIdsRejectsOthers() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        assertTrue(catalog.isKnown("TEXT"));
        assertFalse(catalog.isKnown("text"));            // case-sensitive — IDs are uppercase
        assertFalse(catalog.isKnown(""));
        assertFalse(catalog.isKnown(null));
    }

    @Test
    void categoriesListIsImmutable() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        List<CategoryDef> defs = catalog.categories();
        assertThrows(UnsupportedOperationException.class,
                () -> defs.add(new CategoryDef("X", "X", 0, null, null)));
    }

    @Test
    void rejectsCatalogMissingFallbackId() {
        List<CategoryDef> withoutCustom = List.of(new CategoryDef("TEXT", "Text", 10, "font", "test"));
        assertThrows(IllegalStateException.class, () -> new ToolCategoryCatalog(withoutCustom));
    }

    @Test
    void categoriesContainNonNullDisplayName() throws Exception {
        ToolCategoryCatalog catalog = new ToolCategoryCatalog();
        for (CategoryDef categoryDef : catalog.categories()) {
            assertNotNull(categoryDef.displayName(), "displayName missing for " + categoryDef.id());
            assertNotNull(categoryDef.icon(), "icon missing for " + categoryDef.id());
        }
    }
}
