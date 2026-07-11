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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatImageStoreTest {

    private static final String CONV = "conv-1";

    @TempDir
    Path tempDir;

    private ChatImageStore storeWith(Path base) {
        return new ChatImageStore(new ToolWorkspace(buildOptions(base.toString()), base));
    }

    private Path convRoot() {
        return tempDir.resolve(CONV);
    }

    @Test
    void testStoresImageAndMetaWithEmbeddedExif() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        byte[] png = {1, 2, 3, 4};
        ChatImageStore.Stored stored = store.store(CONV, png, "photo.png", "image/png",
                "{\"Make\":\"Apple\",\"DateTimeOriginal\":\"2024:03:15 10:00:00\"}");

        assertFalse(stored.deduped());
        assertTrue(stored.path().startsWith("images/"));
        assertTrue(stored.path().endsWith(".png"));
        assertEquals(4, stored.bytes());
        assertTrue(Files.exists(convRoot().resolve(stored.path())));

        Path meta = convRoot().resolve(ChatImageStore.IMAGES_DIR + "/" + stored.hash() + ".json");
        assertTrue(Files.exists(meta));
        String metaContent = Files.readString(meta);
        assertTrue(metaContent.contains("photo.png"));
        assertTrue(metaContent.contains("Apple"));
        assertTrue(metaContent.contains(stored.hash()));
    }

    @Test
    void testDedupSkipsSecondWriteForSameBytes() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        byte[] data = "identical".getBytes();
        ChatImageStore.Stored first = store.store(CONV, data, "a.png", "image/png", null);
        ChatImageStore.Stored second = store.store(CONV, data, "b.png", "image/png", null);

        assertFalse(first.deduped());
        assertTrue(second.deduped());
        assertEquals(first.hash(), second.hash());
        assertEquals(first.path(), second.path());
    }

    @Test
    void testDifferentBytesProduceDifferentHash() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        ChatImageStore.Stored a = store.store(CONV, new byte[]{1}, "a.png", "image/png", null);
        ChatImageStore.Stored b = store.store(CONV, new byte[]{2}, "b.png", "image/png", null);
        assertNotEquals(a.hash(), b.hash());
    }

    @Test
    void testExtensionFromMimeType() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        assertTrue(store.store(CONV, new byte[]{1}, "x", "image/jpeg", null).path().endsWith(".jpg"));
        assertTrue(store.store(CONV, new byte[]{2}, "y", "image/webp", null).path().endsWith(".webp"));
        assertTrue(store.store(CONV, new byte[]{3}, "z", "image/gif", null).path().endsWith(".gif"));
    }

    @Test
    void testInvalidExifFallsBackToRaw() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        ChatImageStore.Stored stored = store.store(CONV, new byte[]{9}, "p.png", "image/png", "not-json{{{");
        Path meta = convRoot().resolve(ChatImageStore.IMAGES_DIR + "/" + stored.hash() + ".json");
        assertTrue(Files.readString(meta).contains("exifRaw"));
    }

    @Test
    void testLoadByHashAndByPath() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        byte[] png = {5, 6, 7, 8, 9};
        ChatImageStore.Stored stored = store.store(CONV, png, "photo.png", "image/png", null);

        ChatImageStore.Loaded byHash = store.load(CONV, stored.hash()).orElseThrow();
        assertArrayEquals(png, byHash.bytes());
        assertEquals("image/png", byHash.mimeType());
        assertEquals(stored.hash(), byHash.hash());

        assertArrayEquals(png, store.load(CONV, stored.path()).orElseThrow().bytes());
    }

    @Test
    void testLoadByOriginalFileNameOrShortHash() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        byte[] png = {10, 20, 30, 40};
        ChatImageStore.Stored stored = store.store(CONV, png, "beach.png", "image/png", null);

        assertArrayEquals(png, store.load(CONV, "beach.png").orElseThrow().bytes());
        assertArrayEquals(png, store.load(CONV, "beach").orElseThrow().bytes());
        assertArrayEquals(png, store.load(CONV, stored.hash().substring(0, 8)).orElseThrow().bytes());
    }

    @Test
    void testMultiDotNamesResolveDistinctlyAndDuplicateNamesAreAmbiguous() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        byte[] q1 = {1, 1, 1};
        byte[] q2 = {2, 2, 2};
        store.store(CONV, q1, "chart.q1.png", "image/png", null);
        store.store(CONV, q2, "chart.q2.png", "image/png", null);

        assertArrayEquals(q2, store.load(CONV, "chart.q2.png").orElseThrow().bytes());
        assertArrayEquals(q1, store.load(CONV, "chart.q1.png").orElseThrow().bytes());

        store.store(CONV, new byte[]{3, 3, 3}, "dup.png", "image/png", null);
        store.store(CONV, new byte[]{4, 4, 4}, "dup.png", "image/png", null);
        assertTrue(store.load(CONV, "dup.png").isEmpty());
    }

    @Test
    void testLoadMissingOrBlankReturnsEmpty() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        assertTrue(store.load(CONV, "deadbeef").isEmpty());
        assertTrue(store.load(CONV, null).isEmpty());
        assertTrue(store.load(CONV, "").isEmpty());
    }

    @Test
    void testListReturnsStoredImageMetadata() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        store.store(CONV, new byte[]{1}, "a.png", "image/png", null);
        store.store(CONV, new byte[]{2}, "b.jpg", "image/jpeg", null);

        List<ChatImageStore.ImageRef> refs = store.list(CONV);
        assertEquals(2, refs.size());
        assertTrue(refs.stream().anyMatch(r -> "a.png".equals(r.fileName()) && "image/png".equals(r.mimeType())));
        assertTrue(refs.stream().anyMatch(r -> "b.jpg".equals(r.fileName())));
    }

    @Test
    void testConversationsAreIsolated() throws IOException {
        ChatImageStore store = storeWith(tempDir);
        ChatImageStore.Stored a = store.store("conv-a", new byte[]{1}, "a.png", "image/png", null);

        assertEquals(1, store.list("conv-a").size());
        assertTrue(store.list("conv-b").isEmpty());
        assertTrue(store.load("conv-b", a.hash()).isEmpty());
        assertTrue(store.load("conv-a", a.hash()).isPresent());
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
