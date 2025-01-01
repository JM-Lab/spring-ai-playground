package jm.kr.spring.ai.playground.webui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;

public interface VaadinUtils {
    static UI getUi(Component component) {
        return component.getUI().orElseGet(UI::getCurrent);
    }
}