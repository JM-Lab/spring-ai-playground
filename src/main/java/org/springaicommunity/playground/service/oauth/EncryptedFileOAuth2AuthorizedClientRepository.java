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
package org.springaicommunity.playground.service.oauth;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class EncryptedFileOAuth2AuthorizedClientRepository
        implements OAuth2AuthorizedClientRepository, OAuth2AuthorizedClientService {

    private static final Logger logger = LoggerFactory.getLogger(EncryptedFileOAuth2AuthorizedClientRepository.class);
    private static final String FILE_SUFFIX = ".enc";
    private static final Pattern SAFE_REG_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path tokenDir;
    private final OAuthTokenEncryptor encryptor;
    private final PersistenceExecutor persistenceExecutor;
    private final ObjectMapper objectMapper;
    @Nullable private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentHashMap<String, OAuth2AuthorizedClient> cache = new ConcurrentHashMap<>();

    public EncryptedFileOAuth2AuthorizedClientRepository(Path springAiPlaygroundHomeDir,
            OAuthTokenEncryptor encryptor, PersistenceExecutor persistenceExecutor,
            @Nullable ApplicationEventPublisher eventPublisher) throws IOException {
        this.tokenDir = springAiPlaygroundHomeDir.resolve("mcp").resolve("oauth-tokens");
        Files.createDirectories(this.tokenDir);
        this.encryptor = encryptor;
        this.persistenceExecutor = persistenceExecutor;
        this.eventPublisher = eventPublisher;
        this.objectMapper = JsonMapper.builder()
                .addModules(SecurityJacksonModules.getModules(getClass().getClassLoader())).build();
        loadAllFromDisk();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
            Authentication principal, HttpServletRequest request) {
        return (T) cache.get(clientRegistrationId);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        String regId = authorizedClient.getClientRegistration().getRegistrationId();
        cache.put(regId, authorizedClient);
        persistenceExecutor.submit(() -> writeToDisk(regId, authorizedClient));
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new McpOAuthAuthorizedEvent(this, regId));
        }
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        cache.remove(clientRegistrationId);
        persistenceExecutor.submit(() -> deleteFromDisk(clientRegistrationId));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
            String principalName) {
        return (T) cache.get(clientRegistrationId);
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        saveAuthorizedClient(authorizedClient, principal, null, null);
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        removeAuthorizedClient(clientRegistrationId, null, null, null);
    }

    private Path filePath(String registrationId) {
        if (!SAFE_REG_ID.matcher(registrationId).matches()) {
            throw new IllegalArgumentException("Unsafe registration ID for filesystem: " + registrationId);
        }
        return tokenDir.resolve(registrationId + FILE_SUFFIX);
    }

    private void writeToDisk(String registrationId, OAuth2AuthorizedClient client) {
        try {
            String json = objectMapper.writeValueAsString(client);
            String encrypted = encryptor.encrypt(json);
            Path target = filePath(registrationId);
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, encrypted, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            logger.error("Failed to write OAuth client to disk: regId={}", registrationId, e);
        }
    }

    private void deleteFromDisk(String registrationId) {
        try {
            Files.deleteIfExists(filePath(registrationId));
        } catch (IOException e) {
            logger.error("Failed to delete OAuth client from disk: regId={}", registrationId, e);
        }
    }

    private void loadAllFromDisk() {
        try (Stream<Path> stream = Files.list(tokenDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(FILE_SUFFIX)).forEach(this::loadOne);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void loadOne(Path path) {
        String name = path.getFileName().toString();
        String regId = name.substring(0, name.length() - FILE_SUFFIX.length());
        try {
            String encrypted = Files.readString(path, StandardCharsets.UTF_8);
            String json = encryptor.decrypt(encrypted);
            OAuth2AuthorizedClient client = objectMapper.readValue(json, OAuth2AuthorizedClient.class);
            cache.put(regId, client);
        } catch (Exception e) {
            logger.warn("Failed to load OAuth client from {} — will require re-authorization", path, e);
        }
    }
}
