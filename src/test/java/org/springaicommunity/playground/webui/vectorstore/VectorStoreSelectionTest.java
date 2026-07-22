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
package org.springaicommunity.playground.webui.vectorstore;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.listbox.MultiSelectListBox;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentInfo;
import org.springaicommunity.playground.service.vectorstore.VectorStoreDocumentService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@SpringBootTest
class VectorStoreSelectionTest extends SpringBrowserlessTest {

    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private VectorStoreDocumentService documentService;

    @Test
    @SuppressWarnings("unchecked")
    void selectingSeededDocumentRendersItsChunks() {
        Document chunk = new Document("vector chunk body", Map.of("source", "browserless"));
        VectorStoreDocumentInfo info = this.documentService.putNewDocument("browserless-doc.txt", List.of(chunk));
        lenient().when(this.vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(info.documentListSupplier().get());

        VectorStoreView view = navigate(VectorStoreView.class);
        MultiSelectListBox<VectorStoreDocumentInfo> listBox = $(MultiSelectListBox.class, view).first();
        listBox.select(info);
        roundTrip();

        test($(Button.class, view)
                .withCondition(button -> "Search all".equals(button.getTooltip().getText()))
                .first()).click();
        roundTrip();

        Grid<?> grid = $(Grid.class, view).first();
        assertThat(grid.getGenericDataView().getItems())
                .anyMatch(item -> item instanceof VectorStoreContentItem contentItem
                        && contentItem.getText().contains("vector chunk body"));
    }

}
