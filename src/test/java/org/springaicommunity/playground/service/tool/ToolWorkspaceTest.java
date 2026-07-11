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
import org.springaicommunity.playground.service.tool.runtime.SafeFs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ToolWorkspaceTest {

    @Test
    void safeSegmentSanitizesUnsafeChars() {
        assertThat(ToolWorkspace.safeSegment("Chat-abc.123_X")).isEqualTo("Chat-abc.123_X");
        assertThat(ToolWorkspace.safeSegment("a/b\\c d")).isEqualTo("a_b_c_d");
        assertThat(ToolWorkspace.safeSegment("..")).isNull();
        assertThat(ToolWorkspace.safeSegment(".")).isNull();
        assertThat(ToolWorkspace.safeSegment("")).isNull();
        assertThat(ToolWorkspace.safeSegment(null)).isNull();
    }

    @Test
    void conversationDirStaysUnderBase(@TempDir Path tempDir) {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(tempDir.toString()), tempDir);
        Path dir = ws.conversationDir("Chat-x");
        assertThat(dir).isEqualTo(tempDir.resolve("Chat-x").toAbsolutePath().normalize());
        assertThat(dir.startsWith(ws.base())).isTrue();
        assertThat(ws.conversationDir("..")).isNull();
        assertThat(ws.conversationDir(null)).isNull();
    }

    @Test
    void deleteConversationDirRemovesTreeButNotBase(@TempDir Path tempDir) throws IOException {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(tempDir.toString()), tempDir);
        Path convDir = ws.conversationDir("Chat-y");
        Files.createDirectories(convDir.resolve("sub"));
        Files.writeString(convDir.resolve("a.txt"), "x");
        Files.writeString(convDir.resolve("sub").resolve("b.txt"), "y");

        ws.deleteConversationDir("Chat-y");

        assertThat(convDir).doesNotExist();
        assertThat(ws.base()).exists();
    }

    @Test
    void scopeForConfinesToConversationDir(@TempDir Path tempDir) {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(tempDir.toString()), tempDir);
        SafeFs.FsScope scope = ws.scopeFor("Chat-z");
        assertThat(scope.workspace()).isEqualTo(tempDir.resolve("Chat-z").toAbsolutePath().normalize());
    }

    @Test
    void scopeForFallsBackToBaseWhenConversationIdInvalid(@TempDir Path tempDir) {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(tempDir.toString()), tempDir);
        assertThat(ws.scopeFor(null).workspace()).isEqualTo(ws.base());
        assertThat(ws.scopeFor("..").workspace()).isEqualTo(ws.base());
    }

    @Test
    void conversationFileCountCountsRegularFiles(@TempDir Path tempDir) throws IOException {
        ToolWorkspace ws = new ToolWorkspace(buildOptions(tempDir.toString()), tempDir);
        assertThat(ws.conversationFileCount("Chat-z")).isZero();

        Path convDir = ws.conversationDir("Chat-z");
        Files.createDirectories(convDir.resolve("sub"));
        Files.writeString(convDir.resolve("a.txt"), "x");
        Files.writeString(convDir.resolve("sub").resolve("b.txt"), "y");

        assertThat(ws.conversationFileCount("Chat-z")).isEqualTo(2);
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
