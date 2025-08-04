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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static jm.kr.spring.ai.playground.webui.chat.ChatView.CHAT_HISTORY_CHANGE_EVENT;
import static jm.kr.spring.ai.playground.webui.chat.ChatView.CHAT_HISTORY_EMPTY_EVENT;
import static jm.kr.spring.ai.playground.webui.chat.ChatView.CHAT_HISTORY_SELECT_EVENT;

public class ChatHistoryView extends VerticalLayout implements BeforeEnterObserver {

    private static final String LAST_SELECTED_CHAT_HISTORY = "lastSelectedChatHistory";
    private final PersistentUiDataStorage persistentUiDataStorage;
    private final PropertyChangeSupport chatHistoryChangeSupport;
    private final ChatHistoryService chatHistoryService;
    private final ListBox<ChatHistory> chatHistoryListBox;

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        this.chatHistoryListBox.setItems(this.chatHistoryService.getChatHistoryList());
        this.persistentUiDataStorage.loadData(LAST_SELECTED_CHAT_HISTORY, new TypeReference<ChatHistory>() {},
                chatHistory -> {
                    if (Objects.nonNull(chatHistory))
                        this.chatHistoryListBox.setValue(
                                chatHistoryService.getChatHistory(chatHistory.conversationId()));
                });
    }

    public ChatHistoryView(PersistentUiDataStorage persistentUiDataStorage, ChatHistoryService chatHistoryService,
            PropertyChangeSupport chatHistoryChangeSupport) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatHistoryService = chatHistoryService;
        this.chatHistoryChangeSupport = chatHistoryChangeSupport;

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
            title.getElement().setAttribute("title",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(chatHistory.createTimestamp()),
                            ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return title;
        }));
        this.chatHistoryListBox.addValueChangeListener(
                event -> notifyChatHistorySelection(event.getOldValue(), event.getValue()));
        Scroller scroller = new Scroller(this.chatHistoryListBox);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        add(initChatHistoryHeader(), scroller);
    }

    private void notifyChatHistorySelection(ChatHistory oldChatHistory, ChatHistory newChatHistory) {
        if (Objects.isNull(newChatHistory))
            this.chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_SELECT_EVENT, oldChatHistory, null);
        else if (!newChatHistory.equals(oldChatHistory)) {
            this.chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_SELECT_EVENT, oldChatHistory, newChatHistory);
            this.persistentUiDataStorage.saveData(LAST_SELECTED_CHAT_HISTORY, newChatHistory);
        }
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
            Dialog dialog = VaadinUtils.headerDialog("Rename: " + chatHistory.title());
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.addThemeVariants(DialogVariant.LUMO_NO_PADDING);
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
                ChatHistory updatedChatHistory = this.chatHistoryService.updateChatHistory(
                        chatHistory.mutate(titleTextField.getValue(), System.currentTimeMillis()));
                this.chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_CHANGE_EVENT, null, updatedChatHistory);
                dialog.close();
            });
            saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            saveButton.getStyle().set("margin-right", "auto");
            dialog.getFooter().add(saveButton);

            dialog.open();
            titleTextField.focus();
        });
    }

    private void deleteHistory() {
        this.getCurrentChatHistoryAsOpt().ifPresent(chatHistory -> {
            Dialog dialog = VaadinUtils.headerDialog("Delete: " + chatHistory.title());
            dialog.setModal(true);
            dialog.add("Are you sure you want to delete this history permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.chatHistoryService.deleteChatHistory(chatHistory);
                this.changeChatHistoryContent(null);
                dialog.close();
            });
            deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            deleteButton.getStyle().set("margin-right", "auto");
            dialog.getFooter().add(deleteButton);

            dialog.open();
            deleteButton.focus();
        });
    }

    public void changeChatHistoryContent(ChatHistory targetChatHistory) {
        VaadinUtils.getUi(this).access(() -> {
            this.chatHistoryListBox.removeAll();
            List<ChatHistory> chatHistoryList = this.chatHistoryService.getChatHistoryList();
            if (chatHistoryList.isEmpty()) {
                this.chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_EMPTY_EVENT, false, true);
                return;
            }
            this.chatHistoryListBox.setItems(chatHistoryList);
            this.chatHistoryListBox.setValue(Objects.isNull(targetChatHistory) ? chatHistoryList.getFirst() :
                    targetChatHistory);
        });
    }

    public void clearSelectHistory() {
        this.chatHistoryListBox.clear();
    }

    private Optional<ChatHistory> getCurrentChatHistoryAsOpt() {
        return Optional.ofNullable(this.chatHistoryListBox.getValue());
    }

}
