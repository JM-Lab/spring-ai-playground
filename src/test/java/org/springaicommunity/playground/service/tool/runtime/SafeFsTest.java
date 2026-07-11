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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.tool.runtime.SafeFs.FsPolicyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFsTest {

    @TempDir
    Path tempDir;

    @TempDir
    Path outsideDir;

    private Path base;
    private SafeFs.FsScope scope;

    private static void symlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException e) {
            Assumptions.abort("symbolic links unsupported in this environment: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        base = tempDir.toAbsolutePath().normalize();
        scope = SafeFs.FsScope.confined(base);
        Files.writeString(base.resolve("hello.txt"), "world");
        Files.createDirectory(base.resolve("sub"));
        Files.writeString(base.resolve("sub").resolve("nested.txt"), "deep");
    }

    @Test
    void readTextReturnsFileContent() throws IOException {
        assertEquals("world", SafeFs.readText(scope, "hello.txt"));
    }

    @Test
    void readTextHandlesNestedPath() throws IOException {
        assertEquals("deep", SafeFs.readText(scope, "sub/nested.txt"));
    }

    @Test
    void writeTextCreatesFile() throws IOException {
        SafeFs.writeText(scope, "new.txt", "content");
        assertEquals("content", Files.readString(base.resolve("new.txt")));
    }

    @Test
    void writeTextCreatesParentDirs() throws IOException {
        SafeFs.writeText(scope, "a/b/c/note.txt", "nested write");
        assertTrue(Files.exists(base.resolve("a/b/c/note.txt")));
    }

    @Test
    void writeBytesCreatesFileInUploadsDir() throws IOException {
        byte[] data = {0, 1, 2, 3, 65, 66, 67};
        SafeFs.writeBytes(scope, "uploads/blob.bin", data);
        assertArrayEquals(data, Files.readAllBytes(base.resolve("uploads/blob.bin")));
    }

    @Test
    void writeBytesRejectsEscapeOutsideWorkspace() {
        assertThrows(FsPolicyException.class,
                () -> SafeFs.writeBytes(scope, "../escape.bin", new byte[]{1}));
    }

    @Test
    void listReturnsTopLevelEntries() throws IOException {
        var entries = SafeFs.list(scope, ".");
        assertTrue(entries.contains("hello.txt"));
        assertTrue(entries.contains("sub"));
    }

    @Test
    void existsTrueForExistingFile() {
        assertTrue(SafeFs.exists(scope, "hello.txt"));
    }

    @Test
    void existsFalseForMissingFile() {
        assertFalse(SafeFs.exists(scope, "nope.txt"));
    }

    @Test
    void statReturnsAttrs() throws IOException {
        SafeFs.FileStat s = SafeFs.stat(scope, "hello.txt");
        assertEquals(5, s.size());
        assertFalse(s.directory());
        assertTrue(s.mtime() > 0);
    }

    @Test
    void blocksParentEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(scope, "../etc/passwd"));
    }

    @Test
    void blocksAbsolutePath() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(scope, "/etc/passwd"));
    }

    @Test
    void blocksDeepEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(scope, "sub/../../etc/passwd"));
    }

    @Test
    void blocksEmptyPath() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(scope, ""));
    }

    @Test
    void existsFalseForEscape() {
        assertFalse(SafeFs.exists(scope, "../../../etc/passwd"));
    }

    @Test
    void normalizesInnerDotDot() throws IOException {
        assertEquals("deep", SafeFs.readText(scope, "sub/./nested.txt"));
        assertEquals("world", SafeFs.readText(scope, "sub/../hello.txt"));
    }

    @Test
    void grepFindsMatchingLines() throws IOException {
        Files.writeString(base.resolve("log.txt"), "INFO start\nERROR failed\nINFO ok\nERROR boom\n");
        var matches = SafeFs.grep(scope, "log.txt", "ERROR", false, 0, false);
        assertEquals(2, matches.size());
        assertEquals("ERROR failed", matches.get(0));
        assertEquals("ERROR boom", matches.get(1));
    }

    @Test
    void grepCaseInsensitive() throws IOException {
        Files.writeString(base.resolve("log.txt"), "info\nINFO\nInFo\n");
        var matches = SafeFs.grep(scope, "log.txt", "info", true, 0, false);
        assertEquals(3, matches.size());
    }

    @Test
    void grepNumbered() throws IOException {
        Files.writeString(base.resolve("log.txt"), "a\nb\na\n");
        var matches = SafeFs.grep(scope, "log.txt", "a", false, 0, true);
        assertEquals("1:a", matches.get(0));
        assertEquals("3:a", matches.get(1));
    }

    @Test
    void grepRespectsLimit() throws IOException {
        Files.writeString(base.resolve("log.txt"), "x\nx\nx\nx\nx\n");
        var matches = SafeFs.grep(scope, "log.txt", "x", false, 2, false);
        assertEquals(2, matches.size());
    }

    @Test
    void grepRejectsEmptyPattern() {
        assertThrows(FsPolicyException.class, () -> SafeFs.grep(scope, "hello.txt", "", false, 0, false));
    }

    @Test
    void grepRejectsInvalidRegex() {
        assertThrows(FsPolicyException.class, () -> SafeFs.grep(scope, "hello.txt", "[unclosed", false, 0, false));
    }

    @Test
    void lineCountSingleLine() throws IOException {
        assertEquals(1, SafeFs.lineCount(scope, "hello.txt"));
    }

    @Test
    void lineCountMultiLine() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "a\nb\nc\nd\n");
        assertEquals(4, SafeFs.lineCount(scope, "multi.txt"));
    }

    @Test
    void sliceHead() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(scope, "multi.txt", 0, 3);
        assertEquals(3, lines.size());
        assertEquals("1", lines.get(0));
        assertEquals("3", lines.get(2));
    }

    @Test
    void sliceTailNegative() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(scope, "multi.txt", -2, null);
        assertEquals(2, lines.size());
        assertEquals("4", lines.get(0));
        assertEquals("5", lines.get(1));
    }

    @Test
    void sliceMiddle() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(scope, "multi.txt", 1, 4);
        assertEquals(3, lines.size());
        assertEquals("2", lines.get(0));
        assertEquals("4", lines.get(2));
    }

    @Test
    void sliceFullDefault() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "a\nb\nc\n");
        var lines = SafeFs.slice(scope, "multi.txt", null, null);
        assertEquals(3, lines.size());
    }

    @Test
    void sliceOutOfRange() throws IOException {
        Files.writeString(base.resolve("two.txt"), "x\ny\n");
        var lines = SafeFs.slice(scope, "two.txt", 0, 100);
        assertEquals(2, lines.size());
    }

    @Test
    void pipelineBlocksEscape() {
        assertThrows(FsPolicyException.class,
                () -> SafeFs.grep(scope, "../escape", "x", false, 0, false));
        assertThrows(FsPolicyException.class,
                () -> SafeFs.slice(scope, "/etc/passwd", 0, 1));
    }

    @Test
    void cutTabDelimitedExtractsColumn() throws IOException {
        Files.writeString(base.resolve("tsv.txt"), "a\t1\tx\nb\t2\ty\nc\t3\tz\n");
        var rows = SafeFs.cut(scope, "tsv.txt", "\t", java.util.List.of(2), false);
        assertEquals(3, rows.size());
        assertEquals("1", rows.get(0));
        assertEquals("3", rows.get(2));
    }

    @Test
    void cutMultipleFields() throws IOException {
        Files.writeString(base.resolve("csv.txt"), "a,b,c,d\ne,f,g,h\n");
        var rows = SafeFs.cut(scope, "csv.txt", ",", java.util.List.of(1, 3), false);
        assertEquals("a,c", rows.get(0));
        assertEquals("e,g", rows.get(1));
    }

    @Test
    void cutWhitespaceRegex() throws IOException {
        Files.writeString(base.resolve("log.txt"), "INFO   start\nERROR    failed\n");
        var rows = SafeFs.cut(scope, "log.txt", "\\s+", java.util.List.of(1), true);
        assertEquals("INFO", rows.get(0));
        assertEquals("ERROR", rows.get(1));
    }

    @Test
    void cutHandlesMissingField() throws IOException {
        Files.writeString(base.resolve("sparse.txt"), "a,b\nc\n");
        var rows = SafeFs.cut(scope, "sparse.txt", ",", java.util.List.of(2), false);
        assertEquals("b", rows.get(0));
        assertEquals("", rows.get(1));
    }

    @Test
    void cutRejectsEmptyFields() {
        assertThrows(FsPolicyException.class,
                () -> SafeFs.cut(scope, "hello.txt", ",", java.util.List.of(), false));
    }

    @Test
    void sortLexicographic() throws IOException {
        Files.writeString(base.resolve("u.txt"), "banana\napple\ncherry\n");
        var sorted = SafeFs.sort(scope, "u.txt", false, false, false, false);
        assertEquals("apple", sorted.get(0));
        assertEquals("cherry", sorted.get(2));
    }

    @Test
    void sortReverse() throws IOException {
        Files.writeString(base.resolve("u.txt"), "a\nb\nc\n");
        var sorted = SafeFs.sort(scope, "u.txt", true, false, false, false);
        assertEquals("c", sorted.get(0));
    }

    @Test
    void sortNumeric() throws IOException {
        Files.writeString(base.resolve("n.txt"), "10\n2\n100\n5\n");
        var sorted = SafeFs.sort(scope, "n.txt", false, true, false, false);
        assertEquals("2", sorted.get(0));
        assertEquals("100", sorted.get(3));
    }

    @Test
    void sortUnique() throws IOException {
        Files.writeString(base.resolve("d.txt"), "b\na\nb\nc\na\n");
        var sorted = SafeFs.sort(scope, "d.txt", false, false, false, true);
        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0));
    }

    @Test
    void sortCaseInsensitive() throws IOException {
        Files.writeString(base.resolve("c.txt"), "Banana\napple\nCherry\n");
        var sorted = SafeFs.sort(scope, "c.txt", false, false, true, false);
        assertEquals("apple", sorted.get(0));
        assertEquals("Banana", sorted.get(1));
    }

    @Test
    void findByGlob() throws IOException {
        Files.writeString(base.resolve("a.json"), "{}");
        Files.writeString(base.resolve("b.json"), "{}");
        Files.writeString(base.resolve("c.txt"), "");
        var json = SafeFs.find(scope, ".", "*.json", 0, "file");
        assertEquals(2, json.size());
    }

    @Test
    void findRecursive() throws IOException {
        Files.writeString(base.resolve("sub").resolve("deep.json"), "{}");
        Files.writeString(base.resolve("top.json"), "{}");
        var json = SafeFs.find(scope, ".", "*.json", 0, "file");
        assertTrue(json.stream().anyMatch(p -> p.endsWith("deep.json")));
        assertTrue(json.stream().anyMatch(p -> p.endsWith("top.json")));
    }

    @Test
    void findFilterByType() throws IOException {
        var dirs = SafeFs.find(scope, ".", "*", 1, "dir");
        assertTrue(dirs.stream().anyMatch(p -> p.endsWith("sub")));
        assertFalse(dirs.stream().anyMatch(p -> p.endsWith("hello.txt")));
    }

    @Test
    void findMaxDepth() throws IOException {
        Files.writeString(base.resolve("sub").resolve("deep.txt"), "x");
        var depth1 = SafeFs.find(scope, ".", "*", 1, "file");
        assertTrue(depth1.stream().anyMatch(p -> p.equals("hello.txt")));
        assertFalse(depth1.stream().anyMatch(p -> p.contains("deep")));
    }

    @Test
    void findRejectsBadGlob() {
        assertThrows(FsPolicyException.class, () -> SafeFs.find(scope, ".", "[unclosed", 0, null));
    }

    @Test
    void findRejectsEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.find(scope, "..", "*", 0, null));
    }

    @Test
    void readAllowedAnywhereUnderReadRootButOutsideWorkspace() throws IOException {
        Path workspace = base.resolve("app").resolve("workspace");
        Files.createDirectories(workspace);
        Files.writeString(base.resolve("report.txt"), "outside-ws");
        SafeFs.FsScope split = new SafeFs.FsScope(workspace, List.of(base));
        assertEquals("outside-ws", SafeFs.readText(split, base.resolve("report.txt").toString()));
    }

    @Test
    void readRejectedOutsideReadRoots() {
        Path home = base.resolve("home");
        SafeFs.FsScope split = new SafeFs.FsScope(home.resolve("ws"), List.of(home));
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(split, "/etc/passwd"));
    }

    @Test
    void relativePathResolvesUnderWorkspaceNotReadRoot() throws IOException {
        Path workspace = base.resolve("ws");
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("note.txt"), "in-ws");
        SafeFs.FsScope split = new SafeFs.FsScope(workspace, List.of(base));
        assertEquals("in-ws", SafeFs.readText(split, "note.txt"));
    }

    @Test
    void writeConfinedToWorkspaceEvenWhenReadRootIsWider() throws IOException {
        Path workspace = base.resolve("ws");
        Files.createDirectories(workspace);
        SafeFs.FsScope split = new SafeFs.FsScope(workspace, List.of(base));
        assertThrows(FsPolicyException.class,
                () -> SafeFs.writeText(split, base.resolve("escape.txt").toString(), "x"));
        assertThrows(FsPolicyException.class, () -> SafeFs.writeText(split, "../escape.txt", "x"));
        SafeFs.writeText(split, "ok.txt", "y");
        assertEquals("y", Files.readString(workspace.resolve("ok.txt")));
    }

    @Test
    void listReportsAbsolutePathsForEntriesOutsideWorkspace() throws IOException {
        Path workspace = base.resolve("ws");
        Files.createDirectories(workspace);
        Files.writeString(base.resolve("a.txt"), "x");
        SafeFs.FsScope split = new SafeFs.FsScope(workspace, List.of(base));
        var entries = SafeFs.list(split, base.toString());
        assertTrue(entries.contains(base.resolve("a.txt").toString()));
    }

    @Test
    void writeTextReturnsAbsoluteWrittenPath() throws IOException {
        Path written = SafeFs.writeText(scope, "out/w.txt", "x");
        assertEquals(base.resolve("out").resolve("w.txt"), written);
    }

    @Test
    void listReturnsEmptyForMissingDirectory() throws IOException {
        SafeFs.FsScope split = new SafeFs.FsScope(base.resolve("never-written"), List.of(base));
        assertTrue(SafeFs.list(split, ".").isEmpty());
    }

    @Test
    void blocksReadThroughSymlinkEscapingBase() throws IOException {
        Files.writeString(outsideDir.resolve("secret.txt"), "leak");
        symlinkOrSkip(base.resolve("link"), outsideDir);
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(scope, "link/secret.txt"));
        assertThrows(FsPolicyException.class, () -> SafeFs.list(scope, "link"));
        assertFalse(SafeFs.exists(scope, "link/secret.txt"));
    }

    @Test
    void allowsReadThroughSymlinkStayingInsideBase() throws IOException {
        Files.writeString(base.resolve("hello.txt"), "world");
        symlinkOrSkip(base.resolve("alias.txt"), base.resolve("hello.txt"));
        assertEquals("world", SafeFs.readText(scope, "alias.txt"));
    }

    @Test
    void blocksWriteThroughSymlinkedDirectory() throws IOException {
        symlinkOrSkip(base.resolve("outlink"), outsideDir);
        assertThrows(FsPolicyException.class, () -> SafeFs.writeText(scope, "outlink/evil.txt", "x"));
        assertFalse(Files.exists(outsideDir.resolve("evil.txt")));
    }

    @Test
    void blocksWriteTargetingDanglingSymlink() throws IOException {
        symlinkOrSkip(base.resolve("dangling"), outsideDir.resolve("nope.txt"));
        assertThrows(FsPolicyException.class, () -> SafeFs.writeText(scope, "dangling", "x"));
        assertFalse(Files.exists(outsideDir.resolve("nope.txt")));
    }

    @Test
    void findDoesNotFollowSymlinkOutOfBase() throws IOException {
        Files.writeString(outsideDir.resolve("leak.json"), "{}");
        symlinkOrSkip(base.resolve("linkdir"), outsideDir);
        var found = SafeFs.find(scope, ".", "*.json", 0, "file");
        assertFalse(found.stream().anyMatch(p -> p.contains("leak")));
    }

    @Test
    void appendTextCreatesThenAppends() throws IOException {
        SafeFs.appendText(scope, "log.txt", "line1\n");
        SafeFs.appendText(scope, "log.txt", "line2\n");
        assertEquals("line1\nline2\n", Files.readString(base.resolve("log.txt")));
    }

    @Test
    void appendTextRejectsEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.appendText(scope, "../evil.txt", "x"));
    }

    @Test
    void editTextReplacesUniqueMatch() throws IOException {
        Files.writeString(base.resolve("e.txt"), "alpha beta gamma");
        SafeFs.EditResult result = SafeFs.editText(scope, "e.txt", "beta", "BETA", false);
        assertEquals(1, result.replacements());
        assertEquals("alpha BETA gamma", Files.readString(base.resolve("e.txt")));
    }

    @Test
    void editTextRejectsAmbiguousMatch() throws IOException {
        Files.writeString(base.resolve("e.txt"), "x x x");
        assertThrows(FsPolicyException.class, () -> SafeFs.editText(scope, "e.txt", "x", "y", false));
    }

    @Test
    void editTextReplaceAllReplacesEvery() throws IOException {
        Files.writeString(base.resolve("e.txt"), "x x x");
        SafeFs.EditResult result = SafeFs.editText(scope, "e.txt", "x", "y", true);
        assertEquals(3, result.replacements());
        assertEquals("y y y", Files.readString(base.resolve("e.txt")));
    }

    @Test
    void editTextRejectsNoMatch() throws IOException {
        Files.writeString(base.resolve("e.txt"), "hello");
        assertThrows(FsPolicyException.class, () -> SafeFs.editText(scope, "e.txt", "absent", "x", false));
    }

    @Test
    void editTextRejectsMissingFile() {
        assertThrows(FsPolicyException.class, () -> SafeFs.editText(scope, "nope.txt", "a", "b", false));
    }

    @Test
    void copyDuplicatesFileLeavingSourceIntact() throws IOException {
        SafeFs.copy(scope, "hello.txt", "copy.txt");
        assertEquals("world", Files.readString(base.resolve("copy.txt")));
        assertTrue(Files.exists(base.resolve("hello.txt")));
    }

    @Test
    void copyFromReadRootIntoWorkspace() throws IOException {
        Path workspace = base.resolve("ws");
        Files.createDirectories(workspace);
        Files.writeString(base.resolve("outside.txt"), "from-home");
        SafeFs.FsScope split = new SafeFs.FsScope(workspace, List.of(base));
        SafeFs.copy(split, base.resolve("outside.txt").toString(), "in.txt");
        assertEquals("from-home", Files.readString(workspace.resolve("in.txt")));
    }

    @Test
    void copyRejectsDestinationEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.copy(scope, "hello.txt", "../evil.txt"));
    }

    @Test
    void moveRelocatesAndRemovesSource() throws IOException {
        SafeFs.move(scope, "hello.txt", "moved.txt");
        assertEquals("world", Files.readString(base.resolve("moved.txt")));
        assertFalse(Files.exists(base.resolve("hello.txt")));
    }

    @Test
    void moveRejectsEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.move(scope, "hello.txt", "../evil.txt"));
    }

    @Test
    void deleteRemovesFile() throws IOException {
        assertTrue(SafeFs.delete(scope, "hello.txt"));
        assertFalse(Files.exists(base.resolve("hello.txt")));
    }

    @Test
    void deleteRejectsDirectory() {
        assertThrows(FsPolicyException.class, () -> SafeFs.delete(scope, "sub"));
    }

    @Test
    void deleteMissingReturnsFalse() throws IOException {
        assertFalse(SafeFs.delete(scope, "nope.txt"));
    }

    @Test
    void deleteDirRemovesTreeRecursively() throws IOException {
        long removed = SafeFs.deleteDir(scope, "sub");
        assertFalse(Files.exists(base.resolve("sub")));
        assertTrue(removed >= 2);
    }

    @Test
    void deleteDirRejectsPlainFile() {
        assertThrows(FsPolicyException.class, () -> SafeFs.deleteDir(scope, "hello.txt"));
    }

    @Test
    void deleteDirRejectsWorkspaceRoot() {
        assertThrows(FsPolicyException.class, () -> SafeFs.deleteDir(scope, "."));
    }

    @Test
    void searchInFilesFindsAcrossTree() throws IOException {
        Files.writeString(base.resolve("a.txt"), "needle here\nnope\n");
        Files.writeString(base.resolve("sub").resolve("b.txt"), "another needle\n");
        List<SafeFs.Match> hits = SafeFs.searchInFiles(scope, ".", "*.txt", "needle", false, 0, 0);
        assertEquals(2, hits.size());
    }

    @Test
    void searchInFilesRespectsGlob() throws IOException {
        Files.writeString(base.resolve("a.md"), "target\n");
        Files.writeString(base.resolve("b.txt"), "target\n");
        List<SafeFs.Match> hits = SafeFs.searchInFiles(scope, ".", "*.md", "target", false, 0, 0);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).file().endsWith("a.md"));
    }

    @Test
    void searchInFilesRespectsLimit() throws IOException {
        Files.writeString(base.resolve("a.txt"), "m\nm\nm\nm\n");
        List<SafeFs.Match> hits = SafeFs.searchInFiles(scope, ".", "*.txt", "m", false, 0, 2);
        assertEquals(2, hits.size());
    }

    @Test
    void searchInFilesReportsLineNumbers() throws IOException {
        Files.writeString(base.resolve("a.txt"), "one\ntwo\nthree\n");
        List<SafeFs.Match> hits = SafeFs.searchInFiles(scope, ".", "*.txt", "two", false, 0, 0);
        assertEquals(1, hits.size());
        assertEquals(2, hits.get(0).line());
    }

    @Test
    void searchInFilesRejectsEmptyPattern() {
        assertThrows(FsPolicyException.class, () -> SafeFs.searchInFiles(scope, ".", "*", "", false, 0, 0));
    }
}
