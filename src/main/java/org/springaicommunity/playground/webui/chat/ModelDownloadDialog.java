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
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService.PullProgress;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

class ModelDownloadDialog extends Dialog {

    private final ProgressBar progressBar = new ProgressBar();
    private final Span statusText = new Span();
    private final Button downloadButton;
    private Disposable subscription;

    ModelDownloadDialog(String model, OllamaModelDownloadService downloadService, Runnable onDownloaded) {
        setHeaderTitle("Download model");
        setModality(ModalityMode.STRICT);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);

        Span message = new Span("Model '" + model + "' is not downloaded in Ollama yet. "
                + "It must finish downloading before the chat can start.");
        this.progressBar.setWidthFull();
        this.progressBar.setVisible(false);
        this.statusText.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout body = new VerticalLayout(message, this.progressBar, this.statusText);
        body.setPadding(false);
        body.setSpacing(true);
        body.setWidth("28rem");
        add(body);

        this.downloadButton = new Button("Download", event -> startDownload(model, downloadService, onDownloaded));
        this.downloadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancelButton = new Button("Cancel", event -> {
            if (Objects.nonNull(this.subscription)) this.subscription.dispose();
            close();
        });
        getFooter().add(cancelButton, this.downloadButton);
    }

    private void startDownload(String model, OllamaModelDownloadService downloadService, Runnable onDownloaded) {
        UI ui = UI.getCurrent();
        this.downloadButton.setEnabled(false);
        this.progressBar.setVisible(true);
        this.progressBar.setIndeterminate(true);
        this.statusText.getStyle().set("color", "var(--lumo-secondary-text-color)");
        this.statusText.setText("Starting download...");
        this.subscription = downloadService.pull(model)
                .sample(Duration.ofMillis(400))
                .subscribe(progress -> ui.access(() -> showProgress(progress)),
                        error -> ui.access(() -> showFailure("Download failed: "
                                + Objects.toString(error.getMessage(), "connection interrupted"))),
                        () -> ui.access(() -> onPullStreamComplete(model, downloadService, onDownloaded)));
    }

    // Ollama reports failures (bad model name, interrupted connection) in-band and still completes the
    // stream, so a completed stream counts as success only if the model is now actually present. Otherwise
    // keep the dialog open with an error and a Retry, instead of silently closing and re-opening the gate.
    private void onPullStreamComplete(String model, OllamaModelDownloadService downloadService,
            Runnable onDownloaded) {
        if (downloadService.isDownloaded(model)) {
            close();
            onDownloaded.run();
        } else {
            showFailure("Download did not finish. Check the model name and your Ollama connection, then retry "
                    + "(already-downloaded layers are skipped).");
        }
    }

    private void showProgress(PullProgress progress) {
        if (Objects.nonNull(progress.total()) && Objects.nonNull(progress.completed()) && progress.total() > 0) {
            this.progressBar.setIndeterminate(false);
            this.progressBar.setMin(0);
            this.progressBar.setMax(1);
            this.progressBar.setValue((double) progress.completed() / progress.total());
            this.statusText.setText(String.format(Locale.US, "%s · %s / %s", progress.status(),
                    formatBytes(progress.completed()), formatBytes(progress.total())));
        } else {
            this.progressBar.setIndeterminate(true);
            this.statusText.setText(Objects.toString(progress.status(), "Downloading..."));
        }
    }

    private void showFailure(String message) {
        this.progressBar.setVisible(false);
        this.downloadButton.setEnabled(true);
        this.downloadButton.setText("Retry");
        this.statusText.setText(message);
        this.statusText.getStyle().set("color", "var(--lumo-error-text-color)");
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000L) return String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0);
        if (bytes >= 1_000_000L) return String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0);
        return String.format(Locale.US, "%d KB", bytes / 1_000);
    }
}
