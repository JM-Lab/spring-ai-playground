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
package org.springaicommunity.playground.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

    default void buildSaveData(T saveObject, Map<String, Object> saveObjectMap) {
    }

    String buildSaveFileName(T saveObject);

    T convertTo(Map<String, Object> saveObjectMap);

    void onStart() throws IOException;

    default void onShutdown() throws IOException {
    }

    default void save(T saveObject) throws IOException {
        Path saveDir = getSaveDir();
        String simpleName = saveObject.getClass().getSimpleName();
        Files.createDirectories(saveDir);
        Map<String, Object> saveObjectMap = OBJECT_MAPPER.convertValue(saveObject, MAP_TYPE_REFERENCE);
        buildSaveData(saveObject, saveObjectMap);
        String fileName = buildFileName(saveObject);
        Path target = resolveWithinSaveDir(saveDir, fileName);
        Path tmp = resolveWithinSaveDir(saveDir, fileName + ".tmp");

        getLogger().info("Saving {} to file: {}", simpleName, target);
        OBJECT_MAPPER.writeValue(tmp.toFile(), saveObjectMap);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String buildFileName(T saveObject) {
        return buildSaveFileName(saveObject) + ".json";
    }

    /**
     * Resolves {@code fileName} under {@code saveDir} and refuses any path that escapes the
     * directory (e.g. names containing {@code ..} or absolute paths). This guards against
     * path-traversal when {@link #buildSaveFileName} pulls user-controlled data into the file name.
     */
    private Path resolveWithinSaveDir(Path saveDir, String fileName) {
        Path normalizedSaveDir = saveDir.toAbsolutePath().normalize();
        Path resolved = normalizedSaveDir.resolve(fileName).normalize();
        if (!resolved.startsWith(normalizedSaveDir))
            throw new SecurityException(
                    "Refusing to access path outside save directory: " + resolved + " (saveDir=" + normalizedSaveDir +
                            ")");
        return resolved;
    }

    default boolean shouldLoadFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".json") && !name.endsWith(".tmp")
                && !name.endsWith(".tmp.json") && !name.endsWith(".deprecated");
    }

    default List<T> loads() throws IOException {
        List<T> saveObjectList = new ArrayList<>();
        try (Stream<Path> paths = Files.list(getSaveDir())) {
            List<File> fileList = paths.filter(this::shouldLoadFile).map(Path::toFile)
                    .filter(Predicate.not(File::isHidden))
                    .peek(file -> getLogger().info("Load file : {}", file.getAbsolutePath())).toList();
            for (File file : fileList)
                saveObjectList.add(convertTo(OBJECT_MAPPER.readValue(file, MAP_TYPE_REFERENCE)));
        }
        return saveObjectList;
    }

    default void delete(T saveObject) {
        Path file;
        try {
            file = resolveWithinSaveDir(getSaveDir(), buildFileName(saveObject));
        } catch (SecurityException e) {
            getLogger().error("Refusing to delete file outside save directory", e);
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            getLogger().error("Failed to delete save file {}", file, e);
        }
    }

    default void clear() {
        Path dir = getSaveDir();
        try (Stream<Path> paths = Files.list(dir)) {
            paths.forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            getLogger().error("Failed to clear save dir {}", dir, e);
        }
    }
}
