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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.TestConnectionResult;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.webui.JsonEditorWrapper;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.tool.StaticVariableForm;
import org.springframework.util.StringUtils;

import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.springaicommunity.playground.webui.mcp.McpServerView.MCP_CONNECTION_CHANGE_EVENT;

@NpmPackage(value = "jsoneditor", version = "10.2.0")
@NpmPackage(value = "ace-builds", version = "1.43.2")
public class McpServerConfigView extends VerticalLayout {

    /** Restricts server names to safe characters so they cannot escape the persistence directory. */
    private static final Pattern SAFE_SERVER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private static final String HEADERS_KEY = "headers";
    private static final String REQUIRED_ENV_KEY = "requiredEnv";
    private static final String OAUTH_KEY = "oauth";

    private static final ObjectMapper FORM_OBJECT_MAPPER = new ObjectMapper();

    public enum HeaderPreset {
        NONE("Insert auth header preset…", null, null),
        BEARER("Authorization (Bearer Token)", "Authorization", "Bearer ${YOUR_TOKEN}"),
        BASIC("Authorization (Basic Auth)", "Authorization", "Basic <base64(user:pass)>"),
        API_KEY("API Key Header", "X-API-Key", "${YOUR_API_KEY}"),
        OAUTH_2_1("OAuth 2.1 Authorization Code (configure)", null, null);

        private final String label;
        private final String headerName;
        private final String valueTemplate;

        HeaderPreset(String label, String headerName, String valueTemplate) {
            this.label = label;
            this.headerName = headerName;
            this.valueTemplate = valueTemplate;
        }

        public String headerName() { return headerName; }
        public String valueTemplate() { return valueTemplate; }

        @Override public String toString() { return label; }
    }

    private final TextField serverNameField = new TextField("Server name");
    private final TextField descField = new TextField("Description");
    private final Span createdLabel = new Span();
    private final Span updatedLabel = new Span();

    private final H4 connectionHeader = new H4("MCP Connection");
    private final RadioButtonGroup<McpTransportType> transportRadioButtonGroup = new RadioButtonGroup<>();
    private final Map<McpTransportType, JsonEditorWrapper> editors = new EnumMap<>(McpTransportType.class);

    private final VerticalLayout httpExtrasGroup = new VerticalLayout();

    private final VerticalLayout headersContainer = new VerticalLayout();
    private final List<StaticVariableForm> headerRows = new ArrayList<>();
    private final Button addHeaderButton = new Button(VaadinIcon.PLUS.create());
    private final Select<HeaderPreset> presetInsertSelect = new Select<>();
    private final OAuth21SubForm oauthSubForm =
            new OAuth21SubForm(this::onOAuthChanged, this::currentRegistrationId);

    private final McpServerInfo mcpServerInfo;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;

    private Button saveAndConnectButton;
    private Button testConnectionButton;

    private boolean nameChanged = false;
    private boolean descChanged = false;
    private boolean jsonChanged = false;
    private boolean transportChanged = false;
    private boolean extrasChanged = false;

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
            oauthSubForm.refreshRedirectInfo();
        });

        addEditor(McpTransportType.STREAMABLE_HTTP, """
                {
                   "url": "http://127.0.0.1:<server-port>",
                   "endpoint": "/mcp"
                 }""");
        addEditor(McpTransportType.SSE, """
                {
                   "url": "http://127.0.0.1:<server-port>",
                   "sse-endpoint": "/sse"
                 }""");
        addEditor(McpTransportType.STDIO, """
                {
                   "command": "/path/to/server",
                   "args": [
                     "--port=<server-port>",
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

        testConnectionButton = new Button("Test Connection", VaadinIcon.PLUG.create(), e -> testConnection());
        testConnectionButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        testConnectionButton.setTooltipText("Try to initialize without saving — verifies URL, headers, and env vars");

        serverNameField.addValueChangeListener(e -> {
            nameChanged = !Objects.equals(e.getValue(), originalName);
            validateNameField();
            updateSaveButtonState();
            oauthSubForm.refreshRedirectInfo();
        });

        descField.addValueChangeListener(e -> {
            descChanged = !Objects.equals(e.getValue(), originalDesc);
            updateSaveButtonState();
        });

        buildHttpExtrasGroup();

        add(metaForm, new Hr(), connectionHeader, transportRadioButtonGroup);

        editors.values().forEach(ed -> {
            ed.setVisible(false);
            add(ed);
        });

        add(httpExtrasGroup);

        HorizontalLayout footer = new HorizontalLayout(saveAndConnectButton, testConnectionButton);
        footer.setWidthFull();
        footer.setSpacing(true);
        add(footer);
    }

    private void buildHttpExtrasGroup() {
        httpExtrasGroup.setPadding(false);
        httpExtrasGroup.setSpacing(false);
        httpExtrasGroup.setWidthFull();

        H4 headersHeader = new H4("Headers");
        headersHeader.getStyle().set("margin-top", "0.4em");

        Span headersHelp = new Span(
                "Use ${ENV_VAR} in any value to source from environment or system properties.");
        headersHelp.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        headersContainer.setPadding(false);
        headersContainer.setSpacing(false);
        headersContainer.setWidthFull();

        addHeaderButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        addHeaderButton.setTooltipText("Add a custom header");
        addHeaderButton.addClickListener(e -> addHeaderRow(null, null));

        presetInsertSelect.setItems(HeaderPreset.values());
        presetInsertSelect.setValue(HeaderPreset.NONE);
        presetInsertSelect.setWidth("360px");
        presetInsertSelect.setEmptySelectionAllowed(false);
        presetInsertSelect.addValueChangeListener(e -> {
            HeaderPreset preset = e.getValue();
            if (preset == null || preset == HeaderPreset.NONE) return;
            if (preset == HeaderPreset.OAUTH_2_1) {
                oauthSubForm.setVisible(true);
                oauthSubForm.refreshRedirectInfo();
                extrasChanged = true;
                updateSaveButtonState();
            } else {
                addHeaderRow(preset.headerName(), preset.valueTemplate());
            }
            presetInsertSelect.setValue(HeaderPreset.NONE);
        });

        HorizontalLayout actionsRow = new HorizontalLayout(addHeaderButton, presetInsertSelect);
        actionsRow.setSpacing(true);
        actionsRow.setPadding(false);
        actionsRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        actionsRow.getStyle().set("margin-top", "0.4em");

        oauthSubForm.setVisible(false);

        httpExtrasGroup.add(oauthSubForm, headersHeader, headersHelp, headersContainer, actionsRow);
    }

    private void onOAuthChanged() {
        extrasChanged = true;
        updateSaveButtonState();
    }

    private String currentRegistrationId() {
        McpTransportType transport = transportRadioButtonGroup.getValue();
        String serverName = serverNameField.getValue();
        if (transport == null || !StringUtils.hasText(serverName)) return null;
        return "mcp-" + transport.name().toLowerCase() + "-" + serverName.trim();
    }

    private StaticVariableForm addHeaderRow(String key, String value) {
        StaticVariableForm row = new StaticVariableForm(headerRows.size() + 1,
                this::removeHeaderRow,
                () -> {});
        row.setKeyPlaceholder("Header-Name");
        row.setValuePlaceholder("value or ${ENV_VAR}");
        row.setAddButtonVisible(false);
        row.setDeleteButtonVisible(true);
        if (key != null || value != null) {
            row.update(Map.entry(key == null ? "" : key, value == null ? "" : value));
        }
        headerRows.add(row);
        headersContainer.add(row);
        extrasChanged = true;
        refreshHeaderRowIndexes();
        updateSaveButtonState();
        return row;
    }

    private void removeHeaderRow(StaticVariableForm row) {
        headerRows.remove(row);
        headersContainer.remove(row);
        extrasChanged = true;
        refreshHeaderRowIndexes();
        updateSaveButtonState();
    }

    private void refreshHeaderRowIndexes() {
        for (int i = 0; i < headerRows.size(); i++) {
            headerRows.get(i).updateIndex(i + 1);
        }
    }

    private boolean isHttpTransport(McpTransportType type) {
        return type == McpTransportType.SSE || type == McpTransportType.STREAMABLE_HTTP;
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
        if (!SAFE_SERVER_NAME.matcher(name).matches()) {
            serverNameField.setInvalid(true);
            serverNameField.setErrorMessage("Server name may only contain letters, digits, '.', '_', '-'");
            return false;
        }
        serverNameField.setInvalid(false);
        serverNameField.setErrorMessage(null);
        return true;
    }

    private void updateSaveButtonState() {
        boolean hasChanges = nameChanged || descChanged || jsonChanged || transportChanged || extrasChanged;
        boolean isValid = validateNameField();
        saveAndConnectButton.setEnabled(hasChanges && isValid);
    }

    private boolean isFormValid() {
        return StringUtils.hasText(serverNameField.getValue());
    }

    private void switchEditor(McpTransportType show) {
        editors.forEach((t, ed) -> ed.setVisible(t == show));
        httpExtrasGroup.setVisible(isHttpTransport(show));
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

        String editorJson = rawJson;
        if (isHttpTransport(type)) {
            try {
                JsonNode root = FORM_OBJECT_MAPPER.readTree(rawJson);
                if (root.isObject()) {
                    ObjectNode obj = (ObjectNode) root;
                    Map<String, String> headers = readHeaders(obj.get(HEADERS_KEY));
                    populateExtras(headers);
                    populateOAuth(obj.get(OAUTH_KEY));
                    obj.remove(HEADERS_KEY);
                    obj.remove(REQUIRED_ENV_KEY);
                    obj.remove(OAUTH_KEY);
                    editorJson = FORM_OBJECT_MAPPER.writeValueAsString(obj);
                }
            } catch (JsonProcessingException ignore) {
                resetExtras();
            }
        } else {
            resetExtras();
        }
        originalJson = editorJson;
        editors.get(type).setJson(editorJson);

        transportRadioButtonGroup.setValue(type);
        switchEditor(type);

        nameChanged = false;
        descChanged = false;
        jsonChanged = false;
        transportChanged = false;
        extrasChanged = false;
        saveAndConnectButton.setEnabled(false);
    }

    private void resetExtras() {
        headerRows.clear();
        headersContainer.removeAll();
        presetInsertSelect.setValue(HeaderPreset.NONE);
        oauthSubForm.clearFields();
        oauthSubForm.setVisible(false);
        extrasChanged = false;
    }

    private void populateExtras(Map<String, String> headers) {
        headerRows.clear();
        headersContainer.removeAll();
        presetInsertSelect.setValue(HeaderPreset.NONE);
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && !k.isBlank()) addHeaderRow(k, v == null ? "" : v);
            });
        }
        extrasChanged = false;
    }

    private void populateOAuth(JsonNode oauthNode) {
        if (oauthNode == null || oauthNode.isNull() || !oauthNode.isObject()) {
            oauthSubForm.clearFields();
            oauthSubForm.setVisible(false);
            return;
        }
        try {
            HttpConnectionParametersWithExtras.OAuth oauth = FORM_OBJECT_MAPPER.treeToValue(oauthNode,
                    HttpConnectionParametersWithExtras.OAuth.class);
            oauthSubForm.populate(oauth);
            oauthSubForm.setVisible(true);
        } catch (JsonProcessingException ignore) {
            oauthSubForm.clearFields();
            oauthSubForm.setVisible(false);
        }
    }

    private static Map<String, String> readHeaders(JsonNode node) {
        if (node == null || !node.isObject()) return new LinkedHashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), e.getValue().isNull() ? null : e.getValue().asText(null));
        }
        return out;
    }

    private Map<String, String> snapshotHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        for (StaticVariableForm row : headerRows) {
            String key = row.getKey();
            String value = row.getValue();
            if (key != null && !key.isBlank()) {
                headers.put(key, value == null ? "" : value);
            }
        }
        return headers;
    }

    private void saveAndConnect() {
        if (!isFormValid()) {
            VaadinUtils.showErrorNotification("Please correct the errors before saving.");
            return;
        }
        if (oauthSubForm.isVisible() && !oauthSubForm.validateForSave()) {
            VaadinUtils.showErrorNotification("Please correct the OAuth fields before saving.");
            return;
        }

        buildMcpServerInfoFromForm(
                uiMcpServerInfo -> {
                    boolean sameKey = mcpServerInfo != null
                            && mcpServerInfo.mcpTransportType() == uiMcpServerInfo.mcpTransportType()
                            && Objects.equals(mcpServerInfo.serverName(), uiMcpServerInfo.serverName());
                    if (!sameKey && this.mcpServerInfoService.getMcpServerInfos()
                            .get(uiMcpServerInfo.mcpTransportType()).stream().map(McpServerInfo::serverName)
                            .anyMatch(uiMcpServerInfo.serverName()::equals)) {
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
                        System.currentTimeMillis(), mergeExtras(mcpTransportType, json))));
    }

    private String mergeExtras(McpTransportType transport, String editorJson) {
        if (!isHttpTransport(transport)) return editorJson;
        try {
            JsonNode root = FORM_OBJECT_MAPPER.readTree(editorJson == null ? "{}" : editorJson);
            ObjectNode obj = root.isObject() ? (ObjectNode) root : FORM_OBJECT_MAPPER.createObjectNode();
            Map<String, String> headers = snapshotHeaders();

            obj.remove(REQUIRED_ENV_KEY);
            if (headers.isEmpty()) {
                obj.remove(HEADERS_KEY);
            } else {
                ObjectNode headersNode = FORM_OBJECT_MAPPER.createObjectNode();
                headers.forEach(headersNode::put);
                obj.set(HEADERS_KEY, headersNode);
            }

            HttpConnectionParametersWithExtras.OAuth oauth = oauthSubForm.isVisible() ? oauthSubForm.toRecord() : null;
            if (oauth == null) {
                obj.remove(OAUTH_KEY);
            } else {
                obj.set(OAUTH_KEY, FORM_OBJECT_MAPPER.valueToTree(oauth));
            }

            return FORM_OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return editorJson;
        }
    }

    private void testConnection() {
        if (!isFormValid()) {
            VaadinUtils.showErrorNotification("Please correct the errors before testing.");
            return;
        }
        buildMcpServerInfoFromForm(transientInfo -> {
            TestConnectionResult result = mcpClientService.testConnection(transientInfo);
            if (result.ok()) {
                VaadinUtils.showInfoNotification(
                        "Connection OK — discovered " + result.toolCount() + " tool(s).");
            } else {
                VaadinUtils.showErrorNotification("Test failed: " + result.error());
            }
        });
    }

}
