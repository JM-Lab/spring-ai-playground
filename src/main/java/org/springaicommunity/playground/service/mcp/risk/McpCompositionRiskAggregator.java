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
package org.springaicommunity.playground.service.mcp.risk;

import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class McpCompositionRiskAggregator {

    public RiskLevel builtinServerLevel(Collection<RiskLevel> exposedToolLevels) {
        if (exposedToolLevels == null || exposedToolLevels.isEmpty()) return RiskLevel.L0;
        RiskLevel max = RiskLevel.L0;
        for (RiskLevel level : exposedToolLevels) {
            if (level == null) continue;
            if (level.ordinal() > max.ordinal()) max = level;
        }
        return max;
    }

    public RiskLevel compositionLevel(List<RiskLevel> memberLevels) {
        return builtinServerLevel(memberLevels);
    }
}
