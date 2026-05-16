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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayoutVariant;

import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;
import static org.springaicommunity.playground.webui.VaadinUtils.styledIcon;

public class ContentWorkspaceView extends Div {

    private static final double DEFAULT_SPLITTER_POSITION = 15;

    private final SplitLayout splitLayout;
    private final Div contentLayout;
    private final WorkspaceContentHeader header;
    private final Div contentArea;
    private final Div contentBody;

    private Component sidebar;
    private Button toggleButton;
    private Component hideIcon;
    private Icon showIcon;
    private double splitterPosition = DEFAULT_SPLITTER_POSITION;
    private boolean sidebarCollapsed;

    public ContentWorkspaceView() {
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(splitterPosition);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.contentLayout = new Div();
        this.contentLayout.setHeightFull();
        this.contentLayout.setWidthFull();
        this.contentLayout.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("overflow", "hidden");
        this.splitLayout.addToSecondary(this.contentLayout);

        this.header = new WorkspaceContentHeader();
        this.contentLayout.add(this.header);

        this.contentArea = new Div();
        this.contentArea.setWidthFull();
        this.contentArea.getStyle()
                .set("position", "relative")
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("overflow", "hidden")
                .set("display", "flex")
                .set("flex-direction", "column");

        this.contentBody = new Div();
        this.contentBody.setWidthFull();
        this.contentBody.getStyle().set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("overflow", "hidden")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "stretch");
        this.contentArea.add(this.contentBody);
        this.contentLayout.add(this.contentArea);
    }

    public void setSidebarSplitterPosition(double percent) {
        this.splitterPosition = percent;
        this.splitLayout.setSplitterPosition(percent);
    }

    public Button configureSidebar(Component sidebarComponent, String entityPluralLabel) {
        this.sidebar = sidebarComponent;
        this.splitLayout.addToPrimary(sidebarComponent);

        this.toggleButton = styledButton("Hide " + entityPluralLabel,
                VaadinIcon.CHEVRON_LEFT.create(), null);
        this.hideIcon = this.toggleButton.getIcon();
        this.showIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        this.showIcon.setTooltipText("Show " + entityPluralLabel);
        this.toggleButton.addClickListener(event -> toggleSidebar());

        this.header.addFirstAction(this.toggleButton);
        return this.toggleButton;
    }

    private void toggleSidebar() {
        this.sidebarCollapsed = !this.sidebarCollapsed;
        this.toggleButton.setIcon(this.sidebarCollapsed ? this.showIcon : this.hideIcon);
        if (this.sidebarCollapsed) {
            this.sidebar.removeFromParent();
        } else {
            this.splitLayout.addToPrimary(this.sidebar);
        }
        if (this.splitLayout.getSplitterPosition() > 0) {
            this.splitterPosition = this.splitLayout.getSplitterPosition();
        }
        this.splitLayout.setSplitterPosition(this.sidebarCollapsed ? 0 : this.splitterPosition);
    }

    public void addHeaderAction(Component... actions) {
        this.header.addAction(actions);
    }

    public void setHeaderCenter(Component center) {
        this.header.setCenter(center);
    }

    public void setHeaderLabel(String text) {
        this.header.setLabel(text);
    }

    public MenuBar getHeaderEndMenuBar() {
        return this.header.getEndMenuBar();
    }

    public WorkspaceSettingsDrawer installSettingsDrawer(VaadinIcon icon, String title,
            String tooltip) {
        WorkspaceSettingsDrawer drawer = new WorkspaceSettingsDrawer(title);
        this.contentArea.add(drawer.getScrim(), drawer);

        Icon iconInstance = styledIcon(icon.create());
        iconInstance.getStyle().set("marginRight", "var(--lumo-space-l)");
        iconInstance.setTooltipText(tooltip);
        this.header.getEndMenuBar().addItem(iconInstance, e -> drawer.toggle());
        return drawer;
    }

    public void setContent(Component content) {
        this.contentBody.removeAll();
        if (content != null) {
            this.contentBody.add(content);
        }
    }
}
