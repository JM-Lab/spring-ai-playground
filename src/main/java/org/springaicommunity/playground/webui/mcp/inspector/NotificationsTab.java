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
package org.springaicommunity.playground.webui.mcp.inspector;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpNotificationStore;
import org.springaicommunity.playground.webui.VaadinUtils;

import java.util.List;

public class NotificationsTab extends VerticalLayout {

    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final VerticalLayout notificationsListContainer = new VerticalLayout();
    private Runnable notificationsUnsubscribe;

    public NotificationsTab(McpServerInfo serverInfo, McpClientService clientService) {
        this.serverInfo = serverInfo;
        this.clientService = clientService;
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        add(buildNotificationsToolbar());
        notificationsListContainer.setPadding(false);
        notificationsListContainer.setSpacing(false);
        notificationsListContainer.setWidthFull();
        notificationsListContainer.getStyle().set("gap", "0.4em");
        add(notificationsListContainer);
        renderNotifications();
    }

    public void attachListeners(UI ui) {
        if (ui == null) return;
        notificationsUnsubscribe = clientService.subscribeNotifications(serverInfo, evt -> {
            try { ui.access(this::renderNotifications); } catch (RuntimeException ignore) {}
        });
    }

    public void detach() {
        try {
            if (notificationsUnsubscribe != null) notificationsUnsubscribe.run();
        } catch (RuntimeException ignore) {}
        notificationsUnsubscribe = null;
    }

    private Component buildNotificationsToolbar() {
        ComboBox<String> levelCombo = new ComboBox<>();
        levelCombo.setItems("DEBUG", "INFO", "NOTICE", "WARNING", "ERROR", "CRITICAL", "ALERT", "EMERGENCY");
        levelCombo.setPlaceholder("Set logging level…");
        levelCombo.setWidth("220px");
        levelCombo.addValueChangeListener(e -> {
            String v = e.getValue();
            if (v == null || v.isBlank()) return;
            try {
                clientService.setLoggingLevel(serverInfo, McpSchema.LoggingLevel.valueOf(v));
                VaadinUtils.showInfoNotification("Logging level set to " + v);
            } catch (Exception ex) {
                VaadinUtils.showErrorNotification("Failed: " + ex.getMessage());
            }
        });

        Button clear = new Button("Clear", VaadinIcon.TRASH.create());
        clear.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        clear.addClickListener(e -> {
            clientService.clearEvents(serverInfo);
            renderNotifications();
        });

        HorizontalLayout bar = new HorizontalLayout(levelCombo, clear);
        bar.setWidthFull();
        bar.setPadding(false);
        bar.setSpacing(true);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.getStyle().set("gap", "0.6em").set("padding", "0.2em 0 0.6em");
        return bar;
    }

    private void renderNotifications() {
        notificationsListContainer.removeAll();
        List<McpNotificationStore.Event> events = clientService.snapshotEvents(serverInfo);
        if (events.isEmpty()) {
            notificationsListContainer.add(InspectorHelpers.emptyState(
                    "No notifications received yet. Server-side log messages, progress, and list-changed events appear here."));
            return;
        }
        for (var ev : events) notificationsListContainer.add(buildNotificationCard(ev));
    }

    private static Component buildNotificationCard(McpNotificationStore.Event ev) {
        Span kind = new Span(ev.kind().name());
        String color;
        switch (ev.kind()) {
            case LOGGING -> color = "var(--lumo-primary-color)";
            case PROGRESS -> color = "var(--lumo-success-color)";
            case TOOLS_CHANGED, RESOURCES_CHANGED, PROMPTS_CHANGED -> color = "var(--lumo-warning-text-color)";
            case SAMPLING_REQUEST, ELICITATION_REQUEST -> color = "var(--lumo-error-color)";
            default -> color = "var(--lumo-contrast-50pct)";
        }
        kind.getStyle()
                .set("font-size", "0.7em")
                .set("font-weight", "600")
                .set("letter-spacing", "0.05em")
                .set("padding", "0.15em 0.6em")
                .set("border-radius", "999px")
                .set("border", "1px solid " + color)
                .set("color", color)
                .set("white-space", "nowrap");

        Span ts = new Span(ev.timestamp().format(InspectorHelpers.TIME_FMT));
        ts.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-left", "auto");

        Span summary = new Span(ev.summary() == null ? "" : ev.summary());
        summary.getStyle().set("font-size", "0.9em").set("word-break", "break-word");

        Div headerRow = new Div(kind, summary, ts);
        headerRow.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "0.6em")
                .set("flex-wrap", "wrap");

        Div card = new Div(headerRow);
        card.setWidthFull();
        card.getStyle()
                .set("box-sizing", "border-box")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("padding", "0.5em 0.8em")
                .set("background-color", "var(--lumo-base-color)");
        return card;
    }
}
