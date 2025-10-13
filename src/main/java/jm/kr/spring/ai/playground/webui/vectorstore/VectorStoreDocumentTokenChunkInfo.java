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
package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService.TokenTextSplitInfo;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService.DEFAULT_TOKEN_TEXT_SPLIT_INFO;

public class VectorStoreDocumentTokenChunkInfo extends VerticalLayout {

    private final NumberField chunkSizeField;
    private final NumberField minChunkSizeCharsField;
    private final NumberField minChunkLengthToEmbedField;
    private final NumberField maxNumChunksField;
    private final Checkbox keepSeparatorCheckbox;

    public VectorStoreDocumentTokenChunkInfo() {
        setMargin(false);
        setPadding(false);
        setSpacing(false);

        H5 title = new H5("Token Text Splitter Settings");
        add(title);

        chunkSizeField = createValidatedField("Chunk Size", DEFAULT_TOKEN_TEXT_SPLIT_INFO.chunkSize(), 1, 10000,
                "Enter 1~10,000.");
        minChunkSizeCharsField =
                createValidatedField("Min Chunk Size Chars", DEFAULT_TOKEN_TEXT_SPLIT_INFO.minChunkSizeChars(), 1,
                        10000, "Enter 1~10,000.");
        minChunkLengthToEmbedField =
                createValidatedField("Min Chunk Length To Embed", DEFAULT_TOKEN_TEXT_SPLIT_INFO.minChunkLengthToEmbed(),
                        1, 10000, "Enter 1~10,000.");
        maxNumChunksField =
                createValidatedField("Max Num Chunks", DEFAULT_TOKEN_TEXT_SPLIT_INFO.maxNumChunks(), 1, 10000,
                        "Enter 1~10,000.");

        keepSeparatorCheckbox = new Checkbox("Keep Separator");
        keepSeparatorCheckbox.setValue(DEFAULT_TOKEN_TEXT_SPLIT_INFO.keepSeparator());

        HorizontalLayout paramLayout =
                new HorizontalLayout(chunkSizeField, minChunkSizeCharsField, minChunkLengthToEmbedField,
                        maxNumChunksField);
        paramLayout.setWidthFull();
        paramLayout.getStyle().set("padding", "var(--lumo-space-m) 0 var(--lumo-space-m) 0");

        HorizontalLayout keepSeparatorLayout = new HorizontalLayout(keepSeparatorCheckbox);
        keepSeparatorLayout.setWidthFull();
        keepSeparatorLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        keepSeparatorLayout.getStyle().set("padding-bottom", "var(--lumo-space-m)");

        add(paramLayout, keepSeparatorLayout);
    }

    private NumberField createValidatedField(String label, double defaultVal, double min, double max, String errorMsg) {
        NumberField field = new NumberField(label);
        field.setValue(defaultVal);
        field.setStep(1);
        field.setMin(min);
        field.setMax(max);
        field.setWidth("15em");
        field.setRequiredIndicatorVisible(true);
        field.addValueChangeListener(ev -> {
            Double v = ev.getValue();
            boolean invalid = v == null || v < min || v > max;
            field.setInvalid(invalid);
            field.setErrorMessage(invalid ? errorMsg : null);
        });
        return field;
    }

    private boolean validateAllFields() {
        boolean valid = true;
        valid &= !chunkSizeField.isInvalid();
        valid &= !minChunkSizeCharsField.isInvalid();
        valid &= !minChunkLengthToEmbedField.isInvalid();
        valid &= !maxNumChunksField.isInvalid();

        valid &= chunkSizeField.getValue() != null;
        valid &= minChunkSizeCharsField.getValue() != null;
        valid &= minChunkLengthToEmbedField.getValue() != null;
        valid &= maxNumChunksField.getValue() != null;

        return valid;
    }

    public TokenTextSplitInfo collectInput() {
        if (!validateAllFields()) {
            throw new IllegalStateException("Some fields are invalid or empty");
        }
        return new TokenTextSplitInfo(chunkSizeField.getValue().intValue(),
                minChunkSizeCharsField.getValue().intValue(), minChunkLengthToEmbedField.getValue().intValue(),
                maxNumChunksField.getValue().intValue(),
                keepSeparatorCheckbox.getValue()
        );
    }
}
