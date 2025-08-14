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

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;

public class HomeMarkdownView extends VerticalLayout {

    private final ResourceLoader resourceLoader;
    private final String location;
    private final Markdown markdown;

    public HomeMarkdownView(String resourceLocation, String title) {
        this.location = resourceLocation;
        this.resourceLoader = new DefaultResourceLoader();

        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden");

        VerticalLayout content = new VerticalLayout();
        content.setSizeFull();
        content.setPadding(false);
        content.setSpacing(true);
        content.getStyle()
                .set("padding-left", "2rem")
                .set("padding-right", "2rem");

        Anchor link = new Anchor(location, location);
        link.setTarget("_blank");

        Div status = new Div();
        status.add(new Text("Source: " + title + ", "), link);

        markdown = new Markdown("");
        markdown.setWidthFull();

        content.add(status, markdown);
        content.setFlexGrow(1, markdown);

        Scroller scroller = new Scroller(content);
        scroller.setSizeFull();
        add(scroller);

        loadMarkdown();
    }

    private void loadMarkdown() {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                VaadinUtils.showErrorNotification("Resource not found");
                return;
            }
            try (var in = resource.getInputStream()) {
                String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                markdown.setContent(md);

                getUI().ifPresent(ui ->
                        ui.getPage().executeJs(
                                """
                                const target = $0;
                                const obs = new MutationObserver(() => {
                                    const links = target.querySelectorAll('a[href]');
                                    if (links.length > 0) {
                                        links.forEach(a => {
                                            a.setAttribute('target', '_blank');
                                            a.setAttribute('rel', 'noopener noreferrer');
                                        });
                                        obs.disconnect();
                                    }
                                });
                                obs.observe(target, { childList: true, subtree: true });
                                """,
                                markdown.getElement()
                        )
                );
            }
        } catch (Exception e) {
            VaadinUtils.showErrorNotification("Error loading resource: " + e.getMessage());
        }
    }
}