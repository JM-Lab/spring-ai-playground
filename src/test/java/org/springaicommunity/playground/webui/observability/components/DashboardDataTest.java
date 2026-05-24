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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DashboardDataTest {

    private static long epochMs(int hour, int minute) {
        return LocalDateTime.of(2026, 5, 21, hour, minute)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Test
    void bucketLabelsFromStartsFormatsEachEpochAsHourMinute() {
        List<Long> starts = List.of(epochMs(9, 30), epochMs(9, 45), epochMs(10, 0));
        assertThat(DashboardData.bucketLabelsFromStarts(starts)).containsExactly("09:30", "09:45", "10:00");
    }

    @Test
    void bucketLabelsFromStartsHandlesEmpty() {
        assertThat(DashboardData.bucketLabelsFromStarts(List.of())).isEmpty();
    }

    @Test
    void bucketLabelsFromWindowProducesEvenlySpacedLabels() {
        long start = epochMs(8, 0);
        long fifteenMin = 15 * 60_000L;
        assertThat(DashboardData.bucketLabelsFromWindow(start, fifteenMin, 4))
                .containsExactly("08:00", "08:15", "08:30", "08:45");
    }

    @Test
    void bucketLabelsFromWindowWithZeroBucketsReturnsEmpty() {
        assertThat(DashboardData.bucketLabelsFromWindow(Instant.now().toEpochMilli(), 60_000L, 0)).isEmpty();
    }

    @Test
    void bucketLabelsFromWindowSingleBucketReturnsStartLabel() {
        assertThat(DashboardData.bucketLabelsFromWindow(epochMs(11, 22), 60_000L, 1))
                .containsExactly("11:22");
    }

    @Test
    void percentileOnEmptyArrayIsZero() {
        assertThat(DashboardData.percentile(new long[0], 50)).isZero();
    }

    @Test
    void percentileOnNullIsZero() {
        assertThat(DashboardData.percentile(null, 95)).isZero();
    }

    @Test
    void percentileOnSingleValueReturnsThatValue() {
        assertThat(DashboardData.percentile(new long[] { 42 }, 50)).isEqualTo(42d);
        assertThat(DashboardData.percentile(new long[] { 42 }, 99)).isEqualTo(42d);
    }

    @Test
    void percentileMedianInterpolatesBetweenAdjacentValues() {
        long[] sorted = { 10, 20, 30, 40 };
        assertThat(DashboardData.percentile(sorted, 50)).isCloseTo(25.0, within(0.01));
    }

    @Test
    void percentileMedianOnOddLengthReturnsMiddleValue() {
        long[] sorted = { 10, 20, 30, 40, 50 };
        assertThat(DashboardData.percentile(sorted, 50)).isEqualTo(30d);
    }

    @Test
    void percentileZeroReturnsMinAndHundredReturnsMax() {
        long[] sorted = { 100, 200, 300 };
        assertThat(DashboardData.percentile(sorted, 0)).isEqualTo(100d);
        assertThat(DashboardData.percentile(sorted, 100)).isEqualTo(300d);
    }

    @Test
    void percentile95Interpolates() {
        long[] sorted = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
        assertThat(DashboardData.percentile(sorted, 95)).isCloseTo(10.5, within(0.01));
    }

    @Test
    void toDoubleConvertsLongs() {
        double[] out = DashboardData.toDouble(new long[] { 1L, 2L, 3L });
        assertThat(out).containsExactly(1.0, 2.0, 3.0);
    }

    @Test
    void toNumberListWrapsLongs() {
        assertThat(DashboardData.toNumberList(new long[] { 5L, 10L }))
                .containsExactly(5L, 10L);
    }

    @Test
    void toNumberListDWrapsDoubles() {
        assertThat(DashboardData.toNumberListD(new double[] { 1.5, 2.5 }))
                .containsExactly(1.5, 2.5);
    }

    @Test
    void sumAddsLongs() {
        assertThat(DashboardData.sum(new long[] { 1, 2, 3, 4 })).isEqualTo(10L);
        assertThat(DashboardData.sum(new long[0])).isZero();
    }
}
