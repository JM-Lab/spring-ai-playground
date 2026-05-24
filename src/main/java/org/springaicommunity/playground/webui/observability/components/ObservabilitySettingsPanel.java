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
package org.springaicommunity.playground.webui.observability.components;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import org.springaicommunity.playground.observability.Window;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class ObservabilitySettingsPanel extends VerticalLayout {

    public enum Mode { SLIDING, FIXED }

    public static final int[] REFRESH_PRESETS_SECONDS = {3, 5, 10, 30};

    private static final DateTimeFormatter STATUS_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ObservabilityGlobalSettings settings;
    private final Map<Window, Button> presetChips = new LinkedHashMap<>();
    private final Map<Integer, Button> intervalChips = new LinkedHashMap<>();
    private final IntegerField customRefreshField = new IntegerField();
    private final RadioButtonGroup<Mode> modeRadio = new RadioButtonGroup<>();
    private final Div presetRow = new Div();
    private final VerticalLayout fixedBox = new VerticalLayout();
    private final DateTimePicker fromPicker = new DateTimePicker();
    private final DateTimePicker toPicker = new DateTimePicker();
    private final Span willApplyStatus = new Span();

    private Mode stagedMode;
    private Window stagedWindow;
    private LocalDateTime stagedFrom;
    private LocalDateTime stagedTo;
    private int stagedRefreshSeconds;

    public ObservabilitySettingsPanel(ObservabilityGlobalSettings settings, Component perTabSection) {
        this.settings = settings;

        this.stagedMode = settings.hasCustomRange() ? Mode.FIXED : Mode.SLIDING;
        this.stagedWindow = settings.window();
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        if (settings.hasCustomRange()) {
            this.stagedFrom = settings.customFrom();
            this.stagedTo = settings.customTo();
        } else {
            this.stagedFrom = now.minusMinutes(stagedWindow.minutes);
            this.stagedTo = now;
        }
        this.stagedRefreshSeconds = settings.refreshSeconds();

        setPadding(false);
        setSpacing(false);
        setWidthFull();
        getStyle().set("gap", "var(--lumo-space-l)");

        add(buildRefreshSection());
        add(divider());
        add(buildTimeSection());
        if (perTabSection != null) {
            add(divider());
            add(perTabSection);
        }

        syncIntervalHighlight();
        syncPresetHighlight();
        syncModeEmphasis();
        syncStatus();
    }

    public void applyStaged() {
        settings.setRefreshSeconds(stagedRefreshSeconds);
        if (stagedMode == Mode.FIXED) {
            settings.applyCustomRange(stagedFrom, stagedTo);
        } else {
            settings.selectPreset(stagedWindow);
        }
    }

    Mode stagedMode() { return stagedMode; }
    Window stagedWindow() { return stagedWindow; }
    int stagedRefreshSeconds() { return stagedRefreshSeconds; }
    LocalDateTime stagedFrom() { return stagedFrom; }
    LocalDateTime stagedTo() { return stagedTo; }

    private Component buildRefreshSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        section.getStyle().set("gap", "var(--lumo-space-s)");

        section.add(sectionHeader(VaadinIcon.REFRESH, "Refresh interval"));

        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(false);
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.END);
        row.getStyle().set("gap", "var(--lumo-space-xs)").set("flex-wrap", "wrap");
        for (int seconds : REFRESH_PRESETS_SECONDS) {
            Button chip = new Button(labelFor(seconds));
            chip.addThemeVariants(ButtonVariant.LUMO_SMALL);
            chip.addClickListener(e -> {
                stagedRefreshSeconds = seconds;
                customRefreshField.setValue(seconds);
                syncIntervalHighlight();
                syncStatus();
            });
            intervalChips.put(seconds, chip);
            row.add(chip);
        }

        customRefreshField.setLabel("Custom (s)");
        customRefreshField.setMin(0);
        customRefreshField.setStepButtonsVisible(true);
        customRefreshField.setValue(stagedRefreshSeconds);
        customRefreshField.setWidth("110px");
        customRefreshField.addValueChangeListener(e -> {
            Integer v = e.getValue();
            if (v == null) return;
            stagedRefreshSeconds = Math.max(0, v);
            syncIntervalHighlight();
            syncStatus();
        });
        row.add(customRefreshField);
        section.add(row);

        Span note = new Span(
                "Auto-refresh runs each interval while sliding window is active. Set 0 to disable.");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)");
        section.add(note);
        return section;
    }

    private Component buildTimeSection() {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);
        section.setWidthFull();
        section.getStyle().set("gap", "var(--lumo-space-s)");

        section.add(sectionHeader(VaadinIcon.CLOCK, "Time range"));

        modeRadio.setItems(Mode.SLIDING, Mode.FIXED);
        modeRadio.setItemLabelGenerator(m ->
                m == Mode.SLIDING ? "Sliding (moving with now)" : "Fixed (absolute snapshot)");
        modeRadio.setValue(stagedMode);
        modeRadio.getStyle().set("--vaadin-radio-group-flex-direction", "row");
        modeRadio.addValueChangeListener(e -> {
            stagedMode = e.getValue();
            syncModeEmphasis();
            syncPresetHighlight();
            syncStatus();
        });
        section.add(modeRadio);

        willApplyStatus.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("font-weight", "500")
                .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("display", "block");
        section.add(willApplyStatus);

        section.add(subHeader("Quick range — sliding window"));
        presetRow.getStyle().set("display", "flex").set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-xs)")
                .set("transition", "opacity 120ms");
        for (Window w : ObservabilityGlobalSettings.WINDOW_PRESETS) {
            Button chip = new Button(ObservabilityGlobalSettings.formatDuration(w.minutes));
            chip.addThemeVariants(ButtonVariant.LUMO_SMALL);
            chip.addClickListener(e -> {
                stagedMode = Mode.SLIDING;
                stagedWindow = w;
                LocalDateTime nowLocal = LocalDateTime.now().withSecond(0).withNano(0);
                stagedFrom = nowLocal.minusMinutes(w.minutes);
                stagedTo = nowLocal;
                fromPicker.setValue(stagedFrom);
                toPicker.setValue(stagedTo);
                modeRadio.setValue(Mode.SLIDING);
                syncPresetHighlight();
                syncModeEmphasis();
                syncStatus();
            });
            presetChips.put(w, chip);
            presetRow.add(chip);
        }
        section.add(presetRow);

        section.add(subHeader("Fixed range — snapshot (max 3h, pauses refresh)"));
        fixedBox.setPadding(false);
        fixedBox.setSpacing(false);
        fixedBox.setWidthFull();
        fixedBox.getStyle().set("gap", "var(--lumo-space-s)")
                .set("transition", "opacity 120ms");

        fromPicker.setLabel("From");
        fromPicker.setStep(Duration.ofMinutes(1));
        fromPicker.setWidthFull();
        fromPicker.setValue(stagedFrom);
        fromPicker.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                stagedMode = Mode.FIXED;
                stagedFrom = e.getValue();
                modeRadio.setValue(Mode.FIXED);
                syncPresetHighlight();
                syncModeEmphasis();
                syncStatus();
            } else {
                stagedFrom = e.getValue();
            }
        });
        toPicker.setLabel("To");
        toPicker.setStep(Duration.ofMinutes(1));
        toPicker.setWidthFull();
        toPicker.setValue(stagedTo);
        toPicker.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                stagedMode = Mode.FIXED;
                stagedTo = e.getValue();
                modeRadio.setValue(Mode.FIXED);
                syncPresetHighlight();
                syncModeEmphasis();
                syncStatus();
            } else {
                stagedTo = e.getValue();
            }
        });
        fixedBox.add(fromPicker, toPicker);
        section.add(fixedBox);
        return section;
    }

    private void syncModeEmphasis() {
        presetRow.getStyle().set("opacity", stagedMode == Mode.SLIDING ? "1" : "0.55");
        fixedBox.getStyle().set("opacity", stagedMode == Mode.FIXED ? "1" : "0.55");
    }

    private void syncPresetHighlight() {
        Window active = stagedMode == Mode.SLIDING ? stagedWindow : null;
        for (Map.Entry<Window, Button> e : presetChips.entrySet()) {
            Button chip = e.getValue();
            chip.getThemeNames().remove("primary");
            chip.getThemeNames().remove("contrast");
            chip.addThemeVariants(e.getKey() == active ? ButtonVariant.LUMO_PRIMARY
                    : ButtonVariant.LUMO_CONTRAST);
        }
    }

    private void syncIntervalHighlight() {
        for (Map.Entry<Integer, Button> e : intervalChips.entrySet()) {
            Button chip = e.getValue();
            chip.getThemeNames().remove("primary");
            chip.getThemeNames().remove("contrast");
            chip.addThemeVariants(e.getKey() == stagedRefreshSeconds
                    ? ButtonVariant.LUMO_PRIMARY
                    : ButtonVariant.LUMO_CONTRAST);
        }
    }

    private void syncStatus() {
        String windowPart;
        if (stagedMode == Mode.FIXED && stagedFrom != null && stagedTo != null) {
            long minutes = Duration.between(stagedFrom, stagedTo).toMinutes();
            windowPart = "fixed " + stagedFrom.format(STATUS_FMT) + " → "
                    + stagedTo.format(STATUS_FMT)
                    + " (" + ObservabilityGlobalSettings.formatDuration(Math.max(0, minutes)) + ")";
        } else {
            windowPart = "sliding, last "
                    + ObservabilityGlobalSettings.formatDuration(stagedWindow.minutes);
        }
        String refreshPart = stagedRefreshSeconds <= 0 ? "refresh off"
                : "refresh every " + stagedRefreshSeconds + "s";
        willApplyStatus.setText("Will apply on confirm: " + windowPart + " · " + refreshPart);
    }

    private Component sectionHeader(VaadinIcon icon, String text) {
        Span lbl = new Span(text);
        lbl.getStyle().set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout row = new HorizontalLayout(icon.create(), lbl);
        row.setSpacing(true);
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return row;
    }

    private Component subHeader(String text) {
        Span s = new Span(text);
        s.getStyle().set("font-weight", "600")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.04em");
        return s;
    }

    private Div divider() {
        Div d = new Div();
        d.getStyle().set("height", "1px")
                .set("background", "var(--lumo-contrast-10pct)");
        return d;
    }

    private static String labelFor(int seconds) {
        if (seconds <= 0) return "Off";
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m";
    }

    public static class TimeWindowPicker extends Button {

        public TimeWindowPicker(ObservabilityGlobalSettings settings, Runnable onClickAction) {
            setIcon(VaadinIcon.CLOCK.create());
            addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            setText(settings.describeWindow());
            getStyle().set("font-weight", "500").set("white-space", "nowrap");
            setTooltipText("Time range — applies to every observability dashboard");

            addClickListener(e -> {
                if (onClickAction != null) onClickAction.run();
            });
            settings.onRangeChanged(() -> setText(settings.describeWindow()));
        }
    }

    public static class RefreshIntervalPicker extends HorizontalLayout {

        private final ObservabilityGlobalSettings settings;
        private final Button refreshNowBtn = new Button(VaadinIcon.REFRESH.create());
        private final Button intervalBtn = new Button();
        private final Icon intervalCaret = VaadinIcon.CHEVRON_DOWN.create();

        public RefreshIntervalPicker(ObservabilityGlobalSettings settings, Runnable onSettingsClick,
                Runnable refreshNowAction) {
            this.settings = settings;
            setSpacing(false);
            setPadding(false);
            getStyle().set("gap", "0");

            refreshNowBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            refreshNowBtn.setTooltipText("Refresh now");
            refreshNowBtn.addClickListener(e -> {
                if (refreshNowAction != null) refreshNowAction.run();
            });

            intervalCaret.getStyle().set("width", "var(--lumo-icon-size-s)")
                    .set("height", "var(--lumo-icon-size-s)")
                    .set("margin-left", "var(--lumo-space-xs)");
            intervalBtn.setIconAfterText(true);
            intervalBtn.setIcon(intervalCaret);
            intervalBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            intervalBtn.getStyle().set("font-variant-numeric", "tabular-nums");
            intervalBtn.addClickListener(e -> {
                if (onSettingsClick != null) onSettingsClick.run();
            });

            add(refreshNowBtn, intervalBtn);
            settings.onRefreshIntervalChanged(seconds -> syncLabel());
            settings.onRangeChanged(this::syncEnabled);
            syncLabel();
            syncEnabled();
        }

        private void syncLabel() {
            intervalBtn.setText(labelFor(settings.refreshSeconds()));
        }

        private void syncEnabled() {
            boolean disabled = settings.hasCustomRange();
            intervalBtn.setEnabled(!disabled);
            intervalBtn.setTooltipText(disabled
                    ? "Auto-refresh paused while a custom range is active"
                    : "Auto-refresh interval — opens settings");
        }
    }
}
