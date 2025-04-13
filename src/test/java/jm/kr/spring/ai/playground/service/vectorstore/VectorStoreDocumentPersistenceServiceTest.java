package jm.kr.spring.ai.playground.service.vectorstore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
@TestPropertySource(properties = {"spring.ai.playground.user-home=${java.io.tmpdir}"})
class VectorStoreDocumentPersistenceServiceTest {

    @Autowired
    private VectorStoreDocumentPersistenceService vectorStoreDocumentPersistenceService;

    private List<VectorStoreDocumentInfo> sampleData;

    @BeforeEach
    void setUp() {
        sampleData = List.of(
                new VectorStoreDocumentInfo(
                        "doc1", "First Document", System.currentTimeMillis(), System.currentTimeMillis(),
                        "/path/to/doc1.txt",
                        () -> List.of(
                                new Document("Sample text content 1", Map.of("source", "user-input")),
                                new Document("Another sample text", Map.of("source", "system-generated"))
                        )
                ),
                new VectorStoreDocumentInfo(
                        "doc2", "Second Document", System.currentTimeMillis(), System.currentTimeMillis(),
                        "/path/to/doc2.txt",
                        () -> List.of(
                                new Document("Text from second document", Map.of("source", "user-upload")),
                                new Document("Additional content", Map.of("source", "AI-generated"))
                        )
                ),
                new VectorStoreDocumentInfo(
                        "doc3", "Third Document", System.currentTimeMillis(), System.currentTimeMillis(),
                        "/path/to/doc3.txt",
                        () -> List.of(
                                new Document("Random text snippet", Map.of("source", "manual-input")),
                                new Document("Final text block", Map.of("source", "api-response"))
                        )
                )
        );
    }

    @AfterEach
    void tearDown() {
        vectorStoreDocumentPersistenceService.clear();
    }

    @Test
    void testSaveAndLoad() throws IOException {
        for (VectorStoreDocumentInfo vectorStoreDocumentInfo : sampleData) {
            vectorStoreDocumentPersistenceService.save(vectorStoreDocumentInfo);
        }

        List<VectorStoreDocumentInfo> loadedDocuments = vectorStoreDocumentPersistenceService.loads();

        assertThat(loadedDocuments).hasSize(sampleData.size());

        for (int i = 0; i < sampleData.size(); i++) {
            VectorStoreDocumentInfo expected = sampleData.get(i);
            VectorStoreDocumentInfo actual = loadedDocuments.get(i);

            assertThat(actual.docInfoId()).isEqualTo(expected.docInfoId());
            assertThat(actual.title()).isEqualTo(expected.title());
            assertThat(actual.documentPath()).isEqualTo(expected.documentPath());

            assertThat(actual.documentListSupplier().get()).hasSize(expected.documentListSupplier().get().size());
        }
    }
}