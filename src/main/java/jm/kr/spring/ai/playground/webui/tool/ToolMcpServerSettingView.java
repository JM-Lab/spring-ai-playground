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

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jm.kr.spring.ai.playground.service.tool.ToolSpec;
import jm.kr.spring.ai.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ToolMcpServerSettingView extends VerticalLayout {
    private final Checkbox autoAddCheckbox;
    private final MultiSelectComboBox<ToolSpec> toolSelector;

    public ToolMcpServerSettingView(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {
        setSizeFull();
        setPadding(false);
        setSpacing(true);

        this.autoAddCheckbox = new Checkbox("Enable Auto-Add Tools");

        this.toolSelector = new MultiSelectComboBox<>("Select Tools");
        this.toolSelector.setPlaceholder("Select tools to enable...");
        this.toolSelector.setWidth("100%");
        this.toolSelector.setMaxWidth("600px");
        this.toolSelector.setClearButtonVisible(true);
        this.toolSelector.setItemLabelGenerator(ToolSpec::name);

        add(this.autoAddCheckbox, this.toolSelector);
        update(toolSpecs, toolMcpServerSetting);
    }

    public ToolMcpServerSetting getUiToolMcpServerSetting() {
        return new ToolMcpServerSetting(autoAddCheckbox.getValue(),
                this.toolSelector.getSelectedItems().stream().map(ToolSpec::toolId).collect(Collectors.toSet()));
    }

    public void update(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {
        this.autoAddCheckbox.setValue(toolMcpServerSetting.autoAdd());
        this.toolSelector.setItems(toolSpecs);
        Set<String> exposedToolIds = toolMcpServerSetting.exposedToolIds();
        if (!exposedToolIds.isEmpty()) {
            toolSpecs.stream().filter(toolSpec -> exposedToolIds.contains(toolSpec.toolId()))
                    .forEach(toolSelector::select);
        }
    }
}
