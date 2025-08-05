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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfoService;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static jm.kr.spring.ai.playground.webui.mcp.McpView.MCP_CONNECTION_DELETE_EVENT;
import static jm.kr.spring.ai.playground.webui.mcp.McpView.MCP_CONNECTION_SELECT_EVENT;

public class McpServerConnectionView extends VerticalLayout implements BeforeEnterObserver {

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private Map<McpTransportType, ListBox<McpServerInfo>> mcpServerInfoListBoxMap;

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        this.persistentUiDataStorage.loadData(McpContentView.LAST_SELECTED_MCP_CONNECTION,
                new TypeReference<McpServerInfo>() {},
                mcpServerInfo -> {
                    if (Objects.nonNull(mcpServerInfo))
                        this.mcpServerInfoService.getMcpServerInfos().get(mcpServerInfo.mcpTransportType()).stream()
                                .filter(info -> info.serverName().equals(mcpServerInfo.serverName())).findFirst()
                                .ifPresentOrElse(this::selectMcpConnectionContent,
                                        () -> this.mcpServerInfoService.getMcpServerInfos().values().stream()
                                                .flatMap(List::stream).findFirst()
                                                .ifPresent(this::selectMcpConnectionContent));
                });
    }

    public McpServerConnectionView(PersistentUiDataStorage persistentUiDataStorage,
            McpServerInfoService mcpServerInfoService, McpClientService mcpClientService,
            PropertyChangeSupport mcpServerInfoChangeSupport) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfoChangeSupport = mcpServerInfoChangeSupport;

        setSpacing(false);
        setMargin(false);
        getStyle().set("overflow", "hidden");
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
            details.setOpened(true);
            detailsLayout.add(details);
        }

        this.mcpServerInfoService.getMcpServerInfos().forEach(
                (transportType, mcpServerInfos) -> this.mcpServerInfoListBoxMap.get(transportType)
                        .setItems(mcpServerInfos));

        Scroller scroller = new Scroller(detailsLayout);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        removeAll();
        add(initMcpServerInfoHeader(), scroller);
    }

    private ListBox<McpServerInfo> buildMcpServerInfoListBox() {
        ListBox<McpServerInfo> mcpServerInfoListBox = new ListBox<>();
        mcpServerInfoListBox.addClassName("custom-list-box");
        mcpServerInfoListBox.setRenderer(new ComponentRenderer<>(mcpServerInfo -> {
            Span title = new Span(mcpServerInfo.serverName());
            title.getStyle().set("white-space", "nowrap")
                    .set("overflow", "hidden")
                    .set("text-overflow", "ellipsis")
                    .set("flex-grow", "1");
            Tooltip.forComponent(title).withText(mcpServerInfo.description()).withHoverDelay(1);
            return title;
        }));
        mcpServerInfoListBox.addValueChangeListener(
                event -> notifyMcpServerInfoSelection(event.getOldValue(), event.getValue())
        );
        return mcpServerInfoListBox;
    }

    private void notifyMcpServerInfoSelection(McpServerInfo oldMcpServerInfo, McpServerInfo newMcpServerInfo) {
        this.mcpServerInfoChangeSupport.firePropertyChange(MCP_CONNECTION_SELECT_EVENT, oldMcpServerInfo,
                newMcpServerInfo);
        this.persistentUiDataStorage.saveData(McpContentView.LAST_SELECTED_MCP_CONNECTION, newMcpServerInfo);
    }

    private Header initMcpServerInfoHeader() {
        Span appName = new Span("MCP Connections");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        MenuBar menuBar = new MenuBar();
        menuBar.setWidthFull();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon closeIcon = VaadinUtils.styledIcon(VaadinIcon.CLOSE.create());
        closeIcon.setTooltipText("Delete");
        menuBar.addItem(closeIcon, menuItemClickEvent -> deleteMcpConnection());

        Header header = new Header(appName, menuBar);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
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
        return Optional.ofNullable(this.mcpServerInfoListBoxMap.get(McpTransportType.STDIO).getValue());
    }

}
