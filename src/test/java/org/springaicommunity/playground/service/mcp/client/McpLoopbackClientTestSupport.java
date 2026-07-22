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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class McpLoopbackClientTestSupport {

    private static final String SERVER_NAME = "playground-loopback";
    private static final String EVAL_EXPRESSION_TOOL_ID = "6dea1efa-8a21-5522-ac08-4ede8fd9ff84";
    private static final String RESOURCE_URI = "test://loopback/greeting";
    private static final String NOTIFIER_RESOURCE_URI = "test://loopback/notifier";
    private static final String ELICIT_RESOURCE_URI = "test://loopback/elicit";
    private static final String SAMPLING_RESOURCE_URI = "test://loopback/sampling";
    private static final String PROMPT_NAME = "loopback-greeting";
    private static final String ROOT_URI = "file:///tmp/loopback-root";

    @TestConfiguration(proxyBeanMethods = false)
    public static class LoopbackServerFixtures {

        @Bean
        List<McpServerFeatures.SyncResourceSpecification> loopbackResourceSpecifications() {
            return List.of(
                    new McpServerFeatures.SyncResourceSpecification(textResource(RESOURCE_URI, "loopback-greeting"),
                            (exchange, request) -> textResult(RESOURCE_URI, "hello from loopback")),
                    new McpServerFeatures.SyncResourceSpecification(
                            textResource(NOTIFIER_RESOURCE_URI, "loopback-notifier"), (exchange, request) -> {
                                exchange.loggingNotification(McpSchema.LoggingMessageNotification.builder()
                                        .level(McpSchema.LoggingLevel.INFO)
                                        .logger("loopback")
                                        .data("loopback log line")
                                        .build());
                                exchange.progressNotification(
                                        new McpSchema.ProgressNotification("loopback", 0.5, 1.0, "halfway"));
                                return textResult(NOTIFIER_RESOURCE_URI, "notified");
                            }),
                    new McpServerFeatures.SyncResourceSpecification(
                            textResource(ELICIT_RESOURCE_URI, "loopback-elicit"), (exchange, request) -> {
                                McpSchema.ElicitResult decision = exchange.createElicitation(
                                        McpSchema.ElicitFormRequest.builder("Approve loopback read?",
                                                Map.of("type", "object")).build());
                                return textResult(ELICIT_RESOURCE_URI, "elicit:" + decision.action());
                            }),
                    new McpServerFeatures.SyncResourceSpecification(
                            textResource(SAMPLING_RESOURCE_URI, "loopback-sampling"), (exchange, request) -> {
                                McpSchema.CreateMessageResult answer = exchange.createMessage(
                                        McpSchema.CreateMessageRequest.builder()
                                                .messages(List.of(new McpSchema.SamplingMessage(McpSchema.Role.USER,
                                                        new McpSchema.TextContent("ping?"))))
                                                .maxTokens(16)
                                                .build());
                                String text = answer.content() instanceof McpSchema.TextContent textContent
                                        ? textContent.text() : "";
                                return textResult(SAMPLING_RESOURCE_URI, "sampled:" + text);
                            }));
        }

        private static McpSchema.Resource textResource(String uri, String name) {
            return McpSchema.Resource.builder().uri(uri).name(name).mimeType("text/plain").build();
        }

        private static McpSchema.ReadResourceResult textResult(String uri, String text) {
            return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(uri, "text/plain", text)));
        }

        @Bean
        List<McpServerFeatures.SyncPromptSpecification> loopbackPromptSpecifications() {
            McpSchema.Prompt prompt = new McpSchema.Prompt(PROMPT_NAME, "Greets the given name.",
                    List.of(new McpSchema.PromptArgument("name", "Name to greet.", true)));
            return List.of(new McpServerFeatures.SyncPromptSpecification(prompt,
                    (exchange, request) -> new McpSchema.GetPromptResult("greeting",
                            List.of(new McpSchema.PromptMessage(McpSchema.Role.USER,
                                    new McpSchema.TextContent("Greet " + request.arguments().get("name")))))));
        }

    }

    @LocalServerPort
    private int port;

    @Autowired
    McpClientService mcpClientService;

    @Autowired
    private ToolSpecService toolSpecService;

    private McpServerInfo mcpServerInfo;
    private ToolSpecService.ToolMcpServerSetting previousSetting;

    @BeforeEach
    void exposeEvalExpressionTool() {
        this.previousSetting = this.toolSpecService.getToolMcpServerSetting();
        this.toolSpecService.setToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(false, Set.of(EVAL_EXPRESSION_TOOL_ID)));
        this.toolSpecService.reconcileNativeExposure();
    }

    @AfterEach
    void tearDown() {
        if (this.mcpServerInfo != null) {
            this.mcpClientService.stopMcpClient(this.mcpServerInfo);
        }
        this.toolSpecService.setToolMcpServerSetting(this.previousSetting);
        this.toolSpecService.reconcileNativeExposure();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + this.port;
    }

    static McpServerInfo newServerInfo(McpTransportType transportType, String serverName, Object parameters)
            throws JacksonException {
        long now = System.currentTimeMillis();
        return new McpServerInfo(transportType, serverName, "", now, now,
                new ObjectMapper().writeValueAsString(parameters));
    }

    private static <T> T pollUntil(Supplier<T> supplier, Predicate<T> ready) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        T value = supplier.get();
        while (!ready.test(value) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
            value = supplier.get();
        }
        return value;
    }

    void verifyFullCycleAgainstBuiltInServer(McpTransportType transportType, Object parameters) throws Exception {
        this.mcpServerInfo = newServerInfo(transportType, SERVER_NAME, parameters);
        this.mcpClientService.addRoot(this.mcpServerInfo, new McpSchema.Root(ROOT_URI, ROOT_URI));

        this.mcpClientService.startMcpClient(this.mcpServerInfo);

        List<McpSchema.Tool> tools = this.mcpClientService.getToolListAsOpt(this.mcpServerInfo).orElseThrow();
        assertThat(tools).extracting(McpSchema.Tool::name).contains("evalExpression");

        McpSchema.CallToolResult result = this.mcpClientService
                .callTool(this.mcpServerInfo, "evalExpression", Map.of("expression", "7 * 6"), Map.of())
                .orElseThrow();
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("42");

        List<McpSchema.Resource> resources =
                this.mcpClientService.getResourceListAsOpt(this.mcpServerInfo).orElseThrow();
        assertThat(resources).extracting(McpSchema.Resource::uri).contains(RESOURCE_URI);
        McpSchema.ReadResourceResult readResult =
                this.mcpClientService.readResourceAsOpt(this.mcpServerInfo, RESOURCE_URI).orElseThrow();
        assertThat(((McpSchema.TextResourceContents) readResult.contents().getFirst()).text())
                .isEqualTo("hello from loopback");
        assertThat(this.mcpClientService.getResourceTemplateListAsOpt(this.mcpServerInfo)).isPresent();

        List<McpSchema.Prompt> prompts =
                this.mcpClientService.getPromptListAsOpt(this.mcpServerInfo).orElseThrow();
        assertThat(prompts).extracting(McpSchema.Prompt::name).contains(PROMPT_NAME);
        McpSchema.GetPromptResult promptResult = this.mcpClientService
                .getPromptAsOpt(this.mcpServerInfo, PROMPT_NAME, Map.of("name", "Playground")).orElseThrow();
        assertThat(((McpSchema.TextContent) promptResult.messages().getFirst().content()).text())
                .isEqualTo("Greet Playground");

        assertThat(this.mcpClientService.setLoggingLevel(this.mcpServerInfo, McpSchema.LoggingLevel.DEBUG)).isTrue();
        this.mcpClientService.subscribeNotifications(this.mcpServerInfo, event -> { }).run();
        this.mcpClientService.subscribePendingChange(this.mcpServerInfo, () -> { }).run();

        McpSchema.ReadResourceResult notifierResult =
                this.mcpClientService.readResourceAsOpt(this.mcpServerInfo, NOTIFIER_RESOURCE_URI).orElseThrow();
        assertThat(((McpSchema.TextResourceContents) notifierResult.contents().getFirst()).text())
                .isEqualTo("notified");
        List<McpNotificationStore.Event> events = pollUntil(
                () -> this.mcpClientService.snapshotEvents(this.mcpServerInfo),
                recorded -> recorded.stream().anyMatch(e -> e.kind() == McpNotificationStore.Kind.LOGGING));
        assertThat(events).extracting(McpNotificationStore.Event::kind)
                .contains(McpNotificationStore.Kind.LOGGING);
        this.mcpClientService.clearEvents(this.mcpServerInfo);
        assertThat(this.mcpClientService.snapshotEvents(this.mcpServerInfo)).isEmpty();
        assertThat(this.mcpClientService.snapshotPendingSamplings(this.mcpServerInfo)).isEmpty();
        assertThat(this.mcpClientService.snapshotPendingElicitations(this.mcpServerInfo)).isEmpty();

        CompletableFuture<Optional<McpSchema.ReadResourceResult>> pendingElicitRead =
                CompletableFuture.supplyAsync(() ->
                        this.mcpClientService.readResourceAsOpt(this.mcpServerInfo, ELICIT_RESOURCE_URI));
        List<McpNotificationStore.PendingElicitation> pendingElicitations = pollUntil(
                () -> this.mcpClientService.snapshotPendingElicitations(this.mcpServerInfo),
                pending -> !pending.isEmpty());
        assertThat(pendingElicitations).hasSize(1);
        this.mcpClientService.completeElicitation(this.mcpServerInfo, pendingElicitations.getFirst().id(),
                new McpSchema.ElicitResult(McpSchema.ElicitResult.Action.ACCEPT, Map.of()));
        McpSchema.ReadResourceResult elicitResult =
                pendingElicitRead.get(15, TimeUnit.SECONDS).orElseThrow();
        assertThat(((McpSchema.TextResourceContents) elicitResult.contents().getFirst()).text())
                .isEqualToIgnoringCase("elicit:accept");

        CompletableFuture<Optional<McpSchema.ReadResourceResult>> pendingSamplingRead =
                CompletableFuture.supplyAsync(() ->
                        this.mcpClientService.readResourceAsOpt(this.mcpServerInfo, SAMPLING_RESOURCE_URI));
        List<McpNotificationStore.PendingSampling> pendingSamplings = pollUntil(
                () -> this.mcpClientService.snapshotPendingSamplings(this.mcpServerInfo),
                pending -> !pending.isEmpty());
        assertThat(pendingSamplings).hasSize(1);
        this.mcpClientService.completeSampling(this.mcpServerInfo, pendingSamplings.getFirst().id(),
                McpSchema.CreateMessageResult.builder()
                        .role(McpSchema.Role.ASSISTANT)
                        .content(new McpSchema.TextContent("pong"))
                        .model("loopback-test-model")
                        .build());
        McpSchema.ReadResourceResult samplingResult =
                pendingSamplingRead.get(15, TimeUnit.SECONDS).orElseThrow();
        assertThat(((McpSchema.TextResourceContents) samplingResult.contents().getFirst()).text())
                .isEqualTo("sampled:pong");

        assertThat(this.mcpClientService.getRoots(this.mcpServerInfo))
                .extracting(McpSchema.Root::uri).contains(ROOT_URI);
        String secondRootUri = ROOT_URI + "-2";
        this.mcpClientService.addRoot(this.mcpServerInfo, new McpSchema.Root(secondRootUri, secondRootUri));
        assertThat(this.mcpClientService.getRoots(this.mcpServerInfo)).hasSize(2);
        this.mcpClientService.removeRoot(this.mcpServerInfo, secondRootUri);
        this.mcpClientService.removeRoot(this.mcpServerInfo, ROOT_URI);
        assertThat(this.mcpClientService.getRoots(this.mcpServerInfo)).isEmpty();

        List<ToolCallback> toolCallbacks = this.mcpClientService.buildToolCallbackProviders(this.mcpServerInfo)
                .stream().map(ToolCallbackProvider::getToolCallbacks).flatMap(Arrays::stream).toList();
        assertThat(toolCallbacks).isNotEmpty();

        assertThat(this.mcpClientService.pingMcpClient(this.mcpServerInfo)).isNotNull();
        assertThat(this.mcpClientService.pingAndUpdateStatus(this.mcpServerInfo).status())
                .isEqualTo(McpClientService.ServerStatus.OK);
        assertThat(this.mcpClientService.getStatus(this.mcpServerInfo).status())
                .isEqualTo(McpClientService.ServerStatus.OK);
        assertThat(this.mcpClientService.snapshotStatuses()).isNotEmpty();
        assertThat(this.mcpClientService.getServerCapabilitiesAsOpt(this.mcpServerInfo)).isPresent();

        McpClientService.TestConnectionResult connectionResult =
                this.mcpClientService.testConnection(this.mcpServerInfo);
        assertThat(connectionResult.ok()).isTrue();
        assertThat(connectionResult.toolCount()).isGreaterThanOrEqualTo(1);

        this.mcpClientService.stopMcpClient(this.mcpServerInfo);
        assertThat(this.mcpClientService.getServerCapabilitiesAsOpt(this.mcpServerInfo)).isEmpty();
        this.mcpServerInfo = null;
    }

}
