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
package jm.kr.spring.ai.playground.webui.mcp;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayoutVariant;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfoService;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.util.Objects;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@SpringComponent
@UIScope
@RouteAlias(value = "", layout = SpringAiPlaygroundAppLayout.class)
@Route(value = "mcp", layout = SpringAiPlaygroundAppLayout.class)
public class McpView extends Div {

    public static final String MCP_CONNECTION_SELECT_EVENT = "MCP_CONNECTION_SELECT_EVENT";
    public static final String MCP_CONNECTION_CHANGE_EVENT = "MCP_CONNECTION_CHANGE_EVENT";
    public static final String MCP_CONNECTION_DELETE_EVENT = "MCP_CONNECTION_DELETE_EVENT";

    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final SplitLayout splitLayout;
    private final VerticalLayout mcpContentLayout;
    private final McpServerConnectionView mcpServerConnectionView;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private double splitterPosition;
    private boolean sidebarCollapsed;
    private McpContentView mcpContentView;

    public McpView(PersistentUiDataStorage persistentUiDataStorage, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService) {
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfoChangeSupport = new PropertyChangeSupport(this);

        this.mcpServerConnectionView =
                new McpServerConnectionView(persistentUiDataStorage, mcpServerInfoService,
                        mcpClientService, mcpServerInfoChangeSupport);

        this.mcpServerInfoChangeSupport.addPropertyChangeListener(event -> {
            if (Objects.isNull(event.getNewValue()))
                return;
            McpServerInfo newMcpServerInfo = (McpServerInfo) event.getNewValue();
            switch (event.getPropertyName()) {
                case MCP_CONNECTION_SELECT_EVENT -> this.selectMcpServerInfo(newMcpServerInfo);
                case MCP_CONNECTION_CHANGE_EVENT -> {
                    McpServerInfo oldValue = (McpServerInfo) event.getOldValue();
                    McpServerInfo updateMcpServerInfo =
                            mcpServerInfoService.updateMcpServerInfo(oldValue.mcpTransportType(), oldValue.serverName(),
                                    newMcpServerInfo);
                    this.mcpServerConnectionView.updateMcpConnections();
                    this.mcpServerConnectionView.selectMcpConnectionContent(updateMcpServerInfo);
                    VaadinUtils.showInfoNotification("MCP Connection Saved!");
                }
                case MCP_CONNECTION_DELETE_EVENT -> {
                    this.mcpServerConnectionView.updateMcpConnections();
                    addNewMcpServerDetails();
                }
            }
        });

        setHeightFull();
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.splitLayout.addToPrimary(this.mcpServerConnectionView);
        this.mcpContentLayout = new VerticalLayout();
        this.mcpContentLayout.setSpacing(false);
        this.mcpContentLayout.setMargin(false);
        this.mcpContentLayout.setPadding(false);
        this.mcpContentLayout.setHeightFull();
        this.mcpContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        this.splitLayout.addToSecondary(this.mcpContentLayout);
        this.sidebarCollapsed = false;
        addNewMcpServerDetails();
    }

    private HorizontalLayout createChatContentHeader() {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.getStyle().setPadding("var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button toggleButton = styledButton("Hide MCP Connections", VaadinIcon.CHEVRON_LEFT.create(), null);
        Component leftArrowIcon = toggleButton.getIcon();
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        rightArrowIcon.setTooltipText("Show MCP Connections");
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                this.mcpServerConnectionView.removeFromParent();
            else
                this.splitLayout.addToPrimary(this.mcpServerConnectionView);
            if (this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        Button newChatButton = styledButton("New Mcp Connection", VaadinIcon.CONNECT.create(),
                event -> addNewMcpServerDetails());
        horizontalLayout.add(newChatButton);

        H4 mcpServerInfoText = new H4("MCP Server Info");
        mcpServerInfoText.getStyle().set("white-space", "nowrap");
        Div mcpServerInfoTextDiv = new Div(mcpServerInfoText);
        mcpServerInfoTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");

        HorizontalLayout modelLabelLayout = new HorizontalLayout(mcpServerInfoTextDiv);
        modelLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        modelLabelLayout.setWidthFull();
        horizontalLayout.add(modelLabelLayout);

        Icon mcpServerConnectionIcon = styledIcon(VaadinIcon.COG_O.create());
        mcpServerConnectionIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
        mcpServerConnectionIcon.setTooltipText("MCP Server Connection Config");

        MenuBar mcpServerConnectionMenuBar = new MenuBar();
        mcpServerConnectionMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        mcpServerConnectionMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        mcpServerConnectionMenuBar.addItem(mcpServerConnectionIcon);
        mcpServerConnectionMenuBar.getStyle().set("visibility", "hidden");

        horizontalLayout.add(mcpServerConnectionMenuBar);
        return horizontalLayout;
    }

    private void addNewMcpServerDetails() {
        this.mcpServerConnectionView.clearSelectConnection();
        selectMcpServerInfo(this.mcpServerInfoService.createDefaultMcpServerInfo());
    }

    private void selectMcpServerInfo(McpServerInfo mcpServerInfo) {
        if (Objects.isNull(mcpServerInfo))
            return;
        this.mcpContentView =
                new McpContentView(mcpServerInfo, this.mcpServerInfoService, this.mcpClientService,
                        this.mcpServerInfoChangeSupport);

        VaadinUtils.getUi(this).access(() -> {
            this.mcpContentLayout.removeAll();
            this.mcpContentLayout.add(createChatContentHeader(), this.mcpContentView);
        });
    }

}
