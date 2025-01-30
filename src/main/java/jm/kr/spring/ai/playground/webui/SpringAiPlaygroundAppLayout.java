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

        this.tabContents = Map.of("Chat Models", ChatView.class, "Vector Databases", VectorStoreView.class);
        tabs.add(new Tab("Chat Models"));
        tabs.add(new Tab("Vector Databases"));
        tabs.addSelectedChangeListener(
                event -> UI.getCurrent().navigate(tabContents.get(event.getSelectedTab().getLabel())));

    }

}
