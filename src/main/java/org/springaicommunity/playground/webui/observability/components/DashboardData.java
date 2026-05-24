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

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class DashboardData {

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private DashboardData() {}

    public static List<String> bucketLabelsFromStarts(List<Long> startsEpochMs) {
        List<String> out = new ArrayList<>(startsEpochMs.size());
        for (Long ms : startsEpochMs) out.add(HHMM.format(Instant.ofEpochMilli(ms)));
        return out;
    }

    public static List<String> bucketLabelsFromWindow(long windowStartEpochMs, long bucketMs, int bucketCount) {
        List<String> out = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            out.add(HHMM.format(Instant.ofEpochMilli(windowStartEpochMs + i * bucketMs)));
        }
        return out;
    }

    public static double percentile(long[] sorted, int pct) {
        if (sorted == null || sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];
        double rank = (pct / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted[lo];
        return sorted[lo] * (1 - (rank - lo)) + sorted[hi] * (rank - lo);
    }

    public static double[] toDouble(long[] arr) {
        double[] out = new double[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i];
        return out;
    }

    public static List<Number> toNumberList(long[] arr) {
        List<Number> out = new ArrayList<>(arr.length);
        for (long v : arr) out.add(v);
        return out;
    }

    public static List<Number> toNumberListD(double[] arr) {
        List<Number> out = new ArrayList<>(arr.length);
        for (double v : arr) out.add(v);
        return out;
    }

    public static long sum(long[] arr) {
        long s = 0;
        for (long v : arr) s += v;
        return s;
    }
}
