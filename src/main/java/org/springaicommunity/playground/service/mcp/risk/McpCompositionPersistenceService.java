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
package org.springaicommunity.playground.service.mcp.risk;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class McpCompositionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(McpCompositionPersistenceService.class);
    private static final String FILE = "compositions.json";

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final PersistenceExecutor persistenceExecutor;

    public McpCompositionPersistenceService(Path springAiPlaygroundHomeDir, ObjectMapper objectMapper,
            PersistenceExecutor persistenceExecutor) throws IOException {
        Path baseDir = springAiPlaygroundHomeDir.resolve("mcp").resolve("risk");
        Files.createDirectories(baseDir);
        this.filePath = baseDir.resolve(FILE);
        this.objectMapper = objectMapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
        this.persistenceExecutor = persistenceExecutor;
    }

    public List<McpComposition> loadAll() {
        if (!Files.exists(this.filePath)) return List.of();
        try {
            byte[] bytes = Files.readAllBytes(this.filePath);
            if (bytes.length == 0) return List.of();
            return this.objectMapper.readValue(bytes, new TypeReference<List<McpComposition>>() {});
        } catch (IOException | JacksonException e) {
            logger.warn("Failed to load compositions from {} — starting empty", this.filePath, e);
            return List.of();
        }
    }

    public void saveAllAsync(List<McpComposition> compositions) {
        List<McpComposition> snapshot = new ArrayList<>(compositions);
        this.persistenceExecutor.submit(() -> {
            try {
                writeAtomically(snapshot);
            } catch (IOException | JacksonException e) {
                logger.error("Failed to persist compositions", e);
            }
        });
    }

    private void writeAtomically(List<McpComposition> compositions) throws IOException {
        Path tmp = this.filePath.resolveSibling(FILE + ".tmp");
        byte[] bytes = this.objectMapper.writeValueAsBytes(compositions);
        Files.write(tmp, bytes);
        Files.move(tmp, this.filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
