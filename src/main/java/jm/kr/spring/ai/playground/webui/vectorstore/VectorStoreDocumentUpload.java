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
import com.vaadin.flow.component.upload.UploadI18N.Uploading;
import com.vaadin.flow.server.streams.TransferContext;
import com.vaadin.flow.server.streams.TransferProgressListener;
import com.vaadin.flow.server.streams.UploadHandler;
import jm.kr.spring.ai.playground.service.vectorstore.VectorStoreDocumentService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        TransferProgressListener progressListener = new TransferProgressListener() {
            @Override
            public void onComplete(TransferContext context, long transferredBytes) {
                String fileName = context.fileName();
                VaadinUtils.showInfoNotification("Successfully uploaded: " + fileName);
            }

            @Override
            public void onError(TransferContext context, IOException reason) {
                String fileName = context.fileName();
                VaadinUtils.showErrorNotification("Upload failed: " + fileName + " - " + reason.getMessage());
            }

            @Override
            public long progressReportInterval() {
                return 1024 * 1024;
            }
        };

        UploadHandler inMemoryHandler = UploadHandler.inMemory((metadata, data) -> {
            String fileName = metadata.fileName();
            try {
                File tempFile = File.createTempFile("upload-", ".tmp");
                Files.write(tempFile.toPath(), data);
                this.vectorStoreDocumentService.addUploadedDocumentFile(fileName, tempFile);
                this.uploadedFileNames.add(fileName);
                tempFile.delete();
            } catch (Exception e) {
                this.vectorStoreDocumentService.removeUploadedDocumentFile(fileName);
                clearFileList();
                throw new RuntimeException(e);
            }
        }, progressListener);

        Upload upload = new Upload(inMemoryHandler);
        upload.setWidthFull();
        upload.setAcceptedFileTypes("application/pdf", ".pdf",
                "application/msword", ".doc",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx",
                "application/vnd.ms-powerpoint", ".ppt",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
        upload.setMaxFiles(1);
        upload.setMaxFileSize((int) this.vectorStoreDocumentService.getMaxUploadSize().toBytes());
        upload.setDropAllowed(true);

        upload.addFileRejectedListener(event -> VaadinUtils.showErrorNotification(event.getErrorMessage()));


        upload.getElement().addEventListener("file-remove",
                event -> {
                    String string = event.getEventData().getString("event.detail.file.name");
                    Optional.ofNullable(string)
                            .ifPresent(fileName -> {
                                try {
                                    this.vectorStoreDocumentService.removeUploadedDocumentFile(fileName);
                                    this.uploadedFileNames.remove(fileName);
                                } catch (IOException e) {
                                    VaadinUtils.showErrorNotification("Failed to delete file: " + e.getMessage());
                                }
                            });
                }).addEventData("event.detail.file.name");

        upload.setI18n(createI18n());

        return upload;
    }

    private UploadI18N createI18n() {
        UploadI18N i18n = new UploadI18N();

        UploadI18N.AddFiles addFiles = new UploadI18N.AddFiles();
        addFiles.setOne("Upload Document...");
        addFiles.setMany("Upload Documents...");
        i18n.setAddFiles(addFiles);

        UploadI18N.DropFiles dropFiles = new UploadI18N.DropFiles();
        dropFiles.setOne("Drop document here");
        dropFiles.setMany("Drop documents here");
        i18n.setDropFiles(dropFiles);

        UploadI18N.Error error = new UploadI18N.Error();
        error.setTooManyFiles("Too many files. Please upload only one file.");
        error.setFileIsTooBig("File is too big. Maximum size is " +
                this.vectorStoreDocumentService.getMaxUploadSize().toMegabytes() + "MB.");
        error.setIncorrectFileType("The provided file does not have the correct format. " +
                "Please upload PDF, DOC/DOCX, or PPT/PPTX files only.");
        i18n.setError(error);

        Uploading uploading = buildUploading();

        i18n.setUploading(uploading);

        UploadI18N.Units units = new UploadI18N.Units();
        units.setSize(List.of("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"));
        i18n.setUnits(units);

        return i18n;
    }

    private static @NotNull Uploading buildUploading() {
        Uploading uploading = new Uploading();

        Uploading.Status status = new Uploading.Status();
        status.setConnecting("Connecting...");
        status.setStalled("Stalled");
        status.setProcessing("Processing file...");
        status.setHeld("Queued");
        uploading.setStatus(status);

        Uploading.RemainingTime remainingTime = new Uploading.RemainingTime();
        remainingTime.setPrefix("remaining time: ");
        remainingTime.setUnknown("unknown remaining time");
        uploading.setRemainingTime(remainingTime);

        Uploading.Error uploadingError = new Uploading.Error();
        uploadingError.setServerUnavailable("Server unavailable. Please try again later.");
        uploadingError.setUnexpectedServerError("Upload failed due to server error. Please try again.");
        uploadingError.setForbidden("Upload forbidden. Please check file permissions.");
        uploading.setError(uploadingError);
        return uploading;
    }

    public void clearFileList() {
        this.upload.clearFileList();
        this.uploadedFileNames.clear();
    }

    public List<String> getUploadedFileNames() {
        return List.copyOf(this.uploadedFileNames);
    }
}