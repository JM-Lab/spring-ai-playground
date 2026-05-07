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
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class McpClientRegistrationRepositoryTest {

    @Test
    void registerFindUnregister() {
        McpClientRegistrationRepository repo = new McpClientRegistrationRepository();
        assertThat(repo.findByRegistrationId("mcp-streamable_http-notion")).isNull();

        ClientRegistration registration = sampleRegistration("mcp-streamable_http-notion");
        repo.register(registration);

        assertThat(repo.findByRegistrationId("mcp-streamable_http-notion")).isSameAs(registration);
        assertThat(repo.contains("mcp-streamable_http-notion")).isTrue();
        assertThat(repo.size()).isEqualTo(1);

        repo.unregister("mcp-streamable_http-notion");
        assertThat(repo.findByRegistrationId("mcp-streamable_http-notion")).isNull();
        assertThat(repo.size()).isZero();
    }

    @Test
    void reregisterReplaces() {
        McpClientRegistrationRepository repo = new McpClientRegistrationRepository();
        repo.register(sampleRegistration("mcp-sse-linear"));
        ClientRegistration replacement = ClientRegistration.withRegistrationId("mcp-sse-linear")
                .clientId("replacement-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .redirectUri("http://localhost:8282/login/oauth2/code/{registrationId}")
                .scope("read")
                .build();
        repo.register(replacement);

        assertThat(repo.findByRegistrationId("mcp-sse-linear").getClientId()).isEqualTo("replacement-client");
        assertThat(repo.size()).isEqualTo(1);
    }

    @Test
    void concurrentRegistrationsAreThreadSafe() throws Exception {
        McpClientRegistrationRepository repo = new McpClientRegistrationRepository();
        int threads = 16;
        int registrationsPerThread = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        try {
            for (int t = 0; t < threads; t++) {
                final int threadIdx = t;
                exec.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < registrationsPerThread; i++) {
                            repo.register(sampleRegistration("reg-" + threadIdx + "-" + i));
                        }
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            exec.shutdown();
        }

        long count = StreamSupport.stream(repo.spliterator(), false).count();
        assertThat(count).isEqualTo((long) threads * registrationsPerThread);
        assertThat(repo.size()).isEqualTo(threads * registrationsPerThread);
    }

    private ClientRegistration sampleRegistration(String regId) {
        return ClientRegistration.withRegistrationId(regId)
                .clientId("test-client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .redirectUri("http://localhost:8282/login/oauth2/code/{registrationId}")
                .scope("read")
                .build();
    }
}
