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
package org.springaicommunity.playground.service.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvVarResolverTest {

    private static final String NAME_A = "ENV_RESOLVER_TEST_A";
    private static final String NAME_B = "ENV_RESOLVER_TEST_B";
    private static final String NAME_MISSING = "ENV_RESOLVER_TEST_MISSING";

    @AfterEach
    void clearProps() {
        System.clearProperty(NAME_A);
        System.clearProperty(NAME_B);
        System.clearProperty(NAME_MISSING);
    }

    @Test
    void lookupReturnsValueFromSystemProperty() {
        System.setProperty(NAME_A, "alpha");
        assertEquals(Optional.of("alpha"), EnvVarResolver.lookup(NAME_A));
    }

    @Test
    void lookupReturnsEmptyForUnsetName() {
        assertTrue(EnvVarResolver.lookup(NAME_MISSING).isEmpty());
    }

    @Test
    void lookupReturnsEmptyForNullOrBlankName() {
        assertTrue(EnvVarResolver.lookup(null).isEmpty());
        assertTrue(EnvVarResolver.lookup("").isEmpty());
    }

    @Test
    void anchoredEnvNameExtractsExactRef() {
        assertEquals(Optional.of("MY_VAR"), EnvVarResolver.anchoredEnvName("${MY_VAR}"));
    }

    @Test
    void anchoredEnvNameRejectsLowercase() {
        assertTrue(EnvVarResolver.anchoredEnvName("${not_uppercase}").isEmpty());
    }

    @Test
    void anchoredEnvNameRejectsEmbeddedRef() {
        assertTrue(EnvVarResolver.anchoredEnvName("Bearer ${TOKEN}").isEmpty());
    }

    @Test
    void anchoredEnvNameRejectsLeadingDigit() {
        assertTrue(EnvVarResolver.anchoredEnvName("${1VAR}").isEmpty());
    }

    @Test
    void findRefsCapturesEmbeddedAndMultiple() {
        Set<String> refs = EnvVarResolver.findRefs("${A_KEY} and Bearer ${B_TOKEN} plus ${A_KEY}");
        assertEquals(Set.of("A_KEY", "B_TOKEN"), refs);
    }

    @Test
    void findRefsReturnsEmptyForNullOrPlain() {
        assertTrue(EnvVarResolver.findRefs(null).isEmpty());
        assertTrue(EnvVarResolver.findRefs("plain literal value").isEmpty());
    }

    @Test
    void substituteReplacesEmbeddedRefs() {
        System.setProperty(NAME_A, "secret-token");
        assertEquals("Bearer secret-token",
                EnvVarResolver.substitute("Bearer ${" + NAME_A + "}"));
    }

    @Test
    void substituteKeepsUnresolvedRefVerbatim() {
        assertEquals("prefix-${" + NAME_MISSING + "}-suffix",
                EnvVarResolver.substitute("prefix-${" + NAME_MISSING + "}-suffix"));
    }

    @Test
    void substituteMultipleOccurrencesIncludingDuplicate() {
        System.setProperty(NAME_A, "X");
        System.setProperty(NAME_B, "Y");
        String result = EnvVarResolver.substitute(
                "${" + NAME_A + "}-${" + NAME_B + "}-${" + NAME_A + "}");
        assertEquals("X-Y-X", result);
    }

    @Test
    void substituteReturnsNullForNullInput() {
        assertEquals(null, EnvVarResolver.substitute(null));
    }

    @Test
    void substitutePassesThroughLiteralWithoutRefs() {
        assertEquals("plain", EnvVarResolver.substitute("plain"));
    }

    @Test
    void substituteAllAppliesPerValue() {
        System.setProperty(NAME_A, "alpha");
        Map<String, String> input = new LinkedHashMap<>();
        input.put("Authorization", "Bearer ${" + NAME_A + "}");
        input.put("X-Const", "fixed-value");
        Map<String, String> out = EnvVarResolver.substituteAll(input);
        assertEquals("Bearer alpha", out.get("Authorization"));
        assertEquals("fixed-value", out.get("X-Const"));
    }

    @Test
    void substituteAllReturnsEmptyForNullOrEmpty() {
        assertTrue(EnvVarResolver.substituteAll(null).isEmpty());
        assertTrue(EnvVarResolver.substituteAll(Map.of()).isEmpty());
    }

    @Test
    void missingReturnsOnlyUnsetNames() {
        System.setProperty(NAME_A, "value-a");
        Set<String> missing = EnvVarResolver.missing(List.of(NAME_A, NAME_MISSING));
        assertEquals(Set.of(NAME_MISSING), missing);
    }

    @Test
    void missingReturnsEmptyForNullOrEmpty() {
        assertTrue(EnvVarResolver.missing(null).isEmpty());
        assertTrue(EnvVarResolver.missing(List.of()).isEmpty());
    }

    @Test
    void missingAllUnsetReturnsAll() {
        Set<String> missing = EnvVarResolver.missing(List.of(NAME_A, NAME_B, NAME_MISSING));
        assertTrue(missing.contains(NAME_A));
        assertTrue(missing.contains(NAME_B));
        assertTrue(missing.contains(NAME_MISSING));
    }

    @Test
    void substituteDoesNotInterpretReplacementSpecialChars() {
        System.setProperty(NAME_A, "$1\\evil");
        String out = EnvVarResolver.substitute("[${" + NAME_A + "}]");
        assertFalse(out.contains("evil-mangled"));
        assertEquals("[$1\\evil]", out);
    }
}
