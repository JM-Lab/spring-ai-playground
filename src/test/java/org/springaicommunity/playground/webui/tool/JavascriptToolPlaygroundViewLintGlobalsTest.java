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
package org.springaicommunity.playground.webui.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavascriptToolPlaygroundViewLintGlobalsTest {

    @Test
    void alwaysIncludesSandboxRuntimeGlobals() {
        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(List.of(), List.of());

        for (String expected : JavascriptToolPlaygroundView.SANDBOX_RUNTIME_GLOBALS) {
            assertTrue(globals.containsKey(expected), "missing sandbox global: " + expected);
            assertEquals(Boolean.FALSE, globals.get(expected),
                    "sandbox global must be read-only (false), not writable");
        }
    }

    @Test
    void mergesParamNamesIntoGlobals() {
        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(List.of("text", "toTimeZone"), List.of());

        assertTrue(globals.containsKey("text"));
        assertTrue(globals.containsKey("toTimeZone"));
        assertEquals(Boolean.FALSE, globals.get("text"));
    }

    @Test
    void mergesStaticVariableKeysIntoGlobals() {
        List<Map.Entry<String, String>> staticVars = List.of(
                Map.entry("API_KEY", "secret"),
                Map.entry("BASE_URL", "https://example.com"));

        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(List.of(), staticVars);

        assertTrue(globals.containsKey("API_KEY"));
        assertTrue(globals.containsKey("BASE_URL"));
    }

    @Test
    void skipsBlankAndNullParamNames() {
        List<String> names = new ArrayList<>();
        names.add("good");
        names.add("");
        names.add("   ");
        names.add(null);

        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(names, List.of());

        assertTrue(globals.containsKey("good"));
        assertFalse(globals.containsKey(""));
        assertFalse(globals.containsKey("   "));
        assertFalse(globals.containsKey(null));
    }

    @Test
    void laterEntryOverwritesEarlierForSameKey() {
        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(List.of("Java"), List.of());

        assertTrue(globals.containsKey("Java"));
        assertEquals(Boolean.FALSE, globals.get("Java"));
    }

    @Test
    void tolerantOfNullArguments() {
        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(null, null);
        assertTrue(globals.containsKey("Java"));
        assertTrue(globals.containsKey("safety"));
    }

    @Test
    void preservesInsertionOrder() {
        Map<String, Boolean> globals = JavascriptToolPlaygroundView
                .buildLintGlobals(List.of("alpha", "beta"), List.of(Map.entry("zeta", "v")));

        List<String> keys = List.copyOf(globals.keySet());
        int alphaIdx = keys.indexOf("alpha");
        int betaIdx = keys.indexOf("beta");
        int zetaIdx = keys.indexOf("zeta");
        assertTrue(alphaIdx < betaIdx, "params keep insertion order");
        assertTrue(betaIdx < zetaIdx, "static vars come after params");
        assertTrue(keys.indexOf("Java") < alphaIdx, "sandbox globals come first");
    }
}
