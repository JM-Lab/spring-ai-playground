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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.JsonSchemaType;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.ToolParamSpec;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ToolParameterForm extends VerticalLayout {

    private static final List<JsonSchemaType> EDITABLE_TYPES =
            Arrays.stream(JsonSchemaType.values()).filter(type -> type != JsonSchemaType.OBJECT)
                    .collect(Collectors.toList());

    private final TextField nameField;
    private final ComboBox<JsonSchemaType> typeField;
    private final Checkbox requiredField;
    private final TextArea descriptionField;
    private final TextField testValueField;

    private final Button addButton;
    private final Button deleteButton;
    private final H5 headerTitle;

    private final Consumer<ToolParameterForm> deleteListener;
    private final Runnable addParameterListener;

    public ToolParameterForm(ToolParamSpec initialSpec, Consumer<ToolParameterForm> deleteListener,
            Runnable addParameterListener, int index) {
        this.deleteListener = deleteListener;
        this.addParameterListener = addParameterListener;

        requiredField = new Checkbox("Required");
        requiredField.setValue(false);

        nameField = new TextField("Name");
        nameField.setPlaceholder("e.g., location");
        nameField.setRequired(true);
        nameField.setPattern("^[a-zA-Z0-9_]{1,64}$");
        nameField.setErrorMessage("Use alphanumeric characters and underscores.");
        nameField.setWidthFull();

        typeField = new ComboBox<>("Type");
        typeField.setItems(EDITABLE_TYPES);
        typeField.setPlaceholder("Select type");
        typeField.setRequired(true);
        typeField.setWidthFull();
        typeField.setValue(JsonSchemaType.STRING);

        descriptionField = new TextArea("Description");
        descriptionField.setPlaceholder("e.g., The city and state, like 'San Francisco, CA'");
        descriptionField.setRequired(false);
        descriptionField.setHeight("80px");
        descriptionField.setWidthFull();

        testValueField = new TextField("Test Value");
        testValueField.setPlaceholder("Enter a value matching the type");
        testValueField.setRequired(true);
        testValueField.setWidthFull();

        deleteButton = new Button(VaadinIcon.TRASH.create(), e -> this.deleteListener.accept(this));
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY_INLINE);
        deleteButton.setTooltipText("Remove Parameter");

        addButton = new Button(VaadinIcon.PLUS.create(), e -> this.addParameterListener.run());
        addButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        addButton.setTooltipText("Add Parameter");
        addButton.setVisible(false);

        HorizontalLayout buttonGroup = new HorizontalLayout(deleteButton, addButton);
        buttonGroup.setSpacing(false);
        buttonGroup.setPadding(false);
        buttonGroup.setMargin(false);

        headerTitle = new H5("Parameter #" + index);
        HorizontalLayout headerLayout = new HorizontalLayout(headerTitle, buttonGroup);
        headerLayout.setWidthFull();
        headerLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        headerLayout.setAlignItems(FlexComponent.Alignment.BASELINE);

        add(headerLayout, requiredField, nameField, typeField, descriptionField, testValueField);

        setSpacing(false);
        getStyle().set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("padding", "var(--lumo-space-m)");

        typeField.addValueChangeListener(event -> updateTestValueValidation(event.getValue()));

        if (initialSpec != null) {
            nameField.setValue(initialSpec.name() != null ? initialSpec.name() : "");
            typeField.setValue(initialSpec.type());
            requiredField.setValue(initialSpec.required());
            descriptionField.setValue(initialSpec.description() != null ? initialSpec.description() : "");
            testValueField.setValue(initialSpec.testValue() != null ? initialSpec.testValue().toString() : "");
            updateTestValueValidation(initialSpec.type());
        }
    }

    public void setDeleteButtonVisible(boolean visible) {
        deleteButton.setVisible(visible);
    }

    public void updateIndex(int newIndex) {
        headerTitle.setText("Parameter #" + newIndex);
    }

    public void setAddButtonVisible(boolean visible) {
        addButton.setVisible(visible);
    }

    private void updateTestValueValidation(JsonSchemaType type) {
        testValueField.setErrorMessage(null);
        testValueField.setInvalid(false);

        if (type == null) {
            return;
        }

        switch (type) {
            case NUMBER:
                testValueField.setPattern("^-?[0-9]*\\.?[0-9]+$");
                testValueField.setErrorMessage("Must be a number (e.g., 123.45).");
                testValueField.setPlaceholder("Enter a number (e.g., 123.45)");
                break;
            case INTEGER:
                testValueField.setPattern("^-?[0-9]+$");
                testValueField.setErrorMessage("Must be an integer (e.g., 123).");
                testValueField.setPlaceholder("Enter an integer (e.g., 123)");
                break;
            case BOOLEAN:
                testValueField.setPattern("^(true|false)$");
                testValueField.setErrorMessage("Must be 'true' or 'false'.");
                testValueField.setPlaceholder("Enter 'true' or 'false'");
                break;
            case ARRAY:
                testValueField.setPattern("^\\s*(\\[.*\\])?\\s*$");
                testValueField.setErrorMessage(
                        "Must be a valid JSON array format (e.g., [1, \"text\"]).");
                testValueField.setPlaceholder("Enter a JSON array (e.g., [1, \"text\"])");
                break;
            case STRING:
                testValueField.setPattern(null);
                testValueField.setErrorMessage(null);
                testValueField.setPlaceholder("Enter a string value (e.g., San Francisco)");
            default:
                break;
        }
    }

    public ToolParamSpec getToolParamSpec() {
        return !hasValidationErrors() ? new ToolParamSpec(nameField.getValue(), descriptionField.getValue(),
                requiredField.getValue(), typeField.getValue(), testValueField.getValue()) : null;
    }

    private boolean hasValidationErrors() {
        if (nameField.isEmpty() || typeField.isEmpty() || testValueField.isEmpty())
            return true;
        if (nameField.isInvalid() || typeField.isInvalid() || testValueField.isInvalid())
            return true;
        updateTestValueValidation(this.typeField.getValue());
        return testValueField.isInvalid();
    }

    public void updateFields(ToolParamSpec paramSpec) {
        this.requiredField.setValue(paramSpec.required());
        this.nameField.setValue(paramSpec.name());
        this.typeField.setValue(paramSpec.type());
        this.descriptionField.setValue(paramSpec.description());
        this.testValueField.setValue(paramSpec.testValue());
    }
}