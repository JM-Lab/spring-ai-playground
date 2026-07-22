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
import com.vaadin.flow.component.ClientCallable;
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
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.SpringAiPlaygroundRagAdvisor;
import org.springaicommunity.playground.service.chat.ChatExportService;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryPersistenceService;
import org.springaicommunity.playground.service.analytics.UsageAnalyticsService;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatProvider;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.chat.ChatStreamRegistry;
import org.springaicommunity.playground.service.chat.ChatToolPreferences;
import org.springaicommunity.playground.service.chat.ReasoningEffort;
import org.springaicommunity.playground.service.chat.VisionCapabilityService;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.agent.AgentLoopHarness;
import org.springaicommunity.playground.service.mcp.risk.McpCompositionToolCallbackProvider;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.tool.ChatImageStore;
import org.springaicommunity.playground.service.tool.ConversationFileUploadStore;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.webui.SttMicButton;
import org.springaicommunity.playground.webui.UsageEventTracker;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.tool.ExposedToolsSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.MimeType;
import reactor.core.Disposable;
import reactor.core.publisher.SignalType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@JsModule("./playground/chat-stt.js")
public class ChatContentView extends VerticalLayout {

    private static final Logger logger = LoggerFactory.getLogger(ChatContentView.class);

    private static final ScrollIntoViewOption[] DefaultScrollOptions =
            {ScrollIntoViewOption.Block.END, ScrollIntoViewOption.Inline.NEAREST};

    private static final int PROMPT_TOP_MARGIN_PX = 20;
    private static final int MAX_IMAGES = 5;

    private static final String ACTION_BLOCK_MARKER = "```saip-action";
    private static final ObjectMapper ACTION_MAPPER = new ObjectMapper();

    private final VerticalLayout messageListLayout;
    private final Scroller messageScroller;
    private final com.vaadin.flow.component.html.Div scrollSpacer;
    private final TextArea userPromptTextArea;
    private final MultiSelectComboBox<VectorStoreDocumentInfo> documentsComboBox;
    private final MultiSelectComboBox<McpServerInfo> mcpToolProviderComboBox;
    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;
    private final UsageAnalyticsService usageAnalyticsService;
    private final UsageEventTracker usageEventTracker;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private ChatHistory chatHistory;
    private final McpClientService mcpClientService;
    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final ToolActivationCalculator toolActivationCalculator;
    private final McpServerInfoService mcpServerInfoService;
    private final ChatExportService chatExportService;
    private final ConversationFileUploadStore fileUploadStore;
    private final ChatImageStore imageStore;
    private final VisionCapabilityService visionCapabilityService;
    private final List<PendingImage> pendingImages = new ArrayList<>();
    private int inFlightImageAttaches;
    private Button attachButton;
    private final HorizontalLayout pendingImagesBar = new HorizontalLayout();
    private final MultiSelectComboBox<ToolSpec> customToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> builtinToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> composedToolsComboBox;
    private final MultiSelectComboBox<ToolSpec> exposedToolsDisplayBox;
    private final Select<ReasoningEffort> reasoningSelect = new Select<>();
    private Button submitButton;
    private Button micButton;
    private final Checkbox useBuiltinMcpCheckbox = new Checkbox("Manual built-in tool selection");
    private final Checkbox dynamicToolsCheckbox = new Checkbox("Dynamic tool discovery");
    private final Span dynamicToolsNote = new Span();
    private final McpCompositionToolCallbackProvider compositionProvider;
    private final ChatClientActionRegistry clientActionRegistry;
    private final SpringAiPlaygroundOptions.ToolSearch toolSearch;
    private final SpringAiPlaygroundOptions.AgentLoop agentLoop;
    private final ChatStreamRegistry streamRegistry;
    private final CompletableFuture<ZoneId> zoneIdFuture;

    public ChatContentView(ChatService chatService,
            ChatHistoryService chatHistoryService, ChatHistory chatHistory,
            Consumer<ChatHistory> completeChatHistoryConsumer,
            McpClientService mcpClientService, ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ToolActivationCalculator toolActivationCalculator,
            McpServerInfoService mcpServerInfoService, ChatExportService chatExportService,
            McpCompositionToolCallbackProvider compositionProvider, SpringAiPlaygroundOptions playgroundOptions,
            ChatClientActionRegistry clientActionRegistry, ConversationFileUploadStore fileUploadStore,
            ChatImageStore imageStore,
            VisionCapabilityService visionCapabilityService, UsageAnalyticsService usageAnalyticsService,
            UsageEventTracker usageEventTracker, ChatStreamRegistry streamRegistry) {
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
        this.compositionProvider = compositionProvider;
        this.clientActionRegistry = clientActionRegistry;
        this.fileUploadStore = fileUploadStore;
        this.imageStore = imageStore;
        this.visionCapabilityService = visionCapabilityService;
        this.usageAnalyticsService = usageAnalyticsService;
        this.usageEventTracker = usageEventTracker;
        this.streamRegistry = streamRegistry;
        this.toolSearch = playgroundOptions.chat().toolSearch();
        this.agentLoop = playgroundOptions.chat().agentLoop();
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
        this.customToolsComboBox.setItems(List.of());
        this.builtinToolsComboBox.setItems(List.of());
        this.composedToolsComboBox.setItems(List.of());
        this.builtinToolsComboBox.setHelperText(
                "Built-in tools the MCP server currently exposes — tick which this chat may use.");

        this.exposedToolsDisplayBox = new MultiSelectComboBox<>();
        this.exposedToolsDisplayBox.setPlaceholder("Built-in MCP off — click to enable");
        this.exposedToolsDisplayBox.setWidth("300px");
        this.exposedToolsDisplayBox.setReadOnly(true);
        this.exposedToolsDisplayBox.setAutoOpen(false);
        this.exposedToolsDisplayBox.setItemLabelGenerator(ToolSpec::name);
        this.exposedToolsDisplayBox.setTooltipText("Built-in tools used in this chat — click to edit");
        this.exposedToolsDisplayBox.setSelectedItemsOnTop(true);
        this.exposedToolsDisplayBox.addClassName("exposed-tools-display");
        this.exposedToolsDisplayBox.addClassName("active-on-select");
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
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display[readonly]:not([has-value]) input { opacity: 1 !important; width: auto !important; flex: 1 1 auto !important; min-width: 8em !important; }'"
                + "     + 'vaadin-multi-select-combo-box.active-on-select[has-value]::part(input-field), vaadin-multi-select-combo-box.exposed-tools-display.dynamic-active::part(input-field), vaadin-select.control-active::part(input-field) { background: var(--lumo-primary-color-10pct) !important; }'"
                + "     + 'vaadin-multi-select-combo-box.exposed-tools-display.dynamic-active input::placeholder { color: var(--lumo-body-text-color) !important; -webkit-text-fill-color: var(--lumo-body-text-color) !important; opacity: 1 !important; }';"
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
        this.messageScroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        this.messageScroller.addClassName("chat-message-scroller");
        this.messageScroller.getStyle().set("overflow-anchor", "none");

        this.mcpToolProviderComboBox = new MultiSelectComboBox<>();
        this.mcpToolProviderComboBox.setWidth("300px");
        this.mcpToolProviderComboBox.setTooltipText("Access Tools via external MCP connections");
        this.mcpToolProviderComboBox.setSelectedItemsOnTop(true);
        this.mcpToolProviderComboBox.setItemLabelGenerator(
                mcpServerInfo -> mcpServerInfo.serverName() + "(" + mcpServerInfo.mcpTransportType() + ")");
        this.mcpToolProviderComboBox.setItems(externalMcpServerInfos());
        this.mcpToolProviderComboBox.addClassName("active-on-select");
        this.mcpToolProviderComboBox.addValueChangeListener(e -> {
            if (e.isFromClient()) persistToolPreferences();
        });

        this.documentsComboBox = new MultiSelectComboBox<>();
        this.documentsComboBox.setWidth("300px");
        this.documentsComboBox.setTooltipText("RAG with documents stored in VectorDB.");
        this.documentsComboBox.setSelectedItemsOnTop(true);
        this.documentsComboBox.setItemLabelGenerator(VectorStoreDocumentInfo::title);
        List<VectorStoreDocumentInfo> ragDocuments = this.chatService.getExistDocumentInfoList();
        this.documentsComboBox.setItems(ragDocuments);
        this.documentsComboBox.setEnabled(!ragDocuments.isEmpty());
        this.documentsComboBox.setPlaceholder(
                ragDocuments.isEmpty() ? "No documents for RAG" : "Select documents for RAG");
        this.documentsComboBox.addClassName("active-on-select");
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
        this.zoneIdFuture = VaadinUtils.buildClientZoneIdFuture(new CompletableFuture<>());
        this.userPromptTextArea.setId("sttTextArea");

        String savedProvider = this.chatHistory.provider();
        String currentProvider = this.chatService.getChatModelProvider();
        final boolean providerMismatch = savedProvider != null && !savedProvider.isBlank()
                && !savedProvider.equalsIgnoreCase(currentProvider);

        this.micButton = new SttMicButton(this.userPromptTextArea);
        Icon submitIcon = VaadinUtils.styledIcon(VaadinIcon.ARROW_CIRCLE_UP.create());
        this.submitButton = new Button(submitIcon);
        submitButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        submitButton.setTooltipText("Submit");

        submitButton.addClickListener(e -> {
            if (providerMismatch) return;
            if (this.streamRegistry.isStreaming(this.chatHistory.conversationId())) {
                this.streamRegistry.stop(this.chatHistory.conversationId());
                applyStreamingState(false);
                return;
            }
            this.userPromptTextArea.getElement().executeJs("return this.value;").then(String.class, userPrompt -> {
                if (userPrompt.isBlank() && this.pendingImages.isEmpty())
                    return;
                this.userPromptTextArea.getElement().executeJs("this.value='';");
                this.userPromptTextArea.clear();
                this.streamRegistry.begin(this.chatHistory.conversationId());
                applyStreamingState(true);
                this.streamRegistry.attach(this.chatHistory.conversationId(), inputEvent(userPrompt));
            });
        });

        this.userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT))
                submitButton.click();
        });

        String attachAccept = "image/*";
        ChatAttach attach = new ChatAttach(attachAccept, this::onImageAttached,
                this::onImageProcessingStarted, this::onImageAttachError);
        attach.bindTo(this.userPromptTextArea);
        this.attachButton = new Button(VaadinUtils.styledIcon(VaadinIcon.PICTURE.create()));
        this.attachButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        this.attachButton.setTooltipText("Attach image");
        this.attachButton.addClickListener(e -> attach.openPicker());
        HorizontalLayout suffix = new HorizontalLayout(this.attachButton, micButton, submitButton, attach);
        suffix.addClassName("chat-input-suffix");
        suffix.setSpacing(false);
        suffix.setPadding(false);
        this.userPromptTextArea.setSuffixComponent(suffix);

        this.pendingImagesBar.setSpacing(false);
        this.pendingImagesBar.setPadding(false);
        this.pendingImagesBar.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-s)")
                .set("padding", "10px 4px 4px 4px");
        this.pendingImagesBar.setVisible(false);

        Icon ragIcon = VaadinUtils.styledIcon(VaadinIcon.SEARCH_PLUS.create());
        ragIcon.setTooltipText("Select documents in VectorDB");
        ragIcon.addSingleClickListener(event -> {
            if (this.documentsComboBox.isEnabled()) this.documentsComboBox.setOpened(true);
        });
        ragIcon.getStyle().set("margin-right", "0px");
        Icon toolIcon = VaadinUtils.styledIcon(VaadinIcon.TOOLBOX.create());
        toolIcon.setTooltipText("Access Tools via external MCP connections");
        toolIcon.getStyle().set("margin-right", "0px");
        toolIcon.addSingleClickListener(event -> {
            if (this.mcpToolProviderComboBox.isEnabled()) this.mcpToolProviderComboBox.setOpened(true);
        });

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

        Icon toolStudioIcon = VaadinUtils.styledIcon(VaadinIcon.TOOLS.create());
        toolStudioIcon.setTooltipText("Built-in tools used in this chat");
        toolStudioIcon.getStyle().set("margin-right", "0px");

        this.useBuiltinMcpCheckbox.setValue(this.chatHistory.toolPreferences().useBuiltinMcp());
        this.useBuiltinMcpCheckbox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                if (e.getValue()) this.dynamicToolsCheckbox.setValue(false);
                applyDynamicToolsUi();
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });

        this.dynamicToolsCheckbox.setValue(this.chatHistory.toolPreferences().dynamicTools());
        this.dynamicToolsNote.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        this.dynamicToolsCheckbox.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                if (e.getValue()) this.useBuiltinMcpCheckbox.setValue(false);
                applyDynamicToolsUi();
                persistToolPreferences();
                refreshExposedToolsDisplay();
            }
        });
        if (this.dynamicToolsCheckbox.getValue()) this.useBuiltinMcpCheckbox.setValue(false);

        VerticalLayout exposedToolsPopoverBody = new VerticalLayout();
        if (this.toolSearch.enabled()) {
            exposedToolsPopoverBody.add(this.dynamicToolsCheckbox, this.dynamicToolsNote);
        }
        exposedToolsPopoverBody.add(this.useBuiltinMcpCheckbox, this.customToolsComboBox,
                this.builtinToolsComboBox, this.composedToolsComboBox);
        exposedToolsPopoverBody.setPadding(true);
        exposedToolsPopoverBody.setSpacing(true);
        exposedToolsPopoverBody.setWidth("380px");
        applyDynamicToolsUi();

        Popover exposedToolsPopover = new Popover();
        exposedToolsPopover.setTarget(this.exposedToolsDisplayBox);
        exposedToolsPopover.setPosition(PopoverPosition.TOP);
        exposedToolsPopover.setOpenOnClick(true);
        exposedToolsPopover.add(exposedToolsPopoverBody);
        exposedToolsPopover.addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                populateExposedToolsCombos(false);
                applyDynamicToolsUi();
            }
        });

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
        this.reasoningSelect.addValueChangeListener(event -> {
            applyReasoningActiveStyle();
            if (event.isFromClient() && Objects.nonNull(event.getValue()))
                persistToolPreferences();
        });
        applyReasoningActiveStyle();

        Icon reasoningIcon = VaadinUtils.styledIcon(VaadinIcon.LIGHTBULB.create());
        reasoningIcon.setTooltipText("Reasoning effort");
        reasoningIcon.getStyle().set("margin-right", "0px");
        HorizontalLayout reasoningLayout = new HorizontalLayout(reasoningIcon, this.reasoningSelect);
        reasoningLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        reasoningLayout.setSpacing(false);
        reasoningLayout.getStyle().set("gap", "5px");

        HorizontalLayout userInputMenuLayout = new HorizontalLayout(
                reasoningLayout, exposedToolsLayout, toolLayout, ragLayout);
        userInputMenuLayout.getStyle().set("flex-wrap", "wrap");

        VerticalLayout userInputLayout = new VerticalLayout(this.pendingImagesBar, userInputMenuLayout,
                this.userPromptTextArea);
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
            this.attachButton.setEnabled(false);
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
        if (this.streamRegistry.isStreaming(this.chatHistory.conversationId())) {
            applyStreamingState(true);
            UI streamUi = VaadinUtils.getUi(this);
            this.streamRegistry.onFinish(this.chatHistory.conversationId(),
                    () -> streamUi.access(this::refreshAfterBackgroundStream));
            if (!this.streamRegistry.isStreaming(this.chatHistory.conversationId())) refreshAfterBackgroundStream();
        }
        setSizeFull();
        setMargin(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");

        if (this.chatHistory.messagesSupplier().get().isEmpty())
            return;
        renderPersistedMessages();
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
        populateExposedToolsCombos(true);
    }

    private void populateExposedToolsCombos(boolean selectAllWhenNothingSelected) {
        Set<String> defaultIds = this.toolSpecPersistenceService.getDefaultToolIds();
        List<ToolSpec> all = this.toolSpecService.getToolSpecList();
        Set<String> exposedIds = this.toolSpecService.getToolMcpServerSetting().exposedToolIds();

        List<ToolSpec> exposedCustoms = ExposedToolsSelector.customsFrom(all, defaultIds).stream()
                .filter(spec -> exposedIds.contains(spec.toolId())).toList();
        List<ToolSpec> exposedBuiltins = ExposedToolsSelector
                .exposableBuiltinsFrom(all, defaultIds, this.toolActivationCalculator).stream()
                .filter(spec -> exposedIds.contains(spec.toolId())).toList();
        List<ToolSpec> exposedComposed = this.toolSpecService.getExternalToolSpecs();
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
            if (selectAllWhenNothingSelected) {
                exposedCustoms.forEach(this.customToolsComboBox::select);
                exposedBuiltins.forEach(this.builtinToolsComboBox::select);
            }
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
        populateExposedToolsCombos();
        applyStoredChatToolSelection();
        applyDynamicToolsUi();
        registerClientActionBridge();
    }

    private void registerClientActionBridge() {
        List<String> available = this.clientActionRegistry.availableNames();
        if (!available.isEmpty()) {
            getElement().executeJs(
                    "window.Saip=window.Saip||{};"
                            + "window.Saip.invoke=(a,p)=>$0.$server.invokeClientAction(a,p);"
                            + "window.Saip.actions=window.Saip.actions||{};"
                            + "$1.forEach((n)=>{window.Saip.actions[n]=true;});",
                    getElement(), available);
        }
    }

    @ClientCallable
    public void invokeClientAction(String action, String payload) {
        this.clientActionRegistry.find(action).ifPresent(a -> a.handle(payload));
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
        if (this.dynamicToolsCheckbox.getValue()) {
            this.exposedToolsDisplayBox.addClassName("dynamic-active");
            this.exposedToolsDisplayBox.setReadOnly(false);
            this.exposedToolsDisplayBox.deselectAll();
            this.exposedToolsDisplayBox.setPlaceholder("Dynamic — searching all tools");
            this.exposedToolsDisplayBox.setReadOnly(true);
            return;
        }
        this.exposedToolsDisplayBox.removeClassName("dynamic-active");
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
        ReasoningEffort reasoning = Objects.requireNonNullElse(this.reasoningSelect.getValue(), ReasoningEffort.DEFAULT);
        return new ChatToolPreferences(this.useBuiltinMcpCheckbox.getValue(), exposedToolIds, ragDocInfoIds,
                mcpServerNames, reasoning, this.dynamicToolsCheckbox.getValue());
    }

    private Set<String> selectedChatToolNames() {
        Set<String> names = new LinkedHashSet<>();
        this.customToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        this.builtinToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        this.composedToolsComboBox.getSelectedItems().forEach(spec -> names.add(spec.name()));
        return names;
    }

    private void applyReasoningActiveStyle() {
        ReasoningEffort value = this.reasoningSelect.getValue();
        this.reasoningSelect.setClassName("control-active", value != null && value != ReasoningEffort.OFF);
    }

    private void applyDynamicToolsUi() {
        int minTools = this.toolSearch.minTools();
        boolean gateOk = searchablePoolSize() >= minTools;
        this.dynamicToolsCheckbox.setEnabled(gateOk);
        if (!gateOk && this.dynamicToolsCheckbox.getValue()) this.dynamicToolsCheckbox.setValue(false);
        this.dynamicToolsNote.setText(gateOk
                ? "Let the model find tools on demand by searching instead of picking them below — it reaches all "
                        + "Local-Passed built-in tools plus any exposed external tools while keeping context small."
                : "Needs at least " + minTools + " searchable tools to enable — add tools in Tool Studio.");
        this.dynamicToolsNote.getStyle().set("color",
                gateOk ? "var(--lumo-secondary-text-color)" : "var(--lumo-error-text-color)");
        boolean dynamic = this.dynamicToolsCheckbox.getValue();
        boolean manual = this.useBuiltinMcpCheckbox.getValue();
        this.customToolsComboBox.setEnabled(manual && hasItems(this.customToolsComboBox));
        this.builtinToolsComboBox.setEnabled(manual && hasItems(this.builtinToolsComboBox));
        this.composedToolsComboBox.setEnabled(manual && hasItems(this.composedToolsComboBox));
        boolean hasServers = this.mcpToolProviderComboBox.getListDataView().getItems().findAny().isPresent();
        this.mcpToolProviderComboBox.setEnabled(!dynamic && hasServers);
        this.mcpToolProviderComboBox.setPlaceholder(!hasServers ? "No MCP servers connected"
                : dynamic ? "Disabled in Dynamic mode" : "Select MCP servers for tools");
    }

    private static boolean hasItems(MultiSelectComboBox<ToolSpec> combo) {
        return combo.getListDataView().getItems().findAny().isPresent();
    }

    private long searchablePoolSize() {
        long authored = this.toolSpecService.getToolSpecList().stream().filter(spec -> !spec.draft()).count();
        return authored + this.compositionProvider.getToolCallbacks().length;
    }

    private List<ToolCallback> dynamicToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();
        this.toolSpecService.getToolSpecList().stream().filter(spec -> !spec.draft())
                .map(ToolSpec::toolCallback).filter(Objects::nonNull).forEach(callbacks::add);
        Arrays.stream(this.compositionProvider.getToolCallbacks()).forEach(callbacks::add);
        return callbacks;
    }

    private void applyStreamingState(boolean streaming) {
        this.userPromptTextArea.setReadOnly(streaming);
        this.micButton.setEnabled(!streaming);
        this.submitButton.setIcon(VaadinUtils.styledIcon(
                (streaming ? VaadinIcon.STOP : VaadinIcon.ARROW_CIRCLE_UP).create()));
        this.submitButton.setTooltipText(streaming ? "Stop" : "Submit");
        refreshPendingImagesBar();
    }

    private void renderPersistedMessages() {
        this.messageListLayout.removeAll();
        ChatContentManager renderer = new ChatContentManager(null, null, this.zoneIdFuture, this.chatHistory);
        this.chatHistory.messagesSupplier().get().forEach(message ->
                renderer.initMarkdownMessage(this.messageListLayout, message, message.getMessageType()));
        this.messageListLayout.add(this.scrollSpacer);
        this.messageListLayout.getChildren().filter(c -> c != this.scrollSpacer).reduce((a, b) -> b)
                .ifPresent(last -> last.scrollIntoView(DefaultScrollOptions));
    }

    private void refreshAfterBackgroundStream() {
        if (!isAttached()) return;
        ChatHistory latest = this.chatHistoryService.getChatHistory(this.chatHistory.conversationId());
        if (latest != null) {
            this.chatHistory = latest;
            renderPersistedMessages();
        }
        applyStreamingState(false);
    }

    private Disposable inputEvent(String userPrompt) {
        this.chatHistory = this.chatHistory.withToolPreferences(currentToolPreferences());
        this.chatHistoryService.updateChatHistory(this.chatHistory);
        ChatContentManager chatContentManager = new ChatContentManager(this.messageListLayout, userPrompt, zoneIdFuture,
                this.chatHistory);
        List<PendingImage> sentImages = List.copyOf(this.pendingImages);
        this.pendingImages.clear();
        refreshPendingImagesBar();
        if (!sentImages.isEmpty())
            chatContentManager.userMessage.addAttachments(attachmentRowOf(sentImages.stream()
                    .map(image -> thumbnailOf(image.mimeType(), image.bytes(), image.fileName())).toList()));

        List<String> selectedDocInfoIds =
                this.documentsComboBox.getSelectedItems().stream().map(VectorStoreDocumentInfo::docInfoId).toList();
        Set<McpServerInfo> selectedItems = this.mcpToolProviderComboBox.getSelectedItems();
        UI ui = VaadinUtils.getUi(this);
        List<ToolCallback> toolCallbacks;
        if (this.dynamicToolsCheckbox.getValue()) {
            toolCallbacks = dynamicToolCallbacks();
        } else {
            toolCallbacks = new ArrayList<>(selectedItems.stream()
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
        }

        trackChatMessageSent(ui, toolCallbacks.size(), sentImages.size(), !selectedDocInfoIds.isEmpty());
        AtomicBoolean liveSaved = new AtomicBoolean();
        Runnable saveOnFirstActivity = () -> {
            if (liveSaved.compareAndSet(false, true))
                this.chatHistoryService.updateChatHistory(this.chatHistory);
        };
        return this.chatService.stream(this.chatHistory, userPrompt,
                        this.chatService.buildFilterExpression(selectedDocInfoIds), this.completeChatHistoryConsumer,
                        toolCallbacks, o -> {
                            saveOnFirstActivity.run();
                            ui.access(() -> {
                                chatContentManager.appendMcpToolProcessMessage(o);
                                trackToolCalled(ui, o);
                            });
                        },
                        o -> ui.access(() -> chatContentManager.appendRagProcessMessage(o)),
                        o -> {
                            saveOnFirstActivity.run();
                            ui.access(() -> chatContentManager.appendBotThinkProcessMessage(o));
                        },
                        round -> ui.access(() -> chatContentManager.applyRoundUsage(round)),
                        signalType -> {
                            try {
                                ui.access(() -> {
                                    if (SignalType.CANCEL.equals(signalType)) chatContentManager.markStopped();
                                    doFinally(chatContentManager);
                                });
                            } finally {
                                releaseStream();
                            }
                        }, new ChatHumanQuestionHandler(ui, this.agentLoop.approvalTimeoutSeconds()),
                        new ChatFileUploadHandler(ui, this.fileUploadStore, this.chatHistory.conversationId(),
                                this.agentLoop.dialogTimeoutSeconds()),
                        new ChatImageReferenceHandler(ui, this.imageStore, this.chatHistory.conversationId(),
                                this.agentLoop.dialogTimeoutSeconds()),
                        this.reasoningSelect.getValue(), sentImages.stream()
                                .map(image -> Media.builder().id(image.hash()).name(image.fileName())
                                        .mimeType(MimeType.valueOf(image.mimeType())).data(image.bytes()).build())
                                .toList())
                .doOnError(throwable -> {
                    try {
                        ui.access(() -> {
                            chatContentManager.markError(throwable);
                            VaadinUtils.showErrorNotification(ChatErrorMessages.friendly(throwable));
                            doFinally(chatContentManager);
                        });
                    } finally {
                        releaseStream();
                    }
                })
                .subscribe(content -> {
                    saveOnFirstActivity.run();
                    ui.access(() -> chatContentManager.append(content));
                });
    }

    private void trackChatMessageSent(UI ui, int toolCount, int imageCount, boolean ragEnabled) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("provider", this.chatService.getChatProvider().name().toLowerCase(Locale.ROOT));
        params.put("model", this.chatHistory.chatOptions().getModel());
        params.put("reasoning", Objects.requireNonNullElse(this.reasoningSelect.getValue(),
                ReasoningEffort.DEFAULT).name());
        params.put("dynamic_tools", this.dynamicToolsCheckbox.getValue());
        params.put("tool_count", toolCount);
        params.put("image_count", imageCount);
        params.put("rag_enabled", ragEnabled);
        this.usageEventTracker.track(ui, "chat_message_sent", params);
    }

    private void trackToolCalled(UI ui, Object processMessage) {
        if (processMessage instanceof AgentLoopHarness.McpToolResult toolResult)
            this.usageEventTracker.track(ui, "tool_called",
                    this.usageAnalyticsService.toolCalledParams(toolResult.name()));
    }

    private void onImageProcessingStarted() {
        if (this.pendingImages.size() + this.inFlightImageAttaches >= MAX_IMAGES) {
            VaadinUtils.showErrorNotification("You can attach up to " + MAX_IMAGES + " images.");
            return;
        }
        this.inFlightImageAttaches++;
        refreshPendingImagesBar();
    }

    private void onImageAttachError(String message) {
        if (this.inFlightImageAttaches > 0) this.inFlightImageAttaches--;
        refreshPendingImagesBar();
        VaadinUtils.showErrorNotification(message);
    }

    private void onImageAttached(String fileName, byte[] bytes, String mimeType, String exifJson) {
        if (this.inFlightImageAttaches > 0) this.inFlightImageAttaches--;
        if (this.pendingImages.size() >= MAX_IMAGES) {
            refreshPendingImagesBar();
            return;
        }
        ChatImageStore.Stored stored;
        try {
            stored = this.imageStore.store(this.chatHistory.conversationId(), bytes, fileName, mimeType, exifJson);
        } catch (IOException e) {
            refreshPendingImagesBar();
            VaadinUtils.showErrorNotification("Could not save the image: " + e.getMessage());
            return;
        }
        this.pendingImages.add(new PendingImage(stored.hash(), fileName, mimeType, bytes));
        refreshPendingImagesBar();
        warnIfModelLacksVision();
    }

    private void refreshPendingImagesBar() {
        this.pendingImagesBar.removeAll();
        for (PendingImage image : List.copyOf(this.pendingImages)) {
            this.pendingImagesBar.add(pendingImageChip(image));
        }
        for (int i = 0; i < this.inFlightImageAttaches; i++) {
            this.pendingImagesBar.add(imageSkeletonChip());
        }
        int used = this.pendingImages.size() + this.inFlightImageAttaches;
        if (used > 0) {
            Span count = new Span(used + " / " + MAX_IMAGES);
            count.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)").set("align-self", "center")
                    .set("margin-left", "var(--lumo-space-xs)");
            this.pendingImagesBar.add(count);
        }
        this.pendingImagesBar.setVisible(used > 0);
        if (this.attachButton != null) this.attachButton.setEnabled(used < MAX_IMAGES
                && !this.streamRegistry.isStreaming(this.chatHistory.conversationId()));
    }

    private Div pendingImageChip(PendingImage image) {
        Div chip = new Div();
        chip.getStyle().set("position", "relative").set("width", "58px").set("height", "58px")
                .set("flex", "0 0 auto");
        Image thumbnail = thumbnailOf(image.mimeType(), image.bytes(), image.fileName());
        thumbnail.getStyle().set("width", "58px").set("height", "58px").set("object-fit", "cover")
                .set("cursor", "zoom-in").set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 1px 4px rgba(0, 0, 0, 0.18)");
        thumbnail.addClickListener(e -> openImageLightbox(image));
        Icon closeIcon = VaadinIcon.CLOSE_SMALL.create();
        closeIcon.getStyle().set("width", "12px").set("height", "12px").set("color", "var(--lumo-base-color)");
        Div remove = new Div(closeIcon);
        remove.getElement().setAttribute("title", "Remove " + image.fileName());
        remove.getStyle().set("position", "absolute").set("top", "-7px").set("right", "-7px")
                .set("width", "18px").set("height", "18px").set("border-radius", "50%")
                .set("background", "var(--lumo-contrast-70pct)").set("cursor", "pointer")
                .set("display", "flex").set("align-items", "center").set("justify-content", "center")
                .set("box-shadow", "0 1px 3px rgba(0, 0, 0, 0.35)");
        remove.addClickListener(e -> {
            this.pendingImages.remove(image);
            refreshPendingImagesBar();
        });
        chip.add(thumbnail, remove);
        return chip;
    }

    private Div imageSkeletonChip() {
        Div skeleton = new Div();
        skeleton.addClassName("saip-attach-skeleton");
        skeleton.getStyle().set("width", "58px").set("height", "58px").set("flex", "0 0 auto")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)");
        return skeleton;
    }

    private void openImageLightbox(PendingImage image) {
        Dialog dialog = new Dialog();
        dialog.setCloseOnOutsideClick(true);
        dialog.setCloseOnEsc(true);
        Image full = thumbnailOf(image.mimeType(), image.bytes(), image.fileName());
        full.getStyle().set("max-width", "84vw").set("max-height", "84vh").set("object-fit", "contain")
                .set("cursor", "zoom-out");
        full.addClickListener(e -> dialog.close());
        dialog.add(full);
        dialog.open();
    }

    private static Image thumbnailOf(String mimeType, byte[] bytes, String fileName) {
        Image image = new Image("data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes),
                fileName);
        image.getStyle().set("border-radius", "var(--lumo-border-radius-m)").set("display", "block");
        return image;
    }

    private static HorizontalLayout attachmentRowOf(List<Image> thumbnails) {
        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(false);
        row.setPadding(false);
        row.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-xs)")
                .set("margin-bottom", "var(--lumo-space-xs)");
        thumbnails.forEach(thumbnail -> {
            thumbnail.getStyle().set("max-height", "160px").set("max-width", "240px").set("object-fit", "contain");
            row.add(thumbnail);
        });
        return row;
    }

    private void warnIfModelLacksVision() {
        // Off the session-locked attach thread: the probe is a blocking HTTP call and a slow Ollama must not freeze the UI.
        String model = this.chatHistory.chatOptions().getModel();
        UI ui = VaadinUtils.getUi(this);
        CompletableFuture.supplyAsync(() -> this.visionCapabilityService.check(model))
                .orTimeout(6, TimeUnit.SECONDS)
                .whenComplete((support, error) -> {
                    if (error != null || support == null
                            || support == VisionCapabilityService.VisionSupport.SUPPORTED)
                        return;
                    ui.access(() -> VaadinUtils.showErrorNotification(
                            this.visionCapabilityService.warningFor(support, model)));
                });
    }

    private void releaseStream() {
        this.streamRegistry.finish(this.chatHistory.conversationId());
    }

    private void doFinally(ChatContentManager chatContentManager) {
        chatContentManager.doFinally();
        releaseStream();
        applyStreamingState(false);
        this.userPromptTextArea.setEnabled(true);
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
            case DEFAULT -> "Default";
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

    static List<String> extractActionBlocks(Object responseData) {
        String text = responseData == null ? "" : String.valueOf(responseData);
        if (!text.contains(ACTION_BLOCK_MARKER)) return List.of();
        text = unwrapActionContent(text);
        List<String> blocks = new ArrayList<>();
        int from = 0;
        while (true) {
            int start = text.indexOf(ACTION_BLOCK_MARKER, from);
            if (start < 0) break;
            int end = text.indexOf("\n```", start + ACTION_BLOCK_MARKER.length());
            if (end < 0) break;
            blocks.add(text.substring(start, end + 4));
            from = end + 4;
        }
        return blocks;
    }

    static List<String> actionBlocksToAppend(String existing, Collection<String> blocks) {
        if (Objects.isNull(blocks) || blocks.isEmpty()) return List.of();
        List<String> missing = new ArrayList<>();
        for (String block : blocks)
            if (Objects.isNull(existing) || !existing.contains(block)) missing.add(block);
        return missing;
    }

    static String textWithActionBlocks(String text, Object blocks) {
        if (!(blocks instanceof List<?> list) || list.isEmpty()) return text;
        StringBuilder merged = new StringBuilder(Objects.isNull(text) ? "" : text);
        for (String block : actionBlocksToAppend(merged.toString(), list.stream().map(String::valueOf).toList()))
            merged.append("\n\n").append(block).append("\n");
        return merged.toString();
    }

    private static String unwrapActionContent(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("[") && !trimmed.startsWith("{") && !trimmed.startsWith("\"")) return text;
        try {
            JsonNode node = ACTION_MAPPER.readTree(trimmed);
            if (node.isTextual()) return node.asText();
            if (node.isObject() && node.has("text")) return node.get("text").asText();
            if (node.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode el : node) {
                    sb.append(el.isObject() && el.has("text") ? el.get("text").asText() : el.asText());
                }
                if (!sb.isEmpty()) return sb.toString();
            }
        } catch (RuntimeException ignore) {
        }
        return text;
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
        private static final String ACTION_BLOCKS = "actionBlocks";
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
        private final List<String> pendingActionBlocks = new ArrayList<>();
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
            if (content instanceof AgentLoopHarness.McpAssistantToolCall toolCall) {
                toolCall.toolCalls().forEach(tc -> {
                    this.mcpToolCallCount++;
                    this.mcpToolNames.add(tc.name());
                });
            }
            if (content instanceof AgentLoopHarness.McpToolResult toolResult) {
                this.pendingActionBlocks.addAll(extractActionBlocks(toolResult.responseData()));
            }
            if (AgentLoopHarness.MCP_TOOL_EXECUTION_COMPLETED_MESSAGE.equals(contentStr)) {
                this.mcpToolProcessEndTimestamp = timestamp;
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

        private List<String> appendPendingActionBlocks() {
            if (this.pendingActionBlocks.isEmpty() || Objects.isNull(this.botResponse)) return List.of();
            List<String> appended =
                    actionBlocksToAppend(this.botResponse.getRawMarkdown(), this.pendingActionBlocks);
            appended.forEach(block -> this.botResponse.appendMarkdown("\n\n" + block + "\n"));
            this.pendingActionBlocks.clear();
            return appended;
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
            span.getStyle().set("display", "block").set("overflow", "hidden")
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
            return countStr + " · " + String.join(", ", titles);
        }

        private static String formatMcpExtra(int callCount, Collection<String> toolNames) {
            if (callCount <= 0) return null;
            String callStr = callCount + (callCount == 1 ? " call" : " calls");
            if (Objects.isNull(toolNames) || toolNames.isEmpty()) return callStr;
            return callStr + " · " + String.join(", ", toolNames);
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
            if (MessageType.TOOL.equals(messageType)) return;
            if (message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty()
                    && (Objects.isNull(text) || text.isBlank())
                    && !message.getMetadata().containsKey(ACTION_BLOCKS)) return;
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
            if (MessageType.USER.equals(messageType)) {
                ChatMessage userChatMessage = buildMessage(text, messageType, messageTimestamp);
                List<Image> thumbnails = restoredThumbnailsOf(metadata);
                if (!thumbnails.isEmpty()) userChatMessage.addAttachments(attachmentRowOf(thumbnails));
                messageListLayout.add(userChatMessage);
            }
            components.stream().sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond)
                    .forEach(messageListLayout::add);
            if (!MessageType.USER.equals(messageType)) {
                ChatMessage assistant = buildMessage(textWithActionBlocks(text, metadata.get(ACTION_BLOCKS)),
                        messageType, messageTimestamp);
                messageListLayout.add(assistant);
                fillResponseMetrics(assistant, metadata);
            } else if (metadata.containsKey(ACTION_BLOCKS)) {
                ChatMessage assistant = buildMessage(textWithActionBlocks(null, metadata.get(ACTION_BLOCKS)),
                        MessageType.ASSISTANT, messageTimestamp);
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

        private List<Image> restoredThumbnailsOf(Map<String, Object> metadata) {
            Object refs = metadata.get(ChatService.USER_IMAGES);
            if (!(refs instanceof List<?> refList)) return List.of();
            String conversationId = ChatContentView.this.chatHistory.conversationId();
            List<Image> thumbnails = new ArrayList<>();
            for (Object entry : refList) {
                if (!(entry instanceof Map<?, ?> ref)) continue;
                String hash = Objects.toString(ref.get(ChatService.USER_IMAGE_HASH), null);
                if (Objects.isNull(hash)) continue;
                String fileName = Objects.toString(ref.get(ChatService.USER_IMAGE_FILE_NAME), hash);
                try {
                    ChatContentView.this.imageStore.load(conversationId, hash).ifPresent(loaded ->
                            thumbnails.add(thumbnailOf(loaded.mimeType(), loaded.bytes(), fileName)));
                } catch (IOException e) {
                    logger.warn("chat-image.restore-failed hash={} error={}", hash, e.getMessage());
                }
            }
            return thumbnails;
        }

        private static long timestampOf(Map<String, Object> metadata) {
            Object timestamp = metadata.get(ChatHistory.TIMESTAMP);
            return timestamp == null ? 0L : Long.parseLong(timestamp.toString());
        }

        private ChatMessage buildMessage(String message, MessageType messageType, long epochMillis) {
            ChatMessage chatMessage = buildMessage(message, messageType.getValue().toUpperCase(),
                    messageType.ordinal(), epochMillis);
            if (MessageType.USER.equals(messageType)) {
                chatMessage.usePlainText();
                chatMessage.addClassName("user-message");
            } else {
                chatMessage.addClassName("assistant-message");
            }
            chatMessage.addActionBar(buildMessageActionBar(chatMessage, messageType));
            return chatMessage;
        }

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
                collapseProcessDetails(this.mcpToolProcessDetails);
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
            Optional<Map<String, Object>> lastUserMetadata = messageList.flatMap(list -> list.reversed().stream()
                    .filter(message -> MessageType.USER.equals(message.getMessageType())).findFirst())
                    .map(Message::getMetadata);
            lastUserMetadata.ifPresent(metadata -> updateMetadata(metadata, this.startTimestamp));
            Optional<Map<String, Object>> metadataAsOpt = messageList.map(List::getLast).map(Message::getMetadata);

            if (this.isFirstAssistantResponse) {
                boolean noProcessActivity = Objects.isNull(this.ragProcessMessageBuilder)
                        && Objects.isNull(this.thinkProcessMessageBuilder)
                        && Objects.isNull(this.mcpToolProcessMessagesBuilder);
                if (!this.pendingActionBlocks.isEmpty()) {
                    initBotResponse(System.currentTimeMillis());
                } else {
                    if (Objects.nonNull(this.botResponse) && Objects.nonNull(this.messageListLayout))
                        this.messageListLayout.remove(this.botResponse);
                    if (noProcessActivity) {
                        if (Objects.nonNull(this.processListLayout) && Objects.nonNull(this.messageListLayout))
                            this.messageListLayout.remove(this.processListLayout);
                        saveAndRenderStreamStatus(messageList.map(List::getLast).map(Message::getMetadata));
                        return;
                    }
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
            List<String> appendedBlocks = appendPendingActionBlocks();
            if (!appendedBlocks.isEmpty()) metadataAsOpt
                    .ifPresent(metadata -> metadata.put(ACTION_BLOCKS, new ArrayList<>(appendedBlocks)));
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

    private record PendingImage(String hash, String fileName, String mimeType, byte[] bytes) {}
}
