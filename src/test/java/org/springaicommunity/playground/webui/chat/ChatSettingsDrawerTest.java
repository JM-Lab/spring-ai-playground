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
package org.springaicommunity.playground.webui.chat;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@SpringBootTest
class ChatSettingsDrawerTest extends SpringBrowserlessTest {

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void stubModelOptions() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void openingDrawerBuildsSettingViewAndApplyStartsNewChat() {
        ChatView view = navigate(ChatView.class);

        WorkspaceSettingsDrawer drawer = $(WorkspaceSettingsDrawer.class, view).first();
        drawer.open();
        roundTrip();

        ChatModelSettingView settingView = $(ChatModelSettingView.class, view).first();
        assertThat(settingView).isNotNull();

        Button apply = $(Button.class, drawer)
                .withCondition(button -> "Apply & New Chat".equals(button.getText()))
                .first();
        test(apply).click();
        roundTrip();

        assertThat($(ChatContentView.class, view).all()).isNotEmpty();
    }

}
