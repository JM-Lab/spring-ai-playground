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
package jm.kr.spring.ai.playground;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jm.kr.spring.ai.playground.service.PersistenceServiceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@ConditionalOnProperty(prefix = "spring.ai.playground", name = "persistence", havingValue = "true", matchIfMissing = true)
@Component
public class SpringAiPlaygroundPersistenceManager {

    private static final Logger logger = LoggerFactory.getLogger(SpringAiPlaygroundPersistenceManager.class);

    private final PersistenceServiceInterface[] persistenceServices;

    public SpringAiPlaygroundPersistenceManager(PersistenceServiceInterface[] persistenceServices) {
        this.persistenceServices = persistenceServices;
    }

    @PostConstruct
    public void onStartup() {
        logger.info("SpringAiPlaygroundLifecycleManager started.");
        for (PersistenceServiceInterface persistenceService : persistenceServices) {
            try {
                persistenceService.onStart();
            } catch (IOException e) {
                persistenceService.getLogger().error("Failed to start persistence service.", e);
            }
        }
    }

    @PreDestroy
    public void onShutdown() {
        logger.info("SpringAiPlaygroundLifecycleManager shutting down");
        for (PersistenceServiceInterface persistenceService : persistenceServices) {
            try {
                persistenceService.onShutdown();
            } catch (IOException e) {
                persistenceService.getLogger().error("Failed to shutdown persistence service.", e);
            }
        }
    }
}