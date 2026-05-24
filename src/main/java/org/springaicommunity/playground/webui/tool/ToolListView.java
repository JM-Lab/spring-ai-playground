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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import org.springaicommunity.playground.service.tool.DefaultToolPresetCatalog;
import org.springaicommunity.playground.service.tool.DefaultToolsPreferenceResolver;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolCategoryCatalog;
import org.springaicommunity.playground.service.tool.ToolCategoryCatalog.CategoryDef;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator.State;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.Pills;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;
import org.springaicommunity.playground.webui.common.sidebar.CategoryGroupDetails;
import org.springaicommunity.playground.webui.common.sidebar.SidebarFilterBar;
import org.springaicommunity.playground.webui.common.sidebar.SidebarItemLayout;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.springaicommunity.playground.webui.tool.ToolStudioView.TOOL_EMPTY_EVENT;
import static org.springaicommunity.playground.webui.tool.ToolStudioView.TOOL_SELECT_EVENT;

public class ToolListView extends WorkspaceSidebar implements BeforeEnterObserver {

    private static final String LAST_SELECTED_TOOL = "lastSelectedTool";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport toolChangeSupport;
    private final ToolSpecService toolSpecService;
    private final ToolCategoryCatalog categoryCatalog;
    private final ToolActivationCalculator activationCalculator;
    private final SidebarFilterBar filterBar;
    private final VerticalLayout groupContainer;
    private final List<ListBox<ToolSpec>> categoryListBoxes = new ArrayList<>();

    private ToolSpec selectedSpec;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final DefaultToolPresetCatalog defaultToolPresetCatalog;
    private final DefaultToolsPreferenceResolver defaultToolsPreferenceResolver;

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        refreshFilterOptions(this.toolSpecService.getToolSpecList());
        renderGroups();
        this.persistentUiDataStorage.loadData(LAST_SELECTED_TOOL, new TypeReference<ToolSpec>() {},
                tool -> {
                    if (Objects.nonNull(tool)) {
                        this.toolSpecService.getToolSpecList().stream()
                                .filter(t -> t.toolId().equals(tool.toolId()))
                                .findFirst().ifPresent(this::selectInListBoxes);
                    }
                });
    }

    public void refreshAfterCurationChange() {
        renderGroups();
    }

    public ToolListView(PersistentUiDataStorage persistentUiDataStorage, ToolSpecService toolSpecService,
            ToolCategoryCatalog categoryCatalog, ToolActivationCalculator activationCalculator,
            PropertyChangeSupport toolChangeSupport,
            ToolSpecPersistenceService toolSpecPersistenceService,
            DefaultToolPresetCatalog defaultToolPresetCatalog,
            DefaultToolsPreferenceResolver defaultToolsPreferenceResolver) {
        super("Tool List");
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.toolSpecService = toolSpecService;
        this.categoryCatalog = categoryCatalog;
        this.activationCalculator = activationCalculator;
        this.toolChangeSupport = toolChangeSupport;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.defaultToolPresetCatalog = defaultToolPresetCatalog;
        this.defaultToolsPreferenceResolver = defaultToolsPreferenceResolver;

        addHeaderIcon(VaadinIcon.CLOSE, "Delete Selected Tool", e -> deleteTool());

        this.filterBar = new SidebarFilterBar(new SidebarFilterBar.Config(
                "Search tools…", "Category", "Tag", 200));
        this.filterBar.setOnChange(this::renderGroups);

        this.groupContainer = new VerticalLayout();
        this.groupContainer.setPadding(false);
        this.groupContainer.setSpacing(false);
        this.groupContainer.setWidthFull();
        this.groupContainer.getStyle()
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("gap", "0");

        VerticalLayout body = new VerticalLayout(this.filterBar, this.groupContainer);
        body.setPadding(false);
        body.setSpacing(false);
        body.setSizeFull();
        body.getStyle()
                .set("min-height", "0")
                .set("gap", "0")
                .set("overflow-x", "hidden");
        setSidebarContent(body);
    }

    private void renderGroups() {
        this.groupContainer.removeAll();
        this.categoryListBoxes.clear();

        List<ToolSpec> all = this.toolSpecService.getToolSpecList();
        List<ToolSpec> filtered = applyAllFilters(all);
        updateHeaderCount(filtered.size(), all.size());
        if (filtered.isEmpty()) {
            Span empty = new Span(all.isEmpty() ? "No tools yet." : "No tools match the current filters.");
            empty.getStyle()
                    .set("padding", "var(--lumo-space-m)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            this.groupContainer.add(empty);
            return;
        }

        List<ToolSpec> drafts = new ArrayList<>();
        List<ToolSpec> published = new ArrayList<>();
        for (ToolSpec tool : filtered) {
            if (tool.draft()) drafts.add(tool);
            else published.add(tool);
        }

        Details localPass = buildParentSection("Local Pass", published, false);
        Div localPassWrap = new Div(localPass);
        localPassWrap.getStyle()
                .set("padding-inline-start", "var(--lumo-space-s)")
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("max-height", "50%")
                .set("overflow-y", "auto")
                .set("width", "100%");
        this.groupContainer.add(localPassWrap);

        Details draftsSection = buildParentSection("Drafts", drafts, true);
        Div draftsWrap = new Div(draftsSection);
        draftsWrap.getStyle()
                .set("padding-inline-start", "var(--lumo-space-s)")
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("max-height", "50%")
                .set("overflow-y", "auto")
                .set("width", "100%");
        this.groupContainer.add(draftsWrap);
    }

    private Details buildParentSection(String label, List<ToolSpec> tools, boolean draftStyling) {
        Span header = new Span(label + " (" + tools.size() + ")");
        header.getStyle()
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em");
        if (draftStyling) {
            header.getStyle().set("color", "var(--lumo-secondary-text-color)");
        }

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();
        body.getStyle()
                .set("gap", "0")
                .set("padding-inline-start", "var(--lumo-space-s)");
        if (draftStyling && !tools.isEmpty()) {
            Span hint = new Span("Open a draft → Test in Builder → Publish to activate on MCP");
            hint.getStyle()
                    .set("display", "block")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic");
            body.add(hint);
        }
        for (Component child : buildCategoryChildren(tools)) {
            body.add(child);
        }

        Details details = new Details(header, body);
        details.addClassName("workspace-sidebar-parent");
        details.setOpened(true);
        details.setWidthFull();
        details.getStyle()
                .set("--vaadin-details-summary-padding", "var(--lumo-space-xs) var(--lumo-space-m)");
        if (draftStyling) {
            details.addClassName("drafts-section");
            details.getStyle()
                    .set("margin-top", "var(--lumo-space-s)")
                    .set("padding-top", "var(--lumo-space-xs)")
                    .set("border-top", "2px dashed var(--lumo-contrast-20pct)")
                    .set("background", "var(--lumo-shade-5pct)")
                    .set("border-radius", "var(--lumo-border-radius-s)");
        }
        return details;
    }

    private List<Component> buildCategoryChildren(List<ToolSpec> tools) {
        Map<String, List<ToolSpec>> byCategory = new LinkedHashMap<>();
        for (CategoryDef categoryDef : this.categoryCatalog.categories()) {
            byCategory.put(categoryDef.id(), new ArrayList<>());
        }
        for (ToolSpec tool : tools) {
            String id = this.categoryCatalog.resolveOrFallback(tool.category()).id();
            byCategory.get(id).add(tool);
        }
        List<Component> children = new ArrayList<>();
        for (Map.Entry<String, List<ToolSpec>> entry : byCategory.entrySet()) {
            List<ToolSpec> bucket = entry.getValue();
            if (bucket.isEmpty()) continue;
            bucket.sort(Comparator.comparing(ToolSpec::name, String.CASE_INSENSITIVE_ORDER));
            CategoryDef categoryDef = this.categoryCatalog.find(entry.getKey()).orElseThrow();
            children.add(buildCategoryGroup(categoryDef, bucket));
        }
        return children;
    }

    private void updateHeaderCount(int filteredCount, int totalCount) {
        String suffix = filteredCount == totalCount ? " (" + totalCount + ")"
                : " (" + filteredCount + " of " + totalCount + ")";
        setSidebarTitle("Tool List" + suffix);
    }

    private List<ToolSpec> applyAllFilters(List<ToolSpec> tools) {
        if (tools == null || tools.isEmpty()) return List.of();
        List<ToolSpec> out = new ArrayList<>(tools.size());
        for (ToolSpec tool : tools) {
            if (!matchesSearch(tool)) continue;
            if (!matchesCategoryFilter(tool)) continue;
            if (!matchesTagFilter(tool)) continue;
            out.add(tool);
        }
        return out;
    }

    private boolean matchesSearch(ToolSpec tool) {
        String query = this.filterBar.getSearchQuery();
        if (query.isEmpty()) return true;
        String name = tool.name() == null ? "" : tool.name().toLowerCase(Locale.ROOT);
        String desc = tool.description() == null ? "" : tool.description().toLowerCase(Locale.ROOT);
        return name.contains(query) || desc.contains(query);
    }

    private boolean matchesCategoryFilter(ToolSpec tool) {
        Set<String> selection = this.filterBar.getCategorySelection();
        if (selection.isEmpty()) return true;
        String resolved = this.categoryCatalog.resolveOrFallback(tool.category()).id();
        return selection.contains(resolved);
    }

    private boolean matchesTagFilter(ToolSpec tool) {
        Set<String> selection = this.filterBar.getTagSelection();
        if (selection.isEmpty()) return true;
        for (String wanted : selection) {
            if (tool.tags().contains(wanted)) return true;
        }
        return false;
    }

    private void refreshFilterOptions(List<ToolSpec> tools) {
        Set<String> presentCategoryIds = new LinkedHashSet<>();
        Set<String> observedTags = new TreeSet<>();
        for (ToolSpec tool : tools) {
            presentCategoryIds.add(this.categoryCatalog.resolveOrFallback(tool.category()).id());
            observedTags.addAll(tool.tags());
        }
        List<String> categoryItems = new ArrayList<>();
        for (CategoryDef categoryDef : this.categoryCatalog.categories()) {
            if (presentCategoryIds.contains(categoryDef.id())) categoryItems.add(categoryDef.id());
        }
        this.filterBar.setCategoryItems(categoryItems,
                id -> this.categoryCatalog.resolveOrFallback(id).displayName());
        this.filterBar.setTagItems(new ArrayList<>(observedTags));
    }

    private Details buildCategoryGroup(CategoryDef categoryDef, List<ToolSpec> tools) {
        ListBox<ToolSpec> listBox = new ListBox<>();
        listBox.addClassName("custom-list-box");
        listBox.setWidthFull();
        listBox.setItems(tools);
        listBox.setRenderer(new ComponentRenderer<>(this::renderToolItem));
        listBox.addValueChangeListener(event -> notifyToolSelection(event.getOldValue(), event.getValue()));
        listBox.getStyle().set("--lumo-size-m", "var(--lumo-size-s)");
        this.categoryListBoxes.add(listBox);
        return CategoryGroupDetails.build(categoryDef.displayName(), tools.size(), listBox);
    }

    private Div renderToolItem(ToolSpec tool) {
        State state = this.activationCalculator.calculate(tool);
        Span dot = Pills.dot(stateColor(state));
        dot.getElement().setAttribute("title", state.name());

        Span title = listItemText(tool.name());
        title.getElement().setAttribute("title", Objects.toString(tool.description(), ""));

        List<Span> pills = new ArrayList<>();
        for (String tag : tool.tags()) {
            pills.add(Pills.pill(tag, "badge success pill small"));
        }
        return SidebarItemLayout.twoRow(dot, title, pills, false);
    }

    private static String stateColor(State state) {
        return switch (state) {
            case ACTIVE               -> "var(--lumo-success-color)";
            case TEST_FAILED          -> "var(--lumo-error-color)";
            case MISSING_REQUIREMENTS -> "#f0a500";
            case DRAFT                -> "var(--lumo-contrast-30pct)";
        };
    }

    private void notifyToolSelection(ToolSpec oldToolSpec, ToolSpec newToolSpec) {
        if (Objects.isNull(newToolSpec)) return;
        if (newToolSpec.equals(this.selectedSpec)) return;

        for (ListBox<ToolSpec> toolSpecListBox : this.categoryListBoxes) {
            if (!Objects.equals(toolSpecListBox.getValue(), newToolSpec)) {
                toolSpecListBox.clear();
            }
        }
        ToolSpec previous = this.selectedSpec;
        this.selectedSpec = newToolSpec;
        this.toolChangeSupport.firePropertyChange(TOOL_SELECT_EVENT, previous, newToolSpec);
        this.persistentUiDataStorage.saveData(LAST_SELECTED_TOOL, newToolSpec);
    }

    private void selectInListBoxes(ToolSpec target) {
        for (ListBox<ToolSpec> listBox : this.categoryListBoxes) {
            if (listBox.getListDataView().getItems().anyMatch(t -> t.toolId().equals(target.toolId()))) {
                listBox.setValue(target);
                return;
            }
        }
    }

    private void deleteTool() {
        this.getCurrentToolAsOpt().ifPresent(toolSpec -> {
            Dialog dialog = VaadinUtils.headerDialog("Delete Tool: " + toolSpec.name());
            dialog.setModal(true);
            dialog.add("Are you sure you want to delete toolSpec '" + toolSpec.name() + "' permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.toolSpecService.deleteToolSpec(toolSpec.toolId());
                this.changeToolContent(null);
                dialog.close();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            dialog.getFooter().add(deleteButton);

            dialog.open();
            deleteButton.focus();
        });
    }

    public void changeToolContent(ToolSpec toolSpec) {
        VaadinUtils.getUi(this).access(() -> {
            List<ToolSpec> toolList = this.toolSpecService.getToolSpecList();
            refreshFilterOptions(toolList);

            if (toolList.isEmpty()) {
                this.selectedSpec = null;
                renderGroups();
                this.toolChangeSupport.firePropertyChange(TOOL_EMPTY_EVENT, false, true);
                return;
            }

            renderGroups();

            ToolSpec target = Objects.isNull(toolSpec)
                    ? toolList.getFirst()
                    : toolList.stream().filter(t -> t.name().equals(toolSpec.name())).findFirst()
                            .orElse(toolList.getFirst());
            selectInListBoxes(target);
        });
    }

    public void clearSelectTool() {
        for (ListBox<ToolSpec> listBox : this.categoryListBoxes) {
            listBox.clear();
        }
        this.selectedSpec = null;
    }

    private Optional<ToolSpec> getCurrentToolAsOpt() {
        return Optional.ofNullable(this.selectedSpec);
    }
}
