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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthClientRegistrationsTest {

    @Test
    void registrationIdFollowsTransportAndServerName() {
        McpServerInfo server = sampleServer("notion");
        assertThat(OAuthClientRegistrations.registrationId(server)).isEqualTo("mcp-streamable_http-notion");
    }

    @Test
    void nullOAuthYieldsNullRegistration() {
        ClientRegistration registration = OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), null);
        assertThat(registration).isNull();
    }

    @Test
    void missingClientIdYieldsNullRegistration() {
        HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                null, "https://example.com/auth", "https://example.com/token", null, null, List.of("read"), null);
        assertThat(OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), oauth)).isNull();
    }

    @Test
    void missingBothIssuerAndAuthorizationUriYieldsNullRegistration() {
        HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                null, null, null, "client-id", null, List.of("read"), null);
        assertThat(OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), oauth)).isNull();
    }

    @Test
    void explicitAuthorizationUriPathProducesPkceRegistrationWhenSecretIsBlank() {
        HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                null,
                "https://example.com/oauth/authorize",
                "https://example.com/oauth/token",
                "test-client",
                null,
                List.of("read", "write"),
                null);

        ClientRegistration registration =
                OAuthClientRegistrations.toClientRegistration(sampleServer("notion"), oauth);

        assertThat(registration).isNotNull();
        assertThat(registration.getRegistrationId()).isEqualTo("mcp-streamable_http-notion");
        assertThat(registration.getClientId()).isEqualTo("test-client");
        assertThat(registration.getClientSecret()).isNullOrEmpty();
        assertThat(registration.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(registration.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(registration.getRedirectUri())
                .isEqualTo(OAuthClientRegistrations.REDIRECT_URI_TEMPLATE);
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("read", "write");
    }

    @Test
    void clientSecretSwitchesToBasicAuthByDefault() {
        HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                null,
                "https://example.com/oauth/authorize",
                "https://example.com/oauth/token",
                "test-client",
                "the-secret",
                List.of("read"),
                null);

        ClientRegistration registration =
                OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), oauth);

        assertThat(registration).isNotNull();
        assertThat(registration.getClientSecret()).isEqualTo("the-secret");
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }

    @Test
    void explicitClientAuthMethodOverridesSecretHeuristic() {
        HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                null,
                "https://example.com/oauth/authorize",
                "https://example.com/oauth/token",
                "test-client",
                "the-secret",
                List.of("read"),
                "client_secret_post");

        ClientRegistration registration =
                OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), oauth);

        assertThat(registration).isNotNull();
        assertThat(registration.getClientAuthenticationMethod())
                .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Test
    void envVarReferencesAreResolvedWhenSetAndThrowsOtherwise() {
        try {
            System.setProperty("OAUTH_TEST_CLIENT_ID", "resolved-client");
            HttpConnectionParametersWithExtras.OAuth oauth = new HttpConnectionParametersWithExtras.OAuth(
                    null,
                    "https://example.com/oauth/authorize",
                    "https://example.com/oauth/token",
                    "${OAUTH_TEST_CLIENT_ID}",
                    null,
                    List.of("read"),
                    null);

            ClientRegistration registration =
                    OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), oauth);

            assertThat(registration).isNotNull();
            assertThat(registration.getClientId()).isEqualTo("resolved-client");
        } finally {
            System.clearProperty("OAUTH_TEST_CLIENT_ID");
        }

        HttpConnectionParametersWithExtras.OAuth missing = new HttpConnectionParametersWithExtras.OAuth(
                null,
                "https://example.com/oauth/authorize",
                "https://example.com/oauth/token",
                "${OAUTH_TEST_CLIENT_ID_DEFINITELY_UNSET}",
                null,
                List.of("read"),
                null);

        assertThatThrownBy(() -> OAuthClientRegistrations.toClientRegistration(sampleServer("foo"), missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing env var");
    }

    private McpServerInfo sampleServer(String name) {
        return new McpServerInfo(McpTransportType.STREAMABLE_HTTP, name, "test", 0L, 0L, "{}");
    }
}
