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
package org.springaicommunity.playground.webui.home;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer.RiskEvent;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.risk.McpRiskEvents;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springaicommunity.playground.webui.observability.ObservabilityView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

class HomeSystemPanel extends Div {

    private static final List<RiskLevel> RISK_LEVELS = List.of(
            new RiskLevel("L0", "verified", "#1D9E75"),
            new RiskLevel("L1", "safe", "#639922"),
            new RiskLevel("L2", "low", "#BA7517"),
            new RiskLevel("L3", "moderate", "#D85A30"),
            new RiskLevel("L4", "high", "#E24B4A"),
            new RiskLevel("L5", "critical", "#A32D2D"));

    private final Environment environment;
    private final ToolSpecService toolSpecService;
    private final ToolSpecPersistenceService toolSpecPersistenceService;
    private final ToolActivationCalculator toolActivationCalculator;
    private final McpServerInfoService mcpServerInfoService;
    private final McpClientService mcpClientService;
    private final ObservabilityTimeSeries observabilityTimeSeries;
    private final SystemMetricsSnapshot systemMetricsSnapshot;
    private final McpRiskEventRingBuffer mcpRiskEventRingBuffer;
    private final HomeProviderStatus providerStatus;

    private final Div body = new Div();
    private final Div envDetail = new Div();
    private final Div envPill = new Div();
    private Icon envChevron;
    private boolean envExpanded;

    HomeSystemPanel(ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            Optional<EmbeddingOptions> embeddingOptions,
            Environment environment,
            ToolSpecService toolSpecService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ToolActivationCalculator toolActivationCalculator,
            McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService,
            ObservabilityTimeSeries observabilityTimeSeries,
            SystemMetricsSnapshot systemMetricsSnapshot,
            McpRiskEventRingBuffer mcpRiskEventRingBuffer) {
        this.environment = environment;
        this.toolSpecService = toolSpecService;
        this.toolSpecPersistenceService = toolSpecPersistenceService;
        this.toolActivationCalculator = toolActivationCalculator;
        this.mcpServerInfoService = mcpServerInfoService;
        this.mcpClientService = mcpClientService;
        this.observabilityTimeSeries = observabilityTimeSeries;
        this.systemMetricsSnapshot = systemMetricsSnapshot;
        this.mcpRiskEventRingBuffer = mcpRiskEventRingBuffer;
        this.providerStatus = new HomeProviderStatus(
                chatModelProvider, embeddingModelProvider, embeddingOptions, environment);

        setWidthFull();
        getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.85rem")
                .set("padding", "1.1rem 1.25rem")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background-color", "var(--lumo-base-color)");

        this.envPill.addClickListener(e -> toggleEnvDetail());
        add(buildHeader(), buildStatusRow(), this.envDetail, this.body);
    }

    private Component buildStatusRow() {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "0.6rem");
        row.add(this.envPill, this.providerStatus);
        return row;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        render();
    }

    private Component buildHeader() {
        Div header = new Div();
        header.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("gap", "0.6rem");

        Div left = new Div();
        left.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "0.6rem");
        H3 title = new H3("System & Observability");
        title.getStyle().set("margin", "0").set("font-size", "var(--lumo-font-size-m)");
        left.add(title, buildServerPill(), buildBindPosture());

        RouterLink dashboards = new RouterLink("Open all 14 dashboards", ObservabilityView.class);
        dashboards.getStyle()
                .set("color", "var(--lumo-primary-text-color)")
                .set("text-decoration", "none")
                .set("font-size", "var(--lumo-font-size-s)");

        header.add(left, dashboards);
        return header;
    }

    private Component buildServerPill() {
        String port = environment.getProperty("server.port", "8282");
        Div pill = pill("var(--lumo-success-color)");
        Span text = new Span("Built-in MCP server live");
        text.getStyle().set("font-weight", "500");
        Span addr = new Span(":" + port + "/mcp");
        addr.getStyle()
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("font-family", "var(--lumo-font-family-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)");
        pill.add(text, addr);
        return pill;
    }

    private Component buildBindPosture() {
        String address = environment.getProperty("server.address", "").trim();
        boolean localhostOnly = address.equals("localhost")
                || address.equals("127.0.0.1") || address.equals("::1");
        if (localhostOnly) {
            Div pill = pill("var(--lumo-success-color)");
            pill.getElement().setAttribute("title",
                    "Only this machine can reach the built-in MCP server.");
            Span text = new Span("localhost only");
            pill.add(text);
            return pill;
        }
        Div pill = pill("var(--lumo-warning-color)");
        pill.getStyle()
                .set("border-color", "var(--lumo-warning-color-50pct)")
                .set("background-color", "var(--lumo-warning-color-10pct)");
        pill.getElement().setAttribute("title",
                "Anyone on your network can reach the built-in MCP server and call exposed tools. "
                        + "Set server.address=localhost to restrict it to this machine.");
        Span text = new Span("Bound " + (address.isEmpty() ? "0.0.0.0" : address) + " · no auth");
        text.getStyle().set("color", "var(--lumo-warning-text-color)");
        pill.add(text);
        return pill;
    }

    private void render() {
        EnvSnapshot env = envSnapshot();
        renderEnvPill(env);
        buildEnvDetail(env);
        body.removeAll();
        body.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.85rem");
        body.add(buildReadinessChips(env), buildKpis(), buildRisk());
    }

    private EnvSnapshot envSnapshot() {
        List<ToolSpec> defaults = toolSpecPersistenceService.getDefaultToolSpecs();
        Map<String, List<String>> toolsByVar = new TreeMap<>();
        Set<String> missingVars = new LinkedHashSet<>();
        long blockedTools = 0;
        for (ToolSpec spec : defaults) {
            List<String> missing = toolActivationCalculator.missingEnvVars(spec);
            if (!missing.isEmpty()) blockedTools++;
            missingVars.addAll(missing);
            for (String envVar : toolActivationCalculator.declaredEnvVars(spec)) {
                toolsByVar.computeIfAbsent(envVar, k -> new ArrayList<>()).add(spec.name());
            }
        }
        return new EnvSnapshot(defaults.size(), blockedTools, toolsByVar, missingVars);
    }

    private Component buildReadinessChips(EnvSnapshot env) {
        int total = env.totalTools();
        long needKeys = env.blockedTools();
        long localPassed = total - needKeys;

        McpServerInfo defaultInfo = mcpServerInfoService.getDefaultMcpServerInfo();
        List<McpServerInfo> external = mcpServerInfoService.getMcpServerInfos().values().stream()
                .flatMap(List::stream)
                .filter(info -> !Objects.equals(info, defaultInfo))
                .toList();
        long awaiting = external.stream()
                .filter(info -> mcpClientService.getStatus(info).status()
                        == McpClientService.ServerStatus.AWAITING_AUTHORIZATION)
                .count();
        long connected = external.size() - awaiting;

        Set<String> defaultIds = toolSpecPersistenceService.getDefaultToolIds();
        long exposed = toolSpecService.getToolMcpServerSetting().exposedToolIds().stream()
                .filter(defaultIds::contains).count();

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(220px, 1fr))")
                .set("gap", "0.55rem");

        grid.add(chip(VaadinIcon.CHECK_CIRCLE_O, "Built-in tools",
                localPassed + " Local-Passed · " + needKeys + " need keys", needKeys > 0,
                () -> UI.getCurrent().navigate(ToolStudioView.class)));
        grid.add(chip(VaadinIcon.CONNECT, "External MCP",
                awaiting > 0
                        ? connected + " connected · " + awaiting + " awaiting auth"
                        : connected + " connected",
                awaiting > 0,
                () -> UI.getCurrent().navigate(McpServerView.class)));
        grid.add(chip(VaadinIcon.BULLSEYE, "Exposed on MCP",
                exposed + " of " + localPassed, false,
                () -> UI.getCurrent().navigate(McpServerView.class)));
        return grid;
    }

    private Component chip(VaadinIcon icon, String label, String value, boolean warn, Runnable onClick) {
        Div chip = new Div();
        chip.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("gap", "0.5rem")
                .set("padding", "0.55rem 0.7rem")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("cursor", "pointer")
                .set("font-size", "var(--lumo-font-size-s)");

        Div text = new Div();
        text.getStyle().set("display", "flex").set("align-items", "center").set("gap", "0.4rem")
                .set("min-width", "0");
        Icon ic = icon.create();
        ic.getStyle().set("width", "var(--lumo-icon-size-s)").set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-secondary-text-color)").set("flex-shrink", "0");
        Span name = new Span(label);
        name.getStyle().set("color", "var(--lumo-body-text-color)");
        Span val = new Span(value);
        val.getStyle().set("color", warn
                ? "var(--lumo-warning-text-color)" : "var(--lumo-secondary-text-color)");
        text.add(ic, name, val);

        Icon chevron = VaadinIcon.CHEVRON_RIGHT.create();
        chevron.getStyle().set("width", "var(--lumo-icon-size-s)").set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-tertiary-text-color)").set("flex-shrink", "0");

        chip.add(text, chevron);
        chip.addClickListener(e -> onClick.run());
        return chip;
    }

    private void toggleEnvDetail() {
        envExpanded = !envExpanded;
        envDetail.setVisible(envExpanded);
        if (envChevron != null)
            envChevron.getStyle().set("transform", envExpanded ? "rotate(180deg)" : "none");
    }

    private void renderEnvPill(EnvSnapshot env) {
        envPill.removeAll();
        boolean missing = !env.missingVars().isEmpty();
        envPill.getStyle()
                .set("display", "inline-flex")
                .set("flex-wrap", "wrap")
                .set("max-width", "100%")
                .set("align-items", "center")
                .set("gap", "0.45rem")
                .set("padding", "0.3rem 0.7rem")
                .set("border-radius", "999px")
                .set("border", "1px solid " + (missing
                        ? "var(--lumo-warning-color-50pct)" : "var(--lumo-contrast-10pct)"))
                .set("background-color", missing
                        ? "var(--lumo-warning-color-10pct)" : "var(--lumo-base-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("cursor", "pointer");
        envPill.getElement().setAttribute("title",
                "Environment variables for built-in tools: click to expand details below");

        Div dot = new Div();
        dot.getStyle().set("width", "7px").set("height", "7px").set("border-radius", "50%")
                .set("background-color", missing
                        ? "var(--lumo-warning-color)" : "var(--lumo-success-color)")
                .set("flex-shrink", "0");

        Span name = new Span("Environment");
        name.getStyle().set("font-weight", "600").set("color", "var(--lumo-body-text-color)");

        String statusText;
        if (env.totalVars() == 0) statusText = "none required";
        else if (missing) statusText = env.missingVars().size() + " of " + env.totalVars()
                + " not set · " + env.blockedTools() + " tools blocked";
        else statusText = "all " + env.totalVars() + " set";
        Span status = new Span(statusText);
        status.getStyle().set("color", missing
                ? "var(--lumo-warning-text-color)" : "var(--lumo-secondary-text-color)");

        envChevron = VaadinIcon.ANGLE_DOWN.create();
        envChevron.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("flex-shrink", "0")
                .set("transition", "transform 0.15s")
                .set("transform", envExpanded ? "rotate(180deg)" : "none");

        envPill.add(dot, name, HomeUi.divider(), status, envChevron);
    }

    private void buildEnvDetail(EnvSnapshot env) {
        envDetail.removeAll();
        envDetail.setVisible(envExpanded);
        envDetail.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.6rem")
                .set("padding", "0.75rem 0.85rem")
                .set("border", "1px solid var(--lumo-primary-color-50pct)")
                .set("border-radius", "var(--lumo-border-radius-m)");

        envDetail.add(buildEnvNote());
        if (!env.missingVars().isEmpty()) {
            envDetail.add(envGroupLabel("Not set · " + env.missingVars().size(), true),
                    envVarGrid(env, true));
        }
        if (env.setVars() > 0) {
            envDetail.add(envGroupLabel("Set · " + env.setVars(), false),
                    envVarGrid(env, false));
        }
        if (env.totalVars() == 0) {
            envDetail.add(HomeUi.mutedLabel("No built-in tool declares an environment variable."));
        }
    }

    private Component buildEnvNote() {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "0.6rem")
                .set("padding", "0.5rem 0.6rem")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("background-color", "var(--lumo-primary-color-10pct)");

        Span text = new Span(
                "Built-in tools that declare a key (${ENV_VAR}) are held as drafts and never earn a "
                        + "Local Pass while the variable is unset, so they are not exposed on the built-in "
                        + "MCP server and can't be called from chat. Set the variable in the launcher or "
                        + "export it before starting the app. Keys are not entered here.");
        text.getStyle()
                .set("flex", "1 1 260px")
                .set("min-width", "0")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("line-height", "1.5");

        Anchor action = new Anchor(
                "https://spring-ai-community.github.io/spring-ai-playground/getting-started/",
                "Environment variables guide ›");
        action.setTarget("_blank");
        action.getElement().setAttribute("rel", "noopener");
        action.getStyle()
                .set("flex", "0 0 auto")
                .set("white-space", "nowrap")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("color", "var(--lumo-primary-text-color)")
                .set("text-decoration", "none")
                .set("padding", "0.3rem 0.6rem")
                .set("border", "1px solid var(--lumo-primary-color-50pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("background-color", "var(--lumo-base-color)");
        HomeUi.launcherActionOrDocs(action, "env-card", "Open Environment Variables ›");

        row.add(text, action);
        return row;
    }

    private static Component envGroupLabel(String text, boolean warn) {
        Span label = new Span(text);
        label.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.03em")
                .set("color", warn
                        ? "var(--lumo-warning-text-color)" : "var(--lumo-secondary-text-color)");
        return label;
    }

    private Component envVarGrid(EnvSnapshot env, boolean missingGroup) {
        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(230px, 1fr))")
                .set("gap", "0.45rem");
        env.toolsByVar().forEach((envVar, tools) -> {
            if (env.missingVars().contains(envVar) == missingGroup)
                grid.add(envVarCard(envVar, tools, missingGroup));
        });
        return grid;
    }

    private static Component envVarCard(String envVar, List<String> tools, boolean missing) {
        Div card = new Div();
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.15rem")
                .set("padding", "0.45rem 0.6rem")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("border", "1px solid " + (missing
                        ? "var(--lumo-warning-color-50pct)" : "var(--lumo-contrast-10pct)"))
                .set("background-color", missing
                        ? "var(--lumo-warning-color-10pct)" : "var(--lumo-contrast-5pct)")
                .set("min-width", "0");

        Div head = new Div();
        head.getStyle().set("display", "flex").set("align-items", "center").set("gap", "0.35rem")
                .set("min-width", "0");
        Div dot = new Div();
        dot.getStyle().set("width", "7px").set("height", "7px").set("border-radius", "50%")
                .set("background-color", missing
                        ? "var(--lumo-warning-color)" : "var(--lumo-success-color)")
                .set("flex-shrink", "0");
        Span name = new Span(envVar);
        name.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");
        name.getElement().setAttribute("title", envVar);
        Span state = new Span(missing ? "not set" : "set");
        state.getStyle()
                .set("margin-left", "auto")
                .set("flex-shrink", "0")
                .set("font-size", "var(--lumo-font-size-xxs)")
                .set("font-weight", "600")
                .set("color", missing
                        ? "var(--lumo-warning-text-color)" : "var(--lumo-success-text-color)");
        head.add(dot, name, state);

        String toolNames = String.join(", ", tools);
        Span uses = new Span((missing ? "blocks " : "used by ") + toolNames);
        uses.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");
        uses.getElement().setAttribute("title", toolNames);

        card.add(head, uses);
        return card;
    }


    private Component buildKpis() {
        ObservabilityTimeSeries.Series s = observabilityTimeSeries.compute(Window.LAST_30M);
        long toolCalls = Arrays.stream(s.toolCalls()).sum();

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fit, minmax(110px, 1fr))")
                .set("gap", "0.55rem");
        grid.add(
                kpi("Calls", compact(s.totalCalls())),
                kpi("Tokens", compact(s.totalTokens())),
                kpi("Tool calls", compact(toolCalls)),
                kpi("p95 latency", latency(s.overallP95LatencyMs())));
        return grid;
    }

    private static Component kpi(String label, String value) {
        Div tile = new Div();
        tile.getStyle()
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "0.6rem 0.75rem");
        Span l = new Span(label);
        l.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        Span v = new Span(value);
        v.getStyle().set("display", "block").set("font-size", "var(--lumo-font-size-xl)")
                .set("font-weight", "600");
        tile.add(l, v);
        return tile;
    }


    private Component buildRisk() {
        SystemMetricsSnapshot.Snapshot snap = systemMetricsSnapshot.capture();
        Map<String, Long> byLevel = snap.mcpToolRiskByLevel;
        long totalRisk = RISK_LEVELS.stream().mapToLong(r -> byLevel.getOrDefault(r.key(), 0L)).sum();

        long signals = snap.mcpRiskSignalByType.values().stream().mapToLong(Long::longValue).sum();
        long tamper = snap.mcpRiskSignalByType.getOrDefault(McpRiskEvents.Types.HASH_LEDGER_MISMATCH, 0L);
        long approved = snap.mcpHitlByOutcome.getOrDefault("approved", 0L);
        long hitlTotal = snap.mcpHitlByOutcome.values().stream().mapToLong(Long::longValue).sum();
        String hitlRate = hitlTotal == 0 ? "no HITL yet"
                : Math.round(approved * 100.0 / hitlTotal) + "% HITL approved";

        Div section = new Div();
        section.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "0.5rem");

        Div headerRow = new Div();
        headerRow.getStyle().set("display", "flex").set("flex-wrap", "wrap")
                .set("align-items", "center").set("justify-content", "space-between").set("gap", "0.5rem");
        Span title = new Span("Risk distribution · " + totalRisk + " tool calls");
        title.getStyle().set("font-weight", "500").set("font-size", "var(--lumo-font-size-s)");
        Span meta = new Span(signals + " signals · " + hitlRate + " · " + tamper + " tamper");
        meta.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        headerRow.add(title, meta);
        section.add(headerRow);

        if (totalRisk == 0) {
            Span empty = new Span("No tool risk recorded yet — run a tool from chat to populate.");
            empty.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-tertiary-text-color)");
            section.add(empty);
        } else {
            section.add(buildRiskBar(byLevel, totalRisk), buildRiskLegend(byLevel));
        }

        RiskEvent last = lastRiskEvent();
        if (last != null) {
            Span lastLine = new Span("Last risk event · " + last.summary()
                    + " · " + HomeUi.relativeTime(last.epochMs()));
            lastLine.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)");
            section.add(lastLine);
        }

        Span scope = new Span("Defense-in-depth for the local build-and-vet loop, not adversarial isolation.");
        scope.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)");
        section.add(scope);
        return section;
    }

    private Component buildRiskBar(Map<String, Long> byLevel, long total) {
        Div bar = new Div();
        bar.getStyle()
                .set("display", "flex")
                .set("height", "11px")
                .set("border-radius", "6px")
                .set("overflow", "hidden")
                .set("border", "1px solid var(--lumo-contrast-10pct)");
        for (RiskLevel level : RISK_LEVELS) {
            long count = byLevel.getOrDefault(level.key(), 0L);
            if (count <= 0) continue;
            Div seg = new Div();
            seg.getStyle()
                    .set("width", (count * 100.0 / total) + "%")
                    .set("background-color", level.color());
            bar.add(seg);
        }
        return bar;
    }

    private Component buildRiskLegend(Map<String, Long> byLevel) {
        Div legend = new Div();
        legend.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "0.4rem 0.9rem");
        for (RiskLevel level : RISK_LEVELS) {
            long count = byLevel.getOrDefault(level.key(), 0L);
            Div item = new Div();
            item.getStyle().set("display", "inline-flex").set("align-items", "center").set("gap", "0.3rem")
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-secondary-text-color)");
            Div dot = new Div();
            dot.getStyle().set("width", "8px").set("height", "8px").set("border-radius", "2px")
                    .set("background-color", level.color());
            item.add(dot, new Span(level.key() + " " + level.label() + " " + count));
            legend.add(item);
        }
        return legend;
    }

    private RiskEvent lastRiskEvent() {
        List<RiskEvent> events = mcpRiskEventRingBuffer.snapshot();
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }


    private static Div pill(String dotColor) {
        Div pill = new Div();
        pill.getStyle()
                .set("display", "inline-flex")
                .set("flex-wrap", "wrap")
                .set("max-width", "100%")
                .set("align-items", "center")
                .set("gap", "0.45rem")
                .set("padding", "0.3rem 0.7rem")
                .set("border-radius", "999px")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("background-color", "var(--lumo-base-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        Div dot = new Div();
        dot.getStyle().set("width", "7px").set("height", "7px").set("border-radius", "50%")
                .set("background-color", dotColor).set("flex-shrink", "0");
        pill.add(dot);
        return pill;
    }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    private static String latency(double ms) {
        if (ms <= 0) return "0 ms";
        if (ms < 1000) return Math.round(ms) + " ms";
        return String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }

    private record RiskLevel(String key, String label, String color) {}

    private record EnvSnapshot(int totalTools, long blockedTools,
            Map<String, List<String>> toolsByVar, Set<String> missingVars) {

        int totalVars() {
            return toolsByVar.size();
        }

        int setVars() {
            return totalVars() - missingVars.size();
        }
    }
}
