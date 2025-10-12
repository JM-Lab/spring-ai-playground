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
package jm.kr.spring.ai.playground.webui.tool;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

import java.util.Map;
import java.util.function.Consumer;

public class StaticVariableForm extends HorizontalLayout {

    private final TextField keyField = new TextField();
    private final TextField valueField = new TextField();
    private final Button addButton = new Button(VaadinIcon.PLUS.create());
    private final Button deleteButton = new Button(VaadinIcon.TRASH.create());
    private final Span indexLabel = new Span();

    public StaticVariableForm(int index, Consumer<StaticVariableForm> onRemove, Runnable onAdd) {

        setWidthFull();
        setAlignItems(Alignment.CENTER);
        setSpacing(true);

        indexLabel.setWidth("30px");
        indexLabel.getStyle().set("text-align", "right").set("font-weight", "500");
        updateIndex(index);

        keyField.setPlaceholder("variableName");
        keyField.setWidth("200px");

        valueField.setPlaceholder("Enter value or ${ENV_VAR} to use environment variable");
        valueField.setWidthFull();

        addButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
        addButton.setTooltipText("Add new static variable");
        addButton.addClickListener(e -> onAdd.run());

        deleteButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
        deleteButton.setTooltipText("Remove this variable");
        deleteButton.addClickListener(e -> onRemove.accept(this));

        add(indexLabel, keyField, valueField, deleteButton, addButton);

        updateButtons(true, false);
    }

    public void updateIndex(int index) {
        indexLabel.setText(index + ".");
    }

    public void setAddButtonVisible(boolean visible) {
        addButton.setVisible(visible);
    }

    public void setDeleteButtonVisible(boolean visible) {
        deleteButton.setVisible(visible);
    }

    private void updateButtons(boolean addVisible, boolean deleteVisible) {
        addButton.setVisible(addVisible);
        deleteButton.setVisible(deleteVisible);
    }

    public String getKey() {
        return keyField.getValue().trim();
    }

    public String getValue() {
        return valueField.getValue();
    }

    public boolean isEmpty() {
        return keyField.isEmpty() && valueField.isEmpty();
    }

    public void update(Map.Entry<String, String> entry) {
        this.keyField.setValue(entry.getKey());
        this.valueField.setValue(entry.getValue());
    }
}