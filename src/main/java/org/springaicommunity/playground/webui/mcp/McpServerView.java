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

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.catalog.McpCategoryService;
import org.springaicommunity.playground.service.mcp.catalog.McpTagSuggestionService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.risk.McpCompositionService;
import org.springaicommunity.playground.service.mcp.risk.McpExposedToolService;
import org.springaicommunity.playground.service.mcp.risk.McpRegistrationRiskPreview;
import org.springaicommunity.playground.service.mcp.risk.McpToolRiskEvaluator;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;

import java.beans.PropertyChangeSupport;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final McpCategoryService mcpCategoryService;
    private final McpTagSuggestionService mcpTagSuggestionService;
    private final McpToolRiskEvaluator mcpToolRiskEvaluator;
    private final McpRegistrationRiskPreview mcpRegistrationRiskPreview;
    private final McpExposedToolService mcpExposedToolService;
    private final McpCompositionService mcpCompositionService;
    private final ToolSpecService toolSpecService;
    private final McpServerConnectionView mcpServerConnectionView;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private McpContentView mcpContentView;

    public McpServerView(PersistentUiDataStorage persistentUiDataStorage, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService, McpCategoryService mcpCategoryService,
            McpCatalogService mcpCatalogService, McpTagSuggestionService mcpTagSuggestionService,
            McpToolRiskEvaluator mcpToolRiskEvaluator, McpRegistrationRiskPreview mcpRegistrationRiskPreview,
            McpExposedToolService mcpExposedToolService, McpCompositionService mcpCompositionService,
            ToolSpecService toolSpecService) {
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpCategoryService = mcpCategoryService;
        this.mcpTagSuggestionService = mcpTagSuggestionService;
        this.mcpToolRiskEvaluator = mcpToolRiskEvaluator;
        this.mcpRegistrationRiskPreview = mcpRegistrationRiskPreview;
        this.mcpExposedToolService = mcpExposedToolService;
        this.mcpCompositionService = mcpCompositionService;
        this.toolSpecService = toolSpecService;
        this.mcpServerInfoChangeSupport = new PropertyChangeSupport(this);

        this.mcpServerConnectionView =
                new McpServerConnectionView(persistentUiDataStorage, mcpServerInfoService,
                        mcpClientService, mcpCategoryService, mcpCatalogService,
                        mcpServerInfoChangeSupport);

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
        setSidebarSplitterPosition(20);

        Button newMcpConnectionButton = styledButton("Add Custom Server", VaadinIcon.CONNECT.create(),
                event -> addNewMcpServerDetails());
        addHeaderAction(newMcpConnectionButton);

        WorkspaceSettingsDrawer exposeToolsDrawer = installSettingsDrawer(VaadinIcon.COG_O,
                "Built-in MCP Server Composed Tools",
                "Choose what the built-in MCP server exposes — built-in tools, composed external tools, or both");
        McpExposedToolsPanel exposeToolsPanel = new McpExposedToolsPanel(this.mcpExposedToolService,
                this.mcpCompositionService, this.mcpServerInfoService, this.mcpClientService,
                this.mcpToolRiskEvaluator, this.toolSpecService);
        exposeToolsDrawer.setBodyFactory(exposeToolsPanel::build);
        exposeToolsDrawer.setApplyButton("Apply", exposeToolsPanel::apply);

        setHeaderLabel("MCP Server Info");

        McpServerInfo initial = mcpServerInfoService.getDefaultMcpServerInfo();
        if (Objects.nonNull(initial)) {
            this.mcpServerConnectionView.selectMcpConnectionContent(initial);
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
                        this.mcpCategoryService, this.mcpTagSuggestionService,
                        this.mcpToolRiskEvaluator, this.mcpRegistrationRiskPreview,
                        this.mcpServerInfoChangeSupport);

        VaadinUtils.getUi(this).access(() -> setContent(this.mcpContentView));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().getPage().fetchCurrentURL(url -> {
            String serverName = queryParam(url.getQuery(), "server");
            if (serverName == null || serverName.isBlank()) return;
            this.mcpServerInfoService.getMcpServerInfos().values().stream()
                    .flatMap(List::stream)
                    .filter(info -> serverName.equals(info.serverName()))
                    .findFirst()
                    .ifPresent(info -> {
                        this.mcpServerConnectionView.selectMcpConnectionContent(info);
                        selectMcpServerInfo(info);
                    });
        });
    }

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            if (key.equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
