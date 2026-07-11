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
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.playground.service.agent.AgentTurnMessages;
import org.springaicommunity.playground.service.tool.ChatImageStore;
import org.springaicommunity.playground.service.tool.ImageReferenceHandler;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class ChatImageReferenceHandler implements ImageReferenceHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatImageReferenceHandler.class);
    private static final String DESCRIBE_IMAGE_TOOL = "describeImage";

    private final UI ui;
    private final ChatImageStore store;
    private final String conversationId;
    private final int timeoutSeconds;
    private final AtomicReference<CompletableFuture<Resolved>> pending = new AtomicReference<>();

    public ChatImageReferenceHandler(UI ui, ChatImageStore store, String conversationId, int timeoutSeconds) {
        this.ui = ui;
        this.store = store;
        this.conversationId = conversationId;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public Resolved resolve(Request request, BooleanSupplier interactionGate) {
        try {
            List<ChatImageStore.ImageRef> images = this.store.list(this.conversationId);
            if (StringUtils.hasText(request.ref())) {
                Optional<ChatImageStore.Loaded> loaded = this.store.load(this.conversationId, request.ref());
                if (loaded.isPresent())
                    return resolvedOf(loaded.get(), describe(images, loaded.get().hash()));
                if (images.size() == 1) return loadResolved(images.get(0));
                if (!images.isEmpty()) return promptChoice(images, interactionGate);
                return promptUpload(interactionGate);
            }
            if (images.isEmpty()) return promptUpload(interactionGate);
            if (images.size() == 1) return loadResolved(images.get(0));
            return promptChoice(images, interactionGate);
        } catch (IOException e) {
            logger.warn("image-reference.list-failed error={}", e.getMessage());
            return Resolved.none("The image could not be loaded.");
        }
    }

    @Override
    public void cancelPending() {
        CompletableFuture<Resolved> done = this.pending.getAndSet(null);
        if (done != null) done.complete(Resolved.cancelled("The user stopped this response."));
    }

    private Resolved loadResolved(ChatImageStore.ImageRef ref) {
        try {
            return this.store.load(this.conversationId, ref.hash())
                    .map(loaded -> resolvedOf(loaded, ref.fileName()))
                    .orElse(Resolved.none("The image could not be loaded."));
        } catch (IOException e) {
            logger.warn("image-reference.load-failed hash={} error={}", ref.hash(), e.getMessage());
            return Resolved.none("The image could not be loaded.");
        }
    }

    private static Resolved resolvedOf(ChatImageStore.Loaded loaded, String description) {
        return Resolved.of(loaded.bytes(), loaded.mimeType(), description);
    }

    private static String describe(List<ChatImageStore.ImageRef> images, String hash) {
        return images.stream().filter(ref -> hash.equals(ref.hash())).map(ChatImageStore.ImageRef::fileName)
                .filter(Objects::nonNull).findFirst().orElse(hash);
    }

    private Resolved promptUpload(BooleanSupplier interactionGate) {
        return awaitDialog(interactionGate, this::openUpload, "The image upload dialog could not be opened.",
                "No image was uploaded (the request timed out or was interrupted).");
    }

    private Resolved promptChoice(List<ChatImageStore.ImageRef> images, BooleanSupplier interactionGate) {
        return awaitDialog(interactionGate, done -> openChooser(images, done),
                "The image chooser could not be opened.",
                "No image was chosen (the request timed out or was interrupted).");
    }

    private Resolved awaitDialog(BooleanSupplier interactionGate,
            Function<CompletableFuture<Resolved>, Dialog> opener, String openError, String timeoutError) {
        if (!interactionGate.getAsBoolean())
            return Resolved.none(AgentTurnMessages.interactionBudget(DESCRIBE_IMAGE_TOOL));
        CompletableFuture<Resolved> done = new CompletableFuture<>();
        AtomicReference<Dialog> dialogRef = new AtomicReference<>();
        this.pending.set(done);
        try {
            this.ui.access(() -> dialogRef.set(opener.apply(done)));
        } catch (RuntimeException e) {
            logger.warn("image-reference.dialog-failed error={}", e.getMessage());
            this.pending.set(null);
            return Resolved.none(openError);
        }
        try {
            return DialogInteractions.await(done, this.timeoutSeconds, () -> Resolved.cancelled(timeoutError));
        } finally {
            this.pending.set(null);
            closeQuietly(dialogRef.get());
        }
    }

    private Dialog openUpload(CompletableFuture<Resolved> done) {
        Dialog dialog = baseDialog("Upload an image");
        Span prompt = hint("Choose an image to analyze.");
        ChatImageAttach attach = new ChatImageAttach(
                (fileName, bytes, mimeType, exifJson) -> onImage(fileName, bytes, mimeType, exifJson, dialog, done),
                message -> VaadinUtils.showErrorNotification("Attach failed: " + message));
        Button choose = new Button("Choose image", e -> attach.openPicker());
        Button cancel = new Button("Cancel", e -> {
            done.complete(Resolved.cancelled("The user cancelled the image upload."));
            dialog.close();
        });
        VerticalLayout body = new VerticalLayout(prompt, attach, choose);
        body.setPadding(false);
        dialog.add(body);
        dialog.getFooter().add(cancel);
        closeGuard(dialog, done, "The user closed the dialog without choosing an image.");
        dialog.open();
        return dialog;
    }

    private Dialog openChooser(List<ChatImageStore.ImageRef> images, CompletableFuture<Resolved> done) {
        Dialog dialog = baseDialog("Which image?");
        VerticalLayout body = new VerticalLayout(hint("Select the image to analyze."));
        body.setPadding(false);
        for (ChatImageStore.ImageRef ref : images) {
            Button pick = new Button(chooserLabel(ref), e -> {
                done.complete(loadResolved(ref));
                dialog.close();
            });
            thumbnailOf(ref).ifPresent(pick::setIcon);
            pick.setWidthFull();
            pick.getStyle().set("justify-content", "flex-start").set("height", "auto")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)");
            body.add(pick);
        }
        Button cancel = new Button("Cancel", e -> {
            done.complete(Resolved.cancelled("The user cancelled the image selection."));
            dialog.close();
        });
        dialog.add(body);
        dialog.getFooter().add(cancel);
        closeGuard(dialog, done, "The user closed the dialog without choosing an image.");
        dialog.open();
        return dialog;
    }

    private static String chooserLabel(ChatImageStore.ImageRef ref) {
        String name = StringUtils.hasText(ref.fileName()) ? ref.fileName() : ref.hash();
        return name + " (" + ref.hash().substring(0, Math.min(8, ref.hash().length())) + ")";
    }

    private Optional<Image> thumbnailOf(ChatImageStore.ImageRef ref) {
        try {
            return this.store.load(this.conversationId, ref.hash()).map(loaded -> {
                Image thumbnail = new Image("data:" + loaded.mimeType() + ";base64,"
                        + Base64.getEncoder().encodeToString(loaded.bytes()), ref.fileName());
                thumbnail.getStyle().set("height", "48px").set("width", "48px").set("object-fit", "cover")
                        .set("border-radius", "var(--lumo-border-radius-m)");
                return thumbnail;
            });
        } catch (IOException e) {
            logger.warn("image-reference.thumbnail-failed hash={} error={}", ref.hash(), e.getMessage());
            return Optional.empty();
        }
    }

    private void onImage(String fileName, byte[] bytes, String mimeType, String exifJson, Dialog dialog,
            CompletableFuture<Resolved> done) {
        try {
            ChatImageStore.Stored stored = this.store.store(this.conversationId, bytes, fileName, mimeType, exifJson);
            done.complete(Resolved.of(bytes, stored.mimeType(), fileName));
            dialog.close();
        } catch (IOException e) {
            logger.warn("image-reference.store-failed name={} error={}", fileName, e.getMessage());
            VaadinUtils.showErrorNotification("Could not save the image: " + e.getMessage());
        }
    }

    private static Dialog baseDialog(String title) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setModality(ModalityMode.STRICT);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        return dialog;
    }

    private static Span hint(String text) {
        Span span = new Span(text);
        span.getStyle().set("font-size", "var(--lumo-font-size-s)");
        return span;
    }

    private static void closeGuard(Dialog dialog, CompletableFuture<Resolved> done, String note) {
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && !done.isDone()) done.complete(Resolved.cancelled(note));
        });
    }

    private void closeQuietly(Dialog dialog) {
        if (dialog == null) return;
        try {
            this.ui.access(dialog::close);
        } catch (RuntimeException ignore) {
        }
    }
}
