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
package org.springaicommunity.playground.webui.mcp;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.risk.McpComposition;
import org.springaicommunity.playground.service.mcp.risk.McpCompositionService;
import org.springaicommunity.playground.service.mcp.risk.McpExposedToolService;
import org.springaicommunity.playground.service.mcp.risk.McpToolRiskAdvisor;
import org.springaicommunity.playground.service.mcp.risk.McpToolRiskComposer;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolSpecService.ExposureMode;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class McpExposedToolsPanel {

    private final McpExposedToolService exposedToolService;
    private final McpCompositionService compositionService;
    private final McpServerInfoService serverInfoService;
    private final McpClientService clientService;
    private final McpToolRiskAdvisor riskAdvisor;
    private final ToolSpecService toolSpecService;

    private final Map<String, McpComposition.Member> selected = new LinkedHashMap<>();
    private Select<ExposureMode> modeField;
    private Select<RiskLevel> capField;
    private VerticalLayout exposedArea;

    public McpExposedToolsPanel(McpExposedToolService exposedToolService, McpCompositionService compositionService,
            McpServerInfoService serverInfoService, McpClientService clientService, McpToolRiskAdvisor riskAdvisor,
            ToolSpecService toolSpecService) {
        this.exposedToolService = exposedToolService;
        this.compositionService = compositionService;
        this.serverInfoService = serverInfoService;
        this.clientService = clientService;
        this.riskAdvisor = riskAdvisor;
        this.toolSpecService = toolSpecService;
    }

    public Component build() {
        this.selected.clear();
        Optional<McpComposition> existing = this.compositionService.getExposed();
        existing.ifPresent(composition -> composition.members().forEach(member ->
                this.selected.put(key(member.serverId(), member.toolName()), member)));
        RiskLevel cap = existing.map(McpComposition::maxRiskLevel).orElse(RiskLevel.L3);

        Span intro = new Span("Pick what the built-in MCP server exposes; compose external tools below.");
        intro.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        this.exposedArea = new VerticalLayout();
        this.exposedArea.setPadding(false);
        this.exposedArea.setSpacing(false);

        this.modeField = new Select<>();
        this.modeField.setLabel("What the built-in server exposes");
        this.modeField.setItems(ExposureMode.BUILTIN_ONLY, ExposureMode.COMPOSED_ONLY, ExposureMode.BOTH);
        this.modeField.setItemLabelGenerator(McpExposedToolsPanel::modeLabel);
        this.modeField.setValue(this.toolSpecService.exposureMode());
        this.modeField.setHelperText("Built-in = your default + custom tools. Composed = the external tools below.");
        this.modeField.setWidthFull();

        this.capField = new Select<>();
        this.capField.setLabel("Max risk to expose (tools above this can't be selected)");
        this.capField.setItems(RiskLevel.L1, RiskLevel.L2, RiskLevel.L3, RiskLevel.L4, RiskLevel.L5);
        this.capField.setValue(cap);
        this.capField.setWidthFull();

        Checkbox hitlAll = new Checkbox("Mark all selected tools for human review (HITL, −1 risk band)");
        hitlAll.addValueChangeListener(event -> applyHitlToAllSelected(Boolean.TRUE.equals(event.getValue())));

        VerticalLayout root = new VerticalLayout(intro, this.exposedArea,
                this.modeField, this.capField, hitlAll);
        root.setPadding(false);
        root.setSpacing(true);
        refreshExposedArea();

        List<McpServerInfo> active = activeServers();
        if (active.isEmpty()) {
            Span empty = new Span("No connected external MCP servers. Connect a server from the Active MCP list first.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            root.add(empty);
            return root;
        }

        root.add(sectionLabel("Connected MCP servers"));
        for (McpServerInfo server : active) {
            root.add(buildServerAccordion(server));
        }
        return root;
    }

    public void apply() {
        ExposureMode mode = this.modeField.getValue();
        this.toolSpecService.setExposureMode(mode);
        this.exposedToolService.apply(new ArrayList<>(this.selected.values()), this.capField.getValue());
        String composed = mode.includesComposed()
                ? this.selected.size() + " composed tool(s) exposed" : "composed tools off";
        Notification.show("Built-in server exposure: " + modeLabel(mode) + " — " + composed, 3000,
                Notification.Position.BOTTOM_END);
    }

    private static String modeLabel(ExposureMode mode) {
        return switch (mode) {
            case BUILTIN_ONLY -> "Built-in tools only";
            case COMPOSED_ONLY -> "Composed tools only";
            case BOTH -> "Both built-in and composed";
        };
    }

    // Lazy build: tools/list is a network round-trip and each tool runs a risk scan, so defer to first expand.
    private Details buildServerAccordion(McpServerInfo server) {
        McpToolRiskAdvisor.ServerRiskView serverRisk = this.riskAdvisor.evaluateServer(server);

        Span name = new Span(server.serverName());
        name.getStyle().set("font-weight", "600");
        String transport = server.mcpTransportType() == null ? "" : server.mcpTransportType().name();
        Span info = new Span(transport + " · " + exposedCountFor(server) + " exposed");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout summary = new HorizontalLayout(name,
                new McpRiskChip(serverRisk.level(), serverRisk.floorTrigger(), "Server"), info);
        summary.setAlignItems(FlexComponent.Alignment.CENTER);

        Details details = new Details();
        details.setSummary(summary);
        details.setOpened(false);
        details.setWidthFull();
        boolean[] built = {false};
        details.addOpenedChangeListener(event -> {
            if (!event.isOpened() || built[0]) return;
            built[0] = true;
            List<McpSchema.Tool> tools = this.clientService.getToolListAsOpt(server).orElseGet(List::of);
            Runnable refreshSummary = () -> info.setText(transport + " · " + tools.size() + " tools · "
                    + exposedCountFor(server) + " exposed");
            refreshSummary.run();
            details.add(buildServerContent(server, tools, refreshSummary));
        });
        return details;
    }

    private Component buildServerContent(McpServerInfo server, List<McpSchema.Tool> tools,
            Runnable refreshServerSummary) {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(false);
        if (tools.isEmpty()) {
            content.add(new Span("This server exposes no tools."));
            return content;
        }
        Span count = new Span();
        List<Checkbox> rowChecks = new ArrayList<>();
        Checkbox selectAll = new Checkbox("Select all");
        selectAll.addValueChangeListener(event -> {
            boolean value = Boolean.TRUE.equals(event.getValue());
            rowChecks.forEach(check -> {
                if (check.isEnabled()) check.setValue(value);
            });
        });
        HorizontalLayout head = new HorizontalLayout(selectAll, count);
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        content.add(head);

        for (McpSchema.Tool tool : tools) {
            if (tool.name() == null || tool.name().isBlank()) continue;
            content.add(buildToolRow(server, tool, count, rowChecks, tools.size(), refreshServerSummary));
        }
        updateServerCount(count, server, tools.size());
        return content;
    }

    private Component buildToolRow(McpServerInfo server, McpSchema.Tool tool, Span count, List<Checkbox> rowChecks,
            int toolTotal, Runnable refreshServerSummary) {
        String rowKey = key(server.serverName(), tool.name());
        InspectorHelpers.ToolInfo toolInfo = InspectorHelpers.toToolInfo(tool);
        String originalDescription = tool.description() == null ? "" : tool.description();
        McpToolRiskAdvisor.ToolRiskView risk = this.riskAdvisor.evaluateTool(server, tool.name(),
                tool.description(), toolInfo.propertySchemas());
        RiskLevel baseLevel = risk.finalLevel();
        McpComposition.Member existing = this.selected.get(rowKey);

        Checkbox check = new Checkbox(existing != null);
        rowChecks.add(check);
        Span name = new Span(tool.name());
        name.getStyle().set("font-weight", "500").set("cursor", "pointer");
        if (!originalDescription.isBlank()) {
            Tooltip.forComponent(name).withText(originalDescription);
        }
        Icon caret = VaadinIcon.ANGLE_RIGHT.create();
        caret.setVisible(false);
        caret.getStyle().set("cursor", "pointer").set("transition", "transform 150ms ease-out")
                .set("width", "0.9em").set("height", "0.9em")
                .set("color", "var(--lumo-secondary-text-color)");
        Checkbox hitl = new Checkbox("HITL");
        hitl.setValue(existing != null && existing.hitl());
        hitl.setTooltipText("Marks this tool for human review and lowers its displayed risk by one band. "
                + "Pauses the call for approval at runtime (chat approval dialog, or MCP elicitation for "
                + "external clients), and does not let the tool exceed the risk cap.");
        Div chipHolder = new Div();
        chipHolder.getStyle().set("display", "inline-flex").set("align-items", "center").set("gap", "0.3em");

        TextField alias = new TextField("Exposed name");
        alias.setValue(existing != null ? existing.exposedAlias()
                : McpComposition.Member.defaultAlias(server.serverName(), tool.name()));
        alias.setWidthFull();
        TextArea description = new TextArea("Description");
        description.setValue(existing != null && existing.descriptionOverride() != null
                ? existing.descriptionOverride() : originalDescription);
        description.setWidthFull();
        description.setMaxHeight("90px");
        VerticalLayout editor = new VerticalLayout(alias, description);
        editor.setPadding(false);
        editor.setSpacing(false);
        editor.getStyle().set("margin", "0 0 0.5em 2.2em");

        boolean[] expanded = {false};
        Runnable updateExpansion = () -> {
            boolean active = Boolean.TRUE.equals(check.getValue()) && check.isEnabled();
            caret.setVisible(active);
            caret.getStyle().set("transform", expanded[0] ? "rotate(90deg)" : "none");
            editor.setVisible(active && expanded[0]);
        };
        Runnable toggleExpand = () -> {
            if (!Boolean.TRUE.equals(check.getValue())) return;
            expanded[0] = !expanded[0];
            updateExpansion.run();
        };
        name.getElement().addEventListener("click", event -> toggleExpand.run());
        caret.getElement().addEventListener("click", event -> toggleExpand.run());

        Runnable rebuildMember = () -> {
            if (Boolean.TRUE.equals(check.getValue())) {
                this.selected.put(rowKey, memberOf(server, tool, alias.getValue(), description.getValue(),
                        originalDescription, hitl.getValue()));
            } else {
                this.selected.remove(rowKey);
            }
        };
        Runnable refreshRow = () -> {
            RiskLevel effective = McpToolRiskComposer.applyHitlMitigation(baseLevel, hitl.getValue());
            // base risk, not hitl-lowered: hitl has no runtime gate so it must not lift the cap
            boolean overCap = baseLevel.ordinal() > this.capField.getValue().ordinal();
            chipHolder.removeAll();
            chipHolder.add(new McpRiskChip(effective, risk.floorTrigger()));
            if (Boolean.TRUE.equals(hitl.getValue())) {
                Span badge = new Span("HITL −1");
                badge.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                        .set("color", "var(--lumo-success-text-color)");
                chipHolder.add(badge);
            }
            if (overCap && Boolean.TRUE.equals(check.getValue())) check.setValue(false);
            check.setEnabled(!overCap);
            alias.setEnabled(!overCap);
            description.setEnabled(!overCap);
            updateExpansion.run();
        };

        check.addValueChangeListener(event -> {
            if (Boolean.TRUE.equals(event.getValue())) expanded[0] = true;
            rebuildMember.run();
            updateExpansion.run();
            updateServerCount(count, server, toolTotal);
            refreshServerSummary.run();
            refreshExposedArea();
        });
        hitl.addValueChangeListener(event -> {
            refreshRow.run();
            rebuildMember.run();
            refreshExposedArea();
        });
        alias.addValueChangeListener(event -> {
            rebuildMember.run();
            refreshExposedArea();
        });
        description.addValueChangeListener(event -> {
            rebuildMember.run();
            refreshExposedArea();
        });

        refreshRow.run();
        HorizontalLayout top = new HorizontalLayout(check, caret, name, chipHolder, hitl);
        top.setAlignItems(FlexComponent.Alignment.CENTER);
        VerticalLayout row = new VerticalLayout(top, editor);
        row.setPadding(false);
        row.setSpacing(false);
        return row;
    }

    private McpComposition.Member memberOf(McpServerInfo server, McpSchema.Tool tool, String aliasValue,
            String descriptionValue, String originalDescription, boolean hitl) {
        String alias = aliasValue == null || aliasValue.isBlank()
                ? McpComposition.Member.defaultAlias(server.serverName(), tool.name()) : aliasValue.trim();
        String override = descriptionValue == null || descriptionValue.equals(originalDescription)
                ? null : descriptionValue;
        return new McpComposition.Member(server.serverName(), tool.name(), alias, override, hitl, "");
    }

    // Open accordion rows aren't re-synced live; the selected set is authoritative on Apply.
    private void applyHitlToAllSelected(boolean hitl) {
        this.selected.replaceAll((rowKey, member) -> new McpComposition.Member(member.serverId(), member.toolName(),
                member.exposedAlias(), member.descriptionOverride(), hitl, member.contentHash()));
        refreshExposedArea();
    }

    private void updateServerCount(Span count, McpServerInfo server, int toolTotal) {
        long selectedHere = this.selected.keySet().stream()
                .filter(k -> k.startsWith(server.serverName() + "::")).count();
        count.setText(selectedHere + " / " + toolTotal + " selected");
        count.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
    }

    private void refreshExposedArea() {
        if (this.exposedArea == null) return;
        this.exposedArea.removeAll();
        Div box = new Div();
        box.getStyle().set("display", "flex").set("flex-wrap", "wrap").set("align-items", "center")
                .set("gap", "5px").set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("background", "var(--lumo-shade-5pct)").set("border-radius", "var(--lumo-border-radius-m)")
                .set("line-height", "1.9").set("width", "100%").set("box-sizing", "border-box");
        Span label = new Span("COMPOSED TOOLS");
        label.getStyle().set("font-size", "var(--lumo-font-size-xxs)").set("color", "var(--lumo-secondary-text-color)")
                .set("text-transform", "uppercase").set("letter-spacing", "0.08em")
                .set("margin-right", "var(--lumo-space-s)");
        Span summary = new Span(this.selected.isEmpty() ? "none" : this.selected.size() + " exposed");
        summary.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "var(--lumo-font-weight-semibold)").set("margin-right", "var(--lumo-space-s)");
        box.add(label, summary);
        for (McpComposition.Member member : this.selected.values()) {
            Span chip = new Span(member.exposedAlias() + (member.hitl() ? " · HITL" : ""));
            chip.getStyle().set("display", "inline-block").set("padding", "2px 9px")
                    .set("background", "rgba(31, 122, 90, 0.10)").set("color", "var(--lumo-primary-color)")
                    .set("border-radius", "999px")
                    .set("font-family", "'SF Mono', 'Monaco', 'Cascadia Code', 'Consolas', monospace")
                    .set("font-size", "var(--lumo-font-size-xxs)").set("line-height", "1.4");
            box.add(chip);
        }
        this.exposedArea.add(box);
    }

    private long exposedCountFor(McpServerInfo server) {
        return this.selected.keySet().stream().filter(k -> k.startsWith(server.serverName() + "::")).count();
    }

    private List<McpServerInfo> activeServers() {
        McpServerInfo builtin = this.serverInfoService.getDefaultMcpServerInfo();
        return this.serverInfoService.read().stream()
                .filter(this.clientService::isConnecting)
                .filter(info -> builtin == null || !info.serverName().equals(builtin.serverName()))
                .toList();
    }

    private Span sectionLabel(String text) {
        Span label = new Span(text);
        label.getStyle().set("font-weight", "600").set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)").set("margin-top", "0.5em");
        return label;
    }

    private static String key(String serverId, String toolName) {
        return serverId + "::" + toolName;
    }
}
