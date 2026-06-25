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

import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator.State;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService.ToolMcpServerSetting;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ExposedToolsSelector {

    private ExposedToolsSelector() {
    }

    public static MultiSelectComboBox<ToolSpec> newCustomSelector(Function<ToolSpec, String> riskLevelFn,
            Function<ToolSpec, String> categoryFn) {
        return newCategorizedSelector("Custom tools to expose", "Tools you built in Tool Studio.",
                riskLevelFn, categoryFn);
    }

    public static MultiSelectComboBox<ToolSpec> newComposedSelector(Function<ToolSpec, String> riskLevelFn,
            Function<ToolSpec, String> categoryFn) {
        return newCategorizedSelector("Composed external tools",
                "Tools re-exposed from connected external MCP servers — risk and HITL governed.",
                riskLevelFn, categoryFn);
    }

    public static MultiSelectComboBox<ToolSpec> newBuiltinSelector(Function<ToolSpec, String> riskLevelFn,
            Function<ToolSpec, String> categoryFn) {
        return newCategorizedSelector("Built-in tools to expose",
                "Local-Passed built-in tools — tick which to expose.", riskLevelFn, categoryFn);
    }

    public static MultiSelectComboBox<ToolSpec> newCategorizedSelector(String label, String helperText,
            Function<ToolSpec, String> riskLevelFn, Function<ToolSpec, String> categoryFn) {
        MultiSelectComboBox<ToolSpec> selector = new MultiSelectComboBox<>();
        selector.setLabel(label);
        selector.setHelperText(helperText);
        selector.setItemLabelGenerator(ToolSpec::name);
        selector.setRenderer(toolSpecRenderer(riskLevelFn, categoryFn));
        selector.setWidthFull();
        selector.setClearButtonVisible(true);
        return selector;
    }

    private static ComponentRenderer<VerticalLayout, ToolSpec> toolSpecRenderer(
            Function<ToolSpec, String> riskLevelFn, Function<ToolSpec, String> categoryFn) {
        return new ComponentRenderer<>(spec -> {
            Span name = new Span(spec.name());
            name.getStyle().set("flex", "1 1 auto").set("overflow", "hidden").set("text-overflow", "ellipsis");
            HorizontalLayout row = new HorizontalLayout(name);
            if (categoryFn != null) {
                Span category = new Span(categoryFn.apply(spec));
                category.getStyle().set("font-size", "var(--lumo-font-size-xxs)")
                        .set("color", "var(--lumo-secondary-text-color)").set("flex", "0 0 auto")
                        .set("white-space", "nowrap");
                row.add(category);
            }
            row.add(riskBadge(riskLevelFn.apply(spec)));
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            row.setWidthFull();
            row.setSpacing(false);
            row.getStyle().set("gap", "var(--lumo-space-s)");
            VerticalLayout item = new VerticalLayout(row);
            item.setPadding(false);
            item.setSpacing(false);
            item.setWidthFull();
            item.getStyle().set("gap", "0").set("padding", "var(--lumo-space-xs) 0");
            String description = spec.description();
            if (description != null && !description.isBlank()) {
                Span desc = new Span(description);
                desc.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                        .set("color", "var(--lumo-secondary-text-color)").set("white-space", "nowrap")
                        .set("overflow", "hidden").set("text-overflow", "ellipsis")
                        .set("max-width", "32em").set("display", "block");
                item.add(desc);
            }
            item.getElement().setAttribute("title",
                    description == null || description.isBlank() ? spec.name() : description);
            return item;
        });
    }

    private static Span riskBadge(String level) {
        Span badge = new Span(level == null ? "L0" : level);
        badge.getStyle()
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("padding", "1px 6px")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-family", "'SF Mono', 'Monaco', 'Cascadia Code', 'Consolas', monospace")
                .set("flex", "0 0 auto");
        switch (level == null ? "L0" : level) {
            case "L0", "L1" -> badge.getStyle().set("background", "rgba(31, 122, 90, 0.15)")
                    .set("color", "var(--lumo-success-text-color)");
            case "L2", "L3" -> badge.getStyle().set("background", "rgba(210, 161, 71, 0.18)")
                    .set("color", "#8a6519");
            default -> badge.getStyle().set("background", "rgba(219, 80, 80, 0.16)")
                    .set("color", "var(--lumo-error-text-color)");
        }
        return badge;
    }

    public static List<ToolSpec> customsFrom(List<ToolSpec> allToolSpecs, Set<String> defaultIds) {
        return allToolSpecs.stream().filter(spec -> !defaultIds.contains(spec.toolId())).toList();
    }

    public static List<ToolSpec> exposableBuiltinsFrom(List<ToolSpec> allToolSpecs, Set<String> defaultIds,
            ToolActivationCalculator activationCalculator) {
        return allToolSpecs.stream()
                .filter(spec -> defaultIds.contains(spec.toolId()))
                .filter(spec -> !spec.draft())
                .filter(spec -> activationCalculator.calculate(spec) != State.MISSING_REQUIREMENTS).toList();
    }

    public static void applyEmptyState(MultiSelectComboBox<ToolSpec> selector, boolean isEmpty,
            String emptyPlaceholder, String filledPlaceholder) {
        selector.setEnabled(!isEmpty);
        selector.setPlaceholder(isEmpty ? emptyPlaceholder : filledPlaceholder);
    }

    public static ToolMcpServerSetting computeSetting(boolean autoAdd, Set<String> customSelectedIds,
            Set<String> builtinSelectedIds, Set<String> exposableBuiltinIds) {
        HashSet<String> exposed = new HashSet<>(customSelectedIds);
        exposed.addAll(builtinSelectedIds);
        Set<String> excluded = exposableBuiltinIds.stream()
                .filter(id -> !builtinSelectedIds.contains(id)).collect(Collectors.toSet());
        return new ToolMcpServerSetting(autoAdd, exposed, excluded);
    }

    public static Set<ToolSpec> resolveBuiltinSelection(List<ToolSpec> exposableBuiltins, Set<String> exposedToolIds) {
        LinkedHashSet<ToolSpec> result = new LinkedHashSet<>();
        for (ToolSpec spec : exposableBuiltins) {
            if (exposedToolIds.contains(spec.toolId())) result.add(spec);
        }
        return result;
    }
}
