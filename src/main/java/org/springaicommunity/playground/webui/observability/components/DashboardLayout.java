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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.shared.Tooltip;

public final class DashboardLayout {

    private DashboardLayout() {}

    public static Div chartGrid() {
        Div grid = new Div();
        grid.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(500px, 1fr))")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("align-items", "stretch");
        return grid;
    }

    public static Div kpiGrid(Component... cards) {
        Div grid = new Div();
        grid.getStyle().set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(170px, 1fr))")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("align-items", "stretch");
        for (Component c : cards) grid.add(c);
        return grid;
    }

    public static Span kpiGroupHeader(String text) {
        Span s = new Span(text);
        s.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.04em")
                .set("margin", "var(--lumo-space-s) 0 var(--lumo-space-xs) 0")
                .set("display", "block");
        return s;
    }

    public static Component chartCard(String title, String subtitle, Component body) {
        return chartCard(title, subtitle, null, body, null);
    }

    public static Component chartCard(String title, String subtitle, String infoText, Component body) {
        return chartCard(title, subtitle, infoText, body, null);
    }

    public static Component chartCard(String title, String subtitle, Component body, Runnable expandTo) {
        return chartCard(title, subtitle, null, body, expandTo);
    }

    public static Component chartCard(String title, String subtitle, String infoText, Component body,
            Runnable expandTo) {
        Div card = new Div();
        card.addClassName("observability-widget-card");
        card.getStyle().set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-xs)")
                .set("resize", "vertical")
                .set("overflow", "hidden")
                .set("min-height", "360px")
                .set("height", "400px");

        H4 h = new H4(title);
        h.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-m)")
                .set("font-weight", "600").set("flex", "0 0 auto");
        if (infoText != null && !infoText.isBlank()) {
            Tooltip.forComponent(h).withText(infoText).withPosition(Tooltip.TooltipPosition.TOP);
        }

        Span sub = new Span(subtitle == null ? "" : subtitle);
        sub.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("margin-inline-start", "var(--lumo-space-s)")
                .set("flex", "1 1 auto")
                .set("min-width", "0")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        Button hideBtn = new Button(VaadinIcon.CLOSE_SMALL.create());
        hideBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        hideBtn.setTooltipText("Hide this widget — restore from settings (cog) → Restore hidden widgets");
        hideBtn.addClickListener(e -> card.setVisible(false));

        HorizontalLayout titleRow = new HorizontalLayout(h, sub);

        if (body instanceof ChartCanvas chartCanvas) {
            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(false);
            actions.setPadding(false);
            actions.setVisible(false);

            actions.add(chartActionButton(VaadinIcon.DOWNLOAD, "Save as PNG", e -> chartCanvas.savePng()));
            actions.add(chartActionButton(VaadinIcon.REFRESH, "Restore zoom and pan", e -> chartCanvas.restoreZoom()));
            if (chartCanvas.isMagicTypeSupported()) {
                actions.add(chartActionButton(VaadinIcon.BAR_CHART, "Toggle line/bar",
                        e -> chartCanvas.toggleMagicType()));
            }

            Button toolboxBtn = new Button(VaadinIcon.MENU.create());
            toolboxBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            toolboxBtn.setTooltipText("Chart actions");
            toolboxBtn.addClickListener(e -> actions.setVisible(!actions.isVisible()));

            titleRow.add(actions, toolboxBtn);
        }
        if (expandTo != null) {
            Button expandBtn = new Button(VaadinIcon.EXPAND_SQUARE.create());
            expandBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            expandBtn.setTooltipText("Open the full dashboard for this widget");
            expandBtn.addClickListener(e -> expandTo.run());
            titleRow.add(expandBtn);
        }
        titleRow.add(hideBtn);
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        titleRow.setWidthFull();
        titleRow.setSpacing(false);
        titleRow.setPadding(false);

        Div bodyHolder = new Div(body);
        bodyHolder.getStyle().set("flex", "1 1 auto").set("min-height", "0").set("display", "flex");
        if (body instanceof ChartCanvas cc) {
            cc.setHeight("100%");
            cc.setWidth("100%");
        }

        card.add(titleRow, bodyHolder);
        return card;
    }

    private static Button chartActionButton(VaadinIcon icon, String tooltip,
            ComponentEventListener<ClickEvent<Button>> click) {
        Button btn = new Button(icon.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        btn.setTooltipText(tooltip);
        btn.addClickListener(click);
        return btn;
    }
}
