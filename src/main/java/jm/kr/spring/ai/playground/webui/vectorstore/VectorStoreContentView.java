package jm.kr.spring.ai.playground.webui.vectorstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.internal.JsonDecodingException;
import com.vaadin.flow.internal.JsonUtils;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
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

        crud.getGrid().setColumns("score", "id", "text", "metadata", "media");
        crud.getGrid().setPageSize(100);
        crud.getGrid().setColumnReorderingAllowed(true);

        TextField filter = new TextField();
        filter.setPlaceholder("Filter by Text");
        filter.setClearButtonVisible(true);
        filter.addValueChangeListener(e -> crud.refreshGrid());
        crud.getCrudLayout().addFilterComponent(filter);


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
                query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(), filter.getValue()).stream()
                        .map(this::convertToViewDocument),
                query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(), filter.getValue()).size()));
        crud.setAddOperation(vectorStoreContentItem -> {
            vectorStoreService.add(convertToDocument(vectorStoreContentItem));
            return vectorStoreContentItem;
        });

        crud.setCrudListener(new LazyCrudListener<>() {
            @Override
            public DataProvider<VectorStoreContentItem, Void> getDataProvider() {
                return DataProvider.fromFilteringCallbacks(
                        query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(), filter.getValue())
                                .stream().map(document -> convertToViewDocument(document)),
                        query -> vectorStoreService.searchAll(query.getPage(), query.getPageSize(), filter.getValue())
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
