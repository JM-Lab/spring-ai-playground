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
package jm.kr.spring.ai.playground.webui.chat;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;
import java.util.Objects;

public class ChatModelSettingView extends VerticalLayout {
    private final TextArea systemPromptTextArea;
    private final ComboBox<String> modelComboBox;
    private final IntegerField maxTokensInput;
    private final NumberField temperatureInput;
    private final NumberField topPInput;
    private final IntegerField topKInput;
    private final NumberField frequencyPenaltyInput;
    private final NumberField presencePenaltyInput;

    public ChatModelSettingView(List<String> models, String systemPrompt, ChatOptions chatOption) {
        setSpacing(false);
        setAlignItems(FlexComponent.Alignment.START);
        getStyle().set("padding", "var(--lumo-space-m) var(--lumo-space-m) var(--lumo-space-xs)");

        String model = chatOption.getModel();
        modelComboBox = new ComboBox<>("Model");
        modelComboBox.setItems(models);
        modelComboBox.setValue(model);
        modelComboBox.setAllowCustomValue(true);
        add(modelComboBox);

        this.systemPromptTextArea = new TextArea("System Prompt");
        if (Objects.nonNull(systemPrompt))
            this.systemPromptTextArea.setValue(systemPrompt);
        this.systemPromptTextArea.setWidthFull();
        add(systemPromptTextArea);

        this.maxTokensInput = new IntegerField("Max Tokens");
        Integer maxTokens = chatOption.getMaxTokens();
        this.maxTokensInput.setMin(1);
        this.maxTokensInput.setValue(maxTokens);
        this.maxTokensInput.setI18n(new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Quantity must be at least 1"));
        add(maxTokensInput);

        this.temperatureInput = new NumberField("Temperature");
        Double temperature = chatOption.getTemperature();
        this.temperatureInput.setMin(0);
        this.temperatureInput.setMax(1);
        this.temperatureInput.setValue(temperature);
        this.temperatureInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Quantity must be at least 0")
                .setMaxErrorMessage("Value cannot exceed 1"));
        RangeInput temperatureSlider = new RangeInput();
        temperatureSlider.setStep(0.1);
        temperatureSlider.setMin(0);
        temperatureSlider.setMax(1);
        if (Objects.nonNull(temperature))
            temperatureSlider.setValue(temperature);
        temperatureSlider.setWidthFull();
        temperatureSlider.addValueChangeListener(e -> this.temperatureInput.setValue(e.getValue()));
        this.temperatureInput.addValueChangeListener(e -> temperatureSlider.setValue(e.getValue()));
        add(temperatureInput, temperatureSlider);

        this.topPInput = new NumberField("Top P");
        Double topP = chatOption.getTopP();
        this.topPInput.setMin(0);
        this.topPInput.setMax(1);
        if (Objects.nonNull(topP))
            this.topPInput.setValue(topP);
        this.topPInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least 0")
                .setMaxErrorMessage("Value cannot exceed 1"));
        RangeInput topPSlider = new RangeInput();
        topPSlider.setMin(0);
        topPSlider.setMax(1);
        if (Objects.nonNull(topP))
            topPSlider.setValue(topP);
        topPSlider.setWidthFull();
        topPSlider.addValueChangeListener(e -> this.topPInput.setValue(e.getValue()));
        this.topPInput.addValueChangeListener(e -> topPSlider.setValue(e.getValue()));
        add(topPInput, topPSlider);

        this.topKInput = new IntegerField("Top K");
        Integer topK = chatOption.getTopK();
        this.topKInput.setMin(1);
        this.topKInput.setMax(100); // Assuming the range is 0 to 100
        if (Objects.nonNull(topK))
            this.topKInput.setValue(topK);
        this.topKInput.setI18n(new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least 1")
                .setMaxErrorMessage("Value cannot exceed 100"));
        RangeInput topKSlider = new RangeInput();
        topKSlider.setStep(1.0);
        topKSlider.setMin(0);
        topKSlider.setMax(100);
        if (Objects.nonNull(topK))
            topKSlider.setValue(topK.doubleValue());
        topKSlider.setWidthFull();
        topKSlider.addValueChangeListener(e -> this.topKInput.setValue(e.getValue().intValue()));
        this.topKInput.addValueChangeListener(e -> topKSlider.setValue(e.getValue().doubleValue()));
        add(topKInput, topKSlider);

        this.frequencyPenaltyInput = new NumberField("Frequency Penalty");
        Double frequencyPenalty = chatOption.getFrequencyPenalty();
        this.frequencyPenaltyInput.setMin(-2);
        this.frequencyPenaltyInput.setMax(2);
        if (Objects.nonNull(frequencyPenalty))
            this.frequencyPenaltyInput.setValue(frequencyPenalty);
        this.frequencyPenaltyInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least -2")
                .setMaxErrorMessage("Value cannot exceed 2"));
        RangeInput frequencyPenaltySlider = new RangeInput();
        frequencyPenaltySlider.setStep(0.1);
        frequencyPenaltySlider.setMin(-2);
        frequencyPenaltySlider.setMax(2);
        if (Objects.nonNull(frequencyPenalty))
            frequencyPenaltySlider.setValue(frequencyPenalty);
        frequencyPenaltySlider.setWidthFull();
        frequencyPenaltySlider.addValueChangeListener(e -> this.frequencyPenaltyInput.setValue(e.getValue()));
        this.frequencyPenaltyInput.addValueChangeListener(e -> frequencyPenaltySlider.setValue(e.getValue()));
        add(frequencyPenaltyInput, frequencyPenaltySlider);

        this.presencePenaltyInput = new NumberField("Presence Penalty");
        Double presencePenalty = chatOption.getPresencePenalty();
        this.presencePenaltyInput.setMin(-2);
        this.presencePenaltyInput.setMax(2);
        if (Objects.nonNull(presencePenalty))
            this.presencePenaltyInput.setValue(presencePenalty);
        this.presencePenaltyInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least -2")
                .setMaxErrorMessage("Value cannot exceed 2"));
        RangeInput presencePenaltySlider = new RangeInput();
        presencePenaltySlider.setStep(0.1);
        presencePenaltySlider.setMin(-2);
        presencePenaltySlider.setMax(2);
        if (Objects.nonNull(presencePenalty))
            presencePenaltySlider.setValue(presencePenalty);
        presencePenaltySlider.setWidthFull();
        presencePenaltySlider.addValueChangeListener(e -> this.presencePenaltyInput.setValue(e.getValue()));
        this.presencePenaltyInput.addValueChangeListener(e -> presencePenaltySlider.setValue(e.getValue()));
        add(presencePenaltyInput, presencePenaltySlider);

    }

    public String getSystemPromptTextArea() {
        return this.systemPromptTextArea.getValue();
    }

    public ChatOptions getChatOptions() {
        return ChatOptions.builder().model(modelComboBox.getValue()).maxTokens(maxTokensInput.getValue())
                .temperature(temperatureInput.getValue()).topP(topPInput.getValue()).topK(topKInput.getValue())
                .frequencyPenalty(frequencyPenaltyInput.getValue()).presencePenalty(presencePenaltyInput.getValue())
                .build();
    }
}
