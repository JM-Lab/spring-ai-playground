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

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.chat.ChatHistory;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.webui.PersistentUiDataStorage;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springaicommunity.playground.webui.common.WorkspaceSettingsDrawer;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.beans.PropertyChangeSupport;
import java.util.Objects;
import java.util.function.Consumer;

import static org.springaicommunity.playground.webui.VaadinUtils.styledButton;

@SpringComponent
@UIScope
@CssImport("./playground/chat-styles.css")
@PageTitle("Agentic Chat")
@Route(value = "agentic-chat", layout = SpringAiPlaygroundAppLayout.class)
public class ChatView extends ContentWorkspaceView {

    public static final String CHAT_HISTORY_CHANGE_EVENT = "CHAT_HISTORY_CHANGE_EVENT";
    public static final String CHAT_HISTORY_SELECT_EVENT = "CHAT_HISTORY_SELECT_EVENT";
    public static final String CHAT_HISTORY_EMPTY_EVENT = "CHAT_HISTORY_EMPTY_EVENT";

    private final PersistentUiDataStorage persistentUiDataStorage;
    private final ChatService chatService;
    private final Consumer<ChatHistory> completeChatHistoryConsumer;
    private final ChatHistoryService chatHistoryService;
    private final McpClientService mcpClientService;
    private final ChatHistoryView chatHistoryView;
    private final WorkspaceSettingsDrawer settingsDrawer;
    private ChatModelSettingView chatModelSettingView;
    private ChatContentView chatContentView;

    public ChatView(PersistentUiDataStorage persistentUiDataStorage, ChatService chatService,
            ChatHistoryService chatHistoryService, McpClientService mcpClientService) {
        this.persistentUiDataStorage = persistentUiDataStorage;
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
        this.mcpClientService = mcpClientService;

        PropertyChangeSupport chatHistoryChangeSupport = new PropertyChangeSupport(this);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_SELECT_EVENT,
                event -> this.changeChatContent((ChatHistory) event.getNewValue()));
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_EMPTY_EVENT, event -> {
            if ((boolean) event.getNewValue())
                addNewChatContent();
        });
        this.completeChatHistoryConsumer =
                chatHistory -> chatHistoryChangeSupport.firePropertyChange(CHAT_HISTORY_CHANGE_EVENT, null,
                        chatHistoryService.updateChatHistory(chatHistory));

        this.chatHistoryView =
                new ChatHistoryView(persistentUiDataStorage, chatHistoryService, chatHistoryChangeSupport);
        chatHistoryChangeSupport.addPropertyChangeListener(CHAT_HISTORY_CHANGE_EVENT,
                event -> this.chatHistoryView.changeChatHistoryContent((ChatHistory) event.getNewValue()));

        configureSidebar(this.chatHistoryView, "History");

        Button newChatButton =
                styledButton("New Chat", VaadinIcon.CHAT.create(), event -> addNewChatContent());
        addHeaderAction(newChatButton);

        this.settingsDrawer = installSettingsDrawer(VaadinIcon.COG_O, "Chat Model Setting",
                "Chat Model Setting");
        this.settingsDrawer.setBodyFactory(this::buildChatModelSettingView);
        this.settingsDrawer.setApplyButton("Apply & New Chat", this::applySettingsAndNewChat);

        addNewChatContent();
    }

    private ChatModelSettingView buildChatModelSettingView() {
        this.chatModelSettingView = new ChatModelSettingView(this.chatService.getModels(),
                this.chatContentView.getSystemPrompt(), this.chatContentView.getChatOption());
        return this.chatModelSettingView;
    }

    private void applySettingsAndNewChat() {
        addNewChatContent(this.chatModelSettingView.getSystemPromptTextArea(),
                this.chatModelSettingView.getChatOptions());
    }

    private void addNewChatContent() {
        addNewChatContent(this.chatService.getSystemPrompt(), this.chatService.getDefaultOptions());
    }

    private void addNewChatContent(String systemPrompt, ChatOptions chatOptions) {
        this.chatHistoryView.clearSelectHistory();
        changeChatContent(this.chatHistoryService.createChatHistory(systemPrompt, chatOptions));
    }

    private void changeChatContent(ChatHistory chatHistory) {
        if (Objects.isNull(chatHistory))
            return;

        this.chatContentView = new ChatContentView(this.persistentUiDataStorage, this.chatService, chatHistory,
                this.completeChatHistoryConsumer, this.mcpClientService);
        ChatOptions chatOptions = chatHistory.chatOptions();
        String label = String.format("%s: %s", this.chatService.getChatModelProvider(), chatOptions.getModel());
        VaadinUtils.getUi(this).access(() -> {
            setHeaderLabel(label);
            setContent(this.chatContentView);
        });
    }
}
