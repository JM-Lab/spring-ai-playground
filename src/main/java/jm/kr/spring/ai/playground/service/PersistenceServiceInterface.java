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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface PersistenceServiceInterface<T> {

    TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    Path getSaveDir();

    Logger getLogger();

    String buildSaveDataAndReturnName(T saveObject, Map<String, Object> saveObjectMap);

    T convertTo(Map<String, Object> saveObjectMap);

    default void save(T saveObject) throws IOException {
        Map<String, Object> saveObjectMap = OBJECT_MAPPER.convertValue(saveObject, MAP_TYPE_REFERENCE);
        File file = getSaveDir().resolve(buildSaveDataAndReturnName(saveObject, saveObjectMap) + ".json").toFile();
        getLogger().info("Saving {} to file: {}", saveObject.getClass().getSimpleName(), file.getAbsolutePath());
        OBJECT_MAPPER.writeValue(file, saveObjectMap);
    }

    default List<T> loads() throws IOException {
        List<T> saveObjectList = new ArrayList<>();
        try (Stream<Path> paths = Files.list(getSaveDir())) {
            List<File> fileList = paths.map(Path::toFile)
                    .peek(file -> getLogger().info("Load file : {}", file.getAbsolutePath())).toList();
            for (File file : fileList)
                saveObjectList.add(convertTo(OBJECT_MAPPER.readValue(file, MAP_TYPE_REFERENCE)));
        }
        return saveObjectList;
    }

    default void clear() {
        getSaveDir().toFile().deleteOnExit();
    }
}
