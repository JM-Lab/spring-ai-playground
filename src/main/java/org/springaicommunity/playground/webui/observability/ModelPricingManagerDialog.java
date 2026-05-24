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
package org.springaicommunity.playground.webui.observability;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.dialog.DialogVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import org.springaicommunity.playground.observability.pricing.CurrencyService;
import org.springaicommunity.playground.observability.pricing.CurrencyService.CurrencyRate;
import org.springaicommunity.playground.observability.pricing.ModelPricing;
import org.springaicommunity.playground.observability.pricing.ModelPricingService;
import org.springaicommunity.playground.webui.VaadinUtils;

import java.math.BigDecimal;
import java.util.List;

public class ModelPricingManagerDialog extends Dialog {

    private final ModelPricingService pricingService;
    private final CurrencyService currencyService;
    private final Grid<ModelPricing> grid = new Grid<>(ModelPricing.class, false);
    private final TextField newModel = new TextField("Model");
    private final BigDecimalField newIn = new BigDecimalField("Input $/1M");
    private final BigDecimalField newOut = new BigDecimalField("Output $/1M");
    private final ComboBox<String> currencyCombo = new ComboBox<>("Display currency");
    private final BigDecimalField rateField = new BigDecimalField("Rate (per 1 USD)");
    private final TextField customCode = new TextField("Code (3-letter)");
    private final TextField customSymbol = new TextField("Symbol");
    private final BigDecimalField customRate = new BigDecimalField("Rate (per 1 USD)");
    private final Span emptyStateBanner = new Span();
    private final Span currencySummary = new Span();
    private final Details currencyDetails = new Details();
    private final Span activeCurrencyBadge = new Span();

    private Runnable onChangedListener = () -> {};

    public ModelPricingManagerDialog(ModelPricingService pricingService,
            CurrencyService currencyService) {
        this.pricingService = pricingService;
        this.currencyService = currencyService;

        setModal(true);
        setResizable(true);
        setDraggable(true);
        addThemeVariants(DialogVariant.LUMO_NO_PADDING);
        setWidth("780px");
        setMaxWidth("100vw");

        // Header — title + active currency badge + close
        HorizontalLayout headerRow = VaadinUtils.buildHeaderHorizontalLayout(
                "Model pricing", e -> close());
        activeCurrencyBadge.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("padding", "2px 10px")
                .set("border-radius", "999px")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("margin-right", "var(--lumo-space-m)")
                .set("white-space", "nowrap")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "4px");
        // Insert badge as second-to-last (before close button)
        headerRow.addComponentAtIndex(headerRow.getComponentCount() - 1, activeCurrencyBadge);
        getHeader().add(headerRow);

        // Empty-state banner — shows when no pricing rows
        emptyStateBanner.setText(
                "No model pricing configured yet. Add a model below to start tracking cost.");
        emptyStateBanner.getStyle()
                .set("display", "block")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-warning-color-10pct)")
                .set("border-left", "3px solid var(--lumo-warning-color)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("color", "var(--lumo-warning-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        // Pricing grid
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_NO_BORDER);
        grid.addColumn(ModelPricing::model).setHeader("Model").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(p -> formatBd(p.inputPerMillionTokens())).setHeader("Input $/1M").setAutoWidth(true);
        grid.addColumn(p -> formatBd(p.outputPerMillionTokens())).setHeader("Output $/1M").setAutoWidth(true);
        grid.addComponentColumn(p -> {
            Button del = new Button(VaadinIcon.TRASH.create(), e -> {
                pricingService.delete(p.model());
                refreshGrid();
                onChangedListener.run();
            });
            del.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON,
                    ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
            del.setTooltipText("Delete pricing for " + p.model());
            return del;
        }).setAutoWidth(true).setHeader("");
        grid.setAllRowsVisible(true);
        grid.setWidthFull();

        Button addBtn = new Button("Add / Save", VaadinIcon.PLUS.create(), e -> save());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addBtn.setTooltipText("Save or add model pricing entry");

        // Add row: FormLayout-driven so the input + button baselines align
        // regardless of helper text height.
        FormLayout addForm = new FormLayout(newModel, newIn, newOut);
        addForm.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("520px", 3));
        addForm.setWidthFull();
        HorizontalLayout addAction = new HorizontalLayout(addBtn);
        addAction.setWidthFull();
        addAction.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        addAction.setPadding(false);

        // Currency Details (collapsed) — FormLayout for label/baseline
        // alignment; action button on its own right-aligned row beneath.
        currencyCombo.setItems(currencyService.all().stream().map(CurrencyRate::code).toList());
        currencyCombo.setValue(currencyService.getActiveCurrency());
        currencyCombo.setAllowCustomValue(false);
        currencyCombo.setWidthFull();
        // Flag + code + symbol — same generator drives both the selected
        // chip and dropdown rows so the picker reads as a country list.
        currencyCombo.setItemLabelGenerator(code -> {
            CurrencyRate r = currencyService.all().stream()
                    .filter(x -> x.code().equals(code)).findFirst().orElse(null);
            String symbol = r == null ? "" : r.symbol();
            return CurrencyService.flagFor(code) + "  " + code + "  " + symbol;
        });
        currencyCombo.addValueChangeListener(e -> {
            String code = e.getValue();
            if (code == null) return;
            currencyService.setActiveCurrency(code);
            CurrencyRate r = currencyService.getActiveRate();
            rateField.setValue(r.rateFromUsd());
            refreshCurrencySummary();
            onChangedListener.run();
        });
        rateField.setValue(currencyService.getActiveRate().rateFromUsd());
        rateField.setWidthFull();
        Button saveRate = new Button("Save rate", VaadinIcon.CHECK.create(), e -> {
            CurrencyRate current = currencyService.getActiveRate();
            BigDecimal newRate = rateField.getValue();
            if (newRate == null || newRate.signum() <= 0) {
                VaadinUtils.showErrorNotification("Rate must be > 0");
                return;
            }
            currencyService.upsertRate(new CurrencyRate(current.code(), current.symbol(), newRate));
            refreshCurrencySummary();
            onChangedListener.run();
        });
        saveRate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout currencyForm = new FormLayout(currencyCombo, rateField);
        currencyForm.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("420px", 2));
        currencyForm.setWidthFull();

        Span currencyHelper = new Span(
                "Multiplier applied to USD-based pricing. The seeded defaults are an approximation — "
                        + "edit the Rate to override.");
        currencyHelper.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("display", "block")
                .set("padding-top", "var(--lumo-space-xs)");

        HorizontalLayout currencyAction = new HorizontalLayout(saveRate);
        currencyAction.setWidthFull();
        currencyAction.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        currencyAction.setPadding(false);
        currencyAction.getStyle().set("margin-top", "var(--lumo-space-s)");

        // Add custom currency sub-form — unique ID (1-8 chars), symbol, rate.
        // Code isn't strict ISO 4217; same symbol can map to multiple
        // currencies (USD/ARS/CLP all use "$") so we still need a key.
        customCode.setLabel("ID");
        customCode.setPlaceholder("e.g. AUD or MYCUR");
        customCode.setMaxLength(8);
        customCode.setHelperText("Unique short label — used as the dropdown text + storage key");
        customCode.setWidthFull();
        customSymbol.setPlaceholder("e.g. A$");
        customSymbol.setMaxLength(8);
        customSymbol.setHelperText("Shown in KPIs / chart values");
        customSymbol.setWidthFull();
        customRate.setPlaceholder("e.g. 1.52");
        customRate.setHelperText("Multiplier on USD");
        customRate.setWidthFull();
        Button addCustomBtn = new Button("Add custom", VaadinIcon.PLUS.create(), e -> {
            String codeRaw = customCode.getValue();
            String symbol = customSymbol.getValue();
            BigDecimal newRate2 = customRate.getValue();
            if (codeRaw == null || codeRaw.isBlank() || codeRaw.trim().length() > 8) {
                VaadinUtils.showErrorNotification("ID is required (1-8 characters)");
                return;
            }
            if (symbol == null || symbol.isBlank()) {
                VaadinUtils.showErrorNotification("Symbol is required (e.g. A$)");
                return;
            }
            if (newRate2 == null || newRate2.signum() <= 0) {
                VaadinUtils.showErrorNotification("Rate must be > 0");
                return;
            }
            String code = codeRaw.trim().toUpperCase();
            currencyService.upsertRate(new CurrencyRate(code, symbol.trim(), newRate2));
            customCode.clear();
            customSymbol.clear();
            customRate.clear();
            // Re-populate combo with the new entry + select it as active.
            currencyCombo.setItems(currencyService.all().stream().map(CurrencyRate::code).toList());
            currencyCombo.setValue(code);
            currencyService.setActiveCurrency(code);
            rateField.setValue(newRate2);
            refreshCurrencySummary();
            onChangedListener.run();
        });
        addCustomBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout customForm = new FormLayout(customCode, customSymbol, customRate);
        customForm.setResponsiveSteps(
                new ResponsiveStep("0", 1),
                new ResponsiveStep("520px", 3));
        customForm.setWidthFull();

        Span customHelper = new Span(
                "Need a currency that's not in the dropdown? Add any 3-letter ISO 4217 code with a "
                        + "symbol and USD-multiplier rate. The flag is auto-derived from the code's first two letters.");
        customHelper.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("display", "block")
                .set("padding-top", "var(--lumo-space-xs)");

        HorizontalLayout customAction = new HorizontalLayout(addCustomBtn);
        customAction.setWidthFull();
        customAction.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        customAction.setPadding(false);
        customAction.getStyle().set("margin-top", "var(--lumo-space-s)");

        Details customDetails = new Details();
        Div customSummaryRow = new Div();
        customSummaryRow.getStyle().set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("width", "100%");
        com.vaadin.flow.component.icon.Icon plusIcon =
                com.vaadin.flow.component.icon.VaadinIcon.PLUS_CIRCLE_O.create();
        plusIcon.setSize("18px");
        plusIcon.getStyle().set("color", "var(--lumo-secondary-text-color)").set("flex", "0 0 auto");
        Span customSummary = new Span("Add custom currency");
        customSummary.getStyle().set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("flex", "1 1 auto");
        customSummaryRow.add(plusIcon, customSummary);
        customDetails.setSummary(customSummaryRow);
        customDetails.setOpened(false);
        customDetails.addThemeVariants(DetailsVariant.REVERSE, DetailsVariant.FILLED);
        VerticalLayout customContent = new VerticalLayout(customForm, customHelper, customAction);
        customContent.setPadding(false);
        customContent.setSpacing(false);
        customDetails.setContent(customContent);
        customDetails.setWidthFull();
        customDetails.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("margin-top", "var(--lumo-space-m)");

        VerticalLayout currencyContent = new VerticalLayout(currencyForm, currencyHelper,
                currencyAction, customDetails);
        currencyContent.setPadding(false);
        currencyContent.setSpacing(false);

        // Details summary — looks like a clickable card row with chevron.
        // White-on-tinted-bg + border + hover state makes the affordance clear
        // (the prior flat background read as a static label).
        refreshCurrencySummary();
        Div summaryRow = new Div();
        summaryRow.getStyle().set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("width", "100%");
        com.vaadin.flow.component.icon.Icon settingsIcon =
                com.vaadin.flow.component.icon.VaadinIcon.COG_O.create();
        settingsIcon.setSize("18px");
        settingsIcon.getStyle().set("color", "var(--lumo-secondary-text-color)").set("flex", "0 0 auto");
        summaryRow.add(settingsIcon, currencySummary);
        currencySummary.getStyle().set("flex", "1 1 auto");
        currencyDetails.setSummary(summaryRow);
        currencyDetails.setOpened(false);
        currencyDetails.addThemeVariants(DetailsVariant.REVERSE, DetailsVariant.FILLED);
        currencyDetails.setContent(currencyContent);
        currencyDetails.setWidthFull();
        currencyDetails.addClassName("currency-details-clickable");
        currencyDetails.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("margin-top", "var(--lumo-space-l)");

        // Footer hint — small + neutral
        Span hint = new Span(
                "Pricing → pricing.json · Currency → currency.json (~/spring-ai-playground/observability/)");
        hint.getStyle().set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("display", "block")
                .set("text-align", "right")
                .set("margin-top", "var(--lumo-space-m)");

        // Body
        VerticalLayout body = new VerticalLayout();
        body.setPadding(true);
        body.setSpacing(false);
        body.setWidthFull();
        body.getStyle().set("gap", "var(--lumo-space-s)");

        Div gridWrap = new Div(grid);
        gridWrap.getStyle().set("width", "100%").set("margin-top", "var(--lumo-space-s)");

        body.add(emptyStateBanner, gridWrap, addForm, addAction, currencyDetails, hint);
        add(body);

        addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                refreshGrid();
                currencyCombo.setItems(currencyService.all().stream().map(CurrencyRate::code).toList());
                currencyCombo.setValue(currencyService.getActiveCurrency());
                rateField.setValue(currencyService.getActiveRate().rateFromUsd());
                refreshCurrencySummary();
            }
        });
    }

    public void onChanged(Runnable listener) {
        this.onChangedListener = listener == null ? () -> {} : listener;
    }

    private void save() {
        if (newModel.isEmpty()) {
            VaadinUtils.showErrorNotification("Model is required");
            return;
        }
        pricingService.upsert(new ModelPricing(newModel.getValue().trim(),
                nzBd(newIn.getValue()), nzBd(newOut.getValue())));
        newModel.clear();
        newIn.clear();
        newOut.clear();
        refreshGrid();
        onChangedListener.run();
    }

    private void refreshGrid() {
        List<ModelPricing> all = pricingService.all();
        all.sort((a, b) -> a.model().compareToIgnoreCase(b.model()));
        grid.setItems(all);
        emptyStateBanner.setVisible(all.isEmpty());
    }

    private void refreshCurrencySummary() {
        CurrencyRate r = currencyService.getActiveRate();
        String flag = CurrencyService.flagFor(r.code());
        String text = "USD".equals(r.code())
                ? "Currency · " + flag + " USD (" + r.symbol() + ", base ×1.0)"
                : "Currency · " + flag + " " + r.code() + " (" + r.symbol() + " × "
                        + r.rateFromUsd().toPlainString() + ")";
        currencySummary.setText(text);
        currencySummary.getStyle().set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)");
        activeCurrencyBadge.setText(flag + " " + r.code() + " " + r.symbol());
    }

    private static String formatBd(BigDecimal v) {
        return v == null ? "—" : v.toPlainString();
    }

    private static BigDecimal nzBd(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
