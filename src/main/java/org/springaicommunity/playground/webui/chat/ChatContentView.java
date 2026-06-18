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

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.ScrollIntoViewOption;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.internal.Pair;
import org.springaicommunity.playground.service.SpringAiPlaygroundRagAdvisor;
import org.springaicommunity.playground.service.chat.ChatExportService;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryPersistenceService;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatProvider;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.chat.ChatToolPreferences;
import org.springaicommunity.playground.service.chat.ReasoningEffort;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.McpToolCallingManager;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.webui.SttMicButton;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.tool.ExposedToolsSelector;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import reactor.core.Disposable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@JsModule("./playground/chat-stt.js")
public class ChatContentView extends VerticalLayout {

    private static final ScrollIntoViewOption[] DefaultScrollOptions =
            {ScrollIntoViewOption.Block.END, ScrollIntoViewOption.Inline.NEAREST};

    private static final int PROMPT_TOP_MARGIN_PX = 20;

    private final VerticalLayout messageListLayout;
    private final Scroller messageScroller;
    private final com.vaadin.flow.component.html.Div scrollSpacer;
    private final TextArea userPromptTextArea;
    private final MultiSelectComboBox<VectorStoreDocumentInfo> documentsComboBox;
    private final MultiSelectComboBox<McpServerInfo> mcpToolProviderComboBox;
    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private ChatHistory chatHistory;
    private final McpClientService mcpClientService;
    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final ToolActivationCalculator toolActivationCalculator;
    private final McpServerInfoService mcpServerInfoService;
    private final ChatExportService chatExportService;
    private final MultiSelectComboBox<ToolSpec> customToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> builtinToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> composedToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> exposedToolsDisplayBox;
    private final Select<ReasoningEffort> reasoningSelect = new Select<>();
    private Button submitButton;
    private Button micButton;
    private final Checkbox useBuiltinMcpCheckbox = new Checkbox("Use built-in MCP server in this chat");
    private Disposable currentStream;

    public ChatContentView(ChatService chatService,
            ChatHistoryService chatHistoryService, ChatHistory chatHistory,
            Consumer<ChatHistory> completeChatHistoryConsumer,
            McpClientService mcpClientService, ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ToolActivationCalculator toolActivationCalculator,
            McpServerInfoService mcpServerInfoService, ChatExportService chatExportService) {
        this.chatHistory = chatHistory;
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
        this.completeChatHistoryConsumer = completeChatHistoryConsumer;
        this.mcpClientService = mcpClientService;
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.toolActivationCalculator = toolActivationCalculator;
        this.mcpServerInfoService = mcpServerInfoService;
        this.chatExportService = chatExportService;
        this.customToolsComboBox = ExposedToolsSelector.newCustomSelector(
                toolSpecService::riskLevelOf, toolSpecService::categoryOf);
        this.builtinToolsComboBox = ExposedToolsSelector.newBuiltinSelector(
                toolSpecService::riskLevelOf, toolSpecService::categoryOf);
        this.composedToolsComboBox = ExposedToolsSelector.newComposedSelector(
                toolSpecService::riskLevelOf, toolSpecService::categoryOf);
        this.customToolsComboBox.setWidthFull();
        this.builtinToolsComboBox.setWidthFull();
        this.composedToolsComboBox.setWidthFull();
        this.customToolsComboBox.setSelectedItemsOnTop(true);
        this.builtinToolsComboBox.setSelectedItemsOnTop(true);
        this.composedToolsComboBox.setSelectedItemsOnTop(true);

        this.exposedToolsDisplayBox = new MultiSelectComboBox<>();
        this.exposedToolsDisplayBox.setPlaceholder("Built-in MCP off — click to enable");
        this.exposedToolsDisplayBox.setWidth("300px");
        this.exposedToolsDisplayBox.setReadOnly(true);
        this.exposedToolsDisplayBox.setAutoOpen(false);
        this.exposedToolsDisplayBox.setItemLabelGenerator(ToolSpec::name);
        this.exposedToolsDisplayBox.setTooltipText("Built-in tools used in this chat — click to edit");
        this.exposedToolsDisplayBox.setSelectedItemsOnTop(true);
        this.exposedToolsDisplayBox.addClassName("exposed-tools-display");
        this.exposedToolsDisplayBox.getElement().executeJs(
                "const input = this.querySelector('input');"
                + " if (input) input.addEventListener('mousedown', e => e.preventDefault(), true);"
                + " if (!document.getElementById('exposed-tools-display-style')) {"
                + "   const s = document.createElement('style');"
                + "   s.id = 'exposed-tools-display-style';"
                + "   s.textContent = ''"
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display { max-width: 300px !important; cursor: pointer; }'"
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display::part(input-field) { background: var(--lumo-contrast-10pct) !important; }'"
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display::part(input-field)::after { border: none !important; }'"
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display[readonly]:not([has-value]) input { opacity: 1 !important; width: auto !important; flex: 1 1 auto !important; min-width: 8em !important; }';"
                + "   document.head.appendChild(s);"
                + " }");

        this.messageListLayout = new VerticalLayout();
        this.messageListLayout.setMargin(false);
        this.messageListLayout.setSpacing(false);
        this.messageListLayout.setPadding(false);

        this.scrollSpacer = new com.vaadin.flow.component.html.Div();
        this.scrollSpacer.addClassName("chat-scroll-spacer");
        this.scrollSpacer.getStyle().set("flex-shrink", "0");
        this.messageListLayout.add(this.scrollSpacer);

        this.messageScroller = new Scroller(this.messageListLayout);
        this.messageScroller.setSizeFull();
        this.messageScroller.getStyle().set("overflow-anchor", "none");

        this.mcpToolProviderComboBox = new MultiSelectComboBox<>();
        this.mcpToolProviderComboBox.setPlaceholder("No MCP Connections for Tools");
        this.mcpToolProviderComboBox.setWidth("300px");
        this.mcpToolProviderComboBox.setTooltipText("Access Tools via external MCP connections");
        this.mcpToolProviderComboBox.setSelectedItemsOnTop(true);
        this.mcpToolProviderComboBox.setItemLabelGenerator(
                mcpServerInfo -> mcpServerInfo.serverName() + "(" + mcpServerInfo.mcpTransportType() + ")");
        this.mcpToolProviderComboBox.setItems(externalMcpServerInfos());
        this.mcpToolProviderComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) persistToolPreferences();
        });

        this.documentsComboBox = new MultiSelectComboBox<>();
        this.documentsComboBox.setPlaceholder("No documents for RAG");
        this.documentsComboBox.setWidth("300px");
        this.documentsComboBox.setTooltipText("RAG with documents stored in VectorDB.");
        this.documentsComboBox.setSelectedItemsOnTop(true);
        this.documentsComboBox.setItemLabelGenerator(VectorStoreDocumentInfo::title);
        this.documentsComboBox.setItems(this.chatService.getExistDocumentInfoList());
        this.documentsComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) persistToolPreferences();
        });

        this.userPromptTextArea = new TextArea();
        this.userPromptTextArea.setPlaceholder("Ask Spring AI Playground");
        this.userPromptTextArea.setWidthFull();
        this.userPromptTextArea.setAutofocus(true);
        this.userPromptTextArea.focus();
        this.userPromptTextArea.setMinRows(2);
        this.userPromptTextArea.setMaxRows(5);
        this.userPromptTextArea.setValueChangeMode(ValueChangeMode.EAGER);
        this.userPromptTextArea.setClearButtonVisible(true);
        CompletableFuture<ZoneId> zoneIdFuture = VaadinUtils.buildClientZoneIdFuture(new CompletableFuture<>());
        this.userPromptTextArea.setId("sttTextArea");

        String savedProvider = this.chatHistory.provider();
        String currentProvider = this.chatService.getChatModelProvider();
        final boolean providerMismatch = savedProvider != null && !savedProvider.isBlank()
                && !savedProvider.equalsIgnoreCase(currentProvider);

        this.micButton = new SttMicButton(this.userPromptTextArea);
        Icon submitIcon = VaadinUtils.styledLargeIcon(VaadinIcon.ARROW_CIRCLE_UP.create());
        this.submitButton = new Button(submitIcon);
        submitButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        submitButton.setTooltipText("Submit");

        submitButton.addClickListener(e -> {
            if (providerMismatch) return;
            if (this.currentStream != null && !this.currentStream.isDisposed()) {
                this.currentStream.dispose();
                this.currentStream = null;
                submitButton.setIcon(submitIcon);
                this.userPromptTextArea.setReadOnly(false);
                micButton.setEnabled(true);
                return;
            }
            this.userPromptTextArea.getElement().executeJs("return this.value;").then(String.class, userPrompt -> {
                if (userPrompt.isBlank())
                    return;
                this.userPromptTextArea.getElement().executeJs("this.value='';");
                this.userPromptTextArea.clear();
                this.userPromptTextArea.setReadOnly(true);
                micButton.setEnabled(false);
                Icon stopIcon = VaadinUtils.styledLargeIcon(VaadinIcon.STOP.create());
                submitButton.setIcon(stopIcon);
                submitButton.setTooltipText("Stop");
                this.currentStream = inputEvent(zoneIdFuture, userPrompt);
            });
        });

        this.userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT))
                submitButton.click();
        });

        HorizontalLayout suffix = new HorizontalLayout(micButton, submitButton);
        suffix.setSpacing(false);
        suffix.setPadding(false);
        this.userPromptTextArea.setSuffixComponent(suffix);

        Icon ragIcon = VaadinUtils.styledLargeIcon(VaadinIcon.SEARCH_PLUS.create());
        ragIcon.setTooltipText("Select documents in VectorDB");
        ragIcon.addSingleClickListener(event -> this.documentsComboBox.setOpened(true));
        ragIcon.getStyle().set("margin-right", "0px");
        Icon toolIcon = VaadinUtils.styledLargeIcon(VaadinIcon.TOOLBOX.create());
        toolIcon.setTooltipText("Select documents in VectorDB");
        toolIcon.getStyle().set("margin-right", "0px");
        toolIcon.addSingleClickListener(event -> this.mcpToolProviderComboBox.setOpened(true));

        HorizontalLayout toolLayout = new HorizontalLayout(toolIcon, this.mcpToolProviderComboBox);
        toolLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        toolLayout.setSpacing(false);
        toolLayout.getStyle().set("gap", "5px");

        HorizontalLayout ragLayout = new HorizontalLayout(ragIcon, this.documentsComboBox);
        ragLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        ragLayout.setSpacing(false);
        ragLayout.getStyle().set("gap", "5px");


        populateExposedToolsCombos();
        this.customToolsComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });
        this.builtinToolsComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });
        this.composedToolsComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });

        Icon toolStudioIcon = VaadinUtils.styledLargeIcon(VaadinIcon.TOOLS.create());
        toolStudioIcon.setTooltipText("Built-in tools used in this chat");
        toolStudioIcon.getStyle().set("margin-right", "0px");

        this.useBuiltinMcpCheckbox.setValue(this.chatHistory.toolPreferences().useBuiltinMcp());
        this.useBuiltinMcpCheckbox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });

        VerticalLayout exposedToolsPopoverBody = new VerticalLayout(
                this.useBuiltinMcpCheckbox, this.customToolsComboBox, this.builtinToolsComboBox,
                this.composedToolsComboBox);
        exposedToolsPopoverBody.setPadding(true);
        exposedToolsPopoverBody.setSpacing(true);
        exposedToolsPopoverBody.setWidth("380px");

        Popover exposedToolsPopover = new Popover();
        exposedToolsPopover.setTarget(this.exposedToolsDisplayBox);
        exposedToolsPopover.setPosition(PopoverPosition.TOP);
        exposedToolsPopover.setOpenOnClick(true);
        exposedToolsPopover.add(exposedToolsPopoverBody);

        toolStudioIcon.addSingleClickListener(event -> exposedToolsPopover.setOpened(true));

        HorizontalLayout exposedToolsLayout = new HorizontalLayout(
                toolStudioIcon, this.exposedToolsDisplayBox);
        exposedToolsLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        exposedToolsLayout.setSpacing(false);
        exposedToolsLayout.getStyle().set("gap", "5px");
        exposedToolsLayout.add(exposedToolsPopover);

        ChatProvider chatProvider = this.chatService.getChatProvider();
        this.reasoningSelect.setItems(ReasoningEffort.values());
        this.reasoningSelect.setItemLabelGenerator(ChatContentView::reasoningLabel);
        this.reasoningSelect.setValue(this.chatHistory.toolPreferences().reasoningEffort());
        this.reasoningSelect.setVisible(chatProvider.supportsReasoning());
        this.reasoningSelect.setTooltipText(chatProvider.reasoningLabel());
        this.reasoningSelect.setWidth("150px");
        this.reasoningSelect.setPrefixComponent(VaadinIcon.LIGHTBULB.create());
        this.reasoningSelect.addValueChangeListener(event -> {
            if (event.isFromClient() && Objects.nonNull(event.getValue()))
                persistToolPreferences();
        });

        HorizontalLayout userInputMenuLayout = new HorizontalLayout(
                this.reasoningSelect, exposedToolsLayout, toolLayout, ragLayout);
        userInputMenuLayout.getStyle().set("flex-wrap", "wrap");

        VerticalLayout userInputLayout = new VerticalLayout(userInputMenuLayout, this.userPromptTextArea);
        userInputLayout.setWidthFull();
        userInputLayout.setMargin(false);
        userInputLayout.setSpacing(false);
        userInputLayout.setPadding(false);
        add(messageScroller, userInputLayout);

        if (providerMismatch) {
            this.userPromptTextArea.setReadOnly(true);
            this.userPromptTextArea.setEnabled(false);
            submitButton.setEnabled(false);
            micButton.setEnabled(false);
            Span providerLockBanner = new Span("This conversation was created with " + savedProvider
                    + " but the app is now running " + currentProvider
                    + ". It is read-only - start a new chat to continue.");
            providerLockBanner.getStyle().set("display", "block")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                    .set("margin-bottom", "var(--lumo-space-xs)")
                    .set("border-radius", "var(--lumo-border-radius-m)")
                    .set("background-color", "var(--lumo-error-color-10pct)")
                    .set("color", "var(--lumo-error-text-color)")
                    .set("font-size", "var(--lumo-font-size-s)");
            userInputLayout.addComponentAsFirst(providerLockBanner);
        }
        setSizeFull();
        setMargin(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");

        List<Message> messages = this.chatHistory.messagesSupplier().get();
        if (messages.isEmpty())
            return;
        ChatContentManager chatContentManager = new ChatContentManager(null, null, zoneIdFuture,
                this.chatHistory);
        this.messageListLayout.remove(this.scrollSpacer);
        messages.forEach(message -> chatContentManager.initMarkdownMessage(this.messageListLayout, message,
                message.getMessageType()));
        this.messageListLayout.add(this.scrollSpacer);

        this.messageListLayout.getChildren().filter(c -> c != this.scrollSpacer).reduce((a, b) -> b)
                .ifPresent(last -> last.scrollIntoView(DefaultScrollOptions));
        ChatToolPreferences preferences = this.chatHistory.toolPreferences();
        List<String> ragDocInfoIds = preferences.ragDocInfoIds();
        if (!ragDocInfoIds.isEmpty()) {
            this.documentsComboBox.select(this.chatService.getExistDocumentInfoList().stream()
                    .filter(vectorStoreDocumentInfo -> ragDocInfoIds.contains(
                            vectorStoreDocumentInfo.docInfoId())).toList());
        }
        Map<McpTransportType, List<String>> mcpServerNames = preferences.mcpServerNames();
        if (!mcpServerNames.isEmpty()) {
            List<McpServerInfo> mcpServerInfos = externalMcpServerInfos().stream()
                    .filter(mcpServerInfo -> Optional.ofNullable(mcpServerNames.get(mcpServerInfo.mcpTransportType()))
                            .filter(serverNames -> serverNames.contains(mcpServerInfo.serverName()))
                            .isPresent()).toList();
            this.mcpToolProviderComboBox.select(mcpServerInfos);
        }
        applyStoredChatToolSelection();
    }

    private List<McpServerInfo> externalMcpServerInfos() {
        McpServerInfo builtin = this.mcpServerInfoService.getDefaultMcpServerInfo();
        String builtinName = builtin == null ? null : builtin.serverName();
        return this.chatService.getLiveMcpServerInfos().stream()
                .filter(info -> builtinName == null || !builtinName.equals(info.serverName()))
                .toList();
    }

    private void populateExposedToolsCombos() {
        Set<String> defaultIds = this.toolSpecPersistenceService.getDefaultToolIds();
        List<ToolSpec> all = this.toolSpecService.getToolSpecList();
        Set<String> exposedIds = this.toolSpecService.getToolMcpServerSetting().exposedToolIds();

        List<ToolSpec> exposedCustoms = ExposedToolsSelector.customsFrom(all, defaultIds).stream()
                .filter(spec -> exposedIds.contains(spec.toolId())).toList();
        List<ToolSpec> exposedBuiltins = ExposedToolsSelector
                .exposableBuiltinsFrom(all, defaultIds, this.toolActivationCalculator).stream()
                .filter(spec -> exposedIds.contains(spec.toolId())).toList();
        List<ToolSpec> exposedComposed = this.toolSpecService.getExternalToolSpecs();
        // setItems clears the selection, so carry the user's current picks across the refresh; only a chat
        // with no picks at all (first open, nothing saved) defaults to everything exposed.
        Set<String> previouslySelected = new LinkedHashSet<>();
        this.customToolsComboBox.getSelectedItems().forEach(spec -> previouslySelected.add(spec.toolId()));
        this.builtinToolsComboBox.getSelectedItems().forEach(spec -> previouslySelected.add(spec.toolId()));
        this.composedToolsComboBox.getSelectedItems().forEach(spec -> previouslySelected.add(spec.toolId()));
        this.customToolsComboBox.setItems(exposedCustoms);
        this.builtinToolsComboBox.setItems(exposedBuiltins);
        this.composedToolsComboBox.setItems(exposedComposed);

        ExposedToolsSelector.applyEmptyState(this.customToolsComboBox, exposedCustoms.isEmpty(),
                "No custom tools exposed", "Custom tools for this chat");
        ExposedToolsSelector.applyEmptyState(this.builtinToolsComboBox, exposedBuiltins.isEmpty(),
                "No built-in tools exposed", "Built-in tools for this chat");
        ExposedToolsSelector.applyEmptyState(this.composedToolsComboBox, exposedComposed.isEmpty(),
                "No external tools re-exposed", "Composed external tools for this chat");

        if (previouslySelected.isEmpty()) {
            exposedCustoms.forEach(this.customToolsComboBox::select);
            exposedBuiltins.forEach(this.builtinToolsComboBox::select);
        } else {
            selectByToolIds(this.customToolsComboBox, previouslySelected);
            selectByToolIds(this.builtinToolsComboBox, previouslySelected);
            selectByToolIds(this.composedToolsComboBox, previouslySelected);
        }

        List<ToolSpec> displayItems = new ArrayList<>(exposedCustoms);
        displayItems.addAll(exposedBuiltins);
        this.exposedToolsDisplayBox.setItems(displayItems);
        refreshExposedToolsDisplay();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        // Tool exposure (custom, built-in, re-exposed external) is edited in the cog drawer without notifying
        // chat, so re-read all three combos and restore the saved per-chat selection each time the chat is
        // shown. Otherwise newly exposed tools (and their HITL marking) never appear.
        populateExposedToolsCombos();
        applyStoredChatToolSelection();
    }

    private void applyStoredChatToolSelection() {
        Set<String> selectedToolIds = this.chatHistory.toolPreferences().exposedToolIds();
        this.customToolsComboBox.deselectAll();
        this.builtinToolsComboBox.deselectAll();
        this.composedToolsComboBox.deselectAll();
        selectByToolIds(this.customToolsComboBox, selectedToolIds);
        selectByToolIds(this.builtinToolsComboBox, selectedToolIds);
        selectByToolIds(this.composedToolsComboBox, selectedToolIds);
        refreshExposedToolsDisplay();
    }

    private static void selectByToolIds(MultiSelectComboBox<ToolSpec> combo, Set<String> toolIds) {
        combo.getListDataView().getItems()
                .filter(spec -> toolIds.contains(spec.toolId())).toList()
                .forEach(combo::select);
    }

    private void refreshExposedToolsDisplay() {
        boolean builtinEnabled = this.useBuiltinMcpCheckbox.getValue();
        this.exposedToolsDisplayBox.setReadOnly(false);
        this.exposedToolsDisplayBox.deselectAll();
        String placeholder;
        if (builtinEnabled) {
            Set<ToolSpec> combined = new LinkedHashSet<>();
            combined.addAll(this.customToolsComboBox.getSelectedItems());
            combined.addAll(this.builtinToolsComboBox.getSelectedItems());
            combined.addAll(this.composedToolsComboBox.getSelectedItems());
            combined.forEach(this.exposedToolsDisplayBox::select);
            placeholder = combined.isEmpty() ? "No tools selected" : "";
        } else {
            placeholder = "Built-in MCP off";
        }
        this.exposedToolsDisplayBox.setPlaceholder(placeholder);
        this.exposedToolsDisplayBox.setReadOnly(true);
    }

    // Snapshot the current input-bar selections and persist them onto this conversation. Only a chat that is
    // already registered (has been sent to at least once) is saved immediately; an empty new chat keeps them in
    // memory and they ride along when the first send registers it via completeChatHistoryConsumer.
    private void persistToolPreferences() {
        this.chatHistory = this.chatHistory.withToolPreferences(currentToolPreferences());
        if (Objects.nonNull(this.chatHistoryService.getChatHistory(this.chatHistory.conversationId()))) {
            this.chatHistoryService.updateChatHistory(this.chatHistory);
        }
    }

    private ChatToolPreferences currentToolPreferences() {
        Set<String> exposedToolIds = new LinkedHashSet<>();
        this.customToolsComboBox.getSelectedItems().forEach(spec -> exposedToolIds.add(spec.toolId()));
        this.builtinToolsComboBox.getSelectedItems().forEach(spec -> exposedToolIds.add(spec.toolId()));
        this.composedToolsComboBox.getSelectedItems().forEach(spec -> exposedToolIds.add(spec.toolId()));
        List<String> ragDocInfoIds = this.documentsComboBox.getSelectedItems().stream()
                .map(VectorStoreDocumentInfo::docInfoId).toList();
        Map<McpTransportType, List<String>> mcpServerNames = this.mcpToolProviderComboBox.getSelectedItems().stream()
                .collect(Collectors.groupingBy(McpServerInfo::mcpTransportType,
                        Collectors.mapping(McpServerInfo::serverName, Collectors.toList())));
        ReasoningEffort reasoning = Objects.requireNonNullElse(this.reasoningSelect.getValue(), ReasoningEffort.OFF);
        return new ChatToolPreferences(this.useBuiltinMcpCheckbox.getValue(), exposedToolIds, ragDocInfoIds,
                mcpServerNames, reasoning);
    }

    private Set<String> selectedChatToolNames() {
        Set<String> names = new LinkedHashSet<>();
        this.customToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        this.builtinToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        this.composedToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        return names;
    }

    private Disposable inputEvent(CompletableFuture<ZoneId> zoneIdFuture, String userPrompt) {
        // Bake the current input-bar selections onto this conversation and register it before streaming: a
        // stream that dies mid-flight (push drop, error, JVM kill) must not take the conversation with it.
        // The async save usually already sees the user turn; completion-time saves catch up regardless.
        this.chatHistory = this.chatHistory.withToolPreferences(currentToolPreferences());
        this.chatHistoryService.updateChatHistory(this.chatHistory);
        ChatContentManager chatContentManager = new ChatContentManager(this.messageListLayout, userPrompt, zoneIdFuture,
                this.chatHistory);

        List<String> selectedDocInfoIds =
                this.documentsComboBox.getSelectedItems().stream().map(VectorStoreDocumentInfo::docInfoId).toList();
        Set<McpServerInfo> selectedItems = this.mcpToolProviderComboBox.getSelectedItems();
        UI ui = VaadinUtils.getUi(this);
        List<ToolCallback> toolCallbacks = new ArrayList<>(selectedItems.stream()
                .map(this.mcpClientService::buildToolCallbackProviders).flatMap(List::stream)
                .map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream).toList());
        if (this.useBuiltinMcpCheckbox.getValue()) {
            McpServerInfo builtin = this.mcpServerInfoService.getDefaultMcpServerInfo();
            if (builtin != null) {
                Set<String> chatToolNames = selectedChatToolNames();
                this.mcpClientService.buildToolCallbackProviders(builtin).stream()
                        .map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream)
                        .filter(cb -> cb.getToolDefinition() != null
                                && chatToolNames.contains(cb.getToolDefinition().name()))
                        .forEach(toolCallbacks::add);
            }
        }

        // The send-time save races the memory advisor, so it may snapshot an empty conversation; the first
        // model activity proves the user turn reached chat memory and re-saves it once, covering streams
        // that wedge or die before any terminal signal.
        AtomicBoolean liveSaved = new AtomicBoolean();
        Runnable saveOnFirstActivity = () -> {
            if (liveSaved.compareAndSet(false, true))
                this.chatHistoryService.updateChatHistory(this.chatHistory);
        };
        return this.chatService.stream(this.chatHistory, userPrompt,
                        this.chatService.buildFilterExpression(selectedDocInfoIds), this.completeChatHistoryConsumer,
                        toolCallbacks, o -> {
                            saveOnFirstActivity.run();
                            ui.access(() -> chatContentManager.appendMcpToolProcessMessage(o));
                        },
                        o -> ui.access(() -> chatContentManager.appendRagProcessMessage(o)),
                        o -> {
                            saveOnFirstActivity.run();
                            ui.access(() -> chatContentManager.appendBotThinkProcessMessage(o));
                        },
                        round -> ui.access(() -> chatContentManager.applyRoundUsage(round)),
                        signalType -> ui.access(() -> {
                            if (reactor.core.publisher.SignalType.CANCEL.equals(signalType))
                                chatContentManager.markStopped();
                            doFinally(chatContentManager);
                        }), new ChatHumanQuestionHandler(ui), this.reasoningSelect.getValue())
                .doOnError(throwable -> ui.access(() -> {
                    chatContentManager.markError(throwable);
                    VaadinUtils.showErrorNotification(throwable.getMessage());
                    doFinally(chatContentManager);
                }))
                .subscribe(content -> {
                    saveOnFirstActivity.run();
                    ui.access(() -> chatContentManager.append(content));
                });
    }

    private void doFinally(ChatContentManager chatContentManager) {
        chatContentManager.doFinally();
        this.userPromptTextArea.setReadOnly(false);
        this.userPromptTextArea.setEnabled(true);
        // normal completion must reset these too, else the button stays on stop and the mic stays disabled.
        this.submitButton.setIcon(VaadinUtils.styledLargeIcon(VaadinIcon.ARROW_CIRCLE_UP.create()));
        this.submitButton.setTooltipText("Submit");
        this.micButton.setEnabled(true);
        if (this.currentStream != null) {
            this.currentStream.dispose();
            this.currentStream = null;
        }
        pinAfterStream(chatContentManager.userMessage);
        this.userPromptTextArea.focus();
    }

    private void pinAfterStream(ChatMessage userMessage) {
        if (Objects.isNull(userMessage)) {
            this.messageScroller.scrollToBottom();
            return;
        }
        this.messageScroller.getElement().executeJs("""
                const s = this;
                const list = s.firstElementChild;
                const userMsg = $0;
                const spacer = $1;
                const margin = $2;
                const applyPin = () => {
                    if (!userMsg.isConnected) return false;
                    const lastReal = Array.from(list.children).reverse()
                            .find(c => c !== spacer);
                    const userTarget = Math.max(0, userMsg.offsetTop - margin);
                    const lastEnd = lastReal
                            ? (lastReal.offsetTop + lastReal.offsetHeight)
                            : (userMsg.offsetTop + userMsg.offsetHeight);
                    const overflowTarget = Math.max(0, lastEnd - s.clientHeight);
                    const target = Math.max(userTarget, overflowTarget);
                    const currentSpacerH = parseInt(spacer.style.height) || 0;
                    const contentNoSpacer = list.scrollHeight - currentSpacerH;
                    const requiredSpacer = Math.max(0, target + s.clientHeight - contentNoSpacer);
                    spacer.style.height = requiredSpacer + 'px';
                    s.scrollTop = target;
                    return true;
                };
                if (!applyPin()) requestAnimationFrame(applyPin);
                """, userMessage.getElement(), this.scrollSpacer.getElement(), PROMPT_TOP_MARGIN_PX);
    }

    public ChatOptions getChatOption() {
        return this.chatHistory.chatOptions();
    }

    public ChatExtraOptions getExtraOptions() {
        return this.chatHistory.extraOptions();
    }

    public String getSystemPrompt() {
        return this.chatHistory.systemPrompt();
    }

    public String getConversationId() {
        return this.chatHistory.conversationId();
    }

    private static String reasoningLabel(ReasoningEffort effort) {
        return switch (effort) {
            case OFF -> "Off";
            case LOW -> "Low";
            case MEDIUM -> "Medium";
            case HIGH -> "High";
        };
    }

    public void exportConversation(String format) {
        String base = "chat-" + getConversationId();
        switch (format) {
            case "md" -> triggerDownload(base + ".md", "text/markdown",
                    this.chatExportService.conversationToMarkdown(this.chatHistory));
            case "txt" -> triggerDownload(base + ".txt", "text/plain",
                    this.chatExportService.conversationToPlainText(this.chatHistory));
            case "json" -> triggerDownload(base + ".json", "application/json",
                    this.chatExportService.conversationToJson(this.chatHistory));
            case "pdf" -> getElement().executeJs(
                    "window.Saip && window.Saip.chatMarkdown && window.Saip.chatMarkdown.printContainer(this)");
            default -> { }
        }
    }

    // Client-side download: build a Blob from the content and click a transient anchor. No server round-trip,
    // nothing leaves the device. Shared by per-message and whole-conversation Export.
    private void triggerDownload(String filename, String mime, String content) {
        getElement().executeJs("""
                const blob = new Blob([$1], {type: $2});
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = $0;
                document.body.appendChild(link);
                link.click();
                link.remove();
                URL.revokeObjectURL(url);
                """, filename, content, mime);
    }

    private final class ChatContentManager {
        private static final String RAG_PROCESS = "RAG DOCUMENTS";
        private static final String RAG_PROCESS_TIMESTAMP = "ragProcessTimestamp";
        private static final String RAG_PROCESS_END_TIMESTAMP = "ragProcessEndTimestamp";
        private static final String RAG_PROCESS_MESSAGES = "ragProcessMessages";
        private static final String RAG_PROCESS_DOC_COUNT = "ragProcessDocCount";
        private static final String RAG_PROCESS_DOC_TITLES = "ragProcessDocTitles";
        private static final String THINK_PROCESS = "THINK";
        private static final String THINK_PROCESS_TIMESTAMP = "thinkProcessTimestamp";
        private static final String THINK_PROCESS_END_TIMESTAMP = "thinkProcessEndTimestamp";
        private static final String THINK_PROCESS_MESSAGES = "thinkProcessMessages";
        private static final String THINK_PROCESS_PROMPT_TOKENS = "thinkProcessPromptTokens";
        private static final String THINK_PROCESS_COMPLETION_TOKENS = "thinkProcessCompletionTokens";
        private static final String MCP_TOOL_PROCESS = "MCP TOOLS";
        private static final String MCP_TOOL_PROCESS_TIMESTAMP = "mcpToolProcessTimestamp";
        private static final String MCP_TOOL_PROCESS_END_TIMESTAMP = "mcpToolProcessEndTimestamp";
        private static final String MCP_TOOL_PROCESS_MESSAGES = "mcpToolProcessMessages";
        private static final String MCP_TOOL_PROCESS_CALL_COUNT = "mcpToolProcessCallCount";
        private static final String MCP_TOOL_PROCESS_TOOL_NAMES = "mcpToolProcessToolNames";
        private static final String MCP_TOOL_PROCESS_PROMPT_TOKENS = "mcpToolProcessPromptTokens";
        private static final String MCP_TOOL_PROCESS_COMPLETION_TOKENS = "mcpToolProcessCompletionTokens";
        private static final String STREAM_STATUS = "streamStatus";
        private static final String STREAM_STATUS_STAGE = "streamStatusStage";
        private static final String STREAM_STATUS_MESSAGE = "streamStatusMessage";
        private static final String RESPONSE_DURATION_MS = "responseDurationMs";
        private static final String RESPONSE_PROMPT_TOKENS = "responsePromptTokens";
        private static final String RESPONSE_COMPLETION_TOKENS = "responseCompletionTokens";
        private static final String RESPONSE_TOTAL_TOKENS = "responseTotalTokens";
        private static final String RESPONSE_MODEL = "responseModel";
        private static final String STAGE_STARTING = "STARTING";
        private static final String STAGE_RAG = "RAG DOCUMENTS";
        private static final String STAGE_THINK = "THINK";
        private static final String STAGE_MCP = "MCP TOOLS";
        private static final String STAGE_ASSISTANT = "ASSISTANT";
        private static final String STATUS_STOPPED = "STOPPED";
        private static final String STATUS_ERROR = "ERROR";
        private final CompletableFuture<ZoneId> zoneIdFuture;
        private final Supplier<List<Message>> messagesSupplier;
        private VerticalLayout messageListLayout;
        private VerticalLayout processListLayout;
        private long startTimestamp;
        private long responseTimestamp;
        private ChatMessage userMessage;
        private ChatMessage botResponse;
        private boolean isFirstAssistantResponse;
        private ChatMessage ragProcessMessage;
        private long ragProcessTimestamp;
        private long ragProcessEndTimestamp;
        private int ragRetrievedDocCount;
        private final LinkedHashSet<String> ragRetrievedDocTitles = new LinkedHashSet<>();
        private Details ragProcessDetails;
        private StringBuilder ragProcessMessageBuilder;
        private ChatMessage botThinkResponse;
        private long botThinkTimestamp;
        private long botThinkEndTimestamp;
        private Details thinkDetails;
        private StringBuilder thinkProcessMessageBuilder;
        private ChatMessage mcpToolProcessMessage;
        private long mcpToolProcessTimestamp;
        private long mcpToolProcessEndTimestamp;
        private int mcpToolCallCount;
        private final LinkedHashSet<String> mcpToolNames = new LinkedHashSet<>();
        private Details mcpToolProcessDetails;
        private StringBuilder mcpToolProcessMessagesBuilder;
        private long turnPromptTokens;
        private long turnCompletionTokens;
        private long turnTotalTokens;
        private boolean turnUsageSeen;
        private long thinkPromptTokens;
        private long thinkCompletionTokens;
        private boolean thinkUsageSeen;
        private long mcpPromptTokens;
        private long mcpCompletionTokens;
        private boolean mcpUsageSeen;
        private String currentStage = STAGE_STARTING;
        private String streamStatus;
        private String streamStatusMessage;

        private ChatContentManager(VerticalLayout messageListLayout, String userPrompt,
                CompletableFuture<ZoneId> zoneIdFuture, ChatHistory chatHistory) {
            this.zoneIdFuture = zoneIdFuture;
            this.messagesSupplier = chatHistory.messagesSupplier();
            if (Objects.isNull(messageListLayout))
                return;
            this.messageListLayout = messageListLayout;
            this.startTimestamp = System.currentTimeMillis();
            chatHistory.updateLastMessageTimestamp(startTimestamp);
            this.userMessage = buildMessage(userPrompt, MessageType.USER, startTimestamp);
            this.processListLayout = buildProcessListLayout();
            this.botResponse = buildMessage(null, MessageType.ASSISTANT, System.currentTimeMillis());
            // Streamed reply: enhance once in doFinally instead of on every settle while chunks arrive.
            this.botResponse.disableAutoEnhance();
            this.botResponse.addClassName("blink");
            this.isFirstAssistantResponse = true;
            this.messageListLayout.remove(ChatContentView.this.scrollSpacer);
            this.messageListLayout.add(this.userMessage, this.processListLayout, this.botResponse,
                    ChatContentView.this.scrollSpacer);
            anchorPromptToTop(this.userMessage);
        }

        private void anchorPromptToTop(ChatMessage userMessage) {
            ChatContentView.this.messageScroller.getElement().executeJs("""
                    const s = this;
                    const list = s.firstElementChild;
                    const userMsg = $0;
                    const spacer = $1;
                    const margin = $2;
                    const applyAnchor = () => {
                        if (!userMsg.isConnected) return false;
                        const targetScrollTop = Math.max(0, userMsg.offsetTop - margin);
                        const needed = targetScrollTop + s.clientHeight;
                        const currentSpacerH = parseInt(spacer.style.height) || 0;
                        const contentWithoutSpacer = list.scrollHeight - currentSpacerH;
                        const requiredSpacer = Math.max(0, needed - contentWithoutSpacer);
                        if (requiredSpacer > currentSpacerH) {
                            spacer.style.height = requiredSpacer + 'px';
                        }
                        if (Math.abs(s.scrollTop - targetScrollTop) > 1) s.scrollTop = targetScrollTop;
                        return true;
                    };
                    if (!applyAnchor()) requestAnimationFrame(applyAnchor);
                    """, userMessage.getElement(), ChatContentView.this.scrollSpacer.getElement(),
                    PROMPT_TOP_MARGIN_PX);
        }

        private VerticalLayout buildProcessListLayout() {
            VerticalLayout layout = new VerticalLayout();
            layout.setMargin(false);
            layout.setSpacing(false);
            layout.setPadding(false);
            layout.setWidthFull();
            return layout;
        }

        public void appendRagProcessMessage(Object content) {
            this.currentStage = STAGE_RAG;
            markGenerating();
            if (content instanceof SpringAiPlaygroundRagAdvisor.RagRetrievedDocumentsInfo info) {
                this.ragRetrievedDocCount = info.count();
                this.ragRetrievedDocTitles.addAll(info.titles());
                return;
            }
            long timestamp = System.currentTimeMillis();
            String contentStr = content.toString();
            String markdownSnippet = getLocalDateTime(timestamp) + " : " + contentStr + "\n\n";
            getRagProcessMessage(timestamp).appendMarkdown(markdownSnippet);
            if (Objects.isNull(this.ragProcessMessageBuilder))
                this.ragProcessMessageBuilder = new StringBuilder();
            this.ragProcessMessageBuilder.append(markdownSnippet);
            if (SpringAiPlaygroundRagAdvisor.RAG_SEARCH_COMPLETED_MESSAGE.equals(contentStr)) {
                this.ragProcessEndTimestamp = timestamp;
                collapseProcessDetails(this.ragProcessDetails);
                updateDetailsSummary(this.ragProcessDetails, RAG_PROCESS, this.ragProcessTimestamp,
                        this.ragProcessEndTimestamp,
                        formatRagExtra(this.ragRetrievedDocCount, this.ragRetrievedDocTitles));
            }
        }

        private ChatMessage getRagProcessMessage(long timestamp) {
            if (Objects.isNull(this.ragProcessMessage)) {
                this.ragProcessTimestamp = timestamp;
                this.ragProcessMessage = buildMessage(null, RAG_PROCESS, this.ragProcessTimestamp);
                this.processListLayout.add(buildProcessDetails(RAG_PROCESS, getRagProcessDetails(),
                        this.ragProcessMessage));
            }
            return this.ragProcessMessage;
        }

        public void appendMcpToolProcessMessage(Object content) {
            this.currentStage = STAGE_MCP;
            markGenerating();
            long timestamp = System.currentTimeMillis();
            String contentStr = content.toString();
            String markdownSnippet = getLocalDateTime(timestamp) + " : " + contentStr + "\n\n";
            getMcpToolProcessMessage(timestamp).appendMarkdown(markdownSnippet);
            if (Objects.isNull(this.mcpToolProcessMessagesBuilder))
                this.mcpToolProcessMessagesBuilder = new StringBuilder();
            this.mcpToolProcessMessagesBuilder.append(markdownSnippet);
            if (content instanceof McpToolCallingManager.McpAssistantToolCall toolCall) {
                toolCall.toolCalls().forEach(tc -> {
                    this.mcpToolCallCount++;
                    this.mcpToolNames.add(tc.name());
                });
            }
            if (McpToolCallingManager.MCP_TOOL_EXECUTION_COMPLETED_MESSAGE.equals(contentStr)) {
                this.mcpToolProcessEndTimestamp = timestamp;
                collapseProcessDetails(this.mcpToolProcessDetails);
                updateDetailsSummary(this.mcpToolProcessDetails, MCP_TOOL_PROCESS, this.mcpToolProcessTimestamp,
                        this.mcpToolProcessEndTimestamp,
                        joinExtra(formatMcpExtra(this.mcpToolCallCount, this.mcpToolNames), mcpTokensExtra()));
            } else if (Objects.nonNull(this.mcpToolProcessDetails))
                this.mcpToolProcessDetails.setOpened(true);
        }

        private ChatMessage getMcpToolProcessMessage(long timestamp) {
            if (Objects.isNull(this.mcpToolProcessMessage)) {
                this.mcpToolProcessTimestamp = timestamp;
                this.mcpToolProcessMessage = buildMessage(null, MCP_TOOL_PROCESS, this.mcpToolProcessTimestamp);
                this.processListLayout.add(buildProcessDetails(MCP_TOOL_PROCESS, getMcpToolProcessDetails(),
                        this.mcpToolProcessMessage));
            }
            return this.mcpToolProcessMessage;
        }

        public void appendBotThinkProcessMessage(Object content) {
            this.currentStage = STAGE_THINK;
            markGenerating();
            long timestamp = System.currentTimeMillis();
            String markdownSnippet = content.toString();
            getBotThinkResponse(timestamp).appendMarkdown(markdownSnippet);
            if (Objects.isNull(this.thinkProcessMessageBuilder))
                this.thinkProcessMessageBuilder = new StringBuilder();
            this.thinkProcessMessageBuilder.append(markdownSnippet);
        }

        private ChatMessage getBotThinkResponse(long timestamp) {
            if (Objects.isNull(this.botThinkResponse)) {
                this.botThinkTimestamp = timestamp;
                this.botThinkResponse = buildMessage(null, THINK_PROCESS, this.botThinkTimestamp);
                this.processListLayout.add(buildProcessDetails(THINK_PROCESS, getThinkDetails(), this.botThinkResponse));
            }
            return this.botThinkResponse;
        }

        // Every round adds to the turn total shown in the assistant header. A tool-call round's tokens go to
        // the THINK panel when the model streamed thinking (the round's output is mostly reasoning) and to the
        // MCP TOOLS panel otherwise; the final answer round is represented by the header total alone.
        public void applyRoundUsage(ChatService.RoundUsage round) {
            this.turnPromptTokens += round.promptTokens();
            this.turnCompletionTokens += round.completionTokens();
            this.turnTotalTokens += round.totalTokens();
            this.turnUsageSeen = true;
            if (!round.toolCallRound()) return;
            if (round.thinkRound()) {
                this.thinkPromptTokens += round.promptTokens();
                this.thinkCompletionTokens += round.completionTokens();
                this.thinkUsageSeen = true;
                updateDetailsSummary(this.thinkDetails, THINK_PROCESS, this.botThinkTimestamp,
                        this.botThinkEndTimestamp, thinkTokensExtra());
            } else {
                this.mcpPromptTokens += round.promptTokens();
                this.mcpCompletionTokens += round.completionTokens();
                this.mcpUsageSeen = true;
                updateDetailsSummary(this.mcpToolProcessDetails, MCP_TOOL_PROCESS, this.mcpToolProcessTimestamp,
                        this.mcpToolProcessEndTimestamp,
                        joinExtra(formatMcpExtra(this.mcpToolCallCount, this.mcpToolNames), mcpTokensExtra()));
            }
        }

        private String thinkTokensExtra() {
            return this.thinkUsageSeen ? formatTokens(this.thinkPromptTokens, this.thinkCompletionTokens) : null;
        }

        private String mcpTokensExtra() {
            return this.mcpUsageSeen ? formatTokens(this.mcpPromptTokens, this.mcpCompletionTokens) : null;
        }

        private static Details buildProcessDetails(String title, Details details, ChatMessage markdownMessage) {
            details.setSummary(buildDetailsSummary(title, 0L, 0L, null));
            details.add(markdownMessage);
            details.addThemeVariants(DetailsVariant.FILLED);
            details.setWidthFull();
            return details;
        }

        private static Span buildDetailsSummary(String title, long startMs, long endMs, String extraInfo) {
            StringBuilder sb = new StringBuilder(title);
            if (startMs > 0 && endMs > startMs)
                sb.append(" · ").append(formatDuration(endMs - startMs));
            if (Objects.nonNull(extraInfo) && !extraInfo.isEmpty())
                sb.append(" · ").append(extraInfo);
            Span span = new Span(sb.toString());
            span.getStyle().set("max-width", "calc(100% - 2rem)").set("overflow", "hidden")
                    .set("text-overflow", "ellipsis").set("white-space", "nowrap");
            return span;
        }

        private static void updateDetailsSummary(Details details, String title, long startMs, long endMs,
                String extraInfo) {
            if (Objects.nonNull(details))
                details.setSummary(buildDetailsSummary(title, startMs, endMs, extraInfo));
        }

        private static String formatDuration(long durationMs) {
            return durationMs < 1000 ? durationMs + "ms"
                    : String.format(Locale.ROOT, "%.1fs", durationMs / 1000.0);
        }

        private static String formatTokens(long promptTokens, long completionTokens) {
            return String.format(Locale.US, "%,d tokens (in %,d · out %,d)", promptTokens + completionTokens,
                    promptTokens, completionTokens);
        }

        private static String joinExtra(String left, String right) {
            if (Objects.isNull(left) || left.isEmpty()) return right;
            if (Objects.isNull(right) || right.isEmpty()) return left;
            return left + " · " + right;
        }

        private static String tokensExtraOf(Map<String, Object> metadata, String promptKey, String completionKey) {
            if (metadata.get(promptKey) instanceof Number in && metadata.get(completionKey) instanceof Number out)
                return formatTokens(in.longValue(), out.longValue());
            return null;
        }

        // Persisted into the assistant message metadata so the header metrics re-render after a reload.
        // Tokens prefer the per-round accumulation (covers every LLM round of a tool loop); ChatMeta only
        // carries the last round, so it is just the fallback.
        private void persistResponseMeta(Map<String, Object> metadata, long durationMs,
                ChatService.ChatMeta chatMeta) {
            metadata.put(RESPONSE_DURATION_MS, durationMs);
            if (Objects.nonNull(chatMeta) && Objects.nonNull(chatMeta.model()) && !chatMeta.model().isBlank())
                metadata.put(RESPONSE_MODEL, chatMeta.model());
            if (this.turnUsageSeen) {
                metadata.put(RESPONSE_PROMPT_TOKENS, this.turnPromptTokens);
                metadata.put(RESPONSE_COMPLETION_TOKENS, this.turnCompletionTokens);
                metadata.put(RESPONSE_TOTAL_TOKENS, this.turnTotalTokens);
                return;
            }
            Usage usage = Objects.nonNull(chatMeta) ? chatMeta.usage() : null;
            if (Objects.isNull(usage)) return;
            putIfPresent(metadata, RESPONSE_PROMPT_TOKENS, usage.getPromptTokens());
            putIfPresent(metadata, RESPONSE_COMPLETION_TOKENS, usage.getCompletionTokens());
            putIfPresent(metadata, RESPONSE_TOTAL_TOKENS, usage.getTotalTokens());
        }

        private static void putIfPresent(Map<String, Object> metadata, String key, Integer value) {
            if (Objects.nonNull(value)) metadata.put(key, value);
        }

        private static String formatRagExtra(int docCount, Collection<String> titles) {
            if (docCount < 0) return null;
            String countStr = docCount + (docCount == 1 ? " doc" : " docs");
            if (Objects.isNull(titles) || titles.isEmpty()) return countStr;
            String joined = String.join(", ", titles);
            if (joined.length() > 40) joined = joined.substring(0, 37) + "...";
            return countStr + " · " + joined;
        }

        private static String formatMcpExtra(int callCount, Collection<String> toolNames) {
            if (callCount <= 0) return null;
            String callStr = callCount + (callCount == 1 ? " call" : " calls");
            if (Objects.isNull(toolNames) || toolNames.isEmpty()) return callStr;
            String joined = String.join(", ", toolNames);
            if (joined.length() > 40) joined = joined.substring(0, 37) + "...";
            return callStr + " · " + joined;
        }

        private Details getRagProcessDetails() {
            if (Objects.isNull(this.ragProcessDetails)) {
                this.ragProcessDetails = new Details();
                this.ragProcessDetails.setOpened(true);
            }
            return this.ragProcessDetails;
        }

        private Details getMcpToolProcessDetails() {
            if (Objects.isNull(this.mcpToolProcessDetails)) {
                this.mcpToolProcessDetails = new Details();
                this.mcpToolProcessDetails.setOpened(true);
            }
            return this.mcpToolProcessDetails;
        }

        private void initMarkdownMessage(VerticalLayout messageListLayout, Message message, MessageType messageType) {
            String text = message.getText();
            // Tool-loop bookkeeping (TOOL responses, blank assistant rounds that only carried tool calls)
            // feeds the LLM but never rendered live; the MCP TOOLS panel already summarizes it, so skip it
            // on reload too instead of drawing empty epoch-time bubbles.
            if (MessageType.TOOL.equals(messageType)) return;
            if (message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty()
                    && (Objects.isNull(text) || text.isBlank())) return;
            Map<String, Object> metadata = message.getMetadata();

            List<Pair<Long, Component>> components = new ArrayList<>();

            String ragProcessMessages = (String) metadata.get(RAG_PROCESS_MESSAGES);
            if (Objects.nonNull(ragProcessMessages)) {
                Long ragProcessTimestamp = (Long) metadata.get(RAG_PROCESS_TIMESTAMP);
                Long ragEnd = (Long) metadata.get(RAG_PROCESS_END_TIMESTAMP);
                Integer ragDocCount = (Integer) metadata.get(RAG_PROCESS_DOC_COUNT);
                @SuppressWarnings("unchecked")
                Collection<String> ragTitles =
                        (Collection<String>) metadata.get(RAG_PROCESS_DOC_TITLES);
                Details details = ChatContentManager.buildProcessDetails(RAG_PROCESS, new Details(),
                        buildMessage(ragProcessMessages, RAG_PROCESS, ragProcessTimestamp));
                updateDetailsSummary(details, RAG_PROCESS, ragProcessTimestamp,
                        Objects.nonNull(ragEnd) ? ragEnd : 0L,
                        formatRagExtra(Objects.nonNull(ragDocCount) ? ragDocCount : 0, ragTitles));
                details.setOpened(false);
                components.add(new Pair<>(ragProcessTimestamp, details));
            }

            String thinkProcessMessages = (String) metadata.get(THINK_PROCESS_MESSAGES);
            if (Objects.nonNull(thinkProcessMessages)) {
                Long thinkProcessTimestamp = (Long) metadata.get(THINK_PROCESS_TIMESTAMP);
                Long thinkEnd = (Long) metadata.get(THINK_PROCESS_END_TIMESTAMP);
                Details details = ChatContentManager.buildProcessDetails(THINK_PROCESS, new Details(),
                        buildMessage(thinkProcessMessages, THINK_PROCESS, thinkProcessTimestamp));
                updateDetailsSummary(details, THINK_PROCESS, thinkProcessTimestamp,
                        Objects.nonNull(thinkEnd) ? thinkEnd : 0L,
                        tokensExtraOf(metadata, THINK_PROCESS_PROMPT_TOKENS, THINK_PROCESS_COMPLETION_TOKENS));
                details.setOpened(false);
                components.add(new Pair<>(thinkProcessTimestamp, details));
            }

            String mcpToolProcessMessages = (String) metadata.get(MCP_TOOL_PROCESS_MESSAGES);
            if (Objects.nonNull(mcpToolProcessMessages)) {
                Long mcpToolProcessTimestamp = (Long) metadata.get(MCP_TOOL_PROCESS_TIMESTAMP);
                Long mcpEnd = (Long) metadata.get(MCP_TOOL_PROCESS_END_TIMESTAMP);
                Integer mcpCallCount = (Integer) metadata.get(MCP_TOOL_PROCESS_CALL_COUNT);
                @SuppressWarnings("unchecked")
                Collection<String> mcpNames =
                        (Collection<String>) metadata.get(MCP_TOOL_PROCESS_TOOL_NAMES);
                Details details = ChatContentManager.buildProcessDetails(MCP_TOOL_PROCESS, new Details(),
                        buildMessage(mcpToolProcessMessages, MCP_TOOL_PROCESS, mcpToolProcessTimestamp));
                updateDetailsSummary(details, MCP_TOOL_PROCESS, mcpToolProcessTimestamp,
                        Objects.nonNull(mcpEnd) ? mcpEnd : 0L,
                        joinExtra(formatMcpExtra(Objects.nonNull(mcpCallCount) ? mcpCallCount : 0, mcpNames),
                                tokensExtraOf(metadata, MCP_TOOL_PROCESS_PROMPT_TOKENS,
                                        MCP_TOOL_PROCESS_COMPLETION_TOKENS)));
                details.setOpened(false);
                components.add(new Pair<>(mcpToolProcessTimestamp, details));
            }
            long messageTimestamp = timestampOf(metadata);
            if (MessageType.USER.equals(messageType))
                messageListLayout.add(buildMessage(text, messageType, messageTimestamp));
            components.stream().sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond)
                    .forEach(messageListLayout::add);
            if (!MessageType.USER.equals(messageType)) {
                ChatMessage assistant = buildMessage(text, messageType, messageTimestamp);
                messageListLayout.add(assistant);
                fillResponseMetrics(assistant, metadata);
            }
            String streamStatus = (String) metadata.get(STREAM_STATUS);
            if (Objects.nonNull(streamStatus)) {
                Span indicator = buildStreamStatusIndicator(streamStatus, (String) metadata.get(STREAM_STATUS_STAGE),
                        (String) metadata.get(STREAM_STATUS_MESSAGE));
                if (Objects.nonNull(indicator)) messageListLayout.add(indicator);
            }
        }

        private static long timestampOf(Map<String, Object> metadata) {
            Object timestamp = metadata.get(ChatHistory.TIMESTAMP);
            return timestamp == null ? 0L : Long.parseLong(timestamp.toString());
        }

        private ChatMessage buildMessage(String message, MessageType messageType, long epochMillis) {
            ChatMessage chatMessage = buildMessage(message, messageType.getValue().toUpperCase(),
                    messageType.ordinal(), epochMillis);
            if (MessageType.USER.equals(messageType)) chatMessage.usePlainText();
            chatMessage.addActionBar(buildMessageActionBar(chatMessage, messageType));
            return chatMessage;
        }

        // A hover-revealed action row under each message, collapse first. Response metrics are not here:
        // they ride the message header next to the time, mirroring the process panel summaries.
        private HorizontalLayout buildMessageActionBar(ChatMessage chatMessage, MessageType messageType) {
            HorizontalLayout actions = new HorizontalLayout();
            actions.addClassName("chat-message-actions");
            actions.setSpacing(false);
            actions.setPadding(false);
            actions.setAlignItems(FlexComponent.Alignment.CENTER);
            actions.getStyle().set("gap", "var(--lumo-space-s)")
                    .set("margin-left", "calc(var(--lumo-space-l) * 2)").set("margin-top", "2px");
            Button collapse = new Button(VaadinIcon.CHEVRON_UP.create());
            collapse.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            collapse.setTooltipText("Collapse");
            collapse.addClickListener(event -> {
                boolean next = !chatMessage.isCollapsed();
                chatMessage.setCollapsed(next);
                collapse.setIcon((next ? VaadinIcon.CHEVRON_DOWN : VaadinIcon.CHEVRON_UP).create());
                collapse.setTooltipText(next ? "Expand" : "Collapse");
            });
            Button copy = new Button(VaadinIcon.COPY_O.create(), event -> chatMessage.getElement()
                    .executeJs("navigator.clipboard.writeText($0)", chatMessage.getRawMarkdown()));
            copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            copy.setTooltipText("Copy");
            // User bubbles already show the literal input, so the rendered/raw toggle applies to replies only.
            Button rawToggle = null;
            if (!MessageType.USER.equals(messageType)) {
                rawToggle = new Button(VaadinIcon.CODE.create());
                rawToggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                        ButtonVariant.LUMO_ICON);
                rawToggle.setTooltipText("Show raw");
                Button toggleRef = rawToggle;
                rawToggle.addClickListener(event -> {
                    boolean raw = !chatMessage.isShowingRaw();
                    chatMessage.setShowRaw(raw);
                    toggleRef.setIcon((raw ? VaadinIcon.FILE_TEXT_O : VaadinIcon.CODE).create());
                    toggleRef.setTooltipText(raw ? "Show rendered" : "Show raw");
                });
            }
            Button read = new Button(VaadinIcon.VOLUME_UP.create(), event -> chatMessage.getElement().executeJs("""
                    if (!window.speechSynthesis) return;
                    window.speechSynthesis.cancel();
                    window.speechSynthesis.speak(new SpeechSynthesisUtterance($0));
                    """, chatMessage.getRawMarkdown()));
            read.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            read.setTooltipText("Read aloud");
            Button quote = new Button(VaadinIcon.QUOTE_RIGHT.create(),
                    event -> quoteToInput(chatMessage.getRawMarkdown()));
            quote.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            quote.setTooltipText("Quote in prompt");
            // A plain icon button + ContextMenu rather than a single-item MenuBar: a standalone MenuBar mis-measures
            // its height and overflows the message row. ContextMenu has no such layout machinery.
            Button exportButton = new Button(VaadinIcon.DOWNLOAD.create());
            exportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ICON);
            exportButton.setTooltipText("Export");
            ContextMenu exportMenu = new ContextMenu(exportButton);
            exportMenu.setOpenOnClick(true);
            exportMenu.addItem("Markdown (.md)", event -> ChatContentView.this.triggerDownload("message.md",
                    "text/markdown", ChatContentView.this.chatExportService.messageToMarkdown(
                            messageOf(messageType, chatMessage.getRawMarkdown()))));
            exportMenu.addItem("Plain text (.txt)", event -> ChatContentView.this.triggerDownload("message.txt",
                    "text/plain", ChatContentView.this.chatExportService.messageToPlainText(
                            messageOf(messageType, chatMessage.getRawMarkdown()))));
            exportMenu.addItem("JSON (.json)", event -> ChatContentView.this.triggerDownload("message.json",
                    "application/json", ChatContentView.this.chatExportService.messageToJson(
                            messageOf(messageType, chatMessage.getRawMarkdown()))));
            exportMenu.addItem("PDF (print)", event -> chatMessage.getElement().executeJs(
                    "window.Saip && window.Saip.chatMarkdown && window.Saip.chatMarkdown.printMessage(this)"));
            actions.add(collapse, copy);
            if (Objects.nonNull(rawToggle)) actions.add(rawToggle);
            actions.add(read, quote, exportButton);
            return actions;
        }

        // Append elapsed - turn-total tokens (in/out) next to the header time; per-stage tokens and tool/RAG
        // usage live on their own process panel summaries, and the model is persisted but not displayed.
        private void fillResponseMetrics(ChatMessage chatMessage, Map<String, Object> metadata) {
            List<String> parts = new ArrayList<>();
            if (metadata.get(RESPONSE_DURATION_MS) instanceof Number duration)
                parts.add(formatDuration(duration.longValue()));
            if (metadata.get(RESPONSE_TOTAL_TOKENS) instanceof Number total) {
                StringBuilder tokens = new StringBuilder(String.format(Locale.US, "%,d tokens", total.longValue()));
                if (metadata.get(RESPONSE_PROMPT_TOKENS) instanceof Number in
                        && metadata.get(RESPONSE_COMPLETION_TOKENS) instanceof Number out)
                    tokens.append(String.format(Locale.US, " (in %,d · out %,d)", in.longValue(), out.longValue()));
                parts.add(tokens.toString());
            }
            if (!parts.isEmpty()) chatMessage.setTimeDetail(String.join(" · ", parts));
        }

        private static Message messageOf(MessageType type, String raw) {
            String text = raw == null ? "" : raw;
            return type == MessageType.ASSISTANT ? new AssistantMessage(text) : new UserMessage(text);
        }

        private void quoteToInput(String text) {
            if (Objects.isNull(text) || text.isBlank()) return;
            String quoted = "> " + text.strip().replace("\n", "\n> ") + "\n\n";
            String existing = ChatContentView.this.userPromptTextArea.getValue();
            ChatContentView.this.userPromptTextArea.setValue(
                    Objects.isNull(existing) || existing.isBlank() ? quoted : existing + "\n" + quoted);
            ChatContentView.this.userPromptTextArea.focus();
        }

        // Process panels (think/tools/rag) render plain markdown only: their content is transient logging,
        // so the highlight/math/diagram pass is skipped to keep streaming free of re-render flicker.
        private ChatMessage buildMessage(String message, String name, long epochMillis) {
            ChatMessage chatMessage = buildMessage(message, name, -1, epochMillis);
            chatMessage.disableAutoEnhance();
            return chatMessage;
        }

        private ChatMessage buildMessage(String message, String name, int colorIndex, long epochMillis) {
            ChatMessage chatMessage = new ChatMessage(name, getLocalDateTime(epochMillis), colorIndex);
            if (Objects.nonNull(message) && !message.isBlank())
                chatMessage.setMarkdown(message);
            return chatMessage;
        }

        private Details getThinkDetails() {
            if (Objects.isNull(this.thinkDetails)) {
                this.thinkDetails = new Details();
                this.thinkDetails.setOpened(true);
            }
            return this.thinkDetails;
        }

        private LocalDateTime getLocalDateTime(long epochMillis) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis),
                    this.zoneIdFuture.getNow(ZoneId.systemDefault()));
        }

        public void append(String content) {
            if (content == null || content.isEmpty()) return;
            this.currentStage = STAGE_ASSISTANT;
            if (this.isFirstAssistantResponse) {
                long now = System.currentTimeMillis();
                initBotResponse(now);
                if (Objects.nonNull(this.thinkDetails) && this.botThinkEndTimestamp == 0)
                    this.botThinkEndTimestamp = now;
                collapseProcessDetails(this.ragProcessDetails);
                collapseProcessDetails(this.thinkDetails);
                updateDetailsSummary(this.thinkDetails, THINK_PROCESS, this.botThinkTimestamp,
                        this.botThinkEndTimestamp, thinkTokensExtra());
            }
            this.botResponse.removeClassName("blink");
            this.botResponse.appendMarkdown(content);
        }

        private void markGenerating() {
            if (Objects.nonNull(this.botResponse)) this.botResponse.addClassName("blink");
        }

        private void initBotResponse(long epochMillis) {
            this.responseTimestamp = epochMillis;
            this.botResponse.setTime(getLocalDateTime(this.responseTimestamp));
            this.botResponse.removeClassName("blink");
            this.isFirstAssistantResponse = false;
        }

        public void markStopped() {
            this.streamStatus = STATUS_STOPPED;
            this.streamStatusMessage = null;
        }

        public void markError(Throwable throwable) {
            this.streamStatus = STATUS_ERROR;
            this.streamStatusMessage = Optional.ofNullable(throwable).map(Throwable::getMessage)
                    .filter(s -> !s.isBlank()).orElse("Unknown error");
        }

        private static Span buildStreamStatusIndicator(String status, String stage, String message) {
            String text;
            if (STATUS_STOPPED.equals(status))
                text = "Stopped at " + (Objects.nonNull(stage) ? stage : STAGE_STARTING);
            else if (STATUS_ERROR.equals(status))
                text = "Error at " + (Objects.nonNull(stage) ? stage : STAGE_STARTING)
                        + (Objects.nonNull(message) ? ": " + message : "");
            else return null;
            Span span = new Span(text);
            span.getElement().getThemeList().add("badge " + (STATUS_ERROR.equals(status) ? "error" : "contrast"));
            span.getStyle().set("margin-left", "calc(var(--lumo-space-l) * 2)").set("margin-top",
                    "var(--lumo-space-xs)");
            return span;
        }

        public void doFinally() {
            Optional<List<Message>> messageList =
                    Optional.of(this.messagesSupplier.get()).filter(Predicate.not(List::isEmpty));
            // A tool loop appends ASSISTANT/TOOL messages after the user turn, so the user message (where
            // ChatService parks ChatMeta) must be searched for, not assumed to sit next to the tail.
            Optional<Map<String, Object>> lastUserMetadata = messageList.flatMap(list -> list.reversed().stream()
                    .filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst())
                    .map(Message::getMetadata);
            lastUserMetadata.ifPresent(metadata -> updateMetadata(metadata, this.startTimestamp));
            Optional<Map<String, Object>> metadataAsOpt = messageList.map(List::getLast).map(Message::getMetadata);

            if (this.isFirstAssistantResponse) {
                boolean noProcessActivity = Objects.isNull(this.ragProcessMessageBuilder)
                        && Objects.isNull(this.thinkProcessMessageBuilder)
                        && Objects.isNull(this.mcpToolProcessMessagesBuilder);
                if (Objects.nonNull(this.botResponse) && Objects.nonNull(this.messageListLayout))
                    this.messageListLayout.remove(this.botResponse);
                if (noProcessActivity) {
                    if (Objects.nonNull(this.processListLayout) && Objects.nonNull(this.messageListLayout))
                        this.messageListLayout.remove(this.processListLayout);
                    saveAndRenderStreamStatus(messageList.map(List::getLast).map(Message::getMetadata));
                    return;
                }
            }

            collapseProcessDetails(this.ragProcessDetails);
            collapseProcessDetails(this.thinkDetails);
            collapseProcessDetails(this.mcpToolProcessDetails);

            if (Objects.nonNull(this.ragProcessMessageBuilder)) {
                metadataAsOpt.ifPresent(metadata -> {
                    metadata.put(RAG_PROCESS_TIMESTAMP, this.ragProcessTimestamp);
                    if (this.ragProcessEndTimestamp > 0)
                        metadata.put(RAG_PROCESS_END_TIMESTAMP, this.ragProcessEndTimestamp);
                    metadata.put(RAG_PROCESS_DOC_COUNT, this.ragRetrievedDocCount);
                    if (!this.ragRetrievedDocTitles.isEmpty())
                        metadata.put(RAG_PROCESS_DOC_TITLES, new ArrayList<>(this.ragRetrievedDocTitles));
                    metadata.put(RAG_PROCESS_MESSAGES, this.ragProcessMessageBuilder.toString());
                });
                this.ragProcessDetails = null;
                this.ragProcessMessage = null;
                this.ragProcessMessageBuilder = null;
            }
            if (Objects.nonNull(this.thinkProcessMessageBuilder)) {
                if (this.botThinkEndTimestamp == 0)
                    this.botThinkEndTimestamp = System.currentTimeMillis();
                metadataAsOpt.ifPresent(metadata -> {
                    metadata.put(THINK_PROCESS_TIMESTAMP, this.botThinkTimestamp);
                    metadata.put(THINK_PROCESS_END_TIMESTAMP, this.botThinkEndTimestamp);
                    metadata.put(THINK_PROCESS_MESSAGES, this.thinkProcessMessageBuilder.toString());
                    if (this.thinkUsageSeen) {
                        metadata.put(THINK_PROCESS_PROMPT_TOKENS, this.thinkPromptTokens);
                        metadata.put(THINK_PROCESS_COMPLETION_TOKENS, this.thinkCompletionTokens);
                    }
                });
                this.thinkDetails = null;
                this.botThinkResponse = null;
                this.thinkProcessMessageBuilder = null;
            }
            if (Objects.nonNull(this.mcpToolProcessMessagesBuilder)) {
                metadataAsOpt.ifPresent(metadata -> {
                    metadata.put(MCP_TOOL_PROCESS_TIMESTAMP, this.mcpToolProcessTimestamp);
                    if (this.mcpToolProcessEndTimestamp > 0)
                        metadata.put(MCP_TOOL_PROCESS_END_TIMESTAMP, this.mcpToolProcessEndTimestamp);
                    if (this.mcpToolCallCount > 0)
                        metadata.put(MCP_TOOL_PROCESS_CALL_COUNT, this.mcpToolCallCount);
                    if (!this.mcpToolNames.isEmpty())
                        metadata.put(MCP_TOOL_PROCESS_TOOL_NAMES, new ArrayList<>(this.mcpToolNames));
                    metadata.put(MCP_TOOL_PROCESS_MESSAGES, this.mcpToolProcessMessagesBuilder.toString());
                    if (this.mcpUsageSeen) {
                        metadata.put(MCP_TOOL_PROCESS_PROMPT_TOKENS, this.mcpPromptTokens);
                        metadata.put(MCP_TOOL_PROCESS_COMPLETION_TOKENS, this.mcpCompletionTokens);
                    }
                });
                this.mcpToolProcessDetails = null;
                this.mcpToolProcessMessage = null;
                this.mcpToolProcessMessagesBuilder = null;
            }
            long completedTimestamp = this.responseTimestamp > 0 ? this.responseTimestamp : System.currentTimeMillis();
            metadataAsOpt.ifPresent(metadata -> updateMetadata(metadata, completedTimestamp));
            this.botResponse.removeClassName("blink");
            this.botResponse.enhanceNow();
            if (!this.isFirstAssistantResponse) {
                ChatService.ChatMeta chatMeta = lastUserMetadata.map(map -> map.get(ChatService.CHAT_META))
                        .filter(ChatService.ChatMeta.class::isInstance)
                        .map(ChatService.ChatMeta.class::cast).orElse(null);
                long durationMs = Math.max(0, System.currentTimeMillis() - this.startTimestamp);
                metadataAsOpt.ifPresent(metadata -> persistResponseMeta(metadata, durationMs, chatMeta));
                metadataAsOpt.ifPresent(metadata -> fillResponseMetrics(this.botResponse, metadata));
            }
            saveAndRenderStreamStatus(metadataAsOpt);
        }

        private void saveAndRenderStreamStatus(Optional<Map<String, Object>> metadataAsOpt) {
            if (Objects.isNull(this.streamStatus)) return;
            metadataAsOpt.ifPresent(metadata -> {
                metadata.put(STREAM_STATUS, this.streamStatus);
                metadata.put(STREAM_STATUS_STAGE, this.currentStage);
                if (Objects.nonNull(this.streamStatusMessage))
                    metadata.put(STREAM_STATUS_MESSAGE, this.streamStatusMessage);
            });
            Span indicator = buildStreamStatusIndicator(this.streamStatus, this.currentStage,
                    this.streamStatusMessage);
            if (Objects.nonNull(indicator) && Objects.nonNull(this.messageListLayout)) {
                int spacerIdx = this.messageListLayout.indexOf(ChatContentView.this.scrollSpacer);
                if (spacerIdx >= 0) this.messageListLayout.addComponentAtIndex(spacerIdx, indicator);
                else this.messageListLayout.add(indicator);
            }
        }

        private void collapseProcessDetails(Details details) {
            if (Objects.nonNull(details))
                details.setOpened(false);
        }


        private void updateMetadata(Map<String, Object> metadata, long timestamp) {
            metadata.put(ChatHistoryPersistenceService.CONVERSATION_ID, getConversationId());
            metadata.put(ChatHistory.TIMESTAMP, timestamp);
        }

    }
}
