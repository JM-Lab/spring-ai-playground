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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.observability.Window;

public abstract class BaseDashboardTab extends VerticalLayout {

    protected final Div content = new Div();
    protected final Div footer = new Div();

    protected BaseDashboardTab() {
        setPadding(true);
        setSpacing(false);
        setSizeFull();

        content.setWidthFull();
        content.getStyle().set("display", "flex").set("flex-direction", "column")
                .set("gap", "var(--lumo-space-m)").set("flex", "1 1 auto")
                .set("overflow-y", "auto").set("overflow-x", "hidden")
                .set("min-height", "0");

        footer.setWidthFull();
        footer.getStyle().set("color", "var(--lumo-body-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                .set("border-top", "1px solid var(--lumo-contrast-20pct)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("word-break", "break-all")
                .set("box-sizing", "border-box")
                .set("flex", "0 0 auto");

        add(content, footer);
        expand(content);
    }

    public Component getSettingsPanel() {
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.setWidthFull();
        panel.getStyle().set("gap", "var(--lumo-space-s)");
        panel.add(headerRow(VaadinIcon.GRID_BIG, "Layout"));
        Button restoreWidgets = new Button("Restore hidden widgets",
                VaadinIcon.PLUS_SQUARE_O.create());
        restoreWidgets.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        restoreWidgets.addClickListener(e -> restoreHiddenWidgets());
        restoreWidgets.setTooltipText(
                "Show every chart that was previously closed via the × on its card");
        panel.add(restoreWidgets);
        return panel;
    }

    public void restoreHiddenWidgets() {
        restoreHiddenIn(content);
    }

    private void restoreHiddenIn(Component c) {
        if (c instanceof Div d && d.hasClassName("observability-widget-card") && !d.isVisible()) {
            d.setVisible(true);
        }
        c.getChildren().forEach(this::restoreHiddenIn);
    }

    private HorizontalLayout headerRow(VaadinIcon icon, String text) {
        Span lbl = new Span(text);
        lbl.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout row = new HorizontalLayout(icon.create(), lbl);
        row.setSpacing(true);
        row.setPadding(false);
        row.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return row;
    }

    public abstract void refresh(Window window);

    protected void setStatus(String text) {
        footer.setText(text == null ? "" : text);
    }
}
