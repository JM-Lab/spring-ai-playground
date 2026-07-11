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
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.QueryParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.observability.McpRiskEventRingBuffer;
import org.springaicommunity.playground.observability.ObservabilityTimeSeries;
import org.springaicommunity.playground.observability.system.SystemMetricsSnapshot;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.catalog.McpCatalogService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.tool.ToolActivationCalculator;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HomeInfoView extends Div {

    private static final Logger logger = LoggerFactory.getLogger(HomeInfoView.class);

    private static final String DEV_VERSION = "dev";
    private static final String CURRENT_VERSION = loadCurrentVersion();
    private static final String RELEASES_API =
            "https://api.github.com/repos/spring-ai-community/spring-ai-playground/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/spring-ai-community/spring-ai-playground/releases/latest";
    private static final String DOWNLOAD_GUIDE =
            "https://spring-ai-community.github.io/spring-ai-playground/#1-download-the-desktop-app";
    private static final String CONFIGURE_URL =
            "https://spring-ai-community.github.io/spring-ai-playground/getting-started/";

    private static final Pattern PRE_RELEASE_SUFFIX = Pattern.compile("(?i)(M|RC)(\\d+)");
    private static final int STAGE_UNKNOWN = 0;
    private static final int STAGE_MILESTONE = 1;
    private static final int STAGE_RC = 2;
    private static final int STAGE_GA = 3;

    private static volatile LatestRelease cachedRelease;
    private static volatile Instant cachedAt;

    private final Div updateBannerSlot;
    private final Div alertBannerSlot;
    private final HomeChecklist checklist;
    private final boolean desktopManagedUpdates;

    public HomeInfoView(ToolSpecService toolSpecService,
            McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService,
            McpCatalogService mcpCatalogService,
            VectorStoreDocumentService vectorStoreDocumentService,
            ChatHistoryService chatHistoryService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ToolActivationCalculator toolActivationCalculator,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            Optional<EmbeddingOptions> embeddingOptions,
            ObservabilityTimeSeries observabilityTimeSeries,
            SystemMetricsSnapshot systemMetricsSnapshot,
            McpRiskEventRingBuffer mcpRiskEventRingBuffer,
            Environment environment) {
        setSizeFull();
        this.desktopManagedUpdates = environment.getProperty(
                "spring.ai.playground.desktop.managed-updates", Boolean.class, false);

        VerticalLayout content = new VerticalLayout();
        content.setWidthFull();
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle()
                .set("padding", "2rem")
                .set("gap", "1.75rem")
                .set("max-width", "1400px")
                .set("margin", "0 auto");

        this.updateBannerSlot = new Div();
        this.updateBannerSlot.setWidthFull();
        this.updateBannerSlot.setVisible(false);

        this.alertBannerSlot = new Div();
        this.alertBannerSlot.setWidthFull();
        this.alertBannerSlot.setVisible(false);
        if (chatModelProvider.getIfAvailable() == null) {
            renderProviderAlert();
        }

        HomeSystemPanel systemPanel = new HomeSystemPanel(
                chatModelProvider, embeddingModelProvider, embeddingOptions, environment,
                toolSpecService, toolSpecPersistenceService, toolActivationCalculator,
                mcpServerInfoService, mcpClientService,
                observabilityTimeSeries, systemMetricsSnapshot, mcpRiskEventRingBuffer);
        HomeSurfaceCards surfaceCards = new HomeSurfaceCards(
                toolSpecService, toolSpecPersistenceService,
                mcpServerInfoService, mcpClientService, vectorStoreDocumentService, mcpCatalogService,
                chatHistoryService);
        this.checklist = new HomeChecklist(
                chatModelProvider, toolSpecService, toolSpecPersistenceService,
                vectorStoreDocumentService, chatHistoryService, environment);
        HomeRecentActivity recentActivity = new HomeRecentActivity(
                toolSpecService, toolSpecPersistenceService,
                mcpServerInfoService, vectorStoreDocumentService, chatHistoryService);

        content.add(
                this.alertBannerSlot,
                this.updateBannerSlot,
                createHero(chatModelProvider, chatHistoryService),
                surfaceCards,
                systemPanel,
                this.checklist,
                recentActivity
        );

        Scroller scroller = new Scroller(content);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        add(scroller);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();

        ui.getPage().executeJs(
                "const checklistCollapsed = localStorage.getItem('home_checklist_collapsed') === 'true';"
                        + "$0.$server.onClientEnvironment(checklistCollapsed);",
                getElement());

        if (!desktopManagedUpdates && !DEV_VERSION.equals(CURRENT_VERSION)) {
            CompletableFuture.supplyAsync(HomeInfoView::fetchLatestRelease)
                    .thenAccept(release -> {
                        if (release == null) {
                            logger.info("Update check: could not fetch latest release");
                            return;
                        }
                        if (!isNewer(release.tagName(), CURRENT_VERSION)) {
                            logger.info("Update up-to-date: current={} latest={}",
                                    CURRENT_VERSION, release.tagName());
                            return;
                        }
                        logger.info("Update available: current={} latest={}",
                                CURRENT_VERSION, release.tagName());
                        ui.access(() -> renderUpdateBanner(release));
                    })
                    .exceptionally(ex -> {
                        logger.debug("Update check failed", ex);
                        return null;
                    });
        }
    }

    @ClientCallable
    private void onClientEnvironment(boolean checklistCollapsed) {
        checklist.applyCollapsedState(checklistCollapsed);
    }


    private Component createHero(ObjectProvider<ChatModel> chatModelProvider,
            ChatHistoryService chatHistoryService) {
        Div hero = new Div();
        hero.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.5rem");

        H1 title = new H1("Spring AI Playground");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "2.25rem")
                .set("letter-spacing", "-0.02em");

        Paragraph tagline = new Paragraph("Safe Local Execution Layer for AI Agent Tools");
        tagline.getStyle()
                .set("margin", "0")
                .set("font-size", "var(--lumo-font-size-l)")
                .set("color", "var(--lumo-secondary-text-color)");

        Span motto = new Span("No pass, no run.");
        motto.getStyle()
                .set("display", "inline-block")
                .set("padding", "0.3rem 0.8rem")
                .set("border-radius", "999px")
                .set("background-color", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("flex-shrink", "0")
                .set("white-space", "nowrap");

        Div mottoHint = new Div();
        mottoHint.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("line-height", "1.55")
                .set("flex", "1 1 320px")
                .set("min-width", "0");

        Span hintBefore = new Span("Every tool earns a ");
        Span localPassBadge = new Span("Local Pass");
        localPassBadge.getStyle()
                .set("display", "inline-block")
                .set("padding", "0.05rem 0.45rem")
                .set("margin", "0 0.15rem")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("background-color", "var(--lumo-success-color-10pct)")
                .set("color", "var(--lumo-success-text-color)")
                .set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("letter-spacing", "0.02em")
                .set("vertical-align", "baseline");
        Span hintAfter = new Span(
                " — a local test-run with your sample arguments. "
                        + "Only passing tools go live on the built-in MCP server "
                        + "and become callable from chat.");
        mottoHint.add(hintBefore, localPassBadge, hintAfter);

        Div principleRow = new Div();
        principleRow.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "1rem")
                .set("margin-top", "0.5rem");
        principleRow.add(motto, mottoHint);

        hero.add(title, tagline, principleRow);
        Component cta = createHeroCta(chatModelProvider, chatHistoryService);
        if (cta != null) {
            hero.add(cta);
        }
        return hero;
    }

    private Component createHeroCta(ObjectProvider<ChatModel> chatModelProvider,
            ChatHistoryService chatHistoryService) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "0.75rem")
                .set("margin-top", "0.75rem");

        if (chatModelProvider.getIfAvailable() == null) {
            return null;
        }

        Optional<ChatHistory> recent = chatHistoryService.getChatHistoryList().stream()
                .max(Comparator.comparingLong(ChatHistory::updateTimestamp));
        if (recent.isEmpty()) {
            return null;
        }
        ChatHistory history = recent.get();
        String label = (history.title() == null || history.title().isBlank())
                ? "last chat" : history.title();
        Button resume = new Button("Resume · " + label, VaadinIcon.PLAY.create());
        resume.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        String conversationId = history.conversationId();
        resume.addClickListener(e -> UI.getCurrent().navigate(
                ChatView.class, QueryParameters.simple(Map.of("conv", conversationId))));
        row.add(resume);
        return row;
    }

    private static void styleCtaAnchor(Anchor anchor) {
        anchor.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "0.4rem")
                .set("color", "var(--lumo-primary-contrast-color)")
                .set("background-color", "var(--lumo-primary-color)")
                .set("padding", "0.55rem 1rem")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("text-decoration", "none")
                .set("font-weight", "500");
    }


    private void renderProviderAlert() {
        Div banner = new Div();
        banner.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("flex-wrap", "wrap")
                .set("gap", "0.75rem")
                .set("padding", "0.75rem 1rem")
                .set("background-color", "var(--lumo-warning-color-10pct)")
                .set("border", "1px solid var(--lumo-warning-color-50pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        Icon icon = VaadinIcon.WARNING.create();
        icon.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-warning-text-color)");

        Div textBlock = new Div();
        textBlock.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.15rem")
                .set("flex", "1 1 auto")
                .set("min-width", "0");
        Span headline = new Span("No model provider configured");
        headline.getStyle()
                .set("font-weight", "600")
                .set("color", "var(--lumo-body-text-color)");
        Span detail = new Span("Configure Ollama or an OpenAI key to start chatting.");
        detail.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        textBlock.add(headline, detail);

        Anchor configure = new Anchor(CONFIGURE_URL, "Configure a provider");
        configure.setTarget("_blank");
        configure.getElement().setAttribute("rel", "noopener");
        styleCtaAnchor(configure);
        HomeUi.routeToLauncherOnDesktop(configure, "config-card");

        Button dismiss = new Button(VaadinIcon.CLOSE_SMALL.create(),
                e -> alertBannerSlot.setVisible(false));
        dismiss.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        dismiss.setAriaLabel("Dismiss");

        banner.add(icon, textBlock, configure, dismiss);
        alertBannerSlot.removeAll();
        alertBannerSlot.add(banner);
        alertBannerSlot.setVisible(true);
    }


    private void renderUpdateBanner(LatestRelease release) {
        Div banner = new Div();
        banner.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("flex-wrap", "wrap")
                .set("gap", "0.75rem")
                .set("padding", "0.75rem 1rem")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)");

        Icon icon = VaadinIcon.ARROW_UP.create();
        icon.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        Div textBlock = new Div();
        textBlock.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.15rem")
                .set("flex", "1 1 auto")
                .set("min-width", "0");

        Span headline = new Span("Update available · " + release.tagName());
        headline.getStyle()
                .set("font-weight", "600")
                .set("color", "var(--lumo-body-text-color)");

        Div detailLine = new Div();
        detailLine.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("align-items", "center")
                .set("gap", "0.4rem")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        String releaseTitle = release.name();
        if (releaseTitle != null && !releaseTitle.isBlank()
                && !releaseTitle.equalsIgnoreCase(release.tagName())) {
            detailLine.add(new Span(releaseTitle));
        }
        if (release.publishedAtEpochMs() != null) {
            if (detailLine.getElement().getChildCount() > 0) {
                detailLine.add(HomeUi.divider());
            }
            detailLine.add(new Span("Released " + HomeUi.relativeTime(release.publishedAtEpochMs())));
        }
        if (detailLine.getElement().getChildCount() > 0) {
            detailLine.add(HomeUi.divider());
        }
        detailLine.add(new Span("Download the desktop installer for your platform."));

        textBlock.add(headline, detailLine);

        Anchor downloadLink = new Anchor(DOWNLOAD_GUIDE, "Download");
        downloadLink.setTarget("_blank");
        downloadLink.getElement().setAttribute("rel", "noopener");
        downloadLink.getStyle()
                .set("color", "var(--lumo-primary-contrast-color)")
                .set("background-color", "var(--lumo-primary-color)")
                .set("padding", "0.35rem 0.8rem")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("text-decoration", "none")
                .set("font-weight", "500")
                .set("font-size", "var(--lumo-font-size-s)");

        Anchor notesLink = new Anchor(release.htmlUrl(), "Release notes");
        notesLink.setTarget("_blank");
        notesLink.getElement().setAttribute("rel", "noopener");
        notesLink.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("text-decoration", "none")
                .set("font-size", "var(--lumo-font-size-s)");

        Button dismiss = new Button(VaadinIcon.CLOSE_SMALL.create(),
                e -> updateBannerSlot.setVisible(false));
        dismiss.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        dismiss.setAriaLabel("Dismiss");

        banner.add(icon, textBlock, downloadLink, notesLink, dismiss);
        updateBannerSlot.removeAll();
        updateBannerSlot.add(banner);
        updateBannerSlot.setVisible(true);
    }


    static boolean isNewer(String latest, String current) {
        int[] l = parseVersion(latest);
        int[] c = parseVersion(current);
        if (l == null || c == null) return false;
        for (int i = 0; i < l.length; i++) {
            if (l[i] != c[i]) return l[i] > c[i];
        }
        return false;
    }

    static int[] parseVersion(String version) {
        String v = normalizeVersion(version);
        if (v.isEmpty()) return null;
        int dash = v.indexOf('-');
        String core = dash < 0 ? v : v.substring(0, dash);
        String suffix = dash < 0 ? "" : v.substring(dash + 1);
        String[] parts = core.split("\\.");
        int[] out = { 0, 0, 0, STAGE_GA, 0 };
        try {
            for (int i = 0; i < 3 && i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
            if (!suffix.isEmpty()) {
                Matcher preRelease = PRE_RELEASE_SUFFIX.matcher(suffix);
                if (preRelease.matches()) {
                    out[3] = "M".equalsIgnoreCase(preRelease.group(1)) ? STAGE_MILESTONE : STAGE_RC;
                    out[4] = Integer.parseInt(preRelease.group(2));
                } else {
                    out[3] = STAGE_UNKNOWN;
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }

    private static String normalizeVersion(String version) {
        if (version == null) return "";
        String trimmed = version.trim();
        if (trimmed.isEmpty()) return "";
        char first = trimmed.charAt(0);
        if (first == 'v' || first == 'V') {
            return trimmed.substring(1);
        }
        return trimmed;
    }

    private static String loadCurrentVersion() {
        try (var is = HomeInfoView.class.getClassLoader()
                .getResourceAsStream("META-INF/build-info.properties")) {
            if (is == null) return DEV_VERSION;
            Properties props = new Properties();
            props.load(is);
            String version = props.getProperty("build.version");
            return version != null && !version.isBlank() ? version : DEV_VERSION;
        } catch (IOException e) {
            logger.debug("Could not load build-info.properties", e);
            return DEV_VERSION;
        }
    }

    private static LatestRelease fetchLatestRelease() {
        LatestRelease cached = cachedRelease;
        Instant fetchedAt = cachedAt;
        if (cached != null && fetchedAt != null
                && Duration.between(fetchedAt, Instant.now()).toHours() < 6) {
            return cached;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RELEASES_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.debug("GitHub releases API returned status {}", response.statusCode());
                return null;
            }
            String body = response.body();
            String tag = extractJsonField(body, "tag_name");
            String url = extractJsonField(body, "html_url");
            String name = extractJsonField(body, "name");
            String publishedAt = extractJsonField(body, "published_at");
            Long publishedAtMs = null;
            if (publishedAt != null && !publishedAt.isBlank()) {
                try {
                    publishedAtMs = Instant.parse(publishedAt).toEpochMilli();
                } catch (Exception ignored) {
                    // leave null
                }
            }
            if (tag == null || tag.isBlank()) return null;
            LatestRelease release = new LatestRelease(tag,
                    url != null ? url : RELEASES_PAGE,
                    name,
                    publishedAtMs);
            cachedRelease = release;
            cachedAt = Instant.now();
            return release;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            logger.debug("Failed to fetch latest release", e);
            return null;
        }
    }

    private static String extractJsonField(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int idx = json.indexOf(marker);
        if (idx < 0) return null;
        int start = idx + marker.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private record LatestRelease(String tagName, String htmlUrl, String name, Long publishedAtEpochMs) {}
}
