package jm.kr.spring.ai.playground.webui.vectorstore;

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
import com.vaadin.flow.router.RouteAlias;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.chat.ChatModelSettingView;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@CssImport("./playground/vectorstore-styles.css")
@RouteAlias(value = "", layout = SpringAiPlaygroundAppLayout.class)
@Route(value = "vectorstore", layout = SpringAiPlaygroundAppLayout.class)
public class VectorStoreView extends Div {

    private final VectorStoreService vectorStoreService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final VectorStoreDocumentView vectorStoreDocumentView;
    private final SplitLayout splitLayout;
    private final VectorStoreContentView vectorStoreContentView;
    private double splitterPosition;
    private boolean sidebarCollapsed;

    public VectorStoreView(VectorStoreService vectorStoreService,
            VectorStoreDocumentService vectorStoreDocumentService) {
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.vectorStoreService = vectorStoreService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;

        this.vectorStoreDocumentView = new VectorStoreDocumentView(vectorStoreDocumentService);
        this.splitLayout.addToPrimary(this.vectorStoreDocumentView);
        this.vectorStoreContentView = new VectorStoreContentView(vectorStoreService);
        this.vectorStoreContentView.setSpacing(false);
        this.vectorStoreContentView.setMargin(false);
        this.vectorStoreContentView.setPadding(false);

        VerticalLayout vectorStoreContentLayout = new VerticalLayout();
        vectorStoreContentLayout.setSpacing(false);
        vectorStoreContentLayout.setMargin(false);
        vectorStoreContentLayout.setPadding(false);
        vectorStoreContentLayout.setHeightFull();
        vectorStoreContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        vectorStoreContentLayout.add(createDocumentContentHeader(), this.vectorStoreContentView);

        this.splitLayout.addToSecondary(vectorStoreContentLayout);
        this.sidebarCollapsed = false;


    }

    private HorizontalLayout createDocumentContentHeader() {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.getStyle().setPadding("var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button toggleButton = styledButton("Hide Documents", VaadinIcon.CHEVRON_LEFT.create(), null);
        Component leftArrowIcon = toggleButton.getIcon();
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        rightArrowIcon.setTooltipText("Show Documents");
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                vectorStoreDocumentView.removeFromParent();
            else
                this.splitLayout.addToPrimary(vectorStoreDocumentView);
            if (this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        Button newDocumentButton = styledButton("New Document", VaadinIcon.FILE_TEXT_O.create(), null);
        newDocumentButton.addClickListener(event -> {
            Popover popover = new Popover();
            popover.setTarget(newDocumentButton);
//            popover.setWidth("400px");
            popover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
            popover.setPosition(PopoverPosition.BOTTOM_END);
            popover.setAriaLabelledBy("model-setting-heading");
            popover.setModal(true);
            popover.addOpenedChangeListener(openedChangeEvent -> {
                if (!openedChangeEvent.isOpened())
                    popover.removeFromParent();
            });

            VectorStoreDocumentUpload vectorStoreDocumentUpload = new VectorStoreDocumentUpload();
            vectorStoreDocumentUpload.getStyle().set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");

            H4 heading = new H4("Document Create");
            heading.setId("model-setting-heading");
            heading.setWidthFull();

            Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE.create()), e -> popover.close());
            closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout headerLayout = new HorizontalLayout(heading, closeButton);
            headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            headerLayout.getStyle().set("padding", "0 0 0 var(--lumo-space-m)");

            Button insertButton = new Button("Insert Document",
                    buttonClickEvent -> popover.close());
            insertButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            HorizontalLayout applyNewChatButtonLayout = new HorizontalLayout(insertButton);
            applyNewChatButtonLayout.setWidthFull();
            applyNewChatButtonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            applyNewChatButtonLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");

            vectorStoreDocumentUpload.add(applyNewChatButtonLayout);

            popover.add(headerLayout, vectorStoreDocumentUpload);
            popover.open();
        });
        horizontalLayout.add(newDocumentButton);

        H4 vectorStoreText =
                new H4("VectorStore: " + this.vectorStoreService.getVectorStoreName());
        vectorStoreText.getStyle().set("white-space", "nowrap");
        Div modelTextDiv = new Div(vectorStoreText);
        modelTextDiv.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center")
                .set("height", "100%");

        HorizontalLayout vectorStoreLabelLayout = new HorizontalLayout(modelTextDiv);
        vectorStoreLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        vectorStoreLabelLayout.setWidthFull();
        horizontalLayout.add(vectorStoreLabelLayout);

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon vectorStoreMenuIcon = styledIcon(VaadinIcon.COG_O.create());
        vectorStoreMenuIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
        vectorStoreMenuIcon.setTooltipText("VectorStore Setting");
        MenuItem menuItem = menuBar.addItem(vectorStoreMenuIcon);
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
                    new ChatModelSettingView(List.of(), "", ChatOptions.builder().build());
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
//                        addNewVectorStoreContent(chatModelSettingView.getSystemPromptTextArea(),
//                                chatModelSettingView.getChatOptions());
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

}
