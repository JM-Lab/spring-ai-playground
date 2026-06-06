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

import org.slf4j.MDC;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.LinkedHashMap;
import java.util.Map;

public record McpInvocationContext(
        String cid,
        String via,
        String origin,
        String compositionId,
        String compositionName,
        String exposedAlias,
        String upstreamServerId,
        String upstreamToolName,
        String upstreamTransport,
        RiskLevel finalRisk,
        RiskLevel serverRisk,
        RiskLevel publishRisk,
        String floorTrigger) {

    public static McpInvocationContext forWrappedExternal(String cid, String compositionId, String compositionName,
            String exposedAlias, String upstreamServerId, String upstreamToolName, String upstreamTransport,
            RiskLevel finalRisk, RiskLevel serverRisk, RiskLevel publishRisk, String floorTrigger) {
        return new McpInvocationContext(cid, McpRiskMdcKeys.VIA_MCP_SERVER, McpRiskMdcKeys.ORIGIN_WRAPPED_EXTERNAL,
                compositionId, compositionName, exposedAlias, upstreamServerId, upstreamToolName, upstreamTransport,
                finalRisk, serverRisk, publishRisk, floorTrigger);
    }

    public static McpInvocationContext forInternalJs(String cid, String via, RiskLevel finalRisk) {
        return new McpInvocationContext(cid, via == null ? McpRiskMdcKeys.VIA_CHAT : via,
                McpRiskMdcKeys.ORIGIN_INTERNAL_JS, null, null, null, null, null, null,
                finalRisk, null, finalRisk, null);
    }

    public Map<String, String> toMdc() {
        Map<String, String> mdc = new LinkedHashMap<>();
        mdc.put(McpRiskMdcKeys.CID, nullSafe(cid));
        mdc.put(McpRiskMdcKeys.VIA, nullSafe(via));
        mdc.put(McpRiskMdcKeys.ORIGIN, nullSafe(origin));
        mdc.put(McpRiskMdcKeys.COMPOSITION_ID, nullSafe(compositionId));
        mdc.put(McpRiskMdcKeys.COMPOSITION_NAME, nullSafe(compositionName));
        mdc.put(McpRiskMdcKeys.EXPOSED_ALIAS, nullSafe(exposedAlias));
        mdc.put(McpRiskMdcKeys.UPSTREAM_SERVER, nullSafe(upstreamServerId));
        mdc.put(McpRiskMdcKeys.UPSTREAM_TOOL, nullSafe(upstreamToolName));
        mdc.put(McpRiskMdcKeys.UPSTREAM_TRANSPORT, nullSafe(upstreamTransport));
        mdc.put(McpRiskMdcKeys.RISK_FINAL, finalRisk == null ? "" : finalRisk.name());
        mdc.put(McpRiskMdcKeys.RISK_SERVER, serverRisk == null ? "" : serverRisk.name());
        mdc.put(McpRiskMdcKeys.RISK_PUBLISH, publishRisk == null ? "" : publishRisk.name());
        mdc.put(McpRiskMdcKeys.FLOOR_TRIGGER, nullSafe(floorTrigger));
        return mdc;
    }

    public Map<String, String> pushMdc() {
        Map<String, String> applied = toMdc();
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (String key : applied.keySet()) {
            snapshot.put(key, MDC.get(key));
        }
        for (Map.Entry<String, String> entry : applied.entrySet()) {
            MDC.put(entry.getKey(), entry.getValue());
        }
        return snapshot;
    }

    public static void popMdc(Map<String, String> previousSnapshot) {
        if (previousSnapshot == null) return;
        for (Map.Entry<String, String> entry : previousSnapshot.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
