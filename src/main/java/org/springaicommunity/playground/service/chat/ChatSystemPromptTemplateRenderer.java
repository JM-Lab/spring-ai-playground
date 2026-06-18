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

import org.springframework.ai.template.TemplateRenderer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Spring AI TemplateRenderer for {{variable}} system-prompt templates. Double braces (industry convention) are used
// instead of the ST4 single-brace default so literal { } in prompts (JSON examples, code) never collide with
// variables. Token form: {{name}}, {{name:type}}, {{name:type(args)}}, each optionally ending in =default.
// Types: text (default), multiline, number(min,max), select(a,b,c), list(a,b,c, max=N).
@Component
public class ChatSystemPromptTemplateRenderer implements TemplateRenderer {

    public enum VariableType { TEXT, MULTILINE, NUMBER, SELECT, LIST }

    public record VariableSpec(String name, VariableType type, List<String> options, Double min, Double max,
            Integer limit, String defaultValue) {
    }

    private static final Pattern VARIABLE =
            Pattern.compile("\\{\\{(\\w+)(?::(\\w+)(?:\\(([^)]*)\\))?)?(?:=([^}]*))?\\}\\}");

    public List<String> variables(String template) {
        return variableSpecs(template).stream().map(VariableSpec::name).toList();
    }

    public List<VariableSpec> variableSpecs(String template) {
        if (template == null) return List.of();
        Map<String, VariableSpec> ordered = new LinkedHashMap<>();
        Matcher matcher = VARIABLE.matcher(template);
        while (matcher.find()) {
            ordered.putIfAbsent(matcher.group(1),
                    toSpec(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
        }
        return List.copyOf(ordered.values());
    }

    public String render(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) return "";
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values == null ? null : values.get(matcher.group(1));
            if (value == null || value.isBlank()) {
                String fallback = matcher.group(4);
                value = fallback == null ? "" : fallback;
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    @Override
    public String apply(String template, Map<String, ?> variables) {
        Map<String, String> values = new LinkedHashMap<>();
        if (variables != null) variables.forEach((name, value) -> values.put(name, String.valueOf(value)));
        return render(template, values);
    }

    private VariableSpec toSpec(String name, String rawType, String args, String defaultValue) {
        VariableType type = parseType(rawType);
        List<String> parts = args == null ? List.of()
                : Arrays.stream(args.split(",")).map(String::trim).toList();
        return switch (type) {
            case NUMBER -> new VariableSpec(name, type, List.of(), numberAt(parts, 0), numberAt(parts, 1), null,
                    defaultValue);
            case SELECT -> new VariableSpec(name, type, parts.stream().filter(part -> !part.isEmpty()).toList(),
                    null, null, null, defaultValue);
            case LIST -> listSpec(name, parts, defaultValue);
            default -> new VariableSpec(name, type, List.of(), null, null, null, defaultValue);
        };
    }

    private VariableType parseType(String rawType) {
        if (rawType == null) return VariableType.TEXT;
        try {
            return VariableType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return VariableType.TEXT;
        }
    }

    private VariableSpec listSpec(String name, List<String> parts, String defaultValue) {
        Integer limit = null;
        List<String> options = new ArrayList<>();
        for (String part : parts) {
            if (part.toLowerCase(Locale.ROOT).startsWith("max=")) {
                try {
                    limit = Integer.valueOf(part.substring(4).trim());
                } catch (NumberFormatException e) {
                    limit = null;
                }
            } else if (!part.isEmpty()) {
                options.add(part);
            }
        }
        return new VariableSpec(name, VariableType.LIST, List.copyOf(options), null, null, limit, defaultValue);
    }

    private Double numberAt(List<String> parts, int index) {
        if (index >= parts.size() || parts.get(index).isEmpty()) return null;
        try {
            return Double.valueOf(parts.get(index));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
