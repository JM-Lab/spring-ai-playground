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
package org.springaicommunity.playground.webui.mcp.inspector.primitives.server;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.InlineResultPanel;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

import java.util.ArrayList;
import java.util.List;

public class ResourcePrimitive extends Div {

    private final McpSchema.Resource res;
    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final InlineResultPanel inlineResultPanel = new InlineResultPanel();
    private McpSchema.ReadResourceResult lastResult;
    private String lastError;
    private long lastElapsedMs;

    public ResourcePrimitive(McpSchema.Resource res, McpServerInfo serverInfo,
            McpClientService clientService) {
        this.res = res;
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setWidthFull();
        PrimitiveCardLayout.applyCardStyle(this);

        String displayTitle = InspectorHelpers.pickFirstNonBlank(res.title(), res.name(), res.uri());
        add(PrimitiveCardLayout.titleRow(VaadinIcon.PLAY, "Read resource", displayTitle, this::handleRead));
        add(PrimitiveCardLayout.subUriLabel(res.uri()));

        HorizontalLayout badges = InspectorHelpers.resourceBadges(res);
        if (badges.getComponentCount() > 0) {
            badges.getStyle().set("margin", "0.3em 0 0 2.4em");
            add(badges);
        }

        Div desc = PrimitiveCardLayout.description(res.description());
        if (desc != null) add(desc);

        inlineResultPanel.setOnDismiss(this::clearLastResultState);
        add(inlineResultPanel);
    }

    private void handleRead() {
        long startNs = System.nanoTime();
        try {
            lastResult = clientService.readResourceAsOpt(serverInfo, res.uri()).orElse(null);
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
            inlineResultPanel.renderError(res.uri(), lastElapsedMs, lastError);
            return;
        }
        if (lastResult == null || lastResult.contents() == null
                || lastResult.contents().isEmpty()) {
            inlineResultPanel.render()
                    .emptyBadgeLabel(lastResult == null ? "NO RESULT" : "OK")
                    .omitResponseLabelOnEmpty()
                    .commitEmptyResponse(res.uri(), lastElapsedMs, "(empty content)");
            return;
        }
        List<Component> blocks = new ArrayList<>(lastResult.contents().size());
        for (McpSchema.ResourceContents content : lastResult.contents()) {
            blocks.add(InspectorHelpers.buildResourceContentBlock(content));
        }
        inlineResultPanel.render().responseLabel("Contents")
                .commitOk(res.uri(), lastElapsedMs, blocks);
    }

    private void clearLastResultState() {
        lastResult = null;
        lastError = null;
        lastElapsedMs = 0L;
    }
}
