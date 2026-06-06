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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DefaultIntegrityVerifier {

    public static final String TOOL_INTEGRITY_SOURCE = "tool-studio:default";

    private static final Logger logger = LoggerFactory.getLogger(DefaultIntegrityVerifier.class);

    public enum Status { PASS_UNPINNED, PASS_PRISTINE, REVOKED_TAMPERED }

    public record ToolVerdict(Status status, String toolName, String actualHash) {

        public boolean isBlocked() {
            return status == Status.REVOKED_TAMPERED;
        }

        public boolean isReservedDefault() {
            return status != Status.PASS_UNPINNED;
        }
    }

    private final CanonicalHasher hasher;
    private final McpRiskSignalSink sink;
    private final ObjectProvider<ToolSpecPersistenceService> toolPersistenceProvider;
    private volatile Map<String, Set<String>> reservedToolHashes;

    public DefaultIntegrityVerifier(CanonicalHasher hasher, McpRiskSignalSink sink,
            ObjectProvider<ToolSpecPersistenceService> toolPersistenceProvider) {
        this.hasher = hasher;
        this.sink = sink;
        this.toolPersistenceProvider = toolPersistenceProvider;
    }

    public boolean isReservedToolName(String name) {
        return name != null && reservedToolHashes().containsKey(name);
    }

    public ToolVerdict verifyTool(ToolSpec spec) {
        String name = spec == null ? null : spec.name();
        Set<String> expected = name == null ? null : reservedToolHashes().get(name);
        if (expected == null) {
            return new ToolVerdict(Status.PASS_UNPINNED, name, null);
        }
        String actual = this.hasher.hashTool(spec);
        if (expected.contains(actual)) {
            return new ToolVerdict(Status.PASS_PRISTINE, name, actual);
        }
        this.sink.onHashLedgerMismatch(new McpRiskEvents.HashLedgerMismatch(
                Instant.now(), TOOL_INTEGRITY_SOURCE, name, expected.iterator().next(), actual, List.of()));
        logger.warn("Default tool integrity REVOKED: '{}' uses a reserved default name but its content hash {} "
                + "does not match the shipped baseline, refusing exposure", name, actual);
        return new ToolVerdict(Status.REVOKED_TAMPERED, name, actual);
    }

    private Map<String, Set<String>> reservedToolHashes() {
        Map<String, Set<String>> local = this.reservedToolHashes;
        if (local != null) return local;
        synchronized (this) {
            if (this.reservedToolHashes != null) return this.reservedToolHashes;
            this.reservedToolHashes = buildToolBaseline();
            return this.reservedToolHashes;
        }
    }

    private Map<String, Set<String>> buildToolBaseline() {
        ToolSpecPersistenceService persistence = this.toolPersistenceProvider.getIfAvailable();
        Map<String, Set<String>> baseline = new HashMap<>();
        if (persistence == null) {
            logger.warn("Default tool baseline unavailable — tool integrity gate will pass everything");
            return baseline;
        }
        for (ToolSpec spec : persistence.getDefaultToolSpecs()) {
            if (spec == null || spec.name() == null || spec.name().isBlank()) continue;
            baseline.computeIfAbsent(spec.name(), k -> new HashSet<>()).add(this.hasher.hashTool(spec));
        }
        logger.info("Default tool integrity baseline pinned: {} reserved tool names", baseline.size());
        return baseline;
    }
}
