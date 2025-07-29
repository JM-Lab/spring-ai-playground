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
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.util.concurrent.CompletableFuture;

public class McpContentView extends VerticalLayout {

    static final String LAST_SELECTED_MCP_CONNECTION = "lastSelectedMcpConnection";
    private final McpServerInfo mcpServerInfo;

    public McpContentView(McpServerInfo mcpServerInfo, McpClientService mcpClientService,
            PropertyChangeSupport mcpServerInfoChangeSupport) {
        this.mcpServerInfo = mcpServerInfo;

        setSizeFull();
        setSpacing(false);
        setPadding(false);
        getStyle().set("overflow", "auto");

        McpServerConfigView mcpServerConfigView = new McpServerConfigView(mcpServerInfo, mcpClientService,
                mcpServerInfoChangeSupport);
        mcpServerConfigView.setWidthFull();
        add(mcpServerConfigView);

        if (mcpClientService.isConnecting(this.mcpServerInfo))
            CompletableFuture.supplyAsync(() -> mcpClientService.getToolListAsOpt(this.mcpServerInfo))
                    .thenAccept(toolListAsOpt -> VaadinUtils.getUi(this).access(() ->
                            toolListAsOpt.ifPresent(toolList ->
                                    add(new McpServerInspectorView(this.mcpServerInfo, mcpClientService, toolList)))));
        else
            add(createErrorMcpServerInspectorView());
    }

    private Component createErrorMcpServerInspectorView() {
        H4 logo = new H4("MCP Inspector");
        logo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        HorizontalLayout inspectorHeaderLayout = new HorizontalLayout(logo);
        inspectorHeaderLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        inspectorHeaderLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);

        Span message = new Span("Not connected to MCP Server");
        message.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("font-size", "var(--lumo-font-size-m)")
                .set("font-weight", "600");

        HorizontalLayout notConnectedLayout = new HorizontalLayout(message);
        notConnectedLayout.setWidthFull();
        notConnectedLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        notConnectedLayout.setAlignItems(Alignment.BASELINE);

        VerticalLayout errorMcpServerInspectorViewLayout =
                new VerticalLayout(inspectorHeaderLayout, notConnectedLayout);
        errorMcpServerInspectorViewLayout.setSizeFull();
        errorMcpServerInspectorViewLayout.setPadding(false);
        errorMcpServerInspectorViewLayout.setSpacing(false);
        return errorMcpServerInspectorViewLayout;
    }

}
