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
package org.springaicommunity.playground.webui.vectorstore;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.popover.PopoverVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingOptions;

import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springaicommunity.playground.webui.VaadinUtils.headerPopover;
import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;

@SpringComponent
@UIScope
@AnonymousAllowed
@CssImport("./playground/vectorstore-styles.css")
@PageTitle("Vector Database")
@Route(value = "vector-database", layout = SpringAiPlaygroundAppLayout.class)
public class VectorStoreView extends ContentWorkspaceView {

    public static final String DOCUMENT_SELECTING_EVENT = "DOCUMENT_SELECTING_EVENT";
    public static final String DOCUMENT_ADDING_EVENT = "DOCUMENT_ADDING_EVENT";
    public static final String DOCUMENTS_DELETE_EVENT = "DOCUMENTS_DELETE_EVENT";

    private final VectorStoreService vectorStoreService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final VectorStoreDocumentView vectorStoreDocumentView;
    private final VectorStoreContentView vectorStoreContentView;
    private final WorkspaceSettingsDrawer searchSettingsDrawer;

    public VectorStoreView(PersistentUiDataStorage persistentUiDataStorage, VectorStoreService vectorStoreService,
            VectorStoreDocumentService vectorStoreDocumentService) {
        this.vectorStoreService = vectorStoreService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;

        this.vectorStoreDocumentView =
                new VectorStoreDocumentView(vectorStoreDocumentService, buildPropertyChangeSupport());
        configureSidebar(this.vectorStoreDocumentView, "Documents");

        installNewDocumentPopover();

        setHeaderCenter(buildEmbeddingModelServiceTextDiv());

        VectorStoreSearchSettingView vectorStoreSearchSettingView =
                new VectorStoreSearchSettingView(this.vectorStoreService);
        this.searchSettingsDrawer = installSettingsDrawer(VaadinIcon.COG_O, "Search Settings",
                "Search Settings");
        this.searchSettingsDrawer.setBody(vectorStoreSearchSettingView);

        this.vectorStoreContentView = new VectorStoreContentView(persistentUiDataStorage, vectorStoreService);
        this.vectorStoreContentView.setSpacing(false);
        this.vectorStoreContentView.setMargin(false);
        this.vectorStoreContentView.setPadding(false);
        setContent(this.vectorStoreContentView);
    }

    private void installNewDocumentPopover() {
        Button newDocumentButton =
                styledButton("New Document & ETL Pipeline", VaadinIcon.FILE_ADD.create(), null);
        addHeaderAction(newDocumentButton);

        Popover newDocumentPopover = headerPopover(newDocumentButton, "New Document & ETL Pipeline");
        newDocumentPopover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
        newDocumentPopover.setPosition(PopoverPosition.BOTTOM_END);
        newDocumentPopover.setModal(true);

        VectorStoreDocumentUpload vectorStoreDocumentUpload =
                new VectorStoreDocumentUpload(this.vectorStoreDocumentService);
        vectorStoreDocumentUpload.getStyle().set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");
        newDocumentPopover.add(vectorStoreDocumentUpload);

        Button chunkDocumentButton = new Button("Chunk Document");
        chunkDocumentButton.addThemeVariants(ButtonVariant.LUMO_SMALL);

        HorizontalLayout buttonLayout = new HorizontalLayout(chunkDocumentButton);
        buttonLayout.setWidthFull();
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);

        VectorStoreDocumentTokenChunkInfo vectorStoreDocumentTokenChunkInfo = new VectorStoreDocumentTokenChunkInfo();

        VerticalLayout outerLayout = new VerticalLayout(vectorStoreDocumentTokenChunkInfo, buttonLayout);
        outerLayout.setPadding(false);
        outerLayout.setSpacing(false);
        outerLayout.setWidthFull();

        vectorStoreDocumentUpload.add(outerLayout);

        chunkDocumentButton.addClickListener(buttonClickEvent -> {
            newDocumentPopover.close();
            List<String> uploadedFileNames = new ArrayList<>(vectorStoreDocumentUpload.getUploadedFileNames());
            vectorStoreDocumentUpload.clearFileList();
            if (uploadedFileNames.isEmpty()) {
                VaadinUtils.showInfoNotification("No uploaded files found");
                return;
            }

            Map<String, List<Document>> uploadedDocumentItems =
                    this.vectorStoreDocumentService.extractDocumentItems(uploadedFileNames,
                            vectorStoreDocumentService.newTokenTextSplitter(
                                    vectorStoreDocumentTokenChunkInfo.collectInput()));
            List<Document> chunks = uploadedDocumentItems.values().stream().flatMap(List::stream).toList();
            if (chunks.isEmpty()) {
                VaadinUtils.showInfoNotification("No chunks found");
                return;
            }

            MultiSelectListBox<Document> documentListBox = new MultiSelectListBox<>();
            documentListBox.setRenderer(
                    new ComponentRenderer<Component, Document>(document -> new Span(document.getText())));

            documentListBox.setItems(chunks);
            documentListBox.select(chunks);

            Dialog confirmationDialog = VaadinUtils.headerDialog(
                    String.format("Chunk Summary - %d chunks successfully extracted", chunks.size()));
            confirmationDialog.setModality(ModalityMode.MODELESS);
            Button confirmButton = new Button("Embed and Insert Confirm");
            confirmationDialog.add(confirmButton, documentListBox);
            confirmationDialog.open();
            confirmButton.addClickListener(event -> {
                confirmationDialog.setEnabled(false);
                confirmationDialog.close();
                Set<Document> selectedItems = documentListBox.getSelectedItems();
                Map<String, List<Document>> filenameDocuments =
                        uploadedDocumentItems.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> entry.getValue().stream().filter(selectedItems::contains).toList()));
                this.vectorStoreDocumentView.addDocumentContent(uploadedFileNames, filenameDocuments);
            });
        });
    }

    private PropertyChangeSupport buildPropertyChangeSupport() {
        PropertyChangeSupport documentInfoChangeSupport = new PropertyChangeSupport(this);
        documentInfoChangeSupport.addPropertyChangeListener(changeEvent -> {
            if (Objects.isNull(changeEvent.getNewValue()))
                return;
            Collection<VectorStoreDocumentInfo> newDocumentInfos =
                    (Collection<VectorStoreDocumentInfo>) changeEvent.getNewValue();
            switch (changeEvent.getPropertyName()) {
                case DOCUMENT_ADDING_EVENT -> handleDocumentAdding(newDocumentInfos);
                case DOCUMENT_SELECTING_EVENT -> handleDocumentSelecting(newDocumentInfos);
                case DOCUMENTS_DELETE_EVENT -> handleDocumentDeleting(newDocumentInfos);
            }
        });
        return documentInfoChangeSupport;
    }

    private void handleDocumentAdding(Collection<VectorStoreDocumentInfo> newEventDocumentInfos) {
        newEventDocumentInfos.forEach(this.vectorStoreService::add);
        handleDocumentSelecting(newEventDocumentInfos);
    }

    private void handleDocumentSelecting(Collection<VectorStoreDocumentInfo> newEventDocumentInfos) {
        this.vectorStoreContentView.showDocuments(
                newEventDocumentInfos.stream().map(VectorStoreDocumentInfo::docInfoId).toList());
    }

    private void handleDocumentDeleting(Collection<VectorStoreDocumentInfo> newEventDocumentInfos) {
        this.vectorStoreService.delete(
                newEventDocumentInfos.stream().map(VectorStoreDocumentInfo::documentListSupplier).map(Supplier::get)
                        .flatMap(List::stream).map(Document::getId).toList());
        vectorStoreContentView.showAllDocuments();
    }

    private Div buildEmbeddingModelServiceTextDiv() {
        H4 embeddingModelServiceText = buildEmbeddingModelServiceText();
        embeddingModelServiceText.getStyle().set("white-space", "nowrap").set("margin", "0");
        Div embeddingModelServiceTextDiv = new Div(embeddingModelServiceText);
        embeddingModelServiceTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");
        return embeddingModelServiceTextDiv;
    }

    private H4 buildEmbeddingModelServiceText() {
        EmbeddingOptions embeddingOptions = this.vectorStoreService.getEmbeddingOptions();
        return new H4(Objects.nonNull(embeddingOptions.getDimensions()) ?
                String.format("%s - %s: %s - %d", this.vectorStoreService.getVectorStoreName(),
                        vectorStoreService.getEmbeddingModelServiceName(), embeddingOptions.getModel(),
                        embeddingOptions.getDimensions()) :
                String.format("%s - %s: %s", this.vectorStoreService.getVectorStoreName(),
                        this.vectorStoreService.getEmbeddingModelServiceName(), embeddingOptions.getModel()));
    }
}
