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

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SystemMetricsSnapshot {

    private final MeterRegistry registry;

    public SystemMetricsSnapshot(MeterRegistry registry) {
        this.registry = registry;
    }

    public Snapshot capture() {
        Snapshot s = new Snapshot();

        s.jvmHeapUsedBytes = sumGauge("jvm.memory.used", Tags.of("area", "heap"));
        s.jvmHeapMaxBytes = sumGauge("jvm.memory.max", Tags.of("area", "heap"));
        s.jvmHeapCommittedBytes = sumGauge("jvm.memory.committed", Tags.of("area", "heap"));
        s.jvmNonHeapUsedBytes = sumGauge("jvm.memory.used", Tags.of("area", "nonheap"));
        s.jvmNonHeapCommittedBytes = sumGauge("jvm.memory.committed", Tags.of("area", "nonheap"));

        // Per memory pool used/max for the heap area (eg. "G1 Eden Space", "G1 Old Gen")
        registry.find("jvm.memory.used").tag("area", "heap").gauges().forEach(g -> {
            String id = g.getId().getTag("id");
            if (id != null) s.jvmHeapPoolUsed.put(id, (long) g.value());
        });

        // Buffer pools (direct, mapped)
        registry.find("jvm.buffer.count").gauges().forEach(g -> {
            String id = g.getId().getTag("id");
            if (id != null) s.jvmBufferCount.put(id, (long) g.value());
        });
        registry.find("jvm.buffer.memory.used").gauges().forEach(g -> {
            String id = g.getId().getTag("id");
            if (id != null) s.jvmBufferUsedBytes.put(id, (long) g.value());
        });

        s.jvmThreadsLive = (long) firstGauge("jvm.threads.live");
        s.jvmThreadsDaemon = (long) firstGauge("jvm.threads.daemon");
        s.jvmThreadsPeak = (long) firstGauge("jvm.threads.peak");
        // started is a lifetime FunctionCounter, not a gauge
        s.jvmThreadsStarted = (long) firstFunctionCounterValue("jvm.threads.started");
        registry.find("jvm.threads.states").gauges().forEach(g -> {
            String state = g.getId().getTag("state");
            if (state != null) s.jvmThreadsByState.put(state, (long) g.value());
        });

        registry.find("jvm.gc.pause").timers().forEach(t -> {
            String name = t.getId().getTag("gc");
            String action = t.getId().getTag("action");
            String key = (name == null ? "gc" : name) + " / " + (action == null ? "?" : action);
            s.gcPauseSecondsSum.put(key, t.totalTime(TimeUnit.SECONDS));
            s.gcPauseCount.put(key, (long) t.count());
            s.gcPauseMaxMs.put(key, t.max(TimeUnit.MILLISECONDS));
        });
        s.gcLiveDataBytes = (long) firstGauge("jvm.gc.live.data.size");
        s.gcMaxDataBytes = (long) firstGauge("jvm.gc.max.data.size");
        // allocated / promoted are lifetime FunctionCounter; overhead is a Gauge
        s.gcMemoryAllocatedBytes = (long) firstFunctionCounterValue("jvm.gc.memory.allocated");
        s.gcMemoryPromotedBytes = (long) firstFunctionCounterValue("jvm.gc.memory.promoted");
        s.gcOverheadFraction = firstGauge("jvm.gc.overhead");

        // loaded is a current-state Gauge; unloaded is a lifetime FunctionCounter.
        s.jvmClassesLoaded = (long) firstGauge("jvm.classes.loaded");
        s.jvmClassesUnloaded = (long) firstFunctionCounterValue("jvm.classes.unloaded");

        s.processCpuUsage = firstGauge("process.cpu.usage");
        s.systemCpuUsage = firstGauge("system.cpu.usage");
        s.systemCpuCount = (long) firstGauge("system.cpu.count");
        s.systemLoadAverage1m = firstGauge("system.load.average.1m");
        s.processUptimeSeconds = firstGauge("process.uptime");
        s.processStartTimeSeconds = firstGauge("process.start.time");
        s.applicationReadySeconds = firstGauge("application.ready.time");
        s.applicationStartedSeconds = firstGauge("application.started.time");
        s.processFilesOpen = (long) firstGauge("process.files.open");
        s.processFilesMax = (long) firstGauge("process.files.max");

        s.diskFreeBytes = (long) firstGauge("disk.free");
        s.diskTotalBytes = (long) firstGauge("disk.total");

        registry.find("http.server.requests").timers().forEach(t -> {
            String status = t.getId().getTag("status");
            String outcome = t.getId().getTag("outcome");
            if (status != null) {
                String key = status + " (" + (outcome == null ? "?" : outcome) + ")";
                s.httpRequestsByStatus.merge(key, (long) t.count(), Long::sum);
            }
            s.httpRequestsTotal += t.count();
            s.httpRequestsTotalTimeSeconds += t.totalTime(TimeUnit.SECONDS);
        });

        // active.current / active.max / alive.max are TimeGauge/Gauge.
        // created / expired / rejected are FunctionCounter (lifetime counts).
        s.tomcatSessionsActive = (long) firstGauge("tomcat.sessions.active.current");
        s.tomcatSessionsActiveMax = (long) firstGauge("tomcat.sessions.active.max");
        s.tomcatSessionsCreated = (long) firstFunctionCounterValue("tomcat.sessions.created");
        s.tomcatSessionsExpired = (long) firstFunctionCounterValue("tomcat.sessions.expired");
        s.tomcatSessionsRejected = (long) firstFunctionCounterValue("tomcat.sessions.rejected");

        registry.find("logback.events").counters().forEach(c -> {
            String level = c.getId().getTag("level");
            if (level != null) s.logbackEventsByLevel.merge(level, (long) c.count(), Long::sum);
        });

        s.activeChatClient = (long) sumGauge("gen_ai.chat.client.operation.active", Tags.empty());
        s.activeChatModel = (long) sumGauge("gen_ai.client.operation.active", Tags.empty());
        s.activeAdvisor = (long) sumGauge("spring.ai.advisor.active", Tags.empty());
        s.activeVector = (long) sumGauge("db.vector.client.operation.active", Tags.empty());
        s.activeChatClientStream = (long) sumGauge("gen_ai.chat.client.operation.active",
                Tags.of("spring.ai.chat.client.stream", "true"));

        // jvm.info — vendor / runtime / version metadata, value is constant 1
        Gauge info = registry.find("jvm.info").gauge();
        if (info != null) {
            for (Tag tag : info.getId().getTags()) {
                s.jvmInfo.put(tag.getKey(), tag.getValue());
            }
        }

        s.jvmCompilationTimeMs = firstFunctionCounterValue("jvm.compilation.time");

        registry.find("jvm.memory.usage.after.gc").gauges().forEach(g -> {
            String id = g.getId().getTag("id");
            if (id != null) s.jvmMemoryUsageAfterGc.put(id, g.value());
        });

        registry.find("jvm.gc.concurrent.phase.time").timers().forEach(t -> {
            String key = (t.getId().getTag("gc") == null ? "gc" : t.getId().getTag("gc")) + " / "
                    + (t.getId().getTag("action") == null ? "?" : t.getId().getTag("action"));
            s.gcConcurrentPhaseSecondsSum.put(key, t.totalTime(TimeUnit.SECONDS));
        });

        registry.find("jvm.buffer.total.capacity").gauges().forEach(g -> {
            String id = g.getId().getTag("id");
            if (id != null) s.jvmBufferTotalCapacity.put(id, (long) g.value());
        });

        s.httpServerActive = (long) sumLongTaskTimerActive("http.server.requests.active");
        s.httpClientActive = (long) sumLongTaskTimerActive("http.client.requests.active");

        registry.find("http.client.requests").timers().forEach(t -> {
            String host = t.getId().getTag("client.name");
            if (host == null) host = t.getId().getTag("uri");
            if (host == null) host = "(unknown)";
            HostStats hs = s.httpClientByHost.computeIfAbsent(host, k -> new HostStats());
            hs.count += t.count();
            hs.totalTimeSeconds += t.totalTime(TimeUnit.SECONDS);
            double maxMs = t.max(TimeUnit.MILLISECONDS);
            if (maxMs > hs.maxMs) hs.maxMs = maxMs;
        });

        s.tomcatSessionsAliveMax = (long) firstGauge("tomcat.sessions.alive.max");

        s.processCpuTimeNs = (long) firstFunctionCounterValue("process.cpu.time");

        captureTimerSummary(registry, "gen_ai.client.operation.time_to_first_chunk", s.genAiTtft);
        captureTimerSummary(registry, "gen_ai.client.operation.time_per_output_chunk",
                s.genAiPerChunk);
        captureTimerSummary(registry, "gen_ai.embedding.client.operation",
                s.genAiEmbeddingDuration);
        captureTimerSummary(registry, "gen_ai.image.client.operation",
                s.genAiImageDuration);

        registry.find("sandbox.guard.blocked").counters().forEach(c -> {
            String category = c.getId().getTag("category");
            String reason = c.getId().getTag("reason");
            if (category == null || reason == null) return;
            String key = category + " / " + reason;
            s.sandboxGuardBlocked.merge(key, (long) c.count(), Long::sum);
            s.sandboxGuardBlockedByCategory.merge(category, (long) c.count(), Long::sum);
        });

        registry.find("mcp.server.lifecycle").counters().forEach(c -> {
            String outcome = c.getId().getTag("outcome");
            String server = c.getId().getTag("server");
            String transport = c.getId().getTag("transport");
            if (outcome == null) return;
            s.mcpLifecycleByOutcome.merge(outcome, (long) c.count(), Long::sum);
            if (server != null) {
                String key = (transport == null ? "?" : transport) + ":" + server + " / " + outcome;
                s.mcpLifecycleByServer.merge(key, (long) c.count(), Long::sum);
            }
        });

        // Exclude self-loopback so MCP Primitives reflects only external MCP servers.
        registry.find("mcp.primitive").timers().forEach(t -> {
            String kind = t.getId().getTag("kind");
            String outcome = t.getId().getTag("outcome");
            String server = t.getId().getTag("server");
            if (kind == null) return;
            if (McpClientService.SELF_LOOPBACK_SERVER_NAME.equals(server)) return;
            long count = (long) t.count();
            double totalMs = t.totalTime(TimeUnit.MILLISECONDS);
            double maxMs = t.max(TimeUnit.MILLISECONDS);
            s.mcpPrimitiveCountByKind.merge(kind, count, Long::sum);
            TimerSummary kindSummary = s.mcpPrimitiveTimerByKind.computeIfAbsent(kind, k -> new TimerSummary());
            kindSummary.count += count;
            kindSummary.totalMs += totalMs;
            if (maxMs > kindSummary.maxMs) kindSummary.maxMs = maxMs;
            kindSummary.meanMs = kindSummary.count == 0 ? 0 : kindSummary.totalMs / kindSummary.count;
            if (outcome != null) {
                s.mcpPrimitiveOutcomeCounts.merge(outcome, count, Long::sum);
            }
            if (server != null) {
                s.mcpPrimitiveCountByServer.merge(server, count, Long::sum);
            }
        });

        registry.find("mcp.server.handler").counters().forEach(c -> {
            String kind = c.getId().getTag("kind");
            String outcome = c.getId().getTag("outcome");
            if (kind == null || outcome == null) return;
            s.mcpServerHandlerCounts.merge(kind + " / " + outcome, (long) c.count(), Long::sum);
        });

        registry.find("mcp.oauth.authorized").counters().forEach(c ->
                s.mcpOAuthAuthorizedTotal += (long) c.count());

        return s;
    }

    private double sumLongTaskTimerActive(String name) {
        double sum = 0;
        for (LongTaskTimer t : registry.find(name).longTaskTimers()) {
            sum += t.activeTasks();
        }
        return sum;
    }

    private double firstFunctionCounterValue(String name) {
        for (Meter m : registry.find(name).meters()) {
            if (m instanceof FunctionCounter fc) {
                return fc.count();
            }
            if (m instanceof Gauge g) {
                return g.value();
            }
        }
        return 0;
    }

    private void captureTimerSummary(MeterRegistry registry, String name, TimerSummary out) {
        Timer t = registry.find(name).timer();
        if (t == null) return;
        out.count = t.count();
        out.totalMs = t.totalTime(TimeUnit.MILLISECONDS);
        out.maxMs = t.max(TimeUnit.MILLISECONDS);
        out.meanMs = t.count() == 0 ? 0 : out.totalMs / t.count();
    }

    private double firstGauge(String name) {
        Search search = registry.find(name);
        Gauge g = search.gauge();
        return g == null ? 0 : g.value();
    }

    private double sumGauge(String name, Tags tags) {
        double sum = 0;
        for (Gauge g : registry.find(name).tags(tags).gauges()) {
            sum += g.value();
        }
        return sum;
    }

    public static class Snapshot {
        // JVM memory
        public double jvmHeapUsedBytes;
        public double jvmHeapMaxBytes;
        public double jvmHeapCommittedBytes;
        public double jvmNonHeapUsedBytes;
        public double jvmNonHeapCommittedBytes;
        public final Map<String, Long> jvmHeapPoolUsed = new LinkedHashMap<>();
        public final Map<String, Long> jvmBufferCount = new LinkedHashMap<>();
        public final Map<String, Long> jvmBufferUsedBytes = new LinkedHashMap<>();

        // Threads
        public long jvmThreadsLive;
        public long jvmThreadsDaemon;
        public long jvmThreadsPeak;
        public long jvmThreadsStarted;
        public final Map<String, Long> jvmThreadsByState = new LinkedHashMap<>();

        // GC
        public final Map<String, Double> gcPauseSecondsSum = new LinkedHashMap<>();
        public final Map<String, Long> gcPauseCount = new LinkedHashMap<>();
        public final Map<String, Double> gcPauseMaxMs = new LinkedHashMap<>();
        public long gcLiveDataBytes;
        public long gcMaxDataBytes;
        public long gcMemoryAllocatedBytes;
        public long gcMemoryPromotedBytes;
        public double gcOverheadFraction;

        // Classes
        public long jvmClassesLoaded;
        public long jvmClassesUnloaded;

        // OS / Process
        public double processCpuUsage;
        public double systemCpuUsage;
        public long systemCpuCount;
        public double systemLoadAverage1m;
        public double processUptimeSeconds;
        public double processStartTimeSeconds;
        public double applicationReadySeconds;
        public double applicationStartedSeconds;
        public long processFilesOpen;
        public long processFilesMax;

        // Disk
        public long diskFreeBytes;
        public long diskTotalBytes;

        // HTTP
        public final Map<String, Long> httpRequestsByStatus = new LinkedHashMap<>();
        public long httpRequestsTotal;
        public double httpRequestsTotalTimeSeconds;

        // Tomcat
        public long tomcatSessionsActive;
        public long tomcatSessionsActiveMax;
        public long tomcatSessionsCreated;
        public long tomcatSessionsExpired;
        public long tomcatSessionsRejected;

        // Logback
        public final Map<String, Long> logbackEventsByLevel = new LinkedHashMap<>();

        // Spring AI in-flight
        public long activeChatClient;
        public long activeChatModel;
        public long activeAdvisor;
        public long activeVector;
        public long activeChatClientStream;

        // jvm.info — vendor/runtime/version (value=1, all info in tags)
        public final Map<String, String> jvmInfo = new LinkedHashMap<>();

        // jvm.compilation.time — cumulative JIT compile time
        public double jvmCompilationTimeMs;

        // jvm.memory.usage.after.gc — % per pool retained after GC (leak signal)
        public final Map<String, Double> jvmMemoryUsageAfterGc = new LinkedHashMap<>();

        // jvm.gc.concurrent.phase.time — G1 concurrent phases (background)
        public final Map<String, Double> gcConcurrentPhaseSecondsSum = new LinkedHashMap<>();

        // jvm.buffer.total.capacity — per buffer pool
        public final Map<String, Long> jvmBufferTotalCapacity = new LinkedHashMap<>();

        // HTTP in-flight (LongTaskTimer ACTIVE_TASKS)
        public long httpServerActive;
        public long httpClientActive;

        // http.client.requests aggregated by client.name / host
        public final Map<String, HostStats> httpClientByHost = new LinkedHashMap<>();

        // tomcat.sessions.alive.max — longest session lifetime (seconds)
        public long tomcatSessionsAliveMax;

        // process.cpu.time — cumulative CPU ns
        public long processCpuTimeNs;

        // Spring AI streaming / embedding / image Timer summaries
        public final TimerSummary genAiTtft = new TimerSummary();
        public final TimerSummary genAiPerChunk = new TimerSummary();
        public final TimerSummary genAiEmbeddingDuration = new TimerSummary();
        public final TimerSummary genAiImageDuration = new TimerSummary();

        // Playground sandbox guard blocks (category/reason → count, plus
        // collapsed by category for top-level KPI)
        public final Map<String, Long> sandboxGuardBlocked = new LinkedHashMap<>();
        public final Map<String, Long> sandboxGuardBlockedByCategory = new LinkedHashMap<>();

        // Playground MCP server lifecycle counters (connect/disconnect/error/awaiting_auth)
        public final Map<String, Long> mcpLifecycleByOutcome = new LinkedHashMap<>();
        public final Map<String, Long> mcpLifecycleByServer = new LinkedHashMap<>();

        // Playground MCP primitive timers (kind → count / TimerSummary, plus per-server / outcome)
        public final Map<String, Long> mcpPrimitiveCountByKind = new LinkedHashMap<>();
        public final Map<String, TimerSummary> mcpPrimitiveTimerByKind = new LinkedHashMap<>();
        public final Map<String, Long> mcpPrimitiveOutcomeCounts = new LinkedHashMap<>();
        public final Map<String, Long> mcpPrimitiveCountByServer = new LinkedHashMap<>();

        // Playground MCP server-initiated handlers (sampling / elicitation)
        public final Map<String, Long> mcpServerHandlerCounts = new LinkedHashMap<>();

        // Playground MCP OAuth lifetime counter (across all servers)
        public long mcpOAuthAuthorizedTotal;
    }

    public static class HostStats {
        public long count;
        public double totalTimeSeconds;
        public double maxMs;
        public double avgMs() {
            return count == 0 ? 0 : (totalTimeSeconds * 1000.0) / count;
        }
    }

    public static class TimerSummary {
        public long count;
        public double totalMs;
        public double maxMs;
        public double meanMs;
    }
}
