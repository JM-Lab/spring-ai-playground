package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class VectorStoreDocumentView extends VerticalLayout {

    private final Header header;
    private final Footer footer;
    private final List<Consumer<VectorStoreDocumentInfo>> documentInfoClickConsumers;
    private ListBox<VectorStoreDocumentInfo> documentListBox;
    private VectorStoreDocumentInfo seletDocument;

    public VectorStoreDocumentView(Header header, Footer footer) {
        this.header = header;
        this.footer = footer;
        this.documentInfoClickConsumers = new ArrayList<>();
        setHeightFull();
        setSpacing(false);
        setMargin(false);
        this.documentListBox = new ListBox<>();
        this.documentListBox.setSizeFull();
        this.documentListBox.getStyle().set("overflow-x", "hidden").set("white-space", "nowrap");
        this.documentListBox.setItems(List.of());
        this.documentListBox.setRenderer(new ComponentRenderer<>(chatHistory -> {
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER);

            Span title = new Span(chatHistory.title());
            title.getElement()
                    .setAttribute("title", LocalDateTime.ofInstant(Instant.ofEpochMilli(chatHistory.createTimestamp()),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            row.add(title);
            title.getStyle().set("white-space", "nowrap");
            return title;
        }));
        this.documentListBox.addValueChangeListener(
                event -> Optional.ofNullable(event.getValue()).ifPresent(this::handleDocumentInfoClick));
        add(this.header, this.documentListBox, this.footer);
    }

    public void updateDocumentContent(List<VectorStoreDocumentInfo> chatDocument) {
        if (chatDocument.isEmpty()) {
            VaadinUtils.getUi(this).access(() -> {
                this.documentListBox.removeAll();
            });
            return;
        }
        VectorStoreDocumentInfo firstVectorStoreDocumentInfo = chatDocument.get(0);
        if (!firstVectorStoreDocumentInfo.equals(seletDocument) ||
                chatDocument.size() != this.documentListBox.getChildren().count()) {
            VaadinUtils.getUi(this).access(() -> {
                this.documentListBox.removeAll();
                this.documentListBox.setItems(chatDocument);
                this.documentListBox.setValue(firstVectorStoreDocumentInfo);
            });
            handleDocumentInfoClick(firstVectorStoreDocumentInfo);
        }
    }

    public VectorStoreDocumentView registerHistoryClickConsumer(Consumer<VectorStoreDocumentInfo> historyClickConsumer) {
        this.documentInfoClickConsumers.add(historyClickConsumer);
        return this;
    }

    private void handleDocumentInfoClick(VectorStoreDocumentInfo chatHistory) {
        this.documentInfoClickConsumers.forEach(consumer -> consumer.accept(chatHistory));
    }

    public void clearSelectHistory() {
        getChildren().filter(component -> component instanceof ListBox).findFirst()
                .map((component -> (ListBox<?>) component)).ifPresent(listBox -> listBox.setValue(null));
    }

    public Optional<VectorStoreDocumentInfo> getCurrentDocumentInfoAsOpt() {
        return Optional.ofNullable(this.documentListBox.getValue());
    }
}
