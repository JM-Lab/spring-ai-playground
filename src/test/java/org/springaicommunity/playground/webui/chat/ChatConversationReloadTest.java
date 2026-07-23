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
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.listbox.ListBox;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.QueryParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.ChatExtraOptions;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatToolPreferences;
import org.springaicommunity.playground.service.mcp.McpServerInfo;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpTransportType;
import org.springaicommunity.playground.webui.SttMicButton;
import org.springaicommunity.playground.webui.mcp.McpServerView;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@SpringBootTest(properties = "spring.ai.playground.chat.tool-search.min-tools=0")
class ChatConversationReloadTest extends SpringBrowserlessTest {

    @MockitoBean
    private ChatModel chatModel;

    @Autowired
    private ChatHistoryService chatHistoryService;

    @Autowired
    private McpServerInfoService mcpServerInfoService;

    @BeforeEach
    void stubModelOptions() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    @Test
    void mcpServerSavedAfterChatOpenedAppearsWhenReturningToTheView() {
        UI.getCurrent().navigate(ChatView.class);
        roundTrip();
        assertThat(mcpServerCombo((ChatView) getCurrentView()).getListDataView().getItems()
                .map(McpServerInfo::serverName)).doesNotContain("late-saved-sse");

        long now = System.currentTimeMillis();
        this.mcpServerInfoService.updateMcpServerInfo(McpTransportType.SSE, "late-saved-sse",
                new McpServerInfo(McpTransportType.SSE, "late-saved-sse", "saved after the chat was built",
                        now, now, null));
        try {
            UI.getCurrent().navigate(McpServerView.class);
            roundTrip();
            UI.getCurrent().navigate(ChatView.class);
            roundTrip();

            MultiSelectComboBox<McpServerInfo> combo = mcpServerCombo((ChatView) getCurrentView());
            assertThat(combo.getListDataView().getItems().map(McpServerInfo::serverName))
                    .contains("late-saved-sse");
            assertThat(combo.isEnabled()).isTrue();
            assertThat(combo.getPlaceholder()).isEqualTo("Select MCP servers for tools");
        } finally {
            this.mcpServerInfoService.deleteMcpServerInfo(McpTransportType.SSE, "late-saved-sse");
        }
    }

    @Test
    void selectingAnMcpServerWhileDynamicTurnsDynamicOffAndPersistsTheSelection() {
        long now = System.currentTimeMillis();
        this.mcpServerInfoService.updateMcpServerInfo(McpTransportType.SSE, "dynamic-exit-sse",
                new McpServerInfo(McpTransportType.SSE, "dynamic-exit-sse", "connected before the chat",
                        now, now, null));
        try {
            this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("dyn-conv", "Dyn", now, now, "sys",
                    (DefaultChatOptions) ChatOptions.builder().build(), List::of)
                    .withToolPreferences(ChatToolPreferences.defaults().withDynamicTools(true)));
            UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", "dyn-conv"));
            roundTrip();
            ChatView view = (ChatView) getCurrentView();
            MultiSelectComboBox<McpServerInfo> combo = mcpServerCombo(view);
            assertThat(dynamicCheckbox(view).getValue()).isTrue();
            assertThat(combo.isEnabled()).isTrue();
            assertThat(combo.getPlaceholder()).isEqualTo("Select servers (turns Dynamic off)");

            test(combo).selectItem("dynamic-exit-sse(SSE)");
            ComponentUtil.fireEvent(combo,
                    new AbstractField.ComponentValueChangeEvent<>(combo, combo, Set.of(), true));
            roundTrip();

            assertThat(dynamicCheckbox(view).getValue()).isFalse();
            assertThat(combo.getPlaceholder()).isEqualTo("Select MCP servers for tools");
            ChatToolPreferences persisted = this.chatHistoryService.getChatHistory("dyn-conv").toolPreferences();
            assertThat(persisted.dynamicTools()).isFalse();
            assertThat(persisted.mcpServerNames().get(McpTransportType.SSE)).containsExactly("dynamic-exit-sse");
        } finally {
            this.mcpServerInfoService.deleteMcpServerInfo(McpTransportType.SSE, "dynamic-exit-sse");
        }
    }

    @SuppressWarnings("unchecked")
    private MultiSelectComboBox<McpServerInfo> mcpServerCombo(ChatView view) {
        return $(MultiSelectComboBox.class, view)
                .withCondition(combo -> "Access Tools via external MCP connections"
                        .equals(combo.getTooltip().getText()))
                .first();
    }

    private Checkbox dynamicCheckbox(ChatView view) {
        return $(Checkbox.class, view)
                .withCondition(box -> "Dynamic tool discovery".equals(box.getLabel()))
                .first();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reloadingConversationRendersPersistedMessagesAndHistorySwitchRerenders() {
        long now = System.currentTimeMillis();
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("reload-conv-a", "Reload A", now, now,
                "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("alpha question"), new AssistantMessage("alpha answer"))));
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("reload-conv-b", "Reload B", now + 1,
                now + 1, "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("beta question"), new AssistantMessage("beta answer"))));

        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", "reload-conv-a"));
        roundTrip();
        ChatView view = (ChatView) getCurrentView();
        assertThat(markdownContents(view)).anyMatch(text -> text.contains("alpha answer"));

        ListBox<ChatHistory> historyList = $(ListBox.class, view)
                .withCondition(box -> ((ListBox<Object>) box).getListDataView().getItems()
                        .anyMatch(ChatHistory.class::isInstance))
                .first();
        ChatHistory target = historyList.getListDataView().getItems()
                .filter(history -> "reload-conv-b".equals(history.conversationId()))
                .findFirst().orElseThrow();
        historyList.setValue(target);
        roundTrip();
        assertThat(markdownContents(view)).anyMatch(text -> text.contains("beta answer"));
    }

    @Test
    void reloadedThinkAndMcpPanelsRenderInsideBoundedScrollerChain() {
        long now = System.currentTimeMillis();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp", now + 40_000L);
        metadata.put("thinkProcessMessages", "planning the answer");
        metadata.put("thinkProcessTimestamp", now + 1_000L);
        metadata.put("thinkProcessEndTimestamp", now + 3_500L);
        metadata.put("mcpToolProcessMessages", "tool call log line");
        metadata.put("mcpToolProcessTimestamp", now + 4_000L);
        metadata.put("mcpToolProcessEndTimestamp", now + 6_000L);
        metadata.put("mcpToolProcessCallCount", 1);
        metadata.put("mcpToolProcessToolNames", List.of("toolSearchTool"));
        AssistantMessage assistant = AssistantMessage.builder().content("final answer").properties(metadata).build();
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("reload-conv-panels", "Reload panels",
                now, now, "sys", (DefaultChatOptions) ChatOptions.builder().build(),
                () -> List.of(new UserMessage("with panels"), assistant)));

        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", "reload-conv-panels"));
        roundTrip();
        ChatView view = (ChatView) getCurrentView();

        Details think = panelWithSummary(view, "THINK");
        Details mcp = panelWithSummary(view, "MCP TOOLS");
        assertThat(summaryText(think)).contains("2.5s");
        assertThat(summaryText(mcp)).contains("1 call").contains("toolSearchTool");
        for (Details panel : List.of(think, mcp)) {
            assertThat(panel.getWidth()).isEqualTo("100%");
            assertThat(panel.getContent().anyMatch(ChatMessage.class::isInstance)).isTrue();
            assertScrollerChainBounded(panel);
        }
    }

    @Test
    void providerMismatchLocksPromptControlsIncludingAttach() {
        long now = System.currentTimeMillis();
        this.chatHistoryService.putIfAbsentChatHistory(new ChatHistory("reload-conv-mismatch", "Mismatch",
                now, now, "sys", (DefaultChatOptions) ChatOptions.builder().build(), ChatExtraOptions.defaults(),
                "ollama", ChatToolPreferences.defaults(),
                () -> List.of(new UserMessage("q"), new AssistantMessage("a"))));

        UI.getCurrent().navigate(ChatView.class, QueryParameters.of("conv", "reload-conv-mismatch"));
        roundTrip();
        ChatView view = (ChatView) getCurrentView();

        TextArea prompt = $(TextArea.class, view)
                .withCondition(area -> "Ask Spring AI Playground".equals(area.getPlaceholder()))
                .first();
        assertThat(prompt.isReadOnly()).isTrue();
        assertThat(prompt.isEnabled()).isFalse();
        assertThat($(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first().isEnabled()).isFalse();
        assertThat($(SttMicButton.class, view).first().isEnabled()).isFalse();
        assertThat($(Button.class, view)
                .withCondition(button -> "Attach image".equals(button.getTooltip().getText()))
                .first().isEnabled()).isFalse();
    }

    private Details panelWithSummary(ChatView view, String title) {
        return $(Details.class, view).all().stream()
                .filter(details -> details.getSummary() != null
                        && details.getSummary().getElement().getText().startsWith(title))
                .findFirst().orElseThrow();
    }

    private String summaryText(Details details) {
        return details.getSummary().getElement().getText();
    }

    private void assertScrollerChainBounded(Details panel) {
        List<String> ancestorWidths = new ArrayList<>();
        Component node = panel;
        while (true) {
            Component parent = node.getParent().orElseThrow();
            if (parent instanceof Scroller scroller) {
                assertThat(scroller.getClassNames()).contains("chat-message-scroller");
                assertThat(scroller.getScrollDirection()).isEqualTo(Scroller.ScrollDirection.VERTICAL);
                break;
            }
            assertThat(parent).isInstanceOf(VerticalLayout.class);
            ancestorWidths.add(((VerticalLayout) parent).getWidth());
            node = parent;
        }
        assertThat(ancestorWidths).isNotEmpty().allMatch("100%"::equals);
    }

    private List<String> markdownContents(ChatView view) {
        return $(Markdown.class, view).all().stream()
                .map(Markdown::getContent)
                .filter(Objects::nonNull)
                .toList();
    }

}
