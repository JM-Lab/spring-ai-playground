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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.function.Consumer;

import static jm.kr.spring.ai.playground.webui.home.HomeItemView.ActionType.EXTERNAL_PAGE;
import static jm.kr.spring.ai.playground.webui.home.HomeItemView.ActionType.EXTERNAL_URL;
import static jm.kr.spring.ai.playground.webui.home.HomeItemView.ActionType.MARKDOWN;
import static jm.kr.spring.ai.playground.webui.home.HomeItemView.ActionType.UI_COMPONENT;

public class HomeItemView extends VerticalLayout {

    public record HomeItem(String displayName, Icon icon, ActionType actionType, Object actionData) {
        public Component createComponent() {
            icon.getStyle().set("width", "var(--lumo-icon-size-s)").set("height", "var(--lumo-icon-size-s)")
                    .set("padding", "0").set("margin", "0").set("box-sizing", "content-box").set("flex-shrink", "0");
            Span text = new Span(displayName);
            text.getStyle().set("line-height", "1.4").set("margin", "0").set("white-space", "normal")
                    .set("word-break", "break-word");
            Span container = new Span(icon, text);
            container.getStyle().set("display", "flex").set("align-items", "center").set("gap", "0.5rem");
            return container;
        }
    }

    public enum ActionType {
        UI_COMPONENT, MARKDOWN, EXTERNAL_URL, EXTERNAL_PAGE
    }

    public HomeItemView(Consumer<HomeItem> selectedHomeItemConsumer) {
        setSpacing(false);
        setMargin(false);
        getStyle().set("overflow", "hidden");

        List<HomeItem> homeItems = List.of(buildDefaultHomeItem(),
                new HomeItem("Spring AI Playground Document", VaadinIcon.FILE_TEXT_O.create(),
                        EXTERNAL_PAGE, "https://jm-lab.github.io/spring-ai-playground/"),
                new HomeItem("Spring AI Document", VaadinIcon.LINK.create(), EXTERNAL_URL,
                        "https://docs.spring.io/spring-ai/reference/index.html"),
                new HomeItem("Spring AI Project Repository", VaadinIcon.LINK.create(), EXTERNAL_URL,
                        "https://github.com/spring-projects/spring-ai"),
                new HomeItem("Awesome Spring AI", VaadinIcon.FILE_TEXT_O.create(), MARKDOWN,
                        "https://github.com/spring-ai-community/awesome-spring-ai/raw/refs/heads/main/README.md"),
                new HomeItem("Awesome MCP Servers", VaadinIcon.FILE_TEXT_O.create(), MARKDOWN,
                        "https://github.com/punkpeye/awesome-mcp-servers/raw/refs/heads/main/README.md")
        );

        ListBox<HomeItem> homeItemListBox = new ListBox<>();
        homeItemListBox.setItems(homeItems);


        homeItemListBox.setRenderer(new ComponentRenderer<>(HomeItem::createComponent));

        homeItemListBox.addValueChangeListener(event -> {
            selectedHomeItemConsumer.accept(event.getValue());
            homeItemListBox.clear();
        });

        VerticalLayout contentLayout = new VerticalLayout();
        contentLayout.setPadding(false);
        contentLayout.setSpacing(false);
        contentLayout.setWidthFull();
        contentLayout.add(homeItemListBox);

        Scroller scroller = new Scroller(contentLayout);
        scroller.setSizeFull();
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);

        removeAll();
        add(initHomeItemHeader(), scroller);
    }

    public HomeItem buildDefaultHomeItem() {
        return new HomeItem("Welcome", VaadinIcon.HOME.create(), UI_COMPONENT, new HomeInfoView());
    }

    private Header initHomeItemHeader() {
        Span appName = new Span("Home Contents");
        appName.addClassNames(LumoUtility.FontWeight.SEMIBOLD, LumoUtility.FontSize.LARGE);

        Header header = new Header(appName);
        header.getStyle().set("white-space", "nowrap").set("height", "auto").set("width", "100%").set("display", "flex")
                .set("box-sizing", "border-box").set("align-items", "center");
        return header;
    }
}