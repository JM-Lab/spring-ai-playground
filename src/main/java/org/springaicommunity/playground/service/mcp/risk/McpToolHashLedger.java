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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpToolHashLedger {

    private static final Logger logger = LoggerFactory.getLogger(McpToolHashLedger.class);
    private static final String LEDGER_FILE = "ledger.json";

    private final Path ledgerPath;
    private final ObjectMapper objectMapper;
    private final CanonicalHasher canonicalHasher;
    private final PersistenceExecutor persistenceExecutor;
    private final McpRiskSignalSink sink;
    private final Map<String, Fingerprint> fingerprintsByKey;

    public McpToolHashLedger(Path springAiPlaygroundHomeDir, ObjectMapper objectMapper,
            CanonicalHasher canonicalHasher, PersistenceExecutor persistenceExecutor,
            McpRiskSignalSink sink) throws IOException {
        Path baseDir = springAiPlaygroundHomeDir.resolve("mcp").resolve("risk");
        Files.createDirectories(baseDir);
        this.ledgerPath = baseDir.resolve(LEDGER_FILE);
        this.objectMapper = objectMapper.rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
        this.canonicalHasher = canonicalHasher;
        this.persistenceExecutor = persistenceExecutor;
        this.sink = sink;
        this.fingerprintsByKey = new ConcurrentHashMap<>(loadAll());
        logger.info("Loaded {} tool fingerprints from {}", this.fingerprintsByKey.size(), this.ledgerPath);
    }

    public String computeContentHash(String name, String description, JsonNode inputSchema,
            McpToolDescriptor.Annotations annotations) {
        return this.canonicalHasher.hashMcpTool(name, description, inputSchema, annotations);
    }

    public Optional<Fingerprint> get(String serverId, String toolName) {
        return Optional.ofNullable(this.fingerprintsByKey.get(Fingerprint.keyOf(serverId, toolName)));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fingerprint(
            String serverId,
            String toolName,
            String contentHash,
            long firstSeenAtEpochMs,
            Long curatedAtEpochMs,
            Long publishedAtEpochMs,
            LifecycleStatus status) {

        public enum LifecycleStatus { ACTIVE, AWAITING_REREVIEW, REVOKED }

        public Fingerprint {
            if (status == null) status = LifecycleStatus.ACTIVE;
        }

        public String key() {
            return keyOf(serverId, toolName);
        }

        public static String keyOf(String serverId, String toolName) {
            return serverId + "::" + toolName;
        }
    }

    public record CheckResult(Fingerprint stored, Fingerprint current, Status status) {

        public enum Status { NEW, UNCHANGED, MISMATCH }

        public boolean isBlocking() {
            return status == Status.MISMATCH;
        }
    }

    public synchronized CheckResult checkAndRecord(String serverId, String toolName, String contentHash) {
        String key = Fingerprint.keyOf(serverId, toolName);
        Fingerprint existing = this.fingerprintsByKey.get(key);
        long now = Instant.now().toEpochMilli();
        if (existing == null) {
            Fingerprint fresh = new Fingerprint(serverId, toolName, contentHash,
                    now, null, null, Fingerprint.LifecycleStatus.ACTIVE);
            this.fingerprintsByKey.put(key, fresh);
            persist();
            return new CheckResult(null, fresh, CheckResult.Status.NEW);
        }
        if (existing.contentHash().equals(contentHash)) {
            return new CheckResult(existing, existing, CheckResult.Status.UNCHANGED);
        }
        Fingerprint mismatched = new Fingerprint(serverId, toolName, contentHash,
                existing.firstSeenAtEpochMs(), existing.curatedAtEpochMs(), existing.publishedAtEpochMs(),
                Fingerprint.LifecycleStatus.AWAITING_REREVIEW);
        this.fingerprintsByKey.put(key, mismatched);
        persist();
        this.sink.onHashLedgerMismatch(new McpRiskEvents.HashLedgerMismatch(
                Instant.now(), serverId, toolName, existing.contentHash(), contentHash, List.of()));
        return new CheckResult(existing, mismatched, CheckResult.Status.MISMATCH);
    }

    public synchronized void approveRereview(String serverId, String toolName, String approvedHash) {
        String key = Fingerprint.keyOf(serverId, toolName);
        Fingerprint existing = this.fingerprintsByKey.get(key);
        if (existing == null) return;
        long now = Instant.now().toEpochMilli();
        Fingerprint approved = new Fingerprint(serverId, toolName, approvedHash,
                existing.firstSeenAtEpochMs(), now, existing.publishedAtEpochMs(),
                Fingerprint.LifecycleStatus.ACTIVE);
        this.fingerprintsByKey.put(key, approved);
        persist();
    }

    public synchronized void approveRereview(String serverId, String toolName) {
        Fingerprint existing = this.fingerprintsByKey.get(Fingerprint.keyOf(serverId, toolName));
        if (existing == null) return;
        approveRereview(serverId, toolName, existing.contentHash());
    }

    public synchronized void markPublished(String serverId, String toolName) {
        String key = Fingerprint.keyOf(serverId, toolName);
        Fingerprint existing = this.fingerprintsByKey.get(key);
        if (existing == null) return;
        long now = Instant.now().toEpochMilli();
        Fingerprint updated = new Fingerprint(serverId, toolName, existing.contentHash(),
                existing.firstSeenAtEpochMs(), existing.curatedAtEpochMs(), now, existing.status());
        this.fingerprintsByKey.put(key, updated);
        persist();
    }

    public List<Fingerprint> snapshot() {
        return new ArrayList<>(this.fingerprintsByKey.values());
    }

    private void persist() {
        List<Fingerprint> snapshot = snapshot();
        this.persistenceExecutor.submit(() -> {
            try {
                writeAtomically(snapshot);
            } catch (IOException | JacksonException e) {
                logger.error("Failed to persist hash ledger", e);
            }
        });
    }

    private void writeAtomically(List<Fingerprint> snapshot) throws IOException {
        Path tmp = this.ledgerPath.resolveSibling(LEDGER_FILE + ".tmp");
        byte[] bytes = this.objectMapper.writeValueAsBytes(snapshot);
        Files.write(tmp, bytes);
        Files.move(tmp, this.ledgerPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Map<String, Fingerprint> loadAll() {
        if (!Files.exists(this.ledgerPath)) return Map.of();
        try {
            byte[] bytes = Files.readAllBytes(this.ledgerPath);
            if (bytes.length == 0) return Map.of();
            List<Fingerprint> loaded = this.objectMapper.readValue(bytes,
                    new TypeReference<List<Fingerprint>>() {});
            Map<String, Fingerprint> map = new LinkedHashMap<>();
            for (Fingerprint fp : loaded) {
                if (fp == null || fp.serverId() == null || fp.toolName() == null) continue;
                map.put(fp.key(), fp);
            }
            return map;
        } catch (IOException | JacksonException e) {
            logger.warn("Failed to load hash ledger from {} — starting empty", this.ledgerPath, e);
            return Map.of();
        }
    }
}
