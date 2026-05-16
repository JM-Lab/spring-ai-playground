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
import org.springaicommunity.playground.service.tool.ToolSpec;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolActivationCalculatorTest {

    private static ToolSpec toolWith(List<Entry<String, String>> staticVars) {
        return new ToolSpec("id", "n", "d", staticVars, List.of(), "code",
                ToolSpec.CodeType.Javascript, null);
    }

    private static ToolActivationCalculator calc(Map<String, String> env) {
        return new ToolActivationCalculator(env::get);
    }

    @Test
    void noEnvVarsDeclaredIsActive() {
        ToolActivationCalculator c = calc(Map.of());
        assertEquals(ToolActivationCalculator.State.ACTIVE, c.calculate(toolWith(List.of())));
    }

    @Test
    void declaredButUnsetEnvVarIsMissingRequirements() {
        ToolActivationCalculator c = calc(Map.of());  // empty env
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}")));
        assertEquals(ToolActivationCalculator.State.MISSING_REQUIREMENTS, c.calculate(tool));
    }

    @Test
    void declaredAndSetEnvVarIsActive() {
        ToolActivationCalculator c = calc(Map.of("OPENAI_API_KEY", "sk-test"));
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}")));
        assertEquals(ToolActivationCalculator.State.ACTIVE, c.calculate(tool));
    }

    @Test
    void blankEnvVarValueIsMissingRequirements() {
        ToolActivationCalculator c = calc(Map.of("OPENAI_API_KEY", "   "));
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}")));
        assertEquals(ToolActivationCalculator.State.MISSING_REQUIREMENTS, c.calculate(tool));
    }

    @Test
    void multipleEnvVarsAllRequiredAnyMissingFlips() {
        ToolActivationCalculator c = calc(Map.of("GOOGLE_API_KEY", "k1"));  // PSE_ID missing
        ToolSpec tool = toolWith(List.of(
                Map.entry("googleApiKey", "${GOOGLE_API_KEY}"),
                Map.entry("pseId", "${PSE_ID}")));
        assertEquals(ToolActivationCalculator.State.MISSING_REQUIREMENTS, c.calculate(tool));
    }

    @Test
    void paramStylePlaceholdersAreNotEnvVars() {
        ToolActivationCalculator c = calc(Map.of());
        ToolSpec tool = toolWith(List.of(Map.entry("template", "fetch ${pageUrl}")));
        assertEquals(ToolActivationCalculator.State.ACTIVE, c.calculate(tool),
                "lowercase placeholders are param refs, not env var refs");
    }

    @Test
    void mixedConstantAndEnvVarStaticVarsHandledCorrectly() {
        // googlePseSearch shape — baseUrl constant + googleApiKey env + pseId env
        ToolActivationCalculator c = calc(Map.of("GOOGLE_API_KEY", "k", "PSE_ID", "id"));
        ToolSpec tool = toolWith(List.of(
                Map.entry("baseUrl", "https://www.googleapis.com/customsearch/v1"),
                Map.entry("method", "GET"),
                Map.entry("googleApiKey", "${GOOGLE_API_KEY}"),
                Map.entry("pseId", "${PSE_ID}")));
        assertEquals(ToolActivationCalculator.State.ACTIVE, c.calculate(tool));
        assertEquals(List.of("GOOGLE_API_KEY", "PSE_ID"), c.declaredEnvVars(tool));
    }

    @Test
    void nullToolIsDraft() {
        assertEquals(ToolActivationCalculator.State.DRAFT, calc(Map.of()).calculate(null));
    }

    @Test
    void draftFlagFlipsStateToDraftEvenWhenEnvSatisfied() {
        ToolActivationCalculator c = calc(Map.of("OPENAI_API_KEY", "sk-test"));
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}"))).withDraft(true);
        assertEquals(ToolActivationCalculator.State.DRAFT, c.calculate(tool));
    }

    @Test
    void draftFlagBeatsMissingRequirements() {
        ToolActivationCalculator c = calc(Map.of());
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}"))).withDraft(true);
        assertEquals(ToolActivationCalculator.State.DRAFT, c.calculate(tool));
    }

    @Test
    void publishedToolBehavesAsBefore() {
        ToolActivationCalculator c = calc(Map.of("OPENAI_API_KEY", "sk-test"));
        ToolSpec tool = toolWith(List.of(Map.entry("apiKey", "${OPENAI_API_KEY}"))).withDraft(false);
        assertEquals(ToolActivationCalculator.State.ACTIVE, c.calculate(tool));
    }

    @Test
    void declaredEnvVarsAreDeduplicated() {
        ToolActivationCalculator c = calc(Map.of());
        ToolSpec tool = toolWith(List.of(
                Map.entry("a", "${SAME_VAR}"),
                Map.entry("b", "${SAME_VAR}")));
        assertEquals(List.of("SAME_VAR"), c.declaredEnvVars(tool));
    }

    @Test
    void enumHasFourStates() {
        assertEquals(4, ToolActivationCalculator.State.values().length);
        assertEquals(ToolActivationCalculator.State.ACTIVE, ToolActivationCalculator.State.values()[0]);
        assertTrue(ToolActivationCalculator.State.values()[ToolActivationCalculator.State.values().length - 1] == ToolActivationCalculator.State.DRAFT);
    }
}
