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
package org.springaicommunity.playground.service.chat;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSystemPromptTemplateRendererTest {

    private final ChatSystemPromptTemplateRenderer renderer = new ChatSystemPromptTemplateRenderer();

    @Test
    void variablesExtractedInOrderWithoutDuplicates() {
        assertThat(renderer.variables("You are a {{role}} doing {{task}}, a great {{role}}."))
                .containsExactly("role", "task");
    }

    @Test
    void typedVariablesExtractNameOnly() {
        assertThat(renderer.variables(
                "{{tone:select(formal,casual)=formal}} {{count:number(100,2000)=600}} {{notes:multiline}}"))
                .containsExactly("tone", "count", "notes");
    }

    @Test
    void renderFillsProvidedValues() {
        assertThat(renderer.render("Expert in {{domain}}, focus {{focus_area}}.",
                Map.of("domain", "AI safety", "focus_area", "red-teaming")))
                .isEqualTo("Expert in AI safety, focus red-teaming.");
    }

    @Test
    void missingValueFallsBackToDefaultThenBlank() {
        assertThat(renderer.render("Style {{tone:select(formal,casual)=formal}}, topic {{topic}}.", Map.of()))
                .isEqualTo("Style formal, topic .");
    }

    @Test
    void singleBracesAreLeftUntouched() {
        assertThat(renderer.render("Output {\"k\": 1} with {{x}}.", Map.of("x", "v")))
                .isEqualTo("Output {\"k\": 1} with v.");
    }

    @Test
    void nonTemplateReturnedUnchanged() {
        assertThat(renderer.render("Plain prompt with no variables.", Map.of()))
                .isEqualTo("Plain prompt with no variables.");
    }

    @Test
    void nullAndBlankAreSafe() {
        assertThat(renderer.variables(null)).isEmpty();
        assertThat(renderer.render(null, new HashMap<>())).isEmpty();
        assertThat(renderer.render("   ", new HashMap<>())).isEmpty();
    }

    @Test
    void appliesAsSpringAiTemplateRenderer() {
        assertThat(renderer.apply("Limit {{count:number(1,10)=5}} items.", Map.of("count", 7)))
                .isEqualTo("Limit 7 items.");
    }

    @Test
    void variableSpecsParseTypesBoundsOptionsAndDefaults() {
        var specs = renderer.variableSpecs("{{topic}} {{notes:multiline}} {{count:number(100,2000)=600}}"
                + " {{tone:select(formal,casual)=formal}} {{tags:list(a,b,c, max=3)=a}} {{free:list(max=2)}}");
        assertThat(specs).extracting(ChatSystemPromptTemplateRenderer.VariableSpec::type).containsExactly(
                ChatSystemPromptTemplateRenderer.VariableType.TEXT,
                ChatSystemPromptTemplateRenderer.VariableType.MULTILINE,
                ChatSystemPromptTemplateRenderer.VariableType.NUMBER,
                ChatSystemPromptTemplateRenderer.VariableType.SELECT,
                ChatSystemPromptTemplateRenderer.VariableType.LIST,
                ChatSystemPromptTemplateRenderer.VariableType.LIST);
        assertThat(specs.get(2).min()).isEqualTo(100);
        assertThat(specs.get(2).max()).isEqualTo(2000);
        assertThat(specs.get(2).defaultValue()).isEqualTo("600");
        assertThat(specs.get(3).options()).containsExactly("formal", "casual");
        assertThat(specs.get(4).options()).containsExactly("a", "b", "c");
        assertThat(specs.get(4).limit()).isEqualTo(3);
        assertThat(specs.get(4).defaultValue()).isEqualTo("a");
        assertThat(specs.get(5).options()).isEmpty();
        assertThat(specs.get(5).limit()).isEqualTo(2);
    }

    @Test
    void unknownTypeFallsBackToText() {
        var specs = renderer.variableSpecs("{{x:fancy(1,2)}}");
        assertThat(specs).hasSize(1);
        assertThat(specs.get(0).type()).isEqualTo(ChatSystemPromptTemplateRenderer.VariableType.TEXT);
    }
}
