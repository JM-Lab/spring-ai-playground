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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.playground.service.tool.policy.EffectivePolicyResolver.EffectivePolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsRuntimeFsTest {

    @TempDir
    Path tempDir;

    private Context context;
    private Path base;

    private static final EffectivePolicy FS_ON = new EffectivePolicy(
            Set.of(), Set.of(), false, true, true, false, false, 500_000L, 30L, false, null, null, null);
    private static final EffectivePolicy FS_OFF = new EffectivePolicy(
            Set.of(), Set.of(), false, false, false, false, false, 500_000L, 30L, false, null, null, null);
    private static final EffectivePolicy FS_READ_ONLY = new EffectivePolicy(
            Set.of(), Set.of(), false, true, false, false, false, 500_000L, 30L, false, null, null, null);

    @BeforeEach
    void setUp() throws IOException {
        base = tempDir.toAbsolutePath().normalize();
        Files.writeString(base.resolve("hello.txt"), "world");
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> false)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void fsNotInjectedWhenAllowFileIoFalse() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_OFF, base);
        Value v = context.eval("js", "typeof safety.fs");
        assertEquals("undefined", v.asString());
    }

    @Test
    void fsNotInjectedWhenBasePathNull() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, null);
        Value v = context.eval("js", "typeof safety.fs");
        assertEquals("undefined", v.asString());
    }

    @Test
    void fsReadText() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js", "safety.fs.readText('hello.txt')");
        assertEquals("world", v.asString());
    }

    @Test
    void fsWriteText() throws IOException {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        context.eval("js", "safety.fs.writeText('new.txt', 'created')");
        assertEquals("created", Files.readString(base.resolve("new.txt")));
    }

    @Test
    void fsExists() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        assertTrue(context.eval("js", "safety.fs.exists('hello.txt')").asBoolean());
        assertEquals(false, context.eval("js", "safety.fs.exists('missing.txt')").asBoolean());
    }

    @Test
    void fsList() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js",
                "Array.from(safety.fs.list('.')).includes('hello.txt')");
        assertTrue(v.asBoolean());
    }

    @Test
    void fsStat() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js", "safety.fs.stat('hello.txt').size");
        assertEquals(5, v.asInt());
    }

    @Test
    void fsBlocksTraversal() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "safety.fs.readText('../../../etc/passwd')"));
    }

    @Test
    void fsBlocksAbsolutePath() {
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "safety.fs.readText('/etc/passwd')"));
    }

    @Test
    void fsGrep() throws IOException {
        Files.writeString(base.resolve("log.txt"), "INFO ok\nERROR boom\nINFO done\n");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js",
                "Array.from(safety.fs.grep('ERROR', 'log.txt')).length");
        assertEquals(1, v.asInt());
    }

    @Test
    void fsLineCount() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "a\nb\nc\nd\n");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        assertEquals(4, context.eval("js", "safety.fs.lineCount('multi.txt')").asInt());
    }

    @Test
    void fsSliceHeadTail() throws IOException {
        Files.writeString(base.resolve("multi.txt"), "1\n2\n3\n4\n5\n");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value head = context.eval("js", "Array.from(safety.fs.slice('multi.txt', 0, 2)).join(',')");
        Value tail = context.eval("js", "Array.from(safety.fs.slice('multi.txt', -2)).join(',')");
        assertEquals("1,2", head.asString());
        assertEquals("4,5", tail.asString());
    }

    @Test
    void fsCut() throws IOException {
        Files.writeString(base.resolve("tsv.txt"), "a\t1\tx\nb\t2\ty\n");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js",
                "Array.from(safety.fs.cut('tsv.txt', {delimiter: '\\t', fields: [1, 3]})).join('|')");
        assertEquals("a\tx|b\ty", v.asString());
    }

    @Test
    void fsSortUnique() throws IOException {
        Files.writeString(base.resolve("d.txt"), "b\na\nb\nc\na\n");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js",
                "Array.from(safety.fs.sort('d.txt', {unique: true})).join(',')");
        assertEquals("a,b,c", v.asString());
    }

    @Test
    void fsFindGlob() throws IOException {
        Files.writeString(base.resolve("a.json"), "{}");
        Files.writeString(base.resolve("b.txt"), "x");
        JsRuntimeGlobals.installSafety(context.getBindings("js"), FS_ON, base);
        Value v = context.eval("js",
                "Array.from(safety.fs.find('.', '*.json', {type: 'file'})).length");
        assertEquals(1, v.asInt());
    }
}
