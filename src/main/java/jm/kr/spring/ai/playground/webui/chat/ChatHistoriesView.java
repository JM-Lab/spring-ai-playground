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
    private final List<Consumer<ChatHistory>> historyUpdateConsumers;
    private ListBox<ChatHistory> chatHistoriesListBox;


    public ChatHistoriesView(Header header, Footer footer) {
        this.header = header;
        this.footer = footer;
        this.historyUpdateConsumers = new ArrayList<>();
        setHeightFull();
        setSpacing(false);
        setMargin(false);
        updateHistoriesContent(List.of());
    }

    public void updateHistoriesContent(List<ChatHistory> chatHistories) {
        ListBox<ChatHistory> chatHistoriesListBox = new ListBox<>();
        chatHistoriesListBox.setSizeFull();
        chatHistoriesListBox.getStyle().set("overflow-x", "hidden").set("white-space", "nowrap");
        chatHistoriesListBox.setItems(chatHistories);
        chatHistoriesListBox.setRenderer(new ComponentRenderer<>(chatHistory -> {
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
        if (!chatHistories.isEmpty()) {
            ChatHistory chatHistory = chatHistories.get(0);
            chatHistoriesListBox.setValue(chatHistory);
            this.handleHistoryUpdate(chatHistory);
        }
        chatHistoriesListBox.addValueChangeListener(
                event -> Optional.ofNullable(event.getValue()).ifPresent(this::handleHistoryUpdate));
        VaadinUtils.getUi(this).access(() -> {
            removeAll();
            add(this.header, chatHistoriesListBox, this.footer);
        });
        this.chatHistoriesListBox = chatHistoriesListBox;
    }

    public ChatHistoriesView registerHistoryUpdateConsumer(Consumer<ChatHistory> historyUpdateConsumer) {
        this.historyUpdateConsumers.add(historyUpdateConsumer);
        return this;
    }

    private void handleHistoryUpdate(ChatHistory chatHistory) {
        this.historyUpdateConsumers.forEach(consumer -> consumer.accept(chatHistory));
    }

    public void clearSelectHistory() {
        getChildren().filter(component -> component instanceof ListBox).findFirst()
                .map((component -> (ListBox<?>) component)).ifPresent(listBox -> listBox.setValue(null));
    }

    public Optional<String> getCurrentChatIdAsOpt() {
        return Optional.ofNullable(this.chatHistoriesListBox.getValue()).map(ChatHistory::chatId);
    }
}
