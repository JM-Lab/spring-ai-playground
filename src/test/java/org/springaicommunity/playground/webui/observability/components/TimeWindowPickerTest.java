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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel.TimeWindowPicker;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TimeWindowPickerTest {

    @Test
    void initialLabelReflectsDefaultWindow() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        TimeWindowPicker picker = new TimeWindowPicker(settings, null);
        assertThat(picker.getText()).isEqualTo("Last 30m");
    }

    @Test
    void labelUpdatesOnPresetChange() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        TimeWindowPicker picker = new TimeWindowPicker(settings, null);
        settings.selectPreset(Window.LAST_5M);
        assertThat(picker.getText()).isEqualTo("Last 5m");
        settings.selectPreset(Window.LAST_1H);
        assertThat(picker.getText()).isEqualTo("Last 1h");
    }

    @Test
    void labelSwitchesToCustomWhenRangeApplied() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        TimeWindowPicker picker = new TimeWindowPicker(settings, null);
        LocalDateTime to = LocalDateTime.of(2026, 5, 19, 12, 0);
        settings.applyCustomRange(to.minusMinutes(45), to);
        assertThat(picker.getText()).startsWith("Custom · 45m");
    }

    @Test
    void clickInvokesProvidedCallback() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        AtomicInteger clicks = new AtomicInteger();
        TimeWindowPicker picker = new TimeWindowPicker(settings, clicks::incrementAndGet);
        picker.click();
        picker.click();
        assertThat(clicks.get()).isEqualTo(2);
    }
}
