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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityGlobalSettingsTest {

    @Test
    void defaultsToLast30MAnd5sRefresh() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        assertThat(s.window()).isEqualTo(Window.LAST_30M);
        assertThat(s.refreshSeconds()).isEqualTo(ObservabilityGlobalSettings.DEFAULT_REFRESH_SECONDS);
        assertThat(s.hasCustomRange()).isFalse();
        assertThat(s.isAutoRefreshOn()).isTrue();
    }

    @Test
    void selectPresetClearsCustomRange() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        s.applyCustomRange(LocalDateTime.now().minusHours(1), LocalDateTime.now());
        assertThat(s.hasCustomRange()).isTrue();

        s.selectPreset(Window.LAST_5M);
        assertThat(s.hasCustomRange()).isFalse();
        assertThat(s.window()).isEqualTo(Window.LAST_5M);
    }

    @Test
    void customRangeClampsToThreeHours() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        LocalDateTime to = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime from = to.minusHours(6);
        s.applyCustomRange(from, to);

        long span = Duration.between(s.customFrom(), s.customTo()).toMinutes();
        assertThat(span).isEqualTo(180);
        assertThat(s.customTo()).isEqualTo(to);
    }

    @Test
    void customRangeSwapsReversedBounds() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        LocalDateTime later = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime earlier = LocalDateTime.of(2026, 5, 19, 10, 0);
        s.applyCustomRange(later, earlier);

        assertThat(s.customFrom()).isEqualTo(earlier);
        assertThat(s.customTo()).isEqualTo(later);
    }

    @Test
    void effectiveWindowReturnsNearestPresetForCustomRange() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        LocalDateTime to = LocalDateTime.of(2026, 5, 19, 12, 0);
        s.applyCustomRange(to.minusMinutes(12), to);

        assertThat(s.effectiveWindow()).isEqualTo(Window.LAST_10M);
    }

    @Test
    void rangeListenerFiresOnPresetChange() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        AtomicInteger calls = new AtomicInteger();
        s.onRangeChanged(calls::incrementAndGet);

        s.selectPreset(Window.LAST_1H);
        s.selectPreset(Window.LAST_1H);
        s.selectPreset(Window.LAST_3H);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void refreshListenerFiresOnIntervalChange() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        AtomicInteger lastValue = new AtomicInteger(-1);
        s.onRefreshIntervalChanged(lastValue::set);

        s.setRefreshSeconds(10);
        s.setRefreshSeconds(10);
        s.setRefreshSeconds(0);

        assertThat(lastValue.get()).isZero();
    }

    @Test
    void isAutoRefreshOffWhenCustomRangeActive() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        s.applyCustomRange(LocalDateTime.now().minusMinutes(30), LocalDateTime.now());
        assertThat(s.isAutoRefreshOn()).isFalse();
    }

    @Test
    void describeWindowReflectsCurrentState() {
        ObservabilityGlobalSettings s = new ObservabilityGlobalSettings();
        s.selectPreset(Window.LAST_1H);
        assertThat(s.describeWindow()).isEqualTo("Last 1h");

        s.applyCustomRange(LocalDateTime.now().minusMinutes(45), LocalDateTime.now());
        assertThat(s.describeWindow()).startsWith("Custom · 45m");
    }

    @Test
    void nearestPresetSnapsToKnownValues() {
        assertThat(ObservabilityGlobalSettings.nearestPreset(7)).isEqualTo(Window.LAST_5M);
        assertThat(ObservabilityGlobalSettings.nearestPreset(8)).isEqualTo(Window.LAST_10M);
        assertThat(ObservabilityGlobalSettings.nearestPreset(45)).isEqualTo(Window.LAST_30M);
        assertThat(ObservabilityGlobalSettings.nearestPreset(150)).isEqualTo(Window.LAST_3H);
    }
}
