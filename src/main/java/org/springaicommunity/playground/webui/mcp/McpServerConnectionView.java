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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.ServerStatus;
import org.springaicommunity.playground.service.mcp.client.McpClientService.StatusEntry;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;

import java.beans.PropertyChangeSupport;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.springaicommunity.playground.webui.mcp.McpServerView.MCP_CONNECTION_DELETE_EVENT;
import static org.springaicommunity.playground.webui.mcp.McpServerView.MCP_CONNECTION_SELECT_EVENT;

public class McpServerConnectionView extends WorkspaceSidebar implements BeforeEnterObserver {

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private Map<McpTransportType, ListBox<McpServerInfo>> mcpServerInfoListBoxMap;

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        this.persistentUiDataStorage.loadData(McpContentView.LAST_SELECTED_MCP_CONNECTION,
                new TypeReference<McpServerInfo>() {},
                savedInfo -> resolveInitialSelection(savedInfo).ifPresent(this::selectMcpConnectionContent));
    }

    private Optional<McpServerInfo> resolveInitialSelection(McpServerInfo savedInfo) {
        Optional<McpServerInfo> fromSaved = Optional.ofNullable(savedInfo)
                .map(info -> this.mcpServerInfoService.getMcpServerInfos().get(info.mcpTransportType()))
                .flatMap(list -> list.stream()
                        .filter(info -> info.serverName().equals(savedInfo.serverName()))
                        .findFirst());
        return fromSaved
                .or(() -> Optional.ofNullable(this.mcpServerInfoService.getDefaultMcpServerInfo()))
                .or(() -> this.mcpServerInfoService.getMcpServerInfos().values().stream()
                        .flatMap(List::stream).findFirst());
    }

    public McpServerConnectionView(PersistentUiDataStorage persistentUiDataStorage,
            McpServerInfoService mcpServerInfoService, McpClientService mcpClientService,
            PropertyChangeSupport mcpServerInfoChangeSupport) {
        super("MCP Server Connections");
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfoChangeSupport = mcpServerInfoChangeSupport;

        addHeaderIcon(VaadinIcon.CLOSE, "Delete", e -> deleteMcpConnection());

        updateMcpConnections();
    }

    public void updateMcpConnections() {
        this.mcpServerInfoListBoxMap = new EnumMap<>(McpTransportType.class);

        VerticalLayout detailsLayout = new VerticalLayout();
        detailsLayout.setPadding(false);
        detailsLayout.setSpacing(false);

        for (McpTransportType mcpTransportType : McpTransportType.values()) {
            ListBox<McpServerInfo> mcpServerInfoListBox = buildMcpServerInfoListBox();
            this.mcpServerInfoListBoxMap.put(mcpTransportType, mcpServerInfoListBox);
            Details details = new Details(mcpTransportType.name().replace('_', ' '), mcpServerInfoListBox);
            details.addClassName("workspace-sidebar-details");
            details.setOpened(true);
            detailsLayout.add(details);
        }

        this.mcpServerInfoService.getMcpServerInfos().forEach(
                (transportType, mcpServerInfos) -> this.mcpServerInfoListBoxMap.get(transportType)
                        .setItems(mcpServerInfos));

        setSidebarContent(verticalScroller(detailsLayout));
    }

    private ListBox<McpServerInfo> buildMcpServerInfoListBox() {
        ListBox<McpServerInfo> mcpServerInfoListBox = new ListBox<>();
        mcpServerInfoListBox.addClassName("custom-list-box");
        mcpServerInfoListBox.setRenderer(new ComponentRenderer<>(mcpServerInfo -> {
            StatusEntry status = mcpClientService.getStatus(mcpServerInfo);
            Span dot = statusDot(status);
            Span title = listItemText(mcpServerInfo.serverName());
            HorizontalLayout row = new HorizontalLayout(dot, title);
            row.setSpacing(false);
            row.setPadding(false);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle().set("gap", "0.4em");
            String tooltipText = mcpServerInfo.description() == null ? "" : mcpServerInfo.description();
            if (status.status() == ServerStatus.ERROR && status.error() != null) {
                tooltipText = (tooltipText.isBlank() ? "" : tooltipText + "\n") + "Error: " + status.error();
            } else if (status.status() == ServerStatus.AWAITING_AUTHORIZATION) {
                tooltipText = (tooltipText.isBlank() ? "" : tooltipText + "\n")
                        + "Awaiting OAuth authorization — open the config panel and click Authorize.";
            }
            Tooltip.forComponent(row).withText(tooltipText).withHoverDelay(1);
            schedulePing(mcpServerInfo, mcpServerInfoListBox);
            return row;
        }));
        mcpServerInfoListBox.addValueChangeListener(
                event -> notifyMcpServerInfoSelection(event.getOldValue(), event.getValue())
        );
        return mcpServerInfoListBox;
    }

    private Span statusDot(StatusEntry status) {
        Span dot = new Span();
        String color = switch (status.status()) {
            case OK -> "var(--lumo-success-color)";
            case ERROR -> "var(--lumo-error-color)";
            case AWAITING_AUTHORIZATION -> "var(--lumo-warning-color)";
            case OFFLINE -> "var(--lumo-contrast-30pct)";
        };
        dot.getStyle()
                .set("display", "inline-block")
                .set("width", "0.55em")
                .set("height", "0.55em")
                .set("border-radius", "50%")
                .set("flex", "0 0 auto")
                .set("background-color", color);
        return dot;
    }

    private void schedulePing(McpServerInfo mcpServerInfo, ListBox<McpServerInfo> listBox) {
        UI ui = VaadinUtils.getUi(this);
        if (ui == null) return;
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            StatusEntry before = mcpClientService.getStatus(mcpServerInfo);
            StatusEntry after = mcpClientService.pingAndUpdateStatus(mcpServerInfo);
            if (!Objects.equals(before, after)) {
                ui.access(listBox::getDataProvider);
                ui.access(() -> listBox.getDataProvider().refreshItem(mcpServerInfo));
            }
        });
    }

    private void notifyMcpServerInfoSelection(McpServerInfo oldMcpServerInfo, McpServerInfo newMcpServerInfo) {
        if (Objects.isNull(newMcpServerInfo))
            return;
        this.mcpServerInfoListBoxMap.values().stream()
                .filter(mcpServerInfoListBox -> !newMcpServerInfo.equals(mcpServerInfoListBox.getValue()))
                .forEach(ListBox::clear);
        this.mcpServerInfoChangeSupport.firePropertyChange(MCP_CONNECTION_SELECT_EVENT, oldMcpServerInfo,
                newMcpServerInfo);
        this.persistentUiDataStorage.saveData(McpContentView.LAST_SELECTED_MCP_CONNECTION, newMcpServerInfo);
    }

    private void deleteMcpConnection() {
        this.getCurrentMcpConnectionAsOpt().ifPresent(mcpServerInfo -> {
            Dialog dialog = VaadinUtils.headerDialog("Delete: " + mcpServerInfo.serverName());
            dialog.setModal(true);
            dialog.add("Are you sure you want to delete this connection permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.mcpClientService.deleteConnectingMcpServer(mcpServerInfo);
                this.mcpServerInfoService.deleteMcpServerInfo(mcpServerInfo.mcpTransportType(),
                        mcpServerInfo.serverName());
                this.mcpServerInfoChangeSupport.firePropertyChange(MCP_CONNECTION_DELETE_EVENT, null, mcpServerInfo);
                dialog.close();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            deleteButton.getStyle().set("margin-right", "auto");
            dialog.getFooter().add(deleteButton);
            dialog.open();
            deleteButton.focus();
        });
    }

    public void selectMcpConnectionContent(McpServerInfo targetMcpServerInfo) {
        VaadinUtils.getUi(this).access(() -> this.mcpServerInfoListBoxMap.get(targetMcpServerInfo.mcpTransportType())
                .setValue(targetMcpServerInfo));
    }

    public void clearSelectConnection() {
        this.mcpServerInfoListBoxMap.values().forEach(ListBox::clear);
    }

    private Optional<McpServerInfo> getCurrentMcpConnectionAsOpt() {
        return this.mcpServerInfoListBoxMap.values().stream().map(ListBox::getValue).filter(Objects::nonNull)
                .findFirst();
    }

}
