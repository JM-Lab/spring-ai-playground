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
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// Sequential per-question dialogs would stack their blocking waits past the stream timeout.
public final class ChatHumanQuestionHandler implements HumanQuestionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHumanQuestionHandler.class);

    private final UI ui;
    private final int decisionTimeoutSeconds;
    private final AtomicReference<Runnable> pendingCancel = new AtomicReference<>();

    public ChatHumanQuestionHandler(UI ui, int decisionTimeoutSeconds) {
        this.ui = ui;
        this.decisionTimeoutSeconds = decisionTimeoutSeconds;
    }

    @Override
    public Map<String, String> ask(List<HumanQuestion> questions) {
        if (questions == null || questions.isEmpty()) return Map.of();
        CompletableFuture<Map<String, String>> decision = new CompletableFuture<>();
        AtomicReference<Dialog> dialogRef = new AtomicReference<>();
        AtomicReference<ConfirmDialog> confirmRef = new AtomicReference<>();
        this.pendingCancel.set(() -> decision.complete(defaultAnswers(questions)));
        try {
            this.ui.access(() -> open(questions, decision, dialogRef, confirmRef));
        } catch (RuntimeException e) {
            logger.warn("hitl.dialog-failed questions={} error={}", questions.size(), e.getMessage());
            this.pendingCancel.set(null);
            return defaultAnswers(questions);
        }
        try {
            return DialogInteractions.await(decision, this.decisionTimeoutSeconds,
                    () -> defaultAnswers(questions));
        } finally {
            this.pendingCancel.set(null);
            closeQuietly(dialogRef.get());
            closeQuietly(confirmRef.get());
        }
    }

    @Override
    public void cancelPending() {
        Runnable cancel = this.pendingCancel.getAndSet(null);
        if (cancel != null) cancel.run();
    }

    private void open(List<HumanQuestion> questions, CompletableFuture<Map<String, String>> decision,
            AtomicReference<Dialog> dialogRef, AtomicReference<ConfirmDialog> confirmRef) {
        HumanQuestion first = questions.get(0);
        if (questions.size() == 1 && first.options() != null && first.options().size() == 2) {
            confirmRef.set(openConfirm(first, decision));
        } else {
            dialogRef.set(openBatch(questions, decision));
        }
    }

    private ConfirmDialog openConfirm(HumanQuestion question,
            CompletableFuture<Map<String, String>> decision) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(question.header() == null ? "Approve action" : question.header());
        dialog.add(body(question.question()));
        List<HumanQuestion.Option> options = question.options();
        dialog.setConfirmText(options.get(0).label());
        dialog.setCancelable(true);
        dialog.setCancelText(options.get(1).label());
        dialog.addConfirmListener(e -> decision.complete(singleAnswer(question, options.get(0).label())));
        dialog.addCancelListener(e -> decision.complete(singleAnswer(question, options.get(1).label())));
        dialog.open();
        return dialog;
    }

    private Dialog openBatch(List<HumanQuestion> questions, CompletableFuture<Map<String, String>> decision) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(questions.size() == 1 && questions.get(0).header() != null
                ? questions.get(0).header() : "Tool approvals required");
        dialog.setModality(ModalityMode.STRICT);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        Map<String, Supplier<String>> selections = new LinkedHashMap<>();
        for (HumanQuestion question : questions) {
            body.add(questionRow(question, selections));
        }
        dialog.add(body);
        Button confirm = new Button("Confirm", e -> {
            Map<String, String> answers = new LinkedHashMap<>();
            selections.forEach((id, selection) -> answers.put(id, selection.get()));
            decision.complete(answers);
            dialog.close();
        });
        confirm.getElement().getThemeList().add(ButtonVariant.LUMO_PRIMARY.getVariantName());
        dialog.getFooter().add(confirm);
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && !decision.isDone()) decision.complete(defaultAnswers(questions));
        });
        dialog.open();
        return dialog;
    }

    private VerticalLayout questionRow(HumanQuestion question, Map<String, Supplier<String>> selections) {
        VerticalLayout row = body(question.question());
        List<HumanQuestion.Option> options = question.options() == null ? List.of() : question.options();
        RadioButtonGroup<String> choice = new RadioButtonGroup<>();
        choice.setItems(options.stream().map(HumanQuestion.Option::label).toList());
        choice.setValue(defaultAnswer(question));
        row.add(choice);
        selections.put(question.id(),
                () -> choice.getValue() == null ? defaultAnswer(question) : choice.getValue());
        return row;
    }

    private VerticalLayout body(String question) {
        Span text = new Span(question);
        text.getStyle().set("font-size", "var(--lumo-font-size-s)");
        VerticalLayout body = new VerticalLayout(text);
        body.setPadding(false);
        body.setSpacing(false);
        return body;
    }

    private static Map<String, String> singleAnswer(HumanQuestion question, String label) {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put(question.id(), label);
        return answers;
    }

    private static Map<String, String> defaultAnswers(List<HumanQuestion> questions) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (HumanQuestion question : questions) {
            answers.put(question.id(), defaultAnswer(question));
        }
        return answers;
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
