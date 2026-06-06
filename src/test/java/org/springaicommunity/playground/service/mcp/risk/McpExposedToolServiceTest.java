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

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolSpecService.ExposureMode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpExposedToolServiceTest {

    private ToolCallback callback(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    @Test
    void syncAddsMissingAndRemovesStaleExternalTools() {
        McpCompositionService compositionService = mock(McpCompositionService.class);
        McpCompositionToolCallbackProvider provider = mock(McpCompositionToolCallbackProvider.class);
        ToolSpecService toolSpecService = mock(ToolSpecService.class);

        McpComposition exposed = new McpComposition(McpCompositionService.EXPOSED_ID, "Exposed Tools",
                "", List.of(), true, RiskLevel.L5, 0L, 0L, 0L);
        ToolCallback freshTool = callback("x");
        ToolCallback alreadyExposed = callback("y");
        when(toolSpecService.exposureMode()).thenReturn(ExposureMode.BOTH);
        when(compositionService.getExposed()).thenReturn(Optional.of(exposed));
        when(provider.getToolCallbacksFor(exposed)).thenReturn(new ToolCallback[]{freshTool, alreadyExposed});
        when(toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("y", "z"));

        new McpExposedToolService(compositionService, provider, toolSpecService,
                mock(McpToolHashLedger.class)).sync();

        verify(toolSpecService).removeExternalMcpTool("z");
        verify(toolSpecService, never()).removeExternalMcpTool("y");
        verify(toolSpecService).addExternalMcpTool(freshTool, false);
        verify(toolSpecService, never()).addExternalMcpTool(alreadyExposed, false);
    }

    @Test
    void syncPropagatesHitlFlagFromMember() {
        McpCompositionService compositionService = mock(McpCompositionService.class);
        McpCompositionToolCallbackProvider provider = mock(McpCompositionToolCallbackProvider.class);
        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        when(toolSpecService.exposureMode()).thenReturn(ExposureMode.BOTH);

        McpComposition.Member member = new McpComposition.Member("srv", "tool", "x", null, true, "");
        McpComposition exposed = new McpComposition(McpCompositionService.EXPOSED_ID, "Exposed Tools",
                "", List.of(member), true, RiskLevel.L5, 0L, 0L, 0L);
        ToolCallback hitlTool = callback("x");
        when(compositionService.getExposed()).thenReturn(Optional.of(exposed));
        when(provider.getToolCallbacksFor(exposed)).thenReturn(new ToolCallback[]{hitlTool});
        when(toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of());

        new McpExposedToolService(compositionService, provider, toolSpecService,
                mock(McpToolHashLedger.class)).sync();

        verify(toolSpecService).addExternalMcpTool(hitlTool, true);
    }

    @Test
    void syncReregistersExternalToolWhenHitlToggled() {
        McpCompositionService compositionService = mock(McpCompositionService.class);
        McpCompositionToolCallbackProvider provider = mock(McpCompositionToolCallbackProvider.class);
        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        when(toolSpecService.exposureMode()).thenReturn(ExposureMode.BOTH);

        McpComposition.Member member = new McpComposition.Member("srv", "tool", "x", null, true, "");
        McpComposition exposed = new McpComposition(McpCompositionService.EXPOSED_ID, "Exposed Tools",
                "", List.of(member), true, RiskLevel.L5, 0L, 0L, 0L);
        ToolCallback tool = callback("x");
        when(compositionService.getExposed()).thenReturn(Optional.of(exposed));
        when(provider.getToolCallbacksFor(exposed)).thenReturn(new ToolCallback[]{tool});
        when(toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("x"), Set.of());
        when(toolSpecService.humanInTheLoopFor("x")).thenReturn(null);

        new McpExposedToolService(compositionService, provider, toolSpecService,
                mock(McpToolHashLedger.class)).sync();

        verify(toolSpecService).removeExternalMcpTool("x");
        verify(toolSpecService).addExternalMcpTool(tool, true);
    }

    @Test
    void syncWithNoExposedSetRemovesAllExternalTools() {
        McpCompositionService compositionService = mock(McpCompositionService.class);
        McpCompositionToolCallbackProvider provider = mock(McpCompositionToolCallbackProvider.class);
        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        when(toolSpecService.exposureMode()).thenReturn(ExposureMode.BOTH);
        when(compositionService.getExposed()).thenReturn(Optional.empty());
        when(toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("a", "b"));

        new McpExposedToolService(compositionService, provider, toolSpecService,
                mock(McpToolHashLedger.class)).sync();

        verify(toolSpecService).removeExternalMcpTool("a");
        verify(toolSpecService).removeExternalMcpTool("b");
    }

    @Test
    void syncWithComposedDisabledRemovesAllExternalToolsAndSkipsProvider() {
        McpCompositionService compositionService = mock(McpCompositionService.class);
        McpCompositionToolCallbackProvider provider = mock(McpCompositionToolCallbackProvider.class);
        ToolSpecService toolSpecService = mock(ToolSpecService.class);
        when(toolSpecService.exposureMode()).thenReturn(ExposureMode.BUILTIN_ONLY);
        when(toolSpecService.getExternalMcpToolNames()).thenReturn(Set.of("p", "q"));

        new McpExposedToolService(compositionService, provider, toolSpecService,
                mock(McpToolHashLedger.class)).sync();

        verify(toolSpecService).removeExternalMcpTool("p");
        verify(toolSpecService).removeExternalMcpTool("q");
        verify(provider, never()).getToolCallbacksFor(any());
        verify(toolSpecService, never()).addExternalMcpTool(any(), anyBoolean());
    }
}
