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
package org.springaicommunity.playground.webui.mcp.inspector.primitives.client;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.modelcontextprotocol.spec.McpSchema;

public class RootPrimitive extends Div {

    public RootPrimitive(McpSchema.Root root, Runnable onRemove) {
        Span name = new Span(root.name() == null ? "(no name)" : root.name());
        name.getStyle().set("font-weight", "600").set("font-size", "0.95em");
        Span uriSpan = new Span(root.uri() == null ? "" : root.uri());
        uriSpan.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("word-break", "break-all");
        Button remove = new Button(VaadinIcon.CLOSE_SMALL.create());
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR);
        remove.setTooltipText("Remove root");
        remove.addClickListener(e -> onRemove.run());

        VerticalLayout textCol = new VerticalLayout(name, uriSpan);
        textCol.setPadding(false);
        textCol.setSpacing(false);

        HorizontalLayout row = new HorizontalLayout(textCol, remove);
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("gap", "0.6em");
        textCol.getStyle().set("flex-grow", "1");

        setWidthFull();
        getStyle()
                .set("box-sizing", "border-box")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("padding", "0.7em 0.9em")
                .set("background-color", "var(--lumo-base-color)");
        add(row);
    }
}
