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
package org.springaicommunity.playground.service;

import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.ChatService;
import org.springaicommunity.playground.service.vectorstore.VectorStoreService;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiPlaygroundRagAdvisorTest {

    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final SpringAiPlaygroundRagAdvisor advisor =
            new SpringAiPlaygroundRagAdvisor(vectorStoreService, () -> List.of());

    private ChatClientRequest requestWith(String userText) {
        return ChatClientRequest.builder().prompt(new Prompt(List.of(new UserMessage(userText))))
                .context(Map.of(ChatService.RAG_FILTER_EXPRESSION, "docInfoId in ['x']")).build();
    }

    @Test
    void blankQuerySkipsRetrievalInsteadOfThrowing() {
        ChatClientRequest request = requestWith("   ");

        ChatClientRequest result = advisor.before(request, null);

        assertThat(result).isSameAs(request);
        verify(vectorStoreService, never()).search(any(), any());
    }

    @Test
    void noMatchingDocumentsShortCircuitsWithEmptyContext() {
        ChatClientRequest request = requestWith("weather in seoul");
        when(vectorStoreService.search("weather in seoul", "docInfoId in ['x']")).thenReturn(List.of());

        ChatClientRequest result = advisor.before(request, null);

        assertThat(result.context().get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT)).isEqualTo(List.of());
        verify(vectorStoreService).search("weather in seoul", "docInfoId in ['x']");
    }
}
