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
package jm.kr.spring.ai.playground.webui.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rometools.utils.Strings;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
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
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatService;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.McpToolCallingManager.McpToolResult;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;
import jm.kr.spring.ai.playground.service.mcp.client.McpTransportType;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static jm.kr.spring.ai.playground.service.chat.ChatHistory.TIMESTAMP;
import static jm.kr.spring.ai.playground.service.chat.ChatHistoryPersistenceService.CONVERSATION_ID;
import static org.springframework.ai.chat.messages.MessageType.USER;

@JsModule("./playground/chat-stt.js")
public class ChatContentView extends VerticalLayout {
    private static final String LAST_SELECTED_RAG_DOC_INFO_IDS = "lastSelectedRagDocInfoIds";
    private static final String LAST_SELECTED_MCP_CONNECTION_INFOS = "lastSelectedMcpConnectionInfos";
    private final VerticalLayout messageListLayout;
    private final Scroller messageScroller;
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

        Icon micIcon = VaadinUtils.styledLargeIcon(VaadinIcon.MICROPHONE.create());
        Button micButton = new Button(micIcon);
        micButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        micButton.setTooltipText("Voice input");
        micButton.setId("micButton");
        micButton.addClickListener(e ->
                micButton.getElement().executeJs("window.STTModule.toggle($0, $1)",
                        this.userPromptTextArea.getId().get(),
                        micButton.getId().get())
        );

        Icon submitIcon = VaadinUtils.styledLargeIcon(VaadinIcon.ARROW_CIRCLE_UP.create());
        Button submitButton = new Button(submitIcon);
        submitButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        submitButton.setTooltipText("Submit");

        HorizontalLayout suffix = new HorizontalLayout(micButton, submitButton);
        suffix.setSpacing(false);
        suffix.setPadding(false);
        this.userPromptTextArea.setSuffixComponent(suffix);

        this.userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT))
                submitButton.click();
        });

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
        messages.forEach(message -> chatContentManager.initMarkdownMessage(this.messageListLayout, message,
                message.getMessageType()));
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


        this.messageListLayout.getChildren().toList().getLast().scrollIntoView();
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
        this.messageListLayout.add(chatContentManager.getBotResponse());

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
                        toolCallbacks, o -> ui.access(() -> chatContentManager.appendMcpToolProcessMessage(o)))
                .doFinally(signalType -> ui.access(() -> {
                    chatContentManager.doFinally();
                    this.messageScroller.scrollToBottom();
                    this.userPromptTextArea.setReadOnly(false);
                    this.userPromptTextArea.setEnabled(true);
                    this.userPromptTextArea.focus();
                    this.currentStream = null;
                })).subscribe(content -> ui.access(() -> chatContentManager.append(content)));
    }

    public ChatOptions getChatOption() {
        return this.chatHistory.chatOptions();
    }

    public String getSystemPrompt() {return this.chatHistory.systemPrompt();}

    public String getConversationId() {
        return this.chatHistory.conversationId();
    }

    private class ChatContentManager {
        private static final Pattern ThinkPattern = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
        private static final String THINK_TIMESTAMP = "thinkTimestamp";
        private static final String THINK_PROCESS = "THINK";
        private static final String MCP_TOOL_PROCESS = "MCP TOOL";
        private static final String MCP_TOOL_PROCESS_TIMESTAMP = "mcpToolProcessTimestamp";
        private static final String MCP_TOOL_PROCESS_MESSAGES = "mcpToolProcessMessages";
        private static final DateTimeFormatter DATE_TIME_FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        private final CompletableFuture<ZoneId> zoneIdFuture;
        private final Supplier<List<Message>> messagesSupplier;
        private VerticalLayout messageListLayout;
        private long startTimestamp;
        private long responseTimestamp;
        private MarkdownMessage botResponse;
        private boolean isFirstAssistantResponse;
        private boolean isThinking;
        private MarkdownMessage botThinkResponse;
        private long botThinkTimestamp;
        private Details thinkDetails;
        private MarkdownMessage mcpToolProcessMessage;
        private long mcpToolProcessTimestamp;
        private Details mcpToolProcessDetails;
        private StringBuilder mcpToolProcessMessagesBuilder;

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
            this.messageListLayout.add(userMarkdownMessage);
            userMarkdownMessage.scrollIntoView();
            this.botResponse = buildMarkdownMessage(null, MessageType.ASSISTANT, System.currentTimeMillis());
            this.botResponse.addClassName("blink");
            this.isFirstAssistantResponse = true;
            this.isThinking = false;
        }

        public void appendMcpToolProcessMessage(Object content) {
            long timestamp = System.currentTimeMillis();
            String markdownSnippet = getLocalDateTime(timestamp) + " : " + content.toString() + "\n\n";
            getMcpToolProcessMessage(this.messageListLayout, timestamp).appendMarkdown(markdownSnippet);
            if (Objects.isNull(this.mcpToolProcessMessagesBuilder))
                this.mcpToolProcessMessagesBuilder = new StringBuilder();
            this.mcpToolProcessMessagesBuilder.append(markdownSnippet);
            this.mcpToolProcessMessage.scrollIntoView();
            if (content instanceof McpToolResult)
                this.mcpToolProcessDetails.setOpened(false);
        }

        private MarkdownMessage getMcpToolProcessMessage(VerticalLayout messageListLayout, long timestamp) {
            if (Objects.isNull(this.mcpToolProcessMessage)) {
                this.mcpToolProcessTimestamp = timestamp;
                this.mcpToolProcessMessage = buildMarkdownMessage(null, MCP_TOOL_PROCESS, this.mcpToolProcessTimestamp);
                this.botResponse.addClassName("blink");
                this.botResponse.removeFromParent();
                messageListLayout.add(
                        buildProcessDetails(MCP_TOOL_PROCESS, getMcpToolProcessDetails(), this.mcpToolProcessMessage),
                        this.botResponse);
            }
            return this.mcpToolProcessMessage;
        }

        private static Details buildProcessDetails(String title, Details details,
                MarkdownMessage markdownMessage) {
            details.setSummary(buildDetailsSummary(title));
            details.add(markdownMessage);
            details.addThemeVariants(DetailsVariant.FILLED);
            details.setWidthFull();
            return details;
        }

        private static Span buildDetailsSummary(String title) {
            return new Span(title);
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

            Long thinkTimestamp = (Long) metadata.get(THINK_TIMESTAMP);
            if (Objects.nonNull(thinkTimestamp)) {
                Matcher matcher = ThinkPattern.matcher(text);
                StringBuilder thinkBuilder = new StringBuilder();
                while (matcher.find())
                    thinkBuilder.append(matcher.group(1));

                if (!thinkBuilder.isEmpty()) {
                    Details details = ChatContentManager.buildProcessDetails(THINK_PROCESS, new Details(),
                            buildMarkdownMessage(thinkBuilder.toString(), THINK_PROCESS, thinkTimestamp));
                    details.setOpened(false);
                    text = matcher.replaceAll("");
                    components.add(new Pair<>(thinkTimestamp, details));
                }
            }
            String mcpToolProcessMessages = (String) metadata.get(MCP_TOOL_PROCESS_MESSAGES);
            if (Objects.nonNull(mcpToolProcessMessages)) {
                Long mcpToolProcessTimestamp = (Long) metadata.get(MCP_TOOL_PROCESS_TIMESTAMP);
                Details details =
                        ChatContentManager.buildProcessDetails(MCP_TOOL_PROCESS, new Details(),
                                buildMarkdownMessage(mcpToolProcessMessages, MCP_TOOL_PROCESS,
                                        mcpToolProcessTimestamp));
                details.setOpened(false);
                components.add(new Pair<>(mcpToolProcessTimestamp, details));
            }
            components.stream().sorted(Comparator.comparing(Pair::getFirst)).map(Pair::getSecond)
                    .forEach(messageListLayout::add);
            messageListLayout.add(
                    buildMarkdownMessage(text, messageType, Long.parseLong(metadata.get(TIMESTAMP).toString())));
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
            if (content.startsWith("<think>")) {
                this.isThinking = true;
                return;
            }
            if (content.endsWith("</think>")) {
                this.isThinking = false;
                if (Objects.nonNull(this.thinkDetails))
                    this.thinkDetails.setOpened(false);
                return;
            }
            if (this.isThinking && Strings.isBlank(content) && Objects.isNull(this.botThinkResponse))
                return;

            MarkdownMessage botResponse = getBotResponse();
            botResponse.appendMarkdown(content);
            botResponse.scrollIntoView();

            if (!this.isThinking && this.isFirstAssistantResponse)
                initBotResponse(System.currentTimeMillis());

            if (botResponse == this.botResponse)
                this.botResponse.removeClassName("blink");

        }

        private MarkdownMessage getBotResponse() {
            return this.isThinking ? getBotThinkResponse() : this.botResponse;
        }

        private MarkdownMessage getBotThinkResponse() {
            if (Objects.isNull(this.botThinkResponse)) {
                this.botThinkTimestamp = System.currentTimeMillis();
                this.botThinkResponse = buildMarkdownMessage(null, THINK_PROCESS, this.botThinkTimestamp);
                buildProcessDetails(THINK_PROCESS, getThinkDetails(), this.botThinkResponse);
                this.botResponse.removeFromParent();
                this.messageListLayout.add(this.thinkDetails, this.botResponse);
            }
            return this.botThinkResponse;
        }

        private void initBotResponse(long epochMillis) {
            this.responseTimestamp = epochMillis;
            this.botResponse.getElement().setProperty("time", getFormattedLocalDateTime(this.responseTimestamp));
            this.botResponse.removeClassName("blink");
            this.isFirstAssistantResponse = false;
        }

        public void doFinally() {
            Optional<List<Message>> messageList =
                    Optional.of(this.messagesSupplier.get()).filter(Predicate.not(List::isEmpty))
                            .map(list -> list.subList(list.size() - 2, list.size()));
            messageList.map(List::getFirst).filter(message -> USER.equals(message.getMessageType()))
                    .map(Message::getMetadata).ifPresent(metadata -> updateMetadata(metadata, this.startTimestamp));
            Optional<Map<String, Object>> metadataAsOpt = messageList.map(List::getLast).map(Message::getMetadata);
            if (Objects.nonNull(this.botThinkResponse)) {
                this.thinkDetails.removeFromParent();
                metadataAsOpt.ifPresent(metadata -> metadata.put(THINK_TIMESTAMP, this.botThinkTimestamp));
                this.botThinkResponse.getElement().setProperty("userName", THINK_PROCESS);
                initBotResponse(this.botThinkTimestamp);
                this.thinkDetails = null;
                this.botThinkResponse = null;
            }
            if (Objects.nonNull(this.mcpToolProcessMessagesBuilder)) {
                this.mcpToolProcessDetails.removeFromParent();
                metadataAsOpt.ifPresent(metadata -> {
                    metadata.put(MCP_TOOL_PROCESS_TIMESTAMP, this.mcpToolProcessTimestamp);
                    metadata.put(MCP_TOOL_PROCESS_MESSAGES, this.mcpToolProcessMessagesBuilder.toString());
                });
                this.mcpToolProcessDetails = null;
                this.mcpToolProcessMessage = null;
                this.mcpToolProcessMessagesBuilder = null;
            }
            metadataAsOpt.ifPresent(metadata -> updateMetadata(metadata, this.responseTimestamp));
            this.botResponse.removeClassName("blink");
            this.botResponse.scrollIntoView();
        }

        private void updateMetadata(Map<String, Object> metadata, long timestamp) {
            metadata.put(CONVERSATION_ID, getConversationId());
            metadata.put(TIMESTAMP, timestamp);
        }

    }
}