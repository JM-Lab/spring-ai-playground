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
package org.springaicommunity.playground.observability.ollama;

import org.springaicommunity.playground.observability.BoundedRingBuffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OllamaMetricsRingBuffer extends BoundedRingBuffer<OllamaMetricsRingBuffer.Sample> {

    public record Sample(long epochMs, boolean reachable, int runningModels,
            long totalVramBytes, int installedModels) {}

    public OllamaMetricsRingBuffer(
            @Value("${spring.ai.playground.observability.ollama-metrics-capacity:2400}")
            int capacity) {
        super(capacity, 60);
    }

    public void record(boolean reachable, int runningModels, long totalVramBytes, int installedModels) {
        addWithEviction(new Sample(System.currentTimeMillis(), reachable, runningModels,
                totalVramBytes, installedModels));
    }

    public List<Sample> snapshotSince(long sinceEpochMs) {
        List<Sample> out = new ArrayList<>();
        for (Sample s : buffer) {
            if (s.epochMs() >= sinceEpochMs) out.add(s);
        }
        return out;
    }
}
