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

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceIdProviderTest {

    @Test
    void deriveUuidIsDeterministicForSameMachineId() {
        assertThat(DeviceIdProvider.deriveUuid("machine-123"))
                .isEqualTo(DeviceIdProvider.deriveUuid("machine-123"));
    }

    @Test
    void deriveUuidDiffersForDifferentMachineId() {
        assertThat(DeviceIdProvider.deriveUuid("machine-A"))
                .isNotEqualTo(DeviceIdProvider.deriveUuid("machine-B"));
    }

    @Test
    void deriveUuidIsAWellFormedUuidAndSalted() {
        String derived = DeviceIdProvider.deriveUuid("machine-123");
        assertThat(UUID.fromString(derived)).hasToString(derived);
        // The salt means it is not the naive name-based UUID of the bare machine id.
        assertThat(derived).isNotEqualTo(
                UUID.nameUUIDFromBytes("machine-123".getBytes(StandardCharsets.UTF_8)).toString());
    }
}
