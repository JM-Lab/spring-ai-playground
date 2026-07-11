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
package org.springaicommunity.playground.service.mcp;

import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncToolSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.tool.ToolManifest.HumanInTheLoop;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.BiFunction;

@Component
public class McpServerHitlToolGate {

    private static final Logger logger = LoggerFactory.getLogger(McpServerHitlToolGate.class);

    private static final Map<String, Object> CONFIRMATION_SCHEMA = Map.of("type", "object", "properties", Map.of());

    public static final String INTERACTIVE_HITL_META_KEY = "playgroundInteractiveHitl";

    private final ObjectProvider<McpClientService> mcpClientServiceProvider;
    private final MeterRegistry meterRegistry;

    public McpServerHitlToolGate(ObjectProvider<McpClientService> mcpClientServiceProvider,
            MeterRegistry meterRegistry) {
        this.mcpClientServiceProvider = mcpClientServiceProvider;
        this.meterRegistry = meterRegistry;
    }

    public SyncToolSpecification decorate(SyncToolSpecification specification, HumanInTheLoop humanInTheLoop) {
        if (!requiresApproval(humanInTheLoop)) {
            return specification;
        }
        String promptTemplate = humanInTheLoop.promptTemplate();
        BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> delegate = specification.callHandler();
        return SyncToolSpecification.builder()
                .tool(specification.tool())
                .callHandler((exchange, request) -> gate(exchange, request, promptTemplate, delegate))
                .build();
    }

    public AsyncToolSpecification decorate(AsyncToolSpecification specification, HumanInTheLoop humanInTheLoop) {
        if (!requiresApproval(humanInTheLoop)) {
            return specification;
        }
        String promptTemplate = humanInTheLoop.promptTemplate();
        BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> delegate = specification.callHandler();
        return AsyncToolSpecification.builder()
                .tool(specification.tool())
                .callHandler((exchange, request) -> gateAsync(exchange, request, promptTemplate, delegate))
                .build();
    }

    private CallToolResult gate(McpSyncServerExchange exchange, CallToolRequest request, String promptTemplate,
            BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> delegate) {
        if (isChatLoopback(exchange.getClientInfo()) && !isInteractiveCall(request)) {
            return delegate.apply(exchange, request);
        }
        if (!supportsElicitation(exchange.getClientCapabilities())) {
            logger.info("hitl.server.denied tool={} reason=no-elicitation-capability", request.name());
            countDecision("denied");
            return denied(request.name());
        }
        ElicitResult result;
        try {
            result = exchange.createElicitation(elicitRequest(promptTemplate, request));
        } catch (RuntimeException e) {
            logger.warn("hitl.server.elicit-failed tool={} error={}", request.name(), e.getMessage());
            countDecision("elicit-failed");
            return denied(request.name());
        }
        if (!isApproved(result)) {
            logger.info("hitl.server.declined tool={} action={}", request.name(), action(result));
            countDecision("declined");
            return denied(request.name());
        }
        logger.info("hitl.server.approved tool={}", request.name());
        countDecision("approved");
        return delegate.apply(exchange, request);
    }

    private Mono<CallToolResult> gateAsync(McpAsyncServerExchange exchange, CallToolRequest request,
            String promptTemplate, BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> delegate) {
        if (isChatLoopback(exchange.getClientInfo()) && !isInteractiveCall(request)) {
            return delegate.apply(exchange, request);
        }
        if (!supportsElicitation(exchange.getClientCapabilities())) {
            logger.info("hitl.server.denied tool={} reason=no-elicitation-capability", request.name());
            countDecision("denied");
            return Mono.just(denied(request.name()));
        }
        return exchange.createElicitation(elicitRequest(promptTemplate, request))
                .flatMap(result -> {
                    if (!isApproved(result)) {
                        logger.info("hitl.server.declined tool={} action={}", request.name(), action(result));
                        countDecision("declined");
                        return Mono.just(denied(request.name()));
                    }
                    logger.info("hitl.server.approved tool={}", request.name());
                    countDecision("approved");
                    return delegate.apply(exchange, request);
                })
                .onErrorResume(e -> {
                    logger.warn("hitl.server.elicit-failed tool={} error={}", request.name(), e.getMessage());
                    countDecision("elicit-failed");
                    return Mono.just(denied(request.name()));
                });
    }

    private static boolean requiresApproval(HumanInTheLoop humanInTheLoop) {
        return humanInTheLoop != null && humanInTheLoop.mode() == HumanInTheLoop.Mode.REQUIRED;
    }

    // Chat loopback is gated chat-side already, so skip server elicitation to avoid a double prompt.
    private boolean isChatLoopback(Implementation clientInfo) {
        if (clientInfo == null || clientInfo.name() == null) return false;
        String name = clientInfo.name();
        if (name.contains(McpClientService.TEST_CLIENT_INFIX)) return false;
        McpClientService mcpClientService = this.mcpClientServiceProvider.getIfAvailable();
        String self = mcpClientService == null ? null : mcpClientService.selfLoopbackServerName();
        return self != null && name.endsWith(self);
    }

    private static boolean isInteractiveCall(CallToolRequest request) {
        Map<String, Object> meta = request == null ? null : request.meta();
        return meta != null && Boolean.TRUE.equals(meta.get(INTERACTIVE_HITL_META_KEY));
    }

    private static boolean supportsElicitation(ClientCapabilities capabilities) {
        return capabilities != null && capabilities.elicitation() != null;
    }

    private static boolean isApproved(ElicitResult result) {
        return result != null && result.action() == ElicitResult.Action.ACCEPT;
    }

    private static String action(ElicitResult result) {
        return result == null ? "none" : String.valueOf(result.action());
    }

    private static ElicitRequest elicitRequest(String promptTemplate, CallToolRequest request) {
        return ElicitRequest.builder(renderPrompt(promptTemplate, request.name(), request.arguments()),
                CONFIRMATION_SCHEMA).build();
    }

    static String renderPrompt(String promptTemplate, String toolName, Map<String, Object> arguments) {
        String template = (promptTemplate == null || promptTemplate.isBlank())
                ? "Approve running tool '{toolName}' with arguments {args}?" : promptTemplate;
        return template.replace("{toolName}", toolName == null ? "" : toolName)
                .replace("{args}", arguments == null ? "{}" : arguments.toString());
    }

    private static CallToolResult denied(String toolName) {
        return CallToolResult.builder()
                .addTextContent("Tool '" + toolName + "' was not run: human-in-the-loop approval was not granted. "
                        + "Do not call '" + toolName + "' again for this request; ask the user to run it "
                        + "manually if it is still needed.")
                .isError(true)
                .build();
    }

    private void countDecision(String outcome) {
        this.meterRegistry.counter("mcp.hitl.decision", "outcome", outcome, "side", "server").increment();
    }
}
