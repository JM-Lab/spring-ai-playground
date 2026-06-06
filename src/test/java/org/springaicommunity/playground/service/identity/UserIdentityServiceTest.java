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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityServiceTest {

    private static DeviceIdProvider providerReturning(String value) {
        return new DeviceIdProvider() {
            @Override
            public Optional<String> resolve() {
                return Optional.ofNullable(value);
            }
        };
    }

    @Test
    void seedsFromDeviceProviderAndPersists(@TempDir Path home) throws IOException {
        UserIdentityService service = new UserIdentityService(home, new ObjectMapper(),
                providerReturning("11111111-1111-1111-1111-111111111111"));

        assertThat(service.deviceId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(service.currentUserId()).isEqualTo(service.deviceId());
        assertThat(home.resolve("identity").resolve("installation.json")).exists();
    }

    @Test
    void persistedDeviceIdWinsAcrossRestart(@TempDir Path home) throws IOException {
        String first = new UserIdentityService(home, new ObjectMapper(), providerReturning("seed-1")).deviceId();
        // Second boot: even though the provider would now yield a different value, the persisted id is kept.
        String second = new UserIdentityService(home, new ObjectMapper(),
                providerReturning("seed-2-different")).deviceId();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void fallsBackToRandomWhenMachineIdUnavailableAndStaysStable(@TempDir Path home) throws IOException {
        String first = new UserIdentityService(home, new ObjectMapper(), providerReturning(null)).deviceId();
        assertThat(first).isNotBlank();

        String reloaded = new UserIdentityService(home, new ObjectMapper(), providerReturning(null)).deviceId();
        assertThat(reloaded).isEqualTo(first);
    }
}
