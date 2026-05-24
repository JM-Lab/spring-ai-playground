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
package org.springaicommunity.playground.observability;

public abstract class BucketedTimeSeries<T, S> implements WindowedTimeSeries<S> {

    @Override
    public final S compute(Window window, long nowEpochMs) {
        long bucketMs = window.bucketMs();
        long windowStart = nowEpochMs - window.totalMs();
        int buckets = window.buckets();

        Aggregator<T, S> agg = aggregator(window, windowStart, bucketMs, buckets);
        for (T item : sourceSince(windowStart)) {
            long ts = timestampOf(item);
            if (ts < windowStart) continue;
            int bucket = bucketIndex(ts, windowStart, bucketMs, buckets);
            agg.accept(item, bucket);
        }
        return agg.build();
    }

    protected abstract Iterable<T> sourceSince(long windowStartMs);

    protected abstract long timestampOf(T item);

    protected abstract Aggregator<T, S> aggregator(Window window, long windowStartMs, long bucketMs, int buckets);

    protected static int bucketIndex(long ts, long windowStartMs, long bucketMs, int totalBuckets) {
        return (int) Math.min(totalBuckets - 1, Math.max(0, (ts - windowStartMs) / bucketMs));
    }

    protected interface Aggregator<T, S> {
        void accept(T item, int bucket);
        S build();
    }
}
