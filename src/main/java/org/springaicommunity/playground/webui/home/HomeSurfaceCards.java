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
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springaicommunity.playground.webui.vectorstore.VectorStoreView;

import java.util.List;
import java.util.Objects;
import java.util.Set;

class HomeSurfaceCards extends Div {

    private static final Logger logger = LoggerFactory.getLogger(HomeSurfaceCards.class);

    private enum StatusTone {NORMAL, MUTED, WARNING}

    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final McpServerInfoService mcpServerInfoService;
    private final VectorStoreDocumentService vectorStoreDocumentService;

    private final Span toolStatus;
    private final Span mcpStatus;
    private final Span vectorStatus;

    HomeSurfaceCards(ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            McpServerInfoService mcpServerInfoService,
            VectorStoreDocumentService vectorStoreDocumentService) {
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.mcpServerInfoService = mcpServerInfoService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;

        this.toolStatus = cardStatusSpan();
        this.mcpStatus = cardStatusSpan();
        this.vectorStatus = cardStatusSpan();

        setWidthFull();
        getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(200px, 1fr))")
                .set("gap", "0.9rem");

        add(
                createSurfaceCard("Tool Studio",
                        "Build and test JavaScript tools",
                        VaadinIcon.TOOLS, ToolStudioView.class, toolStatus),
                createSurfaceCard("MCP Server",
                        "Expose tools and inspect connections",
                        VaadinIcon.TOOLBOX, McpServerView.class, mcpStatus),
                createSurfaceCard("Vector Database",
                        "Upload docs and tune retrieval",
                        VaadinIcon.SEARCH_PLUS, VectorStoreView.class, vectorStatus),
                createSurfaceCard("Agentic Chat",
                        "Run tools inside grounded chat",
                        VaadinIcon.CHAT, ChatView.class, null)
        );
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        refresh();
    }

    private void refresh() {
        try {
            applyToolStatus();
        } catch (Exception e) {
            logger.debug("Tool status query failed", e);
        }
        try {
            applyMcpStatus();
        } catch (Exception e) {
            logger.debug("MCP status query failed", e);
        }
        try {
            applyVectorStatus();
        } catch (Exception e) {
            logger.debug("Vector status query failed", e);
        }
    }

    private void applyToolStatus() {
        Set<String> defaultIds = toolSpecPersistenceService.getDefaultToolIds();
        List<ToolSpec> allTools = toolSpecService.getToolSpecList();
        List<ToolSpec> userTools = allTools.stream()
                .filter(spec -> !defaultIds.contains(spec.toolId()))
                .toList();
        int userCount = userTools.size();
        long needsConfig = userTools.stream().filter(HomeSurfaceCards::hasUnsetStaticVariable).count();

        if (allTools.isEmpty()) {
            setStatus(toolStatus, "No tools yet — create your first", StatusTone.MUTED);
        } else if (userCount == 0) {
            setStatus(toolStatus,
                    allTools.size() + " built-in · add your own",
                    StatusTone.MUTED);
        } else if (needsConfig > 0) {
            setStatus(toolStatus,
                    userCount + " your tool" + plural(userCount) + " · " + needsConfig + " need configuration",
                    StatusTone.WARNING);
        } else {
            setStatus(toolStatus,
                    userCount + " your tool" + plural(userCount) + " ready",
                    StatusTone.NORMAL);
        }
    }

    private void applyMcpStatus() {
        McpServerInfo defaultInfo = mcpServerInfoService.getDefaultMcpServerInfo();
        long external = mcpServerInfoService.getMcpServerInfos().values().stream()
                .flatMap(List::stream)
                .filter(info -> !Objects.equals(info, defaultInfo))
                .count();
        if (external == 0) {
            setStatus(mcpStatus, "Only built-in MCP server running", StatusTone.MUTED);
        } else {
            setStatus(mcpStatus,
                    external + " external server" + plural(external) + " connected",
                    StatusTone.NORMAL);
        }
    }

    private void applyVectorStatus() {
        int count = vectorStoreDocumentService.getDocumentList().size();
        if (count == 0) {
            setStatus(vectorStatus, "No documents indexed yet", StatusTone.MUTED);
        } else {
            setStatus(vectorStatus,
                    count + " document" + plural(count) + " indexed",
                    StatusTone.NORMAL);
        }
    }

    private static boolean hasUnsetStaticVariable(ToolSpec spec) {
        if (spec.staticVariables() == null) return false;
        return spec.staticVariables().stream()
                .anyMatch(entry -> entry.getValue() == null || entry.getValue().isBlank());
    }

    private static Component createSurfaceCard(String title, String description,
            VaadinIcon icon, Class<? extends Component> target, Span status) {
        Div card = new Div();
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.5rem")
                .set("padding", "1rem 1.1rem")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background-color", "var(--lumo-base-color)")
                .set("cursor", "pointer")
                .set("transition", "transform 0.15s ease, border-color 0.15s, box-shadow 0.15s");

        card.getElement().addEventListener("mouseenter", e -> card.getStyle()
                .set("border-color", "var(--lumo-primary-color-50pct)")
                .set("transform", "translateY(-2px)")
                .set("box-shadow", "var(--lumo-box-shadow-s)"));
        card.getElement().addEventListener("mouseleave", e -> card.getStyle()
                .set("border-color", "var(--lumo-contrast-10pct)")
                .set("transform", "translateY(0)")
                .set("box-shadow", "none"));
        card.addClickListener(e -> UI.getCurrent().navigate(target));

        Div iconBadge = new Div();
        iconBadge.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "2.2rem")
                .set("height", "2.2rem")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background-color", "var(--lumo-primary-color-10pct)");
        Icon cardIcon = icon.create();
        cardIcon.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-primary-color)");
        iconBadge.add(cardIcon);

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-m)");

        Span descriptionSpan = new Span(description);
        descriptionSpan.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("line-height", "1.35");

        card.add(iconBadge, titleSpan, descriptionSpan);
        if (status != null) {
            card.add(status);
        }
        return card;
    }

    private static Span cardStatusSpan() {
        Span span = new Span("");
        span.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-top", "0.25rem")
                .set("padding-top", "0.55rem")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
        return span;
    }

    private static String plural(long count) {
        return count == 1 ? "" : "s";
    }

    private static void setStatus(Span span, String text, StatusTone tone) {
        span.setText(text);
        span.getStyle().set("color", switch (tone) {
            case WARNING -> "var(--lumo-warning-text-color)";
            case NORMAL -> "var(--lumo-body-text-color)";
            case MUTED -> "var(--lumo-secondary-text-color)";
        });
    }
}
