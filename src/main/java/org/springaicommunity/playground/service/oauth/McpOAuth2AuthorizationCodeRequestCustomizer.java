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

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

import java.net.URI;
import java.net.http.HttpRequest;

public class McpOAuth2AuthorizationCodeRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    private static final Authentication SYSTEM_PRINCIPAL = new AnonymousAuthenticationToken(
            "spring-ai-playground-mcp-client",
            "spring-ai-playground-mcp-client",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public McpOAuth2AuthorizationCodeRequestCustomizer(OAuth2AuthorizedClientManager authorizedClientManager,
            String registrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    @Override
    public void customize(HttpRequest.Builder builder, String method, URI uri, String body,
            McpTransportContext context) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(SYSTEM_PRINCIPAL)
                .build();
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new ClientAuthorizationRequiredException(registrationId);
        }
        builder.header("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue());
    }
}
