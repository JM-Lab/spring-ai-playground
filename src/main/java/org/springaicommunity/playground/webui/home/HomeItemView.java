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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.webui.common.WorkspaceSidebar;

public class HomeItemView extends WorkspaceSidebar {

    public HomeItemView() {
        super("Links");

        VerticalLayout container = new VerticalLayout();
        container.setSpacing(false);
        container.setPadding(false);
        container.getStyle().set("padding", "0.5rem 0");

        container.add(sectionHeader("Project"));
        container.add(
                link(VaadinIcon.FILE_TEXT_O, "Documentation",
                        "https://spring-ai-community.github.io/spring-ai-playground/"),
                link(VaadinIcon.CODE, "Repository",
                        "https://github.com/spring-ai-community/spring-ai-playground"),
                link(VaadinIcon.USERS, "Spring AI Community",
                        "https://springaicommunity.mintlify.app/projects/incubating/spring-ai-playground"),
                link(VaadinIcon.COMMENT_ELLIPSIS, "Discussions",
                        "https://github.com/spring-ai-community/spring-ai-playground/discussions"),
                link(VaadinIcon.BUG, "Report a Bug",
                        "https://github.com/spring-ai-community/spring-ai-playground/issues/new?template=bug_report.yml")
        );

        container.add(sectionHeader("Learn"));
        container.add(
                link(VaadinIcon.SHIELD, "AI Agent Tool Safety",
                        "https://spring-ai-community.github.io/spring-ai-playground/safety-architecture/"),
                link(VaadinIcon.ACADEMY_CAP, "Tutorials",
                        "https://spring-ai-community.github.io/spring-ai-playground/tutorials/"),
                link(VaadinIcon.TOOLBOX, "Default Tools Catalog",
                        "https://spring-ai-community.github.io/spring-ai-playground/features/default-tools/"),
                link(VaadinIcon.LINE_BAR_CHART, "Telemetry & Opt-out",
                        "https://spring-ai-community.github.io/spring-ai-playground/getting-started/configuration/#telemetry")
        );

        container.add(sectionHeader("Related"));
        container.add(
                link(VaadinIcon.BOOK, "Spring AI Docs",
                        "https://docs.spring.io/spring-ai/reference/index.html"),
                link(VaadinIcon.CODE, "Spring AI Repository",
                        "https://github.com/spring-projects/spring-ai"),
                link(VaadinIcon.STAR_O, "Awesome Spring AI",
                        "https://github.com/spring-ai-community/awesome-spring-ai"),
                link(VaadinIcon.STAR_O, "Awesome MCP Servers",
                        "https://github.com/punkpeye/awesome-mcp-servers")
        );

        container.add(sectionHeader("Videos"));
        container.add(link(VaadinIcon.FILM, "It Asks Before It Deletes (1 minute)",
                "https://youtu.be/9t9DELt2bRM"));
        container.add(link(VaadinIcon.FILM, "Trailer (30 seconds)",
                "https://youtu.be/dR6XRs2-nEY"));
        Div episodes = new Div(
                episodeLink("What is Spring AI Playground?", "pOgsT-SOri4"),
                episodeLink("Plan a Meeting: Map, Calendar, Email", "WiuBG9PxqwY"),
                episodeLink("Map a Week of Live Earthquakes", "A7Uv8MKyar0"),
                episodeLink("Spreadsheet In, 5 Charts Out", "J7zApmncelU"),
                episodeLink("An Agent That Finds Its Own Tools", "FIXmWACBl6I"),
                episodeLink("Teach a New Tool in 60 Seconds", "c1rxulSC9bw"),
                episodeLink("It Asks Before It Deletes", "9t9DELt2bRM"),
                episodeLink("See Everything Your Agent Did", "HVoM-tse_q8"));
        episodes.getStyle().set("display", "flex").set("flex-direction", "column");
        episodes.setVisible(false);
        container.add(seriesRow(episodes), episodes);

        setSidebarContent(verticalScroller(container));
    }

    private static Div seriesRow(Div episodes) {
        Anchor series = link(VaadinIcon.YOUTUBE, "Local Model, Real Agent (series)",
                "https://www.youtube.com/playlist?list=PLfizCrbCZK9k");
        series.getStyle().set("flex", "1 1 auto").set("min-width", "0");

        Button toggle = new Button(VaadinIcon.CHEVRON_DOWN.create(), e -> {
            boolean show = !episodes.isVisible();
            episodes.setVisible(show);
            e.getSource().setIcon(show
                    ? VaadinIcon.CHEVRON_UP.create()
                    : VaadinIcon.CHEVRON_DOWN.create());
            e.getSource().setAriaLabel(show ? "Hide episodes" : "Show episodes");
        });
        toggle.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON,
                ButtonVariant.LUMO_SMALL);
        toggle.setAriaLabel("Show episodes");
        toggle.getStyle().set("flex-shrink", "0").set("margin-right", "0.5rem");

        Div row = new Div(series, toggle);
        row.getStyle().set("display", "flex").set("align-items", "center");
        return row;
    }

    private static Anchor episodeLink(String label, String videoId) {
        Anchor anchor = link(VaadinIcon.PLAY_CIRCLE_O, label,
                "https://www.youtube.com/watch?v=" + videoId + "&list=PLfizCrbCZK9k");
        anchor.getStyle().set("padding-left", "1.9rem");
        return anchor;
    }

    private static Span sectionHeader(String text) {
        Span span = new Span(text);
        span.getStyle()
                .set("display", "block")
                .set("padding", "0.75rem 1rem 0.25rem")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("color", "var(--lumo-secondary-text-color)");
        return span;
    }

    private static Anchor link(VaadinIcon icon, String label, String url) {
        Icon iconInstance = icon.create();
        iconInstance.getStyle()
                .set("width", "var(--lumo-icon-size-s)")
                .set("height", "var(--lumo-icon-size-s)")
                .set("flex-shrink", "0")
                .set("color", "var(--lumo-secondary-text-color)");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("line-height", "1.4")
                .set("white-space", "normal")
                .set("word-break", "break-word");

        Div content = new Div(iconInstance, labelSpan);
        content.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "0.6rem");

        Anchor anchor = new Anchor(url, "");
        anchor.removeAll();
        anchor.add(content);
        anchor.setTarget("_blank");
        anchor.getElement().setAttribute("rel", "noopener noreferrer");
        anchor.getStyle()
                .set("display", "block")
                .set("padding", "0.4rem 1rem")
                .set("color", "var(--lumo-body-text-color)")
                .set("text-decoration", "none")
                .set("transition", "background-color 0.1s");

        anchor.getElement().addEventListener("mouseenter",
                e -> anchor.getStyle().set("background-color", "var(--lumo-contrast-5pct)"));
        anchor.getElement().addEventListener("mouseleave",
                e -> anchor.getStyle().set("background-color", "transparent"));

        return anchor;
    }
}
