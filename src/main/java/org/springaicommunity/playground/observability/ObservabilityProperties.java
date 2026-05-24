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

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.playground.observability")
public class ObservabilityProperties {

    private int ringBufferCapacity = 2000;
    private int retainDays = 30;
    private boolean persist = true;
    private int maxSpansPerTrace = 200;
    private boolean capturePromptContent = true;
    private int maxPromptContentBytes = 4096;
    private int maxCapturedMessagesPerSpan = 16;
    private int activeTraceTtlSeconds = 300;

    public int getRingBufferCapacity() {
        return ringBufferCapacity;
    }

    public void setRingBufferCapacity(int ringBufferCapacity) {
        this.ringBufferCapacity = ringBufferCapacity;
    }

    public int getRetainDays() {
        return retainDays;
    }

    public void setRetainDays(int retainDays) {
        this.retainDays = retainDays;
    }

    public boolean isPersist() {
        return persist;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public int getMaxSpansPerTrace() {
        return maxSpansPerTrace;
    }

    public void setMaxSpansPerTrace(int maxSpansPerTrace) {
        this.maxSpansPerTrace = maxSpansPerTrace;
    }

    public boolean isCapturePromptContent() {
        return capturePromptContent;
    }

    public void setCapturePromptContent(boolean capturePromptContent) {
        this.capturePromptContent = capturePromptContent;
    }

    public int getMaxPromptContentBytes() {
        return maxPromptContentBytes;
    }

    public void setMaxPromptContentBytes(int maxPromptContentBytes) {
        this.maxPromptContentBytes = maxPromptContentBytes;
    }

    public int getMaxCapturedMessagesPerSpan() {
        return maxCapturedMessagesPerSpan;
    }

    public void setMaxCapturedMessagesPerSpan(int maxCapturedMessagesPerSpan) {
        this.maxCapturedMessagesPerSpan = maxCapturedMessagesPerSpan;
    }

    public int getActiveTraceTtlSeconds() {
        return activeTraceTtlSeconds;
    }

    public void setActiveTraceTtlSeconds(int activeTraceTtlSeconds) {
        this.activeTraceTtlSeconds = activeTraceTtlSeconds;
    }
}
