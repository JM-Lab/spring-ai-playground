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
package org.springaicommunity.playground.webui.common.sidebar;

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public class SidebarFilterBar extends Div {

    public record Config(String searchPlaceholder, String categoryPlaceholder, String tagPlaceholder,
                         int debounceMs) {
        public Config {
            if (searchPlaceholder == null) searchPlaceholder = "Search…";
            if (categoryPlaceholder == null) categoryPlaceholder = "Category";
            if (tagPlaceholder == null) tagPlaceholder = "Tag";
            if (debounceMs <= 0) debounceMs = 200;
        }
    }

    private final TextField searchField = new TextField();
    private final MultiSelectComboBox<String> categoryFilter = new MultiSelectComboBox<>();
    private final MultiSelectComboBox<String> tagFilter = new MultiSelectComboBox<>();
    private Runnable onChange = () -> {};

    public SidebarFilterBar(Config config) {
        this.searchField.setPlaceholder(config.searchPlaceholder());
        this.searchField.setClearButtonVisible(true);
        this.searchField.setValueChangeMode(ValueChangeMode.LAZY);
        this.searchField.setValueChangeTimeout(config.debounceMs());
        this.searchField.setWidthFull();
        this.searchField.addValueChangeListener(e -> this.onChange.run());

        this.categoryFilter.setPlaceholder(config.categoryPlaceholder());
        this.categoryFilter.setWidthFull();
        this.categoryFilter.addValueChangeListener(e -> this.onChange.run());

        this.tagFilter.setPlaceholder(config.tagPlaceholder());
        this.tagFilter.setWidthFull();
        this.tagFilter.addValueChangeListener(e -> this.onChange.run());

        this.searchField.getStyle().set("flex", "1 1 100%");
        this.categoryFilter.getStyle().set("flex", "1 1 80px");
        this.categoryFilter.setMinWidth("0");
        this.tagFilter.getStyle().set("flex", "1 1 80px");
        this.tagFilter.setMinWidth("0");

        add(this.searchField, this.categoryFilter, this.tagFilter);
        getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-xs)")
                .set("padding", "0 var(--lumo-space-s) var(--lumo-space-s) var(--lumo-space-s)");
    }

    public void setOnChange(Runnable callback) {
        this.onChange = callback == null ? () -> {} : callback;
    }

    public String getSearchQuery() {
        String value = this.searchField.getValue();
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public Set<String> getCategorySelection() {
        Set<String> v = this.categoryFilter.getValue();
        return v == null ? Set.of() : Set.copyOf(v);
    }

    public Set<String> getTagSelection() {
        Set<String> v = this.tagFilter.getValue();
        return v == null ? Set.of() : Set.copyOf(v);
    }

    public void setCategoryItems(List<String> ids, Function<String, String> labelGen) {
        this.categoryFilter.setItems(ids);
        if (labelGen != null) this.categoryFilter.setItemLabelGenerator(labelGen::apply);
    }

    public void setTagItems(List<String> tags) {
        this.tagFilter.setItems(tags);
    }

    public void setCategorySelection(Set<String> selection) {
        this.categoryFilter.setValue(selection == null ? Set.of() : selection);
    }

    public void setTagSelection(Set<String> selection) {
        this.tagFilter.setValue(selection == null ? Set.of() : selection);
    }

    public void clear() {
        this.searchField.clear();
        this.categoryFilter.clear();
        this.tagFilter.clear();
    }

    public boolean isActive() {
        return !getSearchQuery().isEmpty()
                || !getCategorySelection().isEmpty()
                || !getTagSelection().isEmpty();
    }
}
