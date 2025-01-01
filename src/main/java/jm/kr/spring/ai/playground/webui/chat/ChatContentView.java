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
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.vaadin.firitin.components.messagelist.MarkdownMessage;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@CssImport("./playground/styles.css")
public class ChatContentView extends VerticalLayout {
    private final ChatHistory chatHistory;
    private final VerticalLayout messageList;
    private final TextArea userPromptTextArea;
    private final ChatClientService chatClientService;

    public ChatContentView(ChatHistory chatHistory, ChatClientService chatClientService) {
        this.chatHistory = chatHistory;
        this.chatClientService = chatClientService;

        this.messageList = new VerticalLayout();
        if (Objects.nonNull(chatHistory.messages()))
            this.chatClientService.getCurrentMessageList(chatHistory.chatId()).forEach(
                    message -> this.messageList.add(buildMarkdownMessage(message.getText(), message.getMessageType(),
                            System.currentTimeMillis())));

        Scroller messageScroller = new Scroller(messageList);
        messageScroller.setSizeFull();

        this.userPromptTextArea = new TextArea();
        this.userPromptTextArea.setPlaceholder("Ask Spring AI");
        this.userPromptTextArea.setWidthFull();
        this.userPromptTextArea.setAutofocus(true);
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
    }

    private void inputEvent() {
        String userPrompt = this.userPromptTextArea.getValue();
        if (userPrompt.isBlank())
            return;
        this.userPromptTextArea.setEnabled(false);
        this.userPromptTextArea.clear();
        long currentTimeMillis = System.currentTimeMillis();
        this.messageList.add(buildMarkdownMessage(userPrompt, MessageType.USER, currentTimeMillis));

        // Placeholder message for the upcoming AI botResponse
        MarkdownMessage botResponse = buildMarkdownMessage(null, MessageType.ASSISTANT, currentTimeMillis);
        botResponse.addClassName("blink");
        this.messageList.add(botResponse);

        AtomicBoolean isFirst = new AtomicBoolean(true);

        Flux<String> botResponseStream = this.chatClientService.stream(this.chatHistory, userPrompt, currentTimeMillis);
        UI ui = VaadinUtils.getUi(this);
        botResponseStream.doFinally(signalType -> ui.access(() -> {
                    this.userPromptTextArea.setEnabled(true);
                    this.userPromptTextArea.focus();
                }))
                .doOnNext(first -> {
                    if (isFirst.getAndSet(false))
                        ui.access(() -> botResponse.removeClassName("blink"));
                }).subscribe(botResponse::appendMarkdownAsync);
        botResponse.scrollIntoView();
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
}