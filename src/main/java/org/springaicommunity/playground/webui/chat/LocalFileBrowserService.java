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
package org.springaicommunity.playground.webui.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.ToolWorkspace;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class LocalFileBrowserService implements ChatClientAction {

    private static final Logger log = LoggerFactory.getLogger(LocalFileBrowserService.class);

    private final List<Path> readRoots;

    public LocalFileBrowserService(ToolWorkspace toolWorkspace) {
        Path home = Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize();
        Path workspace = toolWorkspace.base().toAbsolutePath().normalize();
        this.readRoots = workspace.startsWith(home) ? List.of(home) : List.of(home, workspace);
    }

    @Override
    public String name() {
        return "openPath";
    }

    @Override
    public boolean isAvailable() {
        return isSupported();
    }

    @Override
    public void handle(String payload) {
        reveal(payload);
    }

    public boolean isSupported() {
        return !GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported();
    }

    public boolean reveal(String rawPath) {
        if (!isSupported()) {
            return false;
        }
        Path target = resolveRevealTarget(rawPath);
        return target != null && openInFileBrowser(target.toFile());
    }

    Path resolveRevealTarget(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        Path target;
        try {
            target = Paths.get(rawPath.trim()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return null;
        }
        if (this.readRoots.stream().noneMatch(target::startsWith)) {
            return null;
        }
        return target.toFile().exists() ? target : null;
    }

    static boolean revealsInParent(File file) {
        boolean bundle = file.getName().endsWith(".app") || file.getName().endsWith(".bundle");
        return !file.isDirectory() || bundle;
    }

    private boolean openInFileBrowser(File file) {
        Desktop desktop = Desktop.getDesktop();
        try {
            if (!revealsInParent(file)) {
                desktop.open(file);
            } else if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(file);
            } else if (file.getParentFile() != null) {
                desktop.open(file.getParentFile());
            } else {
                return false;
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to reveal path in file browser ({})", e.getMessage());
            return false;
        }
    }
}
