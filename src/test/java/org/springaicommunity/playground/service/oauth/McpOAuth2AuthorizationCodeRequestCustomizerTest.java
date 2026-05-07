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

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpOAuth2AuthorizationCodeRequestCustomizerTest {

    @Test
    void addsBearerHeaderWhenManagerReturnsAuthorizedClient() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        OAuth2AuthorizedClient client = sampleAuthorizedClient("notion-token");
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(client);

        McpOAuth2AuthorizationCodeRequestCustomizer customizer =
                new McpOAuth2AuthorizationCodeRequestCustomizer(manager, "mcp-streamable_http-notion");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.notion.com/mcp"));
        customizer.customize(builder, "POST", URI.create("https://api.notion.com/mcp"), "{}",
                McpTransportContext.EMPTY);

        HttpRequest built = builder.build();
        assertThat(built.headers().firstValue("Authorization")).contains("Bearer notion-token");
    }

    @Test
    void throwsClientAuthorizationRequiredWhenManagerReturnsNull() {
        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(null);

        McpOAuth2AuthorizationCodeRequestCustomizer customizer =
                new McpOAuth2AuthorizationCodeRequestCustomizer(manager, "mcp-streamable_http-linear");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://api.linear.app/mcp"));
        assertThatThrownBy(() -> customizer.customize(builder, "POST", URI.create("https://x"), "{}",
                McpTransportContext.EMPTY))
                .isInstanceOf(ClientAuthorizationRequiredException.class);
    }

    @Test
    void usesAnonymousSystemPrincipalEvenWithEmptySecurityContext() {
        // Verifies the customizer works in background threads (no HTTP request, no SecurityContextHolder population).
        SecurityContextHolder.clearContext();

        OAuth2AuthorizedClientManager manager = mock(OAuth2AuthorizedClientManager.class);
        when(manager.authorize(any(OAuth2AuthorizeRequest.class))).thenReturn(sampleAuthorizedClient("token-x"));

        McpOAuth2AuthorizationCodeRequestCustomizer customizer =
                new McpOAuth2AuthorizationCodeRequestCustomizer(manager, "reg-x");

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://x"));
        customizer.customize(builder, "GET", URI.create("https://x"), null, McpTransportContext.EMPTY);

        ArgumentCaptor<OAuth2AuthorizeRequest> captor = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
        verify(manager).authorize(captor.capture());
        OAuth2AuthorizeRequest captured = captor.getValue();
        assertThat(captured.getClientRegistrationId()).isEqualTo("reg-x");
        assertThat(captured.getPrincipal()).isInstanceOf(AnonymousAuthenticationToken.class);
        assertThat(captured.getPrincipal().getName()).isEqualTo("spring-ai-playground-mcp-client");
    }

    private OAuth2AuthorizedClient sampleAuthorizedClient(String tokenValue) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("reg")
                .clientId("c")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://example.com/auth")
                .tokenUri("https://example.com/token")
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue,
                Instant.now(), Instant.now().plus(Duration.ofHours(1)), Set.of("read"));
        return new OAuth2AuthorizedClient(registration, "anonymousUser", accessToken);
    }
}
