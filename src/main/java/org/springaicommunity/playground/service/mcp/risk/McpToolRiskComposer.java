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

@Component
public class McpToolRiskComposer {

    public record Composed(
            RiskLevel finalLevel,
            RiskLevel serverLevel,
            RiskLevel publishLevel,
            String floorTrigger) {}

    public Composed composeWrappedExternal(McpServerRiskCalculator.Result server,
            McpToolPublishRiskCalculator.Result publish) {
        if (server == null || publish == null) {
            throw new IllegalArgumentException("server and publish results must not be null");
        }
        if (server.floorTrigger() != null || publish.floorTrigger() != null) {
            RiskLevel level = mergeFloor(server.level(), publish.level());
            String trigger = server.floorTrigger() != null ? server.floorTrigger() : publish.floorTrigger();
            return new Composed(level, server.level(), publish.level(), trigger);
        }
        int finalScore = server.totalScore() + publish.totalScore();
        RiskLevel level = bucketWrapped(finalScore);
        return new Composed(level, server.level(), publish.level(), null);
    }

    public Composed composeInternalJs(RiskLevel sandboxLevel) {
        if (sandboxLevel == null) sandboxLevel = RiskLevel.L0;
        if (sandboxLevel == RiskLevel.L0) {
            return new Composed(RiskLevel.L0, null, RiskLevel.L0, null);
        }
        return new Composed(sandboxLevel, null, sandboxLevel, null);
    }

    // HITL review of every call lowers risk by one band, floored at L1.
    public static RiskLevel applyHitlMitigation(RiskLevel level, boolean hitl) {
        if (!hitl || level == null) return level;
        int lowered = Math.max(RiskLevel.L1.ordinal(), level.ordinal() - 1);
        return RiskLevel.values()[lowered];
    }

    static RiskLevel bucketWrapped(int score) {
        if (score <= 0) return RiskLevel.L1;
        if (score == 1) return RiskLevel.L2;
        if (score == 2) return RiskLevel.L3;
        if (score == 3) return RiskLevel.L4;
        return RiskLevel.L5;
    }

    static RiskLevel mergeFloor(RiskLevel a, RiskLevel b) {
        RiskLevel left = a == null ? RiskLevel.L1 : a;
        RiskLevel right = b == null ? RiskLevel.L1 : b;
        return left.ordinal() >= right.ordinal() ? left : right;
    }
}
