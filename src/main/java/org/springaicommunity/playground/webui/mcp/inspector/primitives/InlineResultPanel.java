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
package org.springaicommunity.playground.webui.mcp.inspector.primitives;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.springaicommunity.playground.webui.mcp.inspector.InspectorHelpers;

import java.util.ArrayList;
import java.util.List;

public class InlineResultPanel extends Div {

    private Runnable onDismiss;

    public InlineResultPanel() {
        setVisible(false);
        getStyle()
                .set("margin-top", "0.8em")
                .set("padding-top", "0.6em")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
    }

    public void setOnDismiss(Runnable onDismiss) {
        this.onDismiss = onDismiss;
    }

    public void dismiss() {
        removeAll();
        setVisible(false);
        if (onDismiss != null) onDismiss.run();
    }

    public Render render() {
        return new Render();
    }

    public void renderOk(String name, long elapsedMs, List<Component> bodySections) {
        render().commitOk(name, elapsedMs, bodySections);
    }

    public void renderError(String name, long elapsedMs, String errorMessage) {
        render().commitError(name, elapsedMs, errorMessage);
    }

    public void renderEmptyResponse(String name, long elapsedMs, String emptyText) {
        render().commitEmptyResponse(name, elapsedMs, emptyText);
    }

    public class Render {
        private Button extraHeaderButton;
        private final List<Component> preResponseSections = new ArrayList<>();
        private String responseLabel = "Response";
        private String emptyBadgeLabel = "NO RESULT";
        private boolean omitResponseLabelOnEmpty = false;

        private Render() {}

        public Render extraHeaderButton(Button button) {
            this.extraHeaderButton = button;
            return this;
        }

        public Render addPreResponseSection(Component section) {
            this.preResponseSections.add(section);
            return this;
        }

        public Render responseLabel(String label) {
            this.responseLabel = label;
            return this;
        }

        public Render emptyBadgeLabel(String label) {
            this.emptyBadgeLabel = label;
            return this;
        }

        public Render omitResponseLabelOnEmpty() {
            this.omitResponseLabelOnEmpty = true;
            return this;
        }

        public void commitOk(String name, long elapsedMs, List<Component> bodySections) {
            prepareHeader("OK", name, elapsedMs, false);
            addPreResponseSections();
            add(InspectorHelpers.simpleSectionLabel(responseLabel));
            for (int i = 0; i < bodySections.size(); i++) {
                Component block = bodySections.get(i);
                if (i > 0 && block instanceof HasStyle hs) {
                    hs.getStyle().set("margin-top", "0.4em");
                }
                add(block);
            }
        }

        public void commitEmptyResponse(String name, long elapsedMs, String emptyText) {
            prepareHeader(emptyBadgeLabel, name, elapsedMs, false);
            addPreResponseSections();
            if (!omitResponseLabelOnEmpty) {
                add(InspectorHelpers.simpleSectionLabel(responseLabel));
            }
            Span empty = new Span(emptyText);
            empty.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-family", "var(--lumo-font-family-monospace)")
                    .set("font-size", "0.85em");
            add(empty);
        }

        public void commitError(String name, long elapsedMs, String errorMessage) {
            prepareHeader("ERROR", name, elapsedMs, true);
            addPreResponseSections();
            add(InspectorHelpers.simpleSectionLabel("Error"));
            add(InspectorHelpers.codeBlock(errorMessage, true));
        }

        private void prepareHeader(String label, String name, long elapsedMs, boolean isError) {
            removeAll();
            setVisible(true);
            HorizontalLayout statusRow = InspectorHelpers.simpleStatusHeader(label, name, elapsedMs, isError);
            if (extraHeaderButton != null) {
                extraHeaderButton.getStyle().set("margin-left", "0.4em");
                statusRow.add(extraHeaderButton);
            }
            statusRow.add(buildDismissButton());
            add(statusRow);
        }

        private void addPreResponseSections() {
            for (Component section : preResponseSections) {
                add(section);
            }
        }

        private Button buildDismissButton() {
            Button close = new Button(VaadinIcon.CLOSE_SMALL.create());
            close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                    ButtonVariant.LUMO_ICON);
            close.setTooltipText("Dismiss this result");
            close.addClickListener(e -> dismiss());
            return close;
        }
    }
}
