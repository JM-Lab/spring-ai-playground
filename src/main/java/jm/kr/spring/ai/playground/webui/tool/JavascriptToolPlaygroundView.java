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
package jm.kr.spring.ai.playground.webui.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hilerio.ace.AceEditor;
import com.hilerio.ace.AceMode;
import com.hilerio.ace.AceTheme;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.component.textfield.TextArea;
import jm.kr.spring.ai.playground.service.tool.JsToolExecutor.JsExecutionResult;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.JsonSchemaType;
import jm.kr.spring.ai.playground.service.tool.ToolSpec.ToolParamSpec;
import jm.kr.spring.ai.playground.service.tool.ToolSpecService;
import jm.kr.spring.ai.playground.webui.VaadinUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@JavaScript("./prettier-standalone.js")
@JavaScript("./prettier-plugin-babel.js")
@JavaScript("./prettier-plugin-estree.js")
public class JavascriptToolPlaygroundView extends VerticalLayout {

    private final FlexLayout staticVarsContainer;
    private final List<StaticVariableForm> staticVarForms;
    private final AceEditor ace;
    private final TextArea consoleTextArea;
    private final Button testRunButton;
    private final Button formatButton;
    private final ObjectMapper objectMapper;
    private final ToolSpecService toolSpecService;
    private final Supplier<List<ToolParamSpec>> currentToolParamsSupplier;

    public JavascriptToolPlaygroundView(ObjectMapper objectMapper, ToolSpecService toolSpecService,
            Supplier<List<ToolParamSpec>> currentToolParamsSupplier) {
        this.objectMapper = objectMapper;
        this.toolSpecService = toolSpecService;
        this.currentToolParamsSupplier = currentToolParamsSupplier;
        this.staticVarForms = new ArrayList<>();

        setSizeFull();
        setPadding(false);
        setSpacing(true);

        ace = new AceEditor();
        ace.setMinlines(25);
        ace.setSizeFull();
        ace.setTheme(AceTheme.monokai);
        ace.setMode(AceMode.javascript);
        ace.setAutoComplete(true);
        ace.setLiveAutocompletion(true);
        ace.setUseWorker(true);
        ace.setPlaceholder("Type JavaScript code here…");
        String exampleJs = """
                /**
                 * NOTE TO DEVELOPERS:
                 * This code runs on JavaScript (ECMAScript 2023) inside the JVM.
                 * It is NOT a browser or Node.js environment.
                 *
                 * Unavailable APIs:
                 * - Browser APIs: fetch, XMLHttpRequest, DOM (window/document), timers, etc.
                 * - Node.js APIs: require(), module, process, built-in modules, etc.
                 *
                 * Available features:
                 * - Java interop via Java.type() (e.g., java.net.*, java.io.*, etc.)
                 * - console.log (output captured by the host)
                 *
                 * Execution model:
                 * - Your script is wrapped in an async function.
                 * - The value you return becomes the final tool result.
                 */
                function add(a,b){ return a+b }
                add(2,3);
                """;
        ace.setValue(exampleJs);
        ace.getElement().getStyle()
                .set("min-height", "400px")
                .set("font-size", "1rem");

        consoleTextArea = new TextArea();
        consoleTextArea.setReadOnly(true);
        consoleTextArea.setWidthFull();
        consoleTextArea.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("border", "none")
                .set("background-color", "var(--lumo-contrast-5pct)")
                .set("flex-grow", "1");
        consoleTextArea.getElement().getStyle().set("min-height", "300px");

        VerticalLayout bottomContainer = new VerticalLayout();
        bottomContainer.setPadding(false);
        bottomContainer.setSpacing(true);
        bottomContainer.setWidthFull();
        bottomContainer.add(new H5("Debug Console"), consoleTextArea);
        bottomContainer.getStyle().set("display", "flex").set("flex-direction", "column");

        this.testRunButton = new Button("Test Run", VaadinIcon.PLAY.create(), e -> runTestJavascript());
        Button clearButton = new Button("Clear", VaadinIcon.ERASER.create(), e -> ace.clear());
        this.formatButton = new Button("Format", VaadinIcon.MAGIC.create(), e -> formatCodeWithPrettier());
        getElement().executeJs("""
                window.waitForPrettier = new Promise((resolve) => {
                    const check = setInterval(() => {
                        if (window.prettier && window.prettierPlugins) {
                            clearInterval(check);
                            resolve();
                        }
                    }, 50);
                });
                """);

        Span staticHint = new Span(
                "Optional key–value pairs available in your code as global variables, injected into the action context");
        staticHint.getElement().getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout staticVarsHeader =
                new HorizontalLayout(new H5("Static Variables"), staticHint);
        staticVarsHeader.setAlignItems(Alignment.BASELINE);

        this.staticVarsContainer = new FlexLayout();
        staticVarsContainer.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        staticVarsContainer.setWidthFull();

        VerticalLayout staticVarsSection = new VerticalLayout(staticVarsHeader, staticVarsContainer);
        staticVarsSection.setPadding(false);
        staticVarsSection.setSpacing(true);
        staticVarsSection.setWidthFull();
        HorizontalLayout actionBar = new HorizontalLayout(testRunButton, clearButton, formatButton);

        actionBar.setPadding(false);
        actionBar.setSpacing(false);
        actionBar.getStyle().set("gap", "var(--lumo-space-s)");
        actionBar.setWidthFull();

        add(new H4("Tool Action"), staticVarsSection, new H5("JS Code Editor"), ace, actionBar, bottomContainer);
        setFlexGrow(1, ace);
        setFlexGrow(1, bottomContainer);
        addDefaultStaticVariableForm();
    }

    private void addDefaultStaticVariableForm() {
        addStaticVariableForm(1);
    }

    private StaticVariableForm addStaticVariableForm(int index) {
        StaticVariableForm staticVariableForm = new StaticVariableForm(index, this::removeStaticVariableForm,
                () -> addStaticVariableForm(staticVarForms.size() + 1));
        staticVarForms.add(staticVariableForm);
        staticVarsContainer.add(staticVariableForm);
        updateStaticVariableButtons();
        return staticVariableForm;
    }

    private void removeStaticVariableForm(StaticVariableForm form) {
        if (staticVarForms.size() <= 1) {
            return;
        }

        staticVarsContainer.remove(form);
        staticVarForms.remove(form);

        for (int i = 0; i < staticVarForms.size(); i++) {
            staticVarForms.get(i).updateIndex(i + 1);
        }

        updateStaticVariableButtons();
    }

    private void updateStaticVariableButtons() {
        boolean hasMultiple = staticVarForms.size() > 1;

        for (int i = 0; i < staticVarForms.size(); i++) {
            StaticVariableForm form = staticVarForms.get(i);
            boolean isLast = (i == staticVarForms.size() - 1);

            form.setAddButtonVisible(isLast);
            form.setDeleteButtonVisible(hasMultiple);
        }
    }

    public List<Map.Entry<String, String>> getStaticVariables() {
        return staticVarForms.stream().map(staticVariableForm -> {
            String key = staticVariableForm.getKey().trim();
            return !key.isBlank() ? Map.entry(key, staticVariableForm.getValue()) : null;
        }).filter(Objects::nonNull).toList();
    }

    private void formatCodeWithPrettier() {
        formatButton.setEnabled(false);
        String code = ace.getValue();

        PendingJavaScriptResult result = ace.getElement().executeJs("""
                return (async () => {
                    try {
                        const formatted = await prettier.format($0, {
                            parser: "babel",
                            plugins: prettierPlugins,
                            semi: true,
                            trailingComma: "es5",
                            singleQuote: true,
                            printWidth: 80,
                            tabWidth: 2,
                            bracketSpacing: true
                        });
                        return formatted;
                    } catch (err) {
                        return "ERROR: " + err.message;
                    }
                })();
                """, code);

        result.then(String.class,
                formatted -> UI.getCurrent().access(() -> {
                    if (formatted.startsWith("ERROR:")) {
                        VaadinUtils.showErrorNotification("Formatting Failure: " + formatted);
                    } else {
                        ace.setValue(formatted);
                        ace.focus();
                    }
                    formatButton.setEnabled(true);
                }),
                error -> {
                    UI.getCurrent().access(() -> {
                        VaadinUtils.showErrorNotification("JS Error: " + error);
                        formatButton.setEnabled(true);
                    });
                }
        );
    }

    private Object convertValueForType(Object testValue, JsonSchemaType type) throws JsonProcessingException {
        if (testValue instanceof String strValue) {
            if (testValue == null || strValue.trim().isEmpty()) {
                return null;
            }
            return switch (type) {
                case STRING -> strValue;
                case NUMBER -> Double.parseDouble(strValue);
                case INTEGER -> Long.parseLong(strValue);
                case BOOLEAN -> Boolean.parseBoolean(strValue);
                case ARRAY -> this.objectMapper.readValue(strValue,
                        new TypeReference<List<Object>>() {});
                case OBJECT -> this.objectMapper.readValue(strValue,
                        new TypeReference<Map<String, Object>>() {});
            };
        }
        return testValue;
    }

    public boolean runTest() {
        return this.testRunButton.isEnabled() ? runTestJavascript() : false;
    }

    private boolean runTestJavascript() {
        this.testRunButton.setEnabled(false);
        consoleTextArea.clear();
        long start = System.currentTimeMillis();

        try {
            LinkedHashMap<String, Object> toolParams = new LinkedHashMap<>();
            for (ToolParamSpec spec : this.currentToolParamsSupplier.get()) {
                toolParams.put(spec.name(), convertValueForType(spec.testValue(), spec.type()));
            }
            JsExecutionResult jsExecutionResult =
                    toolSpecService.executeTool("", getStaticVariables(), getCurrentJsCode(), toolParams);
            VaadinUtils.getUi(this).access(() -> {
                long end = System.currentTimeMillis();
                String formattedResult = buildResultString(start, end, jsExecutionResult);
                consoleTextArea.setValue(formattedResult);
            });
            return jsExecutionResult.isOk();
        } catch (Exception ex) {
            VaadinUtils.showErrorNotification("Exception: " + ex.getMessage());
            return false;
        } finally {
            this.testRunButton.setEnabled(true);
        }
    }

    private String buildResultString(long start, long end, JsExecutionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.debugInfo()).append("\n\n");
        sb.append("Status: ").append(result.isOk() ? "Success" : "Error").append("\n");
        sb.append("----------------------------------------\n");
        sb.append("Start Time:    ").append(formatTs(start)).append("\n");
        sb.append("End Time:      ").append(formatTs(end)).append("\n");
        sb.append("Elapsed Time:  ").append(formatDuration(end - start)).append("\n");
        sb.append("----------------------------------------\n\n");

        if (result.isOk()) {
            sb.append("Result:\n").append(result.result());
        } else {
            String errorMessage = "Error Details:\n" + result.error();
            sb.append(errorMessage);
            VaadinUtils.getUi(this).access(() -> VaadinUtils.showErrorNotification(errorMessage));
        }
        return sb.toString();
    }

    private String formatTs(long ts) {
        return Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    private String formatDuration(long ms) {
        return ms + " ms";
    }

    public String getCurrentJsCode() {
        return ace.getValue();
    }

    public void updateContents(List<Map.Entry<String, String>> staticVariables, String code) {
        this.staticVarForms.clear();
        this.staticVarsContainer.removeAll();
        int i = 0;
        staticVariables.forEach(addStaticVariableForm(++i)::update);
        this.ace.setValue(code);
    }
}