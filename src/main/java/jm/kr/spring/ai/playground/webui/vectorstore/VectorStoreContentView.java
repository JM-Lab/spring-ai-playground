package jm.kr.spring.ai.playground.webui.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.internal.JsonDecodingException;
import com.vaadin.flow.internal.JsonUtils;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.util.JacksonUtils;
import org.vaadin.crudui.crud.CrudOperation;
import org.vaadin.crudui.crud.LazyCrudListener;
import org.vaadin.crudui.crud.impl.GridCrud;

import java.util.Map;


public class VectorStoreContentView extends VerticalLayout {

    private static final ObjectMapper ObjectMapper =
            JsonMapper.builder().addModules(JacksonUtils.instantiateAvailableModules()).build();

    public VectorStoreContentView(VectorStoreService vectorStoreService) {
        GridCrud<VectorStoreContentItem> crud = new GridCrud<>(VectorStoreContentItem.class);
        crud.getGrid().getElement().setAttribute("theme", "no-border");
        crud.getGrid().setColumns("score", "id", "text", "metadata", "media");
        crud.getGrid().setPageSize(100);
        crud.getGrid().setColumnReorderingAllowed(true);


        Button searchButton = VaadinUtils.styledButton("Search", VaadinIcon.SEARCH.create(), buttonClickEvent -> {});

        TextField userPromptTextField = new TextField();
        userPromptTextField.setPlaceholder("Enter a prompt to search for similarity…");
        userPromptTextField.setWidthFull();
        userPromptTextField.setAutofocus(true);
        userPromptTextField.focus();
        userPromptTextField.getStyle().setPadding("0");
        userPromptTextField.setValueChangeMode(ValueChangeMode.EAGER);
        userPromptTextField.addKeyDownListener(Key.ENTER, event -> {
            if (!event.isComposing() && !event.getModifiers().contains(KeyModifier.SHIFT))
                searchButton.click();
        });


        userPromptTextField.setSuffixComponent(searchButton);

        TextField filterExpressionTextField = new TextField();
        filterExpressionTextField.setPlaceholder("Type a metadata filter with Spring AI’s Filter Expression");
        filterExpressionTextField.setWidthFull();
        filterExpressionTextField.getStyle().setPadding("0");
        Button clearButton = VaadinUtils.styledButton("Clear", VaadinIcon.ERASER.create(),
                buttonClickEvent -> buttonClickEvent.getSource().setText(""));
        filterExpressionTextField.setSuffixComponent(clearButton);


        HorizontalLayout searchInputLayout = new HorizontalLayout(userPromptTextField, filterExpressionTextField);
        searchInputLayout.setDefaultVerticalComponentAlignment(Alignment.CENTER);
        searchInputLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        searchInputLayout.setWidthFull();
        searchInputLayout.setPadding(false);
        searchInputLayout.getStyle().set("marginRight", "var(--lumo-space-l)");
        ((Component) crud.getCrudLayout()).addClassName("custom-container");
        crud.getCrudLayout().addToolbarComponent(searchInputLayout);


        crud.getCrudFormFactory().setUseBeanValidation(true);
        crud.getCrudFormFactory().setVisibleProperties("score", "id", "text", "metadata", "media", "embedding");
        crud.getCrudFormFactory().setVisibleProperties(CrudOperation.UPDATE, "id", "media", "metadata");

        crud.getCrudFormFactory().setFieldProvider("text", o -> new TextArea());
        crud.getCrudFormFactory().setFieldProvider("metadata", o -> new TextArea());
        crud.getCrudFormFactory().setFieldProvider("embedding", o -> new TextArea());


        crud.setRowCountCaption("%d found");
        crud.setFindAllOperationVisible(true);


        setSizeFull();
        add(crud);

        crud.setFindAllOperation(DataProvider.fromFilteringCallbacks(
                query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(),
                                filterExpressionTextField.getValue()).stream()
                        .map(this::convertToViewDocument),
                query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(),
                        filterExpressionTextField.getValue()).size()));
        crud.setAddOperation(vectorStoreContentItem -> {
            vectorStoreService.add(convertToDocument(vectorStoreContentItem));
            return vectorStoreContentItem;
        });

        crud.setCrudListener(new LazyCrudListener<>() {
            @Override
            public DataProvider<VectorStoreContentItem, Void> getDataProvider() {
                return DataProvider.fromFilteringCallbacks(
                        query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(),
                                        filterExpressionTextField.getValue())
                                .stream().map(document -> convertToViewDocument(document)),
                        query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(),
                                        filterExpressionTextField.getValue())
                                .size());
            }

            @Override
            public VectorStoreContentItem add(VectorStoreContentItem document) {
                vectorStoreService.add(convertToDocument(document));
                return document;
            }

            @Override
            public VectorStoreContentItem update(VectorStoreContentItem document) {
                vectorStoreService.update(convertToDocument(document));
                return document;
            }

            @Override
            public void delete(VectorStoreContentItem document) {
                vectorStoreService.delete(convertToDocument(document));
            }
        });

    }

    private VectorStoreContentItem convertToViewDocument(Document document) {
        return new VectorStoreContentItem(document.getScore(), document.getId(), document.getText(),
                JsonUtils.writeValue(Map.of()).toJson(),
                JsonUtils.writeValue(document.getMetadata()).toJson(), "");
    }

    private <T> T readToObject(String jsonString, TypeReference<T> typeReference) {
        try {
            return ObjectMapper.readValue(jsonString, typeReference);
        } catch (JsonProcessingException e) {
            throw new JsonDecodingException("Error converting JsonValue to " + typeReference.getType().getTypeName(),
                    e);
        }
    }

    private Document convertToDocument(VectorStoreContentItem vectorStoreContentItem) {
        return new Document.Builder()
                .id(vectorStoreContentItem.id())
                .text(vectorStoreContentItem.text())
                .media(readToObject(vectorStoreContentItem.media(), new TypeReference<>() {}))
                .metadata(readToObject(vectorStoreContentItem.metadata(), new TypeReference<>() {}))
                .build();
    }

}
