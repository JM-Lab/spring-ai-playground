package jm.kr.spring.ai.playground.webui.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyDownEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.ColumnRendering;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.contextmenu.GridContextMenu;
import com.vaadin.flow.component.grid.dataview.GridDataView;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.internal.JsonDecodingException;
import com.vaadin.flow.internal.JsonUtils;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.vaadin.crudui.crud.CrudOperation;
import org.vaadin.crudui.crud.impl.GridCrud;
import org.vaadin.crudui.form.CrudFormFactory;
import org.vaadin.crudui.form.impl.form.factory.DefaultCrudFormFactory;
import org.vaadin.crudui.layout.CrudLayout;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import static com.vaadin.flow.component.grid.GridVariant.LUMO_NO_BORDER;
import static com.vaadin.flow.component.grid.GridVariant.LUMO_ROW_STRIPES;
import static com.vaadin.flow.component.grid.GridVariant.LUMO_WRAP_CELL_CONTENT;
import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.ALL_SEARCH_REQUEST_OPTION;
import static jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView.DOC_INFO_ID;

public class VectorStoreContentView extends VerticalLayout {

    public static final String CUSTOM_ADD_DOC_INFO_ID = "docInfoId-custom";
    private static final ObjectMapper ObjectMapper =
            JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules())
                    .enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    private static final TypeReference<Media> MEDIA_TYPE_REFERENCE = new TypeReference<>() {};

    private final VectorStoreService vectorStoreService;
    private final GridCrud<VectorStoreContentItem> gridCrud;
    private final GridDataView<VectorStoreContentItem> dataView;
    private SearchRequest searchRequest;

    public VectorStoreContentView(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;

        setSizeFull();

        this.gridCrud = new GridCrud<>(VectorStoreContentItem.class,
                new DefaultCrudFormFactory<>(VectorStoreContentItem.class, new FormLayout.ResponsiveStep("0px", 1))) {

            @Override
            protected void deleteButtonClicked() {
                VectorStoreContentItem domainObject = grid.asSingleSelect().getValue();
                showForm(CrudOperation.DELETE, domainObject, true, deletedMessage, event -> {
                    try {
                        deleteOperation.perform(domainObject);
                        showAllDocuments();
                        grid.asSingleSelect().clear();
                    } catch (Exception e) {
                        VaadinUtils.showErrorNotification(e.getMessage());
                    }
                });
            }

            @Override
            protected void findAllButtonClicked() {
                grid.asSingleSelect().clear();
                showAllDocuments();
            }

            @Override
            protected void addButtonClicked() {
                VectorStoreContentItem domainObject = crudFormFactory.getNewInstanceSupplier().get();
                showForm(CrudOperation.ADD, domainObject, false, savedMessage, event -> {
                    try {
                        VectorStoreContentItem addedObject = addOperation.perform(domainObject);
                        afterRefreshGrid(addedObject);
                    } catch (Exception e) {
                        VaadinUtils.showErrorNotification(e.getMessage());
                    }
                });
            }

            private void afterRefreshGrid(VectorStoreContentItem vectorStoreContentItem) {
                showAllDocuments();
                VectorStoreContentItem realVectorStoreContentItem =
                        dataView.getItems().filter(item -> item.getId().equals(vectorStoreContentItem.getId()))
                                .findFirst().orElseGet(() -> dataView.getItem(0));
                grid.asSingleSelect().setValue(realVectorStoreContentItem);
                grid.deselect(realVectorStoreContentItem);
                grid.scrollToItem(realVectorStoreContentItem);
            }

            @Override
            protected void updateButtonClicked() {
                VectorStoreContentItem domainObject = grid.asSingleSelect().getValue();
                showForm(CrudOperation.UPDATE, domainObject, false, savedMessage, event -> {
                    try {
                        VectorStoreContentItem updatedObject = updateOperation.perform(domainObject);
                        grid.asSingleSelect().clear();
                        afterRefreshGrid(updatedObject);
                    } catch (Exception e) {
                        VaadinUtils.showErrorNotification(e.getMessage());
                    }
                });
            }
        };

        Button findAllButton = this.gridCrud.getFindAllButton();
        findAllButton.getStyle().set("marginLeft", "var(--lumo-space-s)");
        findAllButton.setTooltipText("Search all");
        this.gridCrud.getAddButton().setTooltipText("Add a custom chunk");
        this.gridCrud.getUpdateButton().setTooltipText("Update a chunk");
        this.gridCrud.getDeleteButton().setTooltipText("Delete a chunk");
        new VectorStoreContentContextMenu(this.gridCrud);
        add(this.gridCrud);

        TextField userPromptTextField = new TextField();
        userPromptTextField.setPlaceholder("Enter a prompt to test similarity search...");
        userPromptTextField.setWidth("60%");
        userPromptTextField.setAutofocus(true);
        userPromptTextField.focus();
        userPromptTextField.getStyle().setPadding("0");
        userPromptTextField.setClearButtonVisible(true);
        userPromptTextField.setValueChangeMode(ValueChangeMode.EAGER);
        Button searchButton =
                VaadinUtils.styledButton("Search", VaadinIcon.SEARCH.create(), buttonClickEvent -> refreshGrid());
        userPromptTextField.setSuffixComponent(searchButton);
        userPromptTextField.addKeyDownListener(Key.ENTER, event -> clickSearchButton(event, searchButton));

        TextField filterExpressionTextField = new TextField();
        filterExpressionTextField.setPlaceholder("Enter a Spring AI metadata filters, e.g., country == 'BG'");
        filterExpressionTextField.setWidth("40%");
        filterExpressionTextField.getStyle().setPadding("0");
        filterExpressionTextField.setClearButtonVisible(true);
        Icon filterExpressionInfoIcon = VaadinUtils.styledIcon(VaadinIcon.INFO_CIRCLE.create());
        filterExpressionInfoIcon.addClickListener(event -> VaadinUtils.getUi(this).getPage()
                .open("https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_filter_string"));
        filterExpressionInfoIcon.setTooltipText("Click for metadata filter documentation");
        filterExpressionTextField.setSuffixComponent(filterExpressionInfoIcon);
        filterExpressionTextField.addKeyDownListener(Key.ENTER, event -> clickSearchButton(event, searchButton));

        HorizontalLayout searchInputLayout =
                new HorizontalLayout(userPromptTextField, filterExpressionTextField);
        searchInputLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        searchInputLayout.setJustifyContentMode(JustifyContentMode.START);
        searchInputLayout.setWidthFull();
        searchInputLayout.setPadding(false);
        searchInputLayout.getStyle().set("marginRight", "var(--lumo-space-s)");
        CrudLayout crudLayout = this.gridCrud.getCrudLayout();
        ((Component) crudLayout).addClassName("custom-container");
        crudLayout.addToolbarComponent(searchInputLayout);

        Grid<VectorStoreContentItem> grid = this.gridCrud.getGrid();
        this.dataView = grid.getGenericDataView();
        this.gridCrud.setOperations(
                () -> (Objects.isNull(this.searchRequest) ? vectorStoreService.search(userPromptTextField.getValue(),
                        filterExpressionTextField.getValue()) : vectorStoreService.search(this.searchRequest)).stream()
                        .map(this::convertToViewDocument).toList(),
                item -> convertToViewDocument(this.vectorStoreService.add(buildCustomChunk(item))),
                item -> convertToViewDocument(this.vectorStoreService.update(convertToDocument(item))),
                item -> vectorStoreService.delete(
                        grid.getSelectedItems().stream().map(VectorStoreContentItem::getId).toList()));

        grid.addThemeVariants(LUMO_NO_BORDER, LUMO_WRAP_CELL_CONTENT, LUMO_ROW_STRIPES);
        grid.setColumns("score", "id", "text", "metadata", "media");
        grid.setColumnReorderingAllowed(true);
        grid.setColumnRendering(ColumnRendering.LAZY);
        grid.setEmptyStateText("No items found.");
        Map<String, String> columnWidths = Map.of(
                "Score", "10%",
                "Id", "15%",
                "Text", "50%",
                "Metadata", "20%",
                "Media", "5%"
        );
        grid.getColumns().forEach(column -> {
            column.setResizable(true);
            column.setWidth(columnWidths.getOrDefault(column.getHeaderText(), "auto"));
        });

        CrudFormFactory<VectorStoreContentItem> crudFormFactory = this.gridCrud.getCrudFormFactory();
        crudFormFactory.setUseBeanValidation(true);
        crudFormFactory.setVisibleProperties("id", "text", "metadata", "media");
        crudFormFactory.setDisabledProperties("score", "id", "media");

        crudFormFactory.setFieldProvider("text", o -> {
            TextArea textArea = new TextArea();
            textArea.setWidthFull();
            return textArea;
        });
        crudFormFactory.setFieldProvider("metadata", o -> {
            TextArea textArea = new TextArea();
            textArea.setWidthFull();
            textArea.setPlaceholder("Example JSON object:\n{ \"key\": \"value\" }");
            return textArea;
        });
    }

    private static void clickSearchButton(KeyDownEvent event, Button searchButton) {
        if (!event.isComposing())
            searchButton.click();
    }

    private Document buildCustomChunk(VectorStoreContentItem item) {
        Document document = convertToDocument(item);
        document.getMetadata().put(DOC_INFO_ID, CUSTOM_ADD_DOC_INFO_ID);
        return document;
    }

    private VectorStoreContentItem convertToViewDocument(Document document) {
        return new VectorStoreContentItem(document.getScore(), document.getId(), document.getText(),
                Objects.nonNull(document.getMedia()) ? JsonUtils.writeValue(document.getMedia())
                        .toJson() : null,
                JsonUtils.beanToJson(document.getMetadata()).toJson());
    }

    private <T> T readToObject(String jsonString, TypeReference<T> typeReference) {
        try {
            return ObjectMapper.readValue(jsonString, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonDecodingException(
                    "Error converting JsonValue to " + typeReference.getType().getTypeName(),
                    e);
        }
    }

    private Document convertToDocument(VectorStoreContentItem vectorStoreContentItem) {
        Document.Builder builder = new Document.Builder();
        Optional.ofNullable(vectorStoreContentItem.getId()).filter(Predicate.not(String::isBlank))
                .ifPresent(builder::id);
        builder.metadata(
                Optional.ofNullable(vectorStoreContentItem.getMetadata()).filter(Predicate.not(String::isBlank))
                        .map(metadata -> readToObject(metadata, MAP_TYPE_REFERENCE)).orElseGet(Map::of));
        Optional.ofNullable(vectorStoreContentItem.getMedia()).filter(Predicate.not(String::isBlank))
                .map(media -> readToObject(media, MEDIA_TYPE_REFERENCE)).ifPresent(builder::media);
        return builder.text(vectorStoreContentItem.getText()).build();
    }

    public void showDocuments(List<String> selectDocInfoIds) {
        this.searchRequest = new SearchRequest.Builder().filterExpression(
                        new FilterExpressionBuilder().in(DOC_INFO_ID, selectDocInfoIds.toArray()).build())
                .similarityThreshold(ALL_SEARCH_REQUEST_OPTION.similarityThreshold())
                .topK(ALL_SEARCH_REQUEST_OPTION.topK()).build();
        refreshGrid();
        this.searchRequest = null;
    }

    public void showAllDocuments() {
        searchRequest =
                new SearchRequest.Builder().similarityThreshold(ALL_SEARCH_REQUEST_OPTION.similarityThreshold())
                        .topK(ALL_SEARCH_REQUEST_OPTION.topK()).build();
        refreshGrid();
        searchRequest = null;
    }

    private void refreshGrid() {
        try {
            this.gridCrud.refreshGrid();
            VaadinUtils.showInfoNotification(String.format("Search results: %d items (Threshold: %.2f, TopK: %d)",
                    dataView.getItems().count(), Objects.nonNull(this.searchRequest) ?
                            ALL_SEARCH_REQUEST_OPTION.similarityThreshold() : vectorStoreService.getSearchRequestOption()
                            .similarityThreshold(),
                    Objects.nonNull(this.searchRequest) ? ALL_SEARCH_REQUEST_OPTION.topK()
                            : vectorStoreService.getSearchRequestOption().topK()));
        } catch (Exception e) {
            VaadinUtils.showErrorNotification(e.getMessage());
        }
    }

    private static class VectorStoreContentContextMenu extends GridContextMenu<VectorStoreContentItem> {
        public VectorStoreContentContextMenu(GridCrud<VectorStoreContentItem> gridCrud) {
            super(gridCrud.getGrid());
            Grid<VectorStoreContentItem> grid = gridCrud.getGrid();
            addItem("Edit", e -> e.getItem().ifPresent(item -> {
                grid.select(item);
                gridCrud.getUpdateButton().click();
            }));
            addItem("Delete", e -> e.getItem().ifPresent(item -> {
                grid.select(item);
                gridCrud.getDeleteButton().click();
            }));
            setDynamicContentHandler(Objects::nonNull);
        }

    }
}
