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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.mcp.risk.McpCompositionToolCallbackProvider;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "spring.ai.playground.chat.tool-search", name = "enabled", matchIfMissing = true)
public class ToolIndexWarmup implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ToolIndexWarmup.class);

    private final ObjectProvider<ToolIndex> toolIndexProvider;
    private final ToolSpecService toolSpecService;
    private final McpCompositionToolCallbackProvider compositionProvider;

    public ToolIndexWarmup(ObjectProvider<ToolIndex> toolIndexProvider, ToolSpecService toolSpecService,
            McpCompositionToolCallbackProvider compositionProvider) {
        this.toolIndexProvider = toolIndexProvider;
        this.toolSpecService = toolSpecService;
        this.compositionProvider = compositionProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!(this.toolIndexProvider.getIfAvailable() instanceof PersistentToolIndex persistentToolIndex)) {
            return;
        }
        List<ToolReference> references = references();
        if (references.isEmpty()) {
            return;
        }
        try {
            persistentToolIndex.indexTools(ChatMemory.CONVERSATION_ID, references);
            logger.info("Tool index warm-up complete: {} tools", references.size());
        } catch (RuntimeException e) {
            logger.warn("Tool index warm-up skipped, tools will be indexed on first use ({})", e.getMessage());
        }
    }

    List<ToolReference> references() {
        List<ToolReference> references = new ArrayList<>();
        this.toolSpecService.getToolSpecList().stream().filter(spec -> !spec.draft())
                .map(ToolSpec::toolCallback).filter(Objects::nonNull)
                .forEach(callback -> references.add(toToolReference(callback)));
        Arrays.stream(this.compositionProvider.getToolCallbacks())
                .forEach(callback -> references.add(toToolReference(callback)));
        return references;
    }

    private static ToolReference toToolReference(ToolCallback callback) {
        return ToolReference.builder().toolName(callback.getToolDefinition().name())
                .summary(callback.getToolDefinition().description()).build();
    }
}
