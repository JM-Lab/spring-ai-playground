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
package org.springaicommunity.playground.webui.common;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.function.Supplier;

import static org.springaicommunity.playground.webui.VaadinUtils.styledIcon;

public class WorkspaceSettingsDrawer extends Div {

    private final H4 titleElement;
    private final Div bodyDiv;
    private final HorizontalLayout footerLayout;
    private final Div scrim;

    private Supplier<? extends Component> bodyFactory;
    private Runnable onOpenCallback;
    private Button applyButton;
    private Runnable currentApplyAction;
    private boolean opened;

    public WorkspaceSettingsDrawer(String title) {
        this.scrim = buildScrim();

        addClassName("workspace-settings-drawer");
        getStyle().set("position", "absolute")
                .set("top", "var(--lumo-space-m)")
                .set("right", "var(--lumo-space-m)")
                .set("bottom", "var(--lumo-space-m)")
                .set("width", "min(440px, calc(100% - 2 * var(--lumo-space-m)))")
                .set("box-sizing", "border-box")
                .set("background-color", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-shadow", "var(--lumo-box-shadow-xl)")
                .set("transform", "translateX(calc(100% + var(--lumo-space-m) * 2))")
                .set("transition", "transform 220ms ease-out")
                .set("z-index", "21")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("overflow", "hidden");

        this.titleElement = new H4(title);
        this.titleElement.getStyle().set("margin", "0").set("flex", "1").set("white-space", "nowrap");

        Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE.create()), e -> close());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        closeButton.setTooltipText("Close");

        HorizontalLayout headerRow = new HorizontalLayout(titleElement, closeButton);
        headerRow.setWidthFull();
        headerRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headerRow.getStyle().set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                .set("flex", "0 0 auto");

        this.bodyDiv = new Div();
        this.bodyDiv.setWidthFull();
        this.bodyDiv.getStyle().set("flex", "1 1 auto")
                .set("overflow-y", "auto")
                .set("box-sizing", "border-box")
                .set("padding", "var(--lumo-space-m)");

        this.footerLayout = new HorizontalLayout();
        this.footerLayout.setWidthFull();
        this.footerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        this.footerLayout.getStyle().set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("flex", "0 0 auto");
        this.footerLayout.setVisible(false);

        add(headerRow, bodyDiv, footerLayout);
    }

    private Div buildScrim() {
        Div s = new Div();
        s.addClassName("workspace-settings-drawer-scrim");
        s.getStyle().set("position", "absolute")
                .set("inset", "0")
                .set("background-color", "transparent")
                .set("z-index", "20")
                .set("display", "none");
        s.getElement().addEventListener("click", e -> close());
        return s;
    }

    Div getScrim() {
        return scrim;
    }

    public WorkspaceSettingsDrawer setBodyFactory(Supplier<? extends Component> factory) {
        this.bodyFactory = factory;
        return this;
    }

    public WorkspaceSettingsDrawer setOnOpen(Runnable callback) {
        this.onOpenCallback = callback;
        return this;
    }

    public WorkspaceSettingsDrawer setBody(Component body) {
        bodyDiv.removeAll();
        bodyDiv.add(body);
        return this;
    }

    public WorkspaceSettingsDrawer setApplyButton(String label, Runnable applyAction) {
        this.currentApplyAction = applyAction;
        if (applyButton == null) {
            applyButton = new Button(label);
            applyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            applyButton.addClickListener(e -> {
                if (currentApplyAction != null) {
                    currentApplyAction.run();
                }
                close();
            });
            footerLayout.add(applyButton);
        } else {
            applyButton.setText(label);
        }
        footerLayout.setVisible(true);
        return this;
    }

    public void open() {
        if (bodyFactory != null) {
            bodyDiv.removeAll();
            bodyDiv.add(bodyFactory.get());
        }
        if (onOpenCallback != null) {
            onOpenCallback.run();
        }
        scrim.getStyle().set("display", "block");
        getStyle().set("transform", "translateX(0)");
        opened = true;
    }

    public void close() {
        getStyle().set("transform", "translateX(calc(100% + var(--lumo-space-m) * 2))");
        scrim.getStyle().set("display", "none");
        opened = false;
    }

    public void toggle() {
        if (opened) {
            close();
        } else {
            open();
        }
    }
}
