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
        if (model != null) {
            modelComboBox.setValue(model);
        }
        modelComboBox.setAllowCustomValue(true);
        add(modelComboBox);

        this.systemPromptTextArea = new TextArea("System Prompt");
        this.systemPromptTextArea.setWidthFull();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            this.systemPromptTextArea.setValue(systemPrompt);
        }
        add(systemPromptTextArea);

        this.maxTokensInput = new IntegerField("Max Tokens");
        this.maxTokensInput.setMin(1);
        this.maxTokensInput.setI18n(new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Quantity must be at least 1"));
        Integer maxTokens = chatOption.getMaxTokens();
        if (maxTokens != null) {
            this.maxTokensInput.setValue(maxTokens);
        }
        add(maxTokensInput);

        this.temperatureInput = new NumberField("Temperature");
        this.temperatureInput.setMin(0);
        this.temperatureInput.setMax(1);
        this.temperatureInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Quantity must be at least 0")
                .setMaxErrorMessage("Value cannot exceed 1"));

        RangeInput temperatureSlider = new RangeInput();
        temperatureSlider.setStep(0.1);
        temperatureSlider.setMin(0);
        temperatureSlider.setMax(1);

        Double temperature = chatOption.getTemperature();
        if (temperature != null) {
            this.temperatureInput.setValue(temperature);
            temperatureSlider.setValue(temperature);
        } else {
            this.temperatureInput.setValue(0.7);
            temperatureSlider.setValue(0.7);
        }

        temperatureSlider.setWidthFull();
        final boolean[] isUpdating = {false};
        temperatureSlider.addValueChangeListener(e -> {
            if (!isUpdating[0]) {
                isUpdating[0] = true;
                this.temperatureInput.setValue(e.getValue());
                isUpdating[0] = false;
            }
        });
        this.temperatureInput.addValueChangeListener(e -> {
            if (!isUpdating[0] && e.getValue() != null) {
                isUpdating[0] = true;
                temperatureSlider.setValue(e.getValue());
                isUpdating[0] = false;
            }
        });
        add(temperatureInput, temperatureSlider);

        this.topPInput = new NumberField("Top P");
        this.topPInput.setMin(0);
        this.topPInput.setMax(1);
        this.topPInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least 0")
                .setMaxErrorMessage("Value cannot exceed 1"));

        RangeInput topPSlider = new RangeInput();
        topPSlider.setMin(0);
        topPSlider.setMax(1);
        topPSlider.setStep(0.1);

        Double topP = chatOption.getTopP();
        if (topP != null) {
            this.topPInput.setValue(topP);
            topPSlider.setValue(topP);
        } else {
            this.topPInput.setValue(1.0);
            topPSlider.setValue(1.0);
        }

        topPSlider.setWidthFull();
        final boolean[] isUpdatingTopP = {false};
        topPSlider.addValueChangeListener(e -> {
            if (!isUpdatingTopP[0]) {
                isUpdatingTopP[0] = true;
                this.topPInput.setValue(e.getValue());
                isUpdatingTopP[0] = false;
            }
        });
        this.topPInput.addValueChangeListener(e -> {
            if (!isUpdatingTopP[0] && e.getValue() != null) {
                isUpdatingTopP[0] = true;
                topPSlider.setValue(e.getValue());
                isUpdatingTopP[0] = false;
            }
        });
        add(topPInput, topPSlider);

        this.topKInput = new IntegerField("Top K");
        this.topKInput.setMin(1);
        this.topKInput.setMax(100);
        this.topKInput.setI18n(new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least 1")
                .setMaxErrorMessage("Value cannot exceed 100"));

        RangeInput topKSlider = new RangeInput();
        topKSlider.setStep(1.0);
        topKSlider.setMin(1);
        topKSlider.setMax(100);

        Integer topK = chatOption.getTopK();
        if (topK != null) {
            this.topKInput.setValue(topK);
            topKSlider.setValue(topK.doubleValue());
        } else {
            this.topKInput.setValue(50);
            topKSlider.setValue(50.0);
        }

        topKSlider.setWidthFull();
        final boolean[] isUpdatingTopK = {false};
        topKSlider.addValueChangeListener(e -> {
            if (!isUpdatingTopK[0]) {
                isUpdatingTopK[0] = true;
                this.topKInput.setValue(e.getValue().intValue());
                isUpdatingTopK[0] = false;
            }
        });
        this.topKInput.addValueChangeListener(e -> {
            if (!isUpdatingTopK[0] && e.getValue() != null) {
                isUpdatingTopK[0] = true;
                topKSlider.setValue(e.getValue().doubleValue());
                isUpdatingTopK[0] = false;
            }
        });
        add(topKInput, topKSlider);

        this.frequencyPenaltyInput = new NumberField("Frequency Penalty");
        this.frequencyPenaltyInput.setMin(-2);
        this.frequencyPenaltyInput.setMax(2);
        this.frequencyPenaltyInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least -2")
                .setMaxErrorMessage("Value cannot exceed 2"));

        RangeInput frequencyPenaltySlider = new RangeInput();
        frequencyPenaltySlider.setStep(0.1);
        frequencyPenaltySlider.setMin(-2);
        frequencyPenaltySlider.setMax(2);

        Double frequencyPenalty = chatOption.getFrequencyPenalty();
        if (frequencyPenalty != null) {
            this.frequencyPenaltyInput.setValue(frequencyPenalty);
            frequencyPenaltySlider.setValue(frequencyPenalty);
        } else {
            this.frequencyPenaltyInput.setValue(0.0);
            frequencyPenaltySlider.setValue(0.0);
        }

        frequencyPenaltySlider.setWidthFull();
        final boolean[] isUpdatingFreq = {false};
        frequencyPenaltySlider.addValueChangeListener(e -> {
            if (!isUpdatingFreq[0]) {
                isUpdatingFreq[0] = true;
                this.frequencyPenaltyInput.setValue(e.getValue());
                isUpdatingFreq[0] = false;
            }
        });
        this.frequencyPenaltyInput.addValueChangeListener(e -> {
            if (!isUpdatingFreq[0] && e.getValue() != null) {
                isUpdatingFreq[0] = true;
                frequencyPenaltySlider.setValue(e.getValue());
                isUpdatingFreq[0] = false;
            }
        });
        add(frequencyPenaltyInput, frequencyPenaltySlider);

        this.presencePenaltyInput = new NumberField("Presence Penalty");
        this.presencePenaltyInput.setMin(-2);
        this.presencePenaltyInput.setMax(2);
        this.presencePenaltyInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Invalid number format")
                .setMinErrorMessage("Value must be at least -2")
                .setMaxErrorMessage("Value cannot exceed 2"));

        RangeInput presencePenaltySlider = new RangeInput();
        presencePenaltySlider.setStep(0.1);
        presencePenaltySlider.setMin(-2);
        presencePenaltySlider.setMax(2);

        Double presencePenalty = chatOption.getPresencePenalty();
        if (presencePenalty != null) {
            this.presencePenaltyInput.setValue(presencePenalty);
            presencePenaltySlider.setValue(presencePenalty);
        } else {
            this.presencePenaltyInput.setValue(0.0);
            presencePenaltySlider.setValue(0.0);
        }

        presencePenaltySlider.setWidthFull();
        final boolean[] isUpdatingPres = {false};
        presencePenaltySlider.addValueChangeListener(e -> {
            if (!isUpdatingPres[0]) {
                isUpdatingPres[0] = true;
                this.presencePenaltyInput.setValue(e.getValue());
                isUpdatingPres[0] = false;
            }
        });
        this.presencePenaltyInput.addValueChangeListener(e -> {
            if (!isUpdatingPres[0] && e.getValue() != null) {
                isUpdatingPres[0] = true;
                presencePenaltySlider.setValue(e.getValue());
                isUpdatingPres[0] = false;
            }
        });
        add(presencePenaltyInput, presencePenaltySlider);
    }

    public String getSystemPromptTextArea() {
        return this.systemPromptTextArea.getValue();
    }

    public ChatOptions getChatOptions() {
        return ChatOptions.builder()
                .model(modelComboBox.getValue())
                .maxTokens(maxTokensInput.getValue())
                .temperature(temperatureInput.getValue())
                .topP(topPInput.getValue())
                .topK(topKInput.getValue())
                .frequencyPenalty(frequencyPenaltyInput.getValue())
                .presencePenalty(presencePenaltyInput.getValue())
                .build();
    }
}

