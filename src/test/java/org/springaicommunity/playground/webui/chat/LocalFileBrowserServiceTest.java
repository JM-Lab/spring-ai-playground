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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.FsConfig;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.ToolStudio;
import org.springaicommunity.playground.service.tool.ToolWorkspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileBrowserServiceTest {

    @Test
    void resolveAcceptsExistingFileUnderWorkspaceRoot(@TempDir Path workspace) throws IOException {
        Path file = Files.writeString(workspace.resolve("note.txt"), "x");
        assertThat(service(workspace).resolveRevealTarget(file.toString()))
                .isEqualTo(file.toAbsolutePath().normalize());
    }

    @Test
    void resolveAcceptsWorkspaceDirectoryItself(@TempDir Path workspace) {
        assertThat(service(workspace).resolveRevealTarget(workspace.toString()))
                .isEqualTo(workspace.toAbsolutePath().normalize());
    }

    @Test
    void resolveRejectsExistingPathOutsideAllReadRoots(@TempDir Path workspace, @TempDir Path outside) throws IOException {
        Path file = Files.writeString(outside.resolve("secret.txt"), "x");
        assertThat(service(workspace).resolveRevealTarget(file.toString())).isNull();
    }

    @Test
    void resolveRejectsParentTraversalEscapingRoots(@TempDir Path workspace) {
        String escaping = workspace + "/../../../../../../tmp-escape-" + workspace.getFileName();
        assertThat(service(workspace).resolveRevealTarget(escaping)).isNull();
    }

    @Test
    void resolveRejectsNonExistentPathUnderRoot(@TempDir Path workspace) {
        assertThat(service(workspace).resolveRevealTarget(workspace.resolve("missing.txt").toString())).isNull();
    }

    @Test
    void resolveRejectsNullBlankAndUnparseable(@TempDir Path workspace) {
        LocalFileBrowserService s = service(workspace);
        assertThat(s.resolveRevealTarget(null)).isNull();
        assertThat(s.resolveRevealTarget("   ")).isNull();
        assertThat(s.resolveRevealTarget("foo\0bar")).isNull();
    }

    @Test
    void regularFileIsRevealedInParentNeverOpened(@TempDir Path workspace) throws IOException {
        File file = Files.writeString(workspace.resolve("doc.pdf"), "x").toFile();
        assertThat(LocalFileBrowserService.revealsInParent(file)).isTrue();
    }

    @Test
    void plainDirectoryOpensItsContents(@TempDir Path workspace) {
        assertThat(LocalFileBrowserService.revealsInParent(workspace.toFile())).isFalse();
    }

    @Test
    void appBundleIsRevealedNotEntered(@TempDir Path workspace) throws IOException {
        File bundle = Files.createDirectory(workspace.resolve("Thing.app")).toFile();
        assertThat(LocalFileBrowserService.revealsInParent(bundle)).isTrue();
    }

    private LocalFileBrowserService service(Path workspace) {
        return new LocalFileBrowserService(new ToolWorkspace(buildOptions(workspace.toString()), workspace));
    }

    private SpringAiPlaygroundOptions buildOptions(String basePath) {
        return new SpringAiPlaygroundOptions(
                new ToolStudio(30L, null, new FsConfig(basePath),
                        new SpringAiPlaygroundOptions.DefaultTools(null,
                                new SpringAiPlaygroundOptions.SelectionRule(Set.of(), Set.of(), Set.of()),
                                new SpringAiPlaygroundOptions.SelectionRule(Set.of(), Set.of(), Set.of()))),
                true, null, null, null, null);
    }
}
