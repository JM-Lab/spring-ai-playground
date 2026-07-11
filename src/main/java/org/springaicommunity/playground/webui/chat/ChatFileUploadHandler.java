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

import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.tool.ConversationFileUploadStore;
import org.springaicommunity.playground.service.tool.FileUploadHandler;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class ChatFileUploadHandler implements FileUploadHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatFileUploadHandler.class);

    private final UI ui;
    private final ConversationFileUploadStore store;
    private final String conversationId;
    private final int timeoutSeconds;
    private final AtomicReference<CompletableFuture<Result>> pending = new AtomicReference<>();

    public ChatFileUploadHandler(UI ui, ConversationFileUploadStore store, String conversationId,
            int timeoutSeconds) {
        this.ui = ui;
        this.store = store;
        this.conversationId = conversationId;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Result requestUpload(Request request) {
        CompletableFuture<Result> done = new CompletableFuture<>();
        AtomicReference<Dialog> dialogRef = new AtomicReference<>();
        this.pending.set(done);
        try {
            this.ui.access(() -> dialogRef.set(open(request, done)));
        } catch (RuntimeException e) {
            logger.warn("file-upload.dialog-failed error={}", e.getMessage());
            this.pending.set(null);
            return Result.none("The file upload dialog could not be opened.");
        }
        try {
            return DialogInteractions.await(done, this.timeoutSeconds,
                    () -> Result.none("The user did not upload a file (the request timed out or was interrupted)."));
        } finally {
            this.pending.set(null);
            closeQuietly(dialogRef.get());
        }
    }

    @Override
    public void cancelPending() {
        CompletableFuture<Result> done = this.pending.getAndSet(null);
        if (done != null) done.complete(Result.none("The user stopped this response before uploading a file."));
    }

    private Dialog open(Request request, CompletableFuture<Result> done) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Upload a file");
        dialog.setModality(ModalityMode.STRICT);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        Span prompt = new Span(StringUtils.hasText(request.prompt())
                ? request.prompt() : "Choose a file to upload.");
        prompt.getStyle().set("font-size", "var(--lumo-font-size-s)");

        ChatFileUpload upload = new ChatFileUpload(request.accept(),
                (fileName, mediaType, bytes) -> onBytes(fileName, mediaType, bytes, dialog, done),
                message -> VaadinUtils.showErrorNotification("Upload failed: " + message));

        Button cancel = new Button("Cancel", e -> {
            done.complete(Result.none("The user cancelled the upload."));
            dialog.close();
        });

        VerticalLayout body = new VerticalLayout(prompt, upload);
        body.setPadding(false);
        body.setSpacing(true);
        dialog.add(body);
        dialog.getFooter().add(cancel);
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && !done.isDone())
                done.complete(Result.none("The user closed the upload dialog without uploading a file."));
        });
        dialog.open();
        return dialog;
    }

    private void onBytes(String fileName, String mediaType, byte[] bytes, Dialog dialog,
            CompletableFuture<Result> done) {
        try {
            ConversationFileUploadStore.Stored stored = this.store.store(this.conversationId, fileName, bytes);
            done.complete(Result.of(stored.path(), stored.fileName(), mediaType, stored.bytes()));
            dialog.close();
        } catch (IOException e) {
            logger.warn("file-upload.store-failed name={} error={}", fileName, e.getMessage());
            VaadinUtils.showErrorNotification("Could not save the file: " + e.getMessage());
        }
    }

    private void closeQuietly(Dialog dialog) {
        if (dialog == null) return;
        try {
            this.ui.access(dialog::close);
        } catch (RuntimeException ignore) {
        }
    }
}
