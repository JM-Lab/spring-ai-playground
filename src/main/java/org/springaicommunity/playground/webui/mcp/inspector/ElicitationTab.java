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
import org.springaicommunity.playground.webui.mcp.inspector.primitives.client.ElicitationRequestPrimitive;

public class ElicitationTab extends VerticalLayout {

    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private Runnable elicitationUnsubscribe;

    public ElicitationTab(McpServerInfo serverInfo, McpClientService clientService) {
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(InspectorHelpers.simpleSectionLabel("Pending elicitation requests"));
        render();
    }

    public void attachListeners(UI ui) {
        if (ui == null) return;
        elicitationUnsubscribe = clientService.subscribePendingChange(serverInfo, () -> {
            try { ui.access(this::render); } catch (RuntimeException ignore) {}
        });
    }

    public void detach() {
        try {
            if (elicitationUnsubscribe != null) elicitationUnsubscribe.run();
        } catch (RuntimeException ignore) {}
        elicitationUnsubscribe = null;
    }

    public void render() {
        while (getComponentCount() > 1) {
            remove(getComponentAt(getComponentCount() - 1));
        }
        var pending = clientService.snapshotPendingElicitations(serverInfo);
        if (pending.isEmpty()) {
            add(InspectorHelpers.emptyState(
                    "No pending elicitation requests. When the server asks for structured user input it appears here as a form."));
            return;
        }
        for (var p : pending) add(new ElicitationRequestPrimitive(p, serverInfo, clientService));
    }
}
