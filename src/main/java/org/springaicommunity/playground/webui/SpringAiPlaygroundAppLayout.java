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
package org.springaicommunity.playground.webui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springaicommunity.playground.webui.chat.ChatView;
import org.springaicommunity.playground.webui.home.HomeView;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springaicommunity.playground.webui.observability.ObservabilityView;
import org.springaicommunity.playground.webui.tool.ToolStudioView;
import org.springaicommunity.playground.webui.vectorstore.VectorStoreView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@PageTitle("Spring AI Playground")
@CssImport("./playground/sidebar.css")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-text-field")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-text-area")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-password-field")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-combo-box")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-multi-select-combo-box")
@CssImport(value = "./playground/input-placeholder.css", themeFor = "vaadin-select")
@CssImport(value = "./playground/disabled-placeholder.css", themeFor = "vaadin-multi-select-combo-box")
// The parent layout must allow access at least as broadly as its views, or navigation is denied.
@AnonymousAllowed
public class SpringAiPlaygroundAppLayout extends AppLayout
        implements BeforeEnterObserver {

    private final Tabs tabs;
    private final Map<Tab, Class<? extends Component>> tabToView = new LinkedHashMap<>();
    private final Map<Class<? extends Component>, Tab> viewToTab = new HashMap<>();

    public SpringAiPlaygroundAppLayout() {
        HorizontalLayout titleLayout = new HorizontalLayout();
        titleLayout.setPadding(true);
        titleLayout.setSpacing(true);
        titleLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        Image springImg = new Image("icons/icon.png", "Spring AI Playground");
        springImg.getStyle().set("width", "var(--lumo-icon-size-l)").set("height", "var(--lumo-icon-size-l)");
        Div springImgDiv = new Div(springImg);
        springImgDiv.getStyle().set("display", "flex").set("justify-content", "center").set("align-items", "center");
        H3 title = new H3("Spring AI Playground");
        title.getStyle().set("margin", "0").set("white-space", "nowrap");
        titleLayout.add(springImgDiv, title);
        addToNavbar(titleLayout);

        this.tabs = new Tabs();
        createTab("Home", VaadinIcon.HOME, HomeView.class);
        createTab("Tool Studio", VaadinIcon.TOOLS, ToolStudioView.class);
        createTab("MCP Server", VaadinIcon.TOOLBOX, McpServerView.class);
        createTab("Vector Database", VaadinIcon.SEARCH_PLUS, VectorStoreView.class);
        createTab("Agentic Chat", VaadinIcon.CHAT, ChatView.class);
        Tab observabilityTab = createTab("Observability", VaadinIcon.DASHBOARD, ObservabilityView.class);
        // The observability tab is a "monitoring" surface, not a "tool" — pin it
        // to the right of the navbar with a divider so users see it as a separate
        // class of feature.
        observabilityTab.addClassName("observability-tab");
        this.tabs.setWidthFull();
        addToNavbar(tabs);

        this.tabs.addSelectedChangeListener(event -> {
            Class<? extends Component> targetView = this.tabToView.get(event.getSelectedTab());
            if (targetView != null) {
                UI.getCurrent().navigate(targetView);
            }
        });
    }

    private Tab createTab(String label, VaadinIcon icon, Class<? extends Component> viewClass) {
        Tab tab = new Tab(icon.create(), new Span(label));
        this.tabs.add(tab);
        this.tabToView.put(tab, viewClass);
        this.viewToTab.put(viewClass, tab);
        return tab;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Class<?> navigationTarget = event.getNavigationTarget();
        Tab tab = this.viewToTab.get(navigationTarget);
        if (tab != null) {
            this.tabs.setSelectedTab(tab);
        } else {
            this.tabs.setSelectedTab(this.viewToTab.get(HomeView.class));
        }
    }
}
