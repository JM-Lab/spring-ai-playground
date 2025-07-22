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
package jm.kr.spring.ai.playground.webui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import jm.kr.spring.ai.playground.webui.chat.ChatView;
import jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView;

import java.util.Map;

@PageTitle("Spring AI Playground")
public class SpringAiPlaygroundAppLayout extends AppLayout {

    private final Map<String, Class<? extends Component>> tabContents;

    public SpringAiPlaygroundAppLayout() {
        HorizontalLayout titleLayout = new HorizontalLayout();
        titleLayout.setPadding(true);
        titleLayout.setSpacing(true);
        titleLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        Image springImg = new Image("https://spring.io/img/projects/spring.svg", "Spring AI Playground");
        springImg.getStyle().set("width", "var(--lumo-icon-size-m)").set("height", "var(--lumo-icon-size-m)");
        Div springImgDiv = new Div(springImg);
        springImgDiv.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center");
        titleLayout.add(springImgDiv, new H3("Spring AI Playground"));
        Tabs tabs = new Tabs();
        addToNavbar(titleLayout, tabs);

        this.tabContents = Map.of("Vector Database", VectorStoreView.class, "Chat", ChatView.class);
        tabs.add(new Tab("Vector Database"));
        tabs.add(new Tab("Chat"));
        tabs.addSelectedChangeListener(
                event -> UI.getCurrent().navigate(tabContents.get(event.getSelectedTab().getLabel())));

    }

}
