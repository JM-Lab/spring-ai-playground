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
package org.springaicommunity.playground.webui.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.observability.ObservabilityProperties;
import org.springaicommunity.playground.observability.ObservabilityRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.observability.system.SystemMetricsTimeSeries;
import org.springaicommunity.playground.observability.pricing.CurrencyService;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;
import org.springaicommunity.playground.webui.observability.ObservabilityDashboardSidebar.DashboardEntry;
import org.springaicommunity.playground.webui.observability.ObservabilityDashboardSidebar.Section;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ObservabilityGlobalSettings;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel.RefreshIntervalPicker;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel.TimeWindowPicker;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@SpringComponent
@UIScope
@AnonymousAllowed
@PageTitle("Observability")
@Route(value = "observability", layout = SpringAiPlaygroundAppLayout.class)
public class ObservabilityView extends ContentWorkspaceView {

    private static final String KEY_WINDOW = "obs.global.window";
    private static final String KEY_REFRESH = "obs.global.refresh";

    private final ObservabilityDashboardSidebar sidebar = new ObservabilityDashboardSidebar();
    private final Map<String, DashboardEntry> slugToEntry = new LinkedHashMap<>();
    private final Map<DashboardEntry, BaseDashboardTab> dashboards = new LinkedHashMap<>();
    private final ObservabilityGlobalSettings globalSettings = new ObservabilityGlobalSettings();
    private final LogsTab logsTab;
    private final WorkspaceSettingsDrawer settingsDrawer;
    private final TimeWindowPicker timeWindowPicker;
    private final RefreshIntervalPicker refreshIntervalPicker;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "observability-global-refresh");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> autoRefreshTask;
    private final PersistentUiDataStorage storage;
    private DashboardEntry currentEntry;
    private Button pricingButton;
    private CurrencyService currencyService;
    private ModelPricingService pricingServiceRef;

    public ObservabilityView(ObservabilityRingBuffer buffer,
            ObservabilityTimeSeries timeSeries,
            SystemMetricsSnapshot systemMetrics,
            SystemMetricsTimeSeries systemMetricsTimeSeries,
            ModelPricingService pricingService,
            CurrencyService currencyService,
            Path springAiPlaygroundHomeDir,
            PersistentUiDataStorage storage,
            McpClientService mcpClientService,
            ObservabilityProperties obsProps) {
        this.storage = storage;

        TokensAndCostTab tokens = new TokensAndCostTab(buffer, timeSeries, pricingService, currencyService);
        HostRuntimeTab hostRuntime = new HostRuntimeTab(systemMetrics, systemMetricsTimeSeries);
        WebApplicationTab webApp = new WebApplicationTab(systemMetrics);
        this.logsTab = new LogsTab(springAiPlaygroundHomeDir, globalSettings);
        ToolsTab toolsView = new ToolsTab(buffer, timeSeries, systemMetrics);
        McpTab mcp = new McpTab(buffer, timeSeries, systemMetrics, mcpClientService);
        McpPrimitivesTab mcpPrimitives = new McpPrimitivesTab(systemMetrics);
        VectorTab vector = new VectorTab(buffer, timeSeries);
        LlmTab llm = new LlmTab(timeSeries, systemMetrics);
        AgenticChatTab agenticChat = new AgenticChatTab(buffer, pricingService);
        TracesTab traces = new TracesTab(buffer);
        traces.setRowClickListener(trace -> new TraceDetailDialog(trace, buffer).open());
        OverviewTab overview = new OverviewTab(buffer, timeSeries, systemMetrics, pricingService,
                currencyService, obsProps, this::navigateToSlug);

        DashboardEntry overviewEntry = register("overview", "Overview", VaadinIcon.DASHBOARD, overview);
        DashboardEntry agenticEntry = register("agentic-chat", "Agentic Chat",
                VaadinIcon.CHAT, agenticChat);
        DashboardEntry tokensEntry = register("tokens", "Tokens & Cost", VaadinIcon.MONEY, tokens);
        DashboardEntry llmEntry = register("llm", "AI Models", VaadinIcon.CUBES, llm);
        DashboardEntry toolsEntry = register("tools", "Tool Studio", VaadinIcon.TOOLS, toolsView);
        DashboardEntry mcpEntry = register("mcp", "MCP Servers", VaadinIcon.TOOLBOX, mcp);
        DashboardEntry mcpPrimitivesEntry = register("mcp-primitives", "MCP Inspector",
                VaadinIcon.SEARCH, mcpPrimitives);
        DashboardEntry vectorEntry = register("vector", "Vector Database", VaadinIcon.SEARCH_PLUS, vector);
        DashboardEntry hostEntry = register("host", "Host",
                VaadinIcon.SERVER, hostRuntime);
        DashboardEntry webAppEntry = register("web-application", "Web Application",
                VaadinIcon.GLOBE_WIRE, webApp);
        DashboardEntry logsEntry = register("logs", "Logs", VaadinIcon.FILE_TEXT_O, logsTab);
        DashboardEntry tracesEntry = register("traces", "Traces", VaadinIcon.SITEMAP, traces);

        sidebar.setSections(List.of(
                new Section(null, List.of(overviewEntry)),
                new Section("AI Usage", List.of(tokensEntry, llmEntry)),
                new Section("AI Stack", List.of(toolsEntry, mcpEntry, mcpPrimitivesEntry,
                        vectorEntry, agenticEntry)),
                new Section("Runtime", List.of(hostEntry, webAppEntry,
                        logsEntry, tracesEntry))
        ));
        sidebar.setSelectionListener(this::showEntry);

        configureSidebar(sidebar, "Dashboards");
        setHeaderLabel("Observability");

        this.currencyService = currencyService;
        this.pricingServiceRef = pricingService;
        ModelPricingManagerDialog pricingDialog = new ModelPricingManagerDialog(pricingService, currencyService);
        Button pricingBtn = new Button("Pricing", VaadinIcon.MONEY.create());
        pricingBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        pricingBtn.setTooltipText("Model pricing + display currency / exchange rate");
        pricingBtn.addClickListener(e -> pricingDialog.open());
        pricingBtn.setVisible(false);
        this.pricingButton = pricingBtn;
        pricingDialog.onChanged(() -> {
            refreshPricingButtonLabel();
            refreshCurrent();
        });
        refreshPricingButtonLabel();
        addHeaderAction(pricingBtn);

        AtomicReference<Runnable> drawerToggleRef =
                new AtomicReference<>(() -> {});
        this.timeWindowPicker = new TimeWindowPicker(globalSettings,
                () -> drawerToggleRef.get().run());
        this.refreshIntervalPicker = new RefreshIntervalPicker(globalSettings,
                () -> drawerToggleRef.get().run(), this::refreshCurrent);
        Span toolbarSeparator = new Span();
        toolbarSeparator.getStyle().set("width", "1px")
                .set("height", "24px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("margin", "0 var(--lumo-space-xs)");
        HorizontalLayout toolbar = new HorizontalLayout(
                timeWindowPicker, toolbarSeparator, refreshIntervalPicker);
        toolbar.setSpacing(false);
        toolbar.setPadding(false);
        toolbar.getStyle().set("align-items", "center")
                .set("gap", "var(--lumo-space-xs)")
                .set("margin-right", "var(--lumo-space-m)");
        getHeaderEndMenuBar().addItem(toolbar);

        this.settingsDrawer = installSettingsDrawer(VaadinIcon.COG_O, "Observability settings",
                "Time range, refresh interval, layout");
        this.settingsDrawer.getStyle().set("width",
                "min(440px, calc(100% - 2 * var(--lumo-space-m)))");
        AtomicReference<ObservabilitySettingsPanel> currentPanelRef =
                new AtomicReference<>();
        this.settingsDrawer.setBodyFactory(() -> {
            ObservabilitySettingsPanel p = new ObservabilitySettingsPanel(globalSettings,
                    currentEntry == null ? null : dashboards.get(currentEntry).getSettingsPanel());
            currentPanelRef.set(p);
            return p;
        });
        this.settingsDrawer.setApplyButton("Apply", () -> {
            ObservabilitySettingsPanel p = currentPanelRef.get();
            if (p != null) p.applyStaged();
        });
        drawerToggleRef.set(this.settingsDrawer::toggle);

        globalSettings.onRangeChanged(this::refreshCurrent);
        globalSettings.onRefreshIntervalChanged(seconds -> rescheduleAutoRefresh());
        globalSettings.onRangeChanged(this::rescheduleAutoRefresh);

        loadPersistedSettings();
        sidebar.select(overviewEntry);
    }

    private void rescheduleAutoRefresh() {
        if (autoRefreshTask != null) {
            autoRefreshTask.cancel(false);
            autoRefreshTask = null;
        }
        if (!globalSettings.isAutoRefreshOn()) return;
        UI ui = UI.getCurrent();
        if (ui == null) return;
        int seconds = globalSettings.refreshSeconds();
        autoRefreshTask = scheduler.scheduleAtFixedRate(
                () -> ui.access(this::refreshCurrent),
                seconds, seconds, TimeUnit.SECONDS);
    }

    private void refreshCurrent() {
        if (currentEntry == null) return;
        BaseDashboardTab dash = dashboards.get(currentEntry);
        if (dash != null) dash.refresh(globalSettings.effectiveWindow());
    }

    private void loadPersistedSettings() {
        storage.loadData(KEY_WINDOW, new TypeReference<Window>() {}, w -> {
            if (w != null) {
                globalSettings.selectPreset(w);
                storage.saveData(KEY_WINDOW, w);
            }
        });
        storage.loadData(KEY_REFRESH, new TypeReference<Integer>() {}, s -> {
            if (s != null) globalSettings.setRefreshSeconds(s);
        });
        globalSettings.onRangeChanged(() -> {
            if (!globalSettings.hasCustomRange()) {
                storage.saveData(KEY_WINDOW, globalSettings.window());
            }
        });
        globalSettings.onRefreshIntervalChanged(
                seconds -> storage.saveData(KEY_REFRESH, seconds));
    }

    private DashboardEntry register(String slug, String label, VaadinIcon icon, BaseDashboardTab dashboard) {
        DashboardEntry entry = new DashboardEntry(slug, label, icon);
        slugToEntry.put(slug, entry);
        dashboards.put(entry, dashboard);
        return entry;
    }

    public void navigateToSlug(String slug) {
        DashboardEntry target = slugToEntry.get(slug);
        if (target != null) sidebar.select(target);
    }

    private void showEntry(DashboardEntry entry) {
        BaseDashboardTab dash = dashboards.get(entry);
        if (dash == null) return;
        currentEntry = entry;
        setContent(dash);
        setHeaderLabel("Observability — " + entry.label());
        dash.refresh(globalSettings.effectiveWindow());
        rescheduleAutoRefresh();
        if (pricingButton != null) {
            pricingButton.setVisible("tokens".equals(entry.slug()));
        }
    }

    private void refreshPricingButtonLabel() {
        if (pricingButton == null || currencyService == null) return;
        CurrencyService.CurrencyRate r = currencyService.getActiveRate();
        boolean noPricing = pricingServiceRef != null && pricingServiceRef.all().isEmpty();
        String flag = CurrencyService.flagFor(r.code());
        String currencyPart = "USD".equals(r.code())
                ? flag + " USD"
                : flag + " " + r.code() + " " + r.symbol() + "×" + r.rateFromUsd().toPlainString();
        String label = noPricing
                ? "⚠ Pricing — none configured · " + currencyPart
                : "Pricing · " + currencyPart;
        pricingButton.setText(label);
        pricingButton.getThemeNames().remove("warning");
        if (noPricing) pricingButton.getThemeNames().add("warning");
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        rescheduleAutoRefresh();
        Page page = attachEvent.getUI().getPage();
        page.fetchCurrentURL(url -> {
            String query = url.getQuery();
            if (query == null) return;
            String openTraceId = null;
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) continue;
                String k = pair.substring(0, eq);
                String v = pair.substring(eq + 1);
                if ("tab".equals(k)) {
                    DashboardEntry target = slugToEntry.get(v);
                    if (target != null) sidebar.select(target);
                } else if ("trace".equals(k)) {
                    openTraceId = v;
                }
            }
            if (openTraceId != null) {
                BaseDashboardTab tracesBinding = dashboards.get(slugToEntry.get("traces"));
                if (tracesBinding instanceof TracesTab tt) {
                    tt.openTraceById(openTraceId);
                }
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        if (autoRefreshTask != null) {
            autoRefreshTask.cancel(false);
            autoRefreshTask = null;
        }
        super.onDetach(detachEvent);
    }
}
