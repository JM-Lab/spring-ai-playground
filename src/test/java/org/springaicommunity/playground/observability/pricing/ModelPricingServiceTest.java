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
package org.springaicommunity.playground.observability.pricing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModelPricingServiceTest {

    @TempDir Path tempHome;

    private PersistenceExecutor executor;
    private ModelPricingService service;

    @BeforeEach
    void setUp() {
        executor = new PersistenceExecutor();
        service = new ModelPricingService(tempHome, executor);
        service.init();
    }

    @AfterEach
    void tearDown() {
        executor.flushAndShutdown();
    }

    @Test
    void costReturnsZeroWhenModelNotPriced() {
        BigDecimal cost = service.cost("unknown-model", 1000, 1000);
        assertThat(cost.doubleValue()).isEqualTo(0.0);
    }

    @Test
    void costCalculatesPerMillionTokensCorrectly() {
        // gpt-5.4-mini: $0.15 / 1M input, $0.60 / 1M output
        service.upsert(new ModelPricing("gpt-5.4-mini",
                new BigDecimal("0.15"), new BigDecimal("0.60")));
        // 1000 in + 2000 out -> (1000 * 0.15 + 2000 * 0.60) / 1_000_000 = (150 + 1200) / 1M = 0.00135
        BigDecimal cost = service.cost("gpt-5.4-mini", 1000L, 2000L);
        assertThat(cost.doubleValue()).isCloseTo(0.00135, within(0.000001));
    }

    @Test
    void costHandlesOnlyInputOrOnlyOutputPricing() {
        service.upsert(new ModelPricing("input-only", new BigDecimal("1.0"), null));
        BigDecimal cost = service.cost("input-only", 1_000_000L, 0L);
        assertThat(cost.doubleValue()).isCloseTo(1.0, within(0.000001));
    }

    @Test
    void upsertThenFindReturnsValue() {
        ModelPricing p = new ModelPricing("m", new BigDecimal("0.5"), new BigDecimal("1.0"));
        service.upsert(p);
        assertThat(service.find("m")).contains(p);
    }

    @Test
    void deleteRemovesPricing() {
        service.upsert(new ModelPricing("temp", new BigDecimal("0.1"), new BigDecimal("0.2")));
        service.delete("temp");
        assertThat(service.find("temp")).isEmpty();
    }

    @Test
    void findWithNullModelReturnsEmptyOptional() {
        assertThat(service.find(null)).isEmpty();
    }

    @Test
    void corruptedPricingJsonDoesNotCrashInit() throws Exception {
        Path pricingFile = tempHome.resolve("observability").resolve("pricing.json");
        Files.createDirectories(pricingFile.getParent());
        Files.writeString(pricingFile, "{ this is not valid JSON ]");

        // a fresh service over the same dir must init() without throwing
        PersistenceExecutor exec2 = new PersistenceExecutor();
        try {
            ModelPricingService corrupted = new ModelPricingService(tempHome, exec2);
            corrupted.init();
            assertThat(corrupted.all()).isEmpty();
        } finally {
            exec2.flushAndShutdown();
        }
    }

    @Test
    void persistsToDiskAndReloadsOnInit() throws Exception {
        service.upsert(new ModelPricing("persisted",
                new BigDecimal("0.25"), new BigDecimal("1.5")));
        // wait for async write
        executor.awaitCompletion(Duration.ofSeconds(5));

        // new service instance, same dir
        PersistenceExecutor exec2 = new PersistenceExecutor();
        try {
            ModelPricingService reloaded = new ModelPricingService(tempHome, exec2);
            reloaded.init();
            assertThat(reloaded.find("persisted")).isPresent()
                    .get()
                    .extracting(ModelPricing::inputPerMillionTokens, ModelPricing::outputPerMillionTokens)
                    .containsExactly(new BigDecimal("0.25"), new BigDecimal("1.5"));
        } finally {
            exec2.flushAndShutdown();
        }
    }
}
