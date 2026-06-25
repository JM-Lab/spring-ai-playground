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

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer.RiskEvent;
import org.springaicommunity.playground.service.mcp.risk.McpRiskEvents.Types;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class McpRiskSignalLogger implements McpRiskSignalSink {

    public static final String COUNTER = "saip.risk.signal";

    private static final Logger logger = LoggerFactory.getLogger(McpRiskSignalLogger.class);

    private final MeterRegistry meterRegistry;
    private final McpRiskEventRingBuffer eventBuffer;
    private final Set<String> seenComputedSignals = ConcurrentHashMap.newKeySet();

    public McpRiskSignalLogger(MeterRegistry meterRegistry, McpRiskEventRingBuffer eventBuffer) {
        this.meterRegistry = meterRegistry;
        this.eventBuffer = eventBuffer;
    }

    @Override
    public void onServerRiskComputed(McpRiskEvents.ServerRiskComputed event) {
        if (event != null && !firstSeen(Types.SERVER_RISK_COMPUTED, event.serverId(), event.level())) return;
        info(Types.SERVER_RISK_COMPUTED, event,
                event == null ? null : event.at(),
                event == null ? null : event.displayName() + " → " + event.level());
    }

    @Override
    public void onToolPublishRiskComputed(McpRiskEvents.ToolPublishRiskComputed event) {
        if (event != null && !firstSeen(Types.TOOL_PUBLISH_RISK_COMPUTED,
                event.serverId(), event.toolName(), event.level())) {
            return;
        }
        info(Types.TOOL_PUBLISH_RISK_COMPUTED, event,
                event == null ? null : event.at(),
                event == null ? null : event.serverId() + "/" + event.toolName() + " → " + event.level());
    }

    @Override
    public void onFloorOverrideTriggered(McpRiskEvents.FloorOverrideTriggered event) {
        if (event != null && !firstSeen(Types.FLOOR_OVERRIDE_TRIGGERED, event.layer(), event.trigger(),
                event.serverId(), event.toolName(), event.resultingLevel())) {
            return;
        }
        warn(Types.FLOOR_OVERRIDE_TRIGGERED, event,
                event == null ? null : event.at(),
                event == null ? null : event.serverId() + "/" + event.toolName() + " → "
                        + event.resultingLevel() + " (" + event.trigger() + ")");
    }

    @Override
    public void onHashLedgerMismatch(McpRiskEvents.HashLedgerMismatch event) {
        warn(Types.HASH_LEDGER_MISMATCH, event,
                event == null ? null : event.at(),
                event == null ? null : event.serverId() + "/" + event.toolName()
                        + " content hash changed " + event.changedFields());
    }

    @Override
    public void onCompositionLifecycle(McpRiskEvents.CompositionLifecycle event) {
        info(Types.COMPOSITION_LIFECYCLE, event,
                event == null ? null : event.at(),
                event == null ? null : event.compositionName() + " " + event.action());
    }

    @Override
    public void onPoisoningHit(McpRiskEvents.PoisoningHit event) {
        if (event != null && !firstSeen(Types.POISONING_HIT, event.serverId(), event.toolName(),
                event.patternId(), event.matchedText())) {
            return;
        }
        warn(Types.POISONING_HIT, event,
                event == null ? null : event.at(),
                event == null ? null : event.serverId() + "/" + event.toolName()
                        + " matched pattern " + event.patternId());
    }

    private boolean firstSeen(String type, Object... keyParts) {
        StringBuilder key = new StringBuilder(type);
        for (Object part : keyParts) {
            key.append('|').append(part);
        }
        return seenComputedSignals.add(key.toString());
    }

    private void info(String type, Object event, Instant at, String summary) {
        logger.info("saip.risk.signal type={} event={}", type, event);
        count(type);
        if (summary != null) record(at, type, "info", summary);
    }

    private void warn(String type, Object event, Instant at, String summary) {
        logger.warn("saip.risk.signal type={} event={}", type, event);
        count(type);
        if (summary != null) record(at, type, "warn", summary);
    }

    private void count(String type) {
        this.meterRegistry.counter(COUNTER, "type", type).increment();
    }

    private void record(Instant at, String type, String severity, String summary) {
        this.eventBuffer.record(new RiskEvent(
                at == null ? 0L : at.toEpochMilli(), type, severity, summary));
    }
}
