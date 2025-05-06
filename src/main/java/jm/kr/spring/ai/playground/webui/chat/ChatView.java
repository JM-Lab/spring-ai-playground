package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
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
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.service.chat.ChatService;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.function.Consumer;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.headerPopover;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@SpringComponent
@UIScope
@CssImport("./playground/chat-styles.css")
@Route(value = "chat", layout = SpringAiPlaygroundAppLayout.class)
public class ChatView extends Div {

    public static final String CHAT_HISTORY_CHANGE_EVENT = "CHAT_HISTORY_CHANGE_EVENT";
    public static final String CHAT_HISTORY_SELECT_EVENT = "CHAT_HISTORY_SELECT_EVENT";
    public static final String CHAT_HISTORY_EMPTY_EVENT = "CHAT_HISTORY_EMPTY_EVENT";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final ChatService chatService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private final ChatHistoryService chatHistoryService;
    private final ChatHistoryView chatHistoryView;
    private final SplitLayout splitLayout;
    private final VerticalLayout chatContentLayout;
    private double splitterPosition;
    private boolean sidebarCollapsed;
    private ChatContentView chatContentView;

    public ChatView(PersistentUiDataStorage persistentUiDataStorage, ChatService chatService,
            ChatHistoryService chatHistoryService) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;

        PropertyChangeSupport chatHistoryChangeSupport = new PropertyChangeSupport(this);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_SELECT_EVENT,
                event -> this.changeChatContent((ChatHistory) event.getNewValue()));
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_EMPTY_EVENT, event -> {
            if ((boolean) event.getNewValue())
                addNewChatContent();
        });
        this.completeChatHistoryConsumer =
                chatHistory -> chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_CHANGE_EVENT, null,
                        chatHistoryService.updateChatHistory(chatHistory));
        setHeightFull();
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.chatHistoryView =
                new ChatHistoryView(persistentUiDataStorage, chatHistoryService, chatHistoryChangeSupport);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_CHANGE_EVENT,
                event -> this.chatHistoryView.changeChatHistoryContent((ChatHistory) event.getNewValue()));
        this.splitLayout.addToPrimary(chatHistoryView);
        this.chatContentLayout = new VerticalLayout();
        this.chatContentLayout.setSpacing(false);
        this.chatContentLayout.setMargin(false);
        this.chatContentLayout.setPadding(false);
        this.chatContentLayout.setHeightFull();
        this.chatContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        this.splitLayout.addToSecondary(this.chatContentLayout);
        this.sidebarCollapsed = false;
        addNewChatContent();
    }

    private HorizontalLayout createChatContentHeader(ChatOptions chatOptions) {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.getStyle().setPadding("var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button toggleButton = styledButton("Hide History", VaadinIcon.CHEVRON_LEFT.create(), null);
        Component leftArrowIcon = toggleButton.getIcon();
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        rightArrowIcon.setTooltipText("Show History");
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                chatHistoryView.removeFromParent();
            else
                this.splitLayout.addToPrimary(chatHistoryView);
            if (this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        Button newChatButton = styledButton("New Chat", VaadinIcon.CHAT.create(), event -> addNewChatContent());
        horizontalLayout.add(newChatButton);

        H4 chatModelServiceText =
                new H4(String.format("%s: %s", this.chatService.getChatModelProvider(), chatOptions.getModel()));
        chatModelServiceText.getStyle().set("white-space", "nowrap");
        Div chatModelServiceTextDiv = new Div(chatModelServiceText);
        chatModelServiceTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");

        HorizontalLayout modelLabelLayout = new HorizontalLayout(chatModelServiceTextDiv);
        modelLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        modelLabelLayout.setWidthFull();
        horizontalLayout.add(modelLabelLayout);

        Icon chatModelSettingIcon = styledIcon(VaadinIcon.COG_O.create());
        chatModelSettingIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
        chatModelSettingIcon.setTooltipText("Chat Model Setting");
        Popover chatModelSettingPopover = headerPopover(chatModelSettingIcon, "Chat Model Setting");
        chatModelSettingPopover.setWidth("400px");
        chatModelSettingPopover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
        chatModelSettingPopover.setPosition(PopoverPosition.BOTTOM);
        chatModelSettingPopover.setModal(true);
        ChatModelSettingView chatModelSettingView = new ChatModelSettingView(this.chatService.getModels(),
                this.chatContentView.getSystemPrompt(), this.chatContentView.getChatOption());
        chatModelSettingView.getStyle()
                .set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");
        Button applyNewChatButton = new Button("Apply & New Chat", clickEvent -> {
            addNewChatContent(chatModelSettingView.getSystemPromptTextArea(), chatModelSettingView.getChatOptions());
            chatModelSettingPopover.close();
        });
        applyNewChatButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        HorizontalLayout applyNewChatButtonLayout = new HorizontalLayout(applyNewChatButton);
        applyNewChatButtonLayout.setWidthFull();
        applyNewChatButtonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        applyNewChatButtonLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");
        chatModelSettingView.add(applyNewChatButtonLayout);
        chatModelSettingPopover.add(chatModelSettingView);

        MenuBar chatModelSettingMenuBar = new MenuBar();
        chatModelSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        chatModelSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        chatModelSettingMenuBar.addItem(chatModelSettingIcon);

        horizontalLayout.add(chatModelSettingMenuBar);
        return horizontalLayout;
    }

    private void addNewChatContent() {
        addNewChatContent(this.chatService.getSystemPrompt(), this.chatService.getDefaultOptions());
    }

    private void addNewChatContent(String systemPrompt, ChatOptions chatOptions) {
        this.chatHistoryView.clearSelectHistory();
        changeChatContent(this.chatHistoryService.createChatHistory(systemPrompt, chatOptions));
    }

    private void changeChatContent(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory))
            return;

        this.chatContentView = new ChatContentView(this.persistentUiDataStorage, this.chatService, chatHistory,
                this.completeChatHistoryConsumer);
        VaadinUtils.getUi(this).access(() -> {
            this.chatContentLayout.removeAll();
            this.chatContentLayout.add(createChatContentHeader(chatHistory.chatOptions()), this.chatContentView);
        });
    }

}
