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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

public final class SafeFs {

    public static final class FsPolicyException extends JsHelperException {
        public FsPolicyException(Kind kind, String reason, String message) {
            super(kind, "safety.fs", reason, message);
        }
    }

    static FsPolicyException reject(JsHelperException.Kind kind, String reason, String message) {
        SandboxGuardMetrics.recordBlock("fs", reason);
        return new FsPolicyException(kind, reason, message);
    }

    public record FileStat(long size, long mtime, boolean directory) {}

    public record EditResult(Path path, int replacements) {}

    public record Match(String file, int line, String text) {}

    public record FsScope(Path workspace, List<Path> readRoots) {
        public FsScope {
            workspace = workspace.toAbsolutePath().normalize();
            readRoots = readRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
        }

        public static FsScope confined(Path base) {
            return new FsScope(base, List.of(base));
        }
    }

    private SafeFs() {}

    public static String readText(FsScope scope, String userPath) throws IOException {
        return Files.readString(resolveRead(scope, userPath), StandardCharsets.UTF_8);
    }

    public static byte[] readBytes(FsScope scope, String userPath) throws IOException {
        return Files.readAllBytes(resolveRead(scope, userPath));
    }

    public static Path writeText(FsScope scope, String userPath, String content) throws IOException {
        Path target = resolveWrite(scope, userPath);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
        return target;
    }

    public static Path writeBytes(FsScope scope, String userPath, byte[] content) throws IOException {
        Path target = resolveWrite(scope, userPath);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, content == null ? new byte[0] : content);
        return target;
    }

    public static List<String> list(FsScope scope, String userDir) throws IOException {
        String input = userDir == null || userDir.isEmpty() ? "." : userDir;
        Path dir = resolveRead(scope, input);
        if (!Files.exists(dir)) {
            return new ArrayList<>();
        }
        if (!Files.isDirectory(dir)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "not-a-directory",
                    "not a directory: " + userDir);
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> out.add(displayPath(scope, p)));
        }
        return out;
    }

    public static boolean exists(FsScope scope, String userPath) {
        try {
            return Files.exists(resolveRead(scope, userPath));
        } catch (FsPolicyException e) {
            return false;
        }
    }

    public static FileStat stat(FsScope scope, String userPath) throws IOException {
        Path target = resolveRead(scope, userPath);
        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
        return new FileStat(attrs.size(), attrs.lastModifiedTime().toMillis(), attrs.isDirectory());
    }

    public static List<String> grep(FsScope scope, String userPath, String pattern,
                                    boolean caseInsensitive, int limit, boolean numbered) throws IOException {
        if (pattern == null || pattern.isEmpty())
            throw reject(JsHelperException.Kind.INVALID_INPUT, "grep-pattern-required",
                    "grep: pattern required");
        Path target = resolveRead(scope, userPath);
        Pattern regex;
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            regex = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "grep-invalid-pattern",
                    "grep: invalid pattern: " + e.getMessage());
        }
        int cap = limit > 0 ? limit : 10_000;
        List<String> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            int[] lineNo = {0};
            lines.forEach(line -> {
                lineNo[0]++;
                if (out.size() >= cap) return;
                if (regex.matcher(line).find()) {
                    out.add(numbered ? lineNo[0] + ":" + line : line);
                }
            });
        }
        return out;
    }

    public static long lineCount(FsScope scope, String userPath) throws IOException {
        Path target = resolveRead(scope, userPath);
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    public static List<String> slice(FsScope scope, String userPath, Integer start, Integer end) throws IOException {
        Path target = resolveRead(scope, userPath);
        List<String> all;
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            all = lines.toList();
        }
        int size = all.size();
        int s = normalizeIndex(start, 0, size);
        int e = normalizeIndex(end, size, size);
        if (s >= e) return new ArrayList<>();
        return new ArrayList<>(all.subList(s, e));
    }

    private static int normalizeIndex(Integer idx, int defaultVal, int size) {
        if (idx == null) return defaultVal;
        int v = idx;
        if (v < 0) v = size + v;
        if (v < 0) v = 0;
        if (v > size) v = size;
        return v;
    }

    public static List<String> cut(FsScope scope, String userPath, String delimiter, List<Integer> fields,
                                   boolean regexDelimiter) throws IOException {
        Path target = resolveRead(scope, userPath);
        if (fields == null || fields.isEmpty()) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "cut-fields-required",
                    "cut: fields required");
        }
        String delim = delimiter == null || delimiter.isEmpty() ? "\t" : delimiter;
        String splitter = regexDelimiter ? delim : Pattern.quote(delim);
        String joiner = regexDelimiter ? "\t" : delim;
        Pattern compiled;
        try {
            compiled = Pattern.compile(splitter);
        } catch (PatternSyntaxException e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "cut-invalid-delimiter",
                    "cut: invalid delimiter: " + e.getMessage());
        }
        List<String> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                String[] parts = compiled.split(line, -1);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < fields.size(); i++) {
                    int idx = fields.get(i) - 1;
                    if (i > 0) sb.append(joiner);
                    if (idx >= 0 && idx < parts.length) sb.append(parts[idx]);
                }
                out.add(sb.toString());
            });
        }
        return out;
    }

    public static List<String> sort(FsScope scope, String userPath, boolean reverse, boolean numeric,
                                    boolean caseInsensitive, boolean unique) throws IOException {
        Path target = resolveRead(scope, userPath);
        Comparator<String> cmp;
        if (numeric) {
            cmp = Comparator.comparingDouble(s -> {
                try {
                    return Double.parseDouble(s.trim());
                } catch (NumberFormatException e) {
                    return Double.MAX_VALUE;
                }
            });
        } else if (caseInsensitive) {
            cmp = String.CASE_INSENSITIVE_ORDER;
        } else {
            cmp = Comparator.naturalOrder();
        }
        if (reverse) cmp = cmp.reversed();
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            Stream<String> stream = lines;
            if (unique) {
                Set<String> seen = new LinkedHashSet<>();
                stream.forEach(seen::add);
                return seen.stream().sorted(cmp).toList();
            }
            return stream.sorted(cmp).toList();
        }
    }

    public static List<String> find(FsScope scope, String userDir, String glob, int maxDepth, String type)
            throws IOException {
        String input = userDir == null || userDir.isEmpty() ? "." : userDir;
        Path root = resolveRead(scope, input);
        if (!Files.isDirectory(root)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "find-not-a-directory",
                    "find: not a directory: " + userDir);
        }
        String pattern = glob == null || glob.isEmpty() ? "*" : glob;
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (Exception e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "find-invalid-glob",
                    "find: invalid glob: " + e.getMessage());
        }
        int depth = maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth;
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, depth)) {
            walk.forEach(p -> {
                if (p.equals(root)) return;
                boolean isFile = Files.isRegularFile(p);
                boolean isDir = Files.isDirectory(p);
                if ("file".equals(type) && !isFile) return;
                if ("dir".equals(type) && !isDir) return;
                if (matcher.matches(p.getFileName())) {
                    out.add(displayPath(scope, p));
                }
            });
        }
        return out;
    }

    public static Path appendText(FsScope scope, String userPath, String content) throws IOException {
        Path target = resolveWrite(scope, userPath);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return target;
    }

    public static EditResult editText(FsScope scope, String userPath, String oldString, String newString,
                                      boolean replaceAll) throws IOException {
        if (oldString == null || oldString.isEmpty()) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "edit-old-required",
                    "editText: oldString required");
        }
        Path target = resolveWrite(scope, userPath);
        if (!Files.isRegularFile(target)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "edit-file-missing",
                    "editText: file not found: " + userPath);
        }
        String content = Files.readString(target, StandardCharsets.UTF_8);
        int first = content.indexOf(oldString);
        if (first < 0) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "edit-no-match",
                    "editText: oldString not found in " + userPath);
        }
        String replacement = newString == null ? "" : newString;
        int count;
        String updated;
        if (replaceAll) {
            count = countOccurrences(content, oldString);
            updated = content.replace(oldString, replacement);
        } else {
            int second = content.indexOf(oldString, first + oldString.length());
            if (second >= 0) {
                throw reject(JsHelperException.Kind.INVALID_INPUT, "edit-ambiguous",
                        "editText: oldString matches multiple times in " + userPath
                                + " (use replaceAll or a longer, unique oldString)");
            }
            count = 1;
            updated = content.substring(0, first) + replacement + content.substring(first + oldString.length());
        }
        Files.writeString(target, updated, StandardCharsets.UTF_8);
        return new EditResult(target, count);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    public static Path copy(FsScope scope, String fromPath, String toPath) throws IOException {
        Path source = resolveRead(scope, fromPath);
        Path target = resolveWrite(scope, toPath);
        if (!Files.isRegularFile(source)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "copy-source-not-file",
                    "copy: source is not a regular file: " + fromPath);
        }
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static Path move(FsScope scope, String fromPath, String toPath) throws IOException {
        Path source = resolveWrite(scope, fromPath);
        Path target = resolveWrite(scope, toPath);
        if (!Files.exists(source)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "move-source-missing",
                    "move: source does not exist: " + fromPath);
        }
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public static boolean delete(FsScope scope, String userPath) throws IOException {
        Path target = resolveWrite(scope, userPath);
        if (Files.isDirectory(target)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "delete-is-directory",
                    "delete: path is a directory (use deleteDir): " + userPath);
        }
        return Files.deleteIfExists(target);
    }

    public static long deleteDir(FsScope scope, String userPath) throws IOException {
        Path target = resolveWrite(scope, userPath);
        if (target.equals(scope.workspace())) {
            throw reject(JsHelperException.Kind.SECURITY, "deletedir-workspace-root",
                    "deleteDir: refusing to delete the workspace root");
        }
        if (!Files.exists(target)) {
            return 0L;
        }
        if (!Files.isDirectory(target)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "deletedir-not-a-directory",
                    "deleteDir: path is not a directory (use delete): " + userPath);
        }
        List<Path> entries;
        try (Stream<Path> walk = Files.walk(target)) {
            entries = walk.sorted(Comparator.reverseOrder()).toList();
        }
        long removed = 0L;
        for (Path p : entries) {
            if (Files.deleteIfExists(p)) removed++;
        }
        return removed;
    }

    public static List<Match> searchInFiles(FsScope scope, String userDir, String glob, String pattern,
                                            boolean caseInsensitive, int maxDepth, int limit) throws IOException {
        if (pattern == null || pattern.isEmpty()) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "search-pattern-required",
                    "searchInFiles: pattern required");
        }
        String input = userDir == null || userDir.isEmpty() ? "." : userDir;
        Path root = resolveRead(scope, input);
        if (!Files.isDirectory(root)) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "search-not-a-directory",
                    "searchInFiles: not a directory: " + userDir);
        }
        String globPattern = glob == null || glob.isEmpty() ? "*" : glob;
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        } catch (Exception e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "search-invalid-glob",
                    "searchInFiles: invalid glob: " + e.getMessage());
        }
        Pattern regex;
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            regex = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "search-invalid-pattern",
                    "searchInFiles: invalid pattern: " + e.getMessage());
        }
        int depth = maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth;
        int cap = limit > 0 ? limit : 1_000;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, depth)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .forEach(files::add);
        }
        List<Match> out = new ArrayList<>();
        for (Path file : files) {
            if (out.size() >= cap) break;
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (out.size() >= cap) break;
                if (regex.matcher(lines.get(i)).find()) {
                    out.add(new Match(displayPath(scope, file), i + 1, lines.get(i)));
                }
            }
        }
        return out;
    }

    static Path resolveRead(FsScope scope, String userPath) {
        Path candidate = toCandidate(scope.workspace(), userPath);
        Path real = realCanonical(candidate);
        for (Path root : scope.readRoots()) {
            if (real.startsWith(realCanonical(root))) return candidate;
        }
        throw reject(JsHelperException.Kind.SECURITY, "path-escapes-base",
                "path escapes base: " + userPath);
    }

    static Path resolveWrite(FsScope scope, String userPath) {
        Path candidate = toCandidate(scope.workspace(), userPath);
        if (Files.isSymbolicLink(candidate)) {
            throw reject(JsHelperException.Kind.SECURITY, "write-through-symlink",
                    "writes may not target a symbolic link: " + userPath);
        }
        if (realCanonical(candidate).startsWith(realCanonical(scope.workspace()))) return candidate;
        throw reject(JsHelperException.Kind.SECURITY, "write-outside-workspace",
                "writes are confined to the workspace: " + userPath);
    }

    private static Path realCanonical(Path p) {
        Path cursor = p;
        while (cursor != null && !Files.exists(cursor)) {
            cursor = cursor.getParent();
        }
        if (cursor == null) return p;
        try {
            Path real = cursor.toRealPath();
            return cursor.equals(p) ? real : real.resolve(cursor.relativize(p));
        } catch (IOException e) {
            return p;
        }
    }

    private static Path toCandidate(Path base, String userPath) {
        if (userPath == null || userPath.isEmpty()) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "path-required", "path required");
        }
        try {
            Path p = Paths.get(userPath);
            return (p.isAbsolute() ? p : base.resolve(userPath)).normalize();
        } catch (RuntimeException e) {
            throw reject(JsHelperException.Kind.INVALID_INPUT, "invalid-path",
                    "invalid path: " + userPath);
        }
    }

    private static String displayPath(FsScope scope, Path p) {
        return p.startsWith(scope.workspace())
                ? scope.workspace().relativize(p).toString() : p.toString();
    }
}
