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

import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.HttpConnectionParametersWithExtras;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.util.EnvVarResolver;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

import java.util.Set;

public final class OAuthClientRegistrations {

    public static final String REDIRECT_URI_TEMPLATE = "{baseUrl}/login/oauth2/code/{registrationId}";

    private OAuthClientRegistrations() {}

    public static String registrationId(McpServerInfo server) {
        return registrationId(server.mcpTransportType(), server.serverName());
    }

    public static String registrationId(McpTransportType transport, String serverName) {
        return "mcp-" + transport.name().toLowerCase() + "-" + serverName;
    }

    public static ClientRegistration toClientRegistration(McpServerInfo server,
            HttpConnectionParametersWithExtras.OAuth oauth) {
        if (oauth == null) return null;
        String clientId = resolve(oauth.clientId());
        if (!StringUtils.hasText(clientId)) return null;

        String regId = registrationId(server);
        String resolvedSecret = resolve(oauth.clientSecret());

        ClientAuthenticationMethod authMethod = resolveAuthMethod(oauth.clientAuthenticationMethod(), resolvedSecret);

        ClientRegistration.Builder builder;
        if (StringUtils.hasText(oauth.issuerUri())) {
            builder = ClientRegistrations.fromIssuerLocation(oauth.issuerUri()).registrationId(regId);
        } else if (StringUtils.hasText(oauth.authorizationUri()) && StringUtils.hasText(oauth.tokenUri())) {
            builder = ClientRegistration.withRegistrationId(regId)
                    .authorizationUri(oauth.authorizationUri())
                    .tokenUri(oauth.tokenUri());
        } else {
            return null;
        }

        builder.clientId(clientId)
                .clientSecret(StringUtils.hasText(resolvedSecret) ? resolvedSecret : null)
                .clientAuthenticationMethod(authMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI_TEMPLATE);

        if (!oauth.scopes().isEmpty()) builder.scope(oauth.scopes());

        return builder.build();
    }

    private static ClientAuthenticationMethod resolveAuthMethod(String declared, String resolvedSecret) {
        if (StringUtils.hasText(declared)) return new ClientAuthenticationMethod(declared.toLowerCase());
        if (StringUtils.hasText(resolvedSecret)) return ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        return ClientAuthenticationMethod.NONE; // PKCE-only
    }

    private static String resolve(String raw) {
        if (raw == null) return null;
        Set<String> missing = EnvVarResolver.missing(EnvVarResolver.findRefs(raw));
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing env var(s) for OAuth value: " + missing);
        }
        return EnvVarResolver.substitute(raw);
    }
}
