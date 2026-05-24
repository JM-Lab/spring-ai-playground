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

import org.springaicommunity.playground.observability.Window;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ObservabilityGlobalSettings {

    public static final int MAX_CUSTOM_RANGE_MINUTES = 180;
    public static final int DEFAULT_REFRESH_SECONDS = 5;
    public static final int REFRESH_OFF = 0;
    public static final int[] REFRESH_CHOICES = {REFRESH_OFF, 1, 2, 5, 10, 30, 60};
    public static final Window[] WINDOW_PRESETS = {
            Window.LAST_5M, Window.LAST_10M, Window.LAST_20M,
            Window.LAST_30M, Window.LAST_1H, Window.LAST_3H
    };

    private Window window = Window.LAST_30M;
    private LocalDateTime customFrom;
    private LocalDateTime customTo;
    private int refreshSeconds = DEFAULT_REFRESH_SECONDS;

    private final List<Runnable> rangeListeners = new ArrayList<>();
    private final List<Consumer<Integer>> refreshListeners = new ArrayList<>();

    public Window window() {
        return window;
    }

    public boolean hasCustomRange() {
        return customFrom != null || customTo != null;
    }

    public LocalDateTime customFrom() {
        return customFrom;
    }

    public LocalDateTime customTo() {
        return customTo;
    }

    public int refreshSeconds() {
        return refreshSeconds;
    }

    public boolean isAutoRefreshOn() {
        return refreshSeconds > 0 && !hasCustomRange();
    }

    public void selectPreset(Window preset) {
        if (preset == null) return;
        boolean changed = preset != this.window || hasCustomRange();
        this.window = preset;
        this.customFrom = null;
        this.customTo = null;
        if (changed) fireRangeChanged();
    }

    public void applyCustomRange(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            clearCustomRange();
            return;
        }
        LocalDateTime effectiveFrom = from;
        LocalDateTime effectiveTo = to == null ? LocalDateTime.now() : to;
        if (effectiveFrom == null) {
            effectiveFrom = effectiveTo.minusMinutes(window.minutes);
        }
        if (effectiveFrom.isAfter(effectiveTo)) {
            LocalDateTime swap = effectiveFrom;
            effectiveFrom = effectiveTo;
            effectiveTo = swap;
        }
        Duration span = Duration.between(effectiveFrom, effectiveTo);
        if (span.toMinutes() > MAX_CUSTOM_RANGE_MINUTES) {
            effectiveFrom = effectiveTo.minusMinutes(MAX_CUSTOM_RANGE_MINUTES);
        }
        this.customFrom = effectiveFrom;
        this.customTo = effectiveTo;
        fireRangeChanged();
    }

    public void clearCustomRange() {
        if (!hasCustomRange()) return;
        this.customFrom = null;
        this.customTo = null;
        fireRangeChanged();
    }

    public void setRefreshSeconds(int seconds) {
        int normalized = Math.max(0, seconds);
        if (normalized == refreshSeconds) return;
        this.refreshSeconds = normalized;
        for (Consumer<Integer> l : refreshListeners) l.accept(normalized);
    }

    public Window effectiveWindow() {
        if (!hasCustomRange()) return window;
        long minutes = Duration.between(customFrom, customTo).toMinutes();
        return nearestPreset(minutes);
    }

    public static Window nearestPreset(long minutes) {
        Window best = WINDOW_PRESETS[0];
        long bestDelta = Long.MAX_VALUE;
        for (Window w : WINDOW_PRESETS) {
            long d = Math.abs(w.minutes - minutes);
            if (d < bestDelta) {
                bestDelta = d;
                best = w;
            }
        }
        return best;
    }

    public String describeWindow() {
        if (hasCustomRange()) {
            long min = Duration.between(customFrom, customTo).toMinutes();
            return "Custom · " + formatDuration(min);
        }
        return "Last " + formatDuration(window.minutes);
    }

    public static String formatDuration(long minutes) {
        if (minutes < 60) return minutes + "m";
        if (minutes % 60 == 0) return (minutes / 60) + "h";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    public void onRangeChanged(Runnable listener) {
        if (listener != null) rangeListeners.add(listener);
    }

    public void onRefreshIntervalChanged(Consumer<Integer> listener) {
        if (listener != null) refreshListeners.add(listener);
    }

    private void fireRangeChanged() {
        for (Runnable l : rangeListeners) l.run();
    }
}
