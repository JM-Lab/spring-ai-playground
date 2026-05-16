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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsRuntimeCryptoTest {

    private static final String HEX_FN = """
            function toHex(arr) {
                return Array.from(arr).map(b => b.toString(16).padStart(2,'0')).join('');
            }
            """;

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js")
                .allowExperimentalOptions(true)
                .option("js.text-encoding", "true")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> false)
                .build();
        JsRuntimeGlobals.installCrypto(context.getBindings("js"));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void randomUuidProducesValidUuid() {
        Value v = context.eval("js", "crypto.randomUUID()");
        String uuid = v.asString();
        assertTrue(uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "expected uuid format, got: " + uuid);
    }

    @Test
    void randomUuidIsUnique() {
        Value a = context.eval("js", "crypto.randomUUID()");
        Value b = context.eval("js", "crypto.randomUUID()");
        assertNotEquals(a.asString(), b.asString());
    }

    @Test
    void getRandomValuesFillsBuffer() {
        Value v = context.eval("js", """
                const arr = new Uint8Array(16);
                crypto.getRandomValues(arr);
                let allZero = true;
                for (const b of arr) { if (b !== 0) { allZero = false; break; } }
                allZero;
                """);
        assertEquals(false, v.asBoolean(), "16 random bytes should not all be zero");
    }

    @Test
    void getRandomValuesRejectsTooLarge() {
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "crypto.getRandomValues(new Uint8Array(65537))"));
    }

    @Test
    void digestSha256OfEmpty() {
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        Value v = context.eval("js", HEX_FN + """
                const hash = crypto.subtle.digest('SHA-256', new Uint8Array(0));
                toHex(hash);
                """);
        assertEquals(expected, v.asString());
    }

    @Test
    void digestSha256OfAbc() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        Value v = context.eval("js", HEX_FN + """
                const data = new TextEncoder().encode('abc');
                const hash = crypto.subtle.digest('SHA-256', data);
                toHex(hash);
                """);
        assertEquals(expected, v.asString());
    }

    @Test
    void digestAcceptsAlgorithmObjectForm() {
        String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        Value v = context.eval("js", HEX_FN + """
                const data = new TextEncoder().encode('abc');
                const hash = crypto.subtle.digest({name:'SHA-256'}, data);
                toHex(hash);
                """);
        assertEquals(expected, v.asString());
    }

    @Test
    void digestRejectsUnsupportedAlgorithm() {
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "crypto.subtle.digest('MD5', new Uint8Array(0))"));
    }

    @Test
    void hmacSha256RfcTestVector1() {
        String expected = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7";
        Value v = context.eval("js", HEX_FN + """
                const key = new Uint8Array(20);
                key.fill(0x0b);
                const k = crypto.subtle.importKey('raw', key,
                    {name:'HMAC', hash:'SHA-256'}, false, ['sign']);
                const sig = crypto.subtle.sign('HMAC', k,
                    new TextEncoder().encode('Hi There'));
                toHex(sig);
                """);
        assertEquals(expected, v.asString());
    }

    @Test
    void importKeyRejectsNonHmac() {
        assertThrows(RuntimeException.class, () -> context.eval("js", """
                crypto.subtle.importKey('raw', new Uint8Array([1,2,3]),
                    {name:'AES-GCM', length:128}, false, ['encrypt']);
                """));
    }

    @Test
    void signRejectsInvalidKey() {
        assertThrows(RuntimeException.class, () -> context.eval("js", """
                crypto.subtle.sign('HMAC', {bogus: true}, new Uint8Array(0));
                """));
    }
}
