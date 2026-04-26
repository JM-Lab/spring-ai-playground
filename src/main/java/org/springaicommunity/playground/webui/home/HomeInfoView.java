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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.VaadinUtils;
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
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.springaicommunity.playground.webui.home.HomeUi.divider;
import static org.springaicommunity.playground.webui.home.HomeUi.relativeTime;

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

    private static volatile LatestRelease cachedRelease;
    private static volatile Instant cachedAt;

    private final Div updateBannerSlot;
    private final Div pwaSlot;
    private final HomeChecklist checklist;

    public HomeInfoView(ToolSpecService toolSpecService,
            McpServerInfoService mcpServerInfoService,
            VectorStoreDocumentService vectorStoreDocumentService,
            ChatHistoryService chatHistoryService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            Optional<EmbeddingOptions> embeddingOptions,
            Environment environment) {
        setSizeFull();

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

        HomeProviderStatus providerStatus = new HomeProviderStatus(
                chatModelProvider, embeddingModelProvider, embeddingOptions, environment);
        HomeSurfaceCards surfaceCards = new HomeSurfaceCards(
                toolSpecService, toolSpecPersistenceService,
                mcpServerInfoService, vectorStoreDocumentService);
        this.checklist = new HomeChecklist(
                chatModelProvider, toolSpecService, toolSpecPersistenceService,
                vectorStoreDocumentService, chatHistoryService, environment);
        HomeRecentActivity recentActivity = new HomeRecentActivity(
                toolSpecService, toolSpecPersistenceService,
                mcpServerInfoService, vectorStoreDocumentService, chatHistoryService);

        this.pwaSlot = new Div();
        this.pwaSlot.setWidthFull();
        this.pwaSlot.setVisible(false);

        content.add(
                this.updateBannerSlot,
                createHero(),
                providerStatus,
                surfaceCards,
                this.checklist,
                recentActivity,
                this.pwaSlot
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
                "const isStandalone = window.matchMedia('(display-mode: standalone)').matches;"
                        + "const isElectron = /electron/i.test(navigator.userAgent);"
                        + "const checklistCollapsed = localStorage.getItem('home_checklist_collapsed') === 'true';"
                        + "$0.$server.onClientEnvironment(!isStandalone && !isElectron, checklistCollapsed);",
                getElement());

        if (!DEV_VERSION.equals(CURRENT_VERSION)) {
            CompletableFuture.supplyAsync(HomeInfoView::fetchLatestRelease)
                    .thenAccept(release -> {
                        if (release == null) {
                            logger.info("Update check: could not fetch latest release");
                            return;
                        }
                        boolean same = isSameVersion(release.tagName(), CURRENT_VERSION);
                        logger.info("Update check: current={} latest={} upToDate={}",
                                CURRENT_VERSION, release.tagName(), same);
                        if (same) return;
                        ui.access(() -> renderUpdateBanner(release));
                    })
                    .exceptionally(ex -> {
                        logger.debug("Update check failed", ex);
                        return null;
                    });
        }
    }

    @ClientCallable
    private void onClientEnvironment(boolean isBrowser, boolean checklistCollapsed) {
        if (isBrowser) {
            pwaSlot.removeAll();
            pwaSlot.add(createPwaSection());
            pwaSlot.setVisible(true);
        }
        checklist.applyCollapsedState(checklistCollapsed);
    }

    // ---------- Hero ----------

    private Component createHero() {
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
        return hero;
    }

    // ---------- PWA ----------

    private Component createPwaSection() {
        Div section = new Div();
        section.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.6rem")
                .set("padding", "1.1rem 1.25rem")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        H3 title = new H3("Install as Progressive Web App");
        title.getStyle()
                .set("margin", "0")
                .set("font-size", "var(--lumo-font-size-m)");

        Paragraph description = new Paragraph(
                "Install the browser app for a standalone window. "
                        + "For the full experience, use the native desktop installer from the Releases page.");
        description.getStyle()
                .set("margin", "0")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        Button installButton = new Button("Install PWA", VaadinIcon.DOWNLOAD.create());
        installButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        installButton.setId("installPwaBtn");
        installButton.getStyle().set("align-self", "flex-start");
        installButton.addClickListener(e ->
                VaadinUtils.getUi(this).getPage().executeJs(
                        "if (typeof window.removePwaPopup === 'function') { window.removePwaPopup(); }"
                                + "if (window.pwaInstall && window.pwaInstall.deferredPrompt) {"
                                + "  window.pwaInstall.deferredPrompt.prompt();"
                                + "} else {"
                                + "  alert('The app may already be installed or install is not currently available.');"
                                + "}"
                )
        );

        section.add(title, description, installButton);
        return section;
    }

    // ---------- Update banner ----------

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
                detailLine.add(divider());
            }
            detailLine.add(new Span("Released " + relativeTime(release.publishedAtEpochMs())));
        }
        if (detailLine.getElement().getChildCount() > 0) {
            detailLine.add(divider());
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

    // ---------- Version + release fetching ----------

    private static boolean isSameVersion(String a, String b) {
        return normalizeVersion(a).equalsIgnoreCase(normalizeVersion(b));
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
