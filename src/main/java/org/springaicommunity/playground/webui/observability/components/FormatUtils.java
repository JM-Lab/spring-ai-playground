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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class FormatUtils {

    private FormatUtils() {}

    private static final long KIB = 1024L;
    private static final long MIB = KIB * 1024;
    private static final long GIB = MIB * 1024;
    private static final long TIB = GIB * 1024;

    public static String formatBytes(double bytes) {
        if (bytes <= 0) return "—";
        if (bytes < KIB) return String.format(Locale.ROOT, "%.0f B", bytes);
        if (bytes < MIB) return String.format(Locale.ROOT, "%.1f KB", bytes / KIB);
        if (bytes < GIB) return String.format(Locale.ROOT, "%.1f MB", bytes / (double) MIB);
        if (bytes < TIB) return String.format(Locale.ROOT, "%.2f GB", bytes / (double) GIB);
        return String.format(Locale.ROOT, "%.2f TB", bytes / (double) TIB);
    }

    public static String formatPercent(double v) {
        if (v < 0) return "—";
        return String.format(Locale.ROOT, "%.1f%%", v * 100);
    }

    public static String formatNumber(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1_000_000) return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        if (n < 1_000_000_000L) return String.format(Locale.ROOT, "%.2fM", n / 1_000_000.0);
        if (n < 1_000_000_000_000L) return String.format(Locale.ROOT, "%.2fB", n / 1_000_000_000.0);
        return String.format(Locale.ROOT, "%.2fT", n / 1_000_000_000_000.0);
    }

    public static String formatDuration(Duration d) {
        long s = d.getSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        if (s < 86400) return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
        return (s / 86400) + "d " + ((s % 86400) / 3600) + "h";
    }

    public static String formatDurationMs(long ms) {
        if (ms < 0) return "—";
        if (ms < 1000) return ms + " ms";
        double s = ms / 1000.0;
        if (s < 60) return String.format(Locale.ROOT, "%.2f s", s);
        long si = (long) s;
        if (si < 3600) return (si / 60) + "m " + (si % 60) + "s";
        if (si < 86400) return (si / 3600) + "h " + ((si % 3600) / 60) + "m";
        return (si / 86400) + "d " + ((si % 86400) / 3600) + "h";
    }

    public static String formatLatencyMs(double ms) {
        if (ms <= 0) return "—";
        if (ms < 1000) return String.format(Locale.ROOT, "%.0f ms", ms);
        if (ms < 60_000) return String.format(Locale.ROOT, "%.2f s", ms / 1000.0);
        long s = (long) (ms / 1000);
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }

    public static Map<String, Long> secondsAsMillisMap(Map<String, Double> src) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : src.entrySet()) out.put(e.getKey(), (long) (e.getValue() * 1000));
        return out;
    }
}
