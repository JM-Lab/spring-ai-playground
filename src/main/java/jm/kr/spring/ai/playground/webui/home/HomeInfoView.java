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
package jm.kr.spring.ai.playground.webui.home;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class HomeInfoView extends Div {

    public static final String UPDATE_GUIDE;

    static {
        try (var is = HomeInfoView.class.getClassLoader().getResourceAsStream("update-guide.md")) {
            if (is == null) {
                throw new IllegalArgumentException("update-guide.md not found in resources");
            }
            UPDATE_GUIDE = new String(is.readAllBytes());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String cachedCommitMessage;
    private static Instant cachedCommitTime;
    private static Instant lastFetchedTime;

    private final Span latestCommitSpan;
    private final Button installButton;

    public HomeInfoView() {
        setSizeFull();

        VerticalLayout contentLayout = new VerticalLayout();
        contentLayout.setWidthFull();
        contentLayout.setSpacing(false);
        contentLayout.setPadding(false);
        contentLayout.getStyle()
                .set("padding-left", "2rem")
                .set("padding-right", "2rem");
        add(contentLayout);

        HorizontalLayout latestCommitLayout = new HorizontalLayout();
        latestCommitLayout.setWidthFull();
        latestCommitLayout.getStyle()
                .set("padding", "1rem")
                .set("background-color", "#f0f0f0")
                .set("border-radius", "0.5rem")
                .set("align-items", "center");

        Icon infoIcon = VaadinUtils.styledLargeIcon(VaadinIcon.INFO_CIRCLE.create());

        latestCommitSpan = new Span("Loading recent update...");
        latestCommitSpan.getStyle()
                .set("fontWeight", "600")
                .set("color", "var(--lumo-primary-text-color)")
                .set("padding", "0.1rem 0.4rem")
                .set("borderRadius", "var(--lumo-border-radius-m)");

        latestCommitLayout.add(infoIcon, latestCommitSpan);
        contentLayout.add(latestCommitLayout);
        loadLatestCommit();

        Details updateGuide = new Details();
        updateGuide.setSummary(new Span("Update Guide"));
        updateGuide.getSummary().getElement().getStyle()
                .set("font-weight", "bold")
                .set("color", "var(--lumo-primary-text-color)");

        Markdown markdown = new Markdown(UPDATE_GUIDE);
        updateGuide.add(markdown);
        updateGuide.setOpened(false);
        contentLayout.add(updateGuide);

        contentLayout.add(new H1("Welcome to Spring AI Playground"));
        contentLayout.add(new Paragraph(
                "Spring AI Playground is a self-hosted web UI that simplifies AI experimentation and testing. " +
                        "It provides Java developers with an intuitive interface for working with large language models (LLMs), vector databases, " +
                        "prompt engineering, and Model Context Protocol (MCP) integrations."));

        contentLayout.add(new H2("Install as Progressive Web App"));
        contentLayout.add(new Paragraph(
                "Once the application is running in the browser, it can be installed as a Progressive Web App (PWA) for a native-like experience."
        ));

        installButton = new Button("Install PWA", VaadinIcon.DOWNLOAD.create());
        installButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        installButton.setId("installPwaBtn");
        installButton.addClickListener(e ->
                VaadinUtils.getUi(this).getPage().executeJs(
                        "if (typeof window.removePwaPopup === 'function') { window.removePwaPopup(); }" +
                                "if (window.pwaInstall && window.pwaInstall.deferredPrompt) {" +
                                "  window.pwaInstall.deferredPrompt.prompt();" +
                                "} else {" +
                                "  alert('The application may already be installed or is not available for installation at this moment.');" +
                                "}"
                )
        );
        contentLayout.add(installButton);

        contentLayout.add(new Hr());

        contentLayout.add(new H3("Manual Installation Instructions by Browser"));
        contentLayout.add(new Paragraph(
                "If the 'Install PWA' button above does not work, you can use the methods below to install the app directly."
        ));
        contentLayout.add(createPwaInstructions());

        Scroller scroller = new Scroller(contentLayout);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        add(scroller);
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        VaadinUtils.getUi(this).getPage().executeJs(
                "if (window.matchMedia('(display-mode: standalone)').matches) {" +
                        "  $0.$server.disableInstallButtonInPwaMode();" +
                        "}", getElement()
        );
    }

    @ClientCallable
    private void disableInstallButtonInPwaMode() {
        installButton.setEnabled(false);
        installButton.setText("Running in PWA mode");
        installButton.setIcon(VaadinIcon.CHECK_CIRCLE.create());
    }

    private Div createPwaInstructions() {
        Div container = new Div();
        container.add(new H4("Chrome (Desktop)"));
        Anchor chromeLink = new Anchor("http://localhost:8282", "http://localhost:8282");
        chromeLink.setTarget("_blank");
        container.add(new UnorderedList(new ListItem(new Text("Open "), chromeLink), new ListItem(new Html(
                "<span>Look for the <strong>Install icon</strong> in the address bar (a monitor with a download arrow).</span>")),
                new ListItem("Click Install, then confirm to add the app.")));

        container.add(new H4("Edge (Desktop)"));
        Anchor edgeLink = new Anchor("http://localhost:8282", "http://localhost:8282");
        edgeLink.setTarget("_blank");
        container.add(new UnorderedList(new ListItem(new Text("Open "), edgeLink), new ListItem(
                new Html("<span>Click the <strong>App available</strong> icon in the address bar or go to " +
                        "<strong>Settings and more > Apps > Install this site as an app</strong>.</span>")),
                new ListItem("Confirm installation.")));

        container.add(new H4("Safari (Desktop)"));
        Anchor safariLink = new Anchor("http://localhost:8282", "http://localhost:8282");
        safariLink.setTarget("_blank");
        container.add(new UnorderedList(new ListItem(new Text("Open "), safariLink), new ListItem(new Html(
                "<span>Go to the <strong>File</strong> menu and choose <strong>Add to Dock</strong> " +
                        "(or click the <strong>Share</strong> button and select <strong>Add to Dock</strong> on macOS Sonoma+).</span>")),
                new ListItem("Optionally rename the app and click Add; it will appear in the Dock and Launchpad.")));

        container.add(new H4("After Installation"));
        container.add(new UnorderedList(new ListItem("Launch the app from the OS app launcher or home screen."),
                new ListItem("The PWA runs in its own window with a streamlined UI."),
                new ListItem("Uninstall any time from the browser’s app management or OS app settings.")));

        return container;
    }

    private void loadLatestCommit() {
        if (cachedCommitMessage != null && lastFetchedTime != null) {
            Duration diff = Duration.between(lastFetchedTime, Instant.now());
            if (diff.toMinutes() < 60) {
                latestCommitSpan.setText(formatCommitDisplay(cachedCommitMessage, cachedCommitTime));
                return;
            }
        }

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI("https://api.github.com/repos/spring-ai-community/spring-ai-playground/commits?per_page=1"))
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body();

                String message = body.split("\"message\":\"")[1].split("\"")[0];
                message = cleanCommitMessage(message);

                String dateStr = body.split("\"date\":\"")[1].split("\"")[0];
                Instant commitTime = Instant.parse(dateStr);

                cachedCommitMessage = message;
                cachedCommitTime = commitTime;
                lastFetchedTime = Instant.now();

                latestCommitSpan.setText(formatCommitDisplay(message, commitTime));
            } else {
                latestCommitSpan.setText("Failed to fetch recent update, status: " + response.statusCode());
            }
        } catch (Exception e) {
            latestCommitSpan.setText("Error fetching recent update");
            e.printStackTrace();
        }
    }

    private String cleanCommitMessage(String message) {
        return message.replaceAll("(?i)(?:\\\\n\\s*)*Signed-off-by:.*", "").trim();
    }

    private String formatCommitDisplay(String message, Instant commitTime) {
        if (commitTime == null) {
            return "Recent Update: " + message;
        }
        ZonedDateTime localTime = commitTime.atZone(ZoneId.systemDefault());
        String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(localTime);
        return "Recent Update (" + formattedTime + "): " + message;
    }
}