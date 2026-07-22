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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.tool.ToolManifest.HumanInTheLoop;
import org.springaicommunity.playground.service.tool.ToolManifest.HumanInTheLoop.Mode;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpServerHitlToolGateTest {

    private static final String LOOPBACK_NAME = "spring-ai-playground-built-in";

    @SuppressWarnings("unchecked")
    private final ObjectProvider<McpClientService> mcpClientServiceProvider = mock(ObjectProvider.class);
    private final McpClientService mcpClientService = mock(McpClientService.class);
    private final McpServerHitlToolGate gate = new McpServerHitlToolGate(mcpClientServiceProvider, new SimpleMeterRegistry());
    private final AtomicInteger delegateCalls = new AtomicInteger();
    private final CallToolResult delegateResult =
            CallToolResult.builder().addTextContent("ok").isError(false).build();

    @BeforeEach
    void setUp() {
        when(mcpClientServiceProvider.getIfAvailable()).thenReturn(mcpClientService);
        when(mcpClientService.selfLoopbackServerName()).thenReturn(LOOPBACK_NAME);
    }

    private final BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> delegate = (exchange, request) -> {
        delegateCalls.incrementAndGet();
        return delegateResult;
    };

    private SyncToolSpecification baseSpec() {
        Tool tool = Tool.builder().name("writeFile").description("writes a file").build();
        return SyncToolSpecification.builder().tool(tool).callHandler(delegate).build();
    }

    private SyncToolSpecification decorated(HumanInTheLoop humanInTheLoop) {
        return decorated(humanInTheLoop, null);
    }

    private SyncToolSpecification decorated(HumanInTheLoop humanInTheLoop, RiskLevel riskLevel) {
        return gate.decorate(baseSpec(), humanInTheLoop, riskLevel);
    }

    private McpSyncServerExchange exchange(String clientName, boolean supportsElicitation) {
        McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
        when(exchange.getClientInfo()).thenReturn(new Implementation(clientName, "1.0.0"));
        when(exchange.getClientCapabilities()).thenReturn(supportsElicitation
                ? ClientCapabilities.builder().elicitation().build()
                : ClientCapabilities.builder().build());
        return exchange;
    }

    private CallToolResult invoke(SyncToolSpecification specification, McpSyncServerExchange exchange) {
        return specification.callHandler().apply(exchange, new CallToolRequest("writeFile", Map.of("path", "/tmp/x")));
    }

    private CallToolResult invokeInteractive(SyncToolSpecification specification, McpSyncServerExchange exchange) {
        CallToolRequest request = CallToolRequest.builder().name("writeFile").arguments(Map.of("path", "/tmp/x"))
                .meta(Map.of(McpServerHitlToolGate.INTERACTIVE_HITL_META_KEY, true)).build();
        return specification.callHandler().apply(exchange, request);
    }

    private static HumanInTheLoop required() {
        return new HumanInTheLoop(Mode.REQUIRED, null);
    }

    @Test
    void externalClientApprovalRunsTool() {
        McpSyncServerExchange exchange = exchange("cursor", true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(1, delegateCalls.get());
        assertSame(delegateResult, result);
    }

    @Test
    void externalClientDeclineBlocksTool() {
        McpSyncServerExchange exchange = exchange("cursor", true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.DECLINE, Map.of()));

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(0, delegateCalls.get());
        assertTrue(result.isError());
        assertTrue(deniedText(result).contains("Do not call"));
    }

    private static String deniedText(CallToolResult result) {
        return ((TextContent) result.content().get(0)).text();
    }

    @Test
    void clientWithoutElicitationCapabilityFailsafeDenies() {
        McpSyncServerExchange exchange = exchange("cursor", false);

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(0, delegateCalls.get());
        assertTrue(result.isError());
        verify(exchange, never()).createElicitation(any());
    }

    @Test
    void loopbackCallerSkipsElicitation() {
        String loopbackClient = "spring-ai-playground - " + LOOPBACK_NAME;
        McpSyncServerExchange exchange = exchange(loopbackClient, true);

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(1, delegateCalls.get());
        assertSame(delegateResult, result);
        verify(exchange, never()).createElicitation(any());
    }

    @Test
    void loopbackInspectorCallWithMetaElicits() {
        String loopbackClient = "spring-ai-playground - " + LOOPBACK_NAME;
        McpSyncServerExchange exchange = exchange(loopbackClient, true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));

        CallToolResult result = invokeInteractive(decorated(required()), exchange);

        assertEquals(1, delegateCalls.get());
        assertSame(delegateResult, result);
        verify(exchange).createElicitation(any());
    }

    @Test
    void inspectorTestClientElicits() {
        String testClient = "spring-ai-playground" + McpClientService.TEST_CLIENT_INFIX + LOOPBACK_NAME;
        McpSyncServerExchange exchange = exchange(testClient, true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(1, delegateCalls.get());
        assertSame(delegateResult, result);
        verify(exchange).createElicitation(any());
    }

    @Test
    void inspectorTestClientDeclineBlocksTool() {
        String testClient = "spring-ai-playground" + McpClientService.TEST_CLIENT_INFIX + LOOPBACK_NAME;
        McpSyncServerExchange exchange = exchange(testClient, true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.DECLINE, Map.of()));

        CallToolResult result = invoke(decorated(required()), exchange);

        assertEquals(0, delegateCalls.get());
        assertTrue(result.isError());
    }

    @Test
    void disabledModeReturnsSpecificationUnchanged() {
        SyncToolSpecification base = baseSpec();
        assertSame(base, gate.decorate(base, new HumanInTheLoop(Mode.DISABLED, null), RiskLevel.L4));
        assertSame(base, gate.decorate(base, null, null));
    }

    @Test
    void elicitationMessageCarriesRiskPrefix() {
        McpSyncServerExchange exchange = exchange("cursor", true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));

        invoke(decorated(required(), RiskLevel.L5), exchange);

        ArgumentCaptor<ElicitRequest> captor = ArgumentCaptor.forClass(ElicitRequest.class);
        verify(exchange).createElicitation(captor.capture());
        assertTrue(captor.getValue().message().startsWith("[risk L5 Critical] "));
    }

    @Test
    void elicitationMessageWithoutLevelHasNoPrefix() {
        McpSyncServerExchange exchange = exchange("cursor", true);
        when(exchange.createElicitation(any())).thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));

        invoke(decorated(required()), exchange);

        ArgumentCaptor<ElicitRequest> captor = ArgumentCaptor.forClass(ElicitRequest.class);
        verify(exchange).createElicitation(captor.capture());
        assertTrue(captor.getValue().message().startsWith("Approve running tool"));
    }

    @Test
    void renderPromptSubstitutesPlaceholders() {
        String rendered = McpServerHitlToolGate.renderPrompt(
                "Run {toolName} with {args}?", "writeFile", Map.of("path", "/tmp/x"));
        assertTrue(rendered.contains("writeFile"));
        assertTrue(rendered.contains("/tmp/x"));
        assertFalse(rendered.contains("{toolName}"));
        assertFalse(rendered.contains("{args}"));
    }

    @Test
    void renderPromptFallsBackToDefaultTemplate() {
        String rendered = McpServerHitlToolGate.renderPrompt(null, "writeFile", Map.of());
        assertTrue(rendered.contains("writeFile"));
    }
}
