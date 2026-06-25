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
package org.springaicommunity.playground.service.chat;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.playground.service.mcp.risk.McpCompositionToolCallbackProvider;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolIndexWarmupTest {

    private final ToolSpecService toolSpecService = mock(ToolSpecService.class);
    private final McpCompositionToolCallbackProvider compositionProvider =
            mock(McpCompositionToolCallbackProvider.class);

    @SuppressWarnings("unchecked")
    private ToolIndexWarmup warmup(ToolIndex toolIndex) {
        ObjectProvider<ToolIndex> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(toolIndex);
        return new ToolIndexWarmup(provider, this.toolSpecService, this.compositionProvider);
    }

    private static ToolCallback tool(String name) {
        return FunctionToolCallback.builder(name, (Function<Map<String, Object>, Object>) arguments -> "ok")
                .description(name + " description")
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {}).build();
    }

    private static ToolSpec spec(String name, boolean draft) {
        return new ToolSpec(name, name, name + " description", null, null, null, null, tool(name)).withDraft(draft);
    }

    @Test
    void referencesExcludeDraftSpecsAndIncludeComposition() {
        when(this.toolSpecService.getToolSpecList())
                .thenReturn(List.of(spec("getWeather", false), spec("draftTool", true)));
        when(this.compositionProvider.getToolCallbacks()).thenReturn(new ToolCallback[] {tool("composedTool")});

        List<String> names = warmup(null).references().stream().map(ToolReference::toolName).toList();

        assertThat(names).containsExactly("getWeather", "composedTool");
    }

    @Test
    @SuppressWarnings("unchecked")
    void warmsUpThePersistentIndexWithThePool() {
        when(this.toolSpecService.getToolSpecList()).thenReturn(List.of(spec("getWeather", false)));
        when(this.compositionProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        PersistentToolIndex index = mock(PersistentToolIndex.class);

        warmup(index).run(null);

        ArgumentCaptor<List<ToolReference>> captor = ArgumentCaptor.forClass(List.class);
        verify(index).indexTools(eq(ChatMemory.CONVERSATION_ID), captor.capture());
        assertThat(captor.getValue()).extracting(ToolReference::toolName).containsExactly("getWeather");
    }

    @Test
    void skipsWhenIndexIsNotThePersistentKind() {
        ToolIndex other = mock(ToolIndex.class);

        warmup(other).run(null);

        verify(other, never()).indexTools(any(), any());
        verifyNoInteractions(this.toolSpecService, this.compositionProvider);
    }

    @Test
    void skipsWhenThePoolIsEmpty() {
        when(this.toolSpecService.getToolSpecList()).thenReturn(List.of());
        when(this.compositionProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);
        PersistentToolIndex index = mock(PersistentToolIndex.class);

        warmup(index).run(null);

        verify(index, never()).indexTools(any(), any());
    }
}
