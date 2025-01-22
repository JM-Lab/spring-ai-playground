package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dialog.DialogVariant;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class ChatHistoriesView extends VerticalLayout {

    private final ChatHistoryService chatHistoryService;
    private final List<Consumer<ChatHistory>> historyClickConsumers;
    private final ListBox<ChatHistory> chatHistoryListBox;

    public ChatHistoriesView(ChatHistoryService chatHistoryService) {
        this.chatHistoryService = chatHistoryService;
        this.chatHistoryService.getChatHistoryChangeSupport()
                .addPropertyChangeListener(ChatHistoryService.CURRENT_CHAT_HISTORY_EVENT, event -> {
                    if (Objects.isNull(event.getOldValue()))
                        updateChatHistoriesContent(null);
                    else
                        updateChatHistoriesContent((ChatHistory) event.getNewValue());
                });
        this.historyClickConsumers = new ArrayList<>();
        setHeightFull();
        setSpacing(false);
        setMargin(false);
        getStyle().set("overflow", "hidden");
        this.chatHistoryListBox = new ListBox<>();
        this.chatHistoryListBox.addClassName("custom-list-box");
        this.chatHistoryListBox.setItems(List.of());
        this.chatHistoryListBox.setRenderer(new ComponentRenderer<>(chatHistory -> {
            Span title = new Span(chatHistory.title());
            title.getStyle().set("white-space", "nowrap").set("overflow", "hidden").set("text-overflow", "ellipsis")
                    .set("flex-grow", "1");
            title.getElement()
                    .setAttribute("title", LocalDateTime.ofInstant(Instant.ofEpochMilli(chatHistory.createTimestamp()),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return title;
        }));
        this.chatHistoryListBox.addValueChangeListener(
                event -> Optional.ofNullable(event.getValue())
                        .filter(chatHistory -> !chatHistory.equals(event.getOldValue()))
                        .ifPresent(this::changeCurrentChatHistory));
        Scroller scroller = new Scroller(this.chatHistoryListBox);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        add(initChatHistoryHeader(), scroller);
    }

    private Header initChatHistoryHeader() {
        Span appName = new Span("History");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        MenuBar menuBar = new MenuBar();
        menuBar.setWidthFull();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon closeIcon = VaadinUtils.styledIcon(VaadinIcon.CLOSE.create());
        closeIcon.setTooltipText("Delete");
        menuBar.addItem(closeIcon, menuItemClickEvent -> deleteHistory());

        Icon editIcon = VaadinUtils.styledIcon(VaadinIcon.PENCIL.create());
        editIcon.setTooltipText("Rename");
        menuBar.addItem(editIcon, menuItemClickEvent -> renameHistory());

        Header header = new Header(appName, menuBar);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
    }

    private void renameHistory() {
        this.getCurrentChatHistoryAsOpt().ifPresent(chatHistory -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.addThemeVariants(DialogVariant.LUMO_NO_PADDING);
            dialog.setHeaderTitle("Rename: " + chatHistory.title());
            VerticalLayout dialogLayout = new VerticalLayout();
            dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
            dialogLayout.getStyle().set("width", "300px").set("max-width", "100%");
            dialog.add(dialogLayout);

            TextField titleTextField = new TextField();
            titleTextField.setWidthFull();
            titleTextField.setValue(chatHistory.title());
            titleTextField.addFocusListener(event ->
                    titleTextField.getElement().executeJs("this.inputElement.select();")
            );
            dialogLayout.add(titleTextField);

            Button saveButton = new Button("Save", e -> {
                this.chatHistoryService.updateChatHistory(chatHistory.newTitle(titleTextField.getValue()));
                dialog.close();
            });
            saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            saveButton.getStyle().set("margin-right", "auto");
            dialog.getFooter().add(saveButton);

            Button cancelButton = new Button("Cancel", e -> dialog.close());
            dialog.getFooter().add(cancelButton);

            dialog.open();
            titleTextField.focus();
        });
    }

    private void deleteHistory() {
        this.getCurrentChatHistoryAsOpt().ifPresent(chatHistory -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setHeaderTitle("Delete: " + chatHistory.title());
            dialog.add("Are you sure you want to delete this history permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.chatHistoryService.deleteChatHistory(chatHistory.chatId());
                this.updateChatHistoriesContent(null);
                dialog.close();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            deleteButton.getStyle().set("margin-right", "auto");
            dialog.getFooter().add(deleteButton);

            Button cancelButton = new Button("Cancel", (e) -> dialog.close());
            cancelButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            dialog.getFooter().add(cancelButton);

            dialog.open();
        });
    }

    private void updateChatHistoriesContent(ChatHistory selectedChatHistory) {
        VaadinUtils.getUi(this).access(() -> {
            this.chatHistoryListBox.clear();
            this.chatHistoryListBox.removeAll();
            List<ChatHistory> currentChatHistories = this.chatHistoryService.getChatHistories();
            this.chatHistoryListBox.setItems(currentChatHistories);
            if (Objects.nonNull(selectedChatHistory))
                this.chatHistoryListBox.setValue(selectedChatHistory);
            else if (!currentChatHistories.isEmpty())
                this.chatHistoryListBox.setValue(currentChatHistories.getFirst());
        });
    }

    public ChatHistoriesView registerChangeCurrentHistoryConsumer(Consumer<ChatHistory> changeCurrentHistoryConsumer) {
        this.historyClickConsumers.add(changeCurrentHistoryConsumer);
        return this;
    }

    private void changeCurrentChatHistory(ChatHistory chatHistory) {
        this.historyClickConsumers.forEach(consumer -> consumer.accept(chatHistory));
    }

    public void clearSelectHistory() {
        getChildren().filter(component -> component instanceof ListBox).findFirst()
                .map((component -> (ListBox<?>) component)).ifPresent(listBox -> listBox.setValue(null));
    }

    public Optional<ChatHistory> getCurrentChatHistoryAsOpt() {
        return Optional.ofNullable(this.chatHistoryListBox.getValue());
    }
}
