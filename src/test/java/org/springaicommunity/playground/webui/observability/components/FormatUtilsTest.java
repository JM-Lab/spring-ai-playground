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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FormatUtilsTest {

    @Test
    void formatBytesReturnsPlaceholderForZeroOrNegative() {
        assertThat(FormatUtils.formatBytes(0)).isEqualTo("—");
        assertThat(FormatUtils.formatBytes(-1)).isEqualTo("—");
    }

    @Test
    void formatBytesUsesByteUnitBelow1Kib() {
        assertThat(FormatUtils.formatBytes(1)).isEqualTo("1 B");
        assertThat(FormatUtils.formatBytes(1023)).isEqualTo("1023 B");
    }

    @Test
    void formatBytesPromotesToKbAtBoundary() {
        assertThat(FormatUtils.formatBytes(1024)).isEqualTo("1.0 KB");
        assertThat(FormatUtils.formatBytes(1024 * 1024 - 1)).startsWith("1024.0 KB");
    }

    @Test
    void formatBytesPromotesToMbAtBoundary() {
        assertThat(FormatUtils.formatBytes(1024 * 1024)).isEqualTo("1.0 MB");
        assertThat(FormatUtils.formatBytes(1024L * 1024 * 1024 - 1)).startsWith("1024.0 MB");
    }

    @Test
    void formatBytesPromotesToGbAtBoundary() {
        assertThat(FormatUtils.formatBytes(1024L * 1024 * 1024)).isEqualTo("1.00 GB");
    }

    @Test
    void formatBytesPromotesToTbAtBoundary() {
        assertThat(FormatUtils.formatBytes(1024L * 1024 * 1024 * 1024)).isEqualTo("1.00 TB");
        assertThat(FormatUtils.formatBytes(5L * 1024 * 1024 * 1024 * 1024)).isEqualTo("5.00 TB");
    }

    @Test
    void formatPercentReturnsPlaceholderForNegative() {
        assertThat(FormatUtils.formatPercent(-0.01)).isEqualTo("—");
    }

    @Test
    void formatPercentRendersFractionAsPercent() {
        assertThat(FormatUtils.formatPercent(0)).isEqualTo("0.0%");
        assertThat(FormatUtils.formatPercent(0.5)).isEqualTo("50.0%");
        assertThat(FormatUtils.formatPercent(1.0)).isEqualTo("100.0%");
        assertThat(FormatUtils.formatPercent(1.234)).isEqualTo("123.4%");
    }

    @Test
    void formatNumberKeepsRawValueBelow1000() {
        assertThat(FormatUtils.formatNumber(0)).isEqualTo("0");
        assertThat(FormatUtils.formatNumber(999)).isEqualTo("999");
    }

    @Test
    void formatNumberPromotesAtBoundaries() {
        assertThat(FormatUtils.formatNumber(1000)).isEqualTo("1.0k");
        assertThat(FormatUtils.formatNumber(999_999)).isEqualTo("1000.0k");
        assertThat(FormatUtils.formatNumber(1_000_000)).isEqualTo("1.00M");
        assertThat(FormatUtils.formatNumber(999_999_999L)).startsWith("1000.00M");
        assertThat(FormatUtils.formatNumber(1_000_000_000L)).isEqualTo("1.00B");
        assertThat(FormatUtils.formatNumber(1_000_000_000_000L)).isEqualTo("1.00T");
        assertThat(FormatUtils.formatNumber(5_500_000_000L)).isEqualTo("5.50B");
    }

    @Test
    void formatDurationDurationVariantHandlesSecondsToDays() {
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(0))).isEqualTo("0s");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(59))).isEqualTo("59s");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(60))).isEqualTo("1m 0s");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(3600))).isEqualTo("1h 0m");
        assertThat(FormatUtils.formatDuration(Duration.ofSeconds(86400))).isEqualTo("1d 0h");
        assertThat(FormatUtils.formatDuration(Duration.ofHours(25))).isEqualTo("1d 1h");
    }

    @Test
    void formatDurationMsReturnsPlaceholderForNegative() {
        assertThat(FormatUtils.formatDurationMs(-1)).isEqualTo("—");
    }

    @Test
    void formatDurationMsRendersZeroAsMs() {
        assertThat(FormatUtils.formatDurationMs(0)).isEqualTo("0 ms");
    }

    @Test
    void formatDurationMsUsesMsBelow1Second() {
        assertThat(FormatUtils.formatDurationMs(1)).isEqualTo("1 ms");
        assertThat(FormatUtils.formatDurationMs(999)).isEqualTo("999 ms");
    }

    @Test
    void formatDurationMsUsesSecondsBetween1sAnd1Minute() {
        assertThat(FormatUtils.formatDurationMs(1000)).isEqualTo("1.00 s");
        assertThat(FormatUtils.formatDurationMs(2500)).isEqualTo("2.50 s");
        assertThat(FormatUtils.formatDurationMs(59_999)).isEqualTo("60.00 s");
    }

    @Test
    void formatDurationMsPromotesToMinutesAndBeyond() {
        assertThat(FormatUtils.formatDurationMs(60_000)).isEqualTo("1m 0s");
        assertThat(FormatUtils.formatDurationMs(3_600_000)).isEqualTo("1h 0m");
        assertThat(FormatUtils.formatDurationMs(86_400_000L)).isEqualTo("1d 0h");
    }

    @Test
    void formatLatencyMsReturnsPlaceholderForZeroOrNegative() {
        assertThat(FormatUtils.formatLatencyMs(0)).isEqualTo("—");
        assertThat(FormatUtils.formatLatencyMs(-1)).isEqualTo("—");
    }

    @Test
    void formatLatencyMsRendersSubSecondAsMs() {
        assertThat(FormatUtils.formatLatencyMs(123)).isEqualTo("123 ms");
        assertThat(FormatUtils.formatLatencyMs(999.9)).isEqualTo("1000 ms");
    }

    @Test
    void formatLatencyMsRendersSecondsBetween1sAnd1Minute() {
        assertThat(FormatUtils.formatLatencyMs(1000)).isEqualTo("1.00 s");
        assertThat(FormatUtils.formatLatencyMs(59_000)).isEqualTo("59.00 s");
    }

    @Test
    void formatLatencyMsPromotesPastOneMinute() {
        assertThat(FormatUtils.formatLatencyMs(60_000)).isEqualTo("1m 0s");
        assertThat(FormatUtils.formatLatencyMs(3_600_000)).isEqualTo("1h 0m");
    }
}
