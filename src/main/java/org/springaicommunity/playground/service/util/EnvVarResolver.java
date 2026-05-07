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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvVarResolver {

    private static final Pattern ANCHORED = Pattern.compile("^\\$\\{([A-Z_]+[A-Z0-9_]*)}$");
    private static final Pattern EMBEDDED = Pattern.compile("\\$\\{([A-Z_]+[A-Z0-9_]*)}");

    private EnvVarResolver() {}

    public static Optional<String> lookup(String name) {
        if (name == null || name.isEmpty()) return Optional.empty();
        String value = System.getenv(name);
        if (value == null) value = System.getProperty(name);
        return Optional.ofNullable(value);
    }

    public static Optional<String> anchoredEnvName(String literal) {
        if (literal == null) return Optional.empty();
        Matcher m = ANCHORED.matcher(literal);
        return m.matches() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public static Set<String> findRefs(String literal) {
        if (literal == null || literal.isEmpty()) return Set.of();
        Set<String> refs = new LinkedHashSet<>();
        Matcher m = EMBEDDED.matcher(literal);
        while (m.find()) refs.add(m.group(1));
        return refs;
    }

    public static String substitute(String literal) {
        if (literal == null || literal.isEmpty()) return literal;
        Matcher m = EMBEDDED.matcher(literal);
        if (!m.find()) return literal;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String replacement = lookup(m.group(1)).orElse(m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Map<String, String> substituteAll(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        input.forEach((k, v) -> out.put(k, substitute(v)));
        return out;
    }

    public static Set<String> missing(Collection<String> names) {
        if (names == null || names.isEmpty()) return Set.of();
        Set<String> missing = new LinkedHashSet<>();
        for (String name : names) {
            if (lookup(name).isEmpty()) missing.add(name);
        }
        return missing;
    }
}
