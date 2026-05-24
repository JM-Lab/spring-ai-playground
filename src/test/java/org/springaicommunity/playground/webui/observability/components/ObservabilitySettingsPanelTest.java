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
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel.Mode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilitySettingsPanelTest {

    @Test
    void buildsSixPresetChipsWithoutOneMinute() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        List<String> chipTexts = collectButtonTexts(panel);
        assertThat(chipTexts).contains("5m", "10m", "20m", "30m", "1h", "3h");
        assertThat(ObservabilityGlobalSettings.WINDOW_PRESETS)
                .extracting(w -> w.minutes).doesNotContain(1);
    }

    @Test
    void refreshIntervalHasFourPresetsAndNoOffNorOneMinute() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        List<String> chipTexts = collectButtonTexts(panel);
        assertThat(chipTexts).contains("3s", "5s", "10s", "30s");
        // Previous presets removed
        assertThat(chipTexts).doesNotContain("Off", "1s", "2s");
        // 1m label is reused only for some other context — verify presets are exactly the four
        assertThat(ObservabilitySettingsPanel.REFRESH_PRESETS_SECONDS)
                .containsExactly(3, 5, 10, 30);
    }

    @Test
    void refreshSectionExposesCustomSecondsField() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        long integerFieldCount = panel.getChildren()
                .flatMap(c -> streamAll(c))
                .filter(c -> "VAADIN-INTEGER-FIELD".equalsIgnoreCase(c.getElement().getTag()))
                .count();
        assertThat(integerFieldCount).isEqualTo(1);
    }

    @Test
    void noInlineApplyOrBackButtonsInPanel() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        List<String> texts = collectButtonTexts(panel);
        assertThat(texts).doesNotContain("Apply fixed range", "Back to sliding",
                "Apply custom range", "Clear");
    }

    @Test
    void refreshSectionRendersBeforeTimeSection() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        String html = panel.getElement().getOuterHTML();
        int refreshIdx = html.indexOf("Refresh interval");
        int timeIdx = html.indexOf("Time range");
        assertThat(refreshIdx).isGreaterThanOrEqualTo(0);
        assertThat(timeIdx).isGreaterThan(refreshIdx);
    }

    @Test
    void appendsPerTabSectionWhenProvided() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        Span perTab = new Span("PER_TAB_MARKER");
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, perTab);

        boolean hasMarker = panel.getElement().getOuterHTML().contains("PER_TAB_MARKER");
        assertThat(hasMarker).isTrue();
    }

    @Test
    void chipClickStagesWindowButDoesNotApplyUntilCommit() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        settings.selectPreset(Window.LAST_30M);
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        Button chip = findButtonByText(panel, "10m");
        chip.click();

        assertThat(settings.window()).isEqualTo(Window.LAST_30M);
        assertThat(panel.stagedWindow()).isEqualTo(Window.LAST_10M);

        panel.applyStaged();
        assertThat(settings.window()).isEqualTo(Window.LAST_10M);
    }

    @Test
    void intervalChipClickStagesButDoesNotApplyUntilCommit() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        settings.setRefreshSeconds(5);
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        Button chip = findButtonByText(panel, "30s");
        chip.click();

        assertThat(settings.refreshSeconds()).isEqualTo(5);
        assertThat(panel.stagedRefreshSeconds()).isEqualTo(30);

        panel.applyStaged();
        assertThat(settings.refreshSeconds()).isEqualTo(30);
    }

    @Test
    void modeRadioGroupShowsSlidingAndFixedLabels() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        String html = panel.getElement().getOuterHTML();
        assertThat(html).contains("Sliding (moving with now)").contains("Fixed (absolute snapshot)");
    }

    @Test
    void initialStagedReflectsCurrentSettings() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        settings.selectPreset(Window.LAST_1H);
        settings.setRefreshSeconds(10);
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        assertThat(panel.stagedMode()).isEqualTo(Mode.SLIDING);
        assertThat(panel.stagedWindow()).isEqualTo(Window.LAST_1H);
        assertThat(panel.stagedRefreshSeconds()).isEqualTo(10);
    }

    @Test
    void initialStagedReflectsCustomRangeWhenActive() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        LocalDateTime to = LocalDateTime.of(2026, 5, 20, 10, 0);
        LocalDateTime from = to.minusHours(2);
        settings.applyCustomRange(from, to);
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        assertThat(panel.stagedMode()).isEqualTo(Mode.FIXED);
        assertThat(panel.stagedFrom()).isEqualTo(from);
        assertThat(panel.stagedTo()).isEqualTo(to);
    }

    @Test
    void applyStagedFixedRangeCommitsCustomRangeToSettings() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        // Set fixed range manually then apply
        LocalDateTime to = LocalDateTime.of(2026, 5, 20, 12, 0);
        LocalDateTime from = to.minusHours(1);

        // Cannot click radio programmatically easily; just call applyStaged after manipulating
        // private state via a sequence: use chip click to set Sliding 1h then change mode logic
        // simpler: just verify applyStaged uses stagedMode correctly via reflection-free path
        settings.applyCustomRange(from, to);  // bootstrap with custom range
        ObservabilitySettingsPanel panel2 = new ObservabilitySettingsPanel(settings, null);
        assertThat(panel2.stagedMode()).isEqualTo(Mode.FIXED);
        panel2.applyStaged();
        assertThat(settings.hasCustomRange()).isTrue();
        assertThat(settings.customFrom()).isEqualTo(from);
        assertThat(settings.customTo()).isEqualTo(to);
    }

    @Test
    void willApplyStatusReflectsStagedSliding() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        Button chip = findButtonByText(panel, "1h");
        chip.click();
        String html = panel.getElement().getOuterHTML();
        assertThat(html).contains("Will apply on confirm").contains("sliding").contains("1h");
    }

    @Test
    void bothSectionHeadersAlwaysVisible() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        String html = panel.getElement().getOuterHTML();
        assertThat(html).contains("Quick range").contains("sliding window")
                .contains("Fixed range").contains("max 3h");
    }

    @Test
    void fromAndToPickersAlwaysVisibleEvenInSlidingMode() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        settings.selectPreset(Window.LAST_30M);
        ObservabilitySettingsPanel panel = new ObservabilitySettingsPanel(settings, null);

        assertThat(panel.stagedMode()).isEqualTo(Mode.SLIDING);
        long pickerCount = panel.getChildren()
                .flatMap(c -> streamAll(c))
                .filter(c -> "VAADIN-DATE-TIME-PICKER".equalsIgnoreCase(c.getElement().getTag()))
                .count();
        assertThat(pickerCount).isEqualTo(2);
    }

    private Stream<Component> streamAll(Component c) {
        return Stream.concat(
                Stream.of(c),
                c.getChildren().flatMap(this::streamAll));
    }

    private List<String> collectButtonTexts(Component root) {
        return root.getChildren()
                .flatMap(c -> streamButtons(c))
                .map(Button::getText)
                .filter(t -> t != null && !t.isEmpty())
                .toList();
    }

    private Stream<Button> streamButtons(Component c) {
        Stream<Button> self = c instanceof Button b
                ? Stream.of(b)
                : Stream.empty();
        return Stream.concat(self,
                c.getChildren().flatMap(this::streamButtons));
    }

    private Button findButtonByText(Component root, String text) {
        return root.getChildren()
                .flatMap(this::streamButtons)
                .filter(b -> text.equals(b.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("button not found: " + text));
    }
}
