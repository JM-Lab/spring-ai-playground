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

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.ScrollOptions;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.internal.Pair;
import org.springaicommunity.playground.service.SpringAiPlaygroundRagAdvisor;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpToolCallingManager;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SttMicButton;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.vaadin.firitin.components.messagelist.MarkdownMessage;
import reactor.core.Disposable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springaicommunity.playground.service.chat.ChatHistory.TIMESTAMP;
import static org.springaicommunity.playground.service.chat.ChatHistoryPersistenceService.CONVERSATION_ID;
import static org.springframework.ai.chat.messages.MessageType.USER;

@JsModule("./playground/chat-stt.js")
public class ChatContentView extends VerticalLayout {
    private static final String LAST_SELECTED_RAG_DOC_INFO_IDS = "lastSelectedRagDocInfoIds";
    private static final String LAST_SELECTED_MCP_CONNECTION_INFOS = "lastSelectedMcpConnectionInfos";

    private static final ScrollOptions DefaultScrollOptions = new ScrollOptions();
    static {
        DefaultScrollOptions.setBlock(ScrollOptions.Alignment.END);
        DefaultScrollOptions.setInline(ScrollOptions.Alignment.NEAREST);
    }

    private static final int PROMPT_TOP_MARGIN_PX = 20;

    private final VerticalLayout messageListLayout;
    private final Scroller messageScroller;
    private final com.vaadin.flow.component.html.Div scrollSpacer;
    private final TextArea userPromptTextArea;
    private final MultiSelectComboBox<VectorStoreDocumentInfo> documentsComboBox;
    private final MultiSelectComboBox<McpServerInfo> mcpToolProviderComboBox;
    private final ChatService chatService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private final PersistentUiDataStorage persistentUiDataStorage;
    private final ChatHistory chatHistory;
    private final McpClientService mcpClientService;
    private Disposable currentStream;

    public ChatContentView(PersistentUiDataStorage persistentUiDataStorage, ChatService chatService,
            ChatHistory chatHistory, Consumer<ChatHistory> completeChatHistoryConsumer,
            McpClientService mcpClientService) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatHistory = chatHistory;
        this.chatService = chatService;
        this.completeChatHistoryConsumer = completeChatHistoryConsumer;
        this.mcpClientService = mcpClientService;

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

        this.mcpToolProviderComboBox = new MultiSelectComboBox<>();
        this.mcpToolProviderComboBox.setPlaceholder("No MCP Connections for Tools");
        this.mcpToolProviderComboBox.setWidth("300px");
        this.mcpToolProviderComboBox.setTooltipText("Access Tools via MCP connections");
        this.mcpToolProviderComboBox.setSelectedItemsOnTop(true);
        this.mcpToolProviderComboBox.setItemLabelGenerator(
                mcpServerInfo -> mcpServerInfo.serverName() + "(" + mcpServerInfo.mcpTransportType() + ")");
        this.mcpToolProviderComboBox.setItems(this.chatService.getLiveMcpServerInfos());

        this.documentsComboBox = new MultiSelectComboBox<>();
        this.documentsComboBox.setPlaceholder("No documents for RAG");
        this.documentsComboBox.setWidth("300px");
        this.documentsComboBox.setTooltipText("RAG with documents stored in VectorDB.");
        this.documentsComboBox.setSelectedItemsOnTop(true);
        this.documentsComboBox.setItemLabelGenerator(VectorStoreDocumentInfo::title);
        this.documentsComboBox.setItems(this.chatService.getExistDocumentInfoList());

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

        Button micButton = new SttMicButton(this.userPromptTextArea);
        Icon submitIcon = VaadinUtils.styledLargeIcon(VaadinIcon.ARROW_CIRCLE_UP.create());
        Button submitButton = new Button(submitIcon);
        submitButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        submitButton.setTooltipText("Submit");

        submitButton.addClickListener(e -> {
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


        HorizontalLayout userInputMenuLayout = new HorizontalLayout(toolLayout, ragLayout);
        VerticalLayout userInputLayout = new VerticalLayout(userInputMenuLayout, this.userPromptTextArea);
        userInputLayout.setWidthFull();
        userInputLayout.setMargin(false);
        userInputLayout.setSpacing(false);
        userInputLayout.setPadding(false);
        add(messageScroller, userInputLayout);
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
        this.persistentUiDataStorage.loadData(LAST_SELECTED_RAG_DOC_INFO_IDS, new TypeReference<Set<String>>() {},
                docInfoIds -> {
                    if (docInfoIds != null && !docInfoIds.isEmpty()) {
                        this.documentsComboBox.select(this.chatService.getExistDocumentInfoList().stream()
                                .filter(vectorStoreDocumentInfo -> docInfoIds.contains(
                                        vectorStoreDocumentInfo.docInfoId())).toList());
                    }
                });
        this.persistentUiDataStorage.loadData(LAST_SELECTED_MCP_CONNECTION_INFOS,
                new TypeReference<Map<McpTransportType, Set<String>>>() {},
                typeServerNameMap -> {
                    if (typeServerNameMap != null && !typeServerNameMap.isEmpty()) {
                        List<McpServerInfo> mcpServerInfos = this.chatService.getLiveMcpServerInfos().stream()
                                .filter(mcpServerInfo -> Optional.ofNullable(
                                                typeServerNameMap.get(mcpServerInfo.mcpTransportType()))
                                        .filter(serverNameSet -> serverNameSet.contains(mcpServerInfo.serverName()))
                                        .isPresent()).toList();
                        this.mcpToolProviderComboBox.select(mcpServerInfos);
                    }
                });
    }

    private Disposable inputEvent(CompletableFuture<ZoneId> zoneIdFuture, String userPrompt) {
        ChatContentManager chatContentManager = new ChatContentManager(this.messageListLayout, userPrompt, zoneIdFuture,
                this.chatHistory);

        List<String> selectedDocInfoIds =
                this.documentsComboBox.getSelectedItems().stream().map(VectorStoreDocumentInfo::docInfoId).toList();
        this.persistentUiDataStorage.saveData(LAST_SELECTED_RAG_DOC_INFO_IDS, selectedDocInfoIds);
        Set<McpServerInfo> selectedItems = this.mcpToolProviderComboBox.getSelectedItems();
        List<ToolCallback> toolCallbacks = selectedItems.stream().map(this.mcpClientService::buildToolCallbackProviders)
                .flatMap(List::stream).map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream).toList();
        this.persistentUiDataStorage.saveData(LAST_SELECTED_MCP_CONNECTION_INFOS,
                selectedItems.stream().collect(Collectors.groupingBy(McpServerInfo::mcpTransportType,
                        Collectors.mapping(McpServerInfo::serverName, Collectors.toList()))));

        UI ui = VaadinUtils.getUi(this);
        return this.chatService.stream(this.chatHistory, userPrompt,
                        this.chatService.buildFilterExpression(selectedDocInfoIds), this.completeChatHistoryConsumer,
                        toolCallbacks, o -> ui.access(() -> chatContentManager.appendMcpToolProcessMessage(o)),
                        o -> ui.access(() -> chatContentManager.appendRagProcessMessage(o)),
                        o -> ui.access(() -> chatContentManager.appendBotThinkProcessMessage(o)),
                        signalType -> ui.access(() -> {
                            if (reactor.core.publisher.SignalType.CANCEL.equals(signalType))
                                chatContentManager.markStopped();
                            doFinally(chatContentManager);
                        }))
                .doOnError(throwable -> ui.access(() -> {
                    chatContentManager.markError(throwable);
                    VaadinUtils.showErrorNotification(throwable.getMessage());
                    doFinally(chatContentManager);
                }))
                .subscribe(content -> ui.access(() -> chatContentManager.append(content)));
    }

    private void doFinally(ChatContentManager chatContentManager) {
        chatContentManager.doFinally();
        this.userPromptTextArea.setReadOnly(false);
        this.userPromptTextArea.setEnabled(true);
        if (this.currentStream != null) {
            this.currentStream.dispose();
            this.currentStream = null;
        }
        this.messageScroller.scrollToBottom();
    }

    public ChatOptions getChatOption() {
        return this.chatHistory.chatOptions();
    }

    public String getSystemPrompt() {return this.chatHistory.systemPrompt();}

    public String getConversationId() {
        return this.chatHistory.conversationId();
    }

    private class ChatContentManager {
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
        private static final String MCP_TOOL_PROCESS = "MCP TOOLS";
        private static final String MCP_TOOL_PROCESS_TIMESTAMP = "mcpToolProcessTimestamp";
        private static final String MCP_TOOL_PROCESS_END_TIMESTAMP = "mcpToolProcessEndTimestamp";
        private static final String MCP_TOOL_PROCESS_MESSAGES = "mcpToolProcessMessages";
        private static final String MCP_TOOL_PROCESS_CALL_COUNT = "mcpToolProcessCallCount";
        private static final String MCP_TOOL_PROCESS_TOOL_NAMES = "mcpToolProcessToolNames";
        private static final String STREAM_STATUS = "streamStatus";
        private static final String STREAM_STATUS_STAGE = "streamStatusStage";
        private static final String STREAM_STATUS_MESSAGE = "streamStatusMessage";
        private static final String STAGE_STARTING = "STARTING";
        private static final String STAGE_RAG = "RAG DOCUMENTS";
        private static final String STAGE_THINK = "THINK";
        private static final String STAGE_MCP = "MCP TOOLS";
        private static final String STAGE_ASSISTANT = "ASSISTANT";
        private static final String STATUS_STOPPED = "STOPPED";
        private static final String STATUS_ERROR = "ERROR";
        private static final DateTimeFormatter DATE_TIME_FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        private final CompletableFuture<ZoneId> zoneIdFuture;
        private final Supplier<List<Message>> messagesSupplier;
        private VerticalLayout messageListLayout;
        private VerticalLayout processListLayout;
        private long startTimestamp;
        private long responseTimestamp;
        private MarkdownMessage botResponse;
        private boolean isFirstAssistantResponse;
        private MarkdownMessage ragProcessMessage;
        private long ragProcessTimestamp;
        private long ragProcessEndTimestamp;
        private int ragRetrievedDocCount;
        private final java.util.LinkedHashSet<String> ragRetrievedDocTitles = new java.util.LinkedHashSet<>();
        private Details ragProcessDetails;
        private StringBuilder ragProcessMessageBuilder;
        private MarkdownMessage botThinkResponse;
        private long botThinkTimestamp;
        private long botThinkEndTimestamp;
        private Details thinkDetails;
        private StringBuilder thinkProcessMessageBuilder;
        private MarkdownMessage mcpToolProcessMessage;
        private long mcpToolProcessTimestamp;
        private long mcpToolProcessEndTimestamp;
        private int mcpToolCallCount;
        private final java.util.LinkedHashSet<String> mcpToolNames = new java.util.LinkedHashSet<>();
        private Details mcpToolProcessDetails;
        private StringBuilder mcpToolProcessMessagesBuilder;
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
            MarkdownMessage userMarkdownMessage = buildMarkdownMessage(userPrompt, USER, startTimestamp);
            this.processListLayout = buildProcessListLayout();
            this.botResponse = buildMarkdownMessage(null, MessageType.ASSISTANT, System.currentTimeMillis());
            this.botResponse.addClassName("blink");
            this.isFirstAssistantResponse = true;
            this.messageListLayout.remove(ChatContentView.this.scrollSpacer);
            this.messageListLayout.add(userMarkdownMessage, this.processListLayout, this.botResponse,
                    ChatContentView.this.scrollSpacer);
            anchorPromptToTop(userMarkdownMessage);
        }

        private void anchorPromptToTop(MarkdownMessage userMessage) {
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

        private MarkdownMessage getRagProcessMessage(long timestamp) {
            if (Objects.isNull(this.ragProcessMessage)) {
                this.ragProcessTimestamp = timestamp;
                this.ragProcessMessage = buildMarkdownMessage(null, RAG_PROCESS, this.ragProcessTimestamp);
                this.processListLayout.add(buildProcessDetails(RAG_PROCESS, getRagProcessDetails(),
                        this.ragProcessMessage));
            }
            return this.ragProcessMessage;
        }

        public void appendMcpToolProcessMessage(Object content) {
            this.currentStage = STAGE_MCP;
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
                        formatMcpExtra(this.mcpToolCallCount, this.mcpToolNames));
            } else if (Objects.nonNull(this.mcpToolProcessDetails))
                this.mcpToolProcessDetails.setOpened(true);
        }

        private MarkdownMessage getMcpToolProcessMessage(long timestamp) {
            if (Objects.isNull(this.mcpToolProcessMessage)) {
                this.mcpToolProcessTimestamp = timestamp;
                this.mcpToolProcessMessage = buildMarkdownMessage(null, MCP_TOOL_PROCESS, this.mcpToolProcessTimestamp);
                this.processListLayout.add(buildProcessDetails(MCP_TOOL_PROCESS, getMcpToolProcessDetails(),
                        this.mcpToolProcessMessage));
            }
            return this.mcpToolProcessMessage;
        }

        public void appendBotThinkProcessMessage(Object content) {
            this.currentStage = STAGE_THINK;
            long timestamp = System.currentTimeMillis();
            String markdownSnippet = content.toString();
            getBotThinkResponse(timestamp).appendMarkdown(markdownSnippet);
            if (Objects.isNull(this.thinkProcessMessageBuilder))
                this.thinkProcessMessageBuilder = new StringBuilder();
            this.thinkProcessMessageBuilder.append(markdownSnippet);
        }

        private MarkdownMessage getBotThinkResponse(long timestamp) {
            if (Objects.isNull(this.botThinkResponse)) {
                this.botThinkTimestamp = timestamp;
                this.botThinkResponse = buildMarkdownMessage(null, THINK_PROCESS, this.botThinkTimestamp);
                this.processListLayout.add(buildProcessDetails(THINK_PROCESS, getThinkDetails(), this.botThinkResponse));
            }
            return this.botThinkResponse;
        }

        private static Details buildProcessDetails(String title, Details details, MarkdownMessage markdownMessage) {
            details.setSummary(buildDetailsSummary(title, 0L, 0L, null));
            details.add(markdownMessage);
            details.addThemeVariants(DetailsVariant.FILLED);
            details.setWidthFull();
            return details;
        }

        private static Span buildDetailsSummary(String title, long startMs, long endMs, String extraInfo) {
            StringBuilder sb = new StringBuilder(title);
            if (startMs > 0 && endMs > startMs) {
                long durationMs = endMs - startMs;
                String duration = durationMs < 1000 ? durationMs + "ms"
                        : String.format(java.util.Locale.ROOT, "%.1fs", durationMs / 1000.0);
                sb.append(" · ").append(duration);
            }
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

        private static String formatRagExtra(int docCount, java.util.Collection<String> titles) {
            if (docCount < 0) return null;
            String countStr = docCount + (docCount == 1 ? " doc" : " docs");
            if (Objects.isNull(titles) || titles.isEmpty()) return countStr;
            String joined = String.join(", ", titles);
            if (joined.length() > 40) joined = joined.substring(0, 37) + "...";
            return countStr + " · " + joined;
        }

        private static String formatMcpExtra(int callCount, java.util.Collection<String> toolNames) {
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
            Map<String, Object> metadata = message.getMetadata();

            List<Pair<Long, Component>> components = new ArrayList<>();

            String ragProcessMessages = (String) metadata.get(RAG_PROCESS_MESSAGES);
            if (Objects.nonNull(ragProcessMessages)) {
                Long ragProcessTimestamp = (Long) metadata.get(RAG_PROCESS_TIMESTAMP);
                Long ragEnd = (Long) metadata.get(RAG_PROCESS_END_TIMESTAMP);
                Integer ragDocCount = (Integer) metadata.get(RAG_PROCESS_DOC_COUNT);
                @SuppressWarnings("unchecked")
                java.util.Collection<String> ragTitles =
                        (java.util.Collection<String>) metadata.get(RAG_PROCESS_DOC_TITLES);
                Details details = ChatContentManager.buildProcessDetails(RAG_PROCESS, new Details(),
                        buildMarkdownMessage(ragProcessMessages, RAG_PROCESS, ragProcessTimestamp));
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
                        buildMarkdownMessage(thinkProcessMessages, THINK_PROCESS, thinkProcessTimestamp));
                updateDetailsSummary(details, THINK_PROCESS, thinkProcessTimestamp,
                        Objects.nonNull(thinkEnd) ? thinkEnd : 0L, null);
                details.setOpened(false);
                components.add(new Pair<>(thinkProcessTimestamp, details));
            }

            String mcpToolProcessMessages = (String) metadata.get(MCP_TOOL_PROCESS_MESSAGES);
            if (Objects.nonNull(mcpToolProcessMessages)) {
                Long mcpToolProcessTimestamp = (Long) metadata.get(MCP_TOOL_PROCESS_TIMESTAMP);
                Long mcpEnd = (Long) metadata.get(MCP_TOOL_PROCESS_END_TIMESTAMP);
                Integer mcpCallCount = (Integer) metadata.get(MCP_TOOL_PROCESS_CALL_COUNT);
                @SuppressWarnings("unchecked")
                java.util.Collection<String> mcpNames =
                        (java.util.Collection<String>) metadata.get(MCP_TOOL_PROCESS_TOOL_NAMES);
                Details details = ChatContentManager.buildProcessDetails(MCP_TOOL_PROCESS, new Details(),
                        buildMarkdownMessage(mcpToolProcessMessages, MCP_TOOL_PROCESS, mcpToolProcessTimestamp));
                updateDetailsSummary(details, MCP_TOOL_PROCESS, mcpToolProcessTimestamp,
                        Objects.nonNull(mcpEnd) ? mcpEnd : 0L,
                        formatMcpExtra(Objects.nonNull(mcpCallCount) ? mcpCallCount : 0, mcpNames));
                details.setOpened(false);
                components.add(new Pair<>(mcpToolProcessTimestamp, details));
            }
            if (USER.equals(messageType))
                messageListLayout.add(
                        buildMarkdownMessage(text, messageType, Long.parseLong(metadata.get(TIMESTAMP).toString())));
            components.stream().sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond)
                    .forEach(messageListLayout::add);
            if (!USER.equals(messageType))
                messageListLayout.add(
                        buildMarkdownMessage(text, messageType, Long.parseLong(metadata.get(TIMESTAMP).toString())));
            String streamStatus = (String) metadata.get(STREAM_STATUS);
            if (Objects.nonNull(streamStatus)) {
                Span indicator = buildStreamStatusIndicator(streamStatus, (String) metadata.get(STREAM_STATUS_STAGE),
                        (String) metadata.get(STREAM_STATUS_MESSAGE));
                if (Objects.nonNull(indicator)) messageListLayout.add(indicator);
            }
        }

        private MarkdownMessage buildMarkdownMessage(String message, MessageType messageType, long epochMillis) {
            MarkdownMessage markdownMessage =
                    buildMarkdownMessage(message, messageType.getValue().toUpperCase(), epochMillis);
            markdownMessage.setAvatarColor(MarkdownMessage.Color.AVATAR_PRESETS[messageType.ordinal()]);
            return markdownMessage;
        }

        private MarkdownMessage buildMarkdownMessage(String message, String name, long epochMillis) {
            LocalDateTime localDateTime = getLocalDateTime(epochMillis);
            MarkdownMessage markdownMessage = new MarkdownMessage(message, name, localDateTime);
            markdownMessage.setAutoScroll(false);
            markdownMessage.getElement().setProperty("time", getFormattedLocalDateTime(localDateTime));
            return markdownMessage;
        }

        private Details getThinkDetails() {
            if (Objects.isNull(this.thinkDetails)) {
                this.thinkDetails = new Details();
                this.thinkDetails.setOpened(true);
            }
            return this.thinkDetails;
        }

        private String getFormattedLocalDateTime(long epochMillis) {
            return getFormattedLocalDateTime(getLocalDateTime(epochMillis));
        }

        private String getFormattedLocalDateTime(LocalDateTime localDateTime) {
            return localDateTime.format(DATE_TIME_FORMATTER);
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
                        this.botThinkEndTimestamp, null);
            }
            this.botResponse.removeClassName("blink");
            this.botResponse.appendMarkdown(content);
        }

        private void initBotResponse(long epochMillis) {
            this.responseTimestamp = epochMillis;
            this.botResponse.getElement().setProperty("time", getFormattedLocalDateTime(this.responseTimestamp));
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
                    Optional.of(this.messagesSupplier.get()).filter(Predicate.not(List::isEmpty))
                            .map(list -> list.subList(Math.max(0, list.size() - 2), list.size()));
            messageList.map(List::getFirst).filter(message -> USER.equals(message.getMessageType()))
                    .map(Message::getMetadata).ifPresent(metadata -> updateMetadata(metadata, this.startTimestamp));
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
                        metadata.put(RAG_PROCESS_DOC_TITLES, new java.util.ArrayList<>(this.ragRetrievedDocTitles));
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
                        metadata.put(MCP_TOOL_PROCESS_TOOL_NAMES, new java.util.ArrayList<>(this.mcpToolNames));
                    metadata.put(MCP_TOOL_PROCESS_MESSAGES, this.mcpToolProcessMessagesBuilder.toString());
                });
                this.mcpToolProcessDetails = null;
                this.mcpToolProcessMessage = null;
                this.mcpToolProcessMessagesBuilder = null;
            }
            long completedTimestamp = this.responseTimestamp > 0 ? this.responseTimestamp : System.currentTimeMillis();
            metadataAsOpt.ifPresent(metadata -> updateMetadata(metadata, completedTimestamp));
            this.botResponse.removeClassName("blink");
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
            metadata.put(CONVERSATION_ID, getConversationId());
            metadata.put(TIMESTAMP, timestamp);
        }

    }
}
