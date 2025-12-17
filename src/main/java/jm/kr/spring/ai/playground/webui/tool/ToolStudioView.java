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
package jm.kr.spring.ai.playground.webui.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayoutVariant;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jm.kr.spring.ai.playground.service.tool.ToolSpec;
import jm.kr.spring.ai.playground.service.tool.ToolSpecService;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.util.Objects;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.headerPopover;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@SpringComponent
@UIScope
@PageTitle("Tool")
@Route(value = "tool-studio", layout = SpringAiPlaygroundAppLayout.class)
public class ToolStudioView extends Div {

    public static final String TOOL_SELECT_EVENT = "TOOL_SELECT_EVENT";
    public static final String TOOL_CHANGE_EVENT = "TOOL_CHANGE_EVENT";
    public static final String TOOL_EMPTY_EVENT = "TOOL_EMPTY_EVENT";

    private final ToolSpecService toolSpecService;
    private final ObjectMapper objectMapper;
    private final PropertyChangeSupport toolChangeSupport;

    private final SplitLayout splitLayout;
    private final VerticalLayout toolContentLayout;
    private final HorizontalLayout toolContentHeader;
    private final ToolListView toolListView;
    private double splitterPosition;
    private boolean sidebarCollapsed;

    private ToolBuilderView toolBuilderView;

    public ToolStudioView(PersistentUiDataStorage persistentUiDataStorage, ObjectMapper objectMapper,
            ToolSpecService toolSpecService) {
        this.toolSpecService = toolSpecService;
        this.objectMapper = objectMapper;

        this.toolChangeSupport = new PropertyChangeSupport(this);

        this.toolListView = new ToolListView(persistentUiDataStorage, toolSpecService, toolChangeSupport);
        toolChangeSupport.addPropertyChangeListener(TOOL_SELECT_EVENT,
                event -> this.changeToolContent((ToolSpec) event.getNewValue()));
        toolChangeSupport.addPropertyChangeListener(TOOL_CHANGE_EVENT,
                event -> this.toolListView.changeToolContent((ToolSpec) event.getNewValue()));
        toolChangeSupport.addPropertyChangeListener(TOOL_EMPTY_EVENT, event -> {
            if ((boolean) event.getNewValue()) {displayNewToolDesignView();}
        });

        setHeightFull();
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.splitLayout.addToPrimary(this.toolListView);

        this.toolContentLayout = new VerticalLayout();
        this.toolContentLayout.setSpacing(false);
        this.toolContentLayout.setMargin(false);
        this.toolContentLayout.setPadding(false);
        this.toolContentLayout.setHeightFull();
        this.toolContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        this.toolContentHeader = createToolContentHeader();

        this.splitLayout.addToSecondary(this.toolContentLayout);
        this.sidebarCollapsed = false;

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
        }
        VaadinUtils.getUi(this).access(() -> {
            this.toolContentLayout.removeAll();
            this.toolContentLayout.add(toolContentHeader, this.toolBuilderView);
        });
    }

    private HorizontalLayout createToolContentHeader() {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.getStyle().setPadding("var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button toggleButton = styledButton("Hide Tools", VaadinIcon.CHEVRON_LEFT.create(), null);
        Component leftArrowIcon = toggleButton.getIcon();
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        rightArrowIcon.setTooltipText("Show Tools");
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                this.toolListView.removeFromParent();
            else
                this.splitLayout.addToPrimary(this.toolListView);
            if (this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        Button newToolButton =
                styledButton("New Tool", VaadinIcon.TOOLS.create(), event -> displayNewToolDesignView());
        horizontalLayout.add(newToolButton);
        String exportToolSpecificationTitle = "Export Tool Specification";
        Button exportToolSpecButton = styledButton(exportToolSpecificationTitle, VaadinIcon.FILE_CODE.create(),
                event -> this.toolBuilderView.getCurrentToolSpecJsonAsOpt().ifPresent(currentToolSpecJson ->
                        openToolSpecDialog(exportToolSpecificationTitle, currentToolSpecJson)));
        horizontalLayout.add(newToolButton, exportToolSpecButton);


        H4 toolInfoText = new H4("Tool Studio");
        toolInfoText.getStyle().set("white-space", "nowrap");
        Div toolInfoTextDiv = new Div(toolInfoText);
        toolInfoTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");

        HorizontalLayout toolInfoLabelLayout = new HorizontalLayout(toolInfoTextDiv);
        toolInfoLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        toolInfoLabelLayout.setWidthFull();
        horizontalLayout.add(toolInfoLabelLayout);

        Icon toolMcpServerSettingIcon = styledIcon(VaadinIcon.TOOLBOX.create());
        toolMcpServerSettingIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
        String toolMcpServerSetting = "Tool MCP Server Setting";
        toolMcpServerSettingIcon.setTooltipText(toolMcpServerSetting);
        Popover toolMcpServerSettingPopover = headerPopover(toolMcpServerSettingIcon, toolMcpServerSetting);
        toolMcpServerSettingPopover.setWidth("600px");
        toolMcpServerSettingPopover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
        toolMcpServerSettingPopover.setPosition(PopoverPosition.BOTTOM);
        toolMcpServerSettingPopover.setModal(true);
        ToolMcpServerSettingView toolMcpServerSettingView =
                new ToolMcpServerSettingView(this.toolSpecService.getToolSpecList(),
                        this.toolSpecService.getToolMcpServerSetting());
        toolMcpServerSettingView.getStyle()
                .set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");
        Button confirmButton = new Button("Confirm", clickEvent -> {
            this.toolSpecService.updateToolMcpServerSetting(toolMcpServerSettingView.getUiToolMcpServerSetting());
            toolMcpServerSettingPopover.close();
        });
        confirmButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout applyNewChatButtonLayout = new HorizontalLayout(confirmButton);
        applyNewChatButtonLayout.setWidthFull();
        applyNewChatButtonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        applyNewChatButtonLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");
        toolMcpServerSettingView.add(applyNewChatButtonLayout);
        toolMcpServerSettingPopover.add(toolMcpServerSettingView);
        toolMcpServerSettingPopover.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                toolMcpServerSettingView.update(this.toolSpecService.getToolSpecList(),
                        this.toolSpecService.getToolMcpServerSetting());
            }
        });

        MenuBar toolMcpServerSettingMenuBar = new MenuBar();
        toolMcpServerSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        toolMcpServerSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        toolMcpServerSettingMenuBar.addItem(toolMcpServerSettingIcon);

        horizontalLayout.add(toolMcpServerSettingMenuBar);
        return horizontalLayout;
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

        HorizontalLayout footer =
                new HorizontalLayout(copyButton, closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        footer.getStyle().set("flex-shrink", "0");

        VerticalLayout content =
                new VerticalLayout(title, jsonArea, footer);
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