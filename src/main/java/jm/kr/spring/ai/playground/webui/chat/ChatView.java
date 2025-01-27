package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
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
import jm.kr.spring.ai.playground.service.chat.ChatClientService;
import jm.kr.spring.ai.playground.service.chat.ChatHistory;
import jm.kr.spring.ai.playground.service.chat.ChatHistoryService;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Objects;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@CssImport("./playground/chat-styles.css")
@Route(value = "chat", layout = SpringAiPlaygroundAppLayout.class)
public class ChatView extends Div {

    private final ChatClientService chatClientService;
    private final ChatHistoryService chatHistoryService;
    private final ChatHistoriesView chatHistoriesView;
    private final SplitLayout splitLayout;
    private final VerticalLayout chatContentLayout;
    private double splitterPosition;
    private boolean sidebarCollapsed;
    private ChatContentView chatContentView;

    public ChatView(ChatClientService chatClientService, ChatHistoryService chatHistoryService) {
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.chatClientService =
                chatClientService.registerCompleteResponseConsumer(this::handleCompleteResponse);
        this.chatHistoryService = chatHistoryService;
        this.chatHistoriesView =
                new ChatHistoriesView(chatHistoryService).registerChangeCurrentHistoryConsumer(this::changeChatContent);
        this.splitLayout.addToPrimary(chatHistoriesView);
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

    private HorizontalLayout createChatContentHeader(ChatContentView chatContentView) {
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
                chatHistoriesView.removeFromParent();
            else
                this.splitLayout.addToPrimary(chatHistoriesView);
            if(this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        Button newChatButton = styledButton("New Chat", VaadinIcon.CHAT.create(), event -> addNewChatContent());
        horizontalLayout.add(newChatButton);

        H4 modelText = new H4("Model: " + chatContentView.getChatOption().getModel());
        modelText.getStyle().set("white-space", "nowrap");
        Div modelTextDiv = new Div(modelText);
        modelTextDiv.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center")
                .set("height", "100%");

        HorizontalLayout modelLabelLayout = new HorizontalLayout(modelTextDiv);
        modelLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        modelLabelLayout.setWidthFull();
        horizontalLayout.add(modelLabelLayout);

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon chatMenuIcon = styledIcon(VaadinIcon.COG_O.create());
        chatMenuIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
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
            chatModelSettingView.getStyle()
                    .set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");

            H4 heading = new H4("Model Setting");
            heading.setId("model-setting-heading");
            heading.setWidthFull();

            Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE.create()), e -> popover.close());
            closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout headerLayout = new HorizontalLayout(heading, closeButton);
            headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            headerLayout.getStyle().set("padding", "0 0 0 var(--lumo-space-m)");

            Button applyNewChatButton = new Button("Apply & New Chat",
                    event -> {
                        addNewChatContent(chatModelSettingView.getSystemPromptTextArea(),
                                chatModelSettingView.getChatOptions());
                        popover.close();
                    });
            applyNewChatButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            HorizontalLayout applyNewChatButtonLayout = new HorizontalLayout(applyNewChatButton);
            applyNewChatButtonLayout.setWidthFull();
            applyNewChatButtonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            applyNewChatButtonLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");

            chatModelSettingView.add(applyNewChatButtonLayout);

            popover.add(headerLayout, chatModelSettingView);
            popover.open();

        });
        horizontalLayout.add(menuBar);

        return horizontalLayout;
    }

    private void handleCompleteResponse(ChatHistory chatHistory) {
        this.chatHistoryService.updateChatHistory(chatHistory);
    }

    private void addNewChatContent() {
        addNewChatContent(this.chatClientService.getSystemPrompt(), this.chatClientService.getDefaultOptions());
    }

    private void addNewChatContent(String systemPrompt, ChatOptions chatOptions) {
        this.chatHistoriesView.clearSelectHistory();
        changeChatContent(this.chatHistoryService.createChatHistory(systemPrompt, chatOptions));
    }

    private void changeChatContent(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory)) {
            chatHistory = this.chatHistoryService.createChatHistory(this.chatClientService.getSystemPrompt(),
                    this.chatClientService.getDefaultOptions());
        }
        if (Objects.nonNull(this.chatContentView) &&
                chatHistory.chatId().equals(this.chatContentView.getChatId())) {
            this.chatContentView.updateChatHistory(chatHistory);
            return;
        }
        this.chatContentView = new ChatContentView(this.chatClientService, chatHistory,
                this.chatHistoryService.getMessageList(chatHistory.chatId()));
        HorizontalLayout chatContentHeader = createChatContentHeader(this.chatContentView);
        VaadinUtils.getUi(this).access(() -> {
            this.chatContentLayout.removeAll();
            this.chatContentLayout.add(chatContentHeader, this.chatContentView);
        });
    }

}
