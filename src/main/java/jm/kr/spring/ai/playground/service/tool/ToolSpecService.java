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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfoService;
import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionParams;
import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionResult;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.ToolParamSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ToolSpecService {

    public record ToolMcpServerSetting(boolean autoAdd, Set<String> exposedToolIds) {}

    private static final Logger logger = LoggerFactory.getLogger(ToolSpecService.class);
    private static final ParameterizedTypeReference<Map<String, Object>>
            MAP_PARAMETERIZED_TYPE_REFERENCE = new ParameterizedTypeReference<>() {};

    private final McpSyncServer mcpSyncServer;
    private final McpAsyncServer mcpAsyncServer;
    private final McpServerInfoService mcpServerInfoService;
    private final Map<String, ToolSpec> toolIdSpecs;
    private final JsToolExecutor jsToolExecutor;

    private ToolMcpServerSetting toolMcpServerSetting;

    public ToolSpecService(ObjectProvider<McpSyncServer> syncServerProvider,
            ObjectProvider<McpAsyncServer> asyncServerProvider, McpServerInfoService mcpServerInfoService) {
        this.mcpSyncServer = syncServerProvider.getIfAvailable();
        this.mcpAsyncServer = asyncServerProvider.getIfAvailable();
        this.mcpServerInfoService = mcpServerInfoService;
        this.toolMcpServerSetting = new ToolMcpServerSetting(true, Set.of());
        this.toolIdSpecs = new ConcurrentHashMap<>();
        this.jsToolExecutor = new JsToolExecutor();
    }

    public ToolSpec update(String toolId, String toolName, String toolDescription,
            List<Map.Entry<String, String>> staticVariables, List<ToolParamSpec> toolParamSpecs, String jsCode,
            ToolSpec.CodeType codeType) {
        boolean isNew = Objects.isNull(toolId);
        ToolSpec toolSpec = isNew ? null : toolIdSpecs.get(toolId);
        if (Objects.isNull(toolSpec) || !Objects.equals(toolSpec.name(), toolName) ||
                !Objects.equals(toolSpec.description(), toolDescription) ||
                !Objects.equals(toolSpec.staticVariables().toString(),
                        staticVariables.toString()) || !Objects.equals(toolSpec.params().toString(),
                toolParamSpecs.toString()) || !Objects.equals(toolSpec.code(), jsCode) ||
                !Objects.equals(toolSpec.codeType(), codeType)) {
            Function<Map<String, Object>, Object> executor = toolParams -> executeTool(toolName, staticVariables,
                    jsCode, toolParams).result();
            ToolSpec newToolSpec =
                    new ToolSpec(toolId, toolName, toolDescription, staticVariables, toolParamSpecs, jsCode, codeType,
                            FunctionToolCallback.builder(toolName, executor).description(toolDescription)
                                    .inputSchema(toJsonSchema(toolParamSpecs))
                                    .inputType(MAP_PARAMETERIZED_TYPE_REFERENCE).build());
            toolIdSpecs.put(toolId, newToolSpec);
            logger.info("Tool spec {}: toolId={}, name={}", isNew ? "created" : "updated", toolId, toolName);
            if (Objects.nonNull(toolSpec))
                getMcpToolList().stream().filter(tool -> tool.name().equals(toolSpec.name())).findFirst()
                        .map(McpSchema.Tool::name).ifPresent(this::removeMcpTool);
            if (this.toolMcpServerSetting.autoAdd() &&
                    getMcpToolList().stream().noneMatch(tool -> tool.name().equals(newToolSpec.name()))) {
                addMcpTool(newToolSpec);
                HashSet<String> exposedToolIds = new HashSet<>(this.toolMcpServerSetting.exposedToolIds());
                exposedToolIds.add(newToolSpec.toolId());
                this.toolMcpServerSetting = new ToolMcpServerSetting(true, exposedToolIds);
            }
            return newToolSpec;
        }
        return toolSpec;
    }

    public void addMcpTool(ToolSpec toolSpec) {
        logger.info("Adding MCP tool to server: name={}", toolSpec.name());
        if (Objects.nonNull(this.mcpSyncServer)) {
            this.mcpSyncServer.addTool(McpToolUtils.toSyncToolSpecification(toolSpec.toolCallback()));
        } else {
            this.mcpAsyncServer.addTool(McpToolUtils.toAsyncToolSpecification(toolSpec.toolCallback()));
        }
        this.mcpServerInfoService.updateDefaultMcpTool();
    }

    public void removeMcpTool(String toolName) {
        logger.info("Removing MCP tool from server: name={}", toolName);
        if (Objects.nonNull(this.mcpSyncServer)) {
            this.mcpSyncServer.removeTool(toolName);
        } else {
            this.mcpAsyncServer.removeTool(toolName);
        }
    }

    public List<McpSchema.Tool> getMcpToolList() {
        return Objects.nonNull(this.mcpSyncServer) ? this.mcpSyncServer.listTools() : this.mcpAsyncServer.listTools()
                .toStream().toList();
    }

    public Optional<ToolSpec> getToolSpecAsOpt(String name) {
        return toolIdSpecs.values().stream().filter(toolSpec -> toolSpec.name().equals(name)).findFirst();
    }

    private String toJsonSchema(List<ToolParamSpec> toolParamSpecs) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode requiredArray = schema.putArray("required");

        for (ToolParamSpec param : toolParamSpecs) {
            ObjectNode paramNode = properties.putObject(param.name());
            paramNode.put("type", param.type().getValue());
            if (StringUtils.hasText(param.description())) {
                paramNode.put("description", param.description());
            }
            if (param.required()) {
                requiredArray.add(param.name());
            }
        }
        return schema.toPrettyString();
    }

    public JsExecutionResult executeTool(String toolName, List<Map.Entry<String, String>> staticVariables,
            String jsCode, Map<String, Object> toolParams) {
        Map<String, Object> mergeParams = new HashMap<>(toolParams);
        staticVariables.forEach(entry -> mergeParams.put(entry.getKey(), entry.getValue()));
        JsExecutionParams jsExecutionParams = new JsExecutionParams(mergeParams, jsCode);
        JsExecutionResult jsExecutionResult = this.jsToolExecutor.execute(jsExecutionParams);
        logger.info("Executing tool: {}, jsExecutionParams: {}, isOk: {}", toolName, jsExecutionParams.params(),
                jsExecutionResult.isOk());
        logger.debug("Executing tool Result: {}", jsExecutionResult);
        return jsExecutionResult;
    }

    public List<ToolSpec> getToolSpecList() {
        return toolIdSpecs.values().stream().sorted(Comparator.comparingLong(ToolSpec::updateTimestamp).reversed())
                .toList();
    }

    public void deleteToolSpec(String toolId) {
        logger.info("Deleting tool spec: toolId={}", toolId);
        Optional.ofNullable(toolIdSpecs.remove(toolId)).map(ToolSpec::name).ifPresent(this::removeMcpTool);
    }

    public ToolMcpServerSetting getToolMcpServerSetting() {
        return toolMcpServerSetting;
    }

    public void setToolMcpServerSetting(ToolMcpServerSetting toolMcpServerSetting) {
        this.toolMcpServerSetting = toolMcpServerSetting;
    }

    public void updateToolMcpServerSetting(ToolMcpServerSetting toolMcpServerSetting) {
        logger.info("Updating Tool MCP server setting: autoAdd={}, exposedToolCount={}",
                toolMcpServerSetting.autoAdd(), toolMcpServerSetting.exposedToolIds().size());
        setToolMcpServerSetting(toolMcpServerSetting);
        Set<String> toExposeToolNames =
                toolMcpServerSetting.exposedToolIds().stream().map(toolIdSpecs::get).map(ToolSpec::name)
                        .collect(Collectors.toSet());
        getMcpToolList().stream().map(McpSchema.Tool::name).filter(Predicate.not(toExposeToolNames::contains))
                .forEach(this::removeMcpTool);
        Set<String> exposedToolNames = getMcpToolList().stream().map(toolIdSpecs::get).map(ToolSpec::name)
                .collect(Collectors.toSet());
        toExposeToolNames.stream().filter(Predicate.not(exposedToolNames::contains))
                .forEach(name -> toolIdSpecs.values().stream().filter(toolSpec -> name.equals(toolSpec.name()))
                        .findFirst().ifPresent(this::addMcpTool));
        logger.info("Tool MCP server setting updated: exposedToolNames={}", toExposeToolNames);
    }

    @PreDestroy
    private void shutdownMcpServer() {
        logger.info("Shutting down MCP servers...");
        if (Objects.nonNull(this.mcpSyncServer)) {
            logger.info("Closing McpSyncServer");
            this.mcpSyncServer.close();
        }
        if (Objects.nonNull(this.mcpAsyncServer)) {
            logger.info("Closing McpAsyncServer");
            this.mcpAsyncServer.close();
        }
    }

}