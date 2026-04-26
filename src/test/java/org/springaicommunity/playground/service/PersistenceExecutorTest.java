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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceExecutorTest {

    private PersistenceExecutor executor;

    @BeforeEach
    void setUp() {
        this.executor = new PersistenceExecutor();
    }

    @AfterEach
    void tearDown() {
        this.executor.flushAndShutdown();
    }

    @Test
    void submitExecutesTask() throws InterruptedException, TimeoutException {
        AtomicInteger counter = new AtomicInteger(0);
        this.executor.submit(counter::incrementAndGet);
        this.executor.awaitCompletion(Duration.ofSeconds(1));
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void submitPreservesFifoOrder() throws InterruptedException, TimeoutException {
        StringBuilder order = new StringBuilder();
        for (char c : "ABCDE".toCharArray()) {
            char ch = c;
            this.executor.submit(() -> order.append(ch));
        }
        this.executor.awaitCompletion(Duration.ofSeconds(1));
        assertThat(order).hasToString("ABCDE");
    }

    @Test
    void flushAndShutdownWaitsForQueuedTasks() {
        AtomicInteger counter = new AtomicInteger(0);
        for (int i = 0; i < 10; i++)
            this.executor.submit(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                counter.incrementAndGet();
            });

        this.executor.flushAndShutdown();

        assertThat(counter.get()).isEqualTo(10);
    }

    @Test
    void submitAfterShutdownIsSkipped() throws InterruptedException {
        this.executor.flushAndShutdown();

        AtomicInteger counter = new AtomicInteger(0);
        this.executor.submit(counter::incrementAndGet);

        Thread.sleep(100);
        assertThat(counter.get()).isEqualTo(0);
    }

    @Test
    void awaitCompletionTimesOutOnSlowTasks() {
        CountDownLatch running = new CountDownLatch(1);
        this.executor.submit(() -> {
            running.countDown();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            running.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThatThrownBy(() -> this.executor.awaitCompletion(Duration.ofMillis(50)))
                .isInstanceOf(TimeoutException.class);
    }
}
