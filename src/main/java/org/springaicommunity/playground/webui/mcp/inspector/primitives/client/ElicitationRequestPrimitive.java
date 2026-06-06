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

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpNotificationStore;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElicitationRequestPrimitive extends Div {

    private final Map<String, AbstractField<?, ?>> formFields = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    public ElicitationRequestPrimitive(McpNotificationStore.PendingElicitation pending,
            McpServerInfo serverInfo, McpClientService clientService) {
        setWidthFull();
        PrimitiveCardLayout.applyCardStyle(this);

        McpSchema.ElicitRequest req = pending.request();
        String title = req.message() == null ? "Elicitation request" : req.message();
        add(PrimitiveCardLayout.titleRow(VaadinIcon.QUESTION_CIRCLE_O.create(), title));

        Map<String, Object> schema = req.requestedSchema();
        List<String> required = schema != null && schema.get("required") instanceof List<?> rl
                ? rl.stream().map(String::valueOf).toList() : List.of();
        Object propertiesObj = schema == null ? null : schema.get("properties");
        if (propertiesObj instanceof Map<?, ?> propsMap) {
            add(InspectorHelpers.simpleSectionLabel("Form"));
            VerticalLayout form = new VerticalLayout();
            form.setPadding(false);
            form.setSpacing(false);
            form.getStyle().set("gap", "0.5em");
            for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Map<String, Object> prop = entry.getValue() instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                AbstractField<?, ?> field = InspectorHelpers.buildArgField(key, prop, required.contains(key));
                formFields.put(key, field);
                form.add(field);
            }
            add(form);
        } else if (schema == null || schema.isEmpty()) {
            add(InspectorHelpers.simpleSectionLabel("Confirmation"));
            Span note = new Span("No additional input required — approve to allow this action.");
            note.getStyle().set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            add(note);
        } else {
            add(InspectorHelpers.simpleSectionLabel("Schema"));
            add(InspectorHelpers.codeBlock(InspectorHelpers.prettyPrint(schema), false));
        }

        Button accept = new Button("Accept", VaadinIcon.CHECK.create());
        accept.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        accept.addClickListener(e -> {
            Map<String, Object> content = new LinkedHashMap<>();
            for (var entry : formFields.entrySet()) {
                try {
                    Object v = InspectorHelpers.readArgValue(entry.getKey(), entry.getValue(),
                            required.contains(entry.getKey()));
                    if (v != null) content.put(entry.getKey(), v);
                } catch (IllegalArgumentException ex) {
                    VaadinUtils.showErrorNotification(ex.getMessage());
                    return;
                }
            }
            clientService.completeElicitation(serverInfo, pending.id(),
                    new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, content));
        });
        Button decline = new Button("Decline");
        decline.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        decline.addClickListener(e -> clientService.completeElicitation(serverInfo, pending.id(),
                new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.DECLINE, Map.of())));
        Button cancel = new Button("Cancel");
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                ButtonVariant.LUMO_ERROR);
        cancel.addClickListener(e -> clientService.completeElicitation(serverInfo, pending.id(),
                new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.CANCEL, Map.of())));

        HorizontalLayout actions = new HorizontalLayout(accept, decline, cancel);
        actions.setPadding(false);
        actions.setSpacing(true);
        actions.getStyle().set("margin-top", "0.6em").set("gap", "0.6em");
        add(actions);
    }
}
