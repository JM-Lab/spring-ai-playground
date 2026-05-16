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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.tool.ChipListBinding;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpec.JsonSchemaType;
import org.springaicommunity.playground.service.tool.ToolSpec.ToolParamSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolCategoryCatalog;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator;
import org.springaicommunity.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springaicommunity.playground.webui.tool.ToolStudioView.TOOL_CHANGE_EVENT;

public class ToolBuilderView extends VerticalLayout {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private enum SaveAction { SAVE_DRAFT, PUBLISH, UPDATE }

    private final ToolSpec toolSpec;
    private final PropertyChangeSupport toolChangeSupport;
    private final ToolSpecService toolSpecService;
    private final ToolCategoryCatalog categoryCatalog;
    private final HorizontalLayout paramsContainer;
    private final List<ToolParameterForm> paramForms;
    private final TextField toolNameField;
    private final TextArea toolDescriptionField;
    private final ComboBox<String> categoryField;
    private final MultiSelectComboBox<String> tagsField;
    private final JavascriptToolPlaygroundView javascriptToolPlaygroundView;
    private Button saveDraftButton;
    private Button publishButton;
    private Button updateButton;

    public ToolBuilderView(ToolSpec toolSpec, PropertyChangeSupport toolChangeSupport, ToolSpecService toolSpecService,
            ToolCategoryCatalog categoryCatalog, SpringAiPlaygroundOptions options,
            SandboxPostureCalculator postureCalculator, ObjectMapper objectMapper) {
        this.toolSpec = toolSpec;
        this.toolChangeSupport = toolChangeSupport;
        this.toolSpecService = toolSpecService;
        this.categoryCatalog = categoryCatalog;
        this.paramForms = new ArrayList<>();

        setSizeFull();
        setSpacing(true);
        setMargin(false);
        setPadding(false);
        getStyle()
                .set("padding-block", "var(--lumo-space-m)")
                .set("padding-inline-start", "var(--lumo-space-m)");

        this.toolNameField = new TextField("Tool Name");
        this.toolNameField.setPlaceholder(
                "Letters, numbers, and '_' only. Must start with a letter. e.g., getWeather or get_weather");
        this.toolNameField.setRequired(true);
        this.toolNameField.setWidthFull();

        this.toolNameField.addValueChangeListener(e -> {
            String value = e.getValue();

            if (value == null || value.isBlank()) {
                this.toolNameField.setInvalid(true);
                this.toolNameField.setErrorMessage("Tool Name is required.");
                return;
            }

            if (!TOOL_NAME_PATTERN.matcher(value).matches()) {
                this.toolNameField.setInvalid(true);
                this.toolNameField.setErrorMessage(
                        "Use letters, numbers, and '_' only. Must start with a letter.");
                return;
            }

            this.toolNameField.setInvalid(false);
        });

        this.toolDescriptionField = new TextArea("Tool Description");
        this.toolDescriptionField.setPlaceholder("e.g., Get the current weather for a location");
        this.toolDescriptionField.setWidthFull();

        this.categoryField = new ComboBox<>("Category");
        this.categoryField.setItems(this.categoryCatalog.categoryIds());
        this.categoryField.setItemLabelGenerator(id -> this.categoryCatalog.resolveOrFallback(id).displayName());
        this.categoryField.setValue("CUSTOM");

        this.tagsField = new MultiSelectComboBox<>("Tags");
        this.tagsField.setAllowCustomValue(true);

        Set<String> observedTags = new LinkedHashSet<>();
        for (ToolSpec existing : this.toolSpecService.getToolSpecList()) observedTags.addAll(existing.tags());
        ChipListBinding tagsBinding = new ChipListBinding(observedTags);
        tagsBinding.replaceSelected(List.of());
        this.tagsField.setItems(tagsBinding.items());
        this.tagsField.addCustomValueSetListener(e -> {
            if (tagsBinding.add(e.getDetail())) {
                this.tagsField.setItems(tagsBinding.items());
                this.tagsField.setValue(tagsBinding.selected());
            }
        });
        this.tagsField.addValueChangeListener(e -> tagsBinding.replaceSelected(e.getValue()));

        this.toolNameField.setMinWidth("0");
        this.categoryField.setMinWidth("0");
        this.tagsField.setMinWidth("0");
        this.toolNameField.getStyle().set("flex", "2 1 280px");
        this.categoryField.getStyle().set("flex", "1 1 160px");
        this.tagsField.getStyle().set("flex", "1 1 200px");
        Div topRow = new Div(this.toolNameField, this.categoryField, this.tagsField);
        topRow.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%")
                .set("align-items", "flex-end");

        Span paramsHint =
                new Span("Structured tool parameters defined in the tool spec and passed by the LLM at call time");
        paramsHint.getElement().getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout paramsHeader =
                new HorizontalLayout(new H4("Tool Parameters"), paramsHint);
        paramsHeader.setAlignItems(Alignment.BASELINE);

        this.paramsContainer = new HorizontalLayout();
        paramsContainer.setPadding(false);
        paramsContainer.setSpacing(true);
        paramsContainer.setSizeFull();
        paramsContainer.getStyle().set("overflow-x", "auto");
        paramsContainer.setDefaultVerticalComponentAlignment(Alignment.START);

        VerticalLayout parametersSection = new VerticalLayout(paramsHeader, paramsContainer);
        parametersSection.setPadding(false);
        parametersSection.setSpacing(true);
        parametersSection.setWidthFull();

        this.javascriptToolPlaygroundView = new JavascriptToolPlaygroundView(objectMapper, toolSpecService, options,
                postureCalculator, () -> getCurrentToolParamsAsOpt().orElseGet(List::of),
                () -> this.toolNameField.getValue());
        this.javascriptToolPlaygroundView.setHeightFull();

        VerticalLayout scrollArea = new VerticalLayout(topRow, toolDescriptionField,
                parametersSection, javascriptToolPlaygroundView);
        scrollArea.setPadding(false);
        scrollArea.setSpacing(true);
        scrollArea.setSizeFull();
        scrollArea.getStyle()
                .set("overflow-y", "auto")
                .set("overflow-x", "hidden")
                .set("min-width", "0")
                .set("padding-inline-end", "var(--lumo-space-m)");

        boolean startedAsPublished = toolSpec != null && !toolSpec.draft();

        this.saveDraftButton = new Button("Save Draft", VaadinIcon.PENCIL.create(),
                e -> testAndSaveTool(SaveAction.SAVE_DRAFT, toolNameField, toolDescriptionField));
        this.saveDraftButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        this.saveDraftButton.getElement().setProperty("title",
                "Saves the current code as a draft. Not exposed to MCP. No test required.");
        this.publishButton = new Button("Test & Publish", VaadinIcon.PLAY.create(),
                e -> testAndSaveTool(SaveAction.PUBLISH, toolNameField, toolDescriptionField));
        this.publishButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        this.publishButton.getElement().setProperty("title",
                "Runs the test first, publishes only if it passes (Local Pass).");
        this.updateButton = new Button("Test & Update", VaadinIcon.CHECK_CIRCLE.create(),
                e -> testAndSaveTool(SaveAction.UPDATE, toolNameField, toolDescriptionField));
        this.updateButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        this.updateButton.getElement().setProperty("title",
                "Runs the test first, updates the published tool only if it passes (Local Pass).");

        if (startedAsPublished) {
            this.publishButton.setVisible(false);
            this.updateButton.setVisible(true);
            String originalName = toolSpec.name();
            this.toolNameField.addValueChangeListener(ev -> {
                boolean nameChanged = !Objects.equals(originalName, ev.getValue());
                this.updateButton.setEnabled(!nameChanged);
                this.updateButton.getElement().setProperty("title",
                        nameChanged
                                ? "Name changed — use Save Draft to revert to draft."
                                : "Runs the test first, updates the published tool only if it passes (Local Pass).");
            });
        } else {
            this.publishButton.setVisible(true);
            this.updateButton.setVisible(false);
        }

        Span savedText = new Span(formatSavedText(toolSpec));
        savedText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        boolean isPublished = toolSpec != null && !toolSpec.draft();
        Span localPassBadge = buildLocalPassBadge(isPublished);

        HorizontalLayout leftGroup = new HorizontalLayout(this.saveDraftButton, savedText);
        leftGroup.setPadding(false);
        leftGroup.setSpacing(true);
        leftGroup.setAlignItems(Alignment.CENTER);

        HorizontalLayout rightGroup = new HorizontalLayout(
                localPassBadge, this.publishButton, this.updateButton);
        rightGroup.setPadding(false);
        rightGroup.setSpacing(true);
        rightGroup.setAlignItems(Alignment.CENTER);

        HorizontalLayout bottomMenuLayout = new HorizontalLayout(leftGroup, rightGroup);
        bottomMenuLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        bottomMenuLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        bottomMenuLayout.setPadding(false);
        bottomMenuLayout.setSpacing(true);
        bottomMenuLayout.setWidthFull();
        bottomMenuLayout.setHeight("95px");
        bottomMenuLayout.getStyle().set("padding-inline-end", "var(--lumo-space-m)");

        add(scrollArea, bottomMenuLayout);

        if (Objects.nonNull(toolSpec)) {
            this.toolNameField.setValue(toolSpec.name());
            toolDescriptionField.setValue(toolSpec.description());

            String resolvedCategory = this.categoryCatalog.resolveOrFallback(toolSpec.category()).id();
            this.categoryField.setValue(resolvedCategory);
            this.tagsField.setValue(toolSpec.tags() == null ? Set.of() : toolSpec.tags());
            if (!toolSpec.params().isEmpty()) {
                toolSpec.params().forEach(param -> addParameterForm(paramForms.size() + 1).updateFields(param));
            } else {
                addDefaultParameterForm();
            }
            this.javascriptToolPlaygroundView.updateContents(toolSpec.staticVariables(), toolSpec.code());
            this.javascriptToolPlaygroundView.applySandboxOverrides(toolSpec.sandboxOverrides());
        } else {
            addDefaultParameterForm();
        }
    }

    private void addDefaultParameterForm() {
        addParameterForm(1);
    }

    private void addParameterForm() {
        addParameterForm(paramForms.size() + 1);
    }

    private ToolParameterForm addParameterForm(int index) {
        ToolParamSpec toolParamSpec = new ToolParamSpec("", "", false, JsonSchemaType.STRING, "");
        ToolParameterForm toolParameterForm =
                new ToolParameterForm(toolParamSpec, this::removeParameterForm, this::addParameterForm, index);

        toolParameterForm.setWidth("320px");
        toolParameterForm.setMinWidth("320px");

        paramForms.add(toolParameterForm);
        paramsContainer.add(toolParameterForm);
        updateParameterFormButtons();
        return toolParameterForm;
    }

    private void removeParameterForm(ToolParameterForm form) {
        paramsContainer.remove(form);
        paramForms.remove(form);
        for (int i = 0; i < paramForms.size(); i++) {
            paramForms.get(i).updateIndex(i + 1);
        }
        updateParameterFormButtons();
    }

    private void updateParameterFormButtons() {
        boolean allowDeletion = paramForms.size() > 1;
        for (int i = 0; i < paramForms.size(); i++) {
            ToolParameterForm currentForm = paramForms.get(i);
            boolean isLast = (i == paramForms.size() - 1);
            currentForm.setAddButtonVisible(isLast);
            currentForm.setDeleteButtonVisible(allowDeletion);
        }
    }

    private void testAndSaveTool(SaveAction action, TextField nameField, TextArea descriptionField) {
        String toolName = nameField.getValue();

        if (this.toolNameField.isInvalid()) {
            VaadinUtils.showErrorNotification("Please fix the Tool Name.");
            this.toolNameField.focus();
            return;
        }
        if (Objects.isNull(this.toolSpec) && toolSpecService.getToolSpecAsOpt(toolName).isPresent()) {
            VaadinUtils.showErrorNotification("A tool with this name already exists.");
            this.toolNameField.focus();
            return;
        }

        boolean startedAsPublished = this.toolSpec != null && !this.toolSpec.draft();
        boolean nameChanged = this.toolSpec != null && !Objects.equals(this.toolSpec.name(), toolName);
        boolean forceDraftDueToRename = startedAsPublished && nameChanged;
        boolean targetDraft = (action == SaveAction.SAVE_DRAFT) || forceDraftDueToRename;

        String toolDescription = descriptionField.getValue();
        List<ToolParamSpec> toolParamSpecs;
        if (action == SaveAction.SAVE_DRAFT) {
            toolParamSpecs = getCurrentToolParamsLenient();
        } else {
            Optional<List<ToolParamSpec>> paramsOpt = getCurrentToolParamsAsOpt();
            if (paramsOpt.isEmpty()) return;
            toolParamSpecs = paramsOpt.get();
            if (!this.javascriptToolPlaygroundView.runTest()) {
                VaadinUtils.showErrorNotification("Test run failed. Fix the code and retry.");
                return;
            }
        }

        String toolId = Optional.ofNullable(this.toolSpec).map(ToolSpec::toolId)
                .orElseGet(() -> UUID.randomUUID().toString());

        ToolSpec formSpec = new ToolSpec(toolId, toolName, toolDescription,
                this.javascriptToolPlaygroundView.getStaticVariables(), toolParamSpecs,
                this.javascriptToolPlaygroundView.getCurrentJsCode(), ToolSpec.CodeType.Javascript, null)
                .withCategory(this.categoryField.getValue())
                .withTags(this.tagsField.getValue() == null ? Set.of() : this.tagsField.getValue())
                .withSandboxOverrides(this.javascriptToolPlaygroundView.currentSandboxOverrides())
                .withDraft(targetDraft);
        ToolSpec registeredToolSpec = toolSpecService.update(formSpec);
        String stateLabel = targetDraft ? "saved as Draft" : "published";
        VaadinUtils.showInfoNotification("Tool '" + toolName + "' " + stateLabel + ".");
        this.toolChangeSupport.firePropertyChange(TOOL_CHANGE_EVENT, null, registeredToolSpec);
    }

    private static String formatSavedText(ToolSpec toolSpec) {
        if (toolSpec == null || toolSpec.updateTimestamp() == 0L) {
            return "Not saved yet";
        }
        return "Last saved " + formatRelative(toolSpec.updateTimestamp());
    }

    private static Span buildLocalPassBadge(boolean active) {
        Span badge = new Span("Local Pass");
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "0.05rem 0.45rem")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("letter-spacing", "0.02em")
                .set("vertical-align", "baseline");
        if (active) {
            badge.getStyle()
                    .set("background-color", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
        } else {
            badge.getStyle()
                    .set("background-color", "var(--lumo-contrast-10pct)")
                    .set("color", "var(--lumo-tertiary-text-color)");
        }
        return badge;
    }

    private static String formatRelative(long timestamp) {
        Duration elapsed = Duration.between(Instant.ofEpochMilli(timestamp), Instant.now());
        long seconds = elapsed.getSeconds();
        if (seconds < 60) return "just now";
        long minutes = elapsed.toMinutes();
        if (minutes < 60) return minutes + " min ago";
        long hours = elapsed.toHours();
        if (hours < 24) return hours + " h ago";
        return elapsed.toDays() + " d ago";
    }

    private List<ToolParamSpec> getCurrentToolParamsLenient() {
        List<ToolParamSpec> result = new ArrayList<>();
        for (ToolParameterForm form : paramForms) {
            ToolParamSpec spec = form.getToolParamSpec();
            if (spec != null) result.add(spec);
        }
        return result;
    }

    public Optional<List<ToolParamSpec>> getCurrentToolParamsAsOpt() {
        int size = paramForms.size();
        List<ToolParamSpec> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ToolParamSpec spec = paramForms.get(i).getToolParamSpec();
            if (size == 1 && spec == null) {
                return Optional.of(result);
            }
            if (spec == null) {
                VaadinUtils.showErrorNotification("Parameter #" + (i + 1) + " is invalid");
                return Optional.empty();
            }
            result.add(spec);
        }
        return Optional.of(result);
    }

    public Optional<String> getCurrentToolSpecJsonAsOpt() {
        return getCurrentToolParamsAsOpt().map(toolParamSpecs -> {
            ObjectNode schema = JsonNodeFactory.instance.objectNode();
            schema.put("name", Optional.ofNullable(toolSpec).map(ToolSpec::name).orElse(""));
            schema.put("description", Optional.ofNullable(toolSpec).map(ToolSpec::description).orElse(""));
            schema.putIfAbsent("parameters", this.toolSpecService.toJsonSchema(toolParamSpecs));
            return schema.toPrettyString();
        });
    }

    public ToolSpec copyCurrentToolSpec() {
        return new ToolSpec(null, this.toolNameField.getValue() + "_COPY",
                this.toolDescriptionField.getValue(), this.javascriptToolPlaygroundView.getStaticVariables(),
                this.getCurrentToolParamsAsOpt().orElseGet(List::of),
                this.javascriptToolPlaygroundView.getCurrentJsCode(), ToolSpec.CodeType.Javascript, null)
                .withDraft(true);
    }

}