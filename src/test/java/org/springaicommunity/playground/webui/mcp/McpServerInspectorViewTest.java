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
package org.springaicommunity.playground.webui.mcp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.mcp.client.McpLoopbackClientTestSupport;
import org.springaicommunity.playground.service.mcp.client.McpNotificationStore;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.service.mcp.risk.McpToolRiskEvaluator;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.webui.mcp.inspector.primitives.server.ResourcePrimitive;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "vaadin.productionMode=true")
@Import(McpLoopbackClientTestSupport.LoopbackServerFixtures.class)
class McpServerInspectorViewTest extends SpringBrowserlessTest {

    private static final String EVAL_EXPRESSION_TOOL_ID = "6dea1efa-8a21-5522-ac08-4ede8fd9ff84";

    @LocalServerPort
    private int port;

    @Autowired
    private McpClientService mcpClientService;

    @Autowired
    private ToolSpecService toolSpecService;

    @Autowired
    private McpToolRiskEvaluator riskEvaluator;

    private McpServerInfo serverInfo;
    private ToolSpecService.ToolMcpServerSetting previousSetting;

    @BeforeEach
    void exposeToolAndConnectLoopback() throws JacksonException {
        this.previousSetting = this.toolSpecService.getToolMcpServerSetting();
        this.toolSpecService.setToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(false, Set.of(EVAL_EXPRESSION_TOOL_ID)));
        this.toolSpecService.reconcileNativeExposure();

        long now = System.currentTimeMillis();
        this.serverInfo = new McpServerInfo(McpTransportType.STREAMABLE_HTTP, "inspector-loopback", "", now, now,
                new ObjectMapper().writeValueAsString(new McpStreamableHttpClientProperties.ConnectionParameters(
                        "http://127.0.0.1:" + this.port, null)));
        this.mcpClientService.startMcpClient(this.serverInfo);
    }

    @AfterEach
    void tearDown() {
        if (this.serverInfo != null) {
            this.mcpClientService.stopMcpClient(this.serverInfo);
        }
        this.toolSpecService.setToolMcpServerSetting(this.previousSetting);
        this.toolSpecService.reconcileNativeExposure();
    }

    @Test
    void inspectorDrivesPrimitivesAgainstConnectedServer() throws Exception {
        List<McpSchema.Tool> tools = this.mcpClientService.getToolListAsOpt(this.serverInfo).orElseThrow();
        assertThat(tools).extracting(McpSchema.Tool::name).contains("evalExpression");

        McpServerInspectorView inspector = new McpServerInspectorView(this.serverInfo, this.mcpClientService,
                tools, this.riskEvaluator);
        UI.getCurrent().add(inspector);
        roundTrip();

        TabSheet tabSheet = $(TabSheet.class, inspector).first();
        for (int i = 0; i < tabSheet.getTabCount(); i++) {
            tabSheet.setSelectedIndex(i);
            roundTrip();
        }

        selectTab(tabSheet, "Tools");
        TextField expression = $(TextField.class, inspector)
                .withCondition(field -> "expression".equals(field.getLabel()))
                .first();
        test(expression).setValue("6000 * 7");
        Button runTool = $(Button.class, inspector)
                .withCondition(button -> "Run tool".equals(button.getTooltip().getText()))
                .first();
        test(runTool).click();
        roundTrip();
        assertThat(inspector.getElement().getTextRecursively()).contains("42000");

        selectTab(tabSheet, "Resources");
        ResourcePrimitive greeting = $(ResourcePrimitive.class, inspector)
                .withCondition(primitive -> primitive.getElement().getTextRecursively().contains("loopback-greeting"))
                .first();
        test($(Button.class, greeting)
                .withCondition(button -> "Read resource".equals(button.getTooltip().getText()))
                .first()).click();
        roundTrip();
        assertThat(inspector.getElement().getTextRecursively()).contains("hello from loopback");

        selectTab(tabSheet, "Prompts");
        test($(Button.class, inspector)
                .withCondition(button -> "Get prompt".equals(button.getTooltip().getText()))
                .first()).click();
        roundTrip();

        CompletableFuture<Optional<McpSchema.ReadResourceResult>> pendingElicitRead =
                CompletableFuture.supplyAsync(() ->
                        this.mcpClientService.readResourceAsOpt(this.serverInfo, "test://loopback/elicit"));
        pollUntil(() -> this.mcpClientService.snapshotPendingElicitations(this.serverInfo),
                (List<McpNotificationStore.PendingElicitation> pending) -> !pending.isEmpty());

        selectTab(tabSheet, "Elicitation");
        Button accept = pollUntil(() -> {
            roundTrip();
            return $(Button.class, inspector)
                    .withCondition(button -> "Accept".equals(button.getText()))
                    .all().stream().findFirst().orElse(null);
        }, Objects::nonNull);
        test(accept).click();

        McpSchema.ReadResourceResult elicitResult = pendingElicitRead.get(15, TimeUnit.SECONDS).orElseThrow();
        assertThat(((McpSchema.TextResourceContents) elicitResult.contents().getFirst()).text())
                .isEqualToIgnoringCase("elicit:accept");
    }

    private void selectTab(TabSheet tabSheet, String caption) {
        for (int i = 0; i < tabSheet.getTabCount(); i++) {
            Tab tab = tabSheet.getTabAt(i);
            if (tab.getElement().getTextRecursively().contains(caption)) {
                tabSheet.setSelectedIndex(i);
                roundTrip();
                return;
            }
        }
        throw new IllegalStateException("tab not found: " + caption);
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

}
