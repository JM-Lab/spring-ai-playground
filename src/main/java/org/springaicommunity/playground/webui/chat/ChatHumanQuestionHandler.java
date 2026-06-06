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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class ChatHumanQuestionHandler implements HumanQuestionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHumanQuestionHandler.class);
    private static final long DECISION_TIMEOUT_MINUTES = 2;

    private final UI ui;

    public ChatHumanQuestionHandler(UI ui) {
        this.ui = ui;
    }

    @Override
    public Map<String, String> ask(List<HumanQuestion> questions) {
        Map<String, String> answers = new LinkedHashMap<>();
        if (questions == null) return answers;
        for (HumanQuestion question : questions) {
            answers.put(question.question(), askOne(question));
        }
        return answers;
    }

    private String askOne(HumanQuestion question) {
        CompletableFuture<String> decision = new CompletableFuture<>();
        AtomicReference<Dialog> dialogRef = new AtomicReference<>();
        AtomicReference<ConfirmDialog> confirmRef = new AtomicReference<>();
        try {
            this.ui.access(() -> open(question, decision, dialogRef, confirmRef));
        } catch (RuntimeException e) {
            logger.warn("hitl.dialog-failed question='{}' error={}", question.question(), e.getMessage());
            return defaultAnswer(question);
        }
        try {
            return decision.get(DECISION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            return defaultAnswer(question);
        } finally {
            closeQuietly(dialogRef.get());
            closeQuietly(confirmRef.get());
        }
    }

    private void open(HumanQuestion question, CompletableFuture<String> decision,
            AtomicReference<Dialog> dialogRef, AtomicReference<ConfirmDialog> confirmRef) {
        List<HumanQuestion.Option> options = question.options();
        if (options != null && options.size() == 2) {
            confirmRef.set(openConfirm(question, options, decision));
        } else {
            dialogRef.set(openChoices(question, options, decision));
        }
    }

    private ConfirmDialog openConfirm(HumanQuestion question, List<HumanQuestion.Option> options,
            CompletableFuture<String> decision) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(question.header() == null ? "Approve action" : question.header());
        dialog.add(body(question));
        dialog.setConfirmText(options.get(0).label());
        dialog.setCancelable(true);
        dialog.setCancelText(options.get(1).label());
        dialog.addConfirmListener(e -> decision.complete(options.get(0).label()));
        dialog.addCancelListener(e -> decision.complete(options.get(1).label()));
        dialog.open();
        return dialog;
    }

    private Dialog openChoices(HumanQuestion question, List<HumanQuestion.Option> options,
            CompletableFuture<String> decision) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(question.header() == null ? "Choose an option" : question.header());
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.add(body(question));
        Consumer<String> choose = label -> {
            decision.complete(label);
            dialog.close();
        };
        if (options != null) {
            for (HumanQuestion.Option option : options) {
                Button button = new Button(option.label(), e -> choose.accept(option.label()));
                button.setTooltipText(option.description());
                dialog.getFooter().add(button);
            }
            if (!options.isEmpty()) {
                dialog.getFooter().getComponentAt(0).getElement().getThemeList().add(ButtonVariant.LUMO_PRIMARY.getVariantName());
            }
        }
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && !decision.isDone()) decision.complete(defaultAnswer(question));
        });
        dialog.open();
        return dialog;
    }

    private VerticalLayout body(HumanQuestion question) {
        Span text = new Span(question.question());
        text.getStyle().set("font-size", "var(--lumo-font-size-s)");
        VerticalLayout body = new VerticalLayout(text);
        body.setPadding(false);
        body.setSpacing(false);
        return body;
    }

    private static String defaultAnswer(HumanQuestion question) {
        List<HumanQuestion.Option> options = question.options();
        return options == null || options.isEmpty() ? "" : options.get(options.size() - 1).label();
    }

    private void closeQuietly(Object dialog) {
        if (dialog == null) return;
        try {
            this.ui.access(() -> {
                if (dialog instanceof Dialog d) d.close();
                else if (dialog instanceof ConfirmDialog c) c.close();
            });
        } catch (RuntimeException ignore) {
        }
    }
}
