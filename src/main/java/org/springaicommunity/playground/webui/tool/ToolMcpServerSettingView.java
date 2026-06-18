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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ToolMcpServerSettingView extends VerticalLayout {

    private static final int CHIPS_VISIBLE = 10;

    private final ToolSpecPersistenceService persistenceService;
    private final ToolActivationCalculator activationCalculator;

    private List<ToolSpec> currentToolSpecs = List.of();
    private ToolMcpServerSetting confirmedSetting = new ToolMcpServerSetting(true, Set.of(), Set.of());

    private final Checkbox autoAddCheckbox = new Checkbox("Auto-expose newly published tools");
    private final MultiSelectComboBox<ToolSpec> customSelector;
    private final MultiSelectComboBox<ToolSpec> builtinSelector;
    private final Span summary = new Span();
    private final Span dirtyHint = new Span();
    private final Div chipsContainer = new Div();

    private boolean suppressChange;
    private boolean dirty;

    public ToolMcpServerSettingView(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting,
            ToolSpecPersistenceService persistenceService, ToolActivationCalculator activationCalculator,
            ToolSpecService toolSpecService) {
        this.persistenceService = persistenceService;
        this.activationCalculator = activationCalculator;
        this.customSelector = ExposedToolsSelector.newCustomSelector(
                toolSpecService::riskLevelOf, toolSpecService::categoryOf);
        this.builtinSelector = ExposedToolsSelector.newBuiltinSelector(
                toolSpecService::riskLevelOf, toolSpecService::categoryOf);

        setWidthFull();
        setPadding(false);
        setSpacing(true);

        wireDirtyListeners();

        add(buildSummarySection());
        add(buildDivider());
        add(buildCustomSection());
        add(buildDivider());
        add(buildBuiltinSection());

        update(toolSpecs, toolMcpServerSetting);
    }

    private void wireDirtyListeners() {
        autoAddCheckbox.addValueChangeListener(e -> {
            if (!suppressChange) markDirty();
        });
        customSelector.addValueChangeListener(e -> {
            if (!suppressChange) markDirty();
        });
        builtinSelector.addValueChangeListener(e -> {
            if (!suppressChange) markDirty();
        });
    }

    private Component buildCustomSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-s)");
        section.add(sectionHeader("Custom tools"),
                sectionIntro("Tools you built in Tool Studio."),
                customSelector);
        return section;
    }

    private VerticalLayout buildBuiltinSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-s)");
        section.add(sectionHeader("Built-in tools"),
                sectionIntro("Pick which Local-Passed built-in tools the MCP server exposes. "
                        + "Which tools are Local-Passed is set at setup (desktop launcher / CLI) and by Publish "
                        + "in the Tool Studio list."),
                builtinSelector);
        return section;
    }

    private VerticalLayout buildSummarySection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.getStyle().set("gap", "var(--lumo-space-xs)");

        Span toolsLabel = new Span("Tools exposed · Confirmed");
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

        autoAddCheckbox.setHelperText(
                "Newly published custom tools are exposed to the MCP server automatically. "
                        + "Built-in exposure follows the active tool preset.");

        dirtyHint.setText("● Unsaved changes — click \"Confirm\" to commit");
        dirtyHint.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-error-color)")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("padding", "0 var(--lumo-space-s)")
                .set("display", "none");

        section.add(chipsContainer, dirtyHint, autoAddCheckbox);
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

    private Span sectionIntro(String text) {
        Span intro = new Span(text);
        intro.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        return intro;
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

    private Set<String> exposableBuiltinIds() {
        Set<String> defaultIds = persistenceService.getDefaultToolIds();
        return ExposedToolsSelector.exposableBuiltinsFrom(currentToolSpecs, defaultIds, activationCalculator)
                .stream().map(ToolSpec::toolId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void markDirty() {
        if (!dirty) {
            dirty = true;
            dirtyHint.getStyle().set("display", "block");
        }
    }

    private void clearDirty() {
        dirty = false;
        dirtyHint.getStyle().set("display", "none");
    }

    private void refreshSummary() {
        Set<String> defaultIds = persistenceService.getDefaultToolIds();
        Set<String> exposedAllIds = confirmedSetting.exposedToolIds();

        long builtinExposed = exposedAllIds.stream().filter(defaultIds::contains).count();
        long customExposed = exposedAllIds.stream().filter(id -> !defaultIds.contains(id)).count();
        int builtinTotal = persistenceService.getDefaultToolSpecs().size();
        long customTotal = currentToolSpecs.stream()
                .filter(spec -> !defaultIds.contains(spec.toolId())).count();

        String text = "Built-in " + builtinExposed + "/" + builtinTotal
                + " · Custom " + customExposed + "/" + customTotal
                + " · Total " + exposedAllIds.size();
        summary.setText(text);

        Set<String> exposedNames = currentToolSpecs.stream()
                .filter(spec -> exposedAllIds.contains(spec.toolId()))
                .map(ToolSpec::name).collect(Collectors.toCollection(LinkedHashSet::new));
        persistenceService.getDefaultToolSpecs().stream()
                .filter(spec -> exposedAllIds.contains(spec.toolId()))
                .map(ToolSpec::name).forEach(exposedNames::add);
        refreshChips(exposedNames);
    }

    private void refreshChips(Set<String> exposedNames) {
        while (chipsContainer.getComponentCount() > 2) {
            chipsContainer.remove(chipsContainer.getComponentAt(2));
        }
        if (exposedNames.isEmpty()) {
            chipsContainer.add(emptyHint("no tools exposed"));
            return;
        }
        List<String> names = new ArrayList<>(exposedNames);
        int visible = Math.min(CHIPS_VISIBLE, names.size());
        List<Span> hiddenChips = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            Span chip = toolChip(names.get(i));
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
        Set<String> customSel = customSelector.getSelectedItems().stream()
                .map(ToolSpec::toolId).collect(Collectors.toSet());
        Set<String> builtinSel = builtinSelector.getSelectedItems().stream()
                .map(ToolSpec::toolId).collect(Collectors.toSet());
        return ExposedToolsSelector.computeSetting(autoAddCheckbox.getValue(), customSel, builtinSel,
                exposableBuiltinIds());
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void markConfirmed(ToolMcpServerSetting newConfirmed) {
        this.confirmedSetting = newConfirmed;
        clearDirty();
        refreshSummary();
    }

    public void update(List<ToolSpec> toolSpecs, ToolMcpServerSetting toolMcpServerSetting) {
        suppressChange = true;
        try {
            this.currentToolSpecs = toolSpecs;
            this.confirmedSetting = toolMcpServerSetting;
            autoAddCheckbox.setValue(toolMcpServerSetting.autoAdd());

            Set<String> defaultIds = persistenceService.getDefaultToolIds();
            List<ToolSpec> customs = ExposedToolsSelector.customsFrom(toolSpecs, defaultIds);
            List<ToolSpec> exposableBuiltins = ExposedToolsSelector.exposableBuiltinsFrom(
                    toolSpecs, defaultIds, activationCalculator);

            customSelector.setItems(customs);
            builtinSelector.setItems(exposableBuiltins);
            ExposedToolsSelector.applyEmptyState(customSelector, customs.isEmpty(),
                    "No custom tools yet — create one in Tool Studio", "Select tools to expose");
            ExposedToolsSelector.applyEmptyState(builtinSelector, exposableBuiltins.isEmpty(),
                    "No Local-Passed built-in tools — publish one or adjust the setup preset",
                    "Select tools to expose");

            customs.stream().filter(spec -> toolMcpServerSetting.exposedToolIds().contains(spec.toolId()))
                    .forEach(customSelector::select);
            ExposedToolsSelector.resolveBuiltinSelection(exposableBuiltins, toolMcpServerSetting.exposedToolIds())
                    .forEach(builtinSelector::select);
        } finally {
            suppressChange = false;
        }
        clearDirty();
        refreshSummary();
    }
}
