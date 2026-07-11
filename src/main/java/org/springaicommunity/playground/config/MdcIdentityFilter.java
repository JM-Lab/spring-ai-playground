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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springaicommunity.playground.service.identity.UserIdentityService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcIdentityFilter implements Filter {

    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String CONVERSATION_ID = "conversationId";
    // Replayed by the loopback MCP client to carry the caller's conversation and session MDC; read only on /mcp.
    public static final String CONVERSATION_HEADER = "X-Playground-Conversation-Id";
    public static final String SESSION_HEADER = "X-Playground-Session-Id";
    private static final String SESSION_ATTR = "playgroundLogSessionId";
    private static final String MCP_PATH_PREFIX = "/mcp";
    private static final int MAX_IDENTITY_LENGTH = 128;

    private final UserIdentityService userIdentityService;

    public MdcIdentityFilter(UserIdentityService userIdentityService) {
        this.userIdentityService = userIdentityService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        MDC.put(USER_ID, this.userIdentityService.currentUserId());
        String sessionId = resolveSessionId(request);
        if (sessionId == null) sessionId = mcpIdentityHeader(request, SESSION_HEADER);
        if (sessionId != null) MDC.put(SESSION_ID, sessionId);
        String conversationId = mcpIdentityHeader(request, CONVERSATION_HEADER);
        if (conversationId != null) MDC.put(CONVERSATION_ID, conversationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID);
            MDC.remove(SESSION_ID);
            if (conversationId != null) MDC.remove(CONVERSATION_ID);
        }
    }

    private String resolveSessionId(ServletRequest request) {
        if (!(request instanceof HttpServletRequest http)) return null;
        HttpSession session = http.getSession(false);
        if (session == null) return null;
        if (session.getAttribute(SESSION_ATTR) instanceof String existing) return existing;
        String sessionId = UUID.randomUUID().toString();
        session.setAttribute(SESSION_ATTR, sessionId);
        return sessionId;
    }

    private static String mcpIdentityHeader(ServletRequest request, String header) {
        if (!(request instanceof HttpServletRequest http)) return null;
        if (http.getRequestURI() == null || !http.getRequestURI().startsWith(MCP_PATH_PREFIX)) return null;
        String value = http.getHeader(header);
        if (value == null || value.isBlank() || value.length() > MAX_IDENTITY_LENGTH) return null;
        return value.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.')
                ? value : null;
    }
}
