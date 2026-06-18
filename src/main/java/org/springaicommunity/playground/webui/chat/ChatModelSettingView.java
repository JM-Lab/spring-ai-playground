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
package org.springaicommunity.playground.webui.chat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatProvider;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.PresetKind;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetService;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.webui.JsonEditorWrapper;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.tool.ExposedToolsSelector;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ChatModelSettingView extends VerticalLayout {
    private final TextArea systemPromptTextArea;
    private final ComboBox<Preset> systemPromptPresetComboBox;
    private List<Preset> selectablePresets = List.of();
    // Sorted by category then name so the categorized picker keeps each category's tools adjacent.
    private final List<ToolSpec> builtinTools;
    private final Function<ToolSpec, String> riskLevelFn;
    private final Function<ToolSpec, String> categoryFn;
    private List<String> activePresetTools = List.of();
    private final ComboBox<String> modelComboBox;
    private final IntegerField maxTokensInput;
    private final NumberField temperatureInput;
    private final NumberField topPInput;
    private final NumberField frequencyPenaltyInput;
    private final NumberField presencePenaltyInput;
    private final IntegerField seedInput;
    private final IntegerField memoryWindowInput;
    private final TextField stopInput;
    private final JsonEditorWrapper providerOptionsJsonEditor;
    private final ChatProvider provider;

    public ChatModelSettingView(List<String> models, String systemPrompt, ChatOptions chatOption,
            ChatExtraOptions extraOptions, ChatProvider provider, ChatSystemPromptPresetService presetService,
            OllamaModelDownloadService modelDownloadService, List<ToolSpec> builtinTools,
            Function<ToolSpec, String> riskLevelFn, Function<ToolSpec, String> categoryFn, int defaultMemoryWindow) {
        this.riskLevelFn = riskLevelFn;
        this.categoryFn = categoryFn;
        this.provider = provider;
        this.builtinTools = builtinTools.stream()
                .sorted(Comparator.comparing((ToolSpec spec) -> categoryFn.apply(spec), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ToolSpec::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        setSpacing(false);
        setPadding(false);
        setWidthFull();
        setAlignItems(FlexComponent.Alignment.STRETCH);
        getStyle().set("gap", "var(--lumo-space-m)");

        VerticalLayout modelSection = section("Model - " + provider.displayName());
        String model = chatOption.getModel();
        List<String> modelItems = new ArrayList<>(models);
        modelComboBox = new ComboBox<>();
        modelComboBox.setItems(modelItems);
        if (model != null) {
            modelComboBox.setValue(model);
        }
        modelComboBox.setAllowCustomValue(true);
        // Without this listener a typed tag shows in the field but getValue() keeps the previous selection.
        modelComboBox.addCustomValueSetListener(event -> {
            String customModel = event.getDetail();
            if (customModel == null || customModel.isBlank())
                return;
            if (!modelItems.contains(customModel)) {
                modelItems.add(customModel);
                modelComboBox.getDataProvider().refreshAll();
            }
            modelComboBox.setValue(customModel);
        });
        modelComboBox.setWidthFull();
        if (modelDownloadService.isEnabled()) {
            modelComboBox.setRenderer(new ComponentRenderer<>(name -> {
                HorizontalLayout row = new HorizontalLayout(new Span(name));
                row.setSpacing(true);
                row.setPadding(false);
                row.setAlignItems(FlexComponent.Alignment.CENTER);
                if (!modelDownloadService.isDownloaded(name)) {
                    Icon downloadIcon = VaadinIcon.DOWNLOAD.create();
                    downloadIcon.getStyle().set("width", "var(--lumo-icon-size-s)")
                            .set("height", "var(--lumo-icon-size-s)")
                            .set("color", "var(--lumo-secondary-text-color)");
                    downloadIcon.getElement().setAttribute("title", "Not downloaded - downloads when applied");
                    row.add(downloadIcon);
                }
                return row;
            }));
            modelComboBox.addValueChangeListener(event -> modelComboBox.setHelperText(
                    modelDownloadService.isDownloaded(event.getValue())
                            ? null : "Not downloaded yet - Apply & New Chat will download it first."));
            if (!modelDownloadService.isDownloaded(model))
                modelComboBox.setHelperText("Not downloaded yet - Apply & New Chat will download it first.");
        }
        modelSection.add(modelComboBox);
        add(modelSection);

        VerticalLayout contextSection = section("Context");
        this.memoryWindowInput = boundedIntegerField("Recent messages", 1, null);
        this.memoryWindowInput.setHelperText("How many recent messages the model sees each turn. "
                + "Older ones stay in saved history. Empty uses the default (" + defaultMemoryWindow + ").");
        if (extraOptions != null && extraOptions.memoryWindow() != null)
            this.memoryWindowInput.setValue(extraOptions.memoryWindow());

        Span systemPromptLabel = new Span("System prompt");
        systemPromptLabel.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)").set("margin-top", "var(--lumo-space-s)");
        this.systemPromptTextArea = new TextArea();
        this.systemPromptTextArea.setWidthFull();
        this.systemPromptTextArea.setMinHeight("7em");
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            this.systemPromptTextArea.setValue(systemPrompt);
        }
        this.systemPromptPresetComboBox = new ComboBox<>();
        contextSection.add(this.memoryWindowInput, systemPromptLabel,
                buildSystemPromptPresetRow(presetService), this.systemPromptTextArea);
        add(contextSection);

        VerticalLayout generationSection = section("Generation");
        FormLayout generationForm = new FormLayout();
        generationForm.setWidthFull();
        generationForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("26rem", 2));

        this.temperatureInput = boundedNumberField("Temperature", 0, 1);
        generationForm.add(sliderCell(this.temperatureInput, 0, 1, chatOption.getTemperature(), 0.7));
        this.topPInput = boundedNumberField("Top P", 0, 1);
        generationForm.add(sliderCell(this.topPInput, 0, 1, chatOption.getTopP(), 1.0));
        this.frequencyPenaltyInput = boundedNumberField("Frequency Penalty", -2, 2);
        generationForm.add(sliderCell(this.frequencyPenaltyInput, -2, 2, chatOption.getFrequencyPenalty(), 0.0));
        this.presencePenaltyInput = boundedNumberField("Presence Penalty", -2, 2);
        generationForm.add(sliderCell(this.presencePenaltyInput, -2, 2, chatOption.getPresencePenalty(), 0.0));

        this.maxTokensInput = boundedIntegerField("Max Tokens", 1, null);
        Integer maxTokens = chatOption.getMaxTokens();
        if (maxTokens != null) {
            this.maxTokensInput.setValue(maxTokens);
        }
        this.seedInput = boundedIntegerField("Seed", null, null);
        if (extraOptions != null && extraOptions.seed() != null) this.seedInput.setValue(extraOptions.seed());

        generationForm.add(this.maxTokensInput, this.seedInput);
        generationSection.add(generationForm);
        add(generationSection);

        this.stopInput = new TextField("Stop sequences (comma-separated)");
        this.stopInput.setWidthFull();
        boolean hasStop = extraOptions != null && extraOptions.stop() != null && !extraOptions.stop().isEmpty();
        if (hasStop)
            this.stopInput.setValue(String.join(", ", extraOptions.stop()));
        if (provider.maxStopSequences() != null)
            this.stopInput.setHelperText(provider.displayName() + " allows up to "
                    + provider.maxStopSequences() + " stop sequences.");
        this.stopInput.addValueChangeListener(e -> validateStop());
        validateStop();
        generationSection.add(this.stopInput);

        Span overrideCaption = new Span("Provider options (JSON) - applied last, overrides every field above. "
                + "Example: " + provider.jsonPlaceholder());
        overrideCaption.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
        String existingJson = extraOptions != null ? extraOptions.providerOptionsJson() : null;
        this.providerOptionsJsonEditor = new JsonEditorWrapper();
        this.providerOptionsJsonEditor.setWidthFull();
        this.providerOptionsJsonEditor.setHeight("180px");
        this.providerOptionsJsonEditor.setJson(existingJson != null ? existingJson : "{}");

        VerticalLayout advancedContent = new VerticalLayout(overrideCaption,
                this.providerOptionsJsonEditor);
        advancedContent.setPadding(false);
        advancedContent.setSpacing(false);
        advancedContent.getStyle().set("gap", "var(--lumo-space-s)");
        Details advanced = new Details("Advanced", advancedContent);
        advanced.addThemeVariants(DetailsVariant.FILLED);
        advanced.setWidthFull();
        advanced.setOpened(existingJson != null);
        add(advanced);
    }

    private static VerticalLayout section(String title) {
        Span label = new Span(title);
        label.getStyle().set("font-size", "var(--lumo-font-size-xxs)").set("font-weight", "600")
                .set("text-transform", "uppercase").set("letter-spacing", "0.05em")
                .set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout box = new VerticalLayout(label);
        box.setPadding(false);
        box.setSpacing(false);
        box.setWidthFull();
        box.getStyle().set("gap", "var(--lumo-space-xs)");
        return box;
    }

    private static NumberField boundedNumberField(String label, double min, double max) {
        NumberField field = new NumberField(label);
        field.setMin(min);
        field.setMax(max);
        field.setWidthFull();
        field.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least " + formatBound(min))
                .setMaxErrorMessage("Value cannot exceed " + formatBound(max)));
        return field;
    }

    private static String formatBound(double bound) {
        return bound == Math.rint(bound) ? String.valueOf((long) bound) : String.valueOf(bound);
    }

    private static IntegerField boundedIntegerField(String label, Integer min, Integer max) {
        IntegerField field = new IntegerField(label);
        field.setWidthFull();
        field.setStepButtonsVisible(true);
        IntegerField.IntegerFieldI18n i18n = new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Invalid number format");
        if (min != null) {
            field.setMin(min);
            i18n.setMinErrorMessage("Value must be at least " + min);
        }
        if (max != null) {
            field.setMax(max);
            i18n.setMaxErrorMessage("Value cannot exceed " + max);
        }
        field.setI18n(i18n);
        return field;
    }

    private void validateStop() {
        Integer max = this.provider.maxStopSequences();
        List<String> parsed = parseStop(this.stopInput.getValue());
        int count = parsed == null ? 0 : parsed.size();
        if (max != null && count > max) {
            this.stopInput.setErrorMessage(this.provider.displayName() + " allows at most " + max
                    + " stop sequences.");
            this.stopInput.setInvalid(true);
        } else {
            this.stopInput.setInvalid(false);
        }
    }

    public boolean validate() {
        validateStop();
        return !this.maxTokensInput.isInvalid() && !this.seedInput.isInvalid()
                && !this.memoryWindowInput.isInvalid() && !this.temperatureInput.isInvalid()
                && !this.topPInput.isInvalid() && !this.frequencyPenaltyInput.isInvalid()
                && !this.presencePenaltyInput.isInvalid() && !this.stopInput.isInvalid();
    }

    private static VerticalLayout sliderCell(NumberField field, double min, double max, Double value,
            double fallback) {
        RangeInput slider = new RangeInput();
        slider.setMin(min);
        slider.setMax(max);
        slider.setStep(0.1);
        slider.setWidthFull();
        double initial = value != null ? value : fallback;
        field.setValue(initial);
        slider.setValue(initial);
        boolean[] updating = {false};
        slider.addValueChangeListener(event -> {
            if (updating[0]) return;
            updating[0] = true;
            field.setValue(event.getValue());
            updating[0] = false;
        });
        field.addValueChangeListener(event -> {
            if (updating[0] || event.getValue() == null) return;
            updating[0] = true;
            slider.setValue(event.getValue());
            updating[0] = false;
        });
        VerticalLayout cell = new VerticalLayout(field, slider);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.setWidthFull();
        return cell;
    }

    private HorizontalLayout buildSystemPromptPresetRow(ChatSystemPromptPresetService presetService) {
        this.selectablePresets = selectablePresets(presetService);
        this.systemPromptPresetComboBox.setItems(this.selectablePresets);
        this.systemPromptPresetComboBox.setItemLabelGenerator(Preset::displayName);
        this.systemPromptPresetComboBox.setPlaceholder("Select a preset");
        this.systemPromptPresetComboBox.setClearButtonVisible(true);
        this.systemPromptPresetComboBox.setWidthFull();
        this.systemPromptPresetComboBox.addValueChangeListener(event -> {
            Preset selected = event.getValue();
            this.activePresetTools = selected == null ? List.of() : selected.tools();
            if (selected != null) {
                this.systemPromptTextArea.setValue(selected.prompt());
                this.systemPromptPresetComboBox.setHelperText(presetHelperText(selected));
            } else {
                this.systemPromptPresetComboBox.setHelperText(null);
            }
        });
        String current = this.systemPromptTextArea.getValue();
        this.selectablePresets.stream().filter(preset -> preset.prompt().equals(current))
                .findFirst().ifPresent(this.systemPromptPresetComboBox::setValue);

        Button saveButton = new Button("Save as preset...", event -> openSavePresetDialog(presetService));
        saveButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout row = new HorizontalLayout(this.systemPromptPresetComboBox, saveButton);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.setFlexGrow(1, this.systemPromptPresetComboBox);
        return row;
    }

    // The drawer combo offers only ready-to-use presets (built-in examples + saved); variable templates need the
    // Prompt Library dialog to fill in their {{placeholders}} before they become a usable system prompt.
    private List<Preset> selectablePresets(ChatSystemPromptPresetService presetService) {
        List<Preset> selectable = new ArrayList<>(presetService.builtInExamples());
        selectable.addAll(presetService.userExamples());
        return selectable;
    }

    public void applyPreset(Preset preset) {
        this.systemPromptTextArea.setValue(preset.prompt());
        if (this.selectablePresets.contains(preset)) this.systemPromptPresetComboBox.setValue(preset);
        else this.systemPromptPresetComboBox.clear();
        // Set after the combo ops: an ad-hoc preset (edited in the library) clears the combo, whose
        // listener would otherwise wipe the tools it carries.
        this.activePresetTools = preset.tools();
    }

    public List<String> getSelectedPresetTools() {
        return this.activePresetTools;
    }

    private static String presetHelperText(Preset preset) {
        String description = preset.description() == null ? "" : preset.description();
        if (preset.tools().isEmpty()) return description.isBlank() ? null : description;
        String tools = "Activates tools: " + String.join(", ", preset.tools());
        return description.isBlank() ? tools : description + " · " + tools;
    }

    private void openSavePresetDialog(ChatSystemPromptPresetService presetService) {
        String prompt = this.systemPromptTextArea.getValue();
        if (prompt == null || prompt.isBlank()) {
            VaadinUtils.showErrorNotification("Nothing to save - the system prompt is empty.");
            return;
        }
        Dialog dialog = VaadinUtils.headerDialog("Save as preset");
        TextField nameField = new TextField("Preset name");
        nameField.setWidthFull();
        MultiSelectComboBox<ToolSpec> toolsPicker = ExposedToolsSelector.newCategorizedSelector(
                "Tools to activate (optional)",
                "Built-in tools selected for the chat when this preset is applied.",
                this.riskLevelFn, this.categoryFn);
        toolsPicker.setItems(this.builtinTools);
        if (!this.activePresetTools.isEmpty()) {
            Set<String> wanted = Set.copyOf(this.activePresetTools);
            toolsPicker.setValue(this.builtinTools.stream()
                    .filter(spec -> wanted.contains(spec.name())).collect(Collectors.toSet()));
        }
        Button save = new Button("Save", event -> {
            String name = nameField.getValue();
            if (name == null || name.isBlank()) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            List<String> tools = this.builtinTools.stream()
                    .filter(toolsPicker.getSelectedItems()::contains).map(ToolSpec::name).toList();
            Preset saved = presetService.save(name, prompt, PresetKind.EXAMPLE, tools);
            this.systemPromptPresetComboBox.setItems(selectablePresets(presetService));
            this.systemPromptPresetComboBox.setValue(saved);
            dialog.close();
            VaadinUtils.showInfoNotification("Saved preset \"" + saved.displayName() + "\"");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(nameField, toolsPicker);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    public String getSystemPromptTextArea() {
        return this.systemPromptTextArea.getValue();
    }

    public void refreshModelItems() {
        this.modelComboBox.getDataProvider().refreshAll();
        this.modelComboBox.setHelperText(null);
    }

    public ChatOptions getChatOptions() {
        return ChatOptions.builder()
                .model(modelComboBox.getValue())
                .maxTokens(maxTokensInput.getValue())
                .temperature(temperatureInput.getValue())
                .topP(topPInput.getValue())
                .frequencyPenalty(frequencyPenaltyInput.getValue())
                .presencePenalty(presencePenaltyInput.getValue())
                .build();
    }

    public ChatExtraOptions getChatExtraOptions() {
        return new ChatExtraOptions(this.seedInput.getValue(), parseStop(this.stopInput.getValue()),
                jsonOverrideOrNull(this.providerOptionsJsonEditor.getJsonSync()), this.memoryWindowInput.getValue());
    }

    private static List<String> parseStop(String value) {
        if (value == null || value.isBlank()) return null;
        return Arrays.stream(value.split(",")).map(String::trim).filter(token -> !token.isEmpty()).toList();
    }

    private static String jsonOverrideOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        return trimmed.isEmpty() || trimmed.equals("{}") ? null : trimmed;
    }
}

