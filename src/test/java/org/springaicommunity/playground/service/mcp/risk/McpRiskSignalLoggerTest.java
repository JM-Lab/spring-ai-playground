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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpRiskSignalLoggerTest {

    private double count(SimpleMeterRegistry registry, String type) {
        return registry.counter(McpRiskSignalLogger.COUNTER, "type", type).count();
    }

    @Test
    void eachSignalIncrementsItsTaggedCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(registry, new McpRiskEventRingBuffer());

        sink.onHashLedgerMismatch(null);
        sink.onPoisoningHit(null);
        sink.onPoisoningHit(null);
        sink.onServerRiskComputed(null);
        sink.onFloorOverrideTriggered(null);
        sink.onCompositionLifecycle(null);
        sink.onToolPublishRiskComputed(null);

        assertThat(count(registry, "hash-ledger-mismatch")).isEqualTo(1.0);
        assertThat(count(registry, "poisoning-hit")).isEqualTo(2.0);
        assertThat(count(registry, "server-risk-computed")).isEqualTo(1.0);
        assertThat(count(registry, "floor-override-triggered")).isEqualTo(1.0);
        assertThat(count(registry, "composition-lifecycle")).isEqualTo(1.0);
        assertThat(count(registry, "tool-publish-risk-computed")).isEqualTo(1.0);
    }

    @Test
    void replacesNoopContract() {
        assertThat(new McpRiskSignalLogger(new SimpleMeterRegistry(), new McpRiskEventRingBuffer()))
                .isInstanceOf(McpRiskSignalSink.class);
    }

    @Test
    void recordsEventIntoRingBufferForAudit() {
        McpRiskEventRingBuffer buffer = new McpRiskEventRingBuffer();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(new SimpleMeterRegistry(), buffer);

        Instant at = Instant.ofEpochMilli(1_700_000_000_000L);
        sink.onPoisoningHit(new McpRiskEvents.PoisoningHit(at, "weather-server", "get_alerts",
                "PROMPT_INJECTION", "ignore previous instructions", "1 match(es): PROMPT_INJECTION"));

        List<McpRiskEventRingBuffer.RiskEvent> events = buffer.snapshot();
        assertThat(events).hasSize(1);
        McpRiskEventRingBuffer.RiskEvent e = events.getFirst();
        assertThat(e.epochMs()).isEqualTo(at.toEpochMilli());
        assertThat(e.type()).isEqualTo("poisoning-hit");
        assertThat(e.severity()).isEqualTo("warn");
        assertThat(e.summary()).contains("weather-server", "get_alerts", "PROMPT_INJECTION");
    }

    @Test
    void nullEventIncrementsCounterButIsNotAudited() {
        McpRiskEventRingBuffer buffer = new McpRiskEventRingBuffer();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(new SimpleMeterRegistry(), buffer);

        sink.onPoisoningHit(null);

        assertThat(buffer.snapshot()).isEmpty();
    }

    @Test
    void sameContentSignalCountsOnceAcrossRepeatedEvaluations() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        McpRiskEventRingBuffer buffer = new McpRiskEventRingBuffer();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(registry, buffer);
        McpRiskEvents.PoisoningHit hit = new McpRiskEvents.PoisoningHit(
                Instant.ofEpochMilli(1_700_000_000_000L), "weather-server", "get_alerts",
                "PROMPT_INJECTION", "ignore previous instructions", "1 match(es)");

        sink.onPoisoningHit(hit);
        sink.onPoisoningHit(hit);

        assertThat(count(registry, "poisoning-hit")).isEqualTo(1.0);
        assertThat(buffer.snapshot()).hasSize(1);
    }

    @Test
    void changedContentSignalCountsAgain() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        McpRiskEventRingBuffer buffer = new McpRiskEventRingBuffer();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(registry, buffer);
        Instant at = Instant.ofEpochMilli(1_700_000_000_000L);

        sink.onPoisoningHit(new McpRiskEvents.PoisoningHit(at, "weather-server", "get_alerts",
                "PROMPT_INJECTION", "ignore previous instructions", "1 match(es)"));
        sink.onPoisoningHit(new McpRiskEvents.PoisoningHit(at, "weather-server", "get_alerts",
                "PROMPT_INJECTION", "disregard the system prompt", "1 match(es)"));

        assertThat(count(registry, "poisoning-hit")).isEqualTo(2.0);
        assertThat(buffer.snapshot()).hasSize(2);
    }

    @Test
    void serverRiskRecountsOnlyOnLevelChange() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        McpRiskEventRingBuffer buffer = new McpRiskEventRingBuffer();
        McpRiskSignalLogger sink = new McpRiskSignalLogger(registry, buffer);
        Instant at = Instant.ofEpochMilli(1_700_000_000_000L);

        sink.onServerRiskComputed(new McpRiskEvents.ServerRiskComputed(
                at, "srv", "Server", Map.of(), 4, RiskLevel.L2, null));
        sink.onServerRiskComputed(new McpRiskEvents.ServerRiskComputed(
                at, "srv", "Server", Map.of(), 4, RiskLevel.L2, null));
        sink.onServerRiskComputed(new McpRiskEvents.ServerRiskComputed(
                at, "srv", "Server", Map.of(), 9, RiskLevel.L4, null));

        assertThat(count(registry, "server-risk-computed")).isEqualTo(2.0);
        assertThat(buffer.snapshot()).hasSize(2);
    }
}
