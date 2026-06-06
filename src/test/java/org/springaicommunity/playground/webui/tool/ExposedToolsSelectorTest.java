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
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator.State;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExposedToolsSelectorTest {

    private static final Set<String> BUILTIN_IDS = Set.of("builtin-active", "builtin-missing", "builtin-draft");

    private static ToolSpec spec(String id, String name) {
        return new ToolSpec(id, name, "d", List.of(), List.of(), "code", ToolSpec.CodeType.Javascript, null);
    }

    @Test
    void customsFromKeepsOnlyNonDefaultTools() {
        ToolSpec custom = spec("custom-1", "myTool");
        ToolSpec builtin = spec("builtin-active", "weather");
        assertThat(ExposedToolsSelector.customsFrom(List.of(custom, builtin), BUILTIN_IDS))
                .extracting(ToolSpec::toolId).containsExactly("custom-1");
    }

    @Test
    void exposableBuiltinsFromExcludesMissingRequirementsAndDrafts() {
        ToolActivationCalculator calculator = mock(ToolActivationCalculator.class);
        ToolSpec active = spec("builtin-active", "weather");
        ToolSpec missing = spec("builtin-missing", "github");
        ToolSpec draft = spec("builtin-draft", "scratch").withDraft(true);
        when(calculator.calculate(active)).thenReturn(State.ACTIVE);
        when(calculator.calculate(missing)).thenReturn(State.MISSING_REQUIREMENTS);
        assertThat(ExposedToolsSelector.exposableBuiltinsFrom(List.of(active, missing, draft), BUILTIN_IDS, calculator))
                .extracting(ToolSpec::toolId).containsExactly("builtin-active");
    }

    @Test
    void resolveBuiltinSelectionSelectsExposed() {
        ToolSpec a = spec("builtin-active", "a");
        ToolSpec b = spec("builtin-draft", "b");
        assertThat(ExposedToolsSelector.resolveBuiltinSelection(List.of(a, b), Set.of("builtin-active")))
                .extracting(ToolSpec::toolId).containsExactly("builtin-active");
    }

    @Test
    void computeSettingExposesSelectionAndExcludesUntickedExposable() {
        ToolMcpServerSetting setting = ExposedToolsSelector.computeSetting(true,
                Set.of("custom-1"), Set.of("builtin-active"),
                Set.of("builtin-active", "builtin-draft"));
        assertThat(setting.exposedToolIds()).containsExactlyInAnyOrder("custom-1", "builtin-active");
        assertThat(setting.excludedToolIds()).containsExactly("builtin-draft");
    }

    @Test
    void emptyInputsProduceEmptyResults() {
        assertThat(ExposedToolsSelector.customsFrom(List.of(), Set.of())).isEmpty();
        assertThat(ExposedToolsSelector.resolveBuiltinSelection(List.of(), Set.of())).isEmpty();
    }
}
