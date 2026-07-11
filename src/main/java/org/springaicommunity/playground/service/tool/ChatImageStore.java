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
package org.springaicommunity.playground.service.tool;

import org.springaicommunity.playground.service.tool.runtime.SafeFs;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class ChatImageStore {

    public static final String IMAGES_DIR = "images";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolWorkspace workspace;

    public ChatImageStore(ToolWorkspace workspace) {
        this.workspace = workspace;
    }

    public Stored store(String conversationId, byte[] content, String originalFileName, String mimeType,
            String exifJson) throws IOException {
        byte[] data = content == null ? new byte[0] : content;
        String hash = AttachmentHashing.sha256Hex(data);
        String imagePath = IMAGES_DIR + "/" + hash + "." + extensionFor(mimeType);
        SafeFs.FsScope scope = this.workspace.scopeFor(conversationId);
        boolean deduped = SafeFs.exists(scope, imagePath);
        if (!deduped) {
            SafeFs.writeBytes(scope, imagePath, data);
            SafeFs.writeText(scope, IMAGES_DIR + "/" + hash + ".json",
                    buildMeta(hash, originalFileName, mimeType, data.length, exifJson));
        }
        return new Stored(imagePath, hash, originalFileName, mimeType, data.length, deduped);
    }

    public Optional<Loaded> load(String conversationId, String ref) throws IOException {
        if (ref == null || ref.isBlank()) return Optional.empty();
        SafeFs.FsScope scope = this.workspace.scopeFor(conversationId);
        if (!SafeFs.exists(scope, IMAGES_DIR)) return Optional.empty();
        String matchHash = resolveHash(conversationId, ref);
        if (matchHash == null) return Optional.empty();
        for (String entry : SafeFs.list(scope, IMAGES_DIR)) {
            String name = entry.substring(entry.lastIndexOf('/') + 1);
            if (name.endsWith(".json") || !name.startsWith(matchHash + ".")) continue;
            return Optional.of(new Loaded(SafeFs.readBytes(scope, entry), mimeForFile(name), matchHash));
        }
        return Optional.empty();
    }

    private String resolveHash(String conversationId, String ref) throws IOException {
        String normalized = AttachmentHashing.normalizeRef(ref);
        String rawName = ref.trim();
        List<ImageRef> images = list(conversationId);
        for (ImageRef image : images)
            if (image.hash().equals(normalized)) return image.hash();
        String byName = uniqueMatch(images, image -> rawName.equalsIgnoreCase(image.fileName()));
        if (byName != null) return byName;
        String byBaseName = uniqueMatch(images, image -> image.fileName() != null
                && stripExtension(image.fileName()).equalsIgnoreCase(stripExtension(rawName)));
        if (byBaseName != null) return byBaseName;
        return uniqueMatch(images, image -> normalized.length() >= 6 && image.hash().startsWith(normalized));
    }

    private static String uniqueMatch(List<ImageRef> images, Predicate<ImageRef> test) {
        String found = null;
        for (ImageRef image : images) {
            if (!test.test(image)) continue;
            if (found != null && !found.equals(image.hash())) return null;
            found = image.hash();
        }
        return found;
    }

    private static String stripExtension(String name) {
        String base = name;
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    public List<ImageRef> list(String conversationId) throws IOException {
        SafeFs.FsScope scope = this.workspace.scopeFor(conversationId);
        if (!SafeFs.exists(scope, IMAGES_DIR)) return List.of();
        List<ImageRef> refs = new ArrayList<>();
        for (String entry : SafeFs.list(scope, IMAGES_DIR)) {
            String name = entry.substring(entry.lastIndexOf('/') + 1);
            if (!name.endsWith(".json")) continue;
            JsonNode meta = MAPPER.readTree(SafeFs.readText(scope, entry));
            refs.add(new ImageRef(meta.path("hash").asText(), meta.path("originalFileName").asText(),
                    meta.path("mimeType").asText()));
        }
        return refs;
    }

    private static String mimeForFile(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    private static String buildMeta(String hash, String fileName, String mimeType, long bytes, String exifJson) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("hash", hash);
        node.put("originalFileName", fileName);
        node.put("mimeType", mimeType);
        node.put("bytes", bytes);
        if (exifJson != null && !exifJson.isBlank()) {
            try {
                node.set("exif", MAPPER.readTree(exifJson));
            } catch (RuntimeException e) {
                node.put("exifRaw", exifJson);
            }
        }
        return MAPPER.writeValueAsString(node);
    }

    private static String extensionFor(String mimeType) {
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    public record Stored(String path, String hash, String fileName, String mimeType, long bytes, boolean deduped) {}

    public record Loaded(byte[] bytes, String mimeType, String hash) {}

    public record ImageRef(String hash, String fileName, String mimeType) {}
}
