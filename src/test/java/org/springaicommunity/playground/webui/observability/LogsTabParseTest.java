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
package org.springaicommunity.playground.webui.observability;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.webui.observability.LogsTab.LogLine;

import static org.assertj.core.api.Assertions.assertThat;

class LogsTabParseTest {

    @Test
    void parsesFullLineWithUserSession() {
        String raw = "2026-05-31 12:34:56.789 [http-nio-8282-exec-1] INFO  "
                + "o.s.p.s.c.ChatService [user=abc123 sid=sess-1 conv=conv-9 msg=msg-7 "
                + "traceId=trace-5 spanId=span-3] - Calling tool";
        LogLine ln = LogsTab.parse(raw);
        assertThat(ln.time()).isEqualTo("2026-05-31 12:34:56.789");
        assertThat(ln.level()).isEqualTo("INFO");
        assertThat(ln.logger()).isEqualTo("o.s.p.s.c.ChatService");
        assertThat(ln.userId()).isEqualTo("abc123");
        assertThat(ln.sessionId()).isEqualTo("sess-1");
        assertThat(ln.conv()).isEqualTo("conv-9");
        assertThat(ln.userMessageId()).isEqualTo("msg-7");
        assertThat(ln.traceId()).isEqualTo("trace-5");
        assertThat(ln.spanId()).isEqualTo("span-3");
        assertThat(ln.message()).isEqualTo("Calling tool");
    }

    @Test
    void parsesEmptyMdcValues() {
        String raw = "2026-05-31 12:34:56.789 [main] INFO  o.s.Foo "
                + "[user= sid= conv= msg= traceId= spanId=] - boot";
        LogLine ln = LogsTab.parse(raw);
        assertThat(ln.userId()).isEmpty();
        assertThat(ln.sessionId()).isEmpty();
        assertThat(ln.message()).isEqualTo("boot");
    }

    @Test
    void nonConformingLineFallsBackToRaw() {
        String raw = "some random non-conforming line";
        LogLine ln = LogsTab.parse(raw);
        assertThat(ln.level()).isEmpty();
        assertThat(ln.time()).isEmpty();
        assertThat(ln.message()).isEqualTo(raw);
        assertThat(ln.raw()).isEqualTo(raw);
    }

    @Test
    void blankLineReturnsNull() {
        assertThat(LogsTab.parse("")).isNull();
        assertThat(LogsTab.parse("   ")).isNull();
    }
}
