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
package org.springaicommunity.playground.service.tool.runtime;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.JsSandbox;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionParams;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JsToolFsBuiltinsTest {

    private static List<Map<String, Object>> specs;
    private static JsToolExecutor executor;

    @TempDir
    static Path tmp;

    @BeforeAll
    static void loadSpecs() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        specs = new ArrayList<>();
        try (InputStream in = JsToolFsBuiltinsTest.class.getResourceAsStream(
                "/tool/default-tool-specs-builtin-fs.json")) {
            specs.addAll(mapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {}));
        }
        executor = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                tmp);
    }

    private EffectivePolicy fsPolicy() {
        return new EffectivePolicy(Set.of(), Set.of(), false, true, false, false, false,
                5_000_000L, 30L, true, null, null, null);
    }

    private EffectivePolicy fsWritePolicy() {
        return new EffectivePolicy(Set.of(), Set.of(), false, true, true, false, false,
                5_000_000L, 30L, true, null, null, null);
    }

    private static Map<String, Object> specByName(String name) {
        return specs.stream().filter(s -> name.equals(s.get("name"))).findFirst().orElseThrow();
    }

    private JsExecutionResult run(String name, Map<String, Object> params) {
        return executor.execute(new JsExecutionParams(coerce(params), (String) specByName(name).get("code")),
                fsPolicy(), tmp);
    }

    private JsExecutionResult runWithWrite(String name, Map<String, Object> params) {
        return executor.execute(new JsExecutionParams(coerce(params), (String) specByName(name).get("code")),
                fsWritePolicy(), tmp);
    }

    private static Map<String, Object> coerce(Map<String, Object> raw) {
        return new LinkedHashMap<>(raw);
    }

    @Test
    void freshConversationWorkspaceListsEmptyInsteadOfFailing() {
        String conversationId = "Chat-fresh-" + System.nanoTime();
        JsExecutionResult find = runInConversation("findFiles", Map.of("glob", "*", "dir", "."), conversationId);
        assertThat(find.isOk()).as(find.error()).isTrue();
        JsExecutionResult list = runInConversation("listDir", Map.of("dir", "."), conversationId);
        assertThat(list.isOk()).as(list.error()).isTrue();
    }

    private JsExecutionResult runInConversation(String name, Map<String, Object> params, String conversationId) {
        return executor.execute(new JsExecutionParams(coerce(params),
                        (String) specByName(name).get("code"), paramNames(name)),
                fsPolicy(), null, conversationId);
    }

    @SuppressWarnings("unchecked")
    private static List<String> paramNames(String name) {
        return ((List<Map<String, Object>>) specByName(name).getOrDefault("params", List.of())).stream()
                .map(param -> (String) param.get("name")).toList();
    }

    @Test
    void readTextFileReturnsContent() throws IOException {
        Files.writeString(tmp.resolve("notes.md"), "alpha\nbeta\ngamma\n");
        JsExecutionResult r = run("readTextFile", Map.of("path", "notes.md"));
        assertThat(r.isOk()).as(r.error()).isTrue();
        assertThat((String) r.result()).isEqualTo("alpha\nbeta\ngamma\n");
    }

    @Test
    void readTextFileRejectsPathEscape() throws IOException {
        Path outside = tmp.getParent().resolve("outside-" + System.nanoTime() + ".txt");
        Files.writeString(outside, "secret");
        try {
            JsExecutionResult r = run("readTextFile", Map.of("path", "../" + outside.getFileName()));
            assertThat(r.isOk()).as("must reject path escape").isFalse();
            assertThat(r.error()).doesNotContain("secret");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void listDirReturnsImmediateEntries() throws IOException {
        Files.createDirectory(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("a.txt"), "x");
        Files.writeString(tmp.resolve("b.txt"), "y");
        JsExecutionResult r = run("listDir", Map.of("dir", "."));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> entries = (List<String>) r.result();
        assertThat(entries).contains("a.txt", "b.txt", "sub");
    }

    @Test
    void statFileReportsSizeAndDirectoryFlag() throws IOException {
        Path file = tmp.resolve("file.txt");
        Files.writeString(file, "hello");
        Path dir = tmp.resolve("aDir");
        Files.createDirectory(dir);

        JsExecutionResult rf = run("statFile", Map.of("path", "file.txt"));
        assertThat(rf.isOk()).as(rf.error()).isTrue();
        Map<?, ?> stat = (Map<?, ?>) rf.result();
        assertThat(((Number) stat.get("size")).longValue()).isEqualTo(5L);
        assertThat(stat.get("directory")).isEqualTo(false);

        JsExecutionResult rd = run("statFile", Map.of("path", "aDir"));
        assertThat(rd.isOk()).as(rd.error()).isTrue();
        Map<?, ?> dstat = (Map<?, ?>) rd.result();
        assertThat(dstat.get("directory")).isEqualTo(true);
    }

    @Test
    void lineCountReturnsNumberOfLines() throws IOException {
        Files.writeString(tmp.resolve("multi.txt"), "one\ntwo\nthree\nfour\n");
        JsExecutionResult r = run("lineCount", Map.of("path", "multi.txt"));
        assertThat(r.isOk()).as(r.error()).isTrue();
        assertThat(((Number) r.result()).longValue()).isEqualTo(4L);
    }

    @Test
    void sliceFileReturnsRange() throws IOException {
        Files.writeString(tmp.resolve("lines.txt"), "L1\nL2\nL3\nL4\nL5\n");
        JsExecutionResult r = run("sliceFile", Map.of("path", "lines.txt", "start", 1L, "end", 4L));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) r.result();
        assertThat(rows).containsExactly("L2", "L3", "L4");
    }

    @Test
    void sortFileSortsLinesWithOptions() throws IOException {
        Files.writeString(tmp.resolve("sort.txt"), "banana\napple\ncherry\napple\n");
        JsExecutionResult r = run("sortFile", Map.of(
                "path", "sort.txt",
                "reverse", false, "numeric", false, "caseInsensitive", false, "unique", true));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> sorted = (List<String>) r.result();
        assertThat(sorted).containsExactly("apple", "banana", "cherry");
    }

    @Test
    void grepFileReturnsMatchingLinesNumbered() throws IOException {
        Files.writeString(tmp.resolve("src.txt"),
                "ok line\nTODO: fix me\nanother\nFIXME later\nfinal\n");
        JsExecutionResult r = run("grepFile", Map.of(
                "pattern", "TODO|FIXME",
                "path", "src.txt",
                "caseInsensitive", false,
                "numbered", true,
                "limit", 0L));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> hits = (List<String>) r.result();
        assertThat(hits).hasSize(2);
        assertThat(hits.get(0)).startsWith("2:").contains("TODO");
        assertThat(hits.get(1)).startsWith("4:").contains("FIXME");
    }

    @Test
    void findFilesByGlobAndType() throws IOException {
        Files.createDirectory(tmp.resolve("docs"));
        Files.writeString(tmp.resolve("README.md"), "");
        Files.writeString(tmp.resolve("docs/guide.md"), "");
        Files.writeString(tmp.resolve("notes.txt"), "");

        JsExecutionResult r = run("findFiles", Map.of(
                "dir", ".",
                "glob", "*.md",
                "maxDepth", 5L,
                "type", "file"));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> hits = (List<String>) r.result();
        assertThat(hits).contains("README.md", "docs/guide.md");
        assertThat(hits).doesNotContain("notes.txt");
    }

    @Test
    void cutFileFieldsExtractsSelectedColumns() throws IOException {
        Files.writeString(tmp.resolve("data.csv"),
                "name,age,city,zip\nAlice,30,Seoul,12345\nBob,25,Busan,67890\n");
        JsExecutionResult r = run("cutFileFields", Map.of(
                "path", "data.csv",
                "fields", List.of(1, 3),
                "delimiter", ",",
                "regex", false));
        assertThat(r.isOk()).as(r.error()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) r.result();
        assertThat(rows).contains("name,city", "Alice,Seoul", "Bob,Busan");
    }

    @Test
    void writeTextFileCreatesFileAndReportsBytes() throws IOException {
        JsExecutionResult r = runWithWrite("writeTextFile", Map.of(
                "path", "out.txt",
                "content", "hello world"));
        assertThat(r.isOk()).as(r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        assertThat(m.get("ok")).isEqualTo(true);
        assertThat(m.get("path")).isEqualTo(tmp.toAbsolutePath().normalize().resolve("out.txt").toString());
        assertThat(((Number) m.get("bytes")).intValue()).isEqualTo(11);
        assertThat(Files.readString(tmp.resolve("out.txt"))).isEqualTo("hello world");
    }

    @Test
    void writeTextFileRejectsPathEscape() throws IOException {
        JsExecutionResult r = runWithWrite("writeTextFile", Map.of(
                "path", "../../etc/passwd-hack",
                "content", "x"));
        assertThat(r.isOk()).as("path escape must be rejected").isFalse();
    }

    @Test
    void listAllowedDirectoriesReportsWorkspaceAndReadRoots() {
        JsExecutionResult r = run("listAllowedDirectories", Map.of());
        assertThat(r.isOk()).as(r.error()).isTrue();
        Map<?, ?> m = (Map<?, ?>) r.result();
        String workspace = tmp.toAbsolutePath().normalize().toString();
        assertThat(m.get("workingDirectory")).isEqualTo(workspace);
        @SuppressWarnings("unchecked")
        List<String> roots = (List<String>) m.get("readRoots");
        assertThat(roots).contains(workspace);
    }

    @Test
    void defaultScopeWidensReadsButConfinesWritesToWorkspace() throws IOException {
        Path ws = tmp.resolve("workspace");
        Files.createDirectories(ws);
        Files.writeString(ws.resolve("in.txt"), "inside");
        JsToolExecutor scoped = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                ws);

        JsExecutionResult read = scoped.execute(new JsExecutionParams(Map.of("path", "in.txt"),
                (String) specByName("readTextFile").get("code")), fsPolicy(), null);
        assertThat(read.isOk()).as(read.error()).isTrue();
        assertThat((String) read.result()).isEqualTo("inside");

        JsExecutionResult dirs = scoped.execute(new JsExecutionParams(Map.of(),
                (String) specByName("listAllowedDirectories").get("code")), fsPolicy(), null);
        Map<?, ?> m = (Map<?, ?>) dirs.result();
        assertThat(m.get("workingDirectory")).isEqualTo(ws.toAbsolutePath().normalize().toString());
        @SuppressWarnings("unchecked")
        List<String> roots = (List<String>) m.get("readRoots");
        assertThat(roots)
                .contains(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize().toString());

        JsExecutionResult write = scoped.execute(new JsExecutionParams(
                Map.of("path", tmp.resolve("escape.txt").toString(), "content", "x"),
                (String) specByName("writeTextFile").get("code")), fsWritePolicy(), null);
        assertThat(write.isOk()).as("write outside the workspace must be rejected").isFalse();
    }

    @Test
    void perConversationScopeWritesIntoSubdirectory() throws IOException {
        Path ws = tmp.resolve("workspace");
        Files.createDirectories(ws);
        JsToolExecutor scoped = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                ws);
        String conv = "Chat-abc123";
        Path expectedDir = ws.resolve(conv).toAbsolutePath().normalize();

        JsExecutionResult dirs = scoped.execute(new JsExecutionParams(Map.of(),
                (String) specByName("listAllowedDirectories").get("code")), fsPolicy(), null, conv);
        assertThat(((Map<?, ?>) dirs.result()).get("workingDirectory")).isEqualTo(expectedDir.toString());

        JsExecutionResult w = scoped.execute(new JsExecutionParams(
                Map.of("path", "note.txt", "content", "hello"),
                (String) specByName("writeTextFile").get("code")), fsWritePolicy(), null, conv);
        assertThat(w.isOk()).as(w.error()).isTrue();
        assertThat(((Map<?, ?>) w.result()).get("path")).isEqualTo(expectedDir.resolve("note.txt").toString());
        assertThat(Files.readString(expectedDir.resolve("note.txt"))).isEqualTo("hello");
    }

    @Test
    void perConversationScopesAreIsolated() throws IOException {
        Path ws = tmp.resolve("workspace");
        Files.createDirectories(ws);
        JsToolExecutor scoped = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                ws);

        scoped.execute(new JsExecutionParams(Map.of("path", "note.txt", "content", "from-A"),
                (String) specByName("writeTextFile").get("code")), fsWritePolicy(), null, "Chat-A");
        scoped.execute(new JsExecutionParams(Map.of("path", "note.txt", "content", "from-B"),
                (String) specByName("writeTextFile").get("code")), fsWritePolicy(), null, "Chat-B");

        assertThat(Files.readString(ws.resolve("Chat-A").resolve("note.txt"))).isEqualTo("from-A");
        assertThat(Files.readString(ws.resolve("Chat-B").resolve("note.txt"))).isEqualTo("from-B");

        JsExecutionResult readB = scoped.execute(new JsExecutionParams(Map.of("path", "note.txt"),
                (String) specByName("readTextFile").get("code")), fsPolicy(), null, "Chat-B");
        assertThat(readB.result()).isEqualTo("from-B");
    }

    @Test
    void blankOrUnsafeConversationIdWritesToWorkspaceRoot() throws IOException {
        Path ws = tmp.resolve("workspace");
        Files.createDirectories(ws);
        JsToolExecutor scoped = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                ws);
        String expected = ws.resolve("root.txt").toAbsolutePath().normalize().toString();
        for (String conv : new String[] {null, "", ".."}) {
            JsExecutionResult w = scoped.execute(new JsExecutionParams(
                    Map.of("path", "root.txt", "content", "x"),
                    (String) specByName("writeTextFile").get("code")), fsWritePolicy(), null, conv);
            assertThat(w.isOk()).as("conv=%s -> %s", conv, w.error()).isTrue();
            assertThat(((Map<?, ?>) w.result()).get("path")).isEqualTo(expected);
        }
    }

    @Test
    void overrideFsBaseIgnoresConversationId() throws IOException {
        Path ws = tmp.resolve("workspace");
        Path override = tmp.resolve("override");
        Files.createDirectories(override);
        JsToolExecutor scoped = new JsToolExecutor(30L,
                new JsSandbox(false, true, false, false, 5_000_000L, Set.of(), Set.of(), Map.of()),
                ws);
        JsExecutionResult w = scoped.execute(new JsExecutionParams(
                Map.of("path", "note.txt", "content", "x"),
                (String) specByName("writeTextFile").get("code")), fsWritePolicy(), override, "Chat-A");
        assertThat(w.isOk()).as(w.error()).isTrue();
        assertThat(((Map<?, ?>) w.result()).get("path"))
                .isEqualTo(override.resolve("note.txt").toAbsolutePath().normalize().toString());
    }

    @Test
    void everyFsToolDeclaresFileReadAndBlockedNetwork() {
        Set<String> writeTools = Set.of("writeTextFile", "appendTextFile", "editTextFile",
                "copyFile", "moveFile", "deleteFile", "deleteDir");
        for (Map<String, Object> spec : specs) {
            String name = (String) spec.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> overrides = (Map<String, Object>) spec.get("sandboxOverrides");
            assertThat(overrides).as("%s missing sandboxOverrides", name).isNotNull();
            assertThat(overrides.get("fileRead")).as("%s must require fileRead", name).isEqualTo(true);
            if (writeTools.contains(name)) {
                assertThat(overrides.get("fileWrite")).as("%s explicitly grants fileWrite", name)
                        .isEqualTo(true);
            } else {
                assertThat(overrides.get("fileWrite")).as("%s must NOT request fileWrite", name)
                        .isEqualTo(false);
            }
            assertThat(overrides.get("networkMode")).as("%s must keep network blocked", name)
                    .isEqualTo("blocked");
            assertThat(spec.get("category")).isEqualTo("FILE");
        }
    }
}
