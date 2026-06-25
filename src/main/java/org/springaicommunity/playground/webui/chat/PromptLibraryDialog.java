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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.Preset;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetCatalog.PresetKind;
import org.springaicommunity.playground.service.chat.ChatSystemPromptPresetService;
import org.springaicommunity.playground.service.chat.ChatSystemPromptTemplateRenderer;
import org.springaicommunity.playground.service.chat.ChatSystemPromptTemplateRenderer.VariableSpec;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.tool.ExposedToolsSelector;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PromptLibraryDialog extends Dialog {

    public enum ToolReadiness { READY, NOT_EXPOSED, NEEDS_SETUP, NOT_ENABLED }

    private static final Pattern TOKEN = Pattern.compile("\\{\\{[^}]*\\}\\}");

    private static final Set<String> FS_TOOLS = Set.of("readTextFile", "listDir", "statFile", "lineCount",
            "sliceFile", "sortFile", "grepFile", "findFiles", "cutFileFields", "writeTextFile");

    private final ChatSystemPromptPresetService presetService;
    private final ChatSystemPromptTemplateRenderer templateRenderer;
    private final Consumer<Preset> onApply;
    private final Function<String, ToolReadiness> toolReadiness;
    private final List<ToolSpec> builtinTools;
    private final Function<ToolSpec, String> riskLevelFn;
    private final Function<ToolSpec, String> categoryFn;

    private final TextField searchField = new TextField();
    private final VerticalLayout listPanel = new VerticalLayout();
    private final VerticalLayout detailPanel = new VerticalLayout();
    private String selectedId;

    public PromptLibraryDialog(ChatSystemPromptPresetService presetService,
            ChatSystemPromptTemplateRenderer templateRenderer, Consumer<Preset> onApply,
            Function<String, ToolReadiness> toolReadiness, List<ToolSpec> builtinTools,
            Function<ToolSpec, String> riskLevelFn, Function<ToolSpec, String> categoryFn) {
        this.presetService = presetService;
        this.templateRenderer = templateRenderer;
        this.onApply = onApply;
        this.toolReadiness = toolReadiness;
        this.riskLevelFn = riskLevelFn;
        this.categoryFn = categoryFn;
        this.builtinTools = builtinTools.stream()
                .sorted(Comparator.comparing((ToolSpec spec) -> categoryFn.apply(spec), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ToolSpec::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        setHeaderTitle("Prompt Library");
        setWidth("78vw");
        setMaxWidth("1280px");
        setHeight("82vh");
        setResizable(true);
        setDraggable(true);

        this.searchField.setPlaceholder("Search");
        this.searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        this.searchField.setClearButtonVisible(true);
        this.searchField.setWidth("200px");
        this.searchField.setValueChangeMode(ValueChangeMode.EAGER);
        this.searchField.addValueChangeListener(event -> buildList());

        Button newTemplate = new Button("New template", VaadinIcon.PLUS.create(),
                event -> showTemplateEditor(null));
        Button close = new Button(VaadinIcon.CLOSE_SMALL.create(), event -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        getHeader().add(this.searchField, newTemplate, close);

        this.listPanel.setWidth("270px");
        this.listPanel.setHeightFull();
        this.listPanel.setPadding(false);
        this.listPanel.setSpacing(false);
        this.listPanel.getStyle().set("border-right", "1px solid var(--lumo-contrast-10pct)")
                .set("overflow", "auto").set("flex", "0 0 auto")
                .set("padding-right", "var(--lumo-space-s)");

        this.detailPanel.setSizeFull();
        this.detailPanel.getStyle().set("overflow", "auto");

        HorizontalLayout body = new HorizontalLayout(this.listPanel, this.detailPanel);
        body.setSizeFull();
        body.setSpacing(false);
        body.setPadding(false);
        body.setFlexGrow(1, this.detailPanel);
        add(body);

        buildList();
        showPlaceholder();
    }

    private void buildList() {
        this.listPanel.removeAll();
        addCategory("Templates", "fill variables", filtered(this.presetService.variableTemplates()));
        addCategory("Presets", "ready to use", filtered(this.presetService.builtInExamples()));
        addCategory("My presets", "saved by you", filtered(this.presetService.userExamples()));
    }

    private List<Preset> filtered(List<Preset> presets) {
        String query = this.searchField.getValue();
        if (!StringUtils.hasText(query)) return presets;
        String lower = query.toLowerCase(Locale.ROOT);
        return presets.stream().filter(preset -> preset.displayName().toLowerCase(Locale.ROOT).contains(lower)
                || preset.description().toLowerCase(Locale.ROOT).contains(lower)).toList();
    }

    private void addCategory(String label, String hint, List<Preset> presets) {
        if (presets.isEmpty()) return;

        Span title = new Span(label);
        title.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)");
        Span count = new Span(String.valueOf(presets.size()));
        count.getStyle().set("font-size", "var(--lumo-font-size-xxs)")
                .set("background", "var(--lumo-contrast-10pct)").set("border-radius", "999px")
                .set("padding", "0 0.6em").set("color", "var(--lumo-secondary-text-color)");
        Span hintSpan = new Span(hint);
        hintSpan.getStyle().set("font-size", "var(--lumo-font-size-xxs)")
                .set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout summary = new HorizontalLayout(title, count, hintSpan);
        summary.setAlignItems(FlexComponent.Alignment.CENTER);
        summary.setSpacing(false);
        summary.getStyle().set("gap", "var(--lumo-space-s)");

        VerticalLayout items = new VerticalLayout();
        items.setPadding(false);
        items.setSpacing(false);
        for (Preset preset : presets) items.add(listItem(preset));

        Details details = new Details(summary, items);
        details.setOpened(true);
        details.setWidthFull();
        details.addThemeVariants(DetailsVariant.SMALL);
        this.listPanel.add(details);
    }

    private Component listItem(Preset preset) {
        Span name = new Span(preset.displayName());
        name.getStyle().set("overflow", "hidden").set("text-overflow", "ellipsis").set("white-space", "nowrap");
        HorizontalLayout item = new HorizontalLayout(name);
        item.setWidthFull();
        item.setAlignItems(FlexComponent.Alignment.CENTER);
        item.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        item.getStyle().set("padding", "0.35em 0.7em").set("cursor", "pointer")
                .set("font-size", "var(--lumo-font-size-s)").set("box-sizing", "border-box")
                .set("border-radius", "var(--lumo-border-radius-m)");
        if (preset.isTemplate()) item.add(badge("{{ }}"));
        if (StringUtils.hasText(preset.description()))
            item.getElement().setAttribute("title", preset.description());
        if (preset.id().equals(this.selectedId))
            item.getStyle().set("background", "var(--lumo-primary-color-10pct)");
        item.addClickListener(event -> {
            this.selectedId = preset.id();
            buildList();
            showDetail(preset);
        });
        return item;
    }

    private void showPlaceholder() {
        this.detailPanel.removeAll();
        Span hint = new Span("Select a prompt to preview, fill a template's variables, or create a new template.");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        this.detailPanel.add(hint);
    }

    private void showDetail(Preset preset) {
        this.detailPanel.removeAll();
        if (preset.isTemplate()) showTemplateDetail(preset);
        else showPresetDetail(preset);
    }

    private void showTemplateDetail(Preset preset) {
        HorizontalLayout title = titleRow(preset, badge("TEMPLATE"));
        if (isUserOwned(preset)) {
            Button edit = new Button("Edit", event -> showTemplateEditor(preset));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            title.add(edit, deleteButton(preset));
        }
        this.detailPanel.add(title);
        addDescription(preset);
        addRequiredTools(preset);
        this.detailPanel.add(highlightedTemplateText(preset.prompt()), sectionLabel("Fill in the variables"));

        Map<String, Supplier<String>> values = new LinkedHashMap<>();
        TextArea preview = new TextArea("Final prompt (preview)");
        preview.setReadOnly(true);
        preview.setWidthFull();
        preview.setMaxHeight("240px");
        preview.setHelperText("Updates live - unfilled variables fall back to their defaults");
        Runnable updatePreview =
                () -> preview.setValue(this.templateRenderer.render(preset.prompt(), resolve(values)));

        FormLayout form = new FormLayout();
        form.setWidthFull();
        form.setResponsiveSteps(new ResponsiveStep("0", 1), new ResponsiveStep("560px", 2));
        for (VariableSpec spec : this.templateRenderer.variableSpecs(preset.prompt())) {
            Component field = variableField(spec, values, updatePreview);
            form.add(field);
            if (spec.type() == ChatSystemPromptTemplateRenderer.VariableType.MULTILINE
                    || spec.type() == ChatSystemPromptTemplateRenderer.VariableType.LIST)
                form.setColspan(field, 2);
        }
        MultiSelectComboBox<ToolSpec> toolsPicker = newToolsPicker(preset.tools());
        this.detailPanel.add(form, preview, toolsPicker);
        updatePreview.run();

        Button save = new Button("Save as preset",
                event -> saveFilledTemplate(preset, values, orderedSelection(toolsPicker), false));
        Button saveApply = new Button("Save as preset & apply",
                event -> saveFilledTemplate(preset, values, orderedSelection(toolsPicker), true));
        saveApply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        this.detailPanel.add(buttonRow(save, saveApply));
    }

    private Component highlightedTemplateText(String prompt) {
        Div box = new Div();
        box.getStyle().set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)").set("padding", "0.7em 0.9em")
                .set("font-size", "var(--lumo-font-size-s)").set("white-space", "pre-wrap")
                .set("line-height", "1.55").set("width", "100%").set("box-sizing", "border-box");
        Matcher matcher = TOKEN.matcher(prompt);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) box.add(new Span(prompt.substring(last, matcher.start())));
            Span token = new Span(matcher.group());
            token.getStyle().set("background", "var(--lumo-primary-color-10pct)")
                    .set("color", "var(--lumo-primary-text-color)")
                    .set("border-radius", "var(--lumo-border-radius-s)").set("padding", "0 0.25em")
                    .set("font-weight", "600");
            box.add(token);
            last = matcher.end();
        }
        if (last < prompt.length()) box.add(new Span(prompt.substring(last)));
        return box;
    }

    private Map<String, String> resolve(Map<String, Supplier<String>> values) {
        Map<String, String> resolved = new LinkedHashMap<>();
        values.forEach((name, getter) -> resolved.put(name, getter.get()));
        return resolved;
    }

    private Component variableField(VariableSpec spec, Map<String, Supplier<String>> values, Runnable onChange) {
        return switch (spec.type()) {
            case MULTILINE -> {
                TextArea area = new TextArea(spec.name());
                area.setWidthFull();
                area.setMinHeight("70px");
                if (StringUtils.hasText(spec.defaultValue())) area.setValue(spec.defaultValue());
                area.setValueChangeMode(ValueChangeMode.EAGER);
                area.addValueChangeListener(event -> onChange.run());
                values.put(spec.name(), area::getValue);
                yield area;
            }
            case NUMBER -> {
                NumberField number = new NumberField(spec.name());
                number.setWidthFull();
                if (spec.min() != null) number.setMin(spec.min());
                if (spec.max() != null) number.setMax(spec.max());
                if (spec.min() != null || spec.max() != null)
                    number.setHelperText(numberText(spec.min()) + " - " + numberText(spec.max()));
                if (StringUtils.hasText(spec.defaultValue())) {
                    try {
                        number.setValue(Double.valueOf(spec.defaultValue()));
                    } catch (NumberFormatException e) {
                        number.clear();
                    }
                }
                number.setValueChangeMode(ValueChangeMode.EAGER);
                number.addValueChangeListener(event -> onChange.run());
                values.put(spec.name(), () -> numberText(number.getValue()));
                yield number;
            }
            case SELECT -> {
                Select<String> select = new Select<>();
                select.setLabel(spec.name());
                select.setWidthFull();
                select.setItems(spec.options());
                select.setEmptySelectionAllowed(true);
                select.setPlaceholder("Choose...");
                if (StringUtils.hasText(spec.defaultValue()) && spec.options().contains(spec.defaultValue()))
                    select.setValue(spec.defaultValue());
                select.addValueChangeListener(event -> onChange.run());
                values.put(spec.name(), () -> select.getValue() == null ? "" : select.getValue());
                yield select;
            }
            case LIST -> listField(spec, values, onChange);
            default -> {
                TextField text = new TextField(spec.name());
                text.setWidthFull();
                if (StringUtils.hasText(spec.defaultValue())) text.setValue(spec.defaultValue());
                text.setValueChangeMode(ValueChangeMode.EAGER);
                text.addValueChangeListener(event -> onChange.run());
                values.put(spec.name(), text::getValue);
                yield text;
            }
        };
    }

    private Component listField(VariableSpec spec, Map<String, Supplier<String>> values, Runnable onChange) {
        if (spec.options().isEmpty()) {
            TextField text = new TextField(spec.name());
            text.setWidthFull();
            text.setPlaceholder("value1, value2");
            text.setHelperText(spec.limit() == null ? "Comma-separated"
                    : "Comma-separated, up to " + spec.limit());
            if (StringUtils.hasText(spec.defaultValue())) text.setValue(spec.defaultValue());
            text.setValueChangeMode(ValueChangeMode.EAGER);
            text.addValueChangeListener(event -> {
                if (spec.limit() != null) {
                    long count = Arrays.stream(event.getValue().split(",")).filter(StringUtils::hasText).count();
                    text.setInvalid(count > spec.limit());
                    text.setErrorMessage("At most " + spec.limit() + " items");
                }
                onChange.run();
            });
            values.put(spec.name(), text::getValue);
            return text;
        }
        MultiSelectComboBox<String> box = new MultiSelectComboBox<>(spec.name());
        box.setWidthFull();
        box.setItems(spec.options());
        if (spec.limit() != null) box.setHelperText("Select up to " + spec.limit());
        if (StringUtils.hasText(spec.defaultValue())) {
            List<String> defaults = Arrays.stream(spec.defaultValue().split(",")).map(String::trim)
                    .filter(spec.options()::contains).toList();
            box.select(defaults);
        }
        box.addValueChangeListener(event -> {
            if (spec.limit() != null) {
                box.setInvalid(event.getValue().size() > spec.limit());
                box.setErrorMessage("Select at most " + spec.limit());
            }
            onChange.run();
        });
        values.put(spec.name(), () -> spec.options().stream()
                .filter(box.getSelectedItems()::contains).collect(Collectors.joining(", ")));
        return box;
    }

    private void saveFilledTemplate(Preset template, Map<String, Supplier<String>> values, List<String> tools,
            boolean apply) {
        Map<String, String> resolved = resolve(values);
        String rendered = this.templateRenderer.render(template.prompt(), resolved);
        Preset saved = this.presetService.save(presetName(template, resolved), rendered,
                PresetKind.EXAMPLE, tools);
        if (apply) {
            applyAndClose(saved);
        } else {
            this.selectedId = saved.id();
            buildList();
            showDetail(saved);
            VaadinUtils.showInfoNotification("Saved preset \"" + saved.displayName() + "\"");
        }
    }

    private String presetName(Preset template, Map<String, String> resolved) {
        String summary = resolved.values().stream()
                .filter(StringUtils::hasText).filter(value -> !value.contains("\n"))
                .collect(Collectors.joining(", "));
        if (summary.length() > 50) summary = summary.substring(0, 50) + "...";
        return summary.isBlank() ? template.displayName() : template.displayName() + " (" + summary + ")";
    }

    private void showPresetDetail(Preset preset) {
        HorizontalLayout title = titleRow(preset, null);
        if (isUserOwned(preset)) title.add(deleteButton(preset));
        this.detailPanel.add(title);
        addDescription(preset);
        addRequiredTools(preset);

        TextArea prompt = new TextArea("System prompt (editable)");
        prompt.setValue(preset.prompt());
        prompt.setWidthFull();
        prompt.setMinHeight("300px");
        prompt.setHelperText("Tweak freely - Save as preset keeps your copy, Apply uses the text as-is");
        this.detailPanel.add(prompt);

        Button save = new Button("Save as preset",
                event -> openSaveAsPresetDialog(preset.displayName(), prompt.getValue(), preset.tools()));
        Button apply = new Button("Apply to chat", event -> applyAndClose(
                new Preset(preset.id(), preset.displayName(), preset.description(), prompt.getValue(),
                        PresetKind.EXAMPLE, preset.tools(), preset.dynamicTools())));
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        this.detailPanel.add(buttonRow(save, apply));
    }

    private void openSaveAsPresetDialog(String suggestedName, String prompt, List<String> initialTools) {
        if (!StringUtils.hasText(prompt)) {
            VaadinUtils.showErrorNotification("Nothing to save - the prompt is empty.");
            return;
        }
        Dialog dialog = VaadinUtils.headerDialog("Save as preset");
        TextField nameField = new TextField("Preset name");
        nameField.setWidthFull();
        nameField.setValue(suggestedName == null ? "" : suggestedName);
        MultiSelectComboBox<ToolSpec> toolsPicker = newToolsPicker(initialTools);
        Checkbox dynamicTools = newDynamicToolsCheckbox(toolsPicker);
        Button save = new Button("Save", event -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            Preset saved = this.presetService.save(nameField.getValue(), prompt, PresetKind.EXAMPLE,
                    dynamicTools.getValue() ? List.of() : orderedSelection(toolsPicker), dynamicTools.getValue());
            dialog.close();
            this.selectedId = saved.id();
            buildList();
            showDetail(saved);
            VaadinUtils.showInfoNotification("Saved preset \"" + saved.displayName() + "\"");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.add(nameField, dynamicTools, toolsPicker);
        dialog.getFooter().add(cancel, save);
        dialog.open();
    }

    private MultiSelectComboBox<ToolSpec> newToolsPicker(List<String> initialTools) {
        MultiSelectComboBox<ToolSpec> picker = ExposedToolsSelector.newCategorizedSelector(
                "Tools to activate (optional)",
                "Built-in tools selected for the chat when this preset is applied.",
                this.riskLevelFn, this.categoryFn);
        picker.setItems(this.builtinTools);
        if (initialTools != null && !initialTools.isEmpty()) {
            Set<String> wanted = Set.copyOf(initialTools);
            picker.setValue(this.builtinTools.stream()
                    .filter(spec -> wanted.contains(spec.name())).collect(Collectors.toSet()));
        }
        return picker;
    }

    private List<String> orderedSelection(MultiSelectComboBox<ToolSpec> picker) {
        Set<ToolSpec> selected = picker.getSelectedItems();
        return this.builtinTools.stream().filter(selected::contains).map(ToolSpec::name).toList();
    }

    private Checkbox newDynamicToolsCheckbox(MultiSelectComboBox<ToolSpec> toolsPicker) {
        Checkbox dynamicTools = new Checkbox("Use dynamic tool discovery");
        dynamicTools.setTooltipText(
                "The model searches all Local-Passed tools on demand instead of activating the ones picked below.");
        dynamicTools.addValueChangeListener(event -> toolsPicker.setEnabled(!dynamicTools.getValue()));
        return dynamicTools;
    }

    private void showTemplateEditor(Preset existing) {
        this.detailPanel.removeAll();
        Span title = new Span(existing == null ? "New variable template" : "Edit template");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600");

        TextField nameField = new TextField("Name");
        nameField.setWidth("360px");
        if (existing != null) nameField.setValue(existing.displayName());

        TextArea promptArea = new TextArea("Prompt - wrap variables in {{ }}");
        promptArea.setWidthFull();
        promptArea.setMinHeight("240px");
        promptArea.setValueChangeMode(ValueChangeMode.EAGER);
        promptArea.setHelperText(
                "Types: {{name}}, {{name:multiline}}, {{name:number(min,max)}}, {{name:select(a,b)}},"
                        + " {{name:list(a,b, max=3)}} - append =default before }}");
        if (existing != null) promptArea.setValue(existing.prompt());

        FlexLayout tags = new FlexLayout();
        tags.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        tags.getStyle().set("gap", "var(--lumo-space-xs)");
        Runnable updateTags = () -> {
            tags.removeAll();
            for (VariableSpec spec : this.templateRenderer.variableSpecs(promptArea.getValue()))
                tags.add(badge(spec.name() + " : " + spec.type().name().toLowerCase(Locale.ROOT)));
        };
        promptArea.addValueChangeListener(event -> updateTags.run());
        updateTags.run();

        Button addVariable = new Button("Add variable", VaadinIcon.PLUS_CIRCLE_O.create(),
                event -> openAddVariableDialog(promptArea));
        addVariable.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        MultiSelectComboBox<ToolSpec> toolsPicker =
                newToolsPicker(existing == null ? List.of() : existing.tools());
        Checkbox dynamicTools = newDynamicToolsCheckbox(toolsPicker);
        if (existing != null) dynamicTools.setValue(existing.dynamicTools());

        Button cancel = new Button("Cancel", event -> {
            if (existing == null) showPlaceholder();
            else showDetail(existing);
        });
        Button save = new Button("Save template", event -> {
            if (!StringUtils.hasText(nameField.getValue())) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Name is required");
                return;
            }
            if (!StringUtils.hasText(promptArea.getValue())) {
                promptArea.setInvalid(true);
                promptArea.setErrorMessage("Prompt is required");
                return;
            }
            Preset saved = this.presetService.save(nameField.getValue(), promptArea.getValue(), PresetKind.TEMPLATE,
                    dynamicTools.getValue() ? List.of() : orderedSelection(toolsPicker), dynamicTools.getValue());
            if (existing != null && isUserOwned(existing) && !existing.id().equals(saved.id()))
                this.presetService.delete(existing.id());
            this.selectedId = saved.id();
            buildList();
            showDetail(saved);
            VaadinUtils.showInfoNotification("Saved template \"" + saved.displayName() + "\"");
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        this.detailPanel.add(title, nameField, promptArea,
                new HorizontalLayout(sectionLabel("Detected variables"), addVariable), tags,
                dynamicTools, toolsPicker, buttonRow(cancel, save));
    }

    private void openAddVariableDialog(TextArea promptArea) {
        Dialog dialog = VaadinUtils.headerDialog("Add variable");
        TextField nameField = new TextField("Variable name");
        Select<String> typeSelect = new Select<>();
        typeSelect.setLabel("Type");
        typeSelect.setItems("text", "multiline", "number", "select", "list");
        typeSelect.setValue("text");
        TextField optionsField = new TextField("Options (comma-separated)");
        optionsField.setVisible(false);
        NumberField minField = new NumberField("Min");
        NumberField maxField = new NumberField("Max");
        minField.setVisible(false);
        maxField.setVisible(false);
        IntegerField limitField = new IntegerField("Max items");
        limitField.setVisible(false);
        TextField defaultField = new TextField("Default value");
        typeSelect.addValueChangeListener(event -> {
            String type = event.getValue();
            optionsField.setVisible("select".equals(type) || "list".equals(type));
            minField.setVisible("number".equals(type));
            maxField.setVisible("number".equals(type));
            limitField.setVisible("list".equals(type));
        });

        Button insert = new Button("Insert", event -> {
            String name = nameField.getValue();
            if (!StringUtils.hasText(name) || !name.matches("\\w+")) {
                nameField.setInvalid(true);
                nameField.setErrorMessage("Letters, digits, and _ only");
                return;
            }
            String token = variableToken(name, typeSelect.getValue(), optionsField.getValue(),
                    minField.getValue(), maxField.getValue(), limitField.getValue(), defaultField.getValue());
            String current = promptArea.getValue();
            promptArea.setValue(current.isBlank() ? token : current + " " + token);
            dialog.close();
        });
        insert.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel", event -> dialog.close());

        FormLayout form = new FormLayout(nameField, typeSelect, minField, maxField, limitField,
                optionsField, defaultField);
        form.setResponsiveSteps(new ResponsiveStep("0", 1), new ResponsiveStep("400px", 2));
        form.setColspan(optionsField, 2);
        form.setColspan(defaultField, 2);
        form.setWidth("440px");
        dialog.add(form);
        dialog.getFooter().add(cancel, insert);
        dialog.open();
    }

    private String variableToken(String name, String type, String options, Double min, Double max, Integer limit,
            String defaultValue) {
        StringBuilder token = new StringBuilder("{{").append(name);
        switch (type) {
            case "multiline" -> token.append(":multiline");
            case "number" -> {
                token.append(":number");
                if (min != null || max != null)
                    token.append('(').append(numberText(min)).append(',').append(numberText(max)).append(')');
            }
            case "select" -> token.append(":select(").append(options == null ? "" : options.trim()).append(')');
            case "list" -> {
                token.append(":list");
                List<String> parts = new ArrayList<>();
                if (StringUtils.hasText(options)) parts.add(options.trim());
                if (limit != null) parts.add("max=" + limit);
                if (!parts.isEmpty()) token.append('(').append(String.join(", ", parts)).append(')');
            }
            default -> { }
        }
        if (StringUtils.hasText(defaultValue)) token.append('=').append(defaultValue.trim());
        return token.append("}}").toString();
    }

    private HorizontalLayout titleRow(Preset preset, Span kindBadge) {
        Span name = new Span(preset.displayName());
        name.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600");
        HorizontalLayout row = new HorizontalLayout(name);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        if (kindBadge != null) row.add(kindBadge);
        row.setFlexGrow(1, name);
        return row;
    }

    private void addDescription(Preset preset) {
        if (!StringUtils.hasText(preset.description())) return;
        Span description = new Span(preset.description());
        description.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        this.detailPanel.add(description);
    }

    private void addRequiredTools(Preset preset) {
        if (preset.tools().isEmpty()) return;
        FlexLayout chips = new FlexLayout();
        chips.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        chips.getStyle().set("gap", "var(--lumo-space-xs)");
        boolean allReady = true;
        for (String tool : preset.tools()) {
            ToolReadiness readiness = this.toolReadiness.apply(tool);
            if (readiness != ToolReadiness.READY) allReady = false;
            chips.add(toolChip(tool, readiness));
        }
        this.detailPanel.add(sectionLabel("Required tools"), chips);
        if (!allReady) {
            Span hint = new Span("Some tools are not ready - activate, expose, or add their API keys in Tool Studio.");
            hint.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-xs)");
            Button open = new Button("Open Tool Studio", VaadinIcon.EXTERNAL_LINK.create(), event -> {
                close();
                UI.getCurrent().navigate("tool-studio");
            });
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            HorizontalLayout row = new HorizontalLayout(hint, open);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            this.detailPanel.add(row);
        }
        if (preset.tools().stream().anyMatch(FS_TOOLS::contains)) {
            Span fsNote = new Span("Filesystem tools read anywhere under your home directory and write only"
                    + " inside the working directory (default ~/spring-ai-playground/workspace). Call"
                    + " listAllowedDirectories to see the exact roots, or change the working directory via"
                    + " spring.ai.playground.tool-studio.fs.base-path.");
            fsNote.getStyle().set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-xs)");
            this.detailPanel.add(fsNote);
        }
    }

    private Span toolChip(String tool, ToolReadiness readiness) {
        Span chip = new Span(switch (readiness) {
            case READY -> tool;
            case NOT_EXPOSED -> tool + " (not exposed)";
            case NEEDS_SETUP -> tool + " (key)";
            case NOT_ENABLED -> tool + " (off)";
        });
        chip.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("border-radius", "999px").set("padding", "0.1em 0.7em").set("white-space", "nowrap");
        switch (readiness) {
            case READY -> chip.getStyle().set("background", "var(--lumo-success-color-10pct)")
                    .set("color", "var(--lumo-success-text-color)");
            case NOT_EXPOSED -> chip.getStyle().set("background", "var(--lumo-warning-color-10pct)")
                    .set("color", "var(--lumo-warning-text-color)")
                    .set("border", "1px dashed var(--lumo-warning-color)");
            case NEEDS_SETUP -> chip.getStyle().set("background", "var(--lumo-warning-color-10pct)")
                    .set("color", "var(--lumo-warning-text-color)");
            case NOT_ENABLED -> chip.getStyle().set("background", "var(--lumo-contrast-5pct)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("border", "1px dashed var(--lumo-contrast-30pct)");
        }
        chip.getElement().setAttribute("title", switch (readiness) {
            case READY -> "Enabled and ready";
            case NOT_EXPOSED -> "Active but un-checked in MCP exposure - Apply & New Chat will skip it until it is"
                    + " exposed in Tool Studio";
            case NEEDS_SETUP -> "Loaded but missing an API key (env var) - configure it in Tool Studio";
            case NOT_ENABLED -> "Not active - enable it in Tool Studio";
        });
        return chip;
    }

    private Button deleteButton(Preset preset) {
        Button delete = new Button("Delete", event -> {
            this.presetService.delete(preset.id());
            this.selectedId = null;
            buildList();
            showPlaceholder();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        return delete;
    }

    private boolean isUserOwned(Preset preset) {
        return this.presetService.userPresets().stream().anyMatch(p -> p.id().equals(preset.id()));
    }

    private Span badge(String text) {
        Span badge = new Span(text);
        badge.getStyle().set("font-size", "var(--lumo-font-size-xxs)")
                .set("border", "1px solid var(--lumo-primary-color)").set("color", "var(--lumo-primary-color)")
                .set("border-radius", "var(--lumo-border-radius-s)").set("padding", "0 0.4em")
                .set("white-space", "nowrap");
        return badge;
    }

    private void applyAndClose(Preset preset) {
        this.onApply.accept(preset);
        close();
    }

    private Span sectionLabel(String text) {
        Span label = new Span(text);
        label.getStyle().set("font-size", "var(--lumo-font-size-xxs)").set("font-weight", "600")
                .set("text-transform", "uppercase").set("letter-spacing", "0.05em")
                .set("color", "var(--lumo-secondary-text-color)");
        return label;
    }

    private HorizontalLayout buttonRow(Button... buttons) {
        HorizontalLayout row = new HorizontalLayout(buttons);
        row.setWidthFull();
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return row;
    }

    private static String numberText(Double value) {
        if (value == null) return "";
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }
}
