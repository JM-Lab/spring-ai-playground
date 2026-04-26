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
package org.springaicommunity.playground.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PersistenceExecutor {

    private static final Logger logger = LoggerFactory.getLogger(PersistenceExecutor.class);
    private static final Duration FLUSH_TIMEOUT = Duration.ofSeconds(10);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "persistence-save");
        t.setDaemon(false);
        return t;
    });

    public void submit(Runnable task) {
        if (this.executor.isShutdown()) {
            logger.warn("Persistence executor is shut down; skipping submitted task");
            return;
        }
        this.executor.submit(task);
    }

    public void awaitCompletion(Duration timeout) throws InterruptedException, TimeoutException {
        if (this.executor.isShutdown())
            return;
        try {
            this.executor.submit(() -> {}).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            logger.warn("Unexpected execution exception from no-op sentinel task; awaitCompletion is a no-op flush", e);
        }
    }

    public void flushAndShutdown() {
        this.executor.shutdown();
        try {
            if (!this.executor.awaitTermination(FLUSH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("Persistence executor did not flush within {}; forcing shutdown", FLUSH_TIMEOUT);
                this.executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
