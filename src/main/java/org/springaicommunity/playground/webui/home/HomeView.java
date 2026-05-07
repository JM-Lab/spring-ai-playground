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
package org.springaicommunity.playground.webui.home;

import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.springaicommunity.playground.service.chat.ChatHistoryService;
import org.springaicommunity.playground.service.mcp.McpServerInfoService;
import org.springaicommunity.playground.service.mcp.client.McpClientService;
import org.springaicommunity.playground.service.tool.ToolSpecPersistenceService;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springaicommunity.playground.webui.SpringAiPlaygroundAppLayout;
import org.springaicommunity.playground.webui.common.ContentWorkspaceView;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import java.util.Optional;

@SpringComponent
@UIScope
@AnonymousAllowed
@PageTitle("Home")
@Route(value = "", layout = SpringAiPlaygroundAppLayout.class)
public class HomeView extends ContentWorkspaceView {

    public HomeView(ToolSpecService toolSpecService,
            McpServerInfoService mcpServerInfoService,
            McpClientService mcpClientService,
            VectorStoreDocumentService vectorStoreDocumentService,
            ChatHistoryService chatHistoryService,
            ToolSpecPersistenceService toolSpecPersistenceService,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            Optional<EmbeddingOptions> embeddingOptions,
            Environment environment) {
        configureSidebar(new HomeItemView(), "Links");
        setHeaderLabel("Welcome");
        setContent(new HomeInfoView(toolSpecService, mcpServerInfoService, mcpClientService,
                vectorStoreDocumentService, chatHistoryService, toolSpecPersistenceService, chatModelProvider,
                embeddingModelProvider, embeddingOptions, environment));
    }
}
