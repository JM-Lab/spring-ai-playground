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
package org.springaicommunity.playground.webui.observability;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ObservabilityDashboardSidebar extends WorkspaceSidebar {

    private final VerticalLayout list = new VerticalLayout();
    private final Map<DashboardEntry, Div> rowByEntry = new LinkedHashMap<>();
    private DashboardEntry selected;
    private Consumer<DashboardEntry> selectionListener = e -> {};

    public ObservabilityDashboardSidebar() {
        super("Dashboards");
        list.setPadding(false);
        list.setSpacing(false);
        list.setWidthFull();
        list.getStyle().set("padding", "var(--lumo-space-xs) 0");
        setSidebarContent(verticalScroller(list));
    }

    public void setSections(List<Section> sections) {
        list.removeAll();
        rowByEntry.clear();
        for (Section section : sections) {
            if (section.label() != null && !section.label().isEmpty()) {
                list.add(buildCategoryHeader(section.label()));
            }
            for (DashboardEntry entry : section.entries()) {
                Div row = buildRow(entry, section.label() != null && !section.label().isEmpty());
                rowByEntry.put(entry, row);
                list.add(row);
            }
        }
    }

    public void select(DashboardEntry entry) {
        if (entry == null || entry.equals(selected)) return;
        if (selected != null) {
            Div prev = rowByEntry.get(selected);
            if (prev != null) applySelected(prev, false);
        }
        Div row = rowByEntry.get(entry);
        if (row != null) applySelected(row, true);
        this.selected = entry;
        selectionListener.accept(entry);
    }

    public void setSelectionListener(Consumer<DashboardEntry> listener) {
        this.selectionListener = listener == null ? e -> {} : listener;
    }

    private Span buildCategoryHeader(String label) {
        Span header = new Span(label);
        header.getStyle()
                .set("display", "block")
                .set("padding",
                        "var(--lumo-space-m) var(--lumo-space-m) var(--lumo-space-xs) var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "700")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.07em")
                .set("color", "var(--lumo-tertiary-text-color)");
        return header;
    }

    private Div buildRow(DashboardEntry entry, boolean indented) {
        Div row = new Div();
        row.getStyle().set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("padding",
                        "var(--lumo-space-xs) var(--lumo-space-m) var(--lumo-space-xs) "
                                + (indented ? "var(--lumo-space-l)" : "var(--lumo-space-m)"))
                .set("cursor", "pointer")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin", "1px var(--lumo-space-xs)")
                .set("color", "var(--lumo-body-text-color)")
                .set("transition", "background-color 80ms")
                .set("min-width", "0")
                .set("overflow", "hidden")
                .set("box-sizing", "border-box");
        Icon icon = entry.icon().create();
        icon.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("flex", "0 0 auto")
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)");
        Span label = listItemText(entry.label());
        label.getStyle().set("font-size", "var(--lumo-font-size-s)");
        row.add(icon, label);
        row.getElement().addEventListener("click", e -> select(entry));
        row.getElement().addEventListener("mouseenter",
                e -> row.getElement().executeJs(
                        "if (this.dataset.selected !== '1') this.style.backgroundColor = 'var(--lumo-contrast-5pct)';"));
        row.getElement().addEventListener("mouseleave",
                e -> row.getElement().executeJs(
                        "if (this.dataset.selected !== '1') this.style.backgroundColor = '';"));
        return row;
    }

    private void applySelected(Div row, boolean selected) {
        if (selected) {
            row.getElement().setAttribute("data-selected", "1");
            row.getStyle().set("background-color", "var(--lumo-primary-color-10pct)")
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("font-weight", "600");
        } else {
            row.getElement().removeAttribute("data-selected");
            row.getStyle().set("background-color", "")
                    .set("color", "var(--lumo-body-text-color)")
                    .set("font-weight", "");
        }
    }

    public record Section(String label, List<DashboardEntry> entries) {}

    public record DashboardEntry(String slug, String label, VaadinIcon icon) {}
}
