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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.FsConfig;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.ToolStudio;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolWorkspaceBootstrapTest {

    @Test
    void seedsSampleFilesIntoBasePath(@TempDir Path tempDir) throws Exception {
        ToolWorkspaceBootstrap bootstrap = new ToolWorkspaceBootstrap(
                new ToolWorkspace(buildOptions(tempDir.toString()), tempDir));

        bootstrap.seed();

        assertThat(tempDir.resolve("README.md")).exists();
        assertThat(tempDir.resolve("data.csv")).exists();
        assertThat(Files.readString(tempDir.resolve("README.md"))).contains("TODO", "FIXME");
        assertThat(Files.readString(tempDir.resolve("data.csv"))).startsWith("id,name,score,category");
    }

    @Test
    void doesNotOverwriteExistingFiles(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "user-edited content");
        ToolWorkspaceBootstrap bootstrap = new ToolWorkspaceBootstrap(
                new ToolWorkspace(buildOptions(tempDir.toString()), tempDir));

        bootstrap.seed();

        assertThat(Files.readString(tempDir.resolve("README.md"))).isEqualTo("user-edited content");
        assertThat(tempDir.resolve("data.csv")).exists();
    }

    @Test
    void createsBasePathIfMissing(@TempDir Path tempDir) throws Exception {
        Path nestedBase = tempDir.resolve("never-existed").resolve("workspace");
        assertThat(nestedBase).doesNotExist();
        ToolWorkspaceBootstrap bootstrap = new ToolWorkspaceBootstrap(
                new ToolWorkspace(buildOptions(nestedBase.toString()), tempDir));

        bootstrap.seed();

        assertThat(nestedBase).isDirectory();
        assertThat(nestedBase.resolve("README.md")).exists();
    }

    @Test
    void baseFallsBackToWorkspaceSubdir(@TempDir Path tempDir) {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(""), tempDir);
        assertThat(ws.base()).isEqualTo(tempDir.resolve("workspace").toAbsolutePath().normalize());
    }

    @Test
    void migratesLegacyFsToolWorkspaceIntoWorkspace(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("fs-tool-workspace");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("user-file.txt"), "kept");
        Path workspace = tempDir.resolve("workspace");
        ToolWorkspaceBootstrap bootstrap = new ToolWorkspaceBootstrap(
                new ToolWorkspace(buildOptions(workspace.toString()), tempDir));

        bootstrap.seed();

        assertThat(legacy).doesNotExist();
        assertThat(Files.readString(workspace.resolve("user-file.txt"))).isEqualTo("kept");
    }

    @Test
    void doesNotMigrateWhenWorkspaceAlreadyExists(@TempDir Path tempDir) throws Exception {
        Path legacy = tempDir.resolve("fs-tool-workspace");
        Files.createDirectories(legacy);
        Files.writeString(legacy.resolve("old.txt"), "legacy");
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);
        ToolWorkspaceBootstrap bootstrap = new ToolWorkspaceBootstrap(
                new ToolWorkspace(buildOptions(workspace.toString()), tempDir));

        bootstrap.seed();

        assertThat(legacy).exists();
        assertThat(workspace.resolve("old.txt")).doesNotExist();
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
