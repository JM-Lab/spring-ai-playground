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
package org.springaicommunity.playground.service.mcp.client;

import io.modelcontextprotocol.common.McpTransportContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springaicommunity.playground.config.MdcIdentityFilter;

import java.net.URI;
import java.net.http.HttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class McpLoopbackIdentityTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void snapshotCapturesConversationAndSessionFromMdc() {
        MDC.put(MdcIdentityFilter.CONVERSATION_ID, "Chat-abc");
        MDC.put(MdcIdentityFilter.SESSION_ID, "sess-1");

        McpTransportContext context = McpLoopbackIdentity.snapshotMdc();

        assertThat(context.get(MdcIdentityFilter.CONVERSATION_HEADER)).isEqualTo("Chat-abc");
        assertThat(context.get(MdcIdentityFilter.SESSION_HEADER)).isEqualTo("sess-1");
    }

    @Test
    void snapshotWithoutMdcIsEmpty() {
        McpTransportContext context = McpLoopbackIdentity.snapshotMdc();
        assertThat(context.get(MdcIdentityFilter.CONVERSATION_HEADER)).isNull();
        assertThat(context.get(MdcIdentityFilter.SESSION_HEADER)).isNull();
    }

    @Test
    void headerCustomizerReplaysSnapshotAsRequestHeaders() {
        MDC.put(MdcIdentityFilter.CONVERSATION_ID, "Chat-abc");
        McpTransportContext context = McpLoopbackIdentity.snapshotMdc();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1/mcp"));

        McpLoopbackIdentity.headerCustomizer().customize(builder, "POST", URI.create("http://127.0.0.1/mcp"),
                "{}", context);

        HttpRequest request = builder.GET().build();
        assertThat(request.headers().firstValue(MdcIdentityFilter.CONVERSATION_HEADER)).contains("Chat-abc");
        assertThat(request.headers().firstValue(MdcIdentityFilter.SESSION_HEADER)).isEmpty();
    }

    @Test
    void headerCustomizerWithEmptyContextAddsNothing() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1/mcp"));

        McpLoopbackIdentity.headerCustomizer().customize(builder, "POST", URI.create("http://127.0.0.1/mcp"),
                "{}", McpTransportContext.EMPTY);

        HttpRequest request = builder.GET().build();
        assertThat(request.headers().map()).doesNotContainKeys(
                MdcIdentityFilter.CONVERSATION_HEADER, MdcIdentityFilter.SESSION_HEADER);
    }
}
