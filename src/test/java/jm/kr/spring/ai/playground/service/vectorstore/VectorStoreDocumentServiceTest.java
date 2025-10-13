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

import jm.kr.spring.ai.playground.webui.vectorstore.VectorStoreView;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.unit.DataSize;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
class VectorStoreDocumentServiceTest {
    @Autowired
    private VectorStoreDocumentService service;

    @Test
    void testAddAndRemoveFile() throws Exception {
        File tempFile = File.createTempFile("test", ".txt");
        Files.write(tempFile.toPath(), "Test content".getBytes());
        String fileName = "upload-test.txt";
        Path uploadedPath = service.getUploadDir().resolve(fileName);
        Files.deleteIfExists(uploadedPath);

        try {
            service.addUploadedDocumentFile(fileName, tempFile);
        } catch (Exception e) {
            assertEquals("Already Exists - upload-test.txt", e.getMessage());
        }
        assertTrue(Files.exists(service.getUploadDir().resolve(service.encodeFileName(fileName))));

        service.removeUploadedDocumentFile(fileName);
        assertFalse(Files.exists(service.getUploadDir().resolve(service.encodeFileName(fileName))));
    }

    @Test
    void testDocumentLifecycle() {
        String docName = "test-doc.txt";
        List<Document> documents = List.of(
                new Document("doc1", "Sample text", Map.of("key", "value"))
        );

        VectorStoreDocumentInfo addedInfo = service.putNewDocument(docName, documents);

        assertNotNull(addedInfo.docInfoId());
        assertTrue(service.getDocumentList().stream()
                .anyMatch(info -> info.docInfoId().equals(addedInfo.docInfoId())));

        VectorStoreDocumentInfo updatedInfo = service.updateDocumentInfo(addedInfo, "New Title");

        assertEquals("New Title", updatedInfo.title());
        assertEquals(addedInfo.docInfoId(), updatedInfo.docInfoId());

        service.deleteDocumentInfo(addedInfo);
        assertFalse(service.getDocumentList().contains(updatedInfo));
    }

    @Test
    void testTextSplitting() {
        Path testFilePath = service.getUploadDir().resolve(service.encodeFileName("test-split.txt"));
        File testFile = testFilePath.toFile();
        if (testFile.exists()) {
            testFile.delete();
        }
        try {
            Files.writeString(testFilePath, """
                    This is sample data for testing text splitting functionality.
                    We're creating text with multiple lines.
                    The text should be long enough to verify that splitting works properly.
                    This text needs to be sufficiently long to be split into multiple chunks.
                    It is intended to test token-based splitting algorithms.
                    It includes various sentences to ensure split boundaries are handled correctly.""");
        } catch (IOException e) {
            fail("Failed to create sample data file: " + e.getMessage());
        }

        Resource testResource = new FileSystemResource(testFile);
        List<Document> defaultSplit = service.extractDocumentItems(List.of("test-split.txt"),
                service.getDefaultTokenTextSplitter()).get("test-split.txt");
        assertEquals(1, defaultSplit.size());
        assertEquals("test-split.txt", defaultSplit.getFirst().getMetadata().get("source"));

        VectorStoreDocumentService.TokenTextSplitInfo customConfig =
                new VectorStoreDocumentService.TokenTextSplitInfo(50, 10, 2, 500, false);
        List<Document> customSplit = service.split(testResource, customConfig);
        assertEquals(2, customSplit.size());

        try {
            Files.deleteIfExists(testFilePath);
        } catch (IOException e) {
            System.err.println("Failed to delete test file: " + e.getMessage());
        }
    }

    @Test
    void testDocumentEvents() {
        PropertyChangeListener listener = mock(PropertyChangeListener.class);
        PropertyChangeSupport documentInfoChangeSupport = new PropertyChangeSupport(this);
        documentInfoChangeSupport.addPropertyChangeListener(listener);

        VectorStoreDocumentInfo docInfo =
                service.putNewDocument("event-add.txt", List.of(new Document("id", "text", Map.of())));
        documentInfoChangeSupport.firePropertyChange(VectorStoreView.DOCUMENT_ADDING_EVENT, null, docInfo);

        List<VectorStoreDocumentInfo> docList = service.getDocumentList();
        documentInfoChangeSupport.firePropertyChange(VectorStoreView.DOCUMENT_SELECTING_EVENT, null, docList);

        service.deleteDocumentInfo(docInfo);
        documentInfoChangeSupport.firePropertyChange(VectorStoreView.DOCUMENTS_DELETE_EVENT, docInfo, null);

        ArgumentCaptor<PropertyChangeEvent> eventCaptor = ArgumentCaptor.forClass(PropertyChangeEvent.class);
        verify(listener, times(3)).propertyChange(eventCaptor.capture());
        List<PropertyChangeEvent> events = eventCaptor.getAllValues();

        assertTrue(events.stream()
                .anyMatch(e -> e.getPropertyName().equals(VectorStoreView.DOCUMENT_ADDING_EVENT)));
        assertTrue(events.stream()
                .anyMatch(e -> e.getPropertyName().equals(VectorStoreView.DOCUMENT_SELECTING_EVENT)));
        assertTrue(events.stream()
                .anyMatch(e -> e.getPropertyName().equals(VectorStoreView.DOCUMENTS_DELETE_EVENT)));
    }


    @Test
    void testFileSizeLimit() {
        DataSize maxSize = service.getMaxUploadSize();
        assertEquals(DataSize.ofMegabytes(20), maxSize);
    }
}
