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
public record McpRiskFactors(
        boolean writeCapability,
        boolean execCapability,
        boolean sendsUserData,
        boolean irreversibleActions) {

    public static final McpRiskFactors SAFE_READ_ONLY = new McpRiskFactors(false, false, false, false);

    public McpRiskFactors orWith(McpRiskFactors other) {
        if (other == null) return this;
        return new McpRiskFactors(
                writeCapability || other.writeCapability,
                execCapability || other.execCapability,
                sendsUserData || other.sendsUserData,
                irreversibleActions || other.irreversibleActions);
    }

    public boolean anyCapability() {
        return writeCapability || execCapability || sendsUserData || irreversibleActions;
    }
}
