package jm.kr.spring.ai.playground.webui.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rometools.utils.Strings;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.accordion.Accordion;
import com.vaadin.flow.component.accordion.AccordionPanel;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.vaadin.firitin.components.messagelist.MarkdownMessage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import static jm.kr.spring.ai.playground.service.chat.ChatHistoryPersistenceService.CHAT_ID;
import static org.springframework.ai.chat.messages.MessageType.USER;

public class ChatContentView extends VerticalLayout {
    private static final String LAST_SELECTED_RAG_DOC_INFO_IDS = "lastSelectedRagDocInfoIds";
    private final VerticalLayout messageListLayout;
    private final TextArea userPromptTextArea;
    private final MultiSelectComboBox<VectorStoreDocumentInfo> documentsComboBox;
    private final ChatService chatService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private final PersistentUiDataStorage persistentUiDataStorage;
    private ChatHistory chatHistory;

    public ChatContentView(PersistentUiDataStorage persistentUiDataStorage, ChatService chatService,
            ChatHistory chatHistory, Consumer<ChatHistory> completeChatHistoryConsumer) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatHistory = chatHistory;
        this.chatService = chatService;
        this.completeChatHistoryConsumer = completeChatHistoryConsumer;

        this.messageListLayout = new VerticalLayout();
        this.messageListLayout.setMargin(false);
        this.messageListLayout.setSpacing(false);
        this.messageListLayout.setPadding(false);

        Scroller messageScroller = new Scroller(this.messageListLayout);
        messageScroller.setSizeFull();

        this.documentsComboBox = new MultiSelectComboBox<>();
        documentsComboBox.setPlaceholder("No documents for RAG");
        documentsComboBox.setWidth("300px");
        documentsComboBox.setTooltipText("RAG with documents stored in VectorDB.");
        documentsComboBox.setSelectedItemsOnTop(true);
        documentsComboBox.setItemLabelGenerator(VectorStoreDocumentInfo::title);
        documentsComboBox.setItems(this.chatService.getExistDocumentInfoList());

        this.userPromptTextArea = new TextArea();
        this.userPromptTextArea.setPlaceholder("Ask Spring AI");
        this.userPromptTextArea.setWidthFull();
        this.userPromptTextArea.setAutofocus(true);
        this.userPromptTextArea.focus();
        this.userPromptTextArea.setMaxHeight("150px");
        this.userPromptTextArea.setValueChangeMode(ValueChangeMode.EAGER);
        this.userPromptTextArea.setClearButtonVisible(true);
        CompletableFuture<ZoneId> zoneIdFuture = VaadinUtils.buildClientZoneIdFuture(new CompletableFuture<>());

        Button submitButton = new Button("Submit");
        submitButton.addClickListener(buttonClickEvent -> inputEvent(zoneIdFuture));
        this.userPromptTextArea.setSuffixComponent(submitButton);

        this.userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT))
                submitButton.click();
        });

        Icon ragIcon = VaadinUtils.styledIcon(VaadinIcon.SEARCH_PLUS.create());
        ragIcon.setTooltipText("Select documents in VectorDB");
        ragIcon.addSingleClickListener(event -> documentsComboBox.setOpened(true));
        this.userPromptTextArea.setPrefixComponent(ragIcon);

        VerticalLayout userInputLayout = new VerticalLayout(documentsComboBox, this.userPromptTextArea);
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
        messages.forEach(message -> chatContentManager.addMarkdownMessage(this.messageListLayout, message,
                message.getMessageType()));
        this.messageListLayout.getChildren().toList().getLast().scrollIntoView();
        this.persistentUiDataStorage.loadData(LAST_SELECTED_RAG_DOC_INFO_IDS, new TypeReference<Set<String>>() {},
                docInfoIds -> {
                    if (!docInfoIds.isEmpty()) {
                        this.documentsComboBox.select(this.chatService.getExistDocumentInfoList().stream()
                                .filter(vectorStoreDocumentInfo -> docInfoIds.contains(
                                        vectorStoreDocumentInfo.docInfoId())).collect(Collectors.toUnmodifiableSet()));
                    }
                });
    }

    private void inputEvent(CompletableFuture<ZoneId> zoneIdFuture) {
        String userPrompt = this.userPromptTextArea.getValue();
        if (userPrompt.isBlank())
            return;
        this.userPromptTextArea.setEnabled(false);
        this.userPromptTextArea.clear();

        ChatContentManager chatContentManager = new ChatContentManager(this.messageListLayout, userPrompt, zoneIdFuture,
                this.chatHistory);
        this.messageListLayout.add(chatContentManager.getBotResponse());

        UI ui = VaadinUtils.getUi(this);
        List<String> selectedDocInfoIds =
                this.documentsComboBox.getSelectedItems().stream().map(VectorStoreDocumentInfo::docInfoId).toList();
        this.persistentUiDataStorage.saveData(LAST_SELECTED_RAG_DOC_INFO_IDS, selectedDocInfoIds);

        this.chatService.stream(this.chatHistory, userPrompt,
                        this.chatService.buildFilterExpression(selectedDocInfoIds),
                        this.completeChatHistoryConsumer)
                .doFinally(signalType -> ui.access(() -> {
                    chatContentManager.doFinally();
                    this.userPromptTextArea.setEnabled(true);
                    this.userPromptTextArea.focus();
                })).subscribe(content -> ui.access(() -> chatContentManager.append(content)));
    }

    public ChatOptions getChatOption() {
        return this.chatHistory.chatOptions();
    }

    public String getSystemPrompt() {return this.chatHistory.systemPrompt();}

    public String getChatId() {
        return this.chatHistory.chatId();
    }

    private class ChatContentManager {
        private static final Pattern ThinkPattern = Pattern.compile("<think>(.*?)</think>", Pattern.DOTALL);
        private static final String THINK_TIMESTAMP = "thinkTimestamp";
        private static final String THINK_PROCESS = "THINK PROCESS";
        private static final DateTimeFormatter DATE_TIME_FORMATTER =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        private final CompletableFuture<ZoneId> zoneIdFuture;
        private Supplier<List<Message>> messagesSupplier;
        private VerticalLayout messageListLayout;
        private long startTimestamp;
        private long responseTimestamp;
        private MarkdownMessage botResponse;
        private boolean isFirstAssistantResponse;
        private boolean isThinking;
        private MarkdownMessage botThinkResponse;
        private long botThinkTimestamp;
        private Accordion thinkAccordion;

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

        private void addMarkdownMessage(VerticalLayout messageListLayout, Message message, MessageType messageType) {
            String text = message.getText();
            Map<String, Object> metadata = message.getMetadata();
            Long thinkTimestamp = (Long) metadata.get(THINK_TIMESTAMP);
            if (Objects.nonNull(thinkTimestamp)) {
                Matcher matcher = ThinkPattern.matcher(text);
                if (matcher.find()) {
                    Accordion accordion = ChatContentManager.buildThinkAccordionPanel(new Accordion(),
                            buildMarkdownMessage(matcher.group(1),
                                    getBotThinkResponseName((Long) metadata.get(TIMESTAMP) - thinkTimestamp),
                                    thinkTimestamp));
                    accordion.close();
                    messageListLayout.add(accordion);
                    text = matcher.replaceAll("");
                }
            }
            messageListLayout.add(
                    buildMarkdownMessage(text, messageType, (Long) metadata.get(TIMESTAMP)));
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

        private static Accordion buildThinkAccordionPanel(Accordion accordion, MarkdownMessage botThinkResponse) {
            AccordionPanel accordionPanel = accordion.add("think", botThinkResponse);
            accordionPanel.addThemeVariants(DetailsVariant.FILLED);
            accordionPanel.setWidthFull();
            return accordion;
        }

        private Accordion getThinkAccordion() {
            if (Objects.isNull(this.thinkAccordion))
                this.thinkAccordion = new Accordion();
            return this.thinkAccordion;
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
            if ("<think>".equals(content)) {
                this.isThinking = true;
                return;
            }
            if ("</think>".equals(content)) {
                this.isThinking = false;
                if (Objects.nonNull(this.thinkAccordion))
                    this.thinkAccordion.close();
                return;
            }
            if (this.isThinking && Strings.isBlank(content) && Objects.isNull(this.botThinkResponse))
                return;

            getBotResponse().appendMarkdown(content);

            if (!this.isThinking && this.isFirstAssistantResponse)
                initBotResponse(System.currentTimeMillis());
        }

        private MarkdownMessage getBotResponse() {
            return this.isThinking ? getBotThinkResponse() : this.botResponse;
        }

        private MarkdownMessage getBotThinkResponse() {
            if (Objects.isNull(this.botThinkResponse)) {
                this.botThinkTimestamp = System.currentTimeMillis();
                this.botThinkResponse = buildMarkdownMessage(null, THINK_PROCESS, this.botThinkTimestamp);
                buildThinkAccordionPanel(getThinkAccordion(), this.botThinkResponse);
                this.botResponse.removeFromParent();
                this.messageListLayout.add(this.thinkAccordion, this.botResponse);
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
                this.thinkAccordion.removeFromParent();
                this.botResponse.appendMarkdown(this.botThinkResponse.getMarkdown());
                metadataAsOpt.ifPresent(metadata -> metadata.put(THINK_TIMESTAMP, this.botThinkTimestamp));
                this.botThinkResponse.getElement().setProperty("userName",
                        getBotThinkResponseName(this.responseTimestamp - this.botThinkTimestamp));
                initBotResponse(this.botThinkTimestamp);
                this.thinkAccordion = null;
                this.botThinkResponse = null;
            }
            metadataAsOpt.ifPresent(metadata -> updateMetadata(metadata, this.responseTimestamp));
            this.botResponse.scrollIntoView();
        }

        private void updateMetadata(Map<String, Object> metadata, long timetamp) {
            metadata.put(CHAT_ID, getChatId());
            metadata.put(TIMESTAMP, timetamp);
        }

        private static String getBotThinkResponseName(Long tookMillis) {
            return THINK_PROCESS + String.format(" (%.1f sec)", tookMillis.floatValue() / 1000);
        }

    }
}