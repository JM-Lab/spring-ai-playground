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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;

import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.springaicommunity.playground.webui.tool.ToolStudioView.TOOL_EMPTY_EVENT;
import static org.springaicommunity.playground.webui.tool.ToolStudioView.TOOL_SELECT_EVENT;

public class ToolListView extends WorkspaceSidebar implements BeforeEnterObserver {

    private static final String LAST_SELECTED_TOOL = "lastSelectedTool";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport toolChangeSupport;
    private final ToolSpecService toolSpecService;
    private final ListBox<ToolSpec> toolListBox;

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        this.toolListBox.setItems(this.toolSpecService.getToolSpecList());

        this.persistentUiDataStorage.loadData(LAST_SELECTED_TOOL, new TypeReference<ToolSpec>() {},
                tool -> {
                    if (Objects.nonNull(tool)) {
                        toolSpecService.getToolSpecList().stream()
                                .filter(t -> t.toolId().equals(tool.toolId()))
                                .findFirst().ifPresent(this.toolListBox::setValue);
                    }
                });
    }

    public ToolListView(PersistentUiDataStorage persistentUiDataStorage, ToolSpecService toolSpecService,
            PropertyChangeSupport toolChangeSupport) {
        super("Tool List");
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.toolSpecService = toolSpecService;
        this.toolChangeSupport = toolChangeSupport;

        addHeaderIcon(VaadinIcon.CLOSE, "Delete Selected Tool", e -> deleteTool());

        this.toolListBox = new ListBox<>();
        this.toolListBox.addClassName("custom-list-box");
        this.toolListBox.setItems(List.of());

        this.toolListBox.setRenderer(new ComponentRenderer<>(tool -> {
            Span title = listItemText(tool.name());
            title.getElement().setAttribute("title", tool.description());
            return title;
        }));

        this.toolListBox.addValueChangeListener(
                event -> notifyToolSelection(event.getOldValue(), event.getValue()));

        setSidebarContent(verticalScroller(this.toolListBox));
    }

    private void notifyToolSelection(ToolSpec oldToolSpec, ToolSpec newToolSpec) {
        if (Objects.isNull(newToolSpec))
            return;
        if (!newToolSpec.equals(oldToolSpec)) {
            this.toolChangeSupport.firePropertyChange(TOOL_SELECT_EVENT, oldToolSpec, newToolSpec);
            this.persistentUiDataStorage.saveData(LAST_SELECTED_TOOL, newToolSpec);
        }
    }

    private void deleteTool() {
        this.getCurrentToolAsOpt().ifPresent(toolSpec -> {
            Dialog dialog = VaadinUtils.headerDialog("Delete Tool: " + toolSpec.name());
            dialog.setModal(true);
            dialog.add("Are you sure you want to delete toolSpec '" + toolSpec.name() + "' permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.toolSpecService.deleteToolSpec(toolSpec.toolId());
                this.changeToolContent(null);
                dialog.close();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            dialog.getFooter().add(deleteButton);

            dialog.open();
            deleteButton.focus();
        });
    }

    public void changeToolContent(ToolSpec toolSpec) {
        VaadinUtils.getUi(this).access(() -> {
            this.toolListBox.removeAll();
            List<ToolSpec> toolList = this.toolSpecService.getToolSpecList();

            if (toolList.isEmpty()) {
                this.toolChangeSupport.firePropertyChange(TOOL_EMPTY_EVENT, false, true);
                return;
            }

            this.toolListBox.setItems(toolList);

            ToolSpec selectedToolSpec = Objects.isNull(toolSpec) ? toolList.getFirst() :
                    toolList.stream().filter(t -> t.name().equals(toolSpec.name())).findFirst()
                            .orElse(toolList.getFirst());

            this.toolListBox.setValue(selectedToolSpec);
        });
    }

    public void clearSelectTool() {
        this.toolListBox.clear();
    }

    private Optional<ToolSpec> getCurrentToolAsOpt() {
        return Optional.ofNullable(this.toolListBox.getValue());
    }
}
