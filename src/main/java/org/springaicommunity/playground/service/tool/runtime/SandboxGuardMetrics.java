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

import io.micrometer.core.instrument.MeterRegistry;

public final class SandboxGuardMetrics {

    public static final String COUNTER_NAME = "sandbox.guard.blocked";

    private static volatile MeterRegistry meterRegistry;

    private SandboxGuardMetrics() {}

    public static void setMeterRegistry(MeterRegistry registry) {
        meterRegistry = registry;
    }

    public static void recordBlock(String category, String reason) {
        MeterRegistry r = meterRegistry;
        if (r == null) return;
        r.counter(COUNTER_NAME,
                "category", safeTag(category),
                "reason", safeTag(reason)).increment();
    }

    private static String safeTag(String value) {
        return value == null || value.isEmpty() ? "unknown" : value;
    }
}
