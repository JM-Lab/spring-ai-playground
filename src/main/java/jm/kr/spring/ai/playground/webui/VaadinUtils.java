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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.popover.Popover;

import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

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

    static Popover headerPopover(Component target, String headerText) {
        Popover popover = new Popover();
        popover.setTarget(target);
        popover.add(buildHeaderHorizontalLayout(headerText, e -> popover.close()));
        return popover;
    }

    static Dialog headerDialog(String headerText) {
        Dialog dialog = new Dialog();
        dialog.getHeader().add(buildHeaderHorizontalLayout(headerText, e -> dialog.close()));
        return dialog;
    }

    static HorizontalLayout buildHeaderHorizontalLayout(String headerText,
            ComponentEventListener<ClickEvent<Button>> clickListener) {
        H4 heading = new H4(headerText);
        heading.setWidthFull();

        Button closeButton = new Button(styledIcon(VaadinIcon.CLOSE.create()), clickListener);
        closeButton.setTooltipText("Cancel & Close");
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout headerLayout = new HorizontalLayout(heading, closeButton);
        headerLayout.setWidthFull();
        headerLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headerLayout.getStyle().set("padding", "0 var(--lumo-space-m)");
        return headerLayout;
    }

    static void showErrorNotification(String errorMessage) {
        Notification notification = new Notification(errorMessage, 3000, Notification.Position.TOP_CENTER);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        notification.open();
    }

    static void showInfoNotification(String message) {
        Notification notification = new Notification(message, 3000, Notification.Position.TOP_CENTER);
        notification.open();
    }

    static CompletableFuture<ZoneId> buildClientZoneIdFuture(CompletableFuture<ZoneId> zoneIdFuture) {
        UI.getCurrent().getPage().retrieveExtendedClientDetails(details -> zoneIdFuture.complete(
                ZoneId.of(details.getTimeZoneId())));
        return zoneIdFuture;

    }

}