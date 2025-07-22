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
package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.component.upload.receivers.FileBuffer;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VectorStoreDocumentUpload extends VerticalLayout {

    private final VectorStoreDocumentService vectorStoreDocumentService;
    private final List<String> uploadedFileNames;
    private final Upload upload;

    public VectorStoreDocumentUpload(VectorStoreDocumentService vectorStoreDocumentService) {
        this.vectorStoreDocumentService = vectorStoreDocumentService;
        this.uploadedFileNames = new ArrayList<>();
        Paragraph hint = new Paragraph(
                "Please upload a single PDF, DOC/DOCX, or PPT/PPTX file with a maximum size of " +
                        this.vectorStoreDocumentService.getMaxUploadSize().toMegabytes() + "MB");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(hint);

        this.upload = createUpload();
        add(upload);
    }

    private Upload createUpload() {
        FileBuffer fileBuffer = new FileBuffer();
        Upload upload = new Upload(fileBuffer);
        upload.setWidthFull();
        upload.setAcceptedFileTypes(".pdf", ".doc", ".docx", ".ppt", ".pptx");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(Long.valueOf(this.vectorStoreDocumentService.getMaxUploadSize().toBytes()).intValue());
        upload.setDropAllowed(true);

        upload.addFileRejectedListener(event -> VaadinUtils.showErrorNotification(event.getErrorMessage()));

        UploadExamplesI18N i18n = new UploadExamplesI18N();
        i18n.getAddFiles().setOne("Upload Document...");
        i18n.getDropFiles().setOne("Drop document here");
        i18n.getError().setIncorrectFileType(
                "The provided file does not have the correct format.");
        upload.setI18n(i18n);

        upload.addSucceededListener(succeededEvent -> {
            try {
                String fileName = succeededEvent.getFileName();
                File uploadedFile = fileBuffer.getFileData().getFile();
                this.vectorStoreDocumentService.addUploadedDocumentFile(fileName, uploadedFile);
                this.uploadedFileNames.add(fileName);
            } catch (Exception e) {
                VaadinUtils.showErrorNotification("Failed to save file: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });

        upload.addFileRemovedListener(fileRemovedEvent -> {
            try {
                String fileName = fileRemovedEvent.getFileName();
                this.vectorStoreDocumentService.removeUploadedDocumentFile(fileName);
                this.uploadedFileNames.remove(fileName);
            } catch (IOException e) {
                VaadinUtils.showErrorNotification("Failed to delete file: " + e.getMessage());
            }
        });

        upload.addFailedListener(
                event -> VaadinUtils.showErrorNotification("Failed to upload: " + event.getFileName()));
        return upload;
    }

    public void clearFileList() {
        this.upload.clearFileList();
        this.uploadedFileNames.clear();
    }

    public List<String> getUploadedFileNames() {
        return this.uploadedFileNames;
    }

    public static class UploadExamplesI18N extends UploadI18N {
        public UploadExamplesI18N() {
            setDropFiles(new DropFiles().setOne("Drop file here")
                    .setMany("Drop files here"));
            setAddFiles(new AddFiles().setOne("Upload File...")
                    .setMany("Upload Files..."));
            setError(new Error().setTooManyFiles("Too Many Files.")
                    .setFileIsTooBig("File is Too Big.")
                    .setIncorrectFileType("Incorrect File Type."));
            setUploading(new Uploading()
                    .setStatus(new Uploading.Status().setConnecting("Connecting...")
                            .setStalled("Stalled")
                            .setProcessing("Processing File...").setHeld("Queued"))
                    .setRemainingTime(new Uploading.RemainingTime()
                            .setPrefix("remaining time: ")
                            .setUnknown("unknown remaining time"))
                    .setError(new Uploading.Error()
                            .setServerUnavailable("Upload failed, please try again later")
                            .setUnexpectedServerError("Upload failed")
                            .setForbidden("Upload forbidden")));
            setUnits(new Units().setSize(Arrays.asList("B", "kB", "MB", "GB", "TB",
                    "PB", "EB", "ZB", "YB")));
        }
    }
}