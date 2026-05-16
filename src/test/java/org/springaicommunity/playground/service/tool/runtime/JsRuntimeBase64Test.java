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
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsRuntimeBase64Test {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> false)
                .build();
        JsRuntimeGlobals.installBase64(context.getBindings("js"));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void btoaEncodesAscii() {
        Value v = context.eval("js", "btoa('Hello')");
        assertEquals("SGVsbG8=", v.asString());
    }

    @Test
    void atobDecodesAscii() {
        Value v = context.eval("js", "atob('SGVsbG8=')");
        assertEquals("Hello", v.asString());
    }

    @Test
    void btoaAtobRoundTrip() {
        Value v = context.eval("js",
                "atob(btoa('the quick brown fox jumps over the lazy dog'))");
        assertEquals("the quick brown fox jumps over the lazy dog", v.asString());
    }

    @Test
    void btoaEmptyString() {
        Value v = context.eval("js", "btoa('')");
        assertEquals("", v.asString());
    }

    @Test
    void btoaEncodesLatin1() {
        Value v = context.eval("js", "btoa('\\xff\\x00\\x7f')");
        assertEquals("/wB/", v.asString());
    }

    @Test
    void btoaRejectsNonLatin1() {
        assertThrows(RuntimeException.class, () -> context.eval("js", "btoa('\\u0100')"));
    }

    @Test
    void atobRejectsInvalidBase64() {
        assertThrows(RuntimeException.class, () -> context.eval("js", "atob('!!not-base64!!')"));
    }
}
