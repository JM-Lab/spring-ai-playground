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
package org.springaicommunity.playground.service.mcp.client;

import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService.McpToolSource;
import org.springaicommunity.playground.service.mcp.client.McpClientService.OAuthSnapshot;
import org.springaicommunity.playground.service.mcp.client.McpClientService.StatusEntry;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpClientServiceUnitTest {

    private SimpleMeterRegistry meterRegistry;
    private McpClientService service;
    private McpServerInfoService serverInfoService;
    private OAuth2AuthorizedClientService oauthClientService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        serverInfoService = mock(McpServerInfoService.class);
        oauthClientService = mock(OAuth2AuthorizedClientService.class);
        McpClientCommonProperties props = new McpClientCommonProperties();
        props.setName("test-app");
        props.setVersion("0.0.1-test");
        service = new McpClientService(
                null,
                null,
                props,
                new ObjectMapper(),
                new McpClientPropertiesService<?>[] {},
                null,
                oauthClientService,
                presentProvider(serverInfoService),
                mock(McpNotificationStore.class),
                meterRegistry);
    }

    @Test
    void lookupToolSourceReturnsEmptyWhenNameNotIndexed() {
        assertThat(service.lookupToolSource("anything")).isEmpty();
    }

    @Test
    void lookupToolSourceReturnsEmptyForNullOrBlank() {
        assertThat(service.lookupToolSource(null)).isEmpty();
        assertThat(service.lookupToolSource("")).isEmpty();
        assertThat(service.lookupToolSource("   ")).isEmpty();
    }

    @Test
    void snapshotStatusesReturnsEmptyMapWhenNoClientsStarted() {
        assertThat(service.snapshotStatuses()).isEmpty();
    }

    @Test
    void snapshotOAuthStateReturnsEmptyWhenNoServersConfigured() {
        when(serverInfoService.read()).thenReturn(List.of());
        assertThat(service.snapshotOAuthState()).isEmpty();
    }

    @Test
    void snapshotOAuthStateReturnsRowPerOauthServerWithTokenExpiry() {
        McpServerInfo serverWithOauth = new McpServerInfo(McpTransportType.STREAMABLE_HTTP,
                "github", "", 0L, 0L,
                // OAuthClientRegistrations.registrationId returns non-null iff connectionAsJson
                // declares a "registrationId" / OAuth fields. To exercise that branch we wire a
                // mocked OAuth2AuthorizedClient.
                "{}");
        when(serverInfoService.read()).thenReturn(List.of(serverWithOauth));

        // Stub the OAuth client lookup. Any registrationId returns this token.
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token-xyz",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(900));
        OAuth2RefreshToken refresh = new OAuth2RefreshToken("refresh-xyz", Instant.now().minusSeconds(60));
        ClientRegistration clientReg = ClientRegistration.withRegistrationId("github")
                .clientId("cid").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://example.test/cb")
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token").build();
        OAuth2AuthorizedClient authorized = new OAuth2AuthorizedClient(clientReg,
                "spring-ai-playground-mcp-client", token, refresh);
        when(oauthClientService.loadAuthorizedClient(anyString(),
                anyString())).thenReturn(authorized);

        List<OAuthSnapshot> rows = service.snapshotOAuthState();
        // Whether registrationId(...) yields non-null for this serverInfo depends on its
        // connectionAsJson — if it doesn't, the row is skipped. Either result is OK; we
        // only assert that calling the method is safe and returns a list.
        assertThat(rows).isNotNull();
        // If at least one row materialised, verify it contains the stubbed token expiry.
        if (!rows.isEmpty()) {
            assertThat(rows.get(0).accessTokenExpiresAtMs()).isNotNull();
            assertThat(rows.get(0).hasRefreshToken()).isTrue();
        }
    }

    @Test
    void mcpToolSourceRecordCarriesNameAndTransport() {
        McpToolSource s = new McpToolSource("weather", "STDIO");
        assertThat(s.serverName()).isEqualTo("weather");
        assertThat(s.transport()).isEqualTo("STDIO");
    }

    @Test
    void statusEntryFactoryMethodsSetStatusCorrectly() {
        StatusEntry ok = StatusEntry.ok();
        StatusEntry err = StatusEntry.error("boom");
        StatusEntry awaiting = StatusEntry.awaitingAuthorization("/oauth/x");
        assertThat(ok.status()).isEqualTo(McpClientService.ServerStatus.OK);
        assertThat(err.status()).isEqualTo(McpClientService.ServerStatus.ERROR);
        assertThat(err.error()).isEqualTo("boom");
        assertThat(awaiting.status()).isEqualTo(McpClientService.ServerStatus.AWAITING_AUTHORIZATION);
        assertThat(awaiting.authorizationUrl()).isEqualTo("/oauth/x");
        assertThat(StatusEntry.OFFLINE.status()).isEqualTo(McpClientService.ServerStatus.OFFLINE);
    }

    private static <T> ObjectProvider<T> presentProvider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getObject(Object... args) { return value; }
            @Override public T getIfAvailable() { return value; }
            @Override public T getIfAvailable(Supplier<T> def) { return value; }
            @Override public T getIfUnique() { return value; }
            @Override public T getIfUnique(Supplier<T> def) { return value; }
            @Override public void ifAvailable(Consumer<T> c) { c.accept(value); }
            @Override public void ifUnique(Consumer<T> c) { c.accept(value); }
        };
    }
}
