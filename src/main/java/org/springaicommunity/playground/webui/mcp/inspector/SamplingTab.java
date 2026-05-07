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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.client.SamplingRequestPrimitive;

public class SamplingTab extends VerticalLayout {

    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private Runnable samplingUnsubscribe;

    public SamplingTab(McpServerInfo serverInfo, McpClientService clientService) {
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(InspectorHelpers.simpleSectionLabel("Pending sampling requests"));
        render();
    }

    public void attachListeners(UI ui) {
        if (ui == null) return;
        samplingUnsubscribe = clientService.subscribePendingChange(serverInfo, () -> {
            try { ui.access(this::render); } catch (RuntimeException ignore) {}
        });
    }

    public void detach() {
        try {
            if (samplingUnsubscribe != null) samplingUnsubscribe.run();
        } catch (RuntimeException ignore) {}
        samplingUnsubscribe = null;
    }

    public void render() {
        // Remove all children except the section label kept at index 0.
        while (getComponentCount() > 1) {
            remove(getComponentAt(getComponentCount() - 1));
        }
        var pending = clientService.snapshotPendingSamplings(serverInfo);
        if (pending.isEmpty()) {
            add(InspectorHelpers.emptyState(
                    "No pending sampling requests. The server can ask the client to sample an LLM — incoming requests appear here for manual response."));
            return;
        }
        for (var p : pending) add(new SamplingRequestPrimitive(p, serverInfo, clientService));
    }
}
