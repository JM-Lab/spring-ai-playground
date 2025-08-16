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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
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
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentInfo;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.webui.PersistentUiDataStorage;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.jetbrains.annotations.NotNull;
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

import static jm.kr.spring.ai.playground.webui.VaadinUtils.headerPopover;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@SpringComponent
@UIScope
@CssImport("./playground/vectorstore-styles.css")
@PageTitle("Vector Database")
@Route(value = "vector", layout = SpringAiPlaygroundAppLayout.class)
public class VectorStoreView extends Div {

    public static final String DOCUMENT_SELECTING_EVENT = "DOCUMENT_SELECTING_EVENT";
    public static final String DOCUMENT_ADDING_EVENT = "DOCUMENT_ADDING_EVENT";
    public static final String DOCUMENTS_DELETE_EVENT = "DOCUMENTS_DELETE_EVENT";
    private final VectorStoreService vectorStoreService;
    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final VectorStoreDocumentView vectorStoreDocumentView;
    private final VectorStoreContentView vectorStoreContentView;
    private final SplitLayout splitLayout;
    private double splitterPosition;
    private boolean sidebarCollapsed;

    public VectorStoreView(PersistentUiDataStorage persistentUiDataStorage, VectorStoreService vectorStoreService,
            VectorStoreDocumentService vectorStoreDocumentService) {

        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.vectorStoreService = vectorStoreService;
        this.vectorStoreDocumentService = vectorStoreDocumentService;

        this.vectorStoreDocumentView =
                new VectorStoreDocumentView(vectorStoreDocumentService, buildPropertyChangeSupport());
        this.splitLayout.addToPrimary(this.vectorStoreDocumentView);
        this.vectorStoreContentView = new VectorStoreContentView(persistentUiDataStorage, vectorStoreService);
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
        vectorStoreContentLayout.add(createDocumentContentHeader(), vectorStoreContentView);

        this.splitLayout.addToSecondary(vectorStoreContentLayout);
        this.sidebarCollapsed = false;
    }

    private PropertyChangeSupport buildPropertyChangeSupport() {
        PropertyChangeSupport documentInfoChangeSupport = new PropertyChangeSupport(this);
        documentInfoChangeSupport.addPropertyChangeListener(changeEvent -> {
            if (Objects.isNull(changeEvent.getNewValue()))
                return;
            Collection<VectorStoreDocumentInfo> newDocumentInfos =
                    (Collection<VectorStoreDocumentInfo>) changeEvent.getNewValue();
            if (newDocumentInfos.isEmpty())
                return;
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

        Button newDocumentButton = styledButton("New Document", VaadinIcon.FILE_ADD.create(), null);
        horizontalLayout.add(newDocumentButton);

        Popover newDocumentPopover = headerPopover(newDocumentButton, "Upload Document");
        newDocumentPopover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
        newDocumentPopover.setPosition(PopoverPosition.BOTTOM_END);
        newDocumentPopover.setModal(true);

        VectorStoreDocumentUpload vectorStoreDocumentUpload =
                new VectorStoreDocumentUpload(this.vectorStoreDocumentService);
        vectorStoreDocumentUpload.getStyle().set("padding", "0 var(--lumo-space-m) 0 var(--lumo-space-m)");
        newDocumentPopover.add(vectorStoreDocumentUpload);

        HorizontalLayout chunkDocumentHorizontalLayout = new HorizontalLayout();
        vectorStoreDocumentUpload.add(chunkDocumentHorizontalLayout);
        chunkDocumentHorizontalLayout.setWidthFull();
        chunkDocumentHorizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        chunkDocumentHorizontalLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");

        Button chunkDocumentButton = new Button("Chunk Document");
        chunkDocumentButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
        chunkDocumentHorizontalLayout.add(chunkDocumentButton);
        chunkDocumentButton.addClickListener(buttonClickEvent -> {
            newDocumentPopover.close();
            List<String> uploadedFileNames = new ArrayList<>(vectorStoreDocumentUpload.getUploadedFileNames());
            vectorStoreDocumentUpload.clearFileList();
            if (uploadedFileNames.isEmpty()) {
                VaadinUtils.showInfoNotification("No uploaded files found");
                return;
            }
            Map<String, List<Document>> uploadedDocumentItems =
                    this.vectorStoreDocumentService.extractDocumentItems(uploadedFileNames);
            List<Document> chunks =
                    uploadedDocumentItems.values().stream().flatMap(List::stream).toList();
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
            confirmationDialog.setModal(false);
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

        HorizontalLayout vectorStoreLabelLayout = new HorizontalLayout(buildEmbeddingModelServiceTextDiv());
        vectorStoreLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        vectorStoreLabelLayout.setWidthFull();
        horizontalLayout.add(vectorStoreLabelLayout);

        Icon searchSettingIcon = styledIcon(VaadinIcon.COG_O.create());
        searchSettingIcon.getStyle().set("marginRight", "var(--lumo-space-l)");
        Popover searchSettingPopover = headerPopover(searchSettingIcon, "Search Settings");
        searchSettingPopover.setWidth("250px");
        searchSettingPopover.setHoverDelay(0);
        searchSettingPopover.addThemeVariants(PopoverVariant.ARROW, PopoverVariant.LUMO_NO_PADDING);
        searchSettingPopover.setPosition(PopoverPosition.BOTTOM);
        searchSettingPopover.setModal(true);

        VectorStoreSearchSettingView vectorStoreSearchSettingView =
                new VectorStoreSearchSettingView(this.vectorStoreService);
        vectorStoreSearchSettingView.getStyle()
                .set("padding", "0 var(--lumo-space-m) var(--lumo-space-m) var(--lumo-space-m)");
        searchSettingPopover.add(vectorStoreSearchSettingView);

        MenuBar searchSettingMenuBar = new MenuBar();
        searchSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        searchSettingMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        searchSettingMenuBar.addItem(searchSettingIcon);

        horizontalLayout.add(searchSettingMenuBar);
        return horizontalLayout;
    }

    private @NotNull Div buildEmbeddingModelServiceTextDiv() {
        H4 embeddingModelServiceText = buildEmbeddingModelServiceText();
        embeddingModelServiceText.getStyle().set("white-space", "nowrap");
        Div embeddingModelServiceTextDiv = new Div(embeddingModelServiceText);
        embeddingModelServiceTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");
        return embeddingModelServiceTextDiv;
    }

    private @NotNull H4 buildEmbeddingModelServiceText() {
        EmbeddingOptions embeddingOptions = this.vectorStoreService.getEmbeddingOptions();
        return new H4(Objects.nonNull(embeddingOptions.getDimensions())
                ? String.format("%s - %s - %d", this.vectorStoreService.getEmbeddingModelServiceName(),
                embeddingOptions.getModel(), embeddingOptions.getDimensions())
                : String.format("%s - %s", this.vectorStoreService.getEmbeddingModelServiceName(),
                embeddingOptions.getModel()));
    }

}
