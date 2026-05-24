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

public enum Window {
    LAST_1M(1),
    LAST_5M(5),
    LAST_10M(10),
    LAST_20M(20),
    LAST_30M(30),
    LAST_1H(60),
    LAST_3H(180);

    public static final long BUCKET_MS = 60_000L;

    public final int minutes;

    Window(int minutes) {
        this.minutes = minutes;
    }

    public int buckets() {
        return minutes;
    }

    public long bucketMs() {
        return BUCKET_MS;
    }

    public long totalMs() {
        return (long) minutes * BUCKET_MS;
    }
}
