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
package org.springaicommunity.playground.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain mcpClientSecurityFilterChain(HttpSecurity http) throws Exception {
        // Single-user desktop app with no authentication.
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/mcp", "/mcp/**", "/sse", "/sse/**", "/actuator/**",
                        "/oauth2/authorization/**", "/login/oauth2/code/**",
                        "/ace-builds/**")
                .permitAll()
                .anyRequest().permitAll());

        http.csrf(csrf -> csrf.disable());

        http.oauth2Client(Customizer.withDefaults());

        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.enableCsrfConfiguration(false);
            configurer.enableAuthorizedRequestsConfiguration(false);
        });

        return http.build();
    }

    @Bean
    OAuth2AuthorizedClientManager mcpAuthorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository,
                        authorizedClientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .build());
        return manager;
    }
}
