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
package org.springaicommunity.playground.webui;

import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.home.HomeView;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springaicommunity.playground.webui.observability.ObservabilityView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springaicommunity.playground.webui.vectorstore.VectorStoreView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GoogleAnalyticsNavigationListenerTest {

    @Test
    void routeLabelUsesStaticPageTitle() {
        assertEquals("Home", GoogleAnalyticsNavigationListener.routeLabel(HomeView.class, "/"));
        assertEquals("MCP Server", GoogleAnalyticsNavigationListener.routeLabel(McpServerView.class, "/mcp-server"));
        assertEquals("Tool Studio", GoogleAnalyticsNavigationListener.routeLabel(ToolStudioView.class, "/tool-studio"));
        assertEquals("Observability",
                GoogleAnalyticsNavigationListener.routeLabel(ObservabilityView.class, "/observability"));
        assertEquals("Vector Database",
                GoogleAnalyticsNavigationListener.routeLabel(VectorStoreView.class, "/vector-database"));
    }

    @Test
    void routeLabelDerivesLabelFromPathWhenViewTitleIsDynamic() {
        assertEquals("Agentic Chat", GoogleAnalyticsNavigationListener.routeLabel(ChatView.class, "/agentic-chat"));
        assertEquals("Agentic Chat", GoogleAnalyticsNavigationListener.routeLabel(null, "/agentic-chat"));
        assertEquals("Home", GoogleAnalyticsNavigationListener.routeLabel(null, "/"));
    }
}
