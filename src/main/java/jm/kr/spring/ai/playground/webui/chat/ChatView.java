package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.contextmenu.SubMenu;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dialog.DialogVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayoutVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.chat.ChatClientService;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Objects;

@Route(value = "", layout = SpringAiPlaygroundAppLayout.class)
public class ChatView extends Div {

    private static final int SPLITTER_POSITION = 15;
    private final ChatClientService chatClientService;
    private final ChatHistoryService chatHistoryService;
    private final ChatHistoriesView chatHistoriesView;
    private final SplitLayout splitLayout;
    private final VerticalLayout chatContentLayout;
    private boolean sidebarCollapsed;

    public ChatView(ChatClientService chatClientService, ChatHistoryService chatHistoryService) {
        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(SPLITTER_POSITION);
        splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        setSizeFull();
        add(splitLayout);

        this.chatClientService =
                chatClientService.registerCompleteResponseConsumer(chatHistory -> updateChatHistoriesView());
        this.chatHistoryService = chatHistoryService;
        this.chatHistoriesView = new ChatHistoriesView(initChatHistoryHeader(),
                initChatHistoryFooter()).registerHistoryUpdateConsumer(this::addChatContent);
        this.splitLayout.addToPrimary(chatHistoriesView);
        chatContentLayout = new VerticalLayout();
        chatContentLayout.setHeightFull();
        this.splitLayout.addToSecondary(chatContentLayout);
        this.sidebarCollapsed = false;
        addNewChatContent();
    }

    private Icon styledIcon(VaadinIcon vaadinIcon) {
        Icon icon = vaadinIcon.create();
        icon.getStyle().set("width", "var(--lumo-icon-size-m)");
        icon.getStyle().set("height", "var(--lumo-icon-size-m)");
        icon.getStyle().set("marginRight", "var(--lumo-space-s)");
        return icon;
    }

    private Header initChatHistoryHeader() {
        Span appName = new Span("History");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        MenuBar menuBar = new MenuBar();
        menuBar.setWidthFull();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon chatMenuIcon = styledIcon(VaadinIcon.ELLIPSIS_DOTS_H);
        chatMenuIcon.setTooltipText("Open Menu");
        MenuItem chatMenuItem = menuBar.addItem(chatMenuIcon);
        SubMenu chatSubMenu = chatMenuItem.getSubMenu();
        chatSubMenu.addItem("Rename", menuItemClickEvent -> renameHistory());
        chatSubMenu.addItem("Delete", menuItemClickEvent -> deleteHistory());

        Header header = new Header(appName, menuBar);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
    }

    private void renameHistory() {
        chatHistoriesView.getCurrentChatIdAsOpt().ifPresent(chatId -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.addThemeVariants(DialogVariant.LUMO_NO_PADDING);
            dialog.setHeaderTitle("Rename History Title");
            VerticalLayout dialogLayout = new VerticalLayout();
            dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
            dialogLayout.getStyle().set("width", "300px").set("max-width", "100%");
            dialog.add(dialogLayout);

            TextField titleTextField = new TextField();
            titleTextField.setWidthFull();
            titleTextField.setValue(this.chatHistoryService.getChatHistory(chatId).title());
            titleTextField.addFocusListener(event ->
                    titleTextField.getElement().executeJs("this.inputElement.select();")
            );
            dialogLayout.add(titleTextField);

            Button saveButton = new Button("Save", e -> {
                this.chatHistoryService.updateChatHistory(chatId, titleTextField.getValue(),
                        System.currentTimeMillis());
                updateChatHistoriesView();
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
        chatHistoriesView.getCurrentChatIdAsOpt().ifPresent(chatId -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setHeaderTitle("Delete History: " + this.chatHistoryService.getChatHistory(chatId).title());
            dialog.add("Are you sure you want to delete this history permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.chatHistoryService.deleteChatHistory(chatId);
                updateChatHistoriesView();
                if (this.chatHistoryService.getChatHistoriesTotal() < 1)
                    addNewChatContent();
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

    private Footer initChatHistoryFooter() {
        Span footer = new Span("");
        footer.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);
        return new Footer(footer);
    }

    private HorizontalLayout createChatContentHeader(ChatContentView chatContentView) {
        Button toggleButton = new Button();
        toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Icon leftArrowIcon = styledIcon(VaadinIcon.CHEVRON_LEFT);
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT);
        leftArrowIcon.setTooltipText("History Toggle");
        rightArrowIcon.setTooltipText("History Toggle");
        toggleButton.setIcon(leftArrowIcon);
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                chatHistoriesView.removeFromParent();
            else
                this.splitLayout.addToPrimary(chatHistoriesView);
            splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : SPLITTER_POSITION);
        });

        H4 modelText = new H4("Model: " + chatContentView.getChatOption().getModel());
        modelText.getStyle().set("white-space", "nowrap");
        Div modelTextDiv = new Div(modelText);
        modelTextDiv.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center")
                .set("height", "100%");

        HorizontalLayout modelLabelLayout = new HorizontalLayout(modelTextDiv);
        modelLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        modelLabelLayout.setWidthFull();

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon newChatIcon = styledIcon(VaadinIcon.CHAT);
        newChatIcon.setTooltipText("New Chat");
        menuBar.addItem(newChatIcon, event -> addNewChatContent());

        Icon chatMenuIcon = styledIcon(VaadinIcon.COG_O);
        chatMenuIcon.setTooltipText("Model Setting");
        MenuItem menuItem = menuBar.addItem(chatMenuIcon);
        menuItem.addClickListener(menuItemClickEvent -> {
            Popover popover = new Popover();
            popover.setTarget(menuItem);
            popover.setWidth("400px");
            popover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
            popover.setPosition(PopoverPosition.BOTTOM_END);
            popover.setAriaLabelledBy("model-setting-heading");
            popover.setModal(true);
            popover.addOpenedChangeListener(event -> {
                if (!event.isOpened())
                    popover.removeFromParent();
            });

            ChatModelSettingView chatModelSettingView =
                    new ChatModelSettingView(chatClientService.getModels(), chatContentView.getSystemPrompt(),
                            chatContentView.getChatOption());

            H4 heading = new H4("Model Setting");
            heading.setId("model-setting-heading");
            heading.setWidthFull();

            Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE), e -> popover.close());
            closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout headerLayout = new HorizontalLayout(heading, closeButton);
            headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            headerLayout.getStyle().set("padding",
                    "var(--lumo-space-m) var(--lumo-space-m) var(--lumo-space-xs)");

            Button applyNewChatButton = new Button("Apply & New Chat",
                    event -> {
                        popover.close();
                        addNewChatContent(chatModelSettingView.getSystemPromptTextArea(),
                                chatModelSettingView.getChatOptions());
                    });
            applyNewChatButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            chatModelSettingView.add(applyNewChatButton);

            popover.add(headerLayout, chatModelSettingView);
            popover.open();

        });

        HorizontalLayout horizontalLayout = new HorizontalLayout(toggleButton, modelLabelLayout, menuBar);
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        return horizontalLayout;
    }

    private void updateChatHistoriesView() {
        this.chatHistoriesView.updateHistoriesContent(this.chatHistoryService.getChatHistories());
    }

    private void addNewChatContent() {
        addNewChatContent(this.chatClientService.getSystemPrompt(), this.chatClientService.getDefaultOptions());
    }

    private void addNewChatContent(String systemPrompt, ChatOptions chatOptions) {
        addChatContent(this.chatHistoryService.createChatHistory(systemPrompt, chatOptions));
    }

    private void addChatContent(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory.messages()))
            this.chatHistoriesView.clearSelectHistory();
        ChatContentView chatContentView = new ChatContentView(chatHistory, this.chatClientService);
        HorizontalLayout chatContentHeader = createChatContentHeader(chatContentView);
        VaadinUtils.getUi(this).access(() -> {
            this.chatContentLayout.removeAll();
            this.chatContentLayout.add(chatContentHeader, chatContentView);
        });
    }

}
