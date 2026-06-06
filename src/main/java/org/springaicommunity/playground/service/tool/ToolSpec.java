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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ToolSpec {

    public enum JsonSchemaType {
        STRING("string"),
        NUMBER("number"),
        INTEGER("integer"),
        BOOLEAN("boolean"),
        OBJECT("object"),
        ARRAY("array");

        private final String value;

        JsonSchemaType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return this.name();
        }
    }

    public record ToolParamSpec(String name, String description, boolean required, JsonSchemaType type,
                                String testValue) {}

    public enum CodeType {
        Javascript
    }

    public record SandboxOverrides(Set<String> addAllowClasses, Set<String> removeAllowClasses,
                                   Set<String> addDenyClasses, Set<String> removeDenyClasses,
                                   String networkMode, Set<String> hostsAllow,
                                   Boolean fileRead, Boolean fileWrite, String fsBasePath) {
        public static SandboxOverrides empty() {
            return new SandboxOverrides(Set.of(), Set.of(), Set.of(), Set.of(), null, Set.of(), null, null, null);
        }
    }

    private String toolId;
    private String name;
    private String description;
    private List<Map.Entry<String, String>> staticVariables;
    private List<ToolParamSpec> params;
    private String code;
    private CodeType codeType;
    private String category;
    private Set<String> tags;
    private SandboxOverrides sandboxOverrides;
    private boolean draft;
    private Map<String, Object> toolSafety;
    private ToolManifest.HumanInTheLoop humanInTheLoop;

    @JsonIgnore
    private ToolCallback toolCallback;
    private long createTimestamp;
    private long updateTimestamp;

    private ToolSpec() {
    }

    public ToolSpec(String toolId, String name, String description, List<Map.Entry<String, String>> staticVariables,
            List<ToolParamSpec> params, String code, CodeType codeType, ToolCallback toolCallback) {
        this.toolId = toolId;
        this.name = name;
        this.description = description;
        this.staticVariables = staticVariables;
        this.params = params;
        this.code = code;
        this.codeType = codeType;
        this.toolCallback = toolCallback;
        this.createTimestamp = System.currentTimeMillis();
        this.updateTimestamp = this.createTimestamp;
    }

    public String toolId() {
        return toolId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public List<Map.Entry<String, String>> staticVariables() {
        return staticVariables;
    }

    public List<ToolParamSpec> params() {
        return params;
    }

    public String code() {
        return code;
    }

    public CodeType codeType() {
        return codeType;
    }

    public ToolCallback toolCallback() {
        return toolCallback;
    }

    public long createTimestamp() {
        return createTimestamp;
    }

    public long updateTimestamp() {
        return updateTimestamp;
    }

    public String category() {
        return category;
    }

    public Set<String> tags() {
        return tags == null ? Set.of() : tags;
    }

    public SandboxOverrides sandboxOverrides() {
        return sandboxOverrides == null ? SandboxOverrides.empty() : sandboxOverrides;
    }

    public boolean draft() {
        return draft;
    }

    public Map<String, Object> toolSafety() {
        return toolSafety == null ? Map.of() : toolSafety;
    }

    public ToolSpec withToolSafety(Map<String, Object> toolSafety) {
        this.toolSafety = toolSafety;
        return this;
    }

    public ToolManifest.HumanInTheLoop humanInTheLoop() {
        return humanInTheLoop;
    }

    public ToolSpec withHumanInTheLoop(ToolManifest.HumanInTheLoop humanInTheLoop) {
        this.humanInTheLoop = humanInTheLoop;
        return this;
    }

    public ToolSpec withCategory(String category) {
        this.category = category;
        return this;
    }

    public ToolSpec withTags(Set<String> tags) {
        this.tags = tags;
        return this;
    }

    public ToolSpec withSandboxOverrides(SandboxOverrides overrides) {
        this.sandboxOverrides = overrides;
        return this;
    }

    public ToolSpec withDraft(boolean draft) {
        this.draft = draft;
        return this;
    }

}