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
package jm.kr.spring.ai.playground.service.tool;

import io.modelcontextprotocol.spec.McpSchema;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfoService;
import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionResult;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.CodeType;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.JsonSchemaType;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.ToolParamSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@TestPropertySource(properties = "logging.level.com.example=DEBUG")
class ToolSpecServiceTest {

    @Autowired
    ToolSpecService toolSpecService;

    @MockitoBean
    McpServerInfoService mcpServerInfoService;

    @BeforeEach
    void clean() {
        toolSpecService.getToolSpecList().stream()
                .map(ToolSpec::toolId)
                .forEach(toolSpecService::deleteToolSpec);
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
}