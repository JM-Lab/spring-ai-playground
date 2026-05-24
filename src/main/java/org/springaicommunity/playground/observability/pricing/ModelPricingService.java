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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModelPricingService {

    private static final Logger logger = LoggerFactory.getLogger(ModelPricingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000L);
    private static final TypeReference<List<ModelPricing>> LIST_TYPE = new TypeReference<>() {};

    private final Path file;
    private final PersistenceExecutor executor;
    private final Map<String, ModelPricing> pricing = new ConcurrentHashMap<>();

    public ModelPricingService(Path springAiPlaygroundHomeDir, PersistenceExecutor executor) {
        this.executor = executor;
        this.file = springAiPlaygroundHomeDir.resolve("observability").resolve("pricing.json");
    }

    @PostConstruct
    public void init() {
        try {
            if (Files.exists(file)) {
                List<ModelPricing> list = MAPPER.readValue(file.toFile(), LIST_TYPE);
                list.forEach(p -> pricing.put(p.model(), p));
                logger.info("Loaded {} model pricing entries from {}", list.size(), file);
            } else {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            logger.warn("Failed to load pricing from {}", file, e);
        }
    }

    public List<ModelPricing> all() {
        return new ArrayList<>(pricing.values());
    }

    public Optional<ModelPricing> find(String model) {
        if (model == null) return Optional.empty();
        return Optional.ofNullable(pricing.get(model));
    }

    public void upsert(ModelPricing entry) {
        if (entry == null || entry.model() == null) return;
        pricing.put(entry.model(), entry);
        saveAsync();
    }

    public void delete(String model) {
        if (model == null) return;
        pricing.remove(model);
        saveAsync();
    }

    public BigDecimal cost(String model, long inputTokens, long outputTokens) {
        ModelPricing p = pricing.get(model);
        if (p == null) return BigDecimal.ZERO;
        BigDecimal inCost = nz(p.inputPerMillionTokens()).multiply(BigDecimal.valueOf(inputTokens))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        BigDecimal outCost = nz(p.outputPerMillionTokens()).multiply(BigDecimal.valueOf(outputTokens))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        return inCost.add(outCost);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private void saveAsync() {
        executor.submit(() -> {
            try {
                Files.createDirectories(file.getParent());
                MAPPER.writeValue(file.toFile(), new ArrayList<>(pricing.values()));
            } catch (IOException e) {
                logger.error("Failed to save pricing to {}", file, e);
            }
        });
    }
}
