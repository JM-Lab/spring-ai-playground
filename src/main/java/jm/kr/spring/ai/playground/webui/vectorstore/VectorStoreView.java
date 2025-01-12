package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.theme.lumo.LumoIcon;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.chat.ChatModelSettingView;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@RouteAlias(value = "", layout = SpringAiPlaygroundAppLayout.class)
@Route(value = "vectorstore", layout = SpringAiPlaygroundAppLayout.class)
public class VectorStoreView extends Div {

    private static final int SPLITTER_POSITION = 15;
    private final VectorStoreService vectorStoreService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final VectorStoreDocumentView vectorStoreDocumentView;
    private final SplitLayout splitLayout;
    private final VectorStoreContentView vectorStoreContentView;
    private boolean sidebarCollapsed;

    public VectorStoreView(VectorStoreService vectorStoreService,
            VectorStoreDocumentService vectorStoreDocumentService) {
        this.vectorStoreService = vectorStoreService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(SPLITTER_POSITION);
        splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        setSizeFull();
        add(splitLayout);

        this.vectorStoreDocumentView = new VectorStoreDocumentView(initDocumentViewHeader(), initDocumentViewFooter());
        this.splitLayout.addToPrimary(this.vectorStoreDocumentView);
        this.vectorStoreContentView = new VectorStoreContentView(vectorStoreService);
        this.vectorStoreContentView.setSpacing(false);
        this.vectorStoreContentView.setMargin(false);
        this.vectorStoreContentView.setPadding(false);
        HorizontalLayout chatContentHeader = createDocumentContentHeader();


        VerticalLayout vectorStoreContentLayout = new VerticalLayout();
        vectorStoreContentLayout.setSpacing(false);
        vectorStoreContentLayout.setMargin(false);
        vectorStoreContentLayout.setPadding(false);
        vectorStoreContentLayout.setHeightFull();
        vectorStoreContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        vectorStoreContentLayout.add(chatContentHeader, this.vectorStoreContentView);

        this.splitLayout.addToSecondary(vectorStoreContentLayout);
        this.sidebarCollapsed = false;


    }

    private Header initDocumentViewHeader() {
        Span appName = new Span("Document");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        MenuBar menuBar = new MenuBar();
        menuBar.setWidthFull();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon newDocument = styledIcon(VaadinIcon.FILE_TEXT_O);
        newDocument.setTooltipText("New Document");
        menuBar.addItem(newDocument, event -> {
            Popover popover = new Popover();
            popover.setTarget(newDocument);
            popover.setWidth("400px");
            popover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
            popover.setPosition(PopoverPosition.BOTTOM_END);
            popover.setAriaLabelledBy("model-setting-heading");
            popover.setModal(true);
            popover.addOpenedChangeListener(openedChangeEvent -> {
                if (!openedChangeEvent.isOpened())
                    popover.removeFromParent();
            });

            FileUpload fileUpload = new FileUpload();
            fileUpload.getStyle().set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");

            H4 heading = new H4("Document Create");
            heading.setId("model-setting-heading");
            heading.setWidthFull();

            Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE), e -> popover.close());
            closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

            HorizontalLayout headerLayout = new HorizontalLayout(heading, closeButton);
            headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            headerLayout.getStyle().set("padding", "0 0 0 var(--lumo-space-m)");

            Button insertButton = new Button("Insert Document",
                    buttonClickEvent -> {
                        popover.close();
                    });
            insertButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
            HorizontalLayout applyNewChatButtonLayout = new HorizontalLayout(insertButton);
            applyNewChatButtonLayout.setWidthFull();
            applyNewChatButtonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            applyNewChatButtonLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");

            fileUpload.add(applyNewChatButtonLayout);

            popover.add(headerLayout, fileUpload);
            popover.open();

        });

        Icon chatMenuIcon = styledIcon(VaadinIcon.ELLIPSIS_DOTS_H);
        chatMenuIcon.setTooltipText("Open Menu");
        MenuItem chatMenuItem = menuBar.addItem(chatMenuIcon);
        SubMenu chatSubMenu = chatMenuItem.getSubMenu();
        chatSubMenu.addItem("Rename", menuItemClickEvent -> renameDocument());
        chatSubMenu.addItem("Delete", menuItemClickEvent -> deleteDocument());

        Header header = new Header(appName, menuBar);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
    }

    private void renameDocument() {
        vectorStoreDocumentView.getCurrentDocumentInfoAsOpt().ifPresent(documentInfo -> {
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
            titleTextField.setValue(documentInfo.title());
            titleTextField.addFocusListener(event ->
                    titleTextField.getElement().executeJs("this.inputElement.select();")
            );
            dialogLayout.add(titleTextField);

            Button saveButton = new Button("Save", e -> {
                this.vectorStoreDocumentService.updateDocument(documentInfo, titleTextField.getValue());
                this.vectorStoreDocumentView.updateDocumentContent(this.vectorStoreDocumentService.getDocumentList());
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

    private void deleteDocument() {
        vectorStoreDocumentView.getCurrentDocumentInfoAsOpt().ifPresent(documentInfo -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setHeaderTitle("Delete Document: " + documentInfo.title());
            dialog.add("Are you sure you want to delete this permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.vectorStoreDocumentService.deleteDocumentInfo(documentInfo.docId());
                this.vectorStoreDocumentView.updateDocumentContent(this.vectorStoreDocumentService.getDocumentList());
                if (this.vectorStoreDocumentService.getDocumentTotal() < 1)
//                    addNewVectorStoreContent();
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

    private Footer initDocumentViewFooter() {
        Span footer = new Span("");
        footer.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);
        return new Footer(footer);
    }

    private HorizontalLayout createDocumentContentHeader() {
        Button toggleButton = new Button();
        toggleButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Icon leftArrowIcon = styledIcon(VaadinIcon.CHEVRON_LEFT);
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT);
        leftArrowIcon.setTooltipText("Hide Documents");
        rightArrowIcon.setTooltipText("Show Documents");
        toggleButton.setIcon(leftArrowIcon);
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                vectorStoreDocumentView.removeFromParent();
            else
                this.splitLayout.addToPrimary(vectorStoreDocumentView);
            splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : SPLITTER_POSITION);
        });

        TextArea userPromptTextArea = new TextArea();
        userPromptTextArea.setPlaceholder("Test Prompt");
        userPromptTextArea.setWidthFull();
        userPromptTextArea.setAutofocus(true);
        userPromptTextArea.focus();
        userPromptTextArea.getStyle().set("padding", "0");
//        userPromptTextArea.setMaxHeight("150px");
        userPromptTextArea.setValueChangeMode(ValueChangeMode.EAGER);
        userPromptTextArea.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT)) {

            }
        });

        Button submitButton = new Button("Submit");
        submitButton.addClickListener(buttonClickEvent -> {});
        userPromptTextArea.setSuffixComponent(submitButton);


        TextField filterExpressionTextField = new TextField("Filter Expression");
        Icon icon = LumoIcon.DROPDOWN.create();
        filterExpressionTextField.setSuffixComponent(icon);
        filterExpressionTextField.setWidthFull();
        filterExpressionTextField.getStyle().set("padding", "0");


        HorizontalLayout searchInputLayout = new HorizontalLayout(userPromptTextArea, filterExpressionTextField);
        searchInputLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        searchInputLayout.setWidthFull();
        searchInputLayout.setPadding(false);

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon chatMenuIcon = styledIcon(VaadinIcon.COG_O);
        chatMenuIcon.setTooltipText("Model Setting");
        MenuItem menuItem = menuBar.addItem(chatMenuIcon);
        menuItem.addClickListener(menuItemClickEvent ->

        {
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

            Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE), e -> popover.close());
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

        HorizontalLayout horizontalLayout = new HorizontalLayout(toggleButton, searchInputLayout, menuBar);
        horizontalLayout.getStyle().set("padding", "var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        return horizontalLayout;
    }

}
