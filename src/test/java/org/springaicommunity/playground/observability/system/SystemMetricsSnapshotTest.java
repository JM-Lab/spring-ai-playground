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
package org.springaicommunity.playground.observability.system;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.HostStats;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot.Snapshot;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SystemMetricsSnapshotTest {

    @Test
    void heapUsedMaxCommittedAreSummedAcrossPools() {
        MeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "jvm.memory.used", Tags.of("area", "heap", "id", "G1 Eden Space"), 1_000_000);
        gauge(registry, "jvm.memory.used", Tags.of("area", "heap", "id", "G1 Old Gen"), 5_000_000);
        gauge(registry, "jvm.memory.max", Tags.of("area", "heap", "id", "G1 Eden Space"), 2_000_000);
        gauge(registry, "jvm.memory.max", Tags.of("area", "heap", "id", "G1 Old Gen"), 8_000_000);
        gauge(registry, "jvm.memory.committed", Tags.of("area", "heap", "id", "G1 Eden Space"), 1_500_000);
        gauge(registry, "jvm.memory.used", Tags.of("area", "nonheap", "id", "Metaspace"), 500_000);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.jvmHeapUsedBytes).isEqualTo(6_000_000d);
        assertThat(s.jvmHeapMaxBytes).isEqualTo(10_000_000d);
        assertThat(s.jvmHeapCommittedBytes).isEqualTo(1_500_000d);
        assertThat(s.jvmNonHeapUsedBytes).isEqualTo(500_000d);
        assertThat(s.jvmHeapPoolUsed).containsEntry("G1 Eden Space", 1_000_000L);
        assertThat(s.jvmHeapPoolUsed).containsEntry("G1 Old Gen", 5_000_000L);
    }

    @Test
    void logbackEventsAreReadEvenWhenRegisteredAsFunctionCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        FunctionCounter.builder("logback.events", 119.0, Number::doubleValue)
                .tag("level", "info").register(registry);
        FunctionCounter.builder("logback.events", 2.0, Number::doubleValue)
                .tag("level", "error").register(registry);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.logbackEventsByLevel).containsEntry("info", 119L).containsEntry("error", 2L);
    }

    @Test
    void capturesHostPhysicalMemoryFromOsBean() {
        Snapshot s = new SystemMetricsSnapshot(new SimpleMeterRegistry()).capture();

        assertThat(s.systemPhysicalMemoryTotalBytes).isPositive();
        assertThat(s.systemPhysicalMemoryFreeBytes).isBetween(0L, s.systemPhysicalMemoryTotalBytes);
    }

    @Test
    void threadStatesAreCapturedPerStateTag() {
        MeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "jvm.threads.live", Tags.empty(), 73);
        gauge(registry, "jvm.threads.daemon", Tags.empty(), 70);
        gauge(registry, "jvm.threads.peak", Tags.empty(), 80);
        gauge(registry, "jvm.threads.states", Tags.of("state", "runnable"), 5);
        gauge(registry, "jvm.threads.states", Tags.of("state", "waiting"), 50);
        gauge(registry, "jvm.threads.states", Tags.of("state", "timed-waiting"), 18);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.jvmThreadsLive).isEqualTo(73L);
        assertThat(s.jvmThreadsDaemon).isEqualTo(70L);
        assertThat(s.jvmThreadsPeak).isEqualTo(80L);
        assertThat(s.jvmThreadsByState).containsEntry("runnable", 5L);
        assertThat(s.jvmThreadsByState).containsEntry("waiting", 50L);
        assertThat(s.jvmThreadsByState).containsEntry("timed-waiting", 18L);
    }

    @Test
    void mcpRiskSignalsAreGroupedByType() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("saip.risk.signal", "type", "hash-ledger-mismatch").increment();
        registry.counter("saip.risk.signal", "type", "hash-ledger-mismatch").increment();
        registry.counter("saip.risk.signal", "type", "poisoning-hit").increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpRiskSignalByType).containsEntry("hash-ledger-mismatch", 2L);
        assertThat(s.mcpRiskSignalByType).containsEntry("poisoning-hit", 1L);
    }

    @Test
    void mcpHitlDecisionsAreGroupedByOutcomeAcrossSides() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("mcp.hitl.decision", "outcome", "approved", "side", "chat").increment();
        registry.counter("mcp.hitl.decision", "outcome", "approved", "side", "server").increment();
        registry.counter("mcp.hitl.decision", "outcome", "declined", "side", "chat").increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpHitlByOutcome).containsEntry("approved", 2L);
        assertThat(s.mcpHitlByOutcome).containsEntry("declined", 1L);
    }

    @Test
    void mcpToolRiskIsGroupedByLevel() {
        MeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("saip.tool.risk", "level", "L5").increment();
        registry.counter("saip.tool.risk", "level", "L5").increment();
        registry.counter("saip.tool.risk", "level", "L3").increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpToolRiskByLevel).containsEntry("L5", 2L);
        assertThat(s.mcpToolRiskByLevel).containsEntry("L3", 1L);
    }

    @Test
    void gcPauseTimerIsCapturedPerGcActionPair() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer pause = Timer.builder("jvm.gc.pause")
                .tag("gc", "G1 Young Generation")
                .tag("action", "end of minor GC")
                .register(registry);
        pause.record(50, TimeUnit.MILLISECONDS);
        pause.record(70, TimeUnit.MILLISECONDS);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        String key = "G1 Young Generation / end of minor GC";
        assertThat(s.gcPauseSecondsSum).containsKey(key);
        assertThat(s.gcPauseSecondsSum.get(key)).isCloseTo(0.120, within(0.001));
        assertThat(s.gcPauseCount).containsEntry(key, 2L);
        assertThat(s.gcPauseMaxMs.get(key)).isGreaterThanOrEqualTo(70);
    }

    @Test
    void jvmInfoTagsAreCapturedIntoMap() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Gauge.builder("jvm.info", new AtomicLong(1), AtomicLong::doubleValue)
                .tag("vendor", "Homebrew")
                .tag("runtime", "OpenJDK Runtime Environment")
                .tag("version", "21.0.5")
                .register(registry);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.jvmInfo).containsEntry("vendor", "Homebrew");
        assertThat(s.jvmInfo).containsEntry("runtime", "OpenJDK Runtime Environment");
        assertThat(s.jvmInfo).containsEntry("version", "21.0.5");
    }

    @Test
    void bufferPoolsUsedCountCapacityEachKeyedById() {
        MeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "jvm.buffer.count", Tags.of("id", "direct"), 34);
        gauge(registry, "jvm.buffer.count", Tags.of("id", "mapped"), 0);
        gauge(registry, "jvm.buffer.memory.used", Tags.of("id", "direct"), 6_975_592);
        gauge(registry, "jvm.buffer.memory.used", Tags.of("id", "mapped"), 0);
        gauge(registry, "jvm.buffer.total.capacity", Tags.of("id", "direct"), 8_000_000);
        gauge(registry, "jvm.buffer.total.capacity", Tags.of("id", "mapped"), 0);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.jvmBufferCount).containsEntry("direct", 34L);
        assertThat(s.jvmBufferCount).containsEntry("mapped", 0L);
        assertThat(s.jvmBufferUsedBytes).containsEntry("direct", 6_975_592L);
        assertThat(s.jvmBufferTotalCapacity).containsEntry("direct", 8_000_000L);
        assertThat(s.jvmBufferTotalCapacity).containsEntry("mapped", 0L);
    }

    @Test
    void httpClientRequestsAggregatePerHostWithCountTotalMax() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer ollama = Timer.builder("http.client.requests")
                .tag("client.name", "localhost").tag("uri", "/api/chat")
                .register(registry);
        ollama.record(500, TimeUnit.MILLISECONDS);
        ollama.record(1500, TimeUnit.MILLISECONDS);
        Timer openai = Timer.builder("http.client.requests")
                .tag("client.name", "api.openai.com").tag("uri", "/v1/chat/completions")
                .register(registry);
        openai.record(2000, TimeUnit.MILLISECONDS);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        HostStats local = s.httpClientByHost.get("localhost");
        assertThat(local).isNotNull();
        assertThat(local.count).isEqualTo(2L);
        assertThat(local.totalTimeSeconds).isCloseTo(2.0, within(0.001));
        assertThat(local.maxMs).isGreaterThanOrEqualTo(1500);
        assertThat(local.avgMs()).isCloseTo(1000.0, within(0.001));

        HostStats remote = s.httpClientByHost.get("api.openai.com");
        assertThat(remote).isNotNull();
        assertThat(remote.count).isEqualTo(1L);
        assertThat(remote.avgMs()).isCloseTo(2000.0, within(0.001));
    }

    @Test
    void httpInFlightActiveLongTaskTimersAreCaptured() {
        MeterRegistry registry = new SimpleMeterRegistry();
        LongTaskTimer serverActive = LongTaskTimer.builder("http.server.requests.active")
                .register(registry);
        LongTaskTimer.Sample sample = serverActive.start();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.httpServerActive).isEqualTo(1L);
        assertThat(s.httpClientActive).isEqualTo(0L);

        sample.stop();
    }

    @Test
    void genAiTimerSummaryCapturesCountTotalMaxMean() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer ttft = Timer.builder("gen_ai.client.operation.time_to_first_chunk").register(registry);
        ttft.record(200, TimeUnit.MILLISECONDS);
        ttft.record(400, TimeUnit.MILLISECONDS);
        ttft.record(600, TimeUnit.MILLISECONDS);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.genAiTtft.count).isEqualTo(3L);
        assertThat(s.genAiTtft.totalMs).isCloseTo(1200, within(1.0));
        assertThat(s.genAiTtft.maxMs).isGreaterThanOrEqualTo(600);
        assertThat(s.genAiTtft.meanMs).isCloseTo(400, within(1.0));
    }

    @Test
    void genAiImageAndEmbeddingTimerSummariesDefaultToZeroWhenUnregistered() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.genAiImageDuration.count).isZero();
        assertThat(s.genAiEmbeddingDuration.count).isZero();
        assertThat(s.genAiPerChunk.count).isZero();
    }

    @Test
    void diskAndProcessGauges() {
        MeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "disk.free", Tags.empty(), 386_010_000_000L);
        gauge(registry, "disk.total", Tags.empty(), 926_350_000_000L);
        gauge(registry, "process.cpu.usage", Tags.empty(), 0.07);
        gauge(registry, "system.cpu.usage", Tags.empty(), 0.62);
        gauge(registry, "system.cpu.count", Tags.empty(), 10);
        gauge(registry, "process.uptime", Tags.empty(), 96.0);
        gauge(registry, "process.cpu.time", Tags.empty(), 12_000_000_000.0);
        gauge(registry, "process.files.open", Tags.empty(), 432);
        gauge(registry, "process.files.max", Tags.empty(), 1_048_576);
        gauge(registry, "system.load.average.1m", Tags.empty(), 4.41);
        gauge(registry, "application.ready.time", Tags.empty(), 27.7);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.diskFreeBytes).isEqualTo(386_010_000_000L);
        assertThat(s.diskTotalBytes).isEqualTo(926_350_000_000L);
        assertThat(s.processCpuUsage).isCloseTo(0.07, within(0.001));
        assertThat(s.systemCpuUsage).isCloseTo(0.62, within(0.001));
        assertThat(s.systemCpuCount).isEqualTo(10L);
        assertThat(s.processUptimeSeconds).isCloseTo(96.0, within(0.001));
        assertThat(s.processCpuTimeNs).isEqualTo(12_000_000_000L);
        assertThat(s.processFilesOpen).isEqualTo(432L);
        assertThat(s.processFilesMax).isEqualTo(1_048_576L);
        assertThat(s.systemLoadAverage1m).isCloseTo(4.41, within(0.001));
        assertThat(s.applicationReadySeconds).isCloseTo(27.7, within(0.001));
    }

    @Test
    void httpServerRequestsAggregateByStatus() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer ok200 = Timer.builder("http.server.requests")
                .tag("status", "200").tag("outcome", "SUCCESS").register(registry);
        ok200.record(50, TimeUnit.MILLISECONDS);
        ok200.record(60, TimeUnit.MILLISECONDS);
        Timer not404 = Timer.builder("http.server.requests")
                .tag("status", "404").tag("outcome", "CLIENT_ERROR").register(registry);
        not404.record(30, TimeUnit.MILLISECONDS);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.httpRequestsByStatus).containsEntry("200 (SUCCESS)", 2L);
        assertThat(s.httpRequestsByStatus).containsEntry("404 (CLIENT_ERROR)", 1L);
        assertThat(s.httpRequestsTotal).isEqualTo(3L);
        assertThat(s.httpRequestsTotalTimeSeconds).isCloseTo(0.140, within(0.001));
    }

    @Test
    void logbackEventsAreAggregatedPerLevel() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter info = Counter.builder("logback.events").tag("level", "info").register(registry);
        info.increment(10);
        Counter warn = Counter.builder("logback.events").tag("level", "warn").register(registry);
        warn.increment(2);
        Counter error = Counter.builder("logback.events").tag("level", "error").register(registry);
        error.increment(1);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.logbackEventsByLevel).containsEntry("info", 10L);
        assertThat(s.logbackEventsByLevel).containsEntry("warn", 2L);
        assertThat(s.logbackEventsByLevel).containsEntry("error", 1L);
    }

    @Test
    void springAiInFlightGaugesSumCorrectly() {
        MeterRegistry registry = new SimpleMeterRegistry();
        gauge(registry, "spring.ai.chat.client.active", Tags.empty(), 1);
        gauge(registry, "gen_ai.client.operation.active", Tags.empty(), 1);
        gauge(registry, "spring.ai.advisor.active", Tags.empty(), 4);
        gauge(registry, "db.vector.client.operation.active", Tags.empty(), 0);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.activeChatClient).isEqualTo(1L);
        assertThat(s.activeChatModel).isEqualTo(1L);
        assertThat(s.activeAdvisor).isEqualTo(4L);
        assertThat(s.activeVector).isZero();
    }

    @Test
    void mcpPrimitiveTimersAggregatePerKindAndOutcome() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Timer t1 = Timer.builder("mcp.primitive")
                .tag("kind", "resources.read").tag("server", "weather")
                .tag("transport", "STDIO").tag("outcome", "success")
                .register(registry);
        Timer t2 = Timer.builder("mcp.primitive")
                .tag("kind", "resources.read").tag("server", "weather")
                .tag("transport", "STDIO").tag("outcome", "error")
                .register(registry);
        Timer t3 = Timer.builder("mcp.primitive")
                .tag("kind", "prompts.get").tag("server", "docs")
                .tag("transport", "STREAMABLE_HTTP").tag("outcome", "success")
                .register(registry);
        t1.record(100, TimeUnit.MILLISECONDS);
        t1.record(200, TimeUnit.MILLISECONDS);
        t2.record(50, TimeUnit.MILLISECONDS);
        t3.record(300, TimeUnit.MILLISECONDS);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpPrimitiveCountByKind)
                .containsEntry("resources.read", 3L)
                .containsEntry("prompts.get", 1L);
        assertThat(s.mcpPrimitiveOutcomeCounts)
                .containsEntry("success", 3L)
                .containsEntry("error", 1L);
        assertThat(s.mcpPrimitiveCountByServer)
                .containsEntry("weather", 3L)
                .containsEntry("docs", 1L);
        assertThat(s.mcpPrimitiveTimerByKind.get("resources.read").count).isEqualTo(3L);
        assertThat(s.mcpPrimitiveTimerByKind.get("resources.read").meanMs)
                .isCloseTo((100 + 200 + 50) / 3.0, within(0.01));
    }

    @Test
    void mcpServerHandlerCountersKeyedByKindAndOutcome() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("mcp.server.handler")
                .tag("kind", "sampling").tag("server", "s1").tag("outcome", "success")
                .register(registry).increment(3);
        Counter.builder("mcp.server.handler")
                .tag("kind", "elicitation").tag("server", "s1").tag("outcome", "timeout")
                .register(registry).increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpServerHandlerCounts)
                .containsEntry("sampling / success", 3L)
                .containsEntry("elicitation / timeout", 1L);
    }

    @Test
    void mcpOauthAuthorizedCounterSumsToLifetimeTotal() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("mcp.oauth.authorized").tag("server", "github")
                .register(registry).increment(2);
        Counter.builder("mcp.oauth.authorized").tag("server", "slack")
                .register(registry).increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpOAuthAuthorizedTotal).isEqualTo(3L);
    }

    @Test
    void mcpLifecycleCountersCollapseByOutcomeAndPerServer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("mcp.server.lifecycle")
                .tag("outcome", "connect").tag("server", "s1").tag("transport", "STDIO")
                .register(registry).increment(2);
        Counter.builder("mcp.server.lifecycle")
                .tag("outcome", "disconnect").tag("server", "s1").tag("transport", "STDIO")
                .register(registry).increment();
        Counter.builder("mcp.server.lifecycle")
                .tag("outcome", "error").tag("server", "s2").tag("transport", "SSE")
                .register(registry).increment();

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.mcpLifecycleByOutcome)
                .containsEntry("connect", 2L)
                .containsEntry("disconnect", 1L)
                .containsEntry("error", 1L);
        assertThat(s.mcpLifecycleByServer)
                .containsEntry("STDIO:s1 / connect", 2L)
                .containsEntry("STDIO:s1 / disconnect", 1L)
                .containsEntry("SSE:s2 / error", 1L);
    }

    @Test
    void sandboxGuardCountersAggregatePerCategory() {
        MeterRegistry registry = new SimpleMeterRegistry();
        Counter.builder("sandbox.guard.blocked")
                .tag("category", "fetch").tag("reason", "literal-private-ip")
                .register(registry).increment(2);
        Counter.builder("sandbox.guard.blocked")
                .tag("category", "fetch").tag("reason", "host-not-in-allowlist")
                .register(registry).increment();
        Counter.builder("sandbox.guard.blocked")
                .tag("category", "fs").tag("reason", "path-escapes-base")
                .register(registry).increment(5);

        Snapshot s = new SystemMetricsSnapshot(registry).capture();

        assertThat(s.sandboxGuardBlocked)
                .containsEntry("fetch / literal-private-ip", 2L)
                .containsEntry("fetch / host-not-in-allowlist", 1L)
                .containsEntry("fs / path-escapes-base", 5L);
        assertThat(s.sandboxGuardBlockedByCategory)
                .containsEntry("fetch", 3L)
                .containsEntry("fs", 5L);
    }

    private static void gauge(MeterRegistry registry, String name, Tags tags, double value) {
        Gauge.builder(name, new AtomicLong(Double.doubleToRawLongBits(value)),
                ref -> Double.longBitsToDouble(ref.get()))
                .tags(tags)
                .register(registry);
    }
}
