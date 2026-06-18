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

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.PersistenceExecutor;
import org.springaicommunity.playground.service.mcp.catalog.McpToolDescriptor;

import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolHashLedgerTest {

    @TempDir
    Path tempHome;

    private McpToolHashLedger ledger;
    private PersistenceExecutor executor;
    private AtomicReference<McpRiskEvents.HashLedgerMismatch> captured;
    private ObjectMapper objectMapper;
    private McpRiskSignalSink sink;

    @BeforeEach
    void setUp() throws IOException {
        this.objectMapper = new ObjectMapper();
        this.executor = new PersistenceExecutor();
        this.captured = new AtomicReference<>();
        this.sink = new McpRiskSignalSink() {
            @Override public void onServerRiskComputed(McpRiskEvents.ServerRiskComputed event) {}
            @Override public void onToolPublishRiskComputed(McpRiskEvents.ToolPublishRiskComputed event) {}
            @Override public void onFloorOverrideTriggered(McpRiskEvents.FloorOverrideTriggered event) {}
            @Override public void onHashLedgerMismatch(McpRiskEvents.HashLedgerMismatch event) {
                captured.set(event);
            }
            @Override public void onCompositionLifecycle(McpRiskEvents.CompositionLifecycle event) {}
            @Override public void onPoisoningHit(McpRiskEvents.PoisoningHit event) {}
        };
        this.ledger = new McpToolHashLedger(tempHome, objectMapper, executor, sink);
    }

    @AfterEach
    void drainPersistence() throws InterruptedException, TimeoutException {
        executor.awaitCompletion(Duration.ofSeconds(5));
    }

    @Test
    void corruptLedgerFileStartsEmptyInsteadOfCrashing() throws IOException {
        Path ledgerFile = tempHome.resolve("mcp").resolve("risk").resolve("ledger.json");
        Files.createDirectories(ledgerFile.getParent());
        Files.writeString(ledgerFile, "{ not a fingerprint array ]]]");
        McpToolHashLedger recovered = assertDoesNotThrow(
                () -> new McpToolHashLedger(tempHome, objectMapper, executor, sink));
        assertTrue(recovered.get("github", "list_repos").isEmpty());
    }

    @Test
    void contentHashIsStableForSameInputs() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations("List Repos", true, false, true, true);
        String hash1 = ledger.computeContentHash("list_repos", "Lists repositories", null, annotations);
        String hash2 = ledger.computeContentHash("list_repos", "Lists repositories", null, annotations);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length(), "SHA-256 hex length");
    }

    @Test
    void contentHashChangesWhenDescriptionChanges() {
        McpToolDescriptor.Annotations annotations = new McpToolDescriptor.Annotations(null, true, false, true, true);
        String hash1 = ledger.computeContentHash("tool", "version 1 description", null, annotations);
        String hash2 = ledger.computeContentHash("tool", "version 2 description", null, annotations);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void firstSeenIsRecordedAsNew() {
        McpToolHashLedger.CheckResult result = ledger.checkAndRecord("github", "list_repos", "hash-1");
        assertEquals(McpToolHashLedger.CheckResult.Status.NEW, result.status());
        assertNull(result.stored());
        assertNotNull(result.current());
        assertEquals("hash-1", result.current().contentHash());
        assertEquals(McpToolHashLedger.Fingerprint.LifecycleStatus.ACTIVE, result.current().status());
    }

    @Test
    void sameHashAfterFirstSeenReturnsUnchanged() {
        ledger.checkAndRecord("github", "list_repos", "hash-1");
        McpToolHashLedger.CheckResult result = ledger.checkAndRecord("github", "list_repos", "hash-1");
        assertEquals(McpToolHashLedger.CheckResult.Status.UNCHANGED, result.status());
        assertNull(captured.get(), "no mismatch event emitted for unchanged");
    }

    @Test
    void hashChangeMarksAwaitingRereviewAndEmits() {
        ledger.checkAndRecord("github", "list_repos", "hash-1");
        McpToolHashLedger.CheckResult result = ledger.checkAndRecord("github", "list_repos", "hash-2");
        assertEquals(McpToolHashLedger.CheckResult.Status.MISMATCH, result.status());
        assertTrue(result.isBlocking());
        assertEquals(McpToolHashLedger.Fingerprint.LifecycleStatus.AWAITING_REREVIEW, result.current().status());
        McpRiskEvents.HashLedgerMismatch event = captured.get();
        assertNotNull(event, "mismatch event must be emitted");
        assertEquals("hash-1", event.previousHash());
        assertEquals("hash-2", event.currentHash());
    }

    @Test
    void approveRereviewRestoresActiveStatus() {
        ledger.checkAndRecord("github", "list_repos", "hash-1");
        ledger.checkAndRecord("github", "list_repos", "hash-2");
        ledger.approveRereview("github", "list_repos", "hash-2");
        McpToolHashLedger.Fingerprint fp = ledger.get("github", "list_repos").orElseThrow();
        assertEquals(McpToolHashLedger.Fingerprint.LifecycleStatus.ACTIVE, fp.status());
        assertEquals("hash-2", fp.contentHash());
    }

    @Test
    void persistsToDiskAndReloadsOnInit() throws Exception {
        ledger.checkAndRecord("github", "list_repos", "hash-1");
        ledger.checkAndRecord("notion", "read_page", "hash-2");
        executor.awaitCompletion(Duration.ofSeconds(5));

        McpToolHashLedger reopened = new McpToolHashLedger(tempHome, objectMapper, executor,
                McpRiskSignalSink.NOOP);
        assertEquals(2, reopened.snapshot().size());
        assertEquals("hash-1", reopened.get("github", "list_repos").orElseThrow().contentHash());
        assertEquals("hash-2", reopened.get("notion", "read_page").orElseThrow().contentHash());
    }
}
