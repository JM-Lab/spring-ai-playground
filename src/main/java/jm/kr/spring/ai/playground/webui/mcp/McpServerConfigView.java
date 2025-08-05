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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfoService;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import jm.kr.spring.ai.playground.webui.JsonEditorWrapper;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static jm.kr.spring.ai.playground.webui.mcp.McpView.MCP_CONNECTION_CHANGE_EVENT;

@NpmPackage(value = "jsoneditor", version = "10.2.0")
@NpmPackage(value = "ace-builds", version = "1.43.2")
public class McpServerConfigView extends VerticalLayout {

    private final TextField serverNameField = new TextField("Server name");
    private final TextField descField = new TextField("Description");
    private final Span createdLabel = new Span();
    private final Span updatedLabel = new Span();

    private final H4 connectionHeader = new H4("MCP Connection");
    private final RadioButtonGroup<McpTransportType> transportRadioButtonGroup = new RadioButtonGroup<>();
    private final Map<McpTransportType, JsonEditorWrapper> editors = new EnumMap<>(McpTransportType.class);

    private final McpServerInfo mcpServerInfo;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;

    private Button saveAndConnectButton;

    private boolean nameChanged = false;
    private boolean descChanged = false;
    private boolean jsonChanged = false;
    private boolean transportChanged = false;

    private String originalName;
    private String originalDesc;
    private McpTransportType originalTransport;
    private String originalJson;


    public McpServerConfigView(McpServerInfo mcpServerInfo, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService, PropertyChangeSupport mcpServerInfoChangeSupport) {
        this.mcpServerInfo = mcpServerInfo;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpServerInfoChangeSupport = mcpServerInfoChangeSupport;
        buildLayout();
        populateFields();
    }

    private void buildLayout() {
        serverNameField.setHelperText("Use '-' instead of spaces");
        serverNameField.setWidthFull();
        descField.setWidthFull();

        createdLabel.getStyle().set("font-size", "0.8em").set("color", "gray");
        updatedLabel.getStyle().set("font-size", "0.8em").set("color", "gray");

        FormLayout metaForm = new FormLayout(serverNameField, descField, createdLabel, updatedLabel);
        metaForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        metaForm.setColspan(descField, 2);

        transportRadioButtonGroup.setLabel("Transport type");
        transportRadioButtonGroup.setItems(McpTransportType.values());
        transportRadioButtonGroup.setItemLabelGenerator(mcpTransportType -> mcpTransportType.name().replace('_', ' '));
        transportRadioButtonGroup.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                transportChanged = !Objects.equals(e.getValue(), originalTransport);
                updateSaveButtonState();
            }
            switchEditor(e.getValue());
        });

        addEditor(McpTransportType.STREAMABLE_HTTP, """
                {
                   "url": "http://localhost:8080",
                   "endpoint": "/mcp"
                 }""");
        addEditor(McpTransportType.SSE, """
                {
                   "url": "http://localhost:8080",
                   "sse-endpoint": "/sse"
                 }""");
        addEditor(McpTransportType.STDIO, """
                {
                   "command": "/path/to/server",
                   "args": [
                     "--port=8080",
                     "--mode=production"
                   ],
                   "env": {
                     "API_KEY": "your-api-key",
                     "DEBUG": "true"
                   }
                 }""");

        saveAndConnectButton = new Button("Save & Connect", VaadinIcon.CONNECT_O.create(), e -> saveAndConnect());
        saveAndConnectButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        saveAndConnectButton.setEnabled(false);

        serverNameField.addValueChangeListener(e -> {
            nameChanged = !Objects.equals(e.getValue(), originalName);
            validateNameField();
            updateSaveButtonState();
        });

        descField.addValueChangeListener(e -> {
            descChanged = !Objects.equals(e.getValue(), originalDesc);
            updateSaveButtonState();
        });

        add(metaForm, new Hr(), connectionHeader, transportRadioButtonGroup);

        editors.values().forEach(ed -> {
            ed.setVisible(false);
            add(ed);
        });

        HorizontalLayout footer = new HorizontalLayout(saveAndConnectButton);
        footer.setWidthFull();
        footer.setJustifyContentMode(JustifyContentMode.BETWEEN);
        add(footer);
    }

    private void addEditor(McpTransportType type, String templateJson) {
        JsonEditorWrapper ed = new JsonEditorWrapper();
        ed.setWidthFull();
        ed.setHeight("300px");
        ed.setJson(templateJson);
        ed.addJsonChangeListener(json -> {
            jsonChanged = !Objects.equals(json.getJson(), originalJson);
            updateSaveButtonState();
        });
        editors.put(type, ed);
    }

    private boolean validateNameField() {
        String name = serverNameField.getValue();
        if (name == null || name.isBlank()) {
            serverNameField.setInvalid(true);
            serverNameField.setErrorMessage("Server name cannot be empty");
            return false;
        }
        if (name.contains(" ")) {
            serverNameField.setInvalid(true);
            serverNameField.setErrorMessage("Server name cannot contain spaces");
            return false;
        }
        serverNameField.setInvalid(false);
        serverNameField.setErrorMessage(null);
        return true;
    }

    private void updateSaveButtonState() {
        boolean hasChanges = nameChanged || descChanged || jsonChanged || transportChanged;
        boolean isValid = validateNameField();
        saveAndConnectButton.setEnabled(hasChanges && isValid);
    }

    private boolean isFormValid() {
        return StringUtils.hasText(serverNameField.getValue());
    }

    private void switchEditor(McpTransportType show) {
        editors.forEach((t, ed) -> ed.setVisible(t == show));
    }

    private void populateFields() {

        originalName = mcpServerInfo.serverName();
        originalDesc = mcpServerInfo.description();
        originalTransport = mcpServerInfo.mcpTransportType();
        originalJson = mcpServerInfo.connectionAsJson();

        serverNameField.setValue(originalName != null ? originalName : "");
        descField.setValue(originalDesc != null ? originalDesc : "");

        createdLabel.setText("Created : " + Instant.ofEpochMilli(mcpServerInfo.createTimestamp()));
        updatedLabel.setText("Updated : " + Instant.ofEpochMilli(mcpServerInfo.updateTimestamp()));

        McpTransportType type = mcpServerInfo.mcpTransportType();
        String rawJson = mcpServerInfo.connectionAsJson();
        if (rawJson == null || rawJson.isBlank()) {
            JsonEditorWrapper editor = editors.get(type);
            rawJson = (editor != null) ? editor.getJsonSync() : "{}";
        }
        originalJson = rawJson;
        editors.get(type).setJson(rawJson);

        transportRadioButtonGroup.setValue(type);
        switchEditor(type);

        // 상태 초기화
        nameChanged = false;
        descChanged = false;
        jsonChanged = false;
        transportChanged = false;
        saveAndConnectButton.setEnabled(false);
    }

    private void saveAndConnect() {
        if (!isFormValid()) {
            VaadinUtils.showErrorNotification("Please correct the errors before saving.");
            return;
        }

        buildMcpServerInfoFromForm(
                uiMcpServerInfo -> {
                    if (this.mcpServerInfoService.getMcpServerInfos().get(uiMcpServerInfo.mcpTransportType()).stream()
                            .map(McpServerInfo::serverName).filter(uiMcpServerInfo.serverName()::equals).findAny()
                            .isPresent()) {
                        VaadinUtils.showErrorNotification("Failed to save : MCP connection already exists with name " +
                                uiMcpServerInfo.serverName());
                        return;
                    }

                    try {
                        this.mcpClientService.startMcpClient(uiMcpServerInfo);
                        this.mcpServerInfoChangeSupport.firePropertyChange(MCP_CONNECTION_CHANGE_EVENT,
                                mcpServerInfo, uiMcpServerInfo);
                    } catch (Exception e) {
                        VaadinUtils.showErrorNotification("Failed to connect : " + e.getMessage());
                    }
                });
    }

    private void buildMcpServerInfoFromForm(Consumer<McpServerInfo> callback) {
        McpTransportType mcpTransportType = transportRadioButtonGroup.getValue();
        JsonEditorWrapper jsonEditorWrapper = editors.get(mcpTransportType);
        String serverNameField = this.serverNameField.getValue();
        String descField = this.descField.getValue();
        jsonEditorWrapper.fetchJson(
                json -> callback.accept(mcpServerInfo.mutate(mcpTransportType, serverNameField, descField,
                        System.currentTimeMillis(), json)));
    }

}
