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
package org.springaicommunity.playground.webui.mcp.inspector;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.InlineResultPanel;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

import java.util.List;

public class PingTab extends VerticalLayout {

    public PingTab(McpServerInfo serverInfo, McpClientService clientService) {
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(new PingCard(serverInfo, clientService));
    }

    private static class PingCard extends Div {
        private final McpServerInfo serverInfo;
        private final McpClientService clientService;
        private final InlineResultPanel inlineResultPanel = new InlineResultPanel();
        private Object lastResult;
        private String lastError;
        private long lastElapsedMs;

        PingCard(McpServerInfo serverInfo, McpClientService clientService) {
            this.serverInfo = serverInfo;
            this.clientService = clientService;
            setWidthFull();
            PrimitiveCardLayout.applyCardStyle(this);

            add(PrimitiveCardLayout.titleRow(VaadinIcon.PLAY, "Send ping", "Ping", this::handlePing));

            Div desc = PrimitiveCardLayout.description(
                    "Send a keep-alive ping to verify the server is responsive.");
            if (desc != null) add(desc);

            inlineResultPanel.setOnDismiss(this::clearLastResultState);
            add(inlineResultPanel);
        }

        private void handlePing() {
            long startNs = System.nanoTime();
            try {
                lastResult = clientService.pingMcpClient(serverInfo);
                lastError = null;
            } catch (Exception ex) {
                lastResult = null;
                lastError = ex.getMessage();
            }
            lastElapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
            renderInline();
        }

        private void renderInline() {
            if (lastError != null) {
                inlineResultPanel.renderError("ping", lastElapsedMs, lastError);
                return;
            }
            inlineResultPanel.renderOk("ping", lastElapsedMs,
                    List.of(InspectorHelpers.codeBlock(InspectorHelpers.prettyPrint(lastResult), false)));
        }

        private void clearLastResultState() {
            lastResult = null;
            lastError = null;
            lastElapsedMs = 0L;
        }
    }
}
