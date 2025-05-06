package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.SearchRequestOption;

import static jm.kr.spring.ai.playground.service.vectorstore.VectorStoreService.ALL_SEARCH_REQUEST_OPTION;

public class VectorStoreSearchSettingView extends VerticalLayout {
    private final VectorStoreService vectorStoreService;

    public VectorStoreSearchSettingView(VectorStoreService vectorStoreService) {
        this.vectorStoreService = vectorStoreService;
        SearchRequestOption vectorStoreOption = this.vectorStoreService.getSearchRequestOption();

        setSpacing(false);
        setAlignItems(Alignment.START);

        NumberField similarityThresholdInput = new NumberField("Similarity Threshold (0 = All)");
        similarityThresholdInput.setMin(0);
        similarityThresholdInput.setMax(1);
        similarityThresholdInput.setValue(vectorStoreOption.similarityThreshold());
        similarityThresholdInput.setWidthFull();
        similarityThresholdInput.setI18n(new NumberField.NumberFieldI18n()
                .setBadInputErrorMessage("Please enter a valid number.")
                .setMinErrorMessage("The value must be at least 0.")
                .setMaxErrorMessage("The value cannot be greater than 1")
                .setRequiredErrorMessage("Please specify the quantity."));

        RangeInput similarityThresholdSlider = new RangeInput();
        similarityThresholdSlider.setStep(0.1);
        similarityThresholdSlider.setMin(0);
        similarityThresholdSlider.setMax(1);
        similarityThresholdSlider.setValue(vectorStoreOption.similarityThreshold());
        similarityThresholdSlider.setWidthFull();
        similarityThresholdSlider.addValueChangeListener(e -> similarityThresholdInput.setValue(e.getValue()));
        similarityThresholdInput.addValueChangeListener(e -> {
            similarityThresholdSlider.setValue(e.getValue());
            this.vectorStoreService.setVectorStoreOption(vectorStoreOption.newSimilarityThreshold(e.getValue()));
        });
        similarityThresholdInput.setValue(vectorStoreOption.similarityThreshold());
        add(similarityThresholdInput, similarityThresholdSlider);

        IntegerField topKInput = new IntegerField("Top K");
        topKInput.setMin(1);
        topKInput.setMax(ALL_SEARCH_REQUEST_OPTION.topK());
        topKInput.setValue(vectorStoreOption.topK());
        topKInput.setWidthFull();
        topKInput.setI18n(new IntegerField.IntegerFieldI18n()
                .setBadInputErrorMessage("Please enter a valid number.")
                .setMinErrorMessage("The value must be at least 1.")
                .setMaxErrorMessage("The value cannot be greater than 100.")
                .setRequiredErrorMessage("Please specify the quantity."));
        RangeInput topKSlider = new RangeInput();
        topKSlider.setStep(1.0);
        topKSlider.setMin(1);
        topKSlider.setMax(ALL_SEARCH_REQUEST_OPTION.topK());
        topKSlider.setValue(vectorStoreOption.topK().doubleValue());
        topKSlider.setWidthFull();
        topKSlider.addValueChangeListener(e -> topKInput.setValue(e.getValue().intValue()));
        topKInput.addValueChangeListener(e -> {
            topKSlider.setValue(e.getValue().doubleValue());
            this.vectorStoreService.setVectorStoreOption(vectorStoreOption.newTopK(e.getValue()));
        });
        add(topKInput, topKSlider);
    }

}
