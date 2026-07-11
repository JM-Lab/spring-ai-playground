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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AttachmentHashingTest {

    @Test
    void testSha256HexMatchesKnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                AttachmentHashing.sha256Hex(new byte[0]));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                AttachmentHashing.sha256Hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testSha256HexIsDeterministicAndCollisionFree() {
        assertEquals(AttachmentHashing.sha256Hex(new byte[]{1, 2, 3}),
                AttachmentHashing.sha256Hex(new byte[]{1, 2, 3}));
        assertNotEquals(AttachmentHashing.sha256Hex(new byte[]{1}),
                AttachmentHashing.sha256Hex(new byte[]{2}));
    }

    @Test
    void testNormalizeRefStripsPathAndExtension() {
        assertEquals("beach", AttachmentHashing.normalizeRef("beach.png"));
        assertEquals("beach", AttachmentHashing.normalizeRef("images/sub/beach.png"));
        assertEquals("chart", AttachmentHashing.normalizeRef("chart.q1.png"));
        assertEquals("beach", AttachmentHashing.normalizeRef("  beach.png  "));
    }

    @Test
    void testNormalizeRefKeepsBareNameAndHandlesNull() {
        assertEquals("", AttachmentHashing.normalizeRef(null));
        assertEquals("deadbeef", AttachmentHashing.normalizeRef("deadbeef"));
        assertEquals(".hidden", AttachmentHashing.normalizeRef(".hidden"));
    }
}
