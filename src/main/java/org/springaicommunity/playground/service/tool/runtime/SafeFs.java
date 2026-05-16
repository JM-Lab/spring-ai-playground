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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
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
        public FsPolicyException(String message) {
            super(Kind.SECURITY, "safety.fs", "security-violation", message);
        }

        public FsPolicyException(Kind kind, String reason, String message) {
            super(kind, "safety.fs", reason, message);
        }
    }

    public record FileStat(long size, long mtime, boolean directory) {}

    private SafeFs() {}

    public static String readText(Path base, String userPath) throws IOException {
        return Files.readString(resolveAndValidate(base, userPath), StandardCharsets.UTF_8);
    }

    public static void writeText(Path base, String userPath, String content) throws IOException {
        Path target = resolveAndValidate(base, userPath);
        if (target.getParent() != null && !Files.exists(target.getParent())) {
            Files.createDirectories(target.getParent());
        }
        Files.writeString(target, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public static List<String> list(Path base, String userDir) throws IOException {
        String input = userDir == null || userDir.isEmpty() ? "." : userDir;
        Path dir = resolveAndValidate(base, input);
        if (!Files.isDirectory(dir)) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "not-a-directory",
                    "not a directory: " + userDir);
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> out.add(base.relativize(p).toString()));
        }
        return out;
    }

    public static boolean exists(Path base, String userPath) {
        try {
            return Files.exists(resolveAndValidate(base, userPath));
        } catch (FsPolicyException e) {
            return false;
        }
    }

    public static FileStat stat(Path base, String userPath) throws IOException {
        Path target = resolveAndValidate(base, userPath);
        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
        return new FileStat(attrs.size(), attrs.lastModifiedTime().toMillis(), attrs.isDirectory());
    }

    public static List<String> grep(Path base, String userPath, String pattern,
                                    boolean caseInsensitive, int limit, boolean numbered) throws IOException {
        if (pattern == null || pattern.isEmpty())
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "grep-pattern-required",
                    "grep: pattern required");
        Path target = resolveAndValidate(base, userPath);
        Pattern regex;
        try {
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            regex = Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "grep-invalid-pattern",
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

    public static long lineCount(Path base, String userPath) throws IOException {
        Path target = resolveAndValidate(base, userPath);
        try (Stream<String> lines = Files.lines(target, StandardCharsets.UTF_8)) {
            return lines.count();
        }
    }

    public static List<String> slice(Path base, String userPath, Integer start, Integer end) throws IOException {
        Path target = resolveAndValidate(base, userPath);
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

    public static List<String> cut(Path base, String userPath, String delimiter, List<Integer> fields,
                                   boolean regexDelimiter) throws IOException {
        Path target = resolveAndValidate(base, userPath);
        if (fields == null || fields.isEmpty()) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "cut-fields-required",
                    "cut: fields required");
        }
        String delim = delimiter == null || delimiter.isEmpty() ? "\t" : delimiter;
        String splitter = regexDelimiter ? delim : Pattern.quote(delim);
        String joiner = regexDelimiter ? "\t" : delim;
        Pattern compiled;
        try {
            compiled = Pattern.compile(splitter);
        } catch (PatternSyntaxException e) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "cut-invalid-delimiter",
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

    public static List<String> sort(Path base, String userPath, boolean reverse, boolean numeric,
                                    boolean caseInsensitive, boolean unique) throws IOException {
        Path target = resolveAndValidate(base, userPath);
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

    public static List<String> find(Path base, String userDir, String glob, int maxDepth, String type)
            throws IOException {
        String input = userDir == null || userDir.isEmpty() ? "." : userDir;
        Path root = resolveAndValidate(base, input);
        if (!Files.isDirectory(root)) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "find-not-a-directory",
                    "find: not a directory: " + userDir);
        }
        String pattern = glob == null || glob.isEmpty() ? "*" : glob;
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        } catch (Exception e) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "find-invalid-glob",
                    "find: invalid glob: " + e.getMessage());
        }
        int depth = maxDepth <= 0 ? Integer.MAX_VALUE : maxDepth;
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root, depth, FileVisitOption.FOLLOW_LINKS)) {
            walk.forEach(p -> {
                if (p.equals(root)) return;
                boolean isFile = Files.isRegularFile(p);
                boolean isDir = Files.isDirectory(p);
                if ("file".equals(type) && !isFile) return;
                if ("dir".equals(type) && !isDir) return;
                if (matcher.matches(p.getFileName())) {
                    out.add(base.relativize(p).toString());
                }
            });
        }
        return out;
    }

    static Path resolveAndValidate(Path base, String userPath) {
        if (userPath == null || userPath.isEmpty()) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "path-required", "path required");
        }
        Path candidate;
        try {
            candidate = base.resolve(userPath).normalize();
        } catch (Exception e) {
            throw new FsPolicyException(JsHelperException.Kind.INVALID_INPUT, "invalid-path",
                    "invalid path: " + userPath);
        }
        if (!candidate.startsWith(base)) {
            throw new FsPolicyException(JsHelperException.Kind.SECURITY, "path-escapes-base",
                    "path escapes base: " + userPath);
        }
        return candidate;
    }
}
