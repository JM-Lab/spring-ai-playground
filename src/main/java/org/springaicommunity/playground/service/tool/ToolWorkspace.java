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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
public class ToolWorkspace {

    private static final Logger log = LoggerFactory.getLogger(ToolWorkspace.class);

    private final Path base;

    public ToolWorkspace(SpringAiPlaygroundOptions options, Path springAiPlaygroundHomeDir) {
        this.base = resolveBase(options, springAiPlaygroundHomeDir);
    }

    public Path base() {
        return base;
    }

    public Path conversationDir(String conversationId) {
        String segment = safeSegment(conversationId);
        if (segment == null) return null;
        Path dir = base.resolve(segment).normalize();
        return dir.startsWith(base) && !dir.equals(base) ? dir : null;
    }

    public void deleteConversationDir(String conversationId) {
        Path dir = conversationDir(conversationId);
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean conversation workspace {}: {}", dir, e.getMessage());
        }
    }

    public static String safeSegment(String conversationId) {
        if (conversationId == null) return null;
        String cleaned = conversationId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isEmpty() || ".".equals(cleaned) || "..".equals(cleaned)) return null;
        return cleaned;
    }

    private static Path resolveBase(SpringAiPlaygroundOptions options, Path springAiPlaygroundHomeDir) {
        String configured = options.toolStudio() == null || options.toolStudio().fs() == null
                ? null : options.toolStudio().fs().basePath();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        return springAiPlaygroundHomeDir.resolve("workspace").toAbsolutePath().normalize();
    }
}
