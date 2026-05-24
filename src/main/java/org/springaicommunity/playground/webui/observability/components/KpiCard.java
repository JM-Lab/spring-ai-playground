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

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.shared.Tooltip;

import java.util.List;

public class KpiCard extends Div {

    private final Span labelSpan;
    private final Span valueSpan;
    private final Div sparkSlot;
    private final Tooltip labelTooltip;

    public KpiCard(String label) {
        addClassName("playground-kpi-card");
        getStyle().set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("min-width", "0")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-xs)")
                .set("overflow", "hidden");

        this.labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("white-space", "nowrap")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis");
        this.labelTooltip = Tooltip.forComponent(labelSpan);
        this.labelTooltip.setPosition(Tooltip.TooltipPosition.TOP);

        this.valueSpan = new Span("—");
        this.valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "600")
                .set("overflow-wrap", "anywhere")
                .set("word-break", "break-word")
                .set("min-width", "0");

        this.sparkSlot = new Div();
        this.sparkSlot.getStyle().set("min-height", "20px");

        add(labelSpan, valueSpan, sparkSlot);
    }

    public void setValue(String value) {
        this.valueSpan.setText(value == null ? "—" : value);
    }

    public void setValue(String mainValue, String detail) {
        setValue(mainValue);
        setTooltip(detail);
    }

    public void setTooltip(String text) {
        this.labelTooltip.setText(text == null || text.isBlank() ? null : text);
    }

    public void setLabel(String label) {
        this.labelSpan.setText(label == null ? "" : label);
    }

    public void setSparkline(List<? extends Number> values) {
        this.sparkSlot.removeAll();
        Html svg = SparklineSvg.of(values, 160, 24, "var(--lumo-primary-color)");
        this.sparkSlot.add(svg);
    }

    public void setNavigationTarget(Runnable target) {
        if (target == null) return;
        getStyle().set("cursor", "pointer").set("transition", "border-color 120ms, transform 120ms");
        getElement().addEventListener("mouseenter",
                e -> getStyle().set("border-color", "var(--lumo-primary-color-50pct)"));
        getElement().addEventListener("mouseleave",
                e -> getStyle().set("border-color", "var(--lumo-contrast-10pct)"));
        getElement().addEventListener("click", e -> target.run());
        labelTooltip.setText(labelTooltip.getText() == null
                ? "Click to drill down"
                : labelTooltip.getText() + " · Click to drill down");
    }
}
