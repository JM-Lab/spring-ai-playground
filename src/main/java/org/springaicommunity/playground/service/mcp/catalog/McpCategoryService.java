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
package org.springaicommunity.playground.service.mcp.catalog;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class McpCategoryService {

    private static final Logger logger = LoggerFactory.getLogger(McpCategoryService.class);
    private static final String DEFAULT_RESOURCE = "mcp/default-mcp-categories.json";

    private final Map<String, CategoryDef> builtInById;
    private final List<CategoryDef> builtInOrdered;

    public McpCategoryService(ObjectMapper objectMapper) {
        this.builtInById = loadBuiltIn(objectMapper);
        this.builtInOrdered = this.builtInById.values().stream()
                .sorted(Comparator.comparingInt(CategoryDef::order).thenComparing(CategoryDef::id))
                .toList();
        logger.info("Loaded {} built-in MCP categories", this.builtInOrdered.size());
    }

    private Map<String, CategoryDef> loadBuiltIn(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource(DEFAULT_RESOURCE);
        if (!resource.exists()) {
            logger.warn("Built-in MCP categories resource missing at {}", DEFAULT_RESOURCE);
            return Map.of();
        }
        try (InputStream in = resource.getInputStream()) {
            List<CategoryDef> raw = objectMapper.readValue(in, new TypeReference<List<CategoryDef>>() {});
            Map<String, CategoryDef> byId = new LinkedHashMap<>();
            for (CategoryDef d : raw) {
                byId.put(d.id(), new CategoryDef(d.id(), d.displayName(), d.order(), d.icon(), d.description(), true));
            }
            return Collections.unmodifiableMap(byId);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load built-in MCP categories from " + DEFAULT_RESOURCE, e);
        }
    }

    public List<CategoryDef> getBuiltInCategories() {
        return this.builtInOrdered;
    }

    public Optional<CategoryDef> findBuiltIn(String id) {
        return Optional.ofNullable(this.builtInById.get(id));
    }

    public List<CategoryDef> getAllCategories(Collection<String> observedIds) {
        List<CategoryDef> result = new ArrayList<>(this.builtInOrdered);
        if (observedIds == null || observedIds.isEmpty()) return result;
        List<String> userOnly = observedIds.stream()
                .filter(id -> id != null && !id.isBlank() && !this.builtInById.containsKey(id))
                .distinct()
                .sorted()
                .toList();
        int offset = 0;
        for (String id : userOnly) {
            result.add(CategoryDef.synthetic(id, offset++));
        }
        return result;
    }

    public CategoryDef resolveDef(String id) {
        if (id == null || id.isBlank()) return this.builtInById.getOrDefault("CUSTOM", CategoryDef.synthetic("CUSTOM", 0));
        return this.builtInById.getOrDefault(id, CategoryDef.synthetic(id, 0));
    }
}
