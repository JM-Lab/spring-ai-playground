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
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.InlineResultPanel;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.PrimitiveCardLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PromptPrimitive extends Div {

    private final McpSchema.Prompt prompt;
    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final Map<String, TextField> argFields = new LinkedHashMap<>();
    private final InlineResultPanel inlineResultPanel = new InlineResultPanel();
    private Map<String, Object> lastArgs;
    private McpSchema.GetPromptResult lastResult;
    private String lastError;
    private long lastElapsedMs;

    public PromptPrimitive(McpSchema.Prompt prompt, McpServerInfo serverInfo,
            McpClientService clientService) {
        this.prompt = prompt;
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setWidthFull();
        PrimitiveCardLayout.applyCardStyle(this);

        String displayTitle = InspectorHelpers.pickFirstNonBlank(prompt.title(), prompt.name());
        add(PrimitiveCardLayout.titleRow(VaadinIcon.PLAY, "Get prompt", displayTitle, this::handleGet));

        if (prompt.title() != null && !prompt.title().isBlank()
                && !prompt.title().equals(prompt.name())) {
            add(PrimitiveCardLayout.subNameLabel(prompt.name()));
        }

        Div desc = PrimitiveCardLayout.description(prompt.description());
        if (desc != null) add(desc);

        List<McpSchema.PromptArgument> args = prompt.arguments();
        if (args != null && !args.isEmpty()) {
            add(InspectorHelpers.simpleSectionLabel("Arguments"));
            VerticalLayout argsLayout = new VerticalLayout();
            argsLayout.setPadding(false);
            argsLayout.setSpacing(false);
            argsLayout.getStyle().set("gap", "0.6em");
            for (McpSchema.PromptArgument arg : args) {
                TextField tf = new TextField(arg.name());
                tf.setWidthFull();
                if (arg.description() != null) tf.setHelperText(arg.description());
                if (Boolean.TRUE.equals(arg.required())) tf.setRequiredIndicatorVisible(true);
                argFields.put(arg.name(), tf);
                argsLayout.add(tf);
            }
            add(argsLayout);
        }

        inlineResultPanel.setOnDismiss(this::clearLastResultState);
        add(inlineResultPanel);
    }

    private void handleGet() {
        Map<String, Object> args = new LinkedHashMap<>();
        List<McpSchema.PromptArgument> declared = prompt.arguments();
        if (declared != null) {
            for (McpSchema.PromptArgument a : declared) {
                TextField tf = argFields.get(a.name());
                String value = tf == null || tf.getValue() == null ? "" : tf.getValue().trim();
                if (value.isEmpty()) {
                    if (Boolean.TRUE.equals(a.required())) {
                        lastArgs = args;
                        lastResult = null;
                        lastError = "Required argument: " + a.name();
                        lastElapsedMs = 0L;
                        renderInline();
                        return;
                    }
                    continue;
                }
                args.put(a.name(), value);
            }
        }
        lastArgs = args;
        long startNs = System.nanoTime();
        try {
            lastResult = clientService.getPromptAsOpt(serverInfo, prompt.name(), args).orElse(null);
            lastError = null;
        } catch (Exception ex) {
            lastResult = null;
            lastError = ex.getMessage();
        }
        lastElapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        renderInline();
    }

    private void renderInline() {
        InlineResultPanel.Render render = inlineResultPanel.render()
                .addPreResponseSection(InspectorHelpers.simpleSectionLabel("Request"))
                .addPreResponseSection(InspectorHelpers.codeBlock(InspectorHelpers.prettyPrint(
                        lastArgs == null ? Map.of() : lastArgs), false));

        if (lastError != null) {
            render.commitError(prompt.name(), lastElapsedMs, lastError);
            return;
        }
        if (lastResult == null) {
            render.emptyBadgeLabel("NO RESULT").omitResponseLabelOnEmpty()
                    .commitEmptyResponse(prompt.name(), lastElapsedMs, "(no result)");
            return;
        }
        List<McpSchema.PromptMessage> messages = lastResult.messages();
        if (messages == null || messages.isEmpty()) {
            render.emptyBadgeLabel("OK").responseLabel("Messages")
                    .commitEmptyResponse(prompt.name(), lastElapsedMs, "(empty messages)");
            return;
        }
        List<Component> blocks = new ArrayList<>(messages.size());
        for (McpSchema.PromptMessage msg : messages) {
            blocks.add(InspectorHelpers.buildPromptMessageBlock(msg));
        }
        render.responseLabel("Messages").commitOk(prompt.name(), lastElapsedMs, blocks);
    }

    private void clearLastResultState() {
        lastResult = null;
        lastError = null;
        lastElapsedMs = 0L;
    }
}
