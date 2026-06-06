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
package org.springaicommunity.playground.service.mcp.client;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Service
public class McpNotificationStore {

    public enum Kind {
        LOGGING, PROGRESS,
        TOOLS_CHANGED, RESOURCES_CHANGED, PROMPTS_CHANGED,
        SAMPLING_REQUEST, ELICITATION_REQUEST,
        SAMPLING_RESPONDED, ELICITATION_RESPONDED
    }

    public record Event(String id, LocalDateTime timestamp, Kind kind, String summary, Object payload) {
        public static Event of(Kind kind, String summary, Object payload) {
            return new Event(UUID.randomUUID().toString(), LocalDateTime.now(), kind, summary, payload);
        }
    }

    public record PendingSampling(String id, McpSchema.CreateMessageRequest request,
            CompletableFuture<McpSchema.CreateMessageResult> future) {}

    public record PendingElicitation(String id, McpSchema.ElicitRequest request,
            CompletableFuture<McpSchema.ElicitResult> future) {}

    private static final int MAX_EVENTS_PER_SERVER = 500;

    private final Map<String, Deque<Event>> events = new ConcurrentHashMap<>();
    private final Map<String, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PendingSampling>> pendingSamplings = new ConcurrentHashMap<>();
    private final Map<String, Map<String, PendingElicitation>> pendingElicitations = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> pendingChangeListeners = new ConcurrentHashMap<>();

    public void record(String serverKey, Event event) {
        Deque<Event> q = events.computeIfAbsent(serverKey, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addFirst(event);
            while (q.size() > MAX_EVENTS_PER_SERVER) q.pollLast();
        }
        List<Consumer<Event>> ls = listeners.get(serverKey);
        if (ls != null) {
            for (Consumer<Event> l : ls) {
                try { l.accept(event); } catch (RuntimeException ignore) {}
            }
        }
    }

    public List<Event> snapshot(String serverKey) {
        Deque<Event> q = events.get(serverKey);
        if (q == null) return List.of();
        synchronized (q) {
            return new ArrayList<>(q);
        }
    }

    public void clear(String serverKey) {
        Deque<Event> q = events.get(serverKey);
        if (q != null) synchronized (q) { q.clear(); }
    }

    public Runnable subscribe(String serverKey, Consumer<Event> listener) {
        List<Consumer<Event>> ls =
                listeners.computeIfAbsent(serverKey, k -> new CopyOnWriteArrayList<>());
        ls.add(listener);
        return () -> ls.remove(listener);
    }

    public Runnable subscribePendingChange(String serverKey, Runnable listener) {
        List<Runnable> ls = pendingChangeListeners.computeIfAbsent(serverKey,
                k -> new CopyOnWriteArrayList<>());
        ls.add(listener);
        return () -> ls.remove(listener);
    }

    private void firePendingChange(String serverKey) {
        List<Runnable> ls = pendingChangeListeners.get(serverKey);
        if (ls != null) for (Runnable l : ls) {
            try { l.run(); } catch (RuntimeException ignore) {}
        }
    }

    public CompletableFuture<McpSchema.CreateMessageResult> awaitSamplingResponse(String serverKey,
            McpSchema.CreateMessageRequest request) {
        CompletableFuture<McpSchema.CreateMessageResult> future = new CompletableFuture<>();
        String id = UUID.randomUUID().toString();
        pendingSamplings.computeIfAbsent(serverKey, k -> new LinkedHashMap<>())
                .put(id, new PendingSampling(id, request, future));
        record(serverKey, Event.of(Kind.SAMPLING_REQUEST,
                "Sampling request received (" + (request.messages() == null ? 0 : request.messages().size())
                        + " messages)", request));
        firePendingChange(serverKey);
        return future;
    }

    public CompletableFuture<McpSchema.ElicitResult> awaitElicitationResponse(String serverKey,
            McpSchema.ElicitRequest request) {
        CompletableFuture<McpSchema.ElicitResult> future = new CompletableFuture<>();
        String id = UUID.randomUUID().toString();
        pendingElicitations.computeIfAbsent(serverKey, k -> new LinkedHashMap<>())
                .put(id, new PendingElicitation(id, request, future));
        // Drop on settle so an abandoned elicitation does not re-render as a stale prompt on reattach.
        future.whenComplete((result, error) -> discardPendingElicitation(serverKey, id));
        record(serverKey, Event.of(Kind.ELICITATION_REQUEST,
                "Elicitation request: " + request.message(), request));
        firePendingChange(serverKey);
        return future;
    }

    private void discardPendingElicitation(String serverKey, String id) {
        Map<String, PendingElicitation> m = pendingElicitations.get(serverKey);
        if (m == null) return;
        boolean removed;
        synchronized (m) {
            removed = m.remove(id) != null;
        }
        if (removed) firePendingChange(serverKey);
    }

    public List<PendingSampling> snapshotPendingSamplings(String serverKey) {
        Map<String, PendingSampling> m = pendingSamplings.get(serverKey);
        if (m == null) return List.of();
        synchronized (m) { return new ArrayList<>(m.values()); }
    }

    public List<PendingElicitation> snapshotPendingElicitations(String serverKey) {
        Map<String, PendingElicitation> m = pendingElicitations.get(serverKey);
        if (m == null) return List.of();
        synchronized (m) { return new ArrayList<>(m.values()); }
    }

    public void completeSampling(String serverKey, String pendingId, McpSchema.CreateMessageResult result) {
        Map<String, PendingSampling> m = pendingSamplings.get(serverKey);
        if (m == null) return;
        PendingSampling p;
        synchronized (m) { p = m.remove(pendingId); }
        if (p != null) {
            p.future().complete(result);
            record(serverKey, Event.of(Kind.SAMPLING_RESPONDED,
                    "Sampling responded (" + result.role() + ")", result));
            firePendingChange(serverKey);
        }
    }

    public void completeElicitation(String serverKey, String pendingId, McpSchema.ElicitResult result) {
        Map<String, PendingElicitation> m = pendingElicitations.get(serverKey);
        if (m == null) return;
        PendingElicitation p;
        synchronized (m) { p = m.remove(pendingId); }
        if (p != null) {
            p.future().complete(result);
            record(serverKey, Event.of(Kind.ELICITATION_RESPONDED,
                    "Elicitation responded (" + result.action() + ")", result));
            firePendingChange(serverKey);
        }
    }

    public void removeServer(String serverKey) {
        events.remove(serverKey);
        listeners.remove(serverKey);
        Map<String, PendingSampling> ps = pendingSamplings.remove(serverKey);
        if (ps != null) ps.values().forEach(p -> p.future().cancel(true));
        Map<String, PendingElicitation> pe = pendingElicitations.remove(serverKey);
        if (pe != null) pe.values().forEach(p -> p.future().cancel(true));
        pendingChangeListeners.remove(serverKey);
    }
}
