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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class WorkspaceContentHeader extends HorizontalLayout {

    private final HorizontalLayout actionSlot;
    private final HorizontalLayout centerSlot;
    private final MenuBar endMenuBar;

    public WorkspaceContentHeader() {
        setSpacing(false);
        setMargin(false);
        setPadding(false);
        setWidthFull();
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        setAlignItems(FlexComponent.Alignment.CENTER);
        getStyle().set("padding", "var(--lumo-space-m) 0 0 0")
                .set("flex", "0 0 auto");

        this.actionSlot = new HorizontalLayout();
        this.actionSlot.setSpacing(false);
        this.actionSlot.setPadding(false);
        this.actionSlot.setMargin(false);
        this.actionSlot.setAlignItems(FlexComponent.Alignment.CENTER);
        this.actionSlot.getStyle().set("flex-shrink", "0");

        this.centerSlot = new HorizontalLayout();
        this.centerSlot.setPadding(false);
        this.centerSlot.setMargin(false);
        this.centerSlot.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        this.centerSlot.setAlignItems(FlexComponent.Alignment.CENTER);
        this.centerSlot.setWidthFull();
        this.centerSlot.getStyle().set("min-width", "0").set("overflow", "hidden");

        this.endMenuBar = new MenuBar();
        this.endMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED,
                MenuBarVariant.LUMO_TERTIARY_INLINE);
        this.endMenuBar.getStyle().set("flex-shrink", "0");

        add(this.actionSlot, this.centerSlot, this.endMenuBar);
    }

    public void addAction(Component... actions) {
        this.actionSlot.add(actions);
    }

    public void addFirstAction(Component action) {
        this.actionSlot.addComponentAsFirst(action);
    }

    public void setCenter(Component center) {
        this.centerSlot.removeAll();
        if (center != null) {
            this.centerSlot.add(center);
        }
    }

    public void setLabel(String text) {
        H4 heading = new H4(text);
        heading.getStyle().set("white-space", "nowrap").set("margin", "0")
                .set("overflow", "hidden").set("text-overflow", "ellipsis").set("max-width", "100%");
        Div wrapper = new Div(heading);
        wrapper.getStyle().set("display", "flex")
                .set("justify-content", "center")
                .set("align-items", "center")
                .set("height", "100%");
        setCenter(wrapper);
    }

    public MenuBar getEndMenuBar() {
        return this.endMenuBar;
    }
}
