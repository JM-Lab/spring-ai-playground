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
package org.springaicommunity.playground.webui.mcp;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

public class ArgFormRow extends HorizontalLayout {

    private final TextField valueField = new TextField();
    private final Span indexLabel = new Span();
    private final Button addButton;
    private final Button deleteButton;

    public ArgFormRow(int index, Consumer<ArgFormRow> onRemove, Runnable onAdd, Runnable onValueChange) {
        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        indexLabel.setWidth("30px");
        indexLabel.getStyle().set("text-align", "right").set("font-weight", "500");
        updateIndex(index);

        valueField.setPlaceholder("e.g. --port=8080 or /path/to/file");
        valueField.setWidthFull();
        valueField.addValueChangeListener(e -> onValueChange.run());

        deleteButton = new Button(VaadinIcon.TRASH.create(), e -> onRemove.accept(this));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.setTooltipText("Remove this argument");

        addButton = new Button(VaadinIcon.PLUS.create(), e -> onAdd.run());
        addButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        addButton.setTooltipText("Add another argument");

        add(indexLabel, valueField, deleteButton, addButton);
    }

    public void updateIndex(int index) {
        indexLabel.setText(index + ".");
    }

    public String getValue() {
        return valueField.getValue();
    }

    public void setValue(String v) {
        valueField.setValue(v == null ? "" : v);
    }

    public void setAddButtonVisible(boolean visible) {
        addButton.setVisible(visible);
    }

    public void setDeleteButtonVisible(boolean visible) {
        deleteButton.setVisible(visible);
    }
}
