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
package org.springaicommunity.playground;

import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "spring.ai.playground")
public record SpringAiPlaygroundOptions(@NestedConfigurationProperty ToolStudio toolStudio, boolean persistence,
                                        String userHome, @NestedConfigurationProperty Chat chat,
                                        @NestedConfigurationProperty DefaultTools defaultTools) {

    public record DefaultTools(String preset,
                               @NestedConfigurationProperty SelectionRule include,
                               @NestedConfigurationProperty SelectionRule exclude) {}

    public record SelectionRule(Set<String> names, Set<String> tags, Set<String> categories) {}

    public record ToolStudio(Long timeoutSeconds, @NestedConfigurationProperty JsSandbox jsSandbox,
                             @NestedConfigurationProperty FsConfig fs) {}

    public record JsSandbox(boolean allowNetworkIo, boolean allowFileIo, boolean allowNativeAccess,
                            boolean allowCreateThread, Long maxStatements, Set<String> denyClasses,
                            Set<String> allowClasses, Map<String, SandboxProfile> profiles) {}

    public record SandboxProfile(String level, String extendsProfile, Boolean javaInterop,
                                 Set<String> allowClasses, Set<String> denyClasses,
                                 @NestedConfigurationProperty NetworkPolicy network,
                                 String fileMode,
                                 Long maxStatements, Long timeoutMs) {}

    public record NetworkPolicy(String egressLevel, Set<String> hostsAllow, Set<String> hostsDeny) {}

    public record FsConfig(String basePath) {}

    public record Chat(String systemPrompt, List<String> models,
                       @NestedConfigurationProperty DefaultChatOptions chatOptions) {}
}
