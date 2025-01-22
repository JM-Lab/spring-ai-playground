package jm.kr.spring.ai.playground.webui.vectorstore;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadI18N;
import com.vaadin.flow.component.upload.receivers.FileBuffer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

public class VectorStoreDocumentUpload extends Div {

    public VectorStoreDocumentUpload() {
        String userHome = System.getProperty("user.home");
        File uploadDir = new File(userHome, "spring-ai-playground/vectorstore");

        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        FileBuffer fileBuffer = new FileBuffer();
        Upload upload = new Upload(fileBuffer);
        upload.setAcceptedFileTypes(".pdf", ".doc", ".docx", ".ppt", ".pptx");
        upload.setMaxFiles(1);
        int maxMb = 50;
        int maxFileSizeInBytes = maxMb * 1024 * 1024;
        upload.setMaxFileSize(maxFileSizeInBytes);
        upload.setDropAllowed(true);

        upload.addFileRejectedListener(event -> {
            String errorMessage = event.getErrorMessage();
            Notification notification = Notification.show(errorMessage, 5000,
                    Notification.Position.MIDDLE);
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        });

        UploadExamplesI18N i18n = new UploadExamplesI18N();
        i18n.getAddFiles().setOne("Upload Document...");
        i18n.getDropFiles().setOne("Drop document here");
        i18n.getError().setIncorrectFileType(
                "The provided file does not have the correct format.");
        upload.setI18n(i18n);

        Paragraph hint = new Paragraph(
                "Please upload a single PDF, DOC/DOCX, or PPT/PPTX file with a maximum size of " + maxMb + " MB");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(hint, upload);

        upload.addSucceededListener(succeededEvent -> {
            String fileName = succeededEvent.getFileName();
            File uploadedFile = fileBuffer.getFileData().getFile();

            try {
                Path targetPath = new File(uploadDir, fileName).toPath();
                Files.move(uploadedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                Notification.show("File uploaded successfully to: " + targetPath);
            } catch (Exception e) {
                Notification.show("Failed to save file: " + e.getMessage());
            }
        });

        // Upload failed listener
        upload.addFailedListener(event -> Notification.show("Failed to upload: " + event.getFileName(), 3000, Notification.Position.MIDDLE));

        add(upload);
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
                            .setServerUnavailable(
                                    "Upload failed, please try again later")
                            .setUnexpectedServerError(
                                    "Upload failed due to server error")
                            .setForbidden("Upload forbidden")));
            setUnits(new Units().setSize(Arrays.asList("B", "kB", "MB", "GB", "TB",
                    "PB", "EB", "ZB", "YB")));
        }
    }
}