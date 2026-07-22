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

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.progressbar.ProgressBar;
import org.junit.jupiter.api.Test;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService;
import org.springaicommunity.playground.service.chat.OllamaModelDownloadService.PullProgress;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ModelDownloadDialogRenderTest extends SpringBrowserlessTest {

    private static final String MODEL = "smollm2:135m";

    private final OllamaModelDownloadService downloadService = mock(OllamaModelDownloadService.class);

    private final AtomicBoolean downloaded = new AtomicBoolean(false);

    @Test
    void clickingDownloadPullsTheGatedModelAndDisablesTheButton() {
        when(this.downloadService.pull(anyString())).thenReturn(Flux.never());
        ModelDownloadDialog dialog = openDialog();

        clickAction(dialog);

        verify(this.downloadService).pull(MODEL);
        assertThat(actionButton(dialog).isEnabled()).isFalse();
        assertThat(statusOf(dialog)).contains("Starting download...");
        assertThat(dialog.isOpened()).isTrue();
        assertThat(this.downloaded).isFalse();
    }

    @Test
    void aFinishedPullThatLeftTheModelMissingKeepsTheGateShutWithRetry() {
        Sinks.Many<PullProgress> pull = pullSink();
        when(this.downloadService.isDownloaded(MODEL)).thenReturn(false);
        ModelDownloadDialog dialog = openDialog();
        clickAction(dialog);

        assertThat(pull.tryEmitComplete()).isEqualTo(Sinks.EmitResult.OK);
        settle(() -> "Retry".equals(actionButton(dialog).getText()));

        assertThat(dialog.isOpened()).isTrue();
        assertThat(this.downloaded).isFalse();
        assertThat(actionButton(dialog).getText()).isEqualTo("Retry");
        assertThat(actionButton(dialog).isEnabled()).isTrue();
        assertThat(statusOf(dialog)).contains("Download did not finish");
    }

    @Test
    void progressRendersAsADeterminateBarWithHumanReadableBytes() {
        Sinks.Many<PullProgress> pull = pullSink();
        ModelDownloadDialog dialog = openDialog();
        clickAction(dialog);

        pull.tryEmitNext(new PullProgress("pulling manifest", 2_000_000_000L, 500_000_000L));

        settle(() -> statusOf(dialog).contains("pulling manifest"));
        assertThat(statusOf(dialog)).contains("pulling manifest · 500.0 MB / 2.0 GB");
        ProgressBar bar = $(ProgressBar.class, dialog).first();
        assertThat(bar.isIndeterminate()).isFalse();
        assertThat(bar.getValue()).isEqualTo(0.25);
    }

    @Test
    void aFailedPullSurfacesTheCauseAndNeverReportsSuccess() {
        when(this.downloadService.pull(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("ollama refused the connection")));
        ModelDownloadDialog dialog = openDialog();

        clickAction(dialog);

        assertThat(statusOf(dialog)).contains("Download failed").contains("ollama refused the connection");
        assertThat(actionButton(dialog).getText()).isEqualTo("Retry");
        assertThat(actionButton(dialog).isEnabled()).isTrue();
        assertThat(dialog.isOpened()).isTrue();
        assertThat(this.downloaded).isFalse();
        verify(this.downloadService, never()).isDownloaded(anyString());
    }

    @Test
    void retryingAfterAFailurePullsTheSameModelAgain() {
        when(this.downloadService.pull(anyString()))
                .thenReturn(Flux.error(new IllegalStateException("boom")), Flux.never());
        ModelDownloadDialog dialog = openDialog();

        clickAction(dialog);
        clickAction(dialog);

        verify(this.downloadService, times(2)).pull(MODEL);
        assertThat(actionButton(dialog).isEnabled()).isFalse();
        assertThat(statusOf(dialog)).contains("Starting download...");
    }

    private Sinks.Many<PullProgress> pullSink() {
        Sinks.Many<PullProgress> sink = Sinks.many().unicast().onBackpressureBuffer();
        when(this.downloadService.pull(anyString())).thenReturn(sink.asFlux());
        return sink;
    }

    private void settle(BooleanSupplier settled) {
        for (int attempt = 0; attempt < 40; attempt++) {
            roundTrip();
            if (settled.getAsBoolean()) return;
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("the pull signal never reached the dialog");
    }

    private ModelDownloadDialog openDialog() {
        ModelDownloadDialog dialog =
                new ModelDownloadDialog(MODEL, this.downloadService, () -> this.downloaded.set(true));
        dialog.open();
        roundTrip();
        return dialog;
    }

    private void clickAction(ModelDownloadDialog dialog) {
        test(actionButton(dialog)).click();
        roundTrip();
    }

    private Button actionButton(ModelDownloadDialog dialog) {
        return $(Button.class, dialog)
                .withCondition(button -> "Download".equals(button.getText()) || "Retry".equals(button.getText()))
                .first();
    }

    private String statusOf(ModelDownloadDialog dialog) {
        return $(Span.class, dialog).all().stream().map(Span::getText).collect(Collectors.joining("\n"));
    }

}
