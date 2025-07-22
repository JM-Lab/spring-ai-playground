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
package jm.kr.spring.ai.playground.service.vectorstore;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class VectorStoreServiceTest {
    @MockitoBean
    private VectorStore vectorStore;

    @Autowired
    private VectorStoreService vectorStoreService;


    @Test
    public void testSearchWithPromptAndFilter() {
        vectorStoreService.setVectorStoreOption(new VectorStoreService.SearchRequestOption(0.7, 5));
        String userPromptText = "test prompt";
        String filterExpression = "a == 'b'";
        List<Document> expectedResult = List.of(new Document("id", "text", Map.of("a", "b")));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedResult);

        Collection<Document> result = vectorStoreService.search(userPromptText, filterExpression);

        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        assertSame(expectedResult, result);
    }

    @Test
    public void testSearchWithSearchRequest() {
        List<Document> expectedResult = List.of(new Document("id", "text", Map.of()));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(expectedResult);

        Collection<Document> result = vectorStoreService.search("test prompt", "");

        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        assertSame(expectedResult, result);
    }

    @Test
    public void testAddDocument() {
        Document document = new Document("id", "text", Map.of());

        Document result = vectorStoreService.add(List.of(document)).getFirst();

        verify(vectorStore).add(List.of(document));
        assertSame(document, result);
    }

    @Test
    public void testUpdateDocument() {
        Document document = new Document("id", "text", Map.of());

        Document result = vectorStoreService.update(document);

        verify(vectorStore).delete(List.of("id"));
        verify(vectorStore).add(List.of(document));
        assertSame(document, result);
    }

    @Test
    public void testDeleteDocuments() {
        List<String> documentIds = List.of("id1", "id2");

        vectorStoreService.delete(documentIds);

        verify(vectorStore).delete(documentIds);
    }

    @Test
    public void testAddDocuments() {
        List<Document> documents = List.of(
                new Document("id1", "text1", Map.of()),
                new Document("id2", "text2", Map.of())
        );

        vectorStoreService.add(documents);

        verify(vectorStore).add(documents);
    }

}