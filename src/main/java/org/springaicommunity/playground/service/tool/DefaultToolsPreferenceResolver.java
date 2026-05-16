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

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class DefaultToolsPreferenceResolver {

    private final DefaultToolPresetCatalog presetCatalog;

    public DefaultToolsPreferenceResolver(DefaultToolPresetCatalog presetCatalog) {
        this.presetCatalog = presetCatalog;
    }

    public Set<String> resolveActiveNames(DefaultToolsPreference preference, Collection<ToolSpec> defaults) {
        Set<String> allNames = new LinkedHashSet<>();
        for (ToolSpec spec : defaults) allNames.add(spec.name());

        Set<String> base = baseFromPreset(preference.preset(), allNames);
        Set<String> active = new LinkedHashSet<>(base);

        applyAdditive(active, preference.include(), defaults, allNames);
        applySubtractive(active, preference.exclude(), defaults);

        return active;
    }

    private Set<String> baseFromPreset(String presetId, Set<String> allDefaultNames) {
        if (presetId == null || presetId.isBlank()) return new LinkedHashSet<>();
        var preset = presetCatalog.findById(presetId).orElse(null);
        if (preset == null) return new LinkedHashSet<>();
        if (preset.tools().isEmpty()) return new LinkedHashSet<>(allDefaultNames);
        return new LinkedHashSet<>(preset.tools());
    }

    private void applyAdditive(Set<String> active, DefaultToolsPreference.Rule include,
            Collection<ToolSpec> defaults, Set<String> allNames) {
        if (include.isEmpty()) return;
        for (String name : include.names()) {
            if (allNames.contains(name)) active.add(name);
        }
        if (!include.tags().isEmpty()) {
            for (ToolSpec spec : defaults) {
                if (intersects(spec.tags(), include.tags())) active.add(spec.name());
            }
        }
        if (!include.categories().isEmpty()) {
            for (ToolSpec spec : defaults) {
                if (include.categories().contains(spec.category())) active.add(spec.name());
            }
        }
    }

    private void applySubtractive(Set<String> active, DefaultToolsPreference.Rule exclude,
            Collection<ToolSpec> defaults) {
        if (exclude.isEmpty()) return;
        if (!exclude.names().isEmpty()) active.removeAll(exclude.names());
        if (!exclude.tags().isEmpty()) {
            Set<String> toRemove = new HashSet<>();
            for (ToolSpec spec : defaults) {
                if (intersects(spec.tags(), exclude.tags())) toRemove.add(spec.name());
            }
            active.removeAll(toRemove);
        }
        if (!exclude.categories().isEmpty()) {
            Set<String> toRemove = new HashSet<>();
            for (ToolSpec spec : defaults) {
                if (exclude.categories().contains(spec.category())) toRemove.add(spec.name());
            }
            active.removeAll(toRemove);
        }
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return false;
        for (String t : a) if (b.contains(t)) return true;
        return false;
    }
}
