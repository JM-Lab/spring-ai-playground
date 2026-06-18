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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.CategoryDef;
import org.springaicommunity.playground.service.mcp.catalog.McpCategoryService;
import org.springaicommunity.playground.service.mcp.catalog.McpTagSuggestionService;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.TestConnectionResult;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.mcp.risk.McpAuthMode;
import org.springaicommunity.playground.service.mcp.risk.McpRegistrationRiskPreview;
import org.springaicommunity.playground.service.tool.ChipListBinding;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.util.SecretMasking;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.springaicommunity.playground.webui.mcp.McpServerView.MCP_CONNECTION_CHANGE_EVENT;

@NpmPackage(value = "jsoneditor", version = "10.2.0")
@NpmPackage(value = "ace-builds", version = "1.43.2")
public class McpServerConfigView extends VerticalLayout {

    // Restrict to safe chars so a server name can't escape the persistence directory.
    private static final Pattern SAFE_SERVER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private static final String HEADERS_KEY = "headers";
    private static final String REQUIRED_ENV_KEY = "requiredEnv";
    private static final String OAUTH_KEY = "oauth";

    private static final ObjectMapper FORM_OBJECT_MAPPER = new ObjectMapper();

    public enum HeaderPreset {
        NONE("Insert auth header preset…", null, null),
        BEARER("Authorization (Bearer Token)", "Authorization", "Bearer ${YOUR_TOKEN}"),
        BASIC("Authorization (Basic Auth)", "Authorization", "Basic <base64(user:pass)>"),
        API_KEY("API Key Header", "X-API-Key", "${YOUR_API_KEY}");

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
    private final TextArea descField = new TextArea("Description");
    private final ComboBox<String> categoryField = new ComboBox<>("Category");
    private final MultiSelectComboBox<String> tagsField = new MultiSelectComboBox<>("Tags");
    private final Span createdLabel = new Span();
    private final Span updatedLabel = new Span();

    private final H4 connectionHeader = new H4("MCP Connection");
    private final RadioButtonGroup<McpTransportType> transportRadioButtonGroup = new RadioButtonGroup<>();
    private final Map<McpTransportType, JsonEditorWrapper> editors = new EnumMap<>(McpTransportType.class);

    private final VerticalLayout httpUrlGroup = new VerticalLayout();
    private final TextField urlField = new TextField("URL");
    private final TextField endpointField = new TextField("Endpoint");
    private final TextField sseEndpointField = new TextField("SSE Endpoint");

    private final VerticalLayout httpExtrasGroup = new VerticalLayout();

    private final VerticalLayout headersContainer = new VerticalLayout();
    private final List<StaticVariableForm> headerRows = new ArrayList<>();
    private final Select<HeaderPreset> presetInsertSelect = new Select<>();
    private final OAuth21SubForm oauthSubForm =
            new OAuth21SubForm(this::onOAuthChanged, this::currentRegistrationId);
    private final Checkbox oauthEnabledCheckbox = new Checkbox("Use OAuth 2.1 authorization");

    private final VerticalLayout stdioGroup = new VerticalLayout();
    private final TextField commandField = new TextField("Command");
    private final VerticalLayout argsContainer = new VerticalLayout();
    private final List<ArgFormRow> argRows = new ArrayList<>();
    private final VerticalLayout envContainer = new VerticalLayout();
    private final List<StaticVariableForm> envRows = new ArrayList<>();

    private final McpServerInfo mcpServerInfo;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final McpCategoryService mcpCategoryService;
    private final McpTagSuggestionService mcpTagSuggestionService;
    private final McpRegistrationRiskPreview mcpRegistrationRiskPreview;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;

    private final Div riskPreviewPanel = new Div();
    private RiskLevel lastPreviewedLevel = RiskLevel.L1;

    private Button saveAndConnectButton;
    private Button testConnectionButton;

    private boolean nameChanged = false;
    private boolean descChanged = false;
    private boolean jsonChanged = false;
    private boolean transportChanged = false;
    private boolean extrasChanged = false;
    private boolean categoryChanged = false;
    private boolean tagsChanged = false;

    private String originalName;
    private String originalDesc;
    private McpTransportType originalTransport;
    private String originalJson;
    private String originalCategory;
    private Set<String> originalTags;


    public McpServerConfigView(McpServerInfo mcpServerInfo, McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService, McpCategoryService mcpCategoryService,
            McpTagSuggestionService mcpTagSuggestionService,
            McpRegistrationRiskPreview mcpRegistrationRiskPreview,
            PropertyChangeSupport mcpServerInfoChangeSupport) {
        this.mcpServerInfo = mcpServerInfo;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpCategoryService = mcpCategoryService;
        this.mcpTagSuggestionService = mcpTagSuggestionService;
        this.mcpRegistrationRiskPreview = mcpRegistrationRiskPreview;
        this.mcpServerInfoChangeSupport = mcpServerInfoChangeSupport;
        buildLayout();
        populateFields();
        refreshRiskPreview();
    }

    private void buildLayout() {
        serverNameField.setHelperText("Use '-' instead of spaces");
        serverNameField.setWidthFull();
        descField.setWidthFull();
        descField.setMinHeight("80px");
        descField.setMaxHeight("180px");

        createdLabel.getStyle().set("font-size", "0.8em").set("color", "gray");
        updatedLabel.getStyle().set("font-size", "0.8em").set("color", "gray");

        List<String> builtInIds = this.mcpCategoryService.getBuiltInCategories().stream()
                .map(CategoryDef::id).toList();
        categoryField.setItems(builtInIds);
        categoryField.setAllowCustomValue(true);
        categoryField.setHelperText("Pick a built-in or type a new label (e.g. 'personal').");
        categoryField.setWidthFull();
        categoryField.addCustomValueSetListener(e -> categoryField.setValue(e.getDetail().trim()));
        categoryField.addValueChangeListener(e -> {
            categoryChanged = !Objects.equals(normalizeCategory(e.getValue()), originalCategory);
            updateSaveButtonState();
        });

        Set<String> existingTags = mcpServerInfo.tags() == null ? Set.of() : mcpServerInfo.tags();
        Set<String> tagBaseline = new LinkedHashSet<>(this.mcpTagSuggestionService.collectTags());
        tagBaseline.addAll(existingTags);
        ChipListBinding tagsBinding = new ChipListBinding(tagBaseline);
        tagsBinding.replaceSelected(List.of());
        tagsField.setItems(tagsBinding.items());
        tagsField.setAllowCustomValue(true);
        tagsField.setHelperText("Pick suggested or type new (Enter to add).");
        tagsField.setWidthFull();
        tagsField.addCustomValueSetListener(e -> {
            if (tagsBinding.add(e.getDetail())) {
                tagsField.setItems(tagsBinding.items());
                tagsField.setValue(tagsBinding.selected());
            }
        });
        tagsField.addValueChangeListener(e -> {
            tagsBinding.replaceSelected(e.getValue());
            tagsChanged = !Objects.equals(snapshotTags(), originalTags);
            updateSaveButtonState();
        });

        FormLayout metaForm = new FormLayout(serverNameField, categoryField, tagsField, descField,
                createdLabel, updatedLabel);
        metaForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3)
        );
        metaForm.setColspan(descField, 3);
        metaForm.setColspan(createdLabel, 1);
        metaForm.setColspan(updatedLabel, 2);

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

        buildHttpUrlGroup();
        buildHttpExtrasGroup();
        buildOauthGroup();
        buildStdioGroup();

        riskPreviewPanel.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("flex-wrap", "wrap")
                .set("padding", "0")
                .set("border", "none")
                .set("background", "transparent");

        Span transportLabel = new Span("Transport type");
        transportLabel.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("margin-bottom", "var(--lumo-space-xs)");
        transportRadioButtonGroup.setLabel(null);
        transportRadioButtonGroup.getStyle().set("padding-top", "0");

        HorizontalLayout transportInlineRow = new HorizontalLayout(transportRadioButtonGroup, riskPreviewPanel);
        transportInlineRow.setPadding(false);
        transportInlineRow.setSpacing(true);
        transportInlineRow.setAlignItems(FlexComponent.Alignment.CENTER);
        transportInlineRow.getStyle().set("flex-wrap", "wrap").set("row-gap", "var(--lumo-space-s)");
        transportInlineRow.setWidthFull();

        VerticalLayout transportBlock = new VerticalLayout(transportLabel, transportInlineRow);
        transportBlock.setPadding(false);
        transportBlock.setSpacing(false);
        transportBlock.setWidthFull();

        add(metaForm, new Hr(), connectionHeader, transportBlock, httpUrlGroup);

        editors.values().forEach(ed -> {
            ed.setVisible(false);
            add(ed);
        });

        add(httpExtrasGroup, oauthEnabledCheckbox, oauthSubForm, stdioGroup);

        HorizontalLayout footer = new HorizontalLayout(saveAndConnectButton, testConnectionButton);
        footer.setSpacing(true);
        footer.setAlignItems(FlexComponent.Alignment.CENTER);

        add(footer);
    }

    private void refreshRiskPreview() {
        riskPreviewPanel.setVisible(true);
        riskPreviewPanel.removeAll();
        if (this.mcpClientService.isSelfLoopback(serverNameField.getValue())) {
            lastPreviewedLevel = RiskLevel.L0;
            McpRiskChip chip = new McpRiskChip(RiskLevel.L0, null, "Server")
                    .withTooltip("Built-in MCP server — risk model bypassed (spec § 5.4)");
            riskPreviewPanel.add(chip);
            return;
        }
        try {
            McpRegistrationRiskPreview.WizardInput input = buildWizardInputFromForm();
            McpRegistrationRiskPreview.PreviewResult result = mcpRegistrationRiskPreview.preview(input);
            lastPreviewedLevel = result.serverResult().level();
            StringBuilder why = new StringBuilder("Why ").append(lastPreviewedLevel.name()).append(": ");
            boolean first = true;
            for (Map.Entry<String, String> rationale : result.rationale().entrySet()) {
                if (!first) why.append(" · ");
                why.append(rationale.getKey()).append('=').append(rationale.getValue());
                first = false;
            }
            McpRiskChip chip = new McpRiskChip(lastPreviewedLevel,
                    result.serverResult().floorTrigger(), "Server").withTooltip(why.toString());
            riskPreviewPanel.add(chip);
        } catch (RuntimeException ex) {
            Span hint = new Span("Risk preview unavailable: " + ex.getMessage());
            hint.getStyle().set("color", "var(--lumo-error-text-color)");
            riskPreviewPanel.add(hint);
        }
    }

    private McpRegistrationRiskPreview.WizardInput buildWizardInputFromForm() {
        McpTransportType transport = transportRadioButtonGroup.getValue();
        String url = transport == McpTransportType.STDIO ? null : urlField.getValue();
        McpAuthMode authMode = resolveAuthModeFromForm();
        return new McpRegistrationRiskPreview.WizardInput(
                serverNameField.getValue(),
                transport,
                url,
                authMode,
                null,
                null,
                null,
                true,
                true,
                null,
                List.of());
    }

    private McpAuthMode resolveAuthModeFromForm() {
        if (Boolean.TRUE.equals(oauthEnabledCheckbox.getValue())) {
            return McpAuthMode.OAUTH_STANDARD;
        }
        for (StaticVariableForm row : headerRows) {
            String key = row.getKey();
            if (key == null) continue;
            String lower = key.toLowerCase();
            if (lower.equals("authorization")) {
                String value = row.getValue();
                if (value != null && value.toLowerCase().startsWith("bearer")) return McpAuthMode.BEARER;
                return McpAuthMode.API_KEY;
            }
            if (lower.contains("api-key") || lower.contains("apikey") || lower.contains("x-api-key")) {
                return McpAuthMode.API_KEY;
            }
        }
        return McpAuthMode.NONE;
    }

    private boolean isPersisted() {
        if (mcpServerInfo == null || mcpServerInfo.serverName() == null) return false;
        return mcpServerInfoService.getMcpServerInfos()
                .getOrDefault(mcpServerInfo.mcpTransportType(), List.of())
                .stream().anyMatch(saved -> saved.serverName().equals(mcpServerInfo.serverName()));
    }

    private void buildHttpUrlGroup() {
        httpUrlGroup.setPadding(false);
        httpUrlGroup.setSpacing(false);
        httpUrlGroup.setWidthFull();

        Span urlHelp = new Span(
                "Use ${ENV_VAR} in URL or endpoint to source from environment or system properties.");
        urlHelp.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        urlField.setWidthFull();
        urlField.setPlaceholder("e.g. https://mcp.example.com or ${VENDOR_BASE_URL}");
        urlField.addValueChangeListener(e -> {
            if (StringUtils.hasText(e.getValue())) {
                urlField.setInvalid(false);
                urlField.setErrorMessage(null);
            }
            extrasChanged = true;
            updateSaveButtonState();
        });

        endpointField.setWidthFull();
        endpointField.setPlaceholder("e.g. /mcp");
        endpointField.addValueChangeListener(e -> {
            extrasChanged = true;
            updateSaveButtonState();
        });

        sseEndpointField.setWidthFull();
        sseEndpointField.setPlaceholder("e.g. /sse");
        sseEndpointField.addValueChangeListener(e -> {
            extrasChanged = true;
            updateSaveButtonState();
        });

        FormLayout urlForm = new FormLayout(urlField, endpointField, sseEndpointField);
        urlForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2)
        );
        urlForm.setColspan(urlField, 1);
        urlForm.setColspan(endpointField, 1);
        urlForm.setColspan(sseEndpointField, 1);

        httpUrlGroup.add(urlForm, urlHelp);
    }

    private void buildStdioGroup() {
        stdioGroup.setPadding(false);
        stdioGroup.setSpacing(false);
        stdioGroup.setWidthFull();

        Span cmdHelp = new Span(
                "Use ${ENV_VAR} in command, arguments, or env values to source from environment or system properties.");
        cmdHelp.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        commandField.setWidthFull();
        commandField.setPlaceholder("e.g. npx, uvx, /path/to/server");
        commandField.addValueChangeListener(e -> {
            extrasChanged = true;
            updateSaveButtonState();
        });

        Span argsHeader = new Span("Arguments");
        argsHeader.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-s)");

        argsContainer.setPadding(false);
        argsContainer.setSpacing(false);
        argsContainer.setWidthFull();

        Span envHeader = new Span("Environment variables");
        envHeader.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-s)");

        envContainer.setPadding(false);
        envContainer.setSpacing(false);
        envContainer.setWidthFull();

        Button addEnvButton = new Button(VaadinIcon.PLUS.create());
        addEnvButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        addEnvButton.setTooltipText("Add environment variable");
        addEnvButton.addClickListener(e -> addEnvRow(null, null));

        HorizontalLayout envHeaderRow = new HorizontalLayout(envHeader, addEnvButton);
        envHeaderRow.setAlignItems(FlexComponent.Alignment.CENTER);
        envHeaderRow.setSpacing(false);
        envHeaderRow.setPadding(false);
        envHeaderRow.getStyle().set("gap", "var(--lumo-space-xs)");

        stdioGroup.add(commandField, cmdHelp, argsHeader, argsContainer, envHeaderRow, envContainer);
    }

    private ArgFormRow addArgRow(String value) {
        ArgFormRow row = new ArgFormRow(argRows.size() + 1,
                this::removeArgRow,
                () -> addArgRow(null),
                () -> {
                    extrasChanged = true;
                    updateSaveButtonState();
                });
        if (value != null) row.setValue(value);
        argRows.add(row);
        argsContainer.add(row);
        extrasChanged = true;
        refreshArgRowIndexes();
        updateArgRowButtons();
        updateSaveButtonState();
        return row;
    }

    private void removeArgRow(ArgFormRow row) {
        if (argRows.size() <= 1) {
            row.setValue("");
            return;
        }
        argRows.remove(row);
        argsContainer.remove(row);
        extrasChanged = true;
        refreshArgRowIndexes();
        updateArgRowButtons();
        updateSaveButtonState();
    }

    private void refreshArgRowIndexes() {
        for (int i = 0; i < argRows.size(); i++) {
            argRows.get(i).updateIndex(i + 1);
        }
    }

    private void updateArgRowButtons() {
        int size = argRows.size();
        boolean hasMultiple = size > 1;
        for (int i = 0; i < size; i++) {
            ArgFormRow row = argRows.get(i);
            row.setAddButtonVisible(i == size - 1);
            row.setDeleteButtonVisible(hasMultiple);
        }
    }

    private StaticVariableForm addEnvRow(String key, String value) {
        StaticVariableForm row = new StaticVariableForm(envRows.size() + 1,
                this::removeEnvRow,
                () -> addEnvRow(null, null));
        row.setKeyPlaceholder("e.g. API_KEY");
        row.setValuePlaceholder("e.g. ${YOUR_VALUE}");
        if (key != null || value != null) {
            row.update(Map.entry(key == null ? "" : key, value == null ? "" : value));
        }
        envRows.add(row);
        envContainer.add(row);
        extrasChanged = true;
        refreshEnvRowIndexes();
        updateEnvRowButtons();
        updateSaveButtonState();
        return row;
    }

    private void removeEnvRow(StaticVariableForm row) {
        envRows.remove(row);
        envContainer.remove(row);
        extrasChanged = true;
        refreshEnvRowIndexes();
        updateEnvRowButtons();
        updateSaveButtonState();
    }

    private void refreshEnvRowIndexes() {
        for (int i = 0; i < envRows.size(); i++) {
            envRows.get(i).updateIndex(i + 1);
        }
    }

    private void updateEnvRowButtons() {
        for (StaticVariableForm form : envRows) {
            form.setAddButtonVisible(false);
            form.setDeleteButtonVisible(true);
        }
    }

    private void buildHttpExtrasGroup() {
        httpExtrasGroup.setPadding(false);
        httpExtrasGroup.setSpacing(false);
        httpExtrasGroup.setWidthFull();

        Span headersHeader = new Span("Headers");
        headersHeader.getStyle()
                .set("display", "block")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "var(--lumo-space-s)");

        Span headersHelp = new Span(
                "Use ${ENV_VAR} in any value to source from environment or system properties.");
        headersHelp.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        headersContainer.setPadding(false);
        headersContainer.setSpacing(false);
        headersContainer.setWidthFull();

        presetInsertSelect.setItems(HeaderPreset.values());
        presetInsertSelect.setValue(HeaderPreset.NONE);
        presetInsertSelect.setWidth("360px");
        presetInsertSelect.setEmptySelectionAllowed(false);
        presetInsertSelect.setHelperText("Pick a preset to add a pre-filled auth header row below");
        presetInsertSelect.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
        presetInsertSelect.addValueChangeListener(e -> {
            HeaderPreset preset = e.getValue();
            if (preset == null || preset == HeaderPreset.NONE) return;
            StaticVariableForm emptyRow = headerRows.stream().filter(StaticVariableForm::isEmpty)
                    .reduce((a, b) -> b).orElse(null);
            if (emptyRow != null) {
                emptyRow.update(Map.entry(preset.headerName(), preset.valueTemplate()));
                extrasChanged = true;
                updateSaveButtonState();
            } else {
                addHeaderRow(preset.headerName(), preset.valueTemplate());
            }
            presetInsertSelect.setValue(HeaderPreset.NONE);
        });

        Button addCustomHeaderButton = new Button(VaadinIcon.PLUS.create());
        addCustomHeaderButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        addCustomHeaderButton.setTooltipText("Add custom header");
        addCustomHeaderButton.addClickListener(e -> addHeaderRow(null, null));

        presetInsertSelect.getStyle().set("margin-bottom", "0");
        HorizontalLayout headerActionsRow = new HorizontalLayout(presetInsertSelect, addCustomHeaderButton);
        headerActionsRow.setSpacing(false);
        headerActionsRow.setAlignItems(FlexComponent.Alignment.BASELINE);
        headerActionsRow.getStyle().set("gap", "var(--lumo-space-xs)");

        httpExtrasGroup.add(headersHeader, headersHelp, headerActionsRow, headersContainer);
    }

    private void buildOauthGroup() {
        oauthEnabledCheckbox.getStyle().set("margin-top", "var(--lumo-space-m)")
                .set("font-weight", "500");
        oauthEnabledCheckbox.addValueChangeListener(e -> {
            boolean enabled = Boolean.TRUE.equals(e.getValue());
            oauthSubForm.setVisible(enabled);
            if (!enabled) oauthSubForm.clearFields();
            extrasChanged = true;
            updateSaveButtonState();
        });
        oauthSubForm.setVisible(false);
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
                () -> addHeaderRow(null, null));
        row.setKeyPlaceholder("e.g. Authorization");
        row.setValuePlaceholder("e.g. Bearer ${YOUR_TOKEN}");
        if (key != null || value != null) {
            row.update(Map.entry(key == null ? "" : key, value == null ? "" : value));
        }
        headerRows.add(row);
        headersContainer.add(row);
        extrasChanged = true;
        refreshHeaderRowIndexes();
        updateHeaderRowButtons();
        updateSaveButtonState();
        return row;
    }

    private void removeHeaderRow(StaticVariableForm row) {
        headerRows.remove(row);
        headersContainer.remove(row);
        extrasChanged = true;
        refreshHeaderRowIndexes();
        updateHeaderRowButtons();
        updateSaveButtonState();
    }

    private void refreshHeaderRowIndexes() {
        for (int i = 0; i < headerRows.size(); i++) {
            headerRows.get(i).updateIndex(i + 1);
        }
    }

    private void updateHeaderRowButtons() {
        for (StaticVariableForm form : headerRows) {
            form.setAddButtonVisible(false);
            form.setDeleteButtonVisible(true);
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
        boolean hasChanges = nameChanged || descChanged || jsonChanged || transportChanged || extrasChanged
                || categoryChanged || tagsChanged;
        boolean isNewOrGhost = !isPersisted();
        boolean isValid = validateNameField();
        saveAndConnectButton.setEnabled((hasChanges || isNewOrGhost) && isValid);
        refreshRiskPreview();
    }

    private String normalizeCategory(String raw) {
        return raw == null || raw.isBlank() ? McpServerInfo.DEFAULT_CATEGORY : raw.trim();
    }

    private Set<String> snapshotTags() {
        Set<String> v = tagsField.getValue();
        if (v == null) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        v.forEach(t -> {
            if (t != null && !t.isBlank()) out.add(t.trim());
        });
        return out;
    }

    private boolean isFormValid() {
        if (!StringUtils.hasText(serverNameField.getValue())) return false;
        McpTransportType type = transportRadioButtonGroup.getValue();
        return !(type != null && isHttpTransport(type) && !StringUtils.hasText(urlField.getValue()));
    }

    private void switchEditor(McpTransportType show) {
        boolean isHttp = isHttpTransport(show);
        boolean isStdio = show == McpTransportType.STDIO;
        editors.forEach((t, ed) -> ed.setVisible(false));
        httpUrlGroup.setVisible(isHttp);
        httpExtrasGroup.setVisible(isHttp);
        oauthEnabledCheckbox.setVisible(isHttp);
        oauthSubForm.setVisible(isHttp && Boolean.TRUE.equals(oauthEnabledCheckbox.getValue()));
        stdioGroup.setVisible(isStdio);
        endpointField.setVisible(show == McpTransportType.STREAMABLE_HTTP);
        sseEndpointField.setVisible(show == McpTransportType.SSE);
    }

    private void populateFields() {

        originalName = mcpServerInfo.serverName();
        originalDesc = mcpServerInfo.description();
        originalTransport = mcpServerInfo.mcpTransportType();
        originalJson = mcpServerInfo.connectionAsJson();
        originalCategory = normalizeCategory(mcpServerInfo.category());
        originalTags = mcpServerInfo.tags() == null ? Set.of() : Set.copyOf(mcpServerInfo.tags());

        serverNameField.setValue(originalName != null ? originalName : "");
        descField.setValue(originalDesc != null ? originalDesc : "");
        categoryField.setValue(originalCategory);
        tagsField.setValue(new LinkedHashSet<>(originalTags));

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
                    populateHttpUrl(obj, type);
                    obj.remove(HEADERS_KEY);
                    obj.remove(REQUIRED_ENV_KEY);
                    obj.remove(OAUTH_KEY);
                    obj.remove("url");
                    obj.remove(type == McpTransportType.SSE ? "sse-endpoint" : "endpoint");
                    editorJson = FORM_OBJECT_MAPPER.writeValueAsString(obj);
                }
            } catch (JacksonException ignore) {
                resetExtras();
                resetHttpUrl();
            }
            resetStdio();
        } else if (type == McpTransportType.STDIO) {
            try {
                JsonNode root = FORM_OBJECT_MAPPER.readTree(rawJson);
                if (root.isObject()) {
                    populateStdio((ObjectNode) root);
                    editorJson = "{}";
                }
            } catch (JacksonException ignore) {
                resetStdio();
            }
            resetExtras();
            resetHttpUrl();
        } else {
            resetExtras();
            resetHttpUrl();
            resetStdio();
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
        categoryChanged = false;
        tagsChanged = false;
        updateSaveButtonState();
    }

    private void resetExtras() {
        headerRows.clear();
        headersContainer.removeAll();
        presetInsertSelect.setValue(HeaderPreset.NONE);
        oauthSubForm.clearFields();
        oauthEnabledCheckbox.setValue(false);
        oauthSubForm.setVisible(false);
        extrasChanged = false;
    }

    private void populateHttpUrl(ObjectNode obj, McpTransportType type) {
        String url = obj.has("url") && !obj.get("url").isNull() ? obj.get("url").asText("") : "";
        urlField.setValue(url);
        if (type == McpTransportType.SSE) {
            String endpoint = obj.has("sse-endpoint") && !obj.get("sse-endpoint").isNull()
                    ? obj.get("sse-endpoint").asText("") : "";
            sseEndpointField.setValue(endpoint);
            endpointField.setValue("");
        } else {
            String endpoint = obj.has("endpoint") && !obj.get("endpoint").isNull()
                    ? obj.get("endpoint").asText("") : "";
            endpointField.setValue(endpoint);
            sseEndpointField.setValue("");
        }
    }

    private void resetHttpUrl() {
        urlField.setValue("");
        endpointField.setValue("");
        sseEndpointField.setValue("");
    }

    private void populateStdio(ObjectNode obj) {
        String command = obj.has("command") && !obj.get("command").isNull()
                ? obj.get("command").asText("") : "";
        commandField.setValue(command);

        argRows.clear();
        argsContainer.removeAll();
        JsonNode argsNode = obj.get("args");
        if (argsNode != null && argsNode.isArray()) {
            for (JsonNode a : argsNode) {
                addArgRow(a.isTextual() ? a.asText() : a.toString());
            }
        }
        if (argRows.isEmpty()) addArgRow(null);

        envRows.clear();
        envContainer.removeAll();
        JsonNode envNode = obj.get("env");
        if (envNode != null && envNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = envNode.properties().iterator();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                String v = e.getValue().isNull() ? "" : e.getValue().asText("");
                addEnvRow(e.getKey(), v);
            }
        }
        extrasChanged = false;
    }

    private void resetStdio() {
        commandField.setValue("");
        argRows.clear();
        argsContainer.removeAll();
        addArgRow(null);
        envRows.clear();
        envContainer.removeAll();
        addEnvRow(null, null);
    }

    private String buildStdioJson() {
        try {
            ObjectNode obj = FORM_OBJECT_MAPPER.createObjectNode();

            String cmd = commandField.getValue();
            if (cmd != null && !cmd.isBlank()) obj.put("command", cmd.trim());

            if (!argRows.isEmpty()) {
                ArrayNode argsArr = FORM_OBJECT_MAPPER.createArrayNode();
                for (ArgFormRow row : argRows) {
                    String value = row.getValue();
                    if (value != null && !value.isBlank()) argsArr.add(value.trim());
                }
                if (!argsArr.isEmpty()) obj.set("args", argsArr);
            }

            if (!envRows.isEmpty()) {
                ObjectNode envObj = FORM_OBJECT_MAPPER.createObjectNode();
                for (StaticVariableForm row : envRows) {
                    String key = row.getKey();
                    String value = row.getValue();
                    if (key != null && !key.isBlank()) {
                        envObj.put(key, value == null ? "" : value);
                    }
                }
                if (!envObj.isEmpty()) obj.set("env", envObj);
            }

            return FORM_OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private void populateExtras(Map<String, String> headers) {
        headerRows.clear();
        headersContainer.removeAll();
        presetInsertSelect.setValue(HeaderPreset.NONE);
        if (headers != null) {
            headers.forEach((k, v) -> {
                if (k != null && !k.isBlank()) {
                    addHeaderRow(k, v == null ? "" : v);
                }
            });
        }
        extrasChanged = false;
    }

    private void populateOAuth(JsonNode oauthNode) {
        if (oauthNode == null || oauthNode.isNull() || !oauthNode.isObject()) {
            oauthSubForm.clearFields();
            oauthEnabledCheckbox.setValue(false);
            oauthSubForm.setVisible(false);
            return;
        }
        try {
            HttpConnectionParametersWithExtras.OAuth oauth = FORM_OBJECT_MAPPER.treeToValue(oauthNode,
                    HttpConnectionParametersWithExtras.OAuth.class);
            oauthSubForm.populate(oauth);
            oauthEnabledCheckbox.setValue(true);
            oauthSubForm.setVisible(true);
        } catch (JacksonException ignore) {
            oauthSubForm.clearFields();
            oauthEnabledCheckbox.setValue(false);
            oauthSubForm.setVisible(false);
        }
    }

    private static Map<String, String> readHeaders(JsonNode node) {
        if (node == null || !node.isObject()) return new LinkedHashMap<>();
        Map<String, String> out = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.properties().iterator();
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
        if (lastPreviewedLevel != null
                && lastPreviewedLevel.ordinal() >= RiskLevel.L4.ordinal()) {
            showHighRiskConfirmation(this::doSaveAndConnect);
            return;
        }
        doSaveAndConnect();
    }

    private void showHighRiskConfirmation(Runnable onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("High risk server (" + lastPreviewedLevel.name() + ")");
        Span body = new Span("This server registration carries a "
                + lastPreviewedLevel.name() + " risk level based on transport/auth/trust signals. "
                + "Proceed only if you accept the rationale above.");
        dialog.add(body);
        Button cancel = new Button("Cancel", e -> dialog.close());
        Button proceed = new Button("I understand, proceed", e -> {
            dialog.close();
            onConfirm.run();
        });
        proceed.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        dialog.getFooter().add(cancel, proceed);
        dialog.open();
    }

    private void doSaveAndConnect() {
        McpTransportType transportForValidation = transportRadioButtonGroup.getValue();
        if (transportForValidation != null && isHttpTransport(transportForValidation)
                && !StringUtils.hasText(urlField.getValue())) {
            urlField.setInvalid(true);
            urlField.setErrorMessage("URL is required for HTTP/SSE transport");
        }
        if (!isFormValid()) {
            VaadinUtils.showErrorNotification("Please correct the errors before saving.");
            return;
        }
        McpTransportType selectedTransport = transportRadioButtonGroup.getValue();
        boolean isHttp = selectedTransport != null && isHttpTransport(selectedTransport);
        if (isHttp && Boolean.TRUE.equals(oauthEnabledCheckbox.getValue())
                && !oauthSubForm.validateForSave()) {
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
                        String raw = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        String safe = SecretMasking.mask(raw,
                                SecretMasking.collectFromTemplate(uiMcpServerInfo.connectionAsJson()));
                        VaadinUtils.showErrorNotification("Failed to connect : " + safe);
                    }
                });
    }

    private void buildMcpServerInfoFromForm(Consumer<McpServerInfo> callback) {
        McpTransportType mcpTransportType = transportRadioButtonGroup.getValue();
        String serverName = this.serverNameField.getValue();
        String description = this.descField.getValue();
        String category = normalizeCategory(this.categoryField.getValue());
        Set<String> tags = snapshotTags();
        String connectionJson = mergeExtras(mcpTransportType, "{}");
        callback.accept(mcpServerInfo.mutate(mcpTransportType, serverName, description,
                System.currentTimeMillis(), connectionJson, category, tags));
    }

    private String mergeExtras(McpTransportType transport, String editorJson) {
        if (transport == McpTransportType.STDIO) return buildStdioJson();
        if (!isHttpTransport(transport)) return editorJson;
        try {
            JsonNode root = FORM_OBJECT_MAPPER.readTree(editorJson == null ? "{}" : editorJson);
            ObjectNode obj = root.isObject() ? (ObjectNode) root : FORM_OBJECT_MAPPER.createObjectNode();
            Map<String, String> headers = snapshotHeaders();

            String urlValue = urlField.getValue();
            if (urlValue != null && !urlValue.isBlank()) {
                obj.put("url", urlValue.trim());
            } else {
                obj.remove("url");
            }
            String endpointKey;
            String endpointValue;
            if (transport == McpTransportType.SSE) {
                endpointKey = "sse-endpoint";
                endpointValue = sseEndpointField.getValue();
                obj.remove("endpoint");
            } else {
                endpointKey = "endpoint";
                endpointValue = endpointField.getValue();
                obj.remove("sse-endpoint");
            }
            if (endpointValue != null && !endpointValue.isBlank()) {
                obj.put(endpointKey, endpointValue.trim());
            } else {
                obj.remove(endpointKey);
            }

            obj.remove(REQUIRED_ENV_KEY);
            if (headers.isEmpty()) {
                obj.remove(HEADERS_KEY);
            } else {
                ObjectNode headersNode = FORM_OBJECT_MAPPER.createObjectNode();
                headers.forEach(headersNode::put);
                obj.set(HEADERS_KEY, headersNode);
            }

            HttpConnectionParametersWithExtras.OAuth oauth = Boolean.TRUE.equals(oauthEnabledCheckbox.getValue())
                    ? oauthSubForm.toRecord() : null;
            if (oauth == null || !StringUtils.hasText(oauth.issuerUri())) {
                obj.remove(OAUTH_KEY);
            } else {
                obj.set(OAUTH_KEY, FORM_OBJECT_MAPPER.valueToTree(oauth));
            }

            return FORM_OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JacksonException e) {
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
