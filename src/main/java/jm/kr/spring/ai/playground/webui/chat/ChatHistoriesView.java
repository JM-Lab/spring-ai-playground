package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ChatHistoriesView extends VerticalLayout {

    private final Header header;
    private final Footer footer;
    private final List<Consumer<ChatHistory>> historyClickConsumers;
    private ListBox<ChatHistory> chatHistoriesListBox;
    private ChatHistory seletChatHistory;

    public ChatHistoriesView(Header header, Footer footer) {
        this.header = header;
        this.footer = footer;
        this.historyClickConsumers = new ArrayList<>();
        setHeightFull();
        setSpacing(false);
        setMargin(false);
        this.chatHistoriesListBox = new ListBox<>();
        this.chatHistoriesListBox.setSizeFull();
        this.chatHistoriesListBox.getStyle().set("overflow-x", "hidden").set("white-space", "nowrap");
        this.chatHistoriesListBox.setItems(List.of());
        this.chatHistoriesListBox.setRenderer(new ComponentRenderer<>(chatHistory -> {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(FlexComponent.Alignment.CENTER);

            Span title = new Span(chatHistory.title());
            title.getElement()
                    .setAttribute("title", LocalDateTime.ofInstant(Instant.ofEpochMilli(chatHistory.createTimestamp()),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            row.add(title);
            title.getStyle().set("white-space", "nowrap");
            return title;
        }));
        this.chatHistoriesListBox.addValueChangeListener(
                event -> Optional.ofNullable(event.getValue()).ifPresent(this::handleHistoryClick));
        add(header, this.chatHistoriesListBox, this.footer);
    }

    public void updateHistoriesContent(List<ChatHistory> chatHistories) {
        if (chatHistories.isEmpty()) {
            VaadinUtils.getUi(this).access(() -> {
                this.chatHistoriesListBox.removeAll();
            });
            return;
        }
        ChatHistory firstChatHistory = chatHistories.get(0);
        if (!firstChatHistory.equals(seletChatHistory) ||
                chatHistories.size() != this.chatHistoriesListBox.getChildren().count()) {
            VaadinUtils.getUi(this).access(() -> {
                this.chatHistoriesListBox.removeAll();
                this.chatHistoriesListBox.setItems(chatHistories);
                this.chatHistoriesListBox.setValue(firstChatHistory);
            });
            handleHistoryClick(firstChatHistory);
        }
    }

    public ChatHistoriesView registerHistoryClickConsumer(Consumer<ChatHistory> historyClickConsumer) {
        this.historyClickConsumers.add(historyClickConsumer);
        return this;
    }

    private void handleHistoryClick(ChatHistory chatHistory) {
        this.historyClickConsumers.forEach(consumer -> consumer.accept(chatHistory));
    }

    public void clearSelectHistory() {
        getChildren().filter(component -> component instanceof ListBox).findFirst()
                .map((component -> (ListBox<?>) component)).ifPresent(listBox -> listBox.setValue(null));
    }

    public Optional<ChatHistory> getCurrentChatHistoryAsOpt() {
        return Optional.ofNullable(this.chatHistoriesListBox.getValue());
    }
}
