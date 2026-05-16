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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsRuntimeUrlTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> false)
                .build();
        JsRuntimeGlobals.installUrl(context.getBindings("js"));
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void urlParsesAbsoluteUrl() {
        Value u = context.eval("js",
                "new URL('https://example.com:8443/path/to/page?a=1&b=2#frag')");
        assertEquals("https:", u.getMember("protocol").asString());
        assertEquals("example.com:8443", u.getMember("host").asString());
        assertEquals("example.com", u.getMember("hostname").asString());
        assertEquals("8443", u.getMember("port").asString());
        assertEquals("/path/to/page", u.getMember("pathname").asString());
        assertEquals("?a=1&b=2", u.getMember("search").asString());
        assertEquals("#frag", u.getMember("hash").asString());
        assertEquals("https://example.com:8443", u.getMember("origin").asString());
    }

    @Test
    void urlResolvesAgainstBase() {
        Value u = context.eval("js",
                "new URL('/foo/bar', 'https://example.com/old/path')");
        assertEquals("/foo/bar", u.getMember("pathname").asString());
        assertEquals("example.com", u.getMember("hostname").asString());
    }

    @Test
    void urlEmptyPortHidden() {
        Value u = context.eval("js", "new URL('https://example.com/p')");
        assertEquals("", u.getMember("port").asString());
        assertEquals("https://example.com", u.getMember("origin").asString());
    }

    @Test
    void urlSearchParamsGetReturnsFirst() {
        Value u = context.eval("js",
                "new URL('https://example.com/?a=1&b=2&a=3').searchParams.get('a')");
        assertEquals("1", u.asString());
    }

    @Test
    void urlSearchParamsGetAll() {
        Value u = context.eval("js",
                "new URL('https://example.com/?a=1&a=2&a=3').searchParams.getAll('a').length");
        assertEquals(3, u.asInt());
    }

    @Test
    void urlSearchParamsHas() {
        Value present = context.eval("js",
                "new URL('https://example.com/?a=1').searchParams.has('a')");
        Value missing = context.eval("js",
                "new URL('https://example.com/?a=1').searchParams.has('b')");
        assertTrue(present.asBoolean());
        assertFalse(missing.asBoolean());
    }

    @Test
    void searchParamsConstructFromString() {
        Value u = context.eval("js", "new URLSearchParams('?a=1&b=2').get('b')");
        assertEquals("2", u.asString());
    }

    @Test
    void searchParamsConstructFromStringWithoutLeadingQuestion() {
        Value u = context.eval("js", "new URLSearchParams('a=hello&b=world').get('a')");
        assertEquals("hello", u.asString());
    }

    @Test
    void searchParamsConstructFromObject() {
        Value u = context.eval("js",
                "new URLSearchParams({x: 'hello', y: 'world'}).toString()");
        assertEquals("x=hello&y=world", u.asString());
    }

    @Test
    void searchParamsConstructFromPairs() {
        Value u = context.eval("js",
                "new URLSearchParams([['a','1'],['a','2']]).getAll('a').length");
        assertEquals(2, u.asInt());
    }

    @Test
    void searchParamsSetReplacesAll() {
        Value u = context.eval("js", """
                const p = new URLSearchParams('a=1&a=2&b=3');
                p.set('a', '9');
                p.toString();
                """);
        assertEquals("a=9&b=3", u.asString());
    }

    @Test
    void searchParamsAppend() {
        Value u = context.eval("js", """
                const p = new URLSearchParams();
                p.append('a', '1');
                p.append('a', '2');
                p.toString();
                """);
        assertEquals("a=1&a=2", u.asString());
    }

    @Test
    void searchParamsDelete() {
        Value u = context.eval("js", """
                const p = new URLSearchParams('a=1&b=2&a=3');
                p.delete('a');
                p.toString();
                """);
        assertEquals("b=2", u.asString());
    }

    @Test
    void searchParamsEncodesUnsafeChars() {
        Value u = context.eval("js", """
                const p = new URLSearchParams();
                p.append('name', 'Jin Hu');
                p.toString();
                """);
        assertEquals("name=Jin+Hu", u.asString());
    }

    @Test
    void urlMissingInputThrows() {
        assertThrows(RuntimeException.class, () -> context.eval("js", "new URL()"));
    }
}
