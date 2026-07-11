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
package org.springaicommunity.playground.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.identity.UserIdentityService;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MdcIdentityFilterTest {

    @Test
    void setsUserIdDuringChainAndClearsAfter() throws IOException, ServletException {
        UserIdentityService identity = mock(UserIdentityService.class);
        when(identity.currentUserId()).thenReturn("device-xyz");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        HttpServletResponse response = mock(HttpServletResponse.class);

        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInsideChain.set(MDC.get(MdcIdentityFilter.USER_ID));

        new MdcIdentityFilter(identity).doFilter(request, response, chain);

        assertThat(seenInsideChain.get()).isEqualTo("device-xyz");
        assertThat(MDC.get(MdcIdentityFilter.USER_ID)).isNull();
        assertThat(MDC.get(MdcIdentityFilter.SESSION_ID)).isNull();
    }

    @Test
    void mcpRequestCarriesConversationAndSessionHeadersIntoMdc() throws IOException, ServletException {
        HttpServletRequest request = mcpRequest("/mcp", "Chat-1234-abc", "sess-42");
        AtomicReference<String> conversation = new AtomicReference<>();
        AtomicReference<String> session = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            conversation.set(MDC.get(MdcIdentityFilter.CONVERSATION_ID));
            session.set(MDC.get(MdcIdentityFilter.SESSION_ID));
        };

        new MdcIdentityFilter(identity()).doFilter(request, mock(HttpServletResponse.class), chain);

        assertThat(conversation.get()).isEqualTo("Chat-1234-abc");
        assertThat(session.get()).isEqualTo("sess-42");
        assertThat(MDC.get(MdcIdentityFilter.CONVERSATION_ID)).isNull();
        assertThat(MDC.get(MdcIdentityFilter.SESSION_ID)).isNull();
    }

    @Test
    void identityHeadersAreIgnoredOutsideTheMcpEndpoint() throws IOException, ServletException {
        HttpServletRequest request = mcpRequest("/agentic-chat", "Chat-1234-abc", "sess-42");
        AtomicReference<String> conversation = new AtomicReference<>();
        FilterChain chain = (req, res) -> conversation.set(MDC.get(MdcIdentityFilter.CONVERSATION_ID));

        new MdcIdentityFilter(identity()).doFilter(request, mock(HttpServletResponse.class), chain);

        assertThat(conversation.get()).isNull();
    }

    @Test
    void malformedConversationHeaderIsRejected() throws IOException, ServletException {
        HttpServletRequest request = mcpRequest("/mcp", "../escape attempt", null);
        AtomicReference<String> conversation = new AtomicReference<>();
        FilterChain chain = (req, res) -> conversation.set(MDC.get(MdcIdentityFilter.CONVERSATION_ID));

        new MdcIdentityFilter(identity()).doFilter(request, mock(HttpServletResponse.class), chain);

        assertThat(conversation.get()).isNull();
    }

    private static UserIdentityService identity() {
        UserIdentityService identity = mock(UserIdentityService.class);
        when(identity.currentUserId()).thenReturn("device-xyz");
        return identity;
    }

    private static HttpServletRequest mcpRequest(String uri, String conversationHeader, String sessionHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeader(MdcIdentityFilter.CONVERSATION_HEADER)).thenReturn(conversationHeader);
        when(request.getHeader(MdcIdentityFilter.SESSION_HEADER)).thenReturn(sessionHeader);
        return request;
    }
}
