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
package org.springaicommunity.playground.observability.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SystemMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(SystemMetricsCollector.class);

    private final SystemMetricsSnapshot snapshotService;
    private final SystemMetricsRingBuffer buffer;

    public SystemMetricsCollector(SystemMetricsSnapshot snapshotService,
            SystemMetricsRingBuffer buffer) {
        this.snapshotService = snapshotService;
        this.buffer = buffer;
    }

    @Scheduled(fixedDelayString = "${spring.ai.playground.observability.system-metrics-sample-ms:5000}",
            initialDelayString = "${spring.ai.playground.observability.system-metrics-initial-delay-ms:5000}")
    public void sample() {
        try {
            buffer.record(snapshotService.capture());
        } catch (RuntimeException e) {
            logger.warn("System metrics capture failed: {}", e.getMessage());
        }
    }
}
