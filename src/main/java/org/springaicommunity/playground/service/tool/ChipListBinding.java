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

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ChipListBinding {

    private final Set<String> items;
    private final Set<String> selected;

    public ChipListBinding(Collection<String> baseline) {
        this.items = new LinkedHashSet<>();
        this.selected = new LinkedHashSet<>();
        if (baseline != null) {
            for (String entry : baseline) {
                String trimmed = normalize(entry);
                if (trimmed != null) {
                    this.items.add(trimmed);
                    this.selected.add(trimmed);
                }
            }
        }
    }

    public boolean add(String raw) {
        String value = normalize(raw);
        if (value == null) return false;
        boolean newItem = this.items.add(value);
        boolean newlySelected = this.selected.add(value);
        return newItem || newlySelected;
    }

    public void replaceSelected(Collection<String> next) {
        this.selected.clear();
        if (next == null) return;
        for (String entry : next) {
            String value = normalize(entry);
            if (value != null) this.selected.add(value);
        }
    }

    public Set<String> items() {
        return Collections.unmodifiableSet(this.items);
    }

    public Set<String> selected() {
        return Collections.unmodifiableSet(this.selected);
    }

    private static String normalize(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
