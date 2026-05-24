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
package org.springaicommunity.playground.service.mcp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.util.SecretMasking;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class LoggingMcpToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(LoggingMcpToolCallback.class);

    private final ToolCallback delegate;
    private final String serverName;
    private final Set<String> secrets;

    public LoggingMcpToolCallback(ToolCallback delegate, String serverName, Set<String> secrets) {
        this.delegate = delegate;
        this.serverName = serverName;
        this.secrets = secrets;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return this.delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return invoke(() -> this.delegate.call(toolInput));
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        return invoke(() -> this.delegate.call(toolInput, toolContext));
    }

    private String invoke(Supplier<String> action) {
        String cid = UUID.randomUUID().toString().substring(0, 8);
        String toolName = this.delegate.getToolDefinition().name();
        logger.info("mcp.tool.start cid={} server={} tool={} via=chat",
                cid, this.serverName, toolName);
        long startNs = System.nanoTime();
        try {
            String result = action.get();
            long durMs = (System.nanoTime() - startNs) / 1_000_000L;
            logger.info("mcp.tool.done cid={} server={} tool={} durationMs={} via=chat",
                    cid, this.serverName, toolName, durMs);
            return result;
        } catch (RuntimeException e) {
            long durMs = (System.nanoTime() - startNs) / 1_000_000L;
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logger.warn("mcp.tool.crash cid={} server={} tool={} durationMs={} via=chat error={}",
                    cid, this.serverName, toolName, durMs,
                    SecretMasking.mask(msg, this.secrets));
            throw e;
        }
    }
}
