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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class McpServerAddFlowTest extends SpringBrowserlessTest {

    @Test
    void addCustomServerShowsBlankConfigForm() {
        McpServerView view = navigate(McpServerView.class);

        Button addServer = $(Button.class, view)
                .withCondition(button -> "Add Custom Server".equals(button.getTooltip().getText()))
                .first();
        test(addServer).click();
        roundTrip();

        McpServerConfigView configView = $(McpServerConfigView.class, view).first();
        assertThat(configView).isNotNull();
    }

}
