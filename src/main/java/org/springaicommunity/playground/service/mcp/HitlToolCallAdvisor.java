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
package org.springaicommunity.playground.service.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;

public class HitlToolCallAdvisor extends ToolCallAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(HitlToolCallAdvisor.class);

    public HitlToolCallAdvisor(ToolCallingManager toolCallingManager) {
        super(toolCallingManager, BaseAdvisor.HIGHEST_PRECEDENCE + 300);
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        logToolCalls(chatClientResponse);
        return chatClientResponse;
    }

    @Override
    protected ChatClientResponse doAfterStream(ChatClientResponse chatClientResponse,
            StreamAdvisorChain streamAdvisorChain) {
        logToolCalls(chatClientResponse);
        return chatClientResponse;
    }

    private void logToolCalls(ChatClientResponse chatClientResponse) {
        ChatResponse chatResponse = chatClientResponse == null ? null : chatClientResponse.chatResponse();
        if (chatResponse == null || !chatResponse.hasToolCalls()) return;
        chatResponse.getResults().stream()
                .flatMap(result -> result.getOutput().getToolCalls().stream())
                .forEach(toolCall -> logger.info("toolcall.loop name={} id={}", toolCall.name(), toolCall.id()));
    }
}
