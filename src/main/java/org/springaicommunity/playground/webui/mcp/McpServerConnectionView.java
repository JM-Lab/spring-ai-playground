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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.CategoryDef;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogEntry;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.catalog.McpCategoryService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.StatusEntry;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.Pills;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;
import org.springaicommunity.playground.webui.common.sidebar.CategoryGroupDetails;
import org.springaicommunity.playground.webui.common.sidebar.SidebarFilterBar;
import org.springaicommunity.playground.webui.common.sidebar.SidebarItemLayout;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import static org.springaicommunity.playground.webui.mcp.McpServerView.MCP_CONNECTION_SELECT_EVENT;

public class McpServerConnectionView extends WorkspaceSidebar implements BeforeEnterObserver {

    private static final String BUILTIN_KEY = "__builtin__";
    private static final String ACTIVE_KEY_PREFIX = "active::";
    private static final String CATALOG_KEY_PREFIX = "catalog::";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport mcpServerInfoChangeSupport;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final McpCategoryService mcpCategoryService;
    private final McpCatalogService mcpCatalogService;

    private final Map<String, ListBox<McpServerInfo>> mcpServerInfoListBoxMap = new LinkedHashMap<>();
    private final Set<String> ghostNames = new HashSet<>();
    private McpServerInfo selectedMcpServerInfo;

    private final SidebarFilterBar filterBar;
    private final VerticalLayout groupContainer = new VerticalLayout();

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        this.persistentUiDataStorage.loadData(McpContentView.LAST_SELECTED_MCP_CONNECTION,
                new TypeReference<McpServerInfo>() {},
                savedInfo -> resolveInitialSelection(savedInfo).ifPresent(this::selectMcpConnectionContent));
    }

    private Optional<McpServerInfo> resolveInitialSelection(McpServerInfo savedInfo) {
        Optional<McpServerInfo> fromSaved = Optional.ofNullable(savedInfo)
                .map(info -> this.mcpServerInfoService.getMcpServerInfos().get(info.mcpTransportType()))
                .flatMap(list -> list.stream()
                        .filter(info -> info.serverName().equals(savedInfo.serverName()))
                        .findFirst());
        return fromSaved
                .or(() -> Optional.ofNullable(this.mcpServerInfoService.getDefaultMcpServerInfo()))
                .or(() -> this.mcpServerInfoService.getMcpServerInfos().values().stream()
                        .flatMap(List::stream).findFirst());
    }

    public McpServerConnectionView(PersistentUiDataStorage persistentUiDataStorage,
            McpServerInfoService mcpServerInfoService, McpClientService mcpClientService,
            McpCategoryService mcpCategoryService, McpCatalogService mcpCatalogService,
            PropertyChangeSupport mcpServerInfoChangeSupport) {
        super("MCP Server Connections");
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.mcpCategoryService = mcpCategoryService;
        this.mcpCatalogService = mcpCatalogService;
        this.mcpServerInfoChangeSupport = mcpServerInfoChangeSupport;

        addHeaderIcon(VaadinIcon.CLOSE, "Delete Selected MCP Server", e -> deleteSelected());

        this.filterBar = new SidebarFilterBar(new SidebarFilterBar.Config(
                "Search by name, vendor…", "Categories", "Tags", 200));
        this.filterBar.setOnChange(this::renderCategoryGroups);

        setSidebarContent(buildSidebarBody());
        updateMcpConnections();

        addAttachListener(e -> {
            McpServerInfo target = this.selectedMcpServerInfo != null
                    ? this.selectedMcpServerInfo
                    : this.mcpServerInfoService.getDefaultMcpServerInfo();
            if (target == null) return;
            e.getUI().beforeClientResponse(this, ctx -> {
                ListBox<McpServerInfo> box = this.mcpServerInfoListBoxMap.get(listBoxKeyFor(target));
                if (box == null) return;
                box.clear();
                box.setValue(target);
            });
        });
    }

    private VerticalLayout buildSidebarBody() {
        this.groupContainer.setPadding(false);
        this.groupContainer.setSpacing(false);
        this.groupContainer.setWidthFull();
        this.groupContainer.getStyle()
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("gap", "0");

        VerticalLayout body = new VerticalLayout(this.filterBar, verticalScroller(this.groupContainer));
        body.setPadding(false);
        body.setSpacing(false);
        body.setSizeFull();
        body.getStyle().set("min-height", "0").set("gap", "0").set("overflow-x", "hidden");
        return body;
    }

    public void updateMcpConnections() {
        List<String> categoryOptions = this.mcpCategoryService.getAllCategories(collectAllCategoryIds()).stream()
                .map(CategoryDef::id).toList();
        Set<String> previousCats = this.filterBar.getCategorySelection();
        this.filterBar.setCategoryItems(categoryOptions,
                id -> this.mcpCategoryService.resolveDef(id).displayName());
        Set<String> retainedCats = new LinkedHashSet<>(previousCats);
        retainedCats.retainAll(categoryOptions);
        this.filterBar.setCategorySelection(retainedCats);

        List<String> tagOptions = new ArrayList<>(new TreeSet<>(collectAllTags()));
        Set<String> previousTags = this.filterBar.getTagSelection();
        this.filterBar.setTagItems(tagOptions);
        Set<String> retainedTags = new LinkedHashSet<>(previousTags);
        retainedTags.retainAll(tagOptions);
        this.filterBar.setTagSelection(retainedTags);

        renderCategoryGroups();
    }

    private Set<String> collectAllCategoryIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (McpCatalogEntry e : this.mcpCatalogService.getCatalog()) ids.add(e.category());
        this.mcpServerInfoService.getMcpServerInfos().values().stream().flatMap(List::stream)
                .forEach(i -> ids.add(i.category()));
        return ids;
    }

    private Set<String> collectAllTags() {
        Set<String> tags = new LinkedHashSet<>();
        for (McpCatalogEntry e : this.mcpCatalogService.getCatalog()) tags.addAll(e.tags());
        this.mcpServerInfoService.getMcpServerInfos().values().stream().flatMap(List::stream)
                .forEach(i -> tags.addAll(i.tags()));
        return tags;
    }

    private void renderCategoryGroups() {
        this.mcpServerInfoListBoxMap.clear();
        this.ghostNames.clear();
        this.groupContainer.removeAll();

        McpServerInfo builtinInfo = this.mcpServerInfoService.getDefaultMcpServerInfo();
        String builtinName = builtinInfo == null ? null : builtinInfo.serverName();

        Map<String, List<McpServerInfo>> userByCategory = new LinkedHashMap<>();
        Set<String> userIds = new HashSet<>();
        this.mcpServerInfoService.getMcpServerInfos().values().stream().flatMap(List::stream).forEach(info -> {
            userIds.add(info.serverName());
            if (builtinName != null && builtinName.equals(info.serverName())) return;
            userByCategory.computeIfAbsent(info.category(), k -> new ArrayList<>()).add(info);
        });

        int totalCount = userIds.size();
        boolean builtinMatchesFilters = builtinInfo != null
                && matchesSearch(builtinInfo) && matchesTagFilter(builtinInfo)
                && (this.filterBar.getCategorySelection().isEmpty()
                        || this.filterBar.getCategorySelection().contains(builtinInfo.category()));

        Map<String, List<McpServerInfo>> catalogByCategory = new LinkedHashMap<>();
        for (McpCatalogEntry entry : this.mcpCatalogService.getCatalog()) {
            if (userIds.contains(McpCatalogService.stripOsSuffix(entry.id()))) continue;
            try {
                McpServerInfo ghost = this.mcpCatalogService.instantiate(entry, null, Map.of());
                catalogByCategory.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(ghost);
                this.ghostNames.add(ghost.serverName());
                totalCount++;
            } catch (RuntimeException ignore) {
            }
        }

        Map<String, List<McpServerInfo>> userFiltered = filterByCategory(userByCategory);
        Map<String, List<McpServerInfo>> catalogFiltered = filterByCategory(catalogByCategory);

        int activeFilteredCount = userFiltered.values().stream().mapToInt(List::size).sum();
        int catalogFilteredCount = catalogFiltered.values().stream().mapToInt(List::size).sum();
        int filteredCount = (builtinMatchesFilters ? 1 : 0) + activeFilteredCount + catalogFilteredCount;

        boolean showBuiltin = builtinInfo != null && builtinMatchesFilters;
        boolean anyCatalog = catalogFilteredCount > 0;

        if (showBuiltin) {
            this.groupContainer.add(buildBuiltinFlatGroup(builtinInfo));
        }

        Details activeSection = buildActiveSection(userFiltered, activeFilteredCount);
        this.groupContainer.add(wrapSection(activeSection, anyCatalog));

        if (anyCatalog) {
            Details catalogSection = buildCatalogSection(catalogFiltered, catalogFilteredCount);
            this.groupContainer.add(wrapSection(catalogSection, true));
        }

        if (!showBuiltin && activeFilteredCount == 0 && !anyCatalog && this.filterBar.isActive()) {
            this.groupContainer.add(buildEmptyState());
        }

        updateHeaderCount(filteredCount, totalCount);
    }

    private Div buildBuiltinFlatGroup(McpServerInfo builtinInfo) {
        ListBox<McpServerInfo> builtinBox = buildMcpServerInfoListBox();
        builtinBox.setItems(List.of(builtinInfo));
        this.mcpServerInfoListBoxMap.put(BUILTIN_KEY, builtinBox);

        Span header = new Span("Built-in MCP");
        header.getStyle()
                .set("display", "block")
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)");

        Div group = new Div(header, builtinBox);
        group.setWidthFull();
        group.getStyle()
                .set("padding-inline-start", "var(--lumo-space-s)")
                .set("padding-bottom", "var(--lumo-space-xs)")
                .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        return group;
    }

    private Map<String, List<McpServerInfo>> filterByCategory(Map<String, List<McpServerInfo>> source) {
        Set<String> categorySelection = this.filterBar.getCategorySelection();
        Map<String, List<McpServerInfo>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<McpServerInfo>> e : source.entrySet()) {
            if (!categorySelection.isEmpty() && !categorySelection.contains(e.getKey())) continue;
            List<McpServerInfo> filtered = applyAllFilters(e.getValue());
            if (!filtered.isEmpty()) out.put(e.getKey(), filtered);
        }
        return out;
    }

    private Details buildActiveSection(Map<String, List<McpServerInfo>> filteredByCategory, int sectionCount) {
        VerticalLayout body = parentSectionBody();

        if (sectionCount == 0) {
            Span empty = new Span("No active MCP yet — activate one from the Inactive MCP list below.");
            empty.getStyle()
                    .set("display", "block")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-style", "italic");
            body.add(empty);
        }

        List<CategoryDef> ordered = this.mcpCategoryService.getAllCategories(filteredByCategory.keySet());
        for (CategoryDef def : ordered) {
            List<McpServerInfo> items = filteredByCategory.get(def.id());
            if (items == null || items.isEmpty()) continue;

            ListBox<McpServerInfo> listBox = buildMcpServerInfoListBox();
            listBox.setItems(items);
            this.mcpServerInfoListBoxMap.put(ACTIVE_KEY_PREFIX + def.id(), listBox);
            Details catDetails = def.builtIn()
                    ? CategoryGroupDetails.build(def.displayName(), items.size(), listBox)
                    : CategoryGroupDetails.buildUserDefined(def.displayName(), items.size(), listBox);
            body.add(catDetails);
        }

        return wrapParentDetails("Active MCP", sectionCount, body, false);
    }

    private Details buildCatalogSection(Map<String, List<McpServerInfo>> filteredByCategory, int sectionCount) {
        VerticalLayout body = parentSectionBody();

        Span hint = new Span("Catalog entries — click an item to activate.");
        hint.getStyle()
                .set("display", "block")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-m)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-style", "italic");
        body.add(hint);

        List<CategoryDef> ordered = this.mcpCategoryService.getAllCategories(filteredByCategory.keySet());
        for (CategoryDef def : ordered) {
            List<McpServerInfo> items = filteredByCategory.get(def.id());
            if (items == null || items.isEmpty()) continue;

            ListBox<McpServerInfo> listBox = buildMcpServerInfoListBox();
            listBox.setItems(items);
            this.mcpServerInfoListBoxMap.put(CATALOG_KEY_PREFIX + def.id(), listBox);

            Details catDetails = def.builtIn()
                    ? CategoryGroupDetails.build(def.displayName(), items.size(), listBox)
                    : CategoryGroupDetails.buildUserDefined(def.displayName(), items.size(), listBox);
            body.add(catDetails);
        }

        return wrapParentDetails("Inactive MCP", sectionCount, body, true);
    }

    private static VerticalLayout parentSectionBody() {
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.setWidthFull();
        body.getStyle()
                .set("gap", "0")
                .set("padding-inline-start", "var(--lumo-space-s)");
        return body;
    }

    private static Details wrapParentDetails(String label, int count, Component body, boolean dimmed) {
        Span header = new Span(label + " (" + count + ")");
        header.getStyle()
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.06em");
        if (dimmed) header.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Details details = new Details(header, body);
        details.addClassName("workspace-sidebar-parent");
        details.setOpened(true);
        details.setWidthFull();
        details.getStyle()
                .set("--vaadin-details-summary-padding", "var(--lumo-space-xs) var(--lumo-space-m)");
        if (dimmed) {
            details.addClassName("catalog-section");
            details.getStyle()
                    .set("margin-top", "var(--lumo-space-s)")
                    .set("padding-top", "var(--lumo-space-xs)")
                    .set("border-top", "2px dashed var(--lumo-contrast-20pct)")
                    .set("background", "var(--lumo-shade-5pct)")
                    .set("border-radius", "var(--lumo-border-radius-s)");
        }
        return details;
    }

    private static Div wrapSection(Details section, boolean shareHeight) {
        Div wrap = new Div(section);
        wrap.getStyle()
                .set("padding-inline-start", "var(--lumo-space-s)")
                .set("flex", "1 1 auto")
                .set("min-height", "0")
                .set("max-height", shareHeight ? "50%" : "100%")
                .set("overflow-y", "auto")
                .set("width", "100%");
        return wrap;
    }

    private void updateHeaderCount(int filteredCount, int totalCount) {
        String suffix = filteredCount == totalCount ? " (" + totalCount + ")"
                : " (" + filteredCount + " of " + totalCount + ")";
        setSidebarTitle("MCP Server Connections" + suffix);
    }

    private Div buildEmptyState() {
        Span title = new Span("No servers match your filters");
        title.getStyle().set("font-weight", "500");
        Paragraph hint = new Paragraph(
                "Try a broader keyword, fewer categories or tags. Tool-name search needs the server to be activated first.");
        hint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin", "var(--lumo-space-xs) 0 var(--lumo-space-s)");
        Button clear = new Button("Clear filters", VaadinIcon.CLOSE_SMALL.create(), e -> this.filterBar.clear());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Div empty = new Div(title, hint, clear);
        empty.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "flex-start");
        return empty;
    }

    private List<McpServerInfo> applyAllFilters(List<McpServerInfo> items) {
        if (items.isEmpty()) return items;
        List<McpServerInfo> out = new ArrayList<>(items.size());
        for (McpServerInfo i : items) {
            if (!matchesSearch(i)) continue;
            if (!matchesTagFilter(i)) continue;
            out.add(i);
        }
        return out;
    }

    private boolean matchesSearch(McpServerInfo info) {
        String query = this.filterBar.getSearchQuery();
        if (query.isEmpty()) return true;
        if (lower(info.serverName()).contains(query)) return true;
        if (lower(info.description()).contains(query)) return true;
        String vendor = this.mcpCatalogService.findByServerName(info.serverName()).map(McpCatalogEntry::vendor).orElse("");
        if (lower(vendor).contains(query)) return true;
        return this.mcpClientService.getToolListAsOpt(info)
                .map(tools -> tools.stream().anyMatch(t ->
                        lower(t.name()).contains(query)
                                || lower(t.description()).contains(query)))
                .orElse(false);
    }

    private boolean matchesTagFilter(McpServerInfo info) {
        Set<String> selection = this.filterBar.getTagSelection();
        if (selection.isEmpty()) return true;
        for (String wanted : selection) {
            if (info.tags().contains(wanted)) return true;
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private ListBox<McpServerInfo> buildMcpServerInfoListBox() {
        ListBox<McpServerInfo> box = new ListBox<>();
        box.addClassName("custom-list-box");
        box.setWidthFull();
        box.setRenderer(new ComponentRenderer<>(this::renderItem));
        box.addValueChangeListener(event -> notifyMcpServerInfoSelection(event.getOldValue(), event.getValue()));
        box.getStyle().set("--lumo-size-m", "var(--lumo-size-s)");
        return box;
    }

    private Div renderItem(McpServerInfo info) {
        boolean ghost = this.ghostNames.contains(info.serverName());
        StatusEntry status = ghost ? recomputeGhostStatus(info) : mcpClientService.getStatus(info);
        Optional<McpCatalogEntry> entryOpt = this.mcpCatalogService.findByServerName(info.serverName());

        Span dot = Pills.dot(statusColor(status));
        dot.getElement().setAttribute("title", status.status().name());

        Span title = listItemText(info.serverName());
        title.getElement().setAttribute("title", Objects.toString(info.description(), ""));
        if (ghost) title.getStyle().set("font-style", "italic");

        List<Span> pills = new ArrayList<>();
        CategoryDef catDef = this.mcpCategoryService.resolveDef(info.category());
        pills.add(Pills.pill(catDef.displayName(), "badge contrast pill small"));
        for (String tag : info.tags()) {
            if (tag == null || tag.isBlank() || "global".equalsIgnoreCase(tag)) continue;
            pills.add(Pills.pill(tag.toUpperCase(Locale.ROOT), "badge success pill small"));
        }
        entryOpt.map(McpCatalogEntry::stability)
                .filter(s -> s != null && !"GA".equalsIgnoreCase(s))
                .ifPresent(stab -> pills.add(Pills.pill(stab.toLowerCase(Locale.ROOT), "badge warning pill small")));

        Div row = SidebarItemLayout.twoRow(dot, title, pills, ghost);
        boolean isSelected = info.equals(this.selectedMcpServerInfo);
        if (!isSelected) {
            Tooltip.forComponent(row)
                    .withText(buildTooltip(info, status, entryOpt, ghost))
                    .withPosition(Tooltip.TooltipPosition.END)
                    .withHoverDelay(1);
        }
        if (!ghost) schedulePing(info);
        return row;
    }

    private static String statusColor(StatusEntry status) {
        return switch (status.status()) {
            case OK -> "var(--lumo-success-color)";
            case ERROR -> "var(--lumo-error-color)";
            case AWAITING_AUTHORIZATION -> "var(--lumo-warning-color)";
            case OFFLINE -> "var(--lumo-contrast-30pct)";
            case MISSING_CONFIG -> "var(--lumo-contrast-50pct)";
        };
    }

    private String buildTooltip(McpServerInfo info, StatusEntry status, Optional<McpCatalogEntry> entryOpt,
            boolean ghost) {
        StringBuilder sb = new StringBuilder();
        if (info.description() != null && !info.description().isBlank()) sb.append(info.description());
        String vendor = entryOpt.map(McpCatalogEntry::vendor).filter(v -> v != null && !v.isBlank()).orElse(null);
        if (vendor != null) appendLine(sb, "Vendor: " + vendor);
        appendLine(sb, "Transport: " + info.mcpTransportType().name());
        switch (status.status()) {
            case ERROR -> appendLine(sb, "Error: " + (status.error() == null ? "" : status.error()));
            case AWAITING_AUTHORIZATION ->
                    appendLine(sb, "Awaiting OAuth authorization — open the config panel and click Authorize.");
            case MISSING_CONFIG ->
                    appendLine(sb, status.error() == null ? "Missing required configuration" : status.error());
            default -> {
                if (ghost) appendLine(sb, "Catalog entry — click to activate.");
            }
        }
        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String text) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(text);
    }

    private StatusEntry recomputeGhostStatus(McpServerInfo ghost) {
        Set<String> missing = mcpClientService.findMissingEnv(ghost);
        if (!missing.isEmpty()) return StatusEntry.missingConfig(missing);
        return StatusEntry.OFFLINE;
    }

    private String listBoxKeyFor(McpServerInfo info) {
        if (info == null) return null;
        McpServerInfo def = this.mcpServerInfoService.getDefaultMcpServerInfo();
        if (def != null && def.serverName().equals(info.serverName())) return BUILTIN_KEY;
        if (this.ghostNames.contains(info.serverName())) {
            return CATALOG_KEY_PREFIX + info.category();
        }
        return ACTIVE_KEY_PREFIX + info.category();
    }

    private void schedulePing(McpServerInfo info) {
        UI ui = VaadinUtils.getUi(this);
        if (ui == null) return;
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            StatusEntry before = mcpClientService.getStatus(info);
            StatusEntry after = mcpClientService.pingAndUpdateStatus(info);
            if (!Objects.equals(before, after)) {
                ListBox<McpServerInfo> box = mcpServerInfoListBoxMap.get(listBoxKeyFor(info));
                if (box != null) ui.access(() -> box.getDataProvider().refreshItem(info));
            }
        });
    }

    private void notifyMcpServerInfoSelection(McpServerInfo oldMcpServerInfo, McpServerInfo newMcpServerInfo) {
        if (Objects.isNull(newMcpServerInfo)) return;
        this.selectedMcpServerInfo = newMcpServerInfo;
        this.mcpServerInfoListBoxMap.values().stream()
                .filter(box -> !newMcpServerInfo.equals(box.getValue()))
                .forEach(ListBox::clear);
        this.mcpServerInfoChangeSupport.firePropertyChange(MCP_CONNECTION_SELECT_EVENT, oldMcpServerInfo,
                newMcpServerInfo);
        this.persistentUiDataStorage.saveData(McpContentView.LAST_SELECTED_MCP_CONNECTION, newMcpServerInfo);
    }

    public boolean isGhost(McpServerInfo info) {
        return info != null && this.ghostNames.contains(info.serverName());
    }

    public void selectMcpConnectionContent(McpServerInfo targetMcpServerInfo) {
        VaadinUtils.getUi(this).access(() -> {
            ListBox<McpServerInfo> box = this.mcpServerInfoListBoxMap.get(listBoxKeyFor(targetMcpServerInfo));
            if (box == null) return;
            box.getListDataView().getItems()
                    .filter(item -> Objects.equals(item.serverName(), targetMcpServerInfo.serverName()))
                    .findFirst()
                    .ifPresent(box::setValue);
        });
    }

    public void clearSelectConnection() {
        this.mcpServerInfoListBoxMap.values().forEach(ListBox::clear);
    }

    private void deleteSelected() {
        McpServerInfo target = this.selectedMcpServerInfo;
        if (target == null) {
            VaadinUtils.showErrorNotification("No MCP server selected.");
            return;
        }
        McpServerInfo builtin = this.mcpServerInfoService.getDefaultMcpServerInfo();
        if (builtin != null && builtin.serverName().equals(target.serverName())) {
            VaadinUtils.showErrorNotification("Built-in MCP server cannot be deleted.");
            return;
        }
        if (this.ghostNames.contains(target.serverName())) {
            VaadinUtils.showErrorNotification("Inactive (catalog) MCP servers cannot be deleted.");
            return;
        }
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Delete: " + target.serverName());
        dialog.setText("Are you sure you want to delete this MCP server connection permanently?");
        dialog.setCancelable(true);
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            this.mcpClientService.deleteConnectingMcpServer(target);
            this.mcpServerInfoService.deleteMcpServerInfo(target.mcpTransportType(), target.serverName());
            this.mcpServerInfoChangeSupport.firePropertyChange(McpServerView.MCP_CONNECTION_DELETE_EVENT, null, target);
        });
        dialog.open();
    }

}
