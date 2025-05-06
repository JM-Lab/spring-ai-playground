package jm.kr.spring.ai.playground.webui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.spring.annotation.UIScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.vaadin.flow.component.page.WebStorage.Storage.LOCAL_STORAGE;

@UIScope
@Service
public class PersistentUiDataStorage {
    private static final Logger logger = LoggerFactory.getLogger(PersistentUiDataStorage.class);
    private final ObjectMapper objectMapper;

    public PersistentUiDataStorage(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> void saveData(String key, T data) {
        try {
            String jsonValue = objectMapper.writeValueAsString(data);
            WebStorage.setItem(LOCAL_STORAGE, key, jsonValue);
            logger.debug("Data saved to localStorage. Key: {}", key);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON: {}", e.getMessage());
        }
    }

    public <T> void loadData(String key, TypeReference<T> typeReference, Consumer<T> callback) {
        WebStorage.getItem(LOCAL_STORAGE, key, jsonValue -> {
            if (jsonValue != null && !jsonValue.isBlank()) {
                try {
                    T data = objectMapper.readValue(jsonValue, typeReference);
                    callback.accept(data);
                    logger.debug("Data loaded from localStorage. Key: {}", key);
                } catch (JsonProcessingException e) {
                    logger.error("Error converting JSON to object: {}", e.getMessage());
                    callback.accept(null);
                }
            } else {
                logger.debug("No data found in localStorage for key: {}", key);
                callback.accept(null);
            }
        });
    }

    public <T> CompletableFuture<T> loadDataAsync(String key, TypeReference<T> typeReference) {
        CompletableFuture<T> future = new CompletableFuture<>();
        loadData(key, typeReference, future::complete);
        return future;
    }

    public void removeData(String key) {
        WebStorage.removeItem(LOCAL_STORAGE, key);
        logger.debug("Data removed from localStorage. Key: {}", key);
    }

    public void clearAll() {
        WebStorage.clear(LOCAL_STORAGE);
        logger.debug("All data cleared from localStorage.");
    }
}
