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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryDef(String id, String displayName, int order, String icon, String description,
                          boolean builtIn) {

    public CategoryDef {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("CategoryDef id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            displayName = id;
        }
        if (icon == null) {
            icon = "folder-o";
        }
        if (description == null) {
            description = "";
        }
    }

    public static CategoryDef synthetic(String id, int orderOffset) {
        return new CategoryDef(id, id, 2000 + orderOffset, "folder-o", "", false);
    }
}
