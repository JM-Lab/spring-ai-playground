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
package org.springaicommunity.playground.webui.home;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springaicommunity.playground.webui.vectorstore.VectorStoreView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.springaicommunity.playground.webui.home.HomeUi.relativeTime;

class HomeRecentActivity extends Div {

    private static final int RECENT_ITEM_LIMIT = 5;

    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final McpServerInfoService mcpServerInfoService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final ChatHistoryService chatHistoryService;

    HomeRecentActivity(ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            McpServerInfoService mcpServerInfoService,
            VectorStoreDocumentService vectorStoreDocumentService,
            ChatHistoryService chatHistoryService) {
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.mcpServerInfoService = mcpServerInfoService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.chatHistoryService = chatHistoryService;

        setWidthFull();
        setVisible(false);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        render();
    }

    private void render() {
        List<RecentItem> items = collect();
        if (items.isEmpty()) {
            setVisible(false);
            return;
        }

        List<RecentItem> sorted = items.stream()
                .sorted(Comparator.comparingLong(RecentItem::updateTimestamp).reversed())
                .limit(RECENT_ITEM_LIMIT)
                .toList();

        Div section = new Div();
        section.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.5rem");

        H3 header = new H3("Recently updated");
        header.getStyle()
                .set("margin", "0 0 0.25rem 0")
                .set("font-size", "var(--lumo-font-size-m)");

        Div list = new Div();
        list.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("overflow", "hidden")
                .set("background-color", "var(--lumo-base-color)");

        for (int i = 0; i < sorted.size(); i++) {
            list.add(renderRow(sorted.get(i), i < sorted.size() - 1));
        }

        section.add(header, list);
        removeAll();
        add(section);
        setVisible(true);
    }

    private List<RecentItem> collect() {
        List<RecentItem> items = new ArrayList<>();
        Set<String> defaultIds = toolSpecPersistenceService.getDefaultToolIds();

        toolSpecService.getToolSpecList().stream()
                .filter(spec -> !defaultIds.contains(spec.toolId()))
                .forEach(spec -> items.add(new RecentItem(spec.name(), VaadinIcon.TOOLS,
                        spec.updateTimestamp(), ToolStudioView.class, "Tool")));

        McpServerInfo defaultMcp = mcpServerInfoService.getDefaultMcpServerInfo();
        mcpServerInfoService.getMcpServerInfos().values().stream()
                .flatMap(List::stream)
                .filter(info -> !Objects.equals(info, defaultMcp))
                .forEach(info -> items.add(new RecentItem(info.serverName(), VaadinIcon.TOOLBOX,
                        info.updateTimestamp(), McpServerView.class, "MCP")));

        vectorStoreDocumentService.getDocumentList().forEach(doc ->
                items.add(new RecentItem(doc.title(), VaadinIcon.FILE_TEXT_O, doc.updateTimestamp(),
                        VectorStoreView.class, "Document")));

        for (ChatHistory history : chatHistoryService.getChatHistoryList()) {
            String title = (history.title() == null || history.title().isBlank())
                    ? "Untitled chat" : history.title();
            items.add(new RecentItem(title, VaadinIcon.CHAT, history.updateTimestamp(),
                    ChatView.class, "Chat"));
        }
        return items;
    }

    private static Component renderRow(RecentItem item, boolean drawDivider) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "0.75rem")
                .set("padding", "0.65rem 1rem")
                .set("cursor", "pointer")
                .set("transition", "background-color 0.1s");
        if (drawDivider) {
            row.getStyle().set("border-bottom", "1px solid var(--lumo-contrast-5pct)");
        }
        row.getElement().addEventListener("mouseenter",
                e -> row.getStyle().set("background-color", "var(--lumo-contrast-5pct)"));
        row.getElement().addEventListener("mouseleave",
                e -> row.getStyle().set("background-color", "transparent"));
        row.addClickListener(e -> UI.getCurrent().navigate(item.target()));

        Icon icon = item.icon().create();
        icon.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-primary-color)")
                .set("flex-shrink", "0");

        Span name = new Span(item.label());
        name.getStyle()
                .set("font-weight", "500")
                .set("flex", "1 1 auto")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        Span kind = new Span(item.kind());
        kind.getStyle()
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("padding", "0.1rem 0.5rem")
                .set("border-radius", "999px")
                .set("background-color", "var(--lumo-contrast-5pct)");

        Span time = new Span(relativeTime(item.updateTimestamp()));
        time.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("flex-shrink", "0");

        row.add(icon, name, kind, time);
        return row;
    }

    private record RecentItem(String label, VaadinIcon icon, long updateTimestamp,
                              Class<? extends Component> target, String kind) {}
}
