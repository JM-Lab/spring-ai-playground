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
package org.springaicommunity.playground.webui.mcp.inspector.primitives;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public final class PrimitiveCardLayout {

    private PrimitiveCardLayout() {}

    public static void applyCardStyle(HasStyle target) {
        target.getStyle()
                .set("box-sizing", "border-box")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0.9em 1.1em")
                .set("margin-bottom", "0.7em")
                .set("background-color", "var(--lumo-base-color)");
    }

    public static HorizontalLayout titleRow(VaadinIcon iconForRunButton, String tooltip,
            String displayTitle, Runnable onRun) {
        Button runButton = new Button(iconForRunButton.create());
        runButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ICON);
        if (tooltip != null) runButton.setTooltipText(tooltip);
        runButton.addClickListener(e -> onRun.run());

        Span titleSpan = new Span(displayTitle);
        titleSpan.getStyle().set("font-weight", "600").set("font-size", "1em");

        HorizontalLayout row = new HorizontalLayout(runButton, titleSpan);
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("gap", "0.6em");
        return row;
    }

    public static HorizontalLayout titleRow(Component leadingIcon, String displayTitle) {
        Span titleSpan = new Span(displayTitle);
        titleSpan.getStyle().set("font-weight", "600").set("font-size", "1em");

        HorizontalLayout row = new HorizontalLayout(leadingIcon, titleSpan);
        row.setPadding(false);
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("gap", "0.6em");
        return row;
    }

    public static Span subNameLabel(String name) {
        Span span = new Span(name == null ? "" : name);
        span.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin", "0.1em 0 0 2.4em");
        return span;
    }

    public static Span subUriLabel(String uri) {
        Span span = new Span(uri == null ? "" : uri);
        span.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin", "0.2em 0 0 2.4em")
                .set("word-break", "break-all");
        return span;
    }

    public static Div description(String text) {
        if (text == null || text.isBlank()) return null;
        Div desc = new Div();
        desc.setText(text);
        desc.getStyle()
                .set("white-space", "pre-wrap")
                .set("color", "var(--lumo-body-text-color)")
                .set("font-size", "0.9em")
                .set("line-height", "1.5")
                .set("padding", "0.6em 0.9em")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-left", "3px solid var(--lumo-primary-color)")
                .set("border-radius", "0 var(--lumo-border-radius-s) var(--lumo-border-radius-s) 0")
                .set("margin", "0.7em 0 0.3em");
        return desc;
    }
}
