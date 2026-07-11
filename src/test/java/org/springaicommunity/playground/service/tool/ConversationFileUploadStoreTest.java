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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationFileUploadStoreTest {

    private static final String CONV = "conv-1";

    @Test
    void storeWritesToConversationUploadsDirAndReturnsRelativePath(@TempDir Path tempDir) throws IOException {
        ConversationFileUploadStore.Stored stored =
                store(tempDir).store(CONV, "sales.csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8));

        assertThat(stored.path()).isEqualTo("uploads/sales.csv");
        assertThat(stored.bytes()).isEqualTo(7);
        Path written = tempDir.resolve(CONV).resolve("uploads/sales.csv");
        assertThat(written).exists();
        assertThat(Files.readString(written)).isEqualTo("a,b\n1,2");
    }

    @Test
    void storeSanitizesUnsafeFileName(@TempDir Path tempDir) throws IOException {
        ConversationFileUploadStore.Stored stored =
                store(tempDir).store(CONV, "../../etc/pa ss wd.txt", new byte[] {1});

        assertThat(stored.fileName()).doesNotContain("/").doesNotContain("..").doesNotContain(" ");
        assertThat(tempDir.resolve(CONV).resolve("uploads").resolve(stored.fileName())).exists();
    }

    @Test
    void storeUniquifiesOnNameCollision(@TempDir Path tempDir) throws IOException {
        ConversationFileUploadStore store = store(tempDir);
        ConversationFileUploadStore.Stored first = store.store(CONV, "data.csv", new byte[] {1});
        ConversationFileUploadStore.Stored second = store.store(CONV, "data.csv", new byte[] {2});

        assertThat(first.fileName()).isEqualTo("data.csv");
        assertThat(second.fileName()).isNotEqualTo("data.csv");
        assertThat(second.fileName()).startsWith("data-").endsWith(".csv");
        assertThat(tempDir.resolve(CONV).resolve("uploads/data.csv")).exists();
        assertThat(tempDir.resolve(CONV).resolve("uploads").resolve(second.fileName())).exists();
    }

    @Test
    void storeWritesBinaryBytesIntact(@TempDir Path tempDir) throws IOException {
        byte[] data = {0, 1, 2, (byte) 255, 65};
        ConversationFileUploadStore.Stored stored = store(tempDir).store(CONV, "image.bin", data);

        assertThat(Files.readAllBytes(tempDir.resolve(CONV).resolve("uploads").resolve(stored.fileName())))
                .isEqualTo(data);
    }

    @Test
    void conversationsGetSeparateUploadDirs(@TempDir Path tempDir) throws IOException {
        ConversationFileUploadStore store = store(tempDir);
        store.store("conv-a", "x.csv", new byte[] {1});
        store.store("conv-b", "x.csv", new byte[] {2});

        assertThat(Files.readAllBytes(tempDir.resolve("conv-a").resolve("uploads/x.csv"))).isEqualTo(new byte[] {1});
        assertThat(Files.readAllBytes(tempDir.resolve("conv-b").resolve("uploads/x.csv"))).isEqualTo(new byte[] {2});
    }

    private ConversationFileUploadStore store(Path base) {
        return new ConversationFileUploadStore(new ToolWorkspace(buildOptions(base.toString()), base));
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
