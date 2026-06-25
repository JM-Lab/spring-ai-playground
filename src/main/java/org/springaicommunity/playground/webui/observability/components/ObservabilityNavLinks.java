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
package org.springaicommunity.playground.webui.observability.components;

import com.vaadin.flow.component.UI;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ObservabilityNavLinks {

    public static void openMcpServer(String serverName) {
        UI.getCurrent().navigate("mcp-server?server=" + enc(serverName));
    }

    public static void openTool(String toolName) {
        UI.getCurrent().navigate("tool-studio?tool=" + enc(toolName));
    }

    public static void openConversation(String conversationId) {
        UI.getCurrent().navigate("agentic-chat?conv=" + enc(conversationId));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private ObservabilityNavLinks() {}
}
