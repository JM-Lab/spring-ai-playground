package jm.kr.spring.ai.playground.webui.vectorstore;

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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
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

    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final List<Consumer<VectorStoreDocumentInfo>> documentInfoClickConsumers;
    private final ListBox<VectorStoreDocumentInfo> documentListBox;

    public VectorStoreDocumentView(VectorStoreDocumentService vectorStoreDocumentService) {
        setHeightFull();
        setSpacing(false);
        setMargin(false);

        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.documentInfoClickConsumers = new ArrayList<>();
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
        add(initDocumentViewHeader(), this.documentListBox);
    }

    private Header initDocumentViewHeader() {
        Span appName = new Span("Document");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        MenuBar menuBar = new MenuBar();
        menuBar.setWidthFull();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);

        Icon closeIcon = VaadinUtils.styledIcon(VaadinIcon.CLOSE.create());
        closeIcon.setTooltipText("Delete");
        menuBar.addItem(closeIcon, menuItemClickEvent -> deleteDocument());

        Icon editIcon = VaadinUtils.styledIcon(VaadinIcon.PENCIL.create());
        editIcon.setTooltipText("Rename");
        menuBar.addItem(editIcon, menuItemClickEvent -> renameDocument());

        Header header = new Header(appName, menuBar);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
    }

    private void renameDocument() {
        this.getCurrentDocumentInfoAsOpt().ifPresent(documentInfo -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.addThemeVariants(DialogVariant.LUMO_NO_PADDING);
            dialog.setHeaderTitle("Rename: " + documentInfo.title());
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
                this.updateDocumentContent(this.vectorStoreDocumentService.getDocumentList());
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
        this.getCurrentDocumentInfoAsOpt().ifPresent(documentInfo -> {
            Dialog dialog = new Dialog();
            dialog.setModal(true);
            dialog.setHeaderTitle("Delete: " + documentInfo.title());
            dialog.add("Are you sure you want to delete this permanently?");

            Button deleteButton = new Button("Delete", e -> {
                this.vectorStoreDocumentService.deleteDocumentInfo(documentInfo.docId());
                this.updateDocumentContent(this.vectorStoreDocumentService.getDocumentList());
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

    public void updateDocumentContent(List<VectorStoreDocumentInfo> vectorStoreDocumentInfos) {
        VaadinUtils.getUi(this).access(() -> {
            this.documentListBox.removeAll();
            if (!vectorStoreDocumentInfos.isEmpty())
                this.documentListBox.setItems(vectorStoreDocumentInfos);
        });
    }

    public VectorStoreDocumentView registerHistoryClickConsumer(
            Consumer<VectorStoreDocumentInfo> historyClickConsumer) {
        this.documentInfoClickConsumers.add(historyClickConsumer);
        return this;
    }

    private void handleDocumentInfoClick(VectorStoreDocumentInfo vectorStoreDocumentInfo) {
        this.documentInfoClickConsumers.forEach(consumer -> consumer.accept(vectorStoreDocumentInfo));
    }

    public void clearSelectHistory() {
        getChildren().filter(component -> component instanceof ListBox).findFirst()
                .map((component -> (ListBox<?>) component)).ifPresent(listBox -> listBox.setValue(null));
    }

    public Optional<VectorStoreDocumentInfo> getCurrentDocumentInfoAsOpt() {
        return Optional.ofNullable(this.documentListBox.getValue());
    }
}
