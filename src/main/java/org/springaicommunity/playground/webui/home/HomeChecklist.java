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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springaicommunity.playground.webui.vectorstore.VectorStoreView;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.Set;

class HomeChecklist extends Div {

    // Assistant message metadata key written by ChatContentView when an MCP tool
    // trace is captured during a turn. Presence = that chat invoked a tool.
    private static final String MCP_TOOL_PROCESS_MESSAGES_KEY = "mcpToolProcessMessages";

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final ChatHistoryService chatHistoryService;
    private final Environment environment;

    private boolean expanded = true;
    private Button toggleButton;
    private Div itemsContainer;

    HomeChecklist(ObjectProvider<ChatModel> chatModelProvider,
            ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            VectorStoreDocumentService vectorStoreDocumentService,
            ChatHistoryService chatHistoryService,
            Environment environment) {
        this.chatModelProvider = chatModelProvider;
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.chatHistoryService = chatHistoryService;
        this.environment = environment;

        setWidthFull();
        setVisible(false);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        render();
    }

    void applyCollapsedState(boolean collapsed) {
        this.expanded = !collapsed;
        if (itemsContainer != null) {
            itemsContainer.setVisible(expanded);
        }
        if (toggleButton != null) {
            toggleButton.setIcon(expanded
                    ? VaadinIcon.CHEVRON_UP.create()
                    : VaadinIcon.CHEVRON_DOWN.create());
            toggleButton.setAriaLabel(expanded ? "Collapse checklist" : "Expand checklist");
        }
    }

    private void render() {
        List<ChecklistItem> items = buildItems();
        long completed = items.stream().filter(ChecklistItem::done).count();
        if (completed == items.size()) {
            setVisible(false);
            return;
        }

        Div card = new Div();
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.75rem")
                .set("padding", "1.25rem 1.5rem")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background-color", "var(--lumo-base-color)");

        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between");

        Div headerLeft = new Div();
        headerLeft.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "0.6rem");
        H3 title = new H3("Getting started");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "var(--lumo-font-size-m)");
        Span progress = new Span(completed + " of " + items.size() + " complete");
        progress.getStyle()
                .set("padding", "0.15rem 0.55rem")
                .set("border-radius", "999px")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "500");
        headerLeft.add(title, progress);

        this.toggleButton = new Button(
                expanded ? VaadinIcon.CHEVRON_UP.create() : VaadinIcon.CHEVRON_DOWN.create(),
                e -> toggleCollapse());
        this.toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_SMALL);
        this.toggleButton.setAriaLabel(expanded ? "Collapse checklist" : "Expand checklist");

        header.add(headerLeft, this.toggleButton);

        this.itemsContainer = new Div();
        this.itemsContainer.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.25rem")
                .set("margin-top", "0.25rem");
        for (ChecklistItem item : items) {
            this.itemsContainer.add(renderRow(item));
        }
        this.itemsContainer.setVisible(expanded);

        card.add(header, this.itemsContainer);

        removeAll();
        add(card);
        setVisible(true);
    }

    private void toggleCollapse() {
        this.expanded = !this.expanded;
        UI.getCurrent().getPage().executeJs(
                "localStorage.setItem('home_checklist_collapsed', $0);", !this.expanded);
        if (this.itemsContainer != null) {
            this.itemsContainer.setVisible(this.expanded);
        }
        if (this.toggleButton != null) {
            this.toggleButton.setIcon(this.expanded
                    ? VaadinIcon.CHEVRON_UP.create()
                    : VaadinIcon.CHEVRON_DOWN.create());
            this.toggleButton.setAriaLabel(
                    this.expanded ? "Collapse checklist" : "Expand checklist");
        }
    }

    private List<ChecklistItem> buildItems() {
        boolean providerReady = isProviderLikelyReady();
        Set<String> defaultIds = toolSpecPersistenceService.getDefaultToolIds();
        boolean hasUserTool = toolSpecService.getToolSpecList().stream()
                .anyMatch(spec -> !defaultIds.contains(spec.toolId()));
        boolean hasDocument = !vectorStoreDocumentService.getDocumentList().isEmpty();
        List<ChatHistory> histories = chatHistoryService.getChatHistoryList();
        boolean hasChat = !histories.isEmpty();
        boolean hasAgenticExperience = histories.stream().anyMatch(HomeChecklist::hasMcpToolTrace);

        return List.of(
                new ChecklistItem("Configure a model provider",
                        "Ollama reachable or OpenAI key set.",
                        providerReady, null),
                new ChecklistItem("Start a chat",
                        "Built-in tools are ready — try a conversation right away.",
                        hasChat, ChatView.class),
                new ChecklistItem("Upload a document for RAG",
                        "Drop a file into Vector Database to ground your chats.",
                        hasDocument, VectorStoreView.class),
                new ChecklistItem("Create your first tool",
                        "Extend the app with a JavaScript tool of your own.",
                        hasUserTool, ToolStudioView.class),
                new ChecklistItem("Try an agentic workflow",
                        "Ask the assistant: \"Get today's weather and send it to Slack.\"",
                        hasAgenticExperience, ChatView.class)
        );
    }

    private static boolean hasMcpToolTrace(ChatHistory history) {
        try {
            if (history.messagesSupplier() == null) return false;
            List<Message> messages = history.messagesSupplier().get();
            if (messages == null) return false;
            return messages.stream()
                    .filter(m -> m.getMessageType() == MessageType.ASSISTANT)
                    .map(Message::getMetadata)
                    .filter(java.util.Objects::nonNull)
                    .map((Map<String, Object> meta) -> meta.get(MCP_TOOL_PROCESS_MESSAGES_KEY))
                    .anyMatch(trace -> trace instanceof String s && !s.isBlank());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isProviderLikelyReady() {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) return false;
        String className = chatModel.getClass().getSimpleName().toLowerCase();
        if (className.contains("openai")) {
            String apiKey = environment.getProperty("spring.ai.openai.api-key", "");
            return apiKey != null && !apiKey.isBlank();
        }
        return true;
    }

    private static Component renderRow(ChecklistItem item) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("align-items", "flex-start")
                .set("gap", "0.75rem")
                .set("padding", "0.5rem 0.25rem")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("transition", "background-color 0.1s");

        if (item.target() != null) {
            row.getStyle().set("cursor", "pointer");
            row.getElement().addEventListener("mouseenter",
                    e -> row.getStyle().set("background-color", "var(--lumo-contrast-5pct)"));
            row.getElement().addEventListener("mouseleave",
                    e -> row.getStyle().set("background-color", "transparent"));
            row.addClickListener(e -> UI.getCurrent().navigate(item.target()));
        }

        Icon marker = item.done()
                ? VaadinIcon.CHECK_CIRCLE.create()
                : VaadinIcon.CIRCLE_THIN.create();
        marker.getStyle()
                .set("width", "1.25rem")
                .set("height", "1.25rem")
                .set("flex-shrink", "0")
                .set("margin-top", "0.15rem")
                .set("color", item.done()
                        ? "var(--lumo-success-color)"
                        : "var(--lumo-tertiary-text-color)");

        Div text = new Div();
        text.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.15rem");

        Span label = new Span(item.label());
        label.getStyle()
                .set("font-weight", "500")
                .set("color", item.done()
                        ? "var(--lumo-secondary-text-color)"
                        : "var(--lumo-body-text-color)")
                .set("text-decoration", item.done() ? "line-through" : "none");

        Span hint = new Span(item.hint());
        hint.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-tertiary-text-color)");

        text.add(label, hint);
        row.add(marker, text);
        return row;
    }

    private record ChecklistItem(String label, String hint, boolean done,
                                 Class<? extends Component> target) {}
}
