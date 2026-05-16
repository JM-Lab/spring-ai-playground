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
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsRuntimeSafetyTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.ALL)
                .allowHostClassLookup(c -> false)
                .build();
        JsRuntimeGlobals.installSafety(context.getBindings("js"), null, null);
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    @Test
    void htmlExtractsTitle() {
        Value v = context.eval("js",
                "safety.parser.html('<html><head><title>Hi</title></head><body>x</body></html>').title()");
        assertEquals("Hi", v.asString());
    }

    @Test
    void htmlSelectsElements() {
        Value v = context.eval("js", """
                const doc = safety.parser.html('<p class="a">one</p><p class="a">two</p>');
                doc.select('p.a').size();
                """);
        assertEquals(2, v.asInt());
    }

    @Test
    void htmlExtractsText() {
        Value v = context.eval("js",
                "safety.parser.html('<div><b>Hello</b> <i>World</i></div>').body().text()");
        assertEquals("Hello World", v.asString());
    }

    @Test
    void htmlMissingInputThrows() {
        assertThrows(RuntimeException.class, () -> context.eval("js", "safety.parser.html()"));
    }

    @Test
    void yamlParsesScalarObject() {
        Value v = context.eval("js",
                "safety.parser.yaml('name: Alice\\nage: 30').name");
        assertEquals("Alice", v.asString());
    }

    @Test
    void yamlParsesNested() {
        Value v = context.eval("js", """
                const cfg = safety.parser.yaml(`
                server:
                  port: 8080
                  host: localhost
                `);
                cfg.server.port;
                """);
        assertEquals(8080, v.asInt());
    }

    @Test
    void yamlParsesList() {
        Value v = context.eval("js", """
                const arr = safety.parser.yaml('- one\\n- two\\n- three');
                arr.length + ':' + arr[1];
                """);
        assertEquals("3:two", v.asString());
    }

    @Test
    void yamlInvalidThrows() {
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "safety.parser.yaml('  invalid:\\n: : :\\n  - [')"));
    }

    @Test
    void csvParsesWithoutHeader() {
        Value v = context.eval("js", """
                const rows = safety.parser.csv('a,b,c\\n1,2,3\\n4,5,6');
                rows.length + ':' + rows[2][2];
                """);
        assertEquals("3:6", v.asString());
    }

    @Test
    void csvParsesWithHeader() {
        Value v = context.eval("js", """
                const rows = safety.parser.csv('name,age\\nAlice,30\\nBob,25', {header: true});
                rows.length + ':' + rows[0].name + ':' + rows[1].age;
                """);
        assertEquals("2:Alice:25", v.asString());
    }

    @Test
    void csvCustomDelimiter() {
        Value v = context.eval("js", """
                const rows = safety.parser.csv('a;b;c\\n1;2;3', {delimiter: ';'});
                rows[1][1];
                """);
        assertEquals("2", v.asString());
    }

    @Test
    void xmlParsesSimple() {
        Value v = context.eval("js", """
                const doc = safety.parser.xml('<root><item>hello</item></root>');
                doc.tag + ':' + doc.children[0].tag + ':' + doc.children[0].text;
                """);
        assertEquals("root:item:hello", v.asString());
    }

    @Test
    void xmlParsesAttributes() {
        Value v = context.eval("js", """
                const doc = safety.parser.xml('<book isbn="123" lang="en">Title</book>');
                doc.attrs.isbn + ':' + doc.attrs.lang + ':' + doc.text;
                """);
        assertEquals("123:en:Title", v.asString());
    }

    @Test
    void xmlParsesNestedChildren() {
        Value v = context.eval("js", """
                const doc = safety.parser.xml('<list><a/><b/><c/></list>');
                doc.children.length;
                """);
        assertEquals(3, v.asInt());
    }

    @Test
    void xmlBlocksXxeDoctype() {
        String malicious = "<?xml version=\"1.0\"?><!DOCTYPE foo SYSTEM \"file:///etc/passwd\"><foo/>";
        assertThrows(RuntimeException.class, () -> context.eval("js",
                "safety.parser.xml(`" + malicious + "`)"));
    }

    @Test
    void safetyNamespaceShape() {
        Value v = context.eval("js", "typeof safety.parser.html");
        assertEquals("function", v.asString());
        Value v2 = context.eval("js", "typeof safety.parser.yaml");
        assertEquals("function", v2.asString());
    }
}
