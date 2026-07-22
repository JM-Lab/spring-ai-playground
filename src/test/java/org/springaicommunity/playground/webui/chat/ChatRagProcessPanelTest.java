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

import tools.jackson.databind.node.StringNode;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.markdown.Markdown;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@SpringBootTest
class ChatRagProcessPanelTest extends SpringBrowserlessTest {

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private VectorStoreDocumentService documentService;

    @BeforeEach
    void stubStreamAndSearch() {
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        lenient().when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("grounded reply"))))));
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(
                List.of(new Document("indexed chunk", Map.of("source", "rag-panel-test"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void selectingDocumentRendersRagProcessPanelDuringStream() throws InterruptedException {
        VectorStoreDocumentInfo info = this.documentService.putNewDocument("rag-panel-doc.txt",
                List.of(new Document("indexed chunk", Map.of())));

        ChatView view = navigate(ChatView.class);
        MultiSelectComboBox<VectorStoreDocumentInfo> documents =
                $(MultiSelectComboBox.class, view)
                        .withCondition(combo -> ((MultiSelectComboBox<Object>) combo).getListDataView()
                                .getItems().anyMatch(VectorStoreDocumentInfo.class::isInstance))
                        .first();
        documents.setValue(Set.of(info));

        TextArea prompt = $(TextArea.class, view)
                .withCondition(area -> "Ask Spring AI Playground".equals(area.getPlaceholder()))
                .first();
        test(prompt).setValue("what does the document say?");
        test($(Button.class, view)
                .withCondition(button -> "Submit".equals(button.getTooltip().getText()))
                .first()).click();
        completePendingPromptValueJs("what does the document say?");

        assertThat(awaitMarkdownContaining(view, "grounded reply")).isTrue();
        assertThat($(Markdown.class, view).all().stream()
                .map(Markdown::getContent)
                .anyMatch(content -> content != null && content.contains("Searching VectorDB documents"))).isTrue();
    }

    private void completePendingPromptValueJs(String typedValue) {
        UI ui = UI.getCurrent();
        ui.getInternals().getStateTree().runExecutionsBeforeClientResponse();
        ui.getInternals().dumpPendingJavaScriptInvocations().stream()
                .filter(invocation -> invocation.getInvocation().getExpression().contains("return this.value"))
                .forEach(invocation -> invocation.complete(StringNode.valueOf(typedValue)));
    }

    private boolean awaitMarkdownContaining(ChatView view, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            roundTrip();
            boolean rendered = $(Markdown.class, view).all().stream()
                    .anyMatch(markdown -> markdown.getContent() != null && markdown.getContent().contains(expected));
            if (rendered) {
                return true;
            }
            Thread.sleep(50L);
        }
        return false;
    }

}
