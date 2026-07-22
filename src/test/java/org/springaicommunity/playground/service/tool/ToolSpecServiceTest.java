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
package org.springaicommunity.playground.service.tool;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolSpec.SandboxOverrides;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;
import org.springaicommunity.playground.service.tool.ToolSpec.CodeType;
import org.springaicommunity.playground.service.tool.ToolSpec.JsonSchemaType;
import org.springaicommunity.playground.service.tool.ToolSpec.ToolParamSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "logging.level.com.example=DEBUG")
class ToolSpecServiceTest {

    @Autowired
    ToolSpecService toolSpecService;

    @Autowired
    ToolSpecPersistenceService toolSpecPersistenceService;

    @Autowired
    ObservationRegistry observationRegistry;

    @MockitoBean
    McpServerInfoService mcpServerInfoService;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger toolSpecLogger;

    @BeforeEach
    void clean() {
        toolSpecService.getToolSpecList().stream()
                .map(ToolSpec::toolId)
                .forEach(toolSpecService::deleteToolSpec);
        toolSpecService.setExposureMode(ToolSpecService.ExposureMode.BOTH);
        this.toolSpecLogger = (Logger) LoggerFactory.getLogger(ToolSpecService.class);
        this.logAppender = new ListAppender<>();
        this.logAppender.start();
        this.toolSpecLogger.addAppender(this.logAppender);
    }

    @AfterEach
    void detachAppender() {
        if (this.toolSpecLogger != null && this.logAppender != null) {
            this.toolSpecLogger.detachAppender(this.logAppender);
            this.logAppender.stop();
        }
    }

    private String logLevelOfFirstExecStart() {
        return this.logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(m -> m.startsWith("tool.exec.start "))
                .map(m -> m.replaceAll("(?s).*\\blevel=(L\\d)\\b.*", "$1"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no tool.exec.start log captured"));
    }

    @Test
    void testActualJsCodeExecution() {
        var param = new ToolParamSpec(
                "msg", "Message to send", true, JsonSchemaType.STRING, "Test"
        );

        String jsCode = """
                function run(msg) {
                    return "Echo → " + msg.toUpperCase();
                }
                return run(msg);
                """;

        ToolSpec tool = toolSpecService.update(
                "real-001",
                "realEcho",
                "Returns the message in uppercase",
                List.of(Map.entry("prefix", "[REAL] ")),
                List.of(param),
                jsCode,
                CodeType.Javascript
        );

        assertThat(tool.name()).isEqualTo("realEcho");
        assertThat(tool.code()).contains("toUpperCase");

        JsExecutionResult result = toolSpecService.executeTool(
                "realEcho",
                tool.staticVariables(),
                jsCode,
                Map.of("msg", "yahoo")
        );

        assertThat(result.isOk()).isTrue();
        assertThat(result.result())
                .isEqualTo("Echo → YAHOO");
    }

    @Test
    void testStaticVariablesMerging() {
        String code = "function run(){ return a + b + c; }; return run();";

        toolSpecService.update("t1", "mergeTest", "",
                List.of(Map.entry("a", "1"), Map.entry("b", "2")),
                List.of(),
                code, CodeType.Javascript);

        JsExecutionResult result = toolSpecService.executeTool("mergeTest",
                List.of(Map.entry("a", "1"), Map.entry("b", "2")),
                code,
                Map.of("c", "3"));

        assertThat(result.isOk()).isTrue();
        assertThat(result.result()).isEqualTo("123");
    }

    @Test
    void testObjectReuseForSameContent() {
        var p = List.of(new ToolParamSpec("x", "", true, JsonSchemaType.NUMBER, null));
        String code = "function run(p){return p.x*2;}";

        ToolSpec first =
                toolSpecService.update("id", "calc", "x2", List.of(), p, code, CodeType.Javascript);
        ToolSpec second =
                toolSpecService.update("id", "calc", "x2", List.of(), p, code, CodeType.Javascript);

        assertThat(first).isSameAs(second);
    }

    @Test
    void testJsonSchemaGeneration() {
        var params = List.of(
                new ToolParamSpec("name", "Name", true, JsonSchemaType.STRING, null),
                new ToolParamSpec("age", "Age", false, JsonSchemaType.INTEGER, null),
                new ToolParamSpec("on", "Active", true, JsonSchemaType.BOOLEAN, null)
        );

        toolSpecService.update("schema1", "test", "", List.of(), params, "function run(p){return p;}",
                CodeType.Javascript);

        ToolSpec tool = toolSpecService.getToolSpecAsOpt("test").get();
        String schema = tool.toolCallback().getToolDefinition().inputSchema();

        String expectedSchema = """
                {
                  "type" : "object",
                  "properties" : {
                    "name" : {
                      "type" : "string",
                      "description" : "Name"
                    },
                    "age" : {
                      "type" : "integer",
                      "description" : "Age"
                    },
                    "on" : {
                      "type" : "boolean",
                      "description" : "Active"
                    }
                  },
                  "required" : [ "name", "on" ]
                }""";

        Assertions.assertEquals(expectedSchema.replace("\r\n", "\n"), schema.replace("\r\n", "\n"));
    }

    @Test
    void saipActionToolDoesNotSetCallbackReturnDirect() {
        ToolSpec spec = toolSpecService.update("sa-1", "saReturnDirect", "",
                List.of(), List.of(), "return '```saip-action-return-direct';", CodeType.Javascript);
        assertThat(spec.toolCallback().getToolMetadata().returnDirect()).isFalse();
    }

    @Test
    void saipActionResultConverterReturnsRawBlock() {
        ToolSpec spec = toolSpecService.update("sa-2", "saRawBlock", "",
                List.of(), List.of(), "return '```saip-action {\"type\":\"email\"}';", CodeType.Javascript);
        String out = spec.toolCallback().call("{}");
        assertThat(out).isEqualTo("```saip-action {\"type\":\"email\"}");
    }

    private static ToolSpec freshSpec(String id, String name, boolean draft) {
        return new ToolSpec(id, name, "desc",
                List.<Map.Entry<String, String>>of(),
                List.<ToolParamSpec>of(),
                "return 1;", CodeType.Javascript, null)
                .withDraft(draft);
    }

    @Test
    void draftToolIsNotExposedToMcp() {
        ToolSpec draft = freshSpec("draft-1", "draftOne", true);
        toolSpecService.update(draft);

        assertThat(toolSpecService.getToolSpecAsOpt("draftOne")).isPresent();
        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .doesNotContain("draftOne");
    }

    @Test
    void publishingDraftAddsToMcp() {
        toolSpecService.update(freshSpec("publish-1", "publishOne", true));
        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .doesNotContain("publishOne");

        toolSpecService.update(freshSpec("publish-1", "publishOne", false));
        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .contains("publishOne");
    }

    @Test
    void unpublishingRemovesFromMcp() {
        toolSpecService.update(freshSpec("unp-1", "unpubOne", false));
        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .contains("unpubOne");

        toolSpecService.update(freshSpec("unp-1", "unpubOne", true));
        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .doesNotContain("unpubOne");
    }

    @Test
    void draftToolWithStaleExposedIdIsPrunedFromExposedSet() {
        toolSpecService.update(freshSpec("gw-1", "gwTool", true));
        toolSpecService.updateToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(true, Set.of("gw-1")));
        assertThat(toolSpecService.getToolMcpServerSetting().exposedToolIds()).contains("gw-1");

        toolSpecService.update(freshSpec("gw-1", "gwTool", true));

        assertThat(toolSpecService.getToolMcpServerSetting().exposedToolIds()).doesNotContain("gw-1");
        assertThat(currentMcpNames()).doesNotContain("gwTool");
    }

    @Test
    void reconcileExposesNonDraftExplicitIdButNeverDraft() {
        toolSpecService.update(freshSpec("pub-x", "pubXTool", false));
        toolSpecService.update(freshSpec("drf-x", "drfXTool", true));
        toolSpecService.updateToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(false, Set.of("pub-x", "drf-x")));

        assertThat(currentMcpNames()).contains("pubXTool").doesNotContain("drfXTool");

        toolSpecService.reconcileNativeExposure();
        assertThat(currentMcpNames()).contains("pubXTool").doesNotContain("drfXTool");

        toolSpecService.updateToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(true, Set.of()));
    }


    @Test
    void mcpInvocationFillsMissingOptionalParamsWithNull() {
        var paramsSpec = List.of(
                new ToolParamSpec("text", "input", true, JsonSchemaType.STRING, "hello"),
                new ToolParamSpec("mode", "encode|decode", false, JsonSchemaType.STRING, "encode"));
        String code = "return (mode == null ? 'NULL' : mode) + ':' + text;";
        ToolSpec spec = toolSpecService.update("missing-opt", "missingOptTool",
                "", List.of(), paramsSpec, code, ToolSpec.CodeType.Javascript);

        String out = spec.toolCallback().call("{\"text\":\"hello\"}");
        assertThat(out).contains("NULL:hello");
        toolSpecService.deleteToolSpec("missing-opt");
    }

    @Test
    void mcpInvocationSurfacesExecutionErrorAsToolExecutionException() {
        var paramsSpec = List.of(
                new ToolParamSpec("text", "input", true, JsonSchemaType.STRING, "hello"));
        String code = "throw new Error('boom');";
        ToolSpec spec = toolSpecService.update("explodes", "explodingTool",
                "", List.of(), paramsSpec, code, ToolSpec.CodeType.Javascript);

        Assertions.assertThrows(ToolExecutionException.class,
                () -> spec.toolCallback().call("{\"text\":\"hi\"}"));
        toolSpecService.deleteToolSpec("explodes");
    }

    @Test
    void executeToolSurfacesSsrfDenialToUser() {
        String code = "await fetch('http://10.0.0.1/'); return 'ok';";
        JsExecutionResult result = toolSpecService.executeTool(
                "ssrfBlockedTool", List.<Map.Entry<String, String>>of(), code, Map.of());
        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).contains("denied").contains("10.0.0.1");
    }

    @Test
    void executeToolSurfacesAllowlistClassDenialToUser() {
        String code = "Java.type('javax.imageio.ImageIO'); return 'ok';";
        JsExecutionResult result = toolSpecService.executeTool(
                "denyOutsideAllowlistTool", List.<Map.Entry<String, String>>of(), code, Map.of());
        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).isNotEmpty();
    }

    @Test
    void executeToolSurfacesFsUnavailableToUser() {
        String code = "return typeof safety.fs;";
        JsExecutionResult result = toolSpecService.executeTool(
                "noFsTool", List.<Map.Entry<String, String>>of(), code, Map.of());
        assertThat(result.isOk()).isTrue();
        assertThat(result.result()).isEqualTo("undefined");
    }

    @Test
    void publishedToolIsExposedToMcpByDefault() {
        ToolSpec published = freshSpec("pub-1", "pubOne", false);
        toolSpecService.update(published);

        assertThat(toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList())
                .contains("pubOne");
    }

    private List<String> currentMcpNames() {
        return toolSpecService.getMcpToolList().stream().map(McpSchema.Tool::name).toList();
    }

    private static ToolCallback externalCallback(String name) {
        return FunctionToolCallback
                .builder(name, (Function<Map<String, Object>, Object>) params -> "ok")
                .description("external " + name)
                .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                .build();
    }

    @Test
    void composedOnlyModeBlocksNativeExposureAndBothRestoresIt() {
        toolSpecService.update(freshSpec("mode-1", "modeToolOne", false));
        assertThat(currentMcpNames()).contains("modeToolOne");

        toolSpecService.setExposureMode(ToolSpecService.ExposureMode.COMPOSED_ONLY);
        assertThat(currentMcpNames()).doesNotContain("modeToolOne");

        toolSpecService.update(freshSpec("mode-2", "modeToolTwo", false));
        assertThat(currentMcpNames()).doesNotContain("modeToolTwo");

        toolSpecService.setExposureMode(ToolSpecService.ExposureMode.BOTH);
        assertThat(currentMcpNames()).contains("modeToolOne").contains("modeToolTwo");
    }

    @Test
    void nativeReconcileLeavesExternalWrappedToolsUntouched() {
        toolSpecService.update(freshSpec("nat-1", "nativeOne", false));
        toolSpecService.addExternalMcpTool(externalCallback("extTool"), false);
        assertThat(currentMcpNames()).contains("nativeOne").contains("extTool");

        toolSpecService.updateToolMcpServerSetting(
                new ToolSpecService.ToolMcpServerSetting(true, Set.of()));

        assertThat(currentMcpNames()).doesNotContain("nativeOne");
        assertThat(currentMcpNames()).contains("extTool");
        assertThat(toolSpecService.getExternalMcpToolNames()).contains("extTool");
    }

    @Test
    void composedExternalToolsAreSelectableAndApprovalGated() {
        toolSpecService.addExternalMcpTool(externalCallback("extSel"), true);
        assertThat(toolSpecService.getExternalToolSpecs()).extracting(ToolSpec::name).contains("extSel");
        assertThat(toolSpecService.requiresApproval("extSel")).isTrue();
    }

    @Test
    void testAddRemoveAndExecuteTool() {
        String toolId = "dynamic-tool-1";
        String toolName = "greetTool";
        String jsCode = "function run(name) { return 'Hello, ' + name; } return run(name);";
        var params = List.of(new ToolParamSpec("name", "User Name", true, JsonSchemaType.STRING, "User"));

        ToolSpec createdSpec = toolSpecService.update(
                toolId,
                toolName,
                "Greets the user",
                List.of(),
                params,
                jsCode,
                CodeType.Javascript
        );

        toolSpecService.addMcpTool(createdSpec);

        List<McpSchema.Tool> mcpToolList = toolSpecService.getMcpToolList();
        assertThat(mcpToolList.stream().map(McpSchema.Tool::name)
                .anyMatch(createdSpec.name()::equals)).isTrue();

        JsExecutionResult result = toolSpecService.executeTool(
                toolName,
                createdSpec.staticVariables(),
                jsCode,
                Map.of("name", "Spring AI")
        );

        assertThat(result.isOk()).isTrue();
        assertThat(result.result()).isEqualTo("Hello, Spring AI");

        toolSpecService.removeMcpTool(toolName);
        toolSpecService.deleteToolSpec(toolId);

        assertThat(toolSpecService.getToolSpecAsOpt(toolName)).isEmpty();
    }

    @Test
    void emptyOverrideDeltasOnAllowlistToolLogLevelStaysL3() {
        SandboxOverrides allowlistOnly = new SandboxOverrides(
                Set.of(), Set.of(), Set.of(), Set.of(),
                "allowlist", Set.of("wttr.in"),
                null, null, null, null);

        JsExecutionResult result = toolSpecService.executeTool(
                "allowlistOnlyTool", List.<Map.Entry<String, String>>of(),
                "return 1;", Map.of(), allowlistOnly);

        assertThat(result.isOk()).isTrue();
        assertThat(logLevelOfFirstExecStart()).isEqualTo("L3");
    }

    @Test
    void nullOverridesLogLevelIsL0() {
        JsExecutionResult result = toolSpecService.executeTool(
                "noOverridesTool", List.<Map.Entry<String, String>>of(),
                "return 1;", Map.of());

        assertThat(result.isOk()).isTrue();
        assertThat(logLevelOfFirstExecStart()).isEqualTo("L0");
    }

    @Test
    void removingCriticalDenyClassEscalatesLogLevelToL5() {
        SandboxOverrides removeSystem = new SandboxOverrides(
                Set.of(), Set.of(),
                Set.of(), Set.of("java.lang.System"),
                "blocked", Set.of(),
                null, null, null, null);

        JsExecutionResult result = toolSpecService.executeTool(
                "criticalRemovedTool", List.<Map.Entry<String, String>>of(),
                "return 1;", Map.of(), removeSystem);

        assertThat(result.isOk()).isTrue();
        assertThat(logLevelOfFirstExecStart()).isEqualTo("L5");
    }

    @Test
    void maskSecretsRedactsStaticVariableValuesButKeepsRegularParams() {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("query", "spring ai");
        merged.put("naverClientId", "abcdef1234");
        merged.put("naverClientSecret", "xy");
        merged.put("nullKey", null);
        Set<String> secrets = Set.of("naverClientId", "naverClientSecret", "nullKey");

        Map<String, Object> masked = ToolSpecService.maskSecrets(merged, secrets);

        assertThat(masked.get("query")).isEqualTo("spring ai");
        assertThat(masked.get("naverClientId")).isEqualTo("***34");
        assertThat(masked.get("naverClientSecret")).isEqualTo("***");
        assertThat(masked.get("nullKey")).isNull();
        assertThat(masked.toString()).doesNotContain("abcdef1234").doesNotContain("xy");
    }

    @Test
    void maskSecretsHandlesEmptyOrNullMap() {
        assertThat(ToolSpecService.maskSecrets(null, Set.of())).isEmpty();
        assertThat(ToolSpecService.maskSecrets(Map.of(), Set.of("k"))).isEmpty();
    }

    @Test
    void executeToolAugmentsCurrentObservationWithSandboxAttributes() {
        Observation parent = Observation.start("test.parent", observationRegistry);
        try (Observation.Scope scope = parent.openScope()) {
            JsExecutionResult result = toolSpecService.executeTool(
                    "augmentationProbe", List.<Map.Entry<String, String>>of(),
                    "return 42;", Map.of());
            assertThat(result.isOk()).isTrue();
        } finally {
            parent.stop();
        }

        Map<String, String> lowCard = new LinkedHashMap<>();
        for (KeyValue kv : parent.getContext().getLowCardinalityKeyValues()) {
            lowCard.put(kv.getKey(), kv.getValue());
        }
        Map<String, String> highCard = new LinkedHashMap<>();
        for (KeyValue kv : parent.getContext().getHighCardinalityKeyValues()) {
            highCard.put(kv.getKey(), kv.getValue());
        }
        assertThat(lowCard).containsKey("sandbox.level");
        assertThat(lowCard.get("sandbox.level")).matches("L\\d");
        assertThat(highCard).containsKey("sandbox.caps")
                .containsKey("sandbox.fs.base")
                .containsKey("sandbox.hosts.count")
                .containsKey("sandbox.cid");
    }

    @Test
    void executeToolWithoutActiveObservationDoesNotThrow() {
        JsExecutionResult result = toolSpecService.executeTool(
                "noObsTool", List.<Map.Entry<String, String>>of(),
                "return 1;", Map.of());
        assertThat(result.isOk()).isTrue();
    }

    @Test
    void autoAddExposesPublishedCustomToolsButNotDefaults() {
        toolSpecService.setToolMcpServerSetting(new ToolSpecService.ToolMcpServerSetting(true, Set.of()));
        String defaultId = toolSpecPersistenceService.getDefaultToolIds().iterator().next();

        toolSpecService.update(new ToolSpec(defaultId, "defaultLikeTool", "a default-catalog id",
                List.of(), List.of(), "return 1;", CodeType.Javascript, null));
        assertThat(toolSpecService.getToolMcpServerSetting().exposedToolIds())
                .as("built-in tools must not be auto-exposed on publish")
                .doesNotContain(defaultId);

        toolSpecService.update(new ToolSpec("custom-auto-1", "customAutoTool", "a user-authored tool",
                List.of(), List.of(), "return 1;", CodeType.Javascript, null));
        assertThat(toolSpecService.getToolMcpServerSetting().exposedToolIds())
                .as("user-authored tools keep the auto-expose behavior")
                .contains("custom-auto-1");
    }

    @Test
    void gatingRiskLevelDerivesFromSandboxPosture() {
        ToolSpec tool = toolSpecService.update("risk-gate-1", "riskyDeleteTool", "deletes things",
                List.of(), List.of(), "return 1;", CodeType.Javascript);
        tool.withSandboxOverrides(new SandboxOverrides(
                Set.of(), Set.of(), Set.of(), Set.of(),
                null, Set.of(), null, null, null, true));

        assertThat(toolSpecService.gatingRiskLevel("riskyDeleteTool")).isEqualTo(RiskLevel.L5);
        assertThat(toolSpecService.gatingRiskLevel("noSuchTool")).isNull();
    }
}
