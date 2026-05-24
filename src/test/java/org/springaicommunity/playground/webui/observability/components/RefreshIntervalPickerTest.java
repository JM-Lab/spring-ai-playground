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

import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.webui.observability.components.ObservabilitySettingsPanel.RefreshIntervalPicker;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshIntervalPickerTest {

    private Button intervalButton(RefreshIntervalPicker picker) {
        return picker.getChildren()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .skip(1)
                .findFirst()
                .orElseThrow();
    }

    private Button refreshNowButton(RefreshIntervalPicker picker) {
        return picker.getChildren()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void initialLabelMatchesDefaultInterval() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        RefreshIntervalPicker picker = new RefreshIntervalPicker(settings, null, null);
        assertThat(intervalButton(picker).getText()).isEqualTo("5s");
    }

    @Test
    void labelTracksIntervalChanges() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        RefreshIntervalPicker picker = new RefreshIntervalPicker(settings, null, null);

        settings.setRefreshSeconds(10);
        assertThat(intervalButton(picker).getText()).isEqualTo("10s");

        settings.setRefreshSeconds(60);
        assertThat(intervalButton(picker).getText()).isEqualTo("1m");

        settings.setRefreshSeconds(0);
        assertThat(intervalButton(picker).getText()).isEqualTo("Off");
    }

    @Test
    void intervalButtonDisabledWhileCustomRangeActive() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        RefreshIntervalPicker picker = new RefreshIntervalPicker(settings, null, null);
        assertThat(intervalButton(picker).isEnabled()).isTrue();

        settings.applyCustomRange(LocalDateTime.now().minusMinutes(20), LocalDateTime.now());
        assertThat(intervalButton(picker).isEnabled()).isFalse();

        settings.selectPreset(Window.LAST_30M);
        assertThat(intervalButton(picker).isEnabled()).isTrue();
    }

    @Test
    void refreshNowClickInvokesCallback() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        AtomicInteger refreshes = new AtomicInteger();
        RefreshIntervalPicker picker = new RefreshIntervalPicker(settings, null, refreshes::incrementAndGet);
        refreshNowButton(picker).click();
        refreshNowButton(picker).click();
        assertThat(refreshes.get()).isEqualTo(2);
    }

    @Test
    void intervalButtonClickInvokesSettingsCallback() {
        ObservabilityGlobalSettings settings = new ObservabilityGlobalSettings();
        AtomicInteger opens = new AtomicInteger();
        RefreshIntervalPicker picker = new RefreshIntervalPicker(settings, opens::incrementAndGet, null);
        intervalButton(picker).click();
        assertThat(opens.get()).isEqualTo(1);
    }
}
