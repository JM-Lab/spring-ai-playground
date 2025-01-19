package jm.kr.spring.ai.playground.webui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.Icon;

import java.util.Objects;

public interface VaadinUtils {

    static UI getUi(Component component) {
        return component.getUI().orElseGet(UI::getCurrent);
    }

    static Icon styledIcon(Icon icon) {
        icon.getStyle().set("width", "var(--lumo-icon-size-m)");
        icon.getStyle().set("height", "var(--lumo-icon-size-m)");
        return icon;
    }

    static Button styledButton(String toolTip, Icon icon, ComponentEventListener<ClickEvent<Button>> clickListener) {
        Button styledButton = Objects.isNull(icon) ? new Button() : new Button(styledIcon(icon));
        if (Objects.nonNull(toolTip))
            styledButton.setTooltipText(toolTip);
        if (Objects.nonNull(clickListener))
            styledButton.addClickListener(clickListener);
        styledButton.getStyle().setPadding("0").setMargin("0");
        styledButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return styledButton;
    }
}