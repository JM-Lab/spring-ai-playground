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
package org.springaicommunity.playground.webui.mcp.inspector.primitives.client;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpNotificationStore;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

public class SamplingRequestPrimitive extends Div {

    public SamplingRequestPrimitive(McpNotificationStore.PendingSampling pending,
            McpServerInfo serverInfo, McpClientService clientService) {
        setWidthFull();
        PrimitiveCardLayout.applyCardStyle(this);

        add(PrimitiveCardLayout.titleRow(VaadinIcon.CUBES.create(), "Sampling request"));

        McpSchema.CreateMessageRequest req = pending.request();
        add(InspectorHelpers.simpleSectionLabel("Request"));
        add(InspectorHelpers.codeBlock(InspectorHelpers.prettyPrint(req), false));

        add(InspectorHelpers.simpleSectionLabel("Your response (text)"));
        TextArea responseArea = new TextArea();
        responseArea.setWidthFull();
        responseArea.setHeight("8em");
        responseArea.setPlaceholder("Type the assistant message text…");
        add(responseArea);

        Button approve = new Button("Send", VaadinIcon.CHECK.create());
        approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        approve.addClickListener(e -> {
            String text = responseArea.getValue() == null ? "" : responseArea.getValue();
            McpSchema.CreateMessageResult result = McpSchema.CreateMessageResult.builder()
                    .role(McpSchema.Role.ASSISTANT)
                    .content(new McpSchema.TextContent(text))
                    .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
                    .build();
            clientService.completeSampling(serverInfo, pending.id(), result);
        });
        Button decline = new Button("Decline", VaadinIcon.CLOSE_SMALL.create());
        decline.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ERROR);
        decline.addClickListener(e -> {
            McpSchema.CreateMessageResult result = McpSchema.CreateMessageResult.builder()
                    .role(McpSchema.Role.ASSISTANT)
                    .content(new McpSchema.TextContent("User declined."))
                    .stopReason(McpSchema.CreateMessageResult.StopReason.STOP_SEQUENCE)
                    .build();
            clientService.completeSampling(serverInfo, pending.id(), result);
        });

        HorizontalLayout actions = new HorizontalLayout(approve, decline);
        actions.setPadding(false);
        actions.setSpacing(true);
        actions.getStyle().set("margin-top", "0.6em").set("gap", "0.6em");
        add(actions);
    }
}
