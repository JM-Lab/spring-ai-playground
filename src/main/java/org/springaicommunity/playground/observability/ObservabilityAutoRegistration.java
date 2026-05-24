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
package org.springaicommunity.playground.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.runtime.SandboxGuardMetrics;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ObservabilityAutoRegistration {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityAutoRegistration.class);

    private final ApplicationContext applicationContext;
    private final ObservabilityCollector collector;
    private final MeterRegistry meterRegistry;

    public ObservabilityAutoRegistration(ApplicationContext applicationContext,
            ObservabilityCollector collector, MeterRegistry meterRegistry) {
        this.applicationContext = applicationContext;
        this.collector = collector;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void register() {
        Map<String, ObservationRegistry> registries =
                applicationContext.getBeansOfType(ObservationRegistry.class);
        if (registries.isEmpty()) {
            logger.warn("No ObservationRegistry bean found — ObservabilityCollector will not receive events");
            return;
        }
        registries.forEach((name, registry) -> {
            registry.observationConfig().observationHandler(collector);
            logger.info("ObservabilityCollector registered on ObservationRegistry '{}'", name);
        });
        // Wire static sandbox helpers to the shared registry.
        SandboxGuardMetrics.setMeterRegistry(meterRegistry);
    }
}
