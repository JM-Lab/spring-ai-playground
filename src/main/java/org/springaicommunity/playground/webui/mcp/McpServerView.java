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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;

import java.beans.PropertyChangeSupport;
import java.util.Objects;

import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;

@SpringComponent
@UIScope
@AnonymousAllowed
@PageTitle("MCP Server")
@Route(value = "mcp-server", layout = SpringAiPlaygroundAppLayout.class)
public class McpServerView extends ContentWorkspaceView {

    public static final String MCP_CONNECTION_SELECT_EVENT = "MCP_CONNECTION_SELECT_EVENT";
    public static final String MCP_CONNECTION_CHANGE_EVENT = "MCP_CONNECTION_CHANGE_EVENT";
    public static final String MCP_CONNECTION_DELETE_EVENT = "MCP_CONNECTION_DELETE_EVENT";

    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final McpServerConnectionView mcpServerConnectionView;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private McpContentView mcpContentView;

    public McpServerView(PersistentUiDataStorage persistentUiDataStorage, McpServerInfoService mcpServerInfoService,
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

        configureSidebar(this.mcpServerConnectionView, "MCP Connections");

        Button newMcpConnectionButton = styledButton("New Mcp Connection", VaadinIcon.CONNECT.create(),
                event -> addNewMcpServerDetails());
        addHeaderAction(newMcpConnectionButton);

        setHeaderLabel("MCP Server Info");

        McpServerInfo initial = mcpServerInfoService.getDefaultMcpServerInfo();
        if (Objects.nonNull(initial)) {
            selectMcpServerInfo(initial);
        } else {
            addNewMcpServerDetails();
        }
    }

    private void addNewMcpServerDetails() {
        this.mcpServerConnectionView.clearSelectConnection();
        selectMcpServerInfo(this.mcpServerInfoService.createBlankMcpServerInfo());
    }

    private void selectMcpServerInfo(McpServerInfo mcpServerInfo) {
        if (Objects.isNull(mcpServerInfo))
            return;
        this.mcpContentView =
                new McpContentView(mcpServerInfo, this.mcpServerInfoService, this.mcpClientService,
                        this.mcpServerInfoChangeSupport);

        VaadinUtils.getUi(this).access(() -> setContent(this.mcpContentView));
    }
}
