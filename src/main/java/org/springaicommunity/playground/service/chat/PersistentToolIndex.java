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
package org.springaicommunity.playground.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PersistentToolIndex implements ToolIndex {

    private static final Logger logger = LoggerFactory.getLogger(PersistentToolIndex.class);
    private static final double SIMILARITY_THRESHOLD = 0.2;
    private static final int DEFAULT_MAX_RESULTS = 10;
    private static final String META_TOOL_NAME = "toolName";
    private static final String META_TOOL_DESCRIPTION = "toolDescription";

    private final SimpleVectorStore store;
    private final boolean exactNameEnabled;
    private final boolean persistEnabled;
    private final Path embeddingsFile;
    private final Path metaFile;
    private final String signature;
    private final Map<String, ToolReference> byName = new ConcurrentHashMap<>();
    private final Set<String> indexedHashes = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    public PersistentToolIndex(SimpleVectorStore store, String embeddingSignature, Path baseDir,
            boolean exactNameEnabled, boolean persistEnabled) {
        this.store = store;
        this.exactNameEnabled = exactNameEnabled;
        this.persistEnabled = persistEnabled;
        this.signature = embeddingSignature;
        Path dir = baseDir.resolve("tool-index");
        this.embeddingsFile = dir.resolve("embeddings.json");
        this.metaFile = dir.resolve("index.meta");
        if (persistEnabled) {
            loadFromDisk();
        }
    }

    @Override
    public void indexTool(String sessionId, ToolReference reference) {
        synchronized (this.lock) {
            this.byName.put(reference.toolName().toLowerCase(Locale.ROOT), reference);
            String hash = contentHash(reference);
            if (this.indexedHashes.contains(hash)) {
                return;
            }
            this.store.add(List.of(toDocument(hash, reference)));
            this.indexedHashes.add(hash);
            persist();
        }
    }

    @Override
    public void indexTools(String sessionId, List<ToolReference> references) {
        synchronized (this.lock) {
            Map<String, ToolReference> desired = new LinkedHashMap<>();
            for (ToolReference reference : references) {
                desired.put(contentHash(reference), reference);
            }
            this.byName.clear();
            references.forEach(reference -> this.byName.put(reference.toolName().toLowerCase(Locale.ROOT), reference));

            Set<String> stale = new HashSet<>(this.indexedHashes);
            stale.removeAll(desired.keySet());
            List<Document> toEmbed = new ArrayList<>();
            desired.forEach((hash, reference) -> {
                if (!this.indexedHashes.contains(hash)) {
                    toEmbed.add(toDocument(hash, reference));
                }
            });

            boolean changed = false;
            if (!stale.isEmpty()) {
                this.store.delete(new ArrayList<>(stale));
                this.indexedHashes.removeAll(stale);
                changed = true;
            }
            if (!toEmbed.isEmpty()) {
                this.store.add(toEmbed);
                toEmbed.forEach(document -> this.indexedHashes.add(document.getId()));
                changed = true;
            }
            if (changed) {
                logger.info("Tool index updated: +{} embedded, -{} removed, {} total", toEmbed.size(), stale.size(),
                        this.indexedHashes.size());
                persist();
            }
        }
    }

    @Override
    public ToolSearchResponse search(ToolSearchRequest toolSearchRequest) {
        String query = toolSearchRequest.query() == null ? "" : toolSearchRequest.query().trim();
        if (this.exactNameEnabled) {
            ToolReference exact = this.byName.get(query.toLowerCase(Locale.ROOT));
            if (exact != null) {
                return ToolSearchResponse.builder().toolReferences(List.of(exact)).totalMatches(1).build();
            }
        }
        int maxResults = toolSearchRequest.maxResults() != null ? toolSearchRequest.maxResults() : DEFAULT_MAX_RESULTS;
        List<Document> documents = this.store.similaritySearch(SearchRequest.builder().query(query).topK(maxResults)
                .similarityThreshold(SIMILARITY_THRESHOLD).build());
        List<ToolReference> toolReferences = (documents == null ? List.<Document>of() : documents).stream()
                .map(document -> ToolReference.builder()
                        .toolName((String) document.getMetadata().get(META_TOOL_NAME))
                        .summary((String) document.getMetadata().get(META_TOOL_DESCRIPTION))
                        .relevanceScore(Objects.requireNonNullElse(document.getScore(), 0.0)).build())
                .toList();
        return ToolSearchResponse.builder().toolReferences(toolReferences).totalMatches(toolReferences.size()).build();
    }

    @Override
    public void clearIndex(String sessionId) {
    }

    private static Document toDocument(String hash, ToolReference reference) {
        return new Document(hash, reference.summary(),
                Map.of(META_TOOL_NAME, reference.toolName(), META_TOOL_DESCRIPTION, reference.summary()));
    }

    private static String contentHash(ToolReference reference) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(reference.toolName().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(reference.summary().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void loadFromDisk() {
        synchronized (this.lock) {
            try {
                if (!Files.exists(this.metaFile) || !Files.exists(this.embeddingsFile)) {
                    return;
                }
                List<String> lines = Files.readAllLines(this.metaFile, StandardCharsets.UTF_8);
                if (lines.isEmpty() || !this.signature.equals(lines.get(0))) {
                    logger.info("Tool index embedding signature changed; re-embedding from scratch");
                    return;
                }
                this.store.load(this.embeddingsFile.toFile());
                this.indexedHashes.addAll(lines.subList(1, lines.size()));
                logger.info("Loaded {} persisted tool embeddings", this.indexedHashes.size());
            } catch (Exception e) {
                logger.warn("Failed to load persisted tool index ({}); re-embedding from scratch", e.getMessage());
                this.indexedHashes.clear();
            }
        }
    }

    private void persist() {
        if (!this.persistEnabled) {
            return;
        }
        try {
            Files.createDirectories(this.embeddingsFile.getParent());
            this.store.save(this.embeddingsFile.toFile());
            List<String> lines = new ArrayList<>(this.indexedHashes.size() + 1);
            lines.add(this.signature);
            lines.addAll(this.indexedHashes);
            Files.write(this.metaFile, lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.warn("Failed to persist tool index ({})", e.getMessage());
        }
    }

    public static String signatureOf(String embeddingModelClass, String modelName, Integer dimensions) {
        return embeddingModelClass + "|" + (StringUtils.hasText(modelName) ? modelName : "")
                + "|" + (dimensions == null ? "" : dimensions);
    }
}
