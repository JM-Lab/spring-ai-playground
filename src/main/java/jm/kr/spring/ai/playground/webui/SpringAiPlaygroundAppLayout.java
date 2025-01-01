package jm.kr.spring.ai.playground.webui;

import com.vaadin.flow.component.HasComponents;
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

@PageTitle("Spring AI Playground")
public class SpringAiPlaygroundAppLayout extends AppLayout {

    private Tabs tabs = new Tabs();

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
        addToNavbar(titleLayout, tabs);
        addTab(ChatView.class);
    }

    private void addTab(Class<? extends HasComponents> clazz) {
        Tab tab = new Tab(getViewName(clazz));
        tabs.add(tab);
    }

    public String getViewName(Class clazz) {
        String lowerCase = clazz.getSimpleName().toLowerCase().replace("view", "");
        return lowerCase.substring(0, 1).toUpperCase() + lowerCase.substring(1);
    }

}
