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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.util.SecretMasking;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class WrappedExternalToolCallback implements ToolCallback {

    private static final Logger logger = LoggerFactory.getLogger(WrappedExternalToolCallback.class);

    private final ToolCallback delegate;
    private final String compositionId;
    private final String compositionName;
    private final String exposedAlias;
    private final String upstreamServerId;
    private final String upstreamTransport;
    private final RiskLevel finalRiskLevel;
    private final RiskLevel serverRiskLevel;
    private final RiskLevel publishRiskLevel;
    private final String floorTrigger;
    private final Set<String> secrets;
    private final ToolDefinition aliasedDefinition;

    public WrappedExternalToolCallback(ToolCallback delegate, String compositionId, String compositionName,
            String exposedAlias, String upstreamServerId, String upstreamTransport,
            McpToolRiskComposer.Composed risk, Set<String> secrets) {
        this(delegate, compositionId, compositionName, exposedAlias, null, upstreamServerId, upstreamTransport,
                risk, secrets);
    }

    public WrappedExternalToolCallback(ToolCallback delegate, String compositionId, String compositionName,
            String exposedAlias, String descriptionOverride, String upstreamServerId, String upstreamTransport,
            McpToolRiskComposer.Composed risk, Set<String> secrets) {
        this.delegate = delegate;
        this.compositionId = compositionId;
        this.compositionName = compositionName;
        this.exposedAlias = exposedAlias;
        this.upstreamServerId = upstreamServerId;
        this.upstreamTransport = upstreamTransport;
        this.finalRiskLevel = risk.finalLevel();
        this.serverRiskLevel = risk.serverLevel();
        this.publishRiskLevel = risk.publishLevel();
        this.floorTrigger = risk.floorTrigger();
        this.secrets = secrets;
        this.aliasedDefinition = buildAliasedDefinition(delegate.getToolDefinition(), exposedAlias, descriptionOverride);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.aliasedDefinition;
    }

    public RiskLevel riskLevel() {
        return this.finalRiskLevel;
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
        McpInvocationContext context = McpInvocationContext.forWrappedExternal(cid,
                this.compositionId, this.compositionName, this.exposedAlias,
                this.upstreamServerId, this.delegate.getToolDefinition().name(), this.upstreamTransport,
                this.finalRiskLevel, this.serverRiskLevel, this.publishRiskLevel, this.floorTrigger);
        Map<String, String> previousMdc = context.pushMdc();
        long startNs = System.nanoTime();
        try {
            logger.info("mcp.tool.start cid={} via={} origin={} composition={} alias={} upstream={} "
                            + "upstreamTool={} risk={}",
                    cid, context.via(), context.origin(),
                    this.compositionName, this.exposedAlias, this.upstreamServerId,
                    this.delegate.getToolDefinition().name(), this.finalRiskLevel);
            String result = action.get();
            long durMs = (System.nanoTime() - startNs) / 1_000_000L;
            logger.info("mcp.tool.done cid={} via={} alias={} durationMs={} risk={}",
                    cid, context.via(), this.exposedAlias, durMs, this.finalRiskLevel);
            return result;
        } catch (RuntimeException e) {
            long durMs = (System.nanoTime() - startNs) / 1_000_000L;
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            logger.warn("mcp.tool.crash cid={} via={} alias={} durationMs={} risk={} error={}",
                    cid, context.via(), this.exposedAlias, durMs, this.finalRiskLevel,
                    SecretMasking.mask(msg, this.secrets));
            throw e;
        } finally {
            McpInvocationContext.popMdc(previousMdc);
        }
    }

    // Input schema is always passed through unchanged so the upstream contract stays intact.
    static ToolDefinition buildAliasedDefinition(ToolDefinition source, String alias, String descriptionOverride) {
        if (source == null) return null;
        String name = (alias == null || alias.isBlank()) ? source.name() : alias;
        String description = (descriptionOverride == null || descriptionOverride.isBlank())
                ? source.description() : descriptionOverride;
        if (name.equals(source.name()) && Objects.equals(description, source.description())) {
            return source;
        }
        return DefaultToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(source.inputSchema())
                .build();
    }
}
