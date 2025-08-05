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
package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dialog.DialogVariant;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.document.Document;

import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView.DOCUMENTS_DELETE_EVENT;
import static jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView.DOCUMENT_ADDING_EVENT;
import static jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView.DOCUMENT_SELECTING_EVENT;

public class VectorStoreDocumentView extends VerticalLayout implements BeforeEnterObserver {

    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final MultiSelectListBox<VectorStoreDocumentInfo> documentListBox;
    private final PropertyChangeSupport documentInfoChangeSupport;

    public VectorStoreDocumentView(VectorStoreDocumentService vectorStoreDocumentService,
            PropertyChangeSupport documentInfoChangeSupport) {
        this.documentInfoChangeSupport = documentInfoChangeSupport;

        setHeightFull();
        setSpacing(false);
        setMargin(false);

        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.documentListBox = new MultiSelectListBox<>();
        this.documentListBox.setSizeFull();
        this.documentListBox.getStyle().set("overflow-x", "hidden").set("white-space", "nowrap");
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
        this.documentListBox.addValueChangeListener(event -> Optional.ofNullable(event.getValue())
                .filter(vectorStoreDocumentInfos -> this.documentListBox.getSelectedItems()
                        .equals(vectorStoreDocumentInfos))
                .ifPresent(documentInfos -> this.documentInfoChangeSupport.firePropertyChange(DOCUMENT_SELECTING_EVENT,
                        event.getOldValue(), documentInfos)));
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
        Set<VectorStoreDocumentInfo> selectedItems = this.documentListBox.getSelectedItems();
        if (selectedItems.isEmpty())
            return;
        VectorStoreDocumentInfo documentInfo =
                selectedItems.stream().sorted(Comparator.comparingLong(VectorStoreDocumentInfo::updateTimestamp))
                        .toList().getFirst();
        Dialog dialog = VaadinUtils.headerDialog("Rename: " + documentInfo.title());
        dialog.setModal(true);
        dialog.setResizable(true);
        dialog.addThemeVariants(DialogVariant.LUMO_NO_PADDING);
        VerticalLayout dialogLayout = new VerticalLayout();
        dialogLayout.setWidthFull();
        dialogLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        dialog.add(dialogLayout);

        TextField titleTextField = new TextField();
        titleTextField.setWidthFull();
        titleTextField.setValue(documentInfo.title());
        titleTextField.addFocusListener(event -> titleTextField.getElement().executeJs("this.inputElement.select();"));
        dialogLayout.add(titleTextField);

        Button saveButton = new Button("Save", e -> {
            this.vectorStoreDocumentService.updateDocumentInfo(documentInfo, titleTextField.getValue());
            this.updateDocumentContent();
            dialog.close();
        });
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.getStyle().set("margin-right", "auto");
        dialog.getFooter().add(saveButton);

        dialog.open();
        titleTextField.focus();
    }

    private void deleteDocument() {
        Set<VectorStoreDocumentInfo> selectedItems = this.documentListBox.getSelectedItems();
        if (selectedItems.isEmpty())
            return;

        String headerTitle = String.format("Delete: %s%s (%d chunks)",
                selectedItems.stream().map(VectorStoreDocumentInfo::title).findFirst().orElse(""),
                selectedItems.size() > 1 ? String.format(" %d more", selectedItems.size() - 1) : "",
                selectedItems.stream().map(VectorStoreDocumentInfo::documentListSupplier).map(Supplier::get)
                        .mapToInt(List::size).sum());
        Dialog dialog = VaadinUtils.headerDialog(headerTitle);
        dialog.setModal(true);
        dialog.add("Are you sure you want to delete this permanently?");

        Button deleteButton = new Button("Delete", e -> {
            for (VectorStoreDocumentInfo documentInfo : selectedItems)
                this.vectorStoreDocumentService.deleteDocumentInfo(documentInfo);
            this.updateDocumentContent();
            this.documentInfoChangeSupport.firePropertyChange(DOCUMENTS_DELETE_EVENT, null, selectedItems);
            dialog.close();
        });
        deleteButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        deleteButton.getStyle().set("margin-right", "auto");
        dialog.getFooter().add(deleteButton);

        dialog.open();
    }

    public void addDocumentContent(List<String> fileNames, Map<String, List<Document>> uploadedDocumentItems) {
        List<VectorStoreDocumentInfo> newDocumentInfos = fileNames.stream()
                .map(fileName -> {
                    List<Document> documents = uploadedDocumentItems.get(fileName);
                    return documents.isEmpty() ? null : this.vectorStoreDocumentService.putNewDocument(fileName,
                            documents);
                }).filter(Objects::nonNull).toList();
        updateDocumentContent();
        this.documentInfoChangeSupport.firePropertyChange(DOCUMENT_ADDING_EVENT, null, newDocumentInfos);
    }

    private void updateDocumentContent() {
        VaadinUtils.getUi(this).access(() -> {
            this.documentListBox.removeAll();
            this.documentListBox.setItems(this.vectorStoreDocumentService.getDocumentList());
        });
    }

    @Override
    public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
        updateDocumentContent();
    }
}
