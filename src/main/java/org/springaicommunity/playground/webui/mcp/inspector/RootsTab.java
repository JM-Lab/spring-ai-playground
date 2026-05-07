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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.client.RootPrimitive;

import java.util.List;

public class RootsTab extends VerticalLayout {

    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final VerticalLayout rootsListContainer = new VerticalLayout();

    public RootsTab(McpServerInfo serverInfo, McpClientService clientService) {
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(buildRootsAddForm());
        rootsListContainer.setPadding(false);
        rootsListContainer.setSpacing(false);
        rootsListContainer.setWidthFull();
        rootsListContainer.getStyle().set("gap", "0.4em").set("margin-top", "0.6em");
        add(rootsListContainer);
        render();
    }

    public void render() {
        rootsListContainer.removeAll();
        List<McpSchema.Root> roots = clientService.getRoots(serverInfo);
        if (roots.isEmpty()) {
            rootsListContainer.add(InspectorHelpers.emptyState(
                    "No roots advertised. Add one to expose to the server."));
            return;
        }
        rootsListContainer.add(InspectorHelpers.simpleSectionLabel(
                "Configured Roots (" + roots.size() + ")"));
        for (McpSchema.Root r : roots) {
            rootsListContainer.add(new RootPrimitive(r, () -> {
                clientService.removeRoot(serverInfo, r.name());
                render();
            }));
        }
    }

    private Component buildRootsAddForm() {
        TextField uri = new TextField("URI");
        uri.setPlaceholder("file:///path/to/dir");
        uri.setWidthFull();
        TextField name = new TextField("Name");
        name.setPlaceholder("project-source");
        name.setWidthFull();
        Button addBtn = new Button("Add Root", VaadinIcon.PLUS.create());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        addBtn.addClickListener(e -> {
            String u = uri.getValue() == null ? "" : uri.getValue().trim();
            String n = name.getValue() == null ? "" : name.getValue().trim();
            if (u.isEmpty() || n.isEmpty()) {
                VaadinUtils.showErrorNotification("URI and Name are required");
                return;
            }
            try {
                clientService.addRoot(serverInfo, new McpSchema.Root(u, n));
                uri.clear();
                name.clear();
                render();
                VaadinUtils.showInfoNotification("Root added");
            } catch (Exception ex) {
                VaadinUtils.showErrorNotification("Failed: " + ex.getMessage());
            }
        });
        HorizontalLayout row = new HorizontalLayout(uri, name, addBtn);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.END);
        row.getStyle().set("gap", "0.6em");
        Div card = new Div(InspectorHelpers.simpleSectionLabel("Add Root"), row);
        card.setWidthFull();
        card.getStyle()
                .set("padding", "0.8em 1em")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background-color", "var(--lumo-base-color)");
        return card;
    }
}
