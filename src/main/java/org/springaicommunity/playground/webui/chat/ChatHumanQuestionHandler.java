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
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import org.springaicommunity.playground.service.tool.HumanQuestion;
import org.springaicommunity.playground.service.tool.HumanQuestionHandler;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;
import org.springaicommunity.playground.webui.mcp.McpRiskChip;
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
        VerticalLayout body = body(question);
        dialog.add(body);
        List<HumanQuestion.Option> options = question.options();
        dialog.setCancelable(true);
        dialog.setCancelText(options.get(1).label());
        dialog.addCancelListener(e -> decision.complete(singleAnswer(question, options.get(1).label())));
        if (question.riskLevel() == RiskLevel.L5) {
            dialog.setConfirmButton(acknowledgedApprove(question, options.get(0).label(), body, decision, dialog));
        } else {
            dialog.setConfirmText(options.get(0).label());
            if (question.riskLevel() == RiskLevel.L4) {
                dialog.setConfirmButtonTheme("error primary");
            }
            dialog.addConfirmListener(e -> decision.complete(singleAnswer(question, options.get(0).label())));
        }
        dialog.open();
        return dialog;
    }

    private Button acknowledgedApprove(HumanQuestion question, String approveLabel, VerticalLayout body,
            CompletableFuture<Map<String, String>> decision, ConfirmDialog dialog) {
        Button approve = new Button(approveLabel, e -> {
            decision.complete(singleAnswer(question, approveLabel));
            dialog.close();
        });
        approve.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        approve.setEnabled(false);
        Checkbox acknowledge = new Checkbox("I reviewed the arguments and accept the risk");
        acknowledge.getStyle().set("margin-top", "var(--lumo-space-xs)");
        acknowledge.addValueChangeListener(e -> approve.setEnabled(Boolean.TRUE.equals(e.getValue())));
        body.add(acknowledge);
        return approve;
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
        AtomicReference<Runnable> revalidate = new AtomicReference<>(() -> { });
        for (HumanQuestion question : questions) {
            body.add(questionRow(question, selections, revalidate));
        }
        dialog.add(body);
        Button confirm = new Button("Confirm", e -> {
            Map<String, String> answers = new LinkedHashMap<>();
            selections.forEach((id, selection) -> answers.put(id, selection.get()));
            decision.complete(answers);
            dialog.close();
        });
        confirm.getElement().getThemeList().add(ButtonVariant.LUMO_PRIMARY.getVariantName());
        if (questions.stream().map(HumanQuestion::riskLevel)
                .anyMatch(level -> level == RiskLevel.L4 || level == RiskLevel.L5)) {
            confirm.getElement().getThemeList().add(ButtonVariant.LUMO_ERROR.getVariantName());
        }
        if (questions.stream().anyMatch(question -> question.riskLevel() == RiskLevel.L5)) {
            Checkbox acknowledge = new Checkbox("I reviewed the arguments and accept the risk");
            acknowledge.getStyle().set("margin-top", "var(--lumo-space-xs)");
            body.add(acknowledge);
            Runnable gate = () -> confirm.setEnabled(!criticalApproved(questions, selections)
                    || Boolean.TRUE.equals(acknowledge.getValue()));
            acknowledge.addValueChangeListener(e -> gate.run());
            revalidate.set(gate);
            gate.run();
        }
        dialog.getFooter().add(confirm);
        dialog.addOpenedChangeListener(e -> {
            if (!e.isOpened() && !decision.isDone()) decision.complete(defaultAnswers(questions));
        });
        dialog.open();
        return dialog;
    }

    private VerticalLayout questionRow(HumanQuestion question, Map<String, Supplier<String>> selections,
            AtomicReference<Runnable> revalidate) {
        VerticalLayout row = body(question);
        List<HumanQuestion.Option> options = question.options() == null ? List.of() : question.options();
        RadioButtonGroup<String> choice = new RadioButtonGroup<>();
        choice.setItems(options.stream().map(HumanQuestion.Option::label).toList());
        choice.setValue(defaultAnswer(question));
        choice.addValueChangeListener(e -> revalidate.get().run());
        row.add(choice);
        selections.put(question.id(),
                () -> choice.getValue() == null ? defaultAnswer(question) : choice.getValue());
        return row;
    }

    private static boolean criticalApproved(List<HumanQuestion> questions, Map<String, Supplier<String>> selections) {
        return questions.stream()
                .filter(question -> question.riskLevel() == RiskLevel.L5)
                .anyMatch(question -> {
                    List<HumanQuestion.Option> options = question.options();
                    return options != null && !options.isEmpty()
                            && options.get(0).label().equals(selections.get(question.id()).get());
                });
    }

    private VerticalLayout body(HumanQuestion question) {
        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        RiskLevel level = question.riskLevel();
        if (level != null) {
            McpRiskChip chip = new McpRiskChip(level).withTooltip(McpRiskChip.levelRationale(level));
            chip.getStyle().set("margin-bottom", "var(--lumo-space-xs)");
            body.add(chip);
        }
        Span text = new Span(question.question());
        text.getStyle().set("font-size", "var(--lumo-font-size-s)");
        body.add(text);
        String caution = cautionFor(level);
        if (caution != null) {
            Span cautionText = new Span(caution);
            cautionText.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("font-weight", "600")
                    .set("margin-top", "var(--lumo-space-xs)")
                    .set("color", level == RiskLevel.L5
                            ? "var(--lumo-error-text-color)" : "var(--lumo-secondary-text-color)");
            body.add(cautionText);
        }
        return body;
    }

    private static String cautionFor(RiskLevel level) {
        if (level == RiskLevel.L5) {
            return "Critical risk: may be irreversible or destroy data. Review the arguments.";
        }
        if (level == RiskLevel.L4) {
            return "High risk: can modify files, spend money, or change external state.";
        }
        return null;
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
