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
package org.springaicommunity.playground.webui.mcp;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCategoryService;
import org.springaicommunity.playground.service.mcp.catalog.McpTagSuggestionService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;

import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class McpContentView extends VerticalLayout {

    static final String LAST_SELECTED_MCP_CONNECTION = "lastSelectedMcpConnection";
    private final McpServerInfo mcpServerInfo;

    public McpContentView(McpServerInfo mcpServerInfo, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService, McpCategoryService mcpCategoryService,
            McpTagSuggestionService mcpTagSuggestionService, PropertyChangeSupport mcpServerInfoChangeSupport) {
        this.mcpServerInfo = mcpServerInfo;

        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("overflow", "auto");

        McpServerConfigView mcpServerConfigView = new McpServerConfigView(mcpServerInfo, mcpServerInfoService,
                mcpClientService, mcpCategoryService, mcpTagSuggestionService, mcpServerInfoChangeSupport);
        mcpServerConfigView.setWidthFull();
        add(mcpServerConfigView);

        Component inspectorPlaceholder = createDisconnectedInspectorView();
        add(inspectorPlaceholder);

        if (mcpClientService.isConnecting(this.mcpServerInfo)) {
            UI ui = UI.getCurrent();
            CompletableFuture.supplyAsync(() -> mcpClientService.getToolListAsOpt(this.mcpServerInfo))
                    .thenAccept(toolListAsOpt -> {
                        if (Objects.isNull(ui))
                            return;
                        ui.access(() -> toolListAsOpt.ifPresent(toolList -> replace(inspectorPlaceholder,
                                new McpServerInspectorView(this.mcpServerInfo, mcpClientService, toolList))));
                    });
        }
    }

    private Component createDisconnectedInspectorView() {
        H4 logo = new H4("MCP Inspector");
        logo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        HorizontalLayout headerLayout = new HorizontalLayout(logo);
        headerLayout.setWidthFull();
        headerLayout.setPadding(true);
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        Icon infoIcon = VaadinIcon.INFO_CIRCLE_O.create();
        infoIcon.getStyle().set("color", "var(--lumo-primary-color)")
                .set("margin-right", "var(--lumo-space-s)");

        Span prefix = new Span("Click ");
        Span highlight = new Span("\"Save & Connect\"");
        highlight.getStyle().set("font-weight", "600").set("color", "var(--lumo-primary-text-color)");
        Span suffix = new Span(" above to enable the Tool Inspector.");

        HorizontalLayout hintContent = new HorizontalLayout(infoIcon, prefix, highlight, suffix);
        hintContent.setSpacing(false);
        hintContent.setAlignItems(FlexComponent.Alignment.CENTER);

        Div hintBox = new Div(hintContent);
        hintBox.getStyle()
                .set("padding", "var(--lumo-space-l)")
                .set("border", "1px dashed var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin", "0 var(--lumo-space-m) var(--lumo-space-m)")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center");

        VerticalLayout layout = new VerticalLayout(headerLayout, hintBox);
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        return layout;
    }
}
