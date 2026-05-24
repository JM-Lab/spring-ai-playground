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
package org.springaicommunity.playground.service.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ToolCategoryCatalog {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CategoryDef(String id, String displayName, int order, String icon, String description) {}

    static final String CATEGORIES_RESOURCE = "tool/default-tool-categories.json";
    static final String FALLBACK_ID = "CUSTOM";

    private final List<CategoryDef> orderedCategories;
    private final Map<String, CategoryDef> byId;

    public ToolCategoryCatalog() throws IOException {
        this(loadDefaultCategories());
    }

    ToolCategoryCatalog(List<CategoryDef> categories) {
        List<CategoryDef> sorted = new java.util.ArrayList<>(categories);
        sorted.sort(Comparator.comparingInt(CategoryDef::order));
        this.orderedCategories = List.copyOf(sorted);
        Map<String, CategoryDef> map = new LinkedHashMap<>();
        for (CategoryDef categoryDef : this.orderedCategories) {
            map.put(categoryDef.id(), categoryDef);
        }
        this.byId = Map.copyOf(map);
        if (!this.byId.containsKey(FALLBACK_ID)) {
            throw new IllegalStateException(
                    "tool category catalog must include the '" + FALLBACK_ID + "' fallback id");
        }
    }

    private static List<CategoryDef> loadDefaultCategories() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = new ClassPathResource(CATEGORIES_RESOURCE).getInputStream()) {
            return mapper.readValue(in, new TypeReference<>() {});
        }
    }

    public List<CategoryDef> categories() {
        return orderedCategories;
    }

    public Set<String> categoryIds() {
        return byId.keySet();
    }

    public Optional<CategoryDef> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id));
    }

    public CategoryDef resolveOrFallback(String id) {
        CategoryDef def = id == null ? null : byId.get(id);
        return def != null ? def : byId.get(FALLBACK_ID);
    }

    public boolean isKnown(String id) {
        return id != null && byId.containsKey(id);
    }
}
