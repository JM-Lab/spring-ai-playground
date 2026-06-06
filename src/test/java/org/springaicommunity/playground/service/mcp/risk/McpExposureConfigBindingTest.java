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
package org.springaicommunity.playground.service.mcp.risk;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.ToolSpecService.ExposureMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.playground.built-in-mcp-server.exposure-mode=composed-only",
        "spring.ai.playground.mcp-server.composed-tools[0].server=demo-server",
        "spring.ai.playground.mcp-server.composed-tools[0].tool=search",
        "spring.ai.playground.mcp-server.composed-tools[0].alias=demo_search",
        "spring.ai.playground.mcp-server.composed-tools[0].hitl=true"
})
class McpExposureConfigBindingTest {

    @Autowired
    SpringAiPlaygroundOptions options;

    @Autowired
    ToolSpecService toolSpecService;

    @Autowired
    McpCompositionService compositionService;

    @Test
    void exposureModeBindsFromYamlAndDrivesService() {
        assertThat(options.builtInMcpServer().exposureMode()).isEqualTo(ExposureMode.COMPOSED_ONLY);
        assertThat(toolSpecService.exposureMode()).isEqualTo(ExposureMode.COMPOSED_ONLY);
    }

    @Test
    void exposedToolsSeedTheExposedCompositionFromYaml() {
        McpComposition exposed = compositionService.getExposed().orElseThrow();
        assertThat(exposed.members()).hasSize(1);
        McpComposition.Member member = exposed.members().getFirst();
        assertThat(member.serverId()).isEqualTo("demo-server");
        assertThat(member.toolName()).isEqualTo("search");
        assertThat(member.exposedAlias()).isEqualTo("demo_search");
        assertThat(member.hitl()).isTrue();
    }
}
