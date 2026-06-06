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
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpToolDescriptor(
        String name,
        RiskLevel publishRiskLevel,
        Annotations annotations,
        McpRiskFactors riskFactors,
        String rationale,
        String contentHash) {

    public McpToolDescriptor {
        if (annotations == null) annotations = Annotations.EMPTY;
        if (riskFactors == null) riskFactors = McpRiskFactors.SAFE_READ_ONLY;
        if (rationale == null) rationale = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Annotations(
            String title,
            Boolean readOnlyHint,
            Boolean destructiveHint,
            Boolean idempotentHint,
            Boolean openWorldHint) {

        public static final Annotations EMPTY = new Annotations(null, null, null, null, null);

        public boolean noneSpecified() {
            return readOnlyHint == null && destructiveHint == null
                    && idempotentHint == null && openWorldHint == null;
        }
    }
}
