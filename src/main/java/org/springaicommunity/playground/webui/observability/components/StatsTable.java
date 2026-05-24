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
package org.springaicommunity.playground.webui.observability.components;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import java.util.LinkedHashMap;
import java.util.Map;

public class StatsTable extends Div {

    private final Map<String, Span> valueByKey = new LinkedHashMap<>();
    private boolean firstSection = true;

    public StatsTable() {
        getStyle().set("display", "grid")
                .set("grid-template-columns", "max-content 1fr")
                .set("gap", "var(--lumo-space-xs) var(--lumo-space-l)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("align-items", "baseline")
                .set("width", "100%")
                .set("max-width", "100%")
                .set("box-sizing", "border-box");
    }

    public StatsTable addSection(String title) {
        Span heading = new Span(title);
        heading.getStyle().set("grid-column", "1 / -1")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("font-weight", "600")
                .set("padding-top", firstSection ? "0" : "var(--lumo-space-s)")
                .set("padding-bottom", "var(--lumo-space-xs)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("margin-bottom", "var(--lumo-space-xs)");
        add(heading);
        firstSection = false;
        return this;
    }

    public StatsTable addRow(String label, String initialValue) {
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-weight", "500")
                .set("white-space", "nowrap");

        Span val = new Span(initialValue == null ? "—" : initialValue);
        val.getStyle().set("color", "var(--lumo-body-text-color)")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, monospace)")
                .set("font-weight", "500")
                .set("font-variant-numeric", "tabular-nums")
                .set("overflow-wrap", "anywhere")
                .set("word-break", "break-word");

        valueByKey.put(label, val);
        add(lbl, val);
        return this;
    }

    public void setValue(String label, String value) {
        Span val = valueByKey.get(label);
        if (val != null) val.setText(value == null || value.isEmpty() ? "—" : value);
    }

    public boolean hasRow(String label) {
        return valueByKey.containsKey(label);
    }
}
