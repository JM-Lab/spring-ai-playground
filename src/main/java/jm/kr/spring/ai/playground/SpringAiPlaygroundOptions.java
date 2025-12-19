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
package jm.kr.spring.ai.playground;

import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "spring.ai.playground")
public record SpringAiPlaygroundOptions(@NestedConfigurationProperty ToolStudio toolStudio, boolean persistence,
                                        String userHome, @NestedConfigurationProperty Chat chat) {

    public record ToolStudio(Long timeoutSeconds, @NestedConfigurationProperty JsSandbox jsSandbox) {}

    public record JsSandbox(boolean allowNetworkIo, boolean allowFileIo, boolean allowNativeAccess,
                            boolean allowCreateThread, Long maxStatements, Set<String> allowClasses) {}

    public record Chat(String systemPrompt, List<String> models,
                       @NestedConfigurationProperty DefaultChatOptions chatOptions) {}
}
