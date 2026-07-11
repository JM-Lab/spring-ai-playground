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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ConversationFileUploadStore {

    public static final String UPLOADS_DIR = "uploads";
    private static final int MAX_NAME_LENGTH = 120;

    private final ToolWorkspace workspace;

    public ConversationFileUploadStore(ToolWorkspace workspace) {
        this.workspace = workspace;
    }

    public Stored store(String conversationId, String fileName, byte[] content) throws IOException {
        SafeFs.FsScope scope = this.workspace.scopeFor(conversationId);
        Path uploadsDir = scope.workspace().resolve(UPLOADS_DIR);
        String name = uniqueName(uploadsDir, sanitize(fileName));
        Path written = SafeFs.writeBytes(scope, UPLOADS_DIR + "/" + name, content);
        return new Stored(UPLOADS_DIR + "/" + name, name, written.toAbsolutePath().normalize().toString(),
                content == null ? 0L : content.length);
    }

    private static String uniqueName(Path uploadsDir, String name) {
        if (!Files.exists(uploadsDir.resolve(name))) return name;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            String candidate = stem + "-" + i + ext;
            if (!Files.exists(uploadsDir.resolve(candidate))) return candidate;
        }
        return stem + "-" + System.currentTimeMillis() + ext;
    }

    private static String sanitize(String fileName) {
        String base = fileName == null ? "" : fileName.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("_+", "_");
        while (base.startsWith(".") || base.startsWith("_")) base = base.substring(1);
        if (base.length() > MAX_NAME_LENGTH) base = base.substring(0, MAX_NAME_LENGTH);
        return base.isBlank() ? "upload" : base;
    }

    public record Stored(String path, String fileName, String absolutePath, long bytes) {}
}
