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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptedFileOAuth2AuthorizedClientRepositoryTest {

    private final PersistenceExecutor persistenceExecutor = new PersistenceExecutor();

    @AfterEach
    void tearDown() {
        persistenceExecutor.flushAndShutdown();
    }

    @Test
    void saveLoadRemoveRoundTrip(@TempDir Path tempDir) throws Exception {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        EncryptedFileOAuth2AuthorizedClientRepository repo =
                new EncryptedFileOAuth2AuthorizedClientRepository(tempDir, encryptor, persistenceExecutor, null);

        OAuth2AuthorizedClient client = sampleClient("mcp-streamable_http-notion");
        repo.saveAuthorizedClient(client, anonymous(), null, null);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(5));

        OAuth2AuthorizedClient loaded =
                repo.loadAuthorizedClient(client.getClientRegistration().getRegistrationId(), anonymous(), null);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("access-token-value");
        assertThat(loaded.getRefreshToken()).isNotNull();
        assertThat(loaded.getRefreshToken().getTokenValue()).isEqualTo("refresh-token-value");

        Path tokenFile = tempDir.resolve("mcp").resolve("oauth-tokens").resolve(
                client.getClientRegistration().getRegistrationId() + ".enc");
        assertThat(Files.exists(tokenFile)).isTrue();

        repo.removeAuthorizedClient(client.getClientRegistration().getRegistrationId(), anonymous(), null, null);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(5));

        OAuth2AuthorizedClient afterRemoval =
                repo.loadAuthorizedClient(client.getClientRegistration().getRegistrationId(), anonymous(), null);
        assertThat(afterRemoval).isNull();
        assertThat(Files.exists(tokenFile)).isFalse();
    }

    @Test
    void persistsAcrossInstances(@TempDir Path tempDir) throws Exception {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        EncryptedFileOAuth2AuthorizedClientRepository firstRepo =
                new EncryptedFileOAuth2AuthorizedClientRepository(tempDir, encryptor, persistenceExecutor, null);

        OAuth2AuthorizedClient client = sampleClient("mcp-streamable_http-linear");
        firstRepo.saveAuthorizedClient(client, anonymous(), null, null);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(5));

        EncryptedFileOAuth2AuthorizedClientRepository secondRepo =
                new EncryptedFileOAuth2AuthorizedClientRepository(tempDir, encryptor, persistenceExecutor, null);
        OAuth2AuthorizedClient loaded =
                secondRepo.loadAuthorizedClient(client.getClientRegistration().getRegistrationId(), anonymous(), null);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getClientRegistration().getRegistrationId())
                .isEqualTo(client.getClientRegistration().getRegistrationId());
        assertThat(loaded.getClientRegistration().getClientId()).isEqualTo("test-client");
        assertThat(loaded.getAccessToken().getTokenValue()).isEqualTo("access-token-value");
        assertThat(loaded.getAccessToken().getScopes()).containsExactlyInAnyOrder("read");
    }

    @Test
    void corruptFileIsSkippedAndLogged(@TempDir Path tempDir) throws Exception {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        Path tokenDir = tempDir.resolve("mcp").resolve("oauth-tokens");
        Files.createDirectories(tokenDir);
        Files.writeString(tokenDir.resolve("mcp-streamable_http-bogus.enc"), "this-is-not-encrypted-json");

        EncryptedFileOAuth2AuthorizedClientRepository repo =
                new EncryptedFileOAuth2AuthorizedClientRepository(tempDir, encryptor, persistenceExecutor, null);

        OAuth2AuthorizedClient corruptLoad = repo.loadAuthorizedClient("mcp-streamable_http-bogus", anonymous(), null);
        assertThat(corruptLoad).isNull();
    }

    @Test
    void unsafeRegistrationIdIsRejected(@TempDir Path tempDir) throws Exception {
        OAuthTokenEncryptor encryptor = new OAuthTokenEncryptor(tempDir);
        EncryptedFileOAuth2AuthorizedClientRepository repo =
                new EncryptedFileOAuth2AuthorizedClientRepository(tempDir, encryptor, persistenceExecutor, null);

        OAuth2AuthorizedClient unsafe = sampleClient("../escape");
        repo.saveAuthorizedClient(unsafe, anonymous(), null, null);
        persistenceExecutor.awaitCompletion(Duration.ofSeconds(5));

        Path escapeAttempt = tempDir.resolve("escape.enc");
        assertThat(Files.exists(escapeAttempt)).isFalse();
    }

    private OAuth2AuthorizedClient sampleClient(String regId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(regId)
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .redirectUri("http://localhost:8282/login/oauth2/code/{registrationId}")
                .scope("read")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token-value",
                Instant.now(),
                Instant.now().plus(Duration.ofHours(1)),
                Set.of("read"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-token-value", Instant.now());
        return new OAuth2AuthorizedClient(registration, "anonymousUser", accessToken, refreshToken);
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }
}
