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
package org.springaicommunity.playground.observability.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.chat.OllamaMonitorService;
import org.springaicommunity.playground.service.chat.OllamaMonitorService.OllamaStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OllamaMetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(OllamaMetricsCollector.class);

    private final OllamaMonitorService monitor;
    private final OllamaMetricsRingBuffer buffer;

    public OllamaMetricsCollector(OllamaMonitorService monitor, OllamaMetricsRingBuffer buffer) {
        this.monitor = monitor;
        this.buffer = buffer;
    }

    @Scheduled(fixedDelayString = "${spring.ai.playground.observability.ollama-metrics-sample-ms:5000}",
            initialDelayString = "${spring.ai.playground.observability.ollama-metrics-initial-delay-ms:5000}")
    public void sample() {
        try {
            OllamaStatus status = monitor.snapshot();
            if (!status.active()) return;
            buffer.record(status.reachable(), status.runningModels().size(),
                    status.totalVramBytes(), status.installedModels().size());
        } catch (RuntimeException e) {
            logger.warn("Ollama metrics capture failed: {}", e.getMessage());
        }
    }
}
