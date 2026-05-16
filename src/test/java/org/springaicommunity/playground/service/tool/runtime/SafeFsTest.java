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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.tool.runtime.SafeFs.FsPolicyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeFsTest {

    @TempDir
    Path tempDir;

    private Path base;

    @BeforeEach
    void setUp() throws IOException {
        base = tempDir.toAbsolutePath().normalize();
        Files.writeString(base.resolve("hello.txt"), "world");
        Files.createDirectory(base.resolve("sub"));
        Files.writeString(base.resolve("sub").resolve("nested.txt"), "deep");
    }

    @Test
    void readTextReturnsFileContent() throws IOException {
        assertEquals("world", SafeFs.readText(base, "hello.txt"));
    }

    @Test
    void readTextHandlesNestedPath() throws IOException {
        assertEquals("deep", SafeFs.readText(base, "sub/nested.txt"));
    }

    @Test
    void writeTextCreatesFile() throws IOException {
        SafeFs.writeText(base, "new.txt", "content");
        assertEquals("content", Files.readString(base.resolve("new.txt")));
    }

    @Test
    void writeTextCreatesParentDirs() throws IOException {
        SafeFs.writeText(base, "a/b/c/note.txt", "nested write");
        assertTrue(Files.exists(base.resolve("a/b/c/note.txt")));
    }

    @Test
    void listReturnsTopLevelEntries() throws IOException {
        var entries = SafeFs.list(base, ".");
        assertTrue(entries.contains("hello.txt"));
        assertTrue(entries.contains("sub"));
    }

    @Test
    void existsTrueForExistingFile() {
        assertTrue(SafeFs.exists(base, "hello.txt"));
    }

    @Test
    void existsFalseForMissingFile() {
        assertFalse(SafeFs.exists(base, "nope.txt"));
    }

    @Test
    void statReturnsAttrs() throws IOException {
        SafeFs.FileStat s = SafeFs.stat(base, "hello.txt");
        assertEquals(5, s.size());
        assertFalse(s.directory());
        assertTrue(s.mtime() > 0);
    }

    @Test
    void blocksParentEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(base, "../etc/passwd"));
    }

    @Test
    void blocksAbsolutePath() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(base, "/etc/passwd"));
    }

    @Test
    void blocksDeepEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(base, "sub/../../etc/passwd"));
    }

    @Test
    void blocksEmptyPath() {
        assertThrows(FsPolicyException.class, () -> SafeFs.readText(base, ""));
    }

    @Test
    void existsFalseForEscape() {
        assertFalse(SafeFs.exists(base, "../../../etc/passwd"));
    }

    @Test
    void normalizesInnerDotDot() throws IOException {
        assertEquals("deep", SafeFs.readText(base, "sub/./nested.txt"));
        assertEquals("world", SafeFs.readText(base, "sub/../hello.txt"));
    }

    @Test
    void grepFindsMatchingLines() throws IOException {
        Files.writeString(base.resolve("log.txt"), "INFO start\nERROR failed\nINFO ok\nERROR boom\n");
        var matches = SafeFs.grep(base, "log.txt", "ERROR", false, 0, false);
        assertEquals(2, matches.size());
        assertEquals("ERROR failed", matches.get(0));
        assertEquals("ERROR boom", matches.get(1));
    }

    @Test
    void grepCaseInsensitive() throws IOException {
        Files.writeString(base.resolve("log.txt"), "info\nINFO\nInFo\n");
        var matches = SafeFs.grep(base, "log.txt", "info", true, 0, false);
        assertEquals(3, matches.size());
    }

    @Test
    void grepNumbered() throws IOException {
        Files.writeString(base.resolve("log.txt"), "a\nb\na\n");
        var matches = SafeFs.grep(base, "log.txt", "a", false, 0, true);
        assertEquals("1:a", matches.get(0));
        assertEquals("3:a", matches.get(1));
    }

    @Test
    void grepRespectsLimit() throws IOException {
        Files.writeString(base.resolve("log.txt"), "x\nx\nx\nx\nx\n");
        var matches = SafeFs.grep(base, "log.txt", "x", false, 2, false);
        assertEquals(2, matches.size());
    }

    @Test
    void grepRejectsEmptyPattern() {
        assertThrows(FsPolicyException.class, () -> SafeFs.grep(base, "hello.txt", "", false, 0, false));
    }

    @Test
    void grepRejectsInvalidRegex() {
        assertThrows(FsPolicyException.class, () -> SafeFs.grep(base, "hello.txt", "[unclosed", false, 0, false));
    }

    @Test
    void lineCountSingleLine() throws IOException {
        assertEquals(1, SafeFs.lineCount(base, "hello.txt"));
    }

    @Test
    void lineCountMultiLine() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "a\nb\nc\nd\n");
        assertEquals(4, SafeFs.lineCount(base, "multi.txt"));
    }

    @Test
    void sliceHead() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(base, "multi.txt", 0, 3);
        assertEquals(3, lines.size());
        assertEquals("1", lines.get(0));
        assertEquals("3", lines.get(2));
    }

    @Test
    void sliceTailNegative() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(base, "multi.txt", -2, null);
        assertEquals(2, lines.size());
        assertEquals("4", lines.get(0));
        assertEquals("5", lines.get(1));
    }

    @Test
    void sliceMiddle() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        var lines = SafeFs.slice(base, "multi.txt", 1, 4);
        assertEquals(3, lines.size());
        assertEquals("2", lines.get(0));
        assertEquals("4", lines.get(2));
    }

    @Test
    void sliceFullDefault() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "a\nb\nc\n");
        var lines = SafeFs.slice(base, "multi.txt", null, null);
        assertEquals(3, lines.size());
    }

    @Test
    void sliceOutOfRange() throws IOException {
        Files.writeString(base.resolve("two.txt"), "x\ny\n");
        var lines = SafeFs.slice(base, "two.txt", 0, 100);
        assertEquals(2, lines.size());
    }

    @Test
    void pipelineBlocksEscape() {
        assertThrows(FsPolicyException.class,
                () -> SafeFs.grep(base, "../escape", "x", false, 0, false));
        assertThrows(FsPolicyException.class,
                () -> SafeFs.slice(base, "/etc/passwd", 0, 1));
    }

    @Test
    void cutTabDelimitedExtractsColumn() throws IOException {
        Files.writeString(base.resolve("tsv.txt"), "a\t1\tx\nb\t2\ty\nc\t3\tz\n");
        var rows = SafeFs.cut(base, "tsv.txt", "\t", java.util.List.of(2), false);
        assertEquals(3, rows.size());
        assertEquals("1", rows.get(0));
        assertEquals("3", rows.get(2));
    }

    @Test
    void cutMultipleFields() throws IOException {
        Files.writeString(base.resolve("csv.txt"), "a,b,c,d\ne,f,g,h\n");
        var rows = SafeFs.cut(base, "csv.txt", ",", java.util.List.of(1, 3), false);
        assertEquals("a,c", rows.get(0));
        assertEquals("e,g", rows.get(1));
    }

    @Test
    void cutWhitespaceRegex() throws IOException {
        Files.writeString(base.resolve("log.txt"), "INFO   start\nERROR    failed\n");
        var rows = SafeFs.cut(base, "log.txt", "\\s+", java.util.List.of(1), true);
        assertEquals("INFO", rows.get(0));
        assertEquals("ERROR", rows.get(1));
    }

    @Test
    void cutHandlesMissingField() throws IOException {
        Files.writeString(base.resolve("sparse.txt"), "a,b\nc\n");
        var rows = SafeFs.cut(base, "sparse.txt", ",", java.util.List.of(2), false);
        assertEquals("b", rows.get(0));
        assertEquals("", rows.get(1));
    }

    @Test
    void cutRejectsEmptyFields() {
        assertThrows(FsPolicyException.class,
                () -> SafeFs.cut(base, "hello.txt", ",", java.util.List.of(), false));
    }

    @Test
    void sortLexicographic() throws IOException {
        Files.writeString(base.resolve("u.txt"), "banana\napple\ncherry\n");
        var sorted = SafeFs.sort(base, "u.txt", false, false, false, false);
        assertEquals("apple", sorted.get(0));
        assertEquals("cherry", sorted.get(2));
    }

    @Test
    void sortReverse() throws IOException {
        Files.writeString(base.resolve("u.txt"), "a\nb\nc\n");
        var sorted = SafeFs.sort(base, "u.txt", true, false, false, false);
        assertEquals("c", sorted.get(0));
    }

    @Test
    void sortNumeric() throws IOException {
        Files.writeString(base.resolve("n.txt"), "10\n2\n100\n5\n");
        var sorted = SafeFs.sort(base, "n.txt", false, true, false, false);
        assertEquals("2", sorted.get(0));
        assertEquals("100", sorted.get(3));
    }

    @Test
    void sortUnique() throws IOException {
        Files.writeString(base.resolve("d.txt"), "b\na\nb\nc\na\n");
        var sorted = SafeFs.sort(base, "d.txt", false, false, false, true);
        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0));
    }

    @Test
    void sortCaseInsensitive() throws IOException {
        Files.writeString(base.resolve("c.txt"), "Banana\napple\nCherry\n");
        var sorted = SafeFs.sort(base, "c.txt", false, false, true, false);
        assertEquals("apple", sorted.get(0));
        assertEquals("Banana", sorted.get(1));
    }

    @Test
    void findByGlob() throws IOException {
        Files.writeString(base.resolve("a.json"), "{}");
        Files.writeString(base.resolve("b.json"), "{}");
        Files.writeString(base.resolve("c.txt"), "");
        var json = SafeFs.find(base, ".", "*.json", 0, "file");
        assertEquals(2, json.size());
    }

    @Test
    void findRecursive() throws IOException {
        Files.writeString(base.resolve("sub").resolve("deep.json"), "{}");
        Files.writeString(base.resolve("top.json"), "{}");
        var json = SafeFs.find(base, ".", "*.json", 0, "file");
        assertTrue(json.stream().anyMatch(p -> p.endsWith("deep.json")));
        assertTrue(json.stream().anyMatch(p -> p.endsWith("top.json")));
    }

    @Test
    void findFilterByType() throws IOException {
        var dirs = SafeFs.find(base, ".", "*", 1, "dir");
        assertTrue(dirs.stream().anyMatch(p -> p.endsWith("sub")));
        assertFalse(dirs.stream().anyMatch(p -> p.endsWith("hello.txt")));
    }

    @Test
    void findMaxDepth() throws IOException {
        Files.writeString(base.resolve("sub").resolve("deep.txt"), "x");
        var depth1 = SafeFs.find(base, ".", "*", 1, "file");
        assertTrue(depth1.stream().anyMatch(p -> p.equals("hello.txt")));
        assertFalse(depth1.stream().anyMatch(p -> p.contains("deep")));
    }

    @Test
    void findRejectsBadGlob() {
        assertThrows(FsPolicyException.class, () -> SafeFs.find(base, ".", "[unclosed", 0, null));
    }

    @Test
    void findRejectsEscape() {
        assertThrows(FsPolicyException.class, () -> SafeFs.find(base, "..", "*", 0, null));
    }
}
