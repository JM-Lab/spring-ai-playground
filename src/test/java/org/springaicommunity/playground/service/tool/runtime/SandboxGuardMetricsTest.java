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
package org.springaicommunity.playground.service.tool.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SandboxGuardMetricsTest {

    @AfterEach
    void clearRegistry() {
        SandboxGuardMetrics.setMeterRegistry(null);
    }

    @Test
    void recordsToMeterRegistryWithCategoryAndReasonTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SandboxGuardMetrics.setMeterRegistry(registry);

        SandboxGuardMetrics.recordBlock("fetch", "host-not-in-allowlist");
        SandboxGuardMetrics.recordBlock("fetch", "host-not-in-allowlist");
        SandboxGuardMetrics.recordBlock("fs", "path-escapes-base");

        Counter fetch = registry.find(SandboxGuardMetrics.COUNTER_NAME)
                .tag("category", "fetch").tag("reason", "host-not-in-allowlist").counter();
        Counter fs = registry.find(SandboxGuardMetrics.COUNTER_NAME)
                .tag("category", "fs").tag("reason", "path-escapes-base").counter();
        assertThat(fetch).isNotNull();
        assertThat(fetch.count()).isEqualTo(2.0);
        assertThat(fs).isNotNull();
        assertThat(fs.count()).isEqualTo(1.0);
    }

    @Test
    void noOpWhenMeterRegistryUnset() {
        SandboxGuardMetrics.setMeterRegistry(null);
        assertThatNoException().isThrownBy(() ->
                SandboxGuardMetrics.recordBlock("fetch", "any-reason"));
    }

    @Test
    void fetchRejectHelperRecordsBlock() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SandboxGuardMetrics.setMeterRegistry(registry);

        SafeHttpFetch.reject(JsHelperException.Kind.SECURITY,
                "literal-private-ip", "[fetch] denied: literal private/reserved IP: 10.0.0.1");

        Counter c = registry.find(SandboxGuardMetrics.COUNTER_NAME)
                .tag("category", "fetch").tag("reason", "literal-private-ip").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void fsRejectHelperRecordsBlock() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SandboxGuardMetrics.setMeterRegistry(registry);

        SafeFs.reject(JsHelperException.Kind.SECURITY,
                "path-escapes-base", "[safety.fs] denied: path escapes base");

        Counter c = registry.find(SandboxGuardMetrics.COUNTER_NAME)
                .tag("category", "fs").tag("reason", "path-escapes-base").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void nullOrBlankTagsSubstituteUnknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SandboxGuardMetrics.setMeterRegistry(registry);

        SandboxGuardMetrics.recordBlock(null, "");
        Counter c = registry.find(SandboxGuardMetrics.COUNTER_NAME)
                .tag("category", "unknown").tag("reason", "unknown").counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }
}
