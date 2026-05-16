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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Set;

public record DefaultToolsPreference(int schemaVersion, String preset, Rule include, Rule exclude) {

    public record Rule(Set<String> names, Set<String> tags, Set<String> categories) {
        public Rule {
            names = names == null ? Set.of() : Set.copyOf(names);
            tags = tags == null ? Set.of() : Set.copyOf(tags);
            categories = categories == null ? Set.of() : Set.copyOf(categories);
        }

        public static Rule empty() {
            return new Rule(Set.of(), Set.of(), Set.of());
        }

        @JsonIgnore
        public boolean isEmpty() {
            return names.isEmpty() && tags.isEmpty() && categories.isEmpty();
        }
    }

    public DefaultToolsPreference {
        if (schemaVersion < 1) schemaVersion = 3;
        if (include == null) include = Rule.empty();
        if (exclude == null) exclude = Rule.empty();
    }

    public static DefaultToolsPreference forPreset(String presetId) {
        return new DefaultToolsPreference(3, presetId, Rule.empty(), Rule.empty());
    }
}
