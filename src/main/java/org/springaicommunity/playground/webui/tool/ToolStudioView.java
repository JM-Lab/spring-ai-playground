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
package org.springaicommunity.playground.webui.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;

import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.Optional;

import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;

@SpringComponent
@UIScope
@AnonymousAllowed
@PageTitle("Tool Studio")
@Route(value = "tool-studio", layout = SpringAiPlaygroundAppLayout.class)
public class ToolStudioView extends ContentWorkspaceView {

    public static final String TOOL_SELECT_EVENT = "TOOL_SELECT_EVENT";
    public static final String TOOL_CHANGE_EVENT = "TOOL_CHANGE_EVENT";
    public static final String TOOL_EMPTY_EVENT = "TOOL_EMPTY_EVENT";

    private final ToolSpecService toolSpecService;
    private final ObjectMapper objectMapper;
    private final PropertyChangeSupport toolChangeSupport;
    private final ToolListView toolListView;
    private final ToolMcpServerSettingView toolMcpServerSettingView;
    private final WorkspaceSettingsDrawer mcpServerSettingsDrawer;

    private ToolBuilderView toolBuilderView;

    public ToolStudioView(PersistentUiDataStorage persistentUiDataStorage, ObjectMapper objectMapper,
            ToolSpecService toolSpecService) {
        this.toolSpecService = toolSpecService;
        this.objectMapper = objectMapper;
        this.toolChangeSupport = new PropertyChangeSupport(this);

        this.toolListView = new ToolListView(persistentUiDataStorage, toolSpecService, toolChangeSupport);
        toolChangeSupport.addPropertyChangeListener(TOOL_SELECT_EVENT,
                event -> Optional.ofNullable(event.getNewValue()).map(value -> (ToolSpec) value)
                        .ifPresent(this::changeToolContent));
        toolChangeSupport.addPropertyChangeListener(TOOL_CHANGE_EVENT,
                event -> this.toolListView.changeToolContent((ToolSpec) event.getNewValue()));
        toolChangeSupport.addPropertyChangeListener(TOOL_EMPTY_EVENT, event -> {
            if ((boolean) event.getNewValue()) {displayNewToolDesignView();}
        });

        configureSidebar(this.toolListView, "Tools");

        Button newToolButton = styledButton("New Tool", VaadinIcon.TOOLS.create(),
                event -> displayNewToolDesignView());
        Button copyAndNewToolButton = styledButton("Copy And New Tool", VaadinIcon.COPY_O.create(),
                event -> changeToolContent(this.toolBuilderView.copyCurrentToolSpec()));
        addHeaderAction(newToolButton, copyAndNewToolButton);

        setHeaderLabel("Tool Studio");

        String exportTitle = "Export Tool Specification";
        Button exportToolSpecButton = styledButton(exportTitle, VaadinIcon.FILE_CODE.create(),
                event -> this.toolBuilderView.getCurrentToolSpecJsonAsOpt().ifPresent(json ->
                        openToolSpecDialog(exportTitle, json)));
        getHeaderEndMenuBar().addItem(exportToolSpecButton);

        String mcpTitle = "Tool MCP Server Setting";
        this.toolMcpServerSettingView = new ToolMcpServerSettingView(
                this.toolSpecService.getToolSpecList(),
                this.toolSpecService.getToolMcpServerSetting());
        this.mcpServerSettingsDrawer = installSettingsDrawer(VaadinIcon.TOOLBOX, mcpTitle, mcpTitle);
        this.mcpServerSettingsDrawer.setBody(this.toolMcpServerSettingView);
        this.mcpServerSettingsDrawer.setOnOpen(() -> this.toolMcpServerSettingView.update(
                this.toolSpecService.getToolSpecList(),
                this.toolSpecService.getToolMcpServerSetting()));
        this.mcpServerSettingsDrawer.setApplyButton("Confirm", () ->
                this.toolSpecService.updateToolMcpServerSetting(
                        this.toolMcpServerSettingView.getUiToolMcpServerSetting()));

        displayNewToolDesignView();
    }

    private void displayNewToolDesignView() {
        changeToolContent(null);
    }

    private void changeToolContent(ToolSpec toolSpec) {
        if (Objects.isNull(toolSpec)) {
            this.toolBuilderView = new ToolBuilderView(null, this.toolChangeSupport, toolSpecService, objectMapper);
            this.toolListView.clearSelectTool();
        } else {
            this.toolBuilderView =
                    new ToolBuilderView(toolSpec, toolChangeSupport, toolSpecService, objectMapper);
            if (Objects.isNull(toolSpec.toolId())) {
                this.toolListView.clearSelectTool();
            }
        }
        VaadinUtils.getUi(this).access(() -> setContent(this.toolBuilderView));
    }

    private void openToolSpecDialog(String exportToolSpecificationTitle, String toolSpecJson) {
        Dialog dialog = VaadinUtils.headerDialog(exportToolSpecificationTitle);
        dialog.setWidth("800px");
        dialog.setHeight("800px");
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);

        H4 title = new H4("Tool Specification (JSON)");

        TextArea jsonArea = new TextArea();
        jsonArea.setValue(toolSpecJson);
        jsonArea.setReadOnly(true);
        jsonArea.setWidthFull();
        jsonArea.setHeight("100%");

        jsonArea.getStyle()
                .set("font-family", "monospace")
                .set("font-size", "13px")
                .set("overflow", "auto");

        Button copyButton = new Button("Copy", VaadinIcon.COPY.create(), e -> {
            copyToClipboard(toolSpecJson);
            dialog.close();
        });
        copyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button closeButton = new Button("Close", VaadinIcon.CLOSE.create(), e -> dialog.close());

        HorizontalLayout footer = new HorizontalLayout(copyButton, closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        footer.getStyle().set("flex-shrink", "0");

        VerticalLayout content = new VerticalLayout(title, jsonArea, footer);
        content.setPadding(false);
        content.setSpacing(true);
        content.setSizeFull();
        content.setFlexGrow(1, jsonArea);

        dialog.add(content);
        dialog.open();
    }

    private void copyToClipboard(String text) {
        VaadinUtils.getUi(this).getPage().executeJs("navigator.clipboard.writeText($0)", text);
        VaadinUtils.showInfoNotification("Tool specification copied to clipboard.");
    }
}
