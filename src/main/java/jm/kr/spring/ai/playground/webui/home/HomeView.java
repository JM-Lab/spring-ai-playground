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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayoutVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jm.kr.spring.ai.playground.webui.SpringAiPlaygroundAppLayout;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import jm.kr.spring.ai.playground.webui.home.HomeItemView.HomeItem;

import java.util.Objects;

import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledButton;
import static jm.kr.spring.ai.playground.webui.VaadinUtils.styledIcon;

@PageTitle("Home | Spring AI Playground")
@Route(value = "", layout = SpringAiPlaygroundAppLayout.class)
public class HomeView extends Div {

    private final SplitLayout splitLayout;
    private final VerticalLayout homeContentLayout;
    private final HomeItemView homeItemView;
    private HomeContentView homeContentView;
    private double splitterPosition;
    private boolean sidebarCollapsed;

    public HomeView() {
        this.homeItemView = new HomeItemView(this::selectHomeItem);
        this.homeContentView = new HomeContentView();

        setHeightFull();
        setSizeFull();

        this.splitLayout = new SplitLayout();
        this.splitLayout.setSizeFull();
        this.splitLayout.setSplitterPosition(this.splitterPosition = 15);
        this.splitLayout.addThemeVariants(SplitLayoutVariant.LUMO_SMALL);
        add(this.splitLayout);

        this.splitLayout.addToPrimary(this.homeItemView);
        this.homeContentLayout = new VerticalLayout();
        this.homeContentLayout.setSpacing(false);
        this.homeContentLayout.setMargin(false);
        this.homeContentLayout.setPadding(false);
        this.homeContentLayout.setHeightFull();
        this.homeContentLayout.getStyle().set("overflow", "hidden").set("display", "flex")
                .set("flex-direction", "column").set("align-items", "stretch");
        this.splitLayout.addToSecondary(this.homeContentLayout);
        this.sidebarCollapsed = false;
        selectHomeItem(homeItemView.buildDefaultHomeItem());
    }

    private HorizontalLayout createChatContentHeader(String displayName) {
        HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSpacing(false);
        horizontalLayout.setMargin(false);
        horizontalLayout.getStyle().setPadding("var(--lumo-space-m) 0 0 0");
        horizontalLayout.setWidthFull();
        horizontalLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button toggleButton = styledButton("Hide Home Contents", VaadinIcon.CHEVRON_LEFT.create(), null);
        Component leftArrowIcon = toggleButton.getIcon();
        Icon rightArrowIcon = styledIcon(VaadinIcon.CHEVRON_RIGHT.create());
        rightArrowIcon.setTooltipText("Show Home Contents");
        toggleButton.addClickListener(event -> {
            sidebarCollapsed = !sidebarCollapsed;
            toggleButton.setIcon(sidebarCollapsed ? rightArrowIcon : leftArrowIcon);
            if (sidebarCollapsed)
                this.homeItemView.removeFromParent();
            else
                this.splitLayout.addToPrimary(this.homeItemView);
            if (this.splitLayout.getSplitterPosition() > 0)
                this.splitterPosition = this.splitLayout.getSplitterPosition();
            this.splitLayout.setSplitterPosition(sidebarCollapsed ? 0 : splitterPosition);
        });
        horizontalLayout.add(toggleButton);

        H4 homeInfoText = new H4(displayName);
        homeInfoText.getStyle().set("white-space", "nowrap");
        Div homeInfoTextDiv = new Div(homeInfoText);
        homeInfoTextDiv.getStyle().set("display", "flex").set("justify-content", "center")
                .set("align-items", "center").set("height", "100%");

        HorizontalLayout homeInfoLabelLayout = new HorizontalLayout(homeInfoTextDiv);
        homeInfoLabelLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);
        homeInfoLabelLayout.setWidthFull();
        horizontalLayout.add(homeInfoLabelLayout);


        MenuBar mcpServerConnectionMenuBar = new MenuBar();
        mcpServerConnectionMenuBar.addThemeVariants(MenuBarVariant.LUMO_END_ALIGNED);
        mcpServerConnectionMenuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE);
        mcpServerConnectionMenuBar.getStyle().set("visibility", "hidden");

        horizontalLayout.add(mcpServerConnectionMenuBar);
        return horizontalLayout;
    }

    private void selectHomeItem(HomeItem homeItem) {
        if (Objects.isNull(homeItem))
            return;

        if (homeItem.actionType().equals(HomeItemView.ActionType.EXTERNAL_URL)) {
            this.homeContentView.setContent(homeItem);
            return;
        }

        this.homeContentView = new HomeContentView();
        homeContentView.setContent(homeItem);

        VaadinUtils.getUi(this).access(() -> {
            this.homeContentLayout.removeAll();
            this.homeContentLayout.add(createChatContentHeader(homeItem.displayName()), this.homeContentView);
        });
    }
}