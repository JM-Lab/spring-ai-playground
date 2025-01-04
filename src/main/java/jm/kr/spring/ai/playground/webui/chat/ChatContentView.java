package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import jm.kr.spring.ai.playground.service.chat.ChatClientService;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.vaadin.firitin.components.messagelist.MarkdownMessage;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@CssImport("./playground/styles.css")
public class ChatContentView extends VerticalLayout {
    private final VerticalLayout messageListLayout;
    private final TextArea userPromptTextArea;
    private final ChatClientService chatClientService;
    private ChatHistory chatHistory;

    public ChatContentView(ChatClientService chatClientService, ChatHistory chatHistory, List<Message> existMessages) {
        this.chatHistory = chatHistory;
        this.chatClientService = chatClientService;

        this.messageListLayout = new VerticalLayout();
        this.messageListLayout.setMargin(false);
        this.messageListLayout.setSpacing(false);
        this.messageListLayout.setPadding(false);
        if (Objects.nonNull(existMessages))
            existMessages.forEach(message -> this.messageListLayout.add(
                    buildMarkdownMessage(message.getText(), message.getMessageType(),
                            Optional.ofNullable(message.getMetadata().get("timestamp"))
                                    .map(timestamp -> (Long) timestamp).orElseGet(System::currentTimeMillis))));

        Scroller messageScroller = new Scroller(messageListLayout);
        messageScroller.setSizeFull();

        this.userPromptTextArea = new TextArea();
        this.userPromptTextArea.setPlaceholder("Ask Spring AI");
        this.userPromptTextArea.setWidthFull();
        this.userPromptTextArea.setAutofocus(true);
        this.userPromptTextArea.focus();
        this.userPromptTextArea.setMaxHeight("150px");
        this.userPromptTextArea.setValueChangeMode(ValueChangeMode.EAGER);
        this.userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT)) {
                inputEvent();
            }
        });

        Button submitButton = new Button("Submit");
        submitButton.addClickListener(buttonClickEvent -> inputEvent());
        this.userPromptTextArea.setSuffixComponent(submitButton);

        HorizontalLayout userInput = new HorizontalLayout(userPromptTextArea);
        userInput.setWidthFull();
        add(messageScroller, userInput);
        setSizeFull();
        setMargin(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
    }

    private void inputEvent() {
        String userPrompt = this.userPromptTextArea.getValue();
        if (userPrompt.isBlank())
            return;
        this.userPromptTextArea.setEnabled(false);
        this.userPromptTextArea.clear();
        long currentTimeMillis = System.currentTimeMillis();
        this.messageListLayout.add(buildMarkdownMessage(userPrompt, MessageType.USER, currentTimeMillis));

        // Placeholder message for the upcoming AI botResponse
        MarkdownMessage botResponse = buildMarkdownMessage(null, MessageType.ASSISTANT, currentTimeMillis);
        botResponse.addClassName("blink");

        this.messageListLayout.add(botResponse);

        AtomicBoolean isFirst = new AtomicBoolean(true);
        Flux<String> botResponseStream = this.chatClientService.stream(this.chatHistory, userPrompt, currentTimeMillis);
        UI ui = VaadinUtils.getUi(this);
        botResponseStream.doFinally(signalType -> ui.access(() -> {
                    this.userPromptTextArea.setEnabled(true);
                    this.userPromptTextArea.focus();
                }))
                .doOnNext(first -> {
                    if (isFirst.getAndSet(false))
                        ui.access(() -> {
                            botResponse.getElement().setProperty("time",
                                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            botResponse.removeClassName("blink");
                        });
                }).subscribe(botResponse::appendMarkdownAsync);
    }

    private MarkdownMessage buildMarkdownMessage(String message, MessageType messageType, long epochMillis) {
        LocalDateTime localDateTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        MarkdownMessage markdownMessage =
                new MarkdownMessage(message, messageType.getValue().toUpperCase(), localDateTime);
        markdownMessage.setAvatarColor(MarkdownMessage.Color.AVATAR_PRESETS[messageType.ordinal()]);
        markdownMessage.getElement()
                .setProperty("time", localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return markdownMessage;
    }

    public ChatOptions getChatOption() {
        return this.chatHistory.chatOptions();
    }

    public String getSystemPrompt() {return this.chatHistory.systemPrompt();}

    public void updateChatHistory(ChatHistory updateChatHistory) {
        if (updateChatHistory.chatId().equals(getChatId()))
            this.chatHistory = updateChatHistory;
    }

    public String getChatId() {
        return this.chatHistory.chatId();
    }
}