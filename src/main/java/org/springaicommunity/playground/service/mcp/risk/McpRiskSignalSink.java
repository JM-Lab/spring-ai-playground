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
package org.springaicommunity.playground.service.mcp.risk;

public interface McpRiskSignalSink {

    void onServerRiskComputed(McpRiskEvents.ServerRiskComputed event);

    void onToolPublishRiskComputed(McpRiskEvents.ToolPublishRiskComputed event);

    void onFloorOverrideTriggered(McpRiskEvents.FloorOverrideTriggered event);

    void onHashLedgerMismatch(McpRiskEvents.HashLedgerMismatch event);

    void onCompositionLifecycle(McpRiskEvents.CompositionLifecycle event);

    void onPoisoningHit(McpRiskEvents.PoisoningHit event);

    McpRiskSignalSink NOOP = new McpRiskSignalSink() {
        @Override public void onServerRiskComputed(McpRiskEvents.ServerRiskComputed event) {}
        @Override public void onToolPublishRiskComputed(McpRiskEvents.ToolPublishRiskComputed event) {}
        @Override public void onFloorOverrideTriggered(McpRiskEvents.FloorOverrideTriggered event) {}
        @Override public void onHashLedgerMismatch(McpRiskEvents.HashLedgerMismatch event) {}
        @Override public void onCompositionLifecycle(McpRiskEvents.CompositionLifecycle event) {}
        @Override public void onPoisoningHit(McpRiskEvents.PoisoningHit event) {}
    };
}
