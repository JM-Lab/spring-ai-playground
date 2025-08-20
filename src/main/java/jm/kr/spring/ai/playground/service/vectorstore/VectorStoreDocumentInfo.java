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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.Supplier;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class VectorStoreDocumentInfo {
    private final String docInfoId;
    private final String title;
    private final long createTimestamp;
    private final long updateTimestamp;
    private final String documentFileName;
    private final String documentPath;
    @JsonIgnore
    private Supplier<List<Document>> documentListSupplier;

    public VectorStoreDocumentInfo(String docInfoId, String title, long createTimestamp, long updateTimestamp,
            String documentFileName, String documentPath, Supplier<List<Document>> documentListSupplier) {
        this.docInfoId = docInfoId;
        this.title = title;
        this.createTimestamp = createTimestamp;
        this.updateTimestamp = updateTimestamp;
        this.documentFileName = documentFileName;
        this.documentPath = documentPath;
        this.documentListSupplier = documentListSupplier;
    }

    public String docInfoId() {
        return docInfoId;
    }

    public String title() {
        return title;
    }

    public long createTimestamp() {
        return createTimestamp;
    }

    public long updateTimestamp() {
        return updateTimestamp;
    }

    public String getDocumentFileName() {
        return documentFileName;
    }

    public String documentPath() {
        return documentPath;
    }

    public Supplier<List<Document>> documentListSupplier() {
        return documentListSupplier;
    }

    public void changeDocumentListSupplier(Supplier<List<Document>> documentListSupplier) {
        this.documentListSupplier = documentListSupplier;
    }

    public VectorStoreDocumentInfo newTitle(String newTitle) {
        return new VectorStoreDocumentInfo(docInfoId, newTitle, createTimestamp, System.currentTimeMillis(),
                documentFileName, documentPath, documentListSupplier);
    }
}