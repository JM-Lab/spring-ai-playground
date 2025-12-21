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
package jm.kr.spring.ai.playground.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface PersistenceServiceInterface<T> {

    TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    Path getSaveDir();

    Logger getLogger();

    void buildSaveData(T saveObject, Map<String, Object> saveObjectMap);

    String buildSaveFileName(T saveObject);

    T convertTo(Map<String, Object> saveObjectMap);

    void onStart() throws IOException;

    void onShutdown() throws IOException;

    default void save(T saveObject) throws IOException {
        Path saveDir = getSaveDir();
        String simpleName = saveObject.getClass().getSimpleName();
        Files.createDirectories(saveDir);
        Map<String, Object> saveObjectMap = OBJECT_MAPPER.convertValue(saveObject, MAP_TYPE_REFERENCE);
        buildSaveData(saveObject, saveObjectMap);
        File file = saveDir.resolve(buildFileName(saveObject)).toFile();

        getLogger().info("Saving {} to file: {}", simpleName, file.getAbsolutePath());
        OBJECT_MAPPER.writeValue(file, saveObjectMap);
    }

    private String buildFileName(T saveObject) {
        return buildSaveFileName(saveObject) + ".json";
    }

    default List<T> loads() throws IOException {
        List<T> saveObjectList = new ArrayList<>();
        try (Stream<Path> paths = Files.list(getSaveDir())) {
            List<File> fileList = paths.map(Path::toFile).filter(Predicate.not(File::isHidden))
                    .peek(file -> getLogger().info("Load file : {}", file.getAbsolutePath())).toList();
            for (File file : fileList)
                saveObjectList.add(convertTo(OBJECT_MAPPER.readValue(file, MAP_TYPE_REFERENCE)));
        }
        return saveObjectList;
    }

    default void delete(T saveObject) {
        getSaveDir().resolve(buildFileName(saveObject)).toFile().deleteOnExit();
    }

    default void clear() {
        getSaveDir().toFile().deleteOnExit();
    }
}
