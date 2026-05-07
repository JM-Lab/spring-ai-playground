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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.InlineResultPanel;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ResourceTemplatePrimitive extends Div {

    private final McpSchema.ResourceTemplate template;
    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final List<String> variables;
    private final Map<String, TextField> varFields = new LinkedHashMap<>();
    private final InlineResultPanel inlineResultPanel = new InlineResultPanel();
    private String lastUri;
    private McpSchema.ReadResourceResult lastResult;
    private String lastError;
    private long lastElapsedMs;

    public ResourceTemplatePrimitive(McpSchema.ResourceTemplate template, McpServerInfo serverInfo,
            McpClientService clientService) {
        this.template = template;
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        this.variables = InspectorHelpers.extractTemplateVars(template.uriTemplate());
        setWidthFull();
        PrimitiveCardLayout.applyCardStyle(this);

        String displayTitle = InspectorHelpers.pickFirstNonBlank(template.title(), template.name(),
                template.uriTemplate());
        add(PrimitiveCardLayout.titleRow(VaadinIcon.PLAY, "Expand template and read",
                displayTitle, this::handleRead));
        add(PrimitiveCardLayout.subUriLabel(template.uriTemplate()));

        if (template.mimeType() != null && !template.mimeType().isBlank()) {
            HorizontalLayout badges = new HorizontalLayout();
            badges.setSpacing(false);
            badges.setPadding(false);
            badges.getStyle().set("gap", "0.3em").set("flex-wrap", "wrap")
                    .set("margin", "0.3em 0 0 2.4em");
            badges.add(InspectorHelpers.infoBadge(template.mimeType(), "var(--lumo-primary-color)"));
            add(badges);
        }

        Div desc = PrimitiveCardLayout.description(template.description());
        if (desc != null) add(desc);

        if (!variables.isEmpty()) {
            add(InspectorHelpers.simpleSectionLabel("Variables"));
            VerticalLayout argsLayout = new VerticalLayout();
            argsLayout.setPadding(false);
            argsLayout.setSpacing(false);
            argsLayout.getStyle().set("gap", "0.6em");
            for (String var : variables) {
                TextField tf = new TextField(var);
                tf.setWidthFull();
                tf.setRequiredIndicatorVisible(true);
                varFields.put(var, tf);
                argsLayout.add(tf);
            }
            add(argsLayout);
        }

        inlineResultPanel.setOnDismiss(this::clearLastResultState);
        add(inlineResultPanel);
    }

    private void handleRead() {
        String uri = template.uriTemplate();
        if (uri == null) uri = "";
        for (Map.Entry<String, TextField> e : varFields.entrySet()) {
            String value = e.getValue().getValue();
            if (value == null || value.isBlank()) {
                lastUri = uri;
                lastResult = null;
                lastError = "Required variable: " + e.getKey();
                lastElapsedMs = 0L;
                renderInline();
                return;
            }
            uri = uri.replace("{" + e.getKey() + "}",
                    URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        lastUri = uri;
        long startNs = System.nanoTime();
        try {
            lastResult = clientService.readResourceAsOpt(serverInfo, uri).orElse(null);
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
            inlineResultPanel.renderError(lastUri, lastElapsedMs, lastError);
            return;
        }
        if (lastResult == null || lastResult.contents() == null
                || lastResult.contents().isEmpty()) {
            inlineResultPanel.render()
                    .emptyBadgeLabel(lastResult == null ? "NO RESULT" : "OK")
                    .omitResponseLabelOnEmpty()
                    .commitEmptyResponse(lastUri, lastElapsedMs, "(empty content)");
            return;
        }
        List<Component> blocks = new ArrayList<>(lastResult.contents().size());
        for (McpSchema.ResourceContents content : lastResult.contents()) {
            blocks.add(InspectorHelpers.buildResourceContentBlock(content));
        }
        inlineResultPanel.render().responseLabel("Contents")
                .commitOk(lastUri, lastElapsedMs, blocks);
    }

    private void clearLastResultState() {
        lastResult = null;
        lastError = null;
        lastElapsedMs = 0L;
    }
}
