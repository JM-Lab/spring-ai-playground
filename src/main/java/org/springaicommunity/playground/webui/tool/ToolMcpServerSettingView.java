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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import org.springaicommunity.playground.service.tool.DefaultToolPresetCatalog;
import org.springaicommunity.playground.service.tool.DefaultToolPresetCatalog.Preset;
import org.springaicommunity.playground.service.tool.DefaultToolsPreference;
import org.springaicommunity.playground.service.tool.DefaultToolsPreference.Rule;
import org.springaicommunity.playground.service.tool.DefaultToolsPreferenceResolver;
import org.springaicommunity.playground.service.tool.ToolCategoryCatalog;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ToolMcpServerSettingView extends VerticalLayout {

    private static final String CUSTOM_PRESET_LABEL = "Custom (use rules below)";
    private static final int CHIPS_VISIBLE = 10;

    private final ToolSpecPersistenceService persistenceService;
    private final DefaultToolPresetCatalog presetCatalog;
    private final DefaultToolsPreferenceResolver resolver;
    private final ToolCategoryCatalog categoryCatalog;

    private final RadioButtonGroup<String> presetGroup = new RadioButtonGroup<>();
    private final RadioButtonGroup<String> customPresetGroup = new RadioButtonGroup<>();
    private final MultiSelectComboBox<String> includeTags = new MultiSelectComboBox<>("Include by tag");
    private final MultiSelectComboBox<String> includeCategories = new MultiSelectComboBox<>("Include by category");
    private final MultiSelectComboBox<String> includeNames = new MultiSelectComboBox<>("Include by name");
    private final MultiSelectComboBox<String> excludeTags = new MultiSelectComboBox<>("Exclude by tag");
    private final MultiSelectComboBox<String> excludeNames = new MultiSelectComboBox<>("Exclude by name");
    private final Checkbox autoAddCheckbox = new Checkbox("Enable Auto-Add Tools");
    private final MultiSelectComboBox<ToolSpec> toolSelector = new MultiSelectComboBox<>("Registered Tools");
    private final Span summary = new Span();
    private final Div chipsContainer = new Div();

    public ToolMcpServerSettingView(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting,
            ToolSpecPersistenceService persistenceService, DefaultToolPresetCatalog presetCatalog,
            DefaultToolsPreferenceResolver resolver, ToolCategoryCatalog categoryCatalog) {
        this.persistenceService = persistenceService;
        this.presetCatalog = presetCatalog;
        this.resolver = resolver;
        this.categoryCatalog = categoryCatalog;

        setWidthFull();
        setPadding(false);
        setSpacing(true);

        add(buildSummarySection());
        add(buildDivider());
        add(buildRegistrationSection());
        add(buildDivider());
        add(buildDefaultToolsSection());

        update(toolSpecs, toolMcpServerSetting);
    }

    private VerticalLayout buildDefaultToolsSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-s)");

        Span header = sectionHeader("Default tools (built-in)");
        Span intro = new Span("Pick which of the 86 built-in tools the MCP server exposes at boot.");
        intro.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        presetGroup.setLabel("Preset");
        List<Preset> presets = presetCatalog.presets();
        List<String> labels = new ArrayList<>();
        for (Preset p : presets) labels.add(p.displayName());
        presetGroup.setItems(labels);
        presetGroup.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.isFromClient()) return;
            if (e.getValue() != null) customPresetGroup.clear();
            refreshSummary();
        });

        customPresetGroup.setItems(List.of(CUSTOM_PRESET_LABEL));
        customPresetGroup.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.isFromClient()) return;
            if (e.getValue() != null) presetGroup.clear();
            refreshSummary();
        });

        Div rulesPanel = new Div();
        rulesPanel.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("padding", "var(--lumo-space-m)")
                .set("margin-top", "var(--lumo-space-s)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-left", "3px solid var(--lumo-primary-color)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("width", "100%")
                .set("box-sizing", "border-box");
        Div customRow = new Div(customPresetGroup);
        customRow.getStyle().set("grid-column", "1 / -1");
        Div rulesHeaderRow = new Div();
        rulesHeaderRow.getStyle()
                .set("grid-column", "1 / -1")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        rulesHeaderRow.setText("Advanced curation — applied on top of the preset");
        rulesPanel.add(customRow, rulesHeaderRow);

        Set<String> allNames = new TreeSet<>();
        Set<String> allTags = new TreeSet<>();
        for (ToolSpec spec : persistenceService.getDefaultToolSpecs()) {
            allNames.add(spec.name());
            allTags.addAll(spec.tags());
        }
        Set<String> allCategories = new LinkedHashSet<>();
        categoryCatalog.categories().forEach(c -> allCategories.add(c.id()));

        configureMulti(includeTags, allTags);
        configureMulti(includeCategories, allCategories);
        configureMulti(includeNames, allNames);
        configureMulti(excludeTags, allTags);
        configureMulti(excludeNames, allNames);

        Div includeNamesWrap = new Div(includeNames);
        includeNamesWrap.getStyle().set("grid-column", "1 / -1");
        rulesPanel.add(includeTags, includeCategories, includeNamesWrap, excludeTags, excludeNames);

        section.add(header, intro, presetGroup, rulesPanel);
        return section;
    }

    private VerticalLayout buildRegistrationSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-s)");

        Span header = sectionHeader("Custom tools (you created)");

        autoAddCheckbox.setLabel("Auto-add new custom tools to the MCP server");
        autoAddCheckbox.setHelperText("Tools you create in Tool Studio are exposed to the MCP server automatically.");

        toolSelector.setLabel("Manually exposed tools");
        toolSelector.setHelperText("Pick custom tools you want exposed (or override the preset to expose draft default tools too).");
        toolSelector.setWidthFull();
        toolSelector.setClearButtonVisible(true);
        toolSelector.setItemLabelGenerator(ToolSpec::name);
        toolSelector.addValueChangeListener(e -> refreshSummary());

        section.add(header, autoAddCheckbox, toolSelector);
        return section;
    }

    private VerticalLayout buildSummarySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-xs)");

        Span toolsLabel = new Span("Tools exposed");
        toolsLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.08em")
                .set("margin-right", "var(--lumo-space-s)");

        summary.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("margin-right", "var(--lumo-space-s)");

        chipsContainer.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "5px")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-shade-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("line-height", "1.9");
        chipsContainer.add(toolsLabel, summary);

        section.add(chipsContainer);
        return section;
    }

    private Span sectionHeader(String text) {
        Span header = new Span(text);
        header.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.08em");
        return header;
    }

    private Div buildDivider() {
        Div divider = new Div();
        divider.getStyle()
                .set("height", "1px")
                .set("width", "100%")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("margin", "var(--lumo-space-s) 0");
        return divider;
    }

    private void configureMulti(MultiSelectComboBox<String> field, Set<String> items) {
        field.setItems(items);
        field.setWidthFull();
        field.addValueChangeListener(e -> refreshSummary());
    }

    private String getSelectedPresetLabel() {
        if (customPresetGroup.getValue() != null) return CUSTOM_PRESET_LABEL;
        return presetGroup.getValue();
    }

    private void populateFromPreference(DefaultToolsPreference pref) {
        String label = labelForPreset(pref.preset());
        if (CUSTOM_PRESET_LABEL.equals(label)) {
            presetGroup.clear();
            customPresetGroup.setValue(CUSTOM_PRESET_LABEL);
        } else {
            customPresetGroup.clear();
            presetGroup.setValue(label);
        }
        includeTags.setValue(pref.include().tags());
        includeCategories.setValue(pref.include().categories());
        includeNames.setValue(pref.include().names());
        excludeTags.setValue(pref.exclude().tags());
        excludeNames.setValue(pref.exclude().names());
    }

    private String labelForPreset(String id) {
        if (id == null || id.isBlank()) return CUSTOM_PRESET_LABEL;
        return presetCatalog.findById(id).map(Preset::displayName).orElse(CUSTOM_PRESET_LABEL);
    }

    private String presetIdFromLabel(String label) {
        if (label == null || CUSTOM_PRESET_LABEL.equals(label)) return null;
        return presetCatalog.presets().stream()
                .filter(p -> p.displayName().equals(label))
                .map(Preset::id).findFirst().orElse(null);
    }

    private DefaultToolsPreference buildPreference() {
        String presetId = presetIdFromLabel(getSelectedPresetLabel());
        Rule include = new Rule(includeNames.getValue(), includeTags.getValue(), includeCategories.getValue());
        Rule exclude = new Rule(excludeNames.getValue(), excludeTags.getValue(), Set.of());
        return new DefaultToolsPreference(3, presetId, include, exclude);
    }

    private void refreshSummary() {
        DefaultToolsPreference pref = buildPreference();
        Set<String> presetActive = resolver.resolveActiveNames(pref, persistenceService.getDefaultToolSpecs());
        Set<String> manualNames = toolSelector.getSelectedItems().stream()
                .map(ToolSpec::name).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> extras = new LinkedHashSet<>(manualNames);
        extras.removeAll(presetActive);
        LinkedHashSet<String> union = new LinkedHashSet<>(presetActive);
        union.addAll(manualNames);
        int catalogTotal = persistenceService.getDefaultToolSpecs().size();
        String presetLabel = getSelectedPresetLabel();
        String header = (presetLabel == null || CUSTOM_PRESET_LABEL.equals(presetLabel)) ? "Custom" : presetLabel;
        boolean hasRules = !includeTags.getValue().isEmpty()
                || !includeCategories.getValue().isEmpty()
                || !includeNames.getValue().isEmpty()
                || !excludeTags.getValue().isEmpty()
                || !excludeNames.getValue().isEmpty();
        boolean realPreset = !(presetLabel == null || CUSTOM_PRESET_LABEL.equals(presetLabel));
        StringBuilder text = new StringBuilder(header);
        if (realPreset && hasRules) text.append(" + custom rules");
        if (!extras.isEmpty()) text.append(" + ").append(extras.size()).append(" manual");
        text.append(" · ").append(union.size()).append(" of ").append(catalogTotal).append(" tools");
        summary.setText(text.toString());
        refreshChips(presetActive, extras);
    }

    private void refreshChips(Set<String> presetActive, Set<String> extras) {
        while (chipsContainer.getComponentCount() > 2) {
            chipsContainer.remove(chipsContainer.getComponentAt(2));
        }
        if (presetActive.isEmpty() && extras.isEmpty()) {
            chipsContainer.add(emptyHint("no tools — pick a preset or add manual exposures above"));
            return;
        }
        if (!presetActive.isEmpty()) {
            renderChipGroup(new ArrayList<>(presetActive), false);
        }
        if (!extras.isEmpty()) {
            Div lineBreak = new Div();
            lineBreak.getStyle().set("flex-basis", "100%").set("height", "0").set("margin", "0");
            chipsContainer.add(lineBreak);
            Span plus = new Span("+ custom:");
            plus.getStyle()
                    .set("font-size", "var(--lumo-font-size-xxs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("text-transform", "uppercase")
                    .set("letter-spacing", "0.08em")
                    .set("margin-right", "var(--lumo-space-s)");
            chipsContainer.add(plus);
            renderChipGroup(new ArrayList<>(extras), true);
        }
    }

    private void renderChipGroup(List<String> names, boolean customStyle) {
        int visible = Math.min(CHIPS_VISIBLE, names.size());
        List<Span> hiddenChips = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Span chip = toolChip(names.get(i));
            if (customStyle) {
                chip.getStyle()
                        .set("background", "rgba(210, 161, 71, 0.16)")
                        .set("color", "#8a6519");
            }
            if (i >= visible) {
                chip.getStyle().set("display", "none");
                hiddenChips.add(chip);
            }
            chipsContainer.add(chip);
        }
        int hiddenCount = names.size() - visible;
        if (hiddenCount > 0) {
            Button more = new Button("+" + hiddenCount);
            styleAsMoreChip(more);
            more.addClickListener(e -> {
                for (Span hc : hiddenChips) hc.getStyle().remove("display");
                chipsContainer.remove(more);
            });
            chipsContainer.add(more);
        }
    }

    private Span toolChip(String name) {
        Span chip = new Span(name);
        chip.getStyle()
                .set("display", "inline-block")
                .set("padding", "2px 9px")
                .set("background", "rgba(31, 122, 90, 0.10)")
                .set("color", "var(--lumo-primary-color)")
                .set("border-radius", "999px")
                .set("font-family", "'SF Mono', 'Monaco', 'Cascadia Code', 'Consolas', monospace")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("line-height", "1.4");
        return chip;
    }

    private Span emptyHint(String text) {
        Span hint = new Span(text);
        hint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic")
                .set("font-size", "var(--lumo-font-size-s)");
        return hint;
    }

    private void styleAsMoreChip(Button button) {
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle()
                .set("padding", "2px 9px")
                .set("background", "var(--lumo-primary-color)")
                .set("color", "var(--lumo-primary-contrast-color)")
                .set("border-radius", "999px")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("line-height", "1.4")
                .set("min-width", "auto")
                .set("height", "auto")
                .set("min-height", "auto");
    }

    public ToolMcpServerSetting getUiToolMcpServerSetting() {
        return new ToolMcpServerSetting(autoAddCheckbox.getValue(),
                this.toolSelector.getSelectedItems().stream().map(ToolSpec::toolId).collect(Collectors.toSet()));
    }

    public DefaultToolsPreference getUiPreference() {
        return buildPreference();
    }

    public void applyCurationPreference() {
        persistenceService.applyPreference(buildPreference());
    }

    public void update(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {
        autoAddCheckbox.setValue(toolMcpServerSetting.autoAdd());
        toolSelector.setItems(toolSpecs);
        Set<String> exposedToolIds = toolMcpServerSetting.exposedToolIds();
        if (!exposedToolIds.isEmpty()) {
            toolSpecs.stream().filter(toolSpec -> exposedToolIds.contains(toolSpec.toolId()))
                    .forEach(toolSelector::select);
        }
        populateFromPreference(persistenceService.getPreferenceService().current());
        refreshSummary();
    }
}
