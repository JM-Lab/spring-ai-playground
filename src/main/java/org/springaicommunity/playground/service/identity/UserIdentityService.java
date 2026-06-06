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
package org.springaicommunity.playground.service.identity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserIdentityService {

    private static final Logger logger = LoggerFactory.getLogger(UserIdentityService.class);
    private static final String FILE = "installation.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Installation(String deviceId, String source, long createdAtEpochMs) {}

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final String deviceId;

    public UserIdentityService(Path springAiPlaygroundHomeDir, ObjectMapper objectMapper,
            DeviceIdProvider deviceIdProvider) throws IOException {
        Path baseDir = springAiPlaygroundHomeDir.resolve("identity");
        Files.createDirectories(baseDir);
        this.filePath = baseDir.resolve(FILE);
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.deviceId = loadOrSeed(deviceIdProvider);
        logger.info("User identity ready: deviceId={}", this.deviceId);
    }

    public String deviceId() {
        return this.deviceId;
    }

    public String currentUserId() {
        return this.deviceId;
    }

    private String loadOrSeed(DeviceIdProvider deviceIdProvider) throws IOException {
        Optional<Installation> existing = load();
        if (existing.isPresent() && existing.get().deviceId() != null && !existing.get().deviceId().isBlank()) {
            return existing.get().deviceId();
        }
        String source = "machine-id";
        String id = deviceIdProvider.resolve().orElse(null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            source = "random";
        }
        write(new Installation(id, source, Instant.now().toEpochMilli()));
        logger.info("Seeded installation identity: source={}", source);
        return id;
    }

    private Optional<Installation> load() {
        if (!Files.exists(this.filePath)) return Optional.empty();
        try {
            byte[] bytes = Files.readAllBytes(this.filePath);
            if (bytes.length == 0) return Optional.empty();
            return Optional.of(this.objectMapper.readValue(bytes, Installation.class));
        } catch (IOException e) {
            logger.warn("Failed to read {} — reseeding identity", this.filePath, e);
            return Optional.empty();
        }
    }

    private void write(Installation installation) throws IOException {
        Path tmp = this.filePath.resolveSibling(FILE + ".tmp");
        this.objectMapper.writeValue(tmp.toFile(), installation);
        Files.move(tmp, this.filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
