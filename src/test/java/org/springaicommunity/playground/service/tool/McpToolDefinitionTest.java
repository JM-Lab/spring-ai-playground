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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.SpringAiPlaygroundOptions.NetworkPolicy;
import org.springaicommunity.playground.service.tool.ToolManifest.Audit;
import org.springaicommunity.playground.service.tool.ToolManifest.Capabilities;
import org.springaicommunity.playground.service.tool.ToolManifest.Code;
import org.springaicommunity.playground.service.tool.ToolManifest.HumanInTheLoop;
import org.springaicommunity.playground.service.tool.ToolManifest.Integrity;
import org.springaicommunity.playground.service.tool.ToolManifest.Params;
import org.springaicommunity.playground.service.tool.ToolManifest.Runtime;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.service.tool.ToolManifest.StaticVariable;
import org.springaicommunity.playground.service.tool.ToolManifest.TestCase;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpToolDefinitionTest {

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    void implementsToolDefinitionContract() {
        McpToolDefinition tool = exampleTool();
        ToolDefinition def = tool;
        assertEquals("extractPageContent", def.name());
        assertEquals("Extracts page content", def.description());
        assertTrue(def.inputSchema().contains("\"type\""));
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        McpToolDefinition original = exampleTool();
        ObjectMapper mapper = mapper();
        String json = mapper.writeValueAsString(original);
        McpToolDefinition back = mapper.readValue(json, McpToolDefinition.class);
        assertEquals(original, back);
    }

    @Test
    void serializesFlatTopLevelFields() throws Exception {
        McpToolDefinition tool = exampleTool();
        String json = mapper().writeValueAsString(tool);
        assertTrue(json.contains("\"name\":\"extractPageContent\""));
        assertTrue(json.contains("\"toolId\":\"uuid-1\""));
        assertTrue(json.contains("\"createTimestamp\":1700000000000"));
        assertTrue(json.contains("\"category\":\"WEB\""));
        assertTrue(json.contains("\"tags\":["));
        assertTrue(json.contains("\"manifest\""));
    }

    @Test
    void manifestEnvelopeContainsExecutionAndSafety() {
        McpToolDefinition tool = exampleTool();
        ToolManifest m = tool.manifest();
        // Code — full preservation
        assertEquals("javascript", m.code().lang());
        assertEquals("return 1+2;", m.code().source());
        assertEquals("sha256:abc", m.code().sha256());
        assertEquals(Code.EntryPoint.ASYNC_IIFE, m.code().entryPoint());
        assertEquals(Code.ReturnContract.IIFE_RETURN, m.code().returnContract());
        // Params binding only
        assertEquals(Params.Binding.GLOBALS, m.params().binding());
        // StaticVariable — typed, ENV kind preserves envName
        assertEquals(1, m.staticVariables().size());
        StaticVariable sv = m.staticVariables().get(0);
        assertEquals("apiKey", sv.name());
        assertEquals(StaticVariable.Kind.ENV, sv.kind());
        assertEquals("OPENAI_API_KEY", sv.envName());
        // Runtime
        assertEquals("spring-ai-playground/polyglot-js", m.runtime().id());
        assertTrue(m.runtime().javaInterop());
        // Sandbox + overrides (deny-classes + network)
        assertEquals(RiskLevel.L3, m.sandbox().level());
        assertEquals("web-fetch", m.sandbox().profile());
        assertTrue(m.sandbox().overrides().denyClasses().contains("java.io.File"));
        assertEquals("allowlist", m.sandbox().overrides().network().egressLevel());
        // Capabilities — declared network outbound matches sandbox allowlist intent
        assertEquals(List.of("*.spring.io"), m.capabilities().network().outbound());
        assertFalse(m.capabilities().sideEffect());
        // HumanInTheLoop
        assertEquals(HumanInTheLoop.Mode.REQUIRED, m.humanInTheLoop().mode());
        // Audit — passing + matching hash
        assertTrue(m.audit().passing());
        assertEquals("sha256:abc", m.audit().lastTestedHash());
        assertEquals(m.code().sha256(), m.audit().lastTestedHash(),
                "audit.lastTestedHash must equal code.sha256 for tool to be exposable");
    }

    @Test
    void roundTripPreservesNestedFieldValues() throws Exception {
        ObjectMapper mapper = mapper();
        McpToolDefinition back = mapper.readValue(mapper.writeValueAsString(exampleTool()), McpToolDefinition.class);
        // Don't trust record equals alone — verify each leaf survived
        assertEquals("extractPageContent", back.name());
        assertEquals("{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}", back.inputSchema());
        assertEquals("javascript", back.manifest().code().lang());
        assertEquals(Params.Binding.GLOBALS, back.manifest().params().binding());
        assertEquals("OPENAI_API_KEY", back.manifest().staticVariables().get(0).envName());
        assertEquals(RiskLevel.L3, back.manifest().sandbox().level());
        assertEquals(Set.of("java.io.File"), back.manifest().sandbox().overrides().denyClasses());
        assertEquals(Instant.parse("2026-05-09T00:00:00Z"), back.manifest().audit().lastTestedAt());
        assertEquals(Set.of("oss", "global"), back.tags());
        assertEquals("WEB", back.category());
    }


    @Test
    void paramsBindingOnlyNoSchemaDuplication() {
        McpToolDefinition tool = exampleTool();
        // Params has only binding — schema lives in toolDefinition.inputSchema (single source)
        assertEquals(Params.Binding.GLOBALS, tool.manifest().params().binding());
        assertEquals(1, Params.class.getRecordComponents().length);
    }

    @Test
    void categoryAndTagsAtRootNotInsideManifest() throws Exception {
        McpToolDefinition tool = exampleTool();
        String json = mapper().writeValueAsString(tool);
        // category/tags at root level, NOT inside manifest
        int rootCategoryIdx = json.indexOf("\"category\"");
        int manifestIdx = json.indexOf("\"manifest\"");
        int manifestEnd = json.lastIndexOf("}");
        assertTrue(rootCategoryIdx > manifestIdx, "category must follow manifest at root level");
        assertTrue(rootCategoryIdx < manifestEnd, "category must be inside the outer JSON object");
    }

    @Test
    void enumsExposeExpectedValues() {
        assertEquals(6, RiskLevel.values().length);
        assertEquals(2, HumanInTheLoop.Mode.values().length);
        assertEquals(2, StaticVariable.Kind.values().length);
        assertEquals(2, Params.Binding.values().length);
        assertEquals(3, Code.EntryPoint.values().length);
        assertEquals(2, Code.ReturnContract.values().length);
    }

    @Test
    void categoryAlignsWithToolCatalogIds() {
        Set<String> toolCatalogIds = Set.of(
                "TEXT", "DATA", "DATETIME", "MATH", "ENCODING", "CRYPTO", "SECURITY",
                "WEB", "PRODUCTIVITY", "MESSAGING", "AI", "CUSTOM");
        McpToolDefinition tool = exampleTool();
        assertTrue(toolCatalogIds.contains(tool.category()),
                "tool.category() = " + tool.category() + " not in tool-catalog id set");
    }

    private static McpToolDefinition exampleTool() {
        ToolManifest manifest = new ToolManifest("1.0",
                new Code("javascript", "return 1+2;", "sha256:abc",
                        Code.EntryPoint.ASYNC_IIFE, Code.ReturnContract.IIFE_RETURN),
                new Params(Params.Binding.GLOBALS),
                List.of(new StaticVariable("apiKey", StaticVariable.Kind.ENV, null, "OPENAI_API_KEY")),
                new Runtime("spring-ai-playground/polyglot-js", "0.2.0", "2024", true,
                        List.of("tool-safety-helpers/v1#parser")),
                new Sandbox(RiskLevel.L3, "web-fetch",
                        new Sandbox.Overrides(Set.of("java.io.File"),
                                new NetworkPolicy("allowlist", Set.of("*.spring.io"), Set.of()))),
                new Capabilities(new Capabilities.Network(List.of("*.spring.io"), List.of()),
                        List.of(), List.of(), false),
                new HumanInTheLoop(HumanInTheLoop.Mode.REQUIRED, "Fetch ${pageUrl}?"),
                List.of(new TestCase("happy", Map.of("url", "https://x"), Map.of("ok", true))),
                new Audit(1, Instant.parse("2026-05-09T00:00:00Z"), "sha256:abc", true),
                new Integrity("sha256:m", "sha256:c", "sha256:d"),
                null);
        return new McpToolDefinition(
                "extractPageContent",
                "Extracts page content",
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}}}",
                manifest,
                "uuid-1",
                1700000000000L, 1700000001000L,
                "WEB",
                Set.of("oss", "global"));
    }
}
