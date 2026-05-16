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
package org.springaicommunity.playground.webui.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hilerio.ace.AceEditor;
import com.hilerio.ace.AceMode;
import com.hilerio.ace.AceTheme;
import com.vaadin.flow.component.AttachEvent;
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
import org.springaicommunity.playground.SpringAiPlaygroundOptions;
import org.springaicommunity.playground.service.tool.runtime.JsToolExecutor.JsExecutionResult;
import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springaicommunity.playground.service.tool.ToolSpec.JsonSchemaType;
import org.springaicommunity.playground.service.tool.ToolSpec.ToolParamSpec;
import org.springaicommunity.playground.service.tool.ToolSpecService;
import org.springaicommunity.playground.service.tool.policy.SandboxPostureCalculator;
import org.springaicommunity.playground.webui.VaadinUtils;

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

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        attachEvent.getUI().getPage().executeJs("""
                window.PRETTIER_DEFAULT_OPTIONS = {
                  parser: "babel",
                  printWidth: 200,
                  tabWidth: 2,
                  semi: true,
                  singleQuote: true,
                  trailingComma: "es5",
                  proseWrap: "never"
                };
                """);
    }

    private final FlexLayout staticVariableContainer;
    private final List<StaticVariableForm> staticVariableForms;
    private final AceEditor ace;
    private final TextArea consoleTextArea;
    private final HorizontalLayout consoleStatusRow;
    private final Span consoleStatusBadge;
    private final Span consoleTimingBadge;
    private final Button testRunButton;
    private final Button formatButton;
    private final ObjectMapper objectMapper;
    private final ToolSpecService toolSpecService;
    private final Supplier<List<ToolParamSpec>> currentToolParamsSupplier;
    private final Supplier<String> currentToolNameSupplier;
    private final SandboxCapabilitiesView sandboxView;

    public JavascriptToolPlaygroundView(ObjectMapper objectMapper, ToolSpecService toolSpecService,
            SpringAiPlaygroundOptions options, SandboxPostureCalculator postureCalculator,
            Supplier<List<ToolParamSpec>> currentToolParamsSupplier,
            Supplier<String> currentToolNameSupplier) {
        this.objectMapper = objectMapper;
        this.toolSpecService = toolSpecService;
        this.currentToolParamsSupplier = currentToolParamsSupplier;
        this.currentToolNameSupplier = currentToolNameSupplier;
        this.staticVariableForms = new ArrayList<>();
        this.sandboxView = new SandboxCapabilitiesView(options, postureCalculator);

        setSizeFull();
        setPadding(false);
        setSpacing(true);

        ace = new AceEditor();
        ace.setMinlines(35);
        ace.setSizeFull();
        ace.setTheme(AceTheme.monokai);
        ace.setMode(AceMode.javascript);
        ace.setAutoComplete(true);
        ace.setLiveAutocompletion(true);
        ace.setUseWorker(true);
        ace.setShowInvisibles(true);
        ace.setTabSize(2);
        ace.setWrap(true);
        ace.setShowPrintMargin(false);
        ace.setPlaceholder("Type JavaScript code here…");
        String exampleJs = """
                /**
                 * Tool runtime — ECMAScript 2024 on GraalJS (inside the JVM).
                 * Not a browser or Node.js: the host bridges a curated set of globals.
                 * Per-tool policy (network / filesystem / classes) is set in the Sandbox panel above.
                 *
                 * ── Web Standard globals (always available) ──────────────────────────
                 *   fetch(url, init?)              WHATWG fetch — gated by Network mode
                 *   URL, URLSearchParams           WHATWG URL parsing
                 *   atob(b64), btoa(str)           Base64 encode / decode
                 *   crypto.randomUUID()            UUID v4
                 *   crypto.getRandomValues(buf)    fill a Uint8Array
                 *   crypto.subtle.digest|sign|importKey   SHA-256/384/512, HMAC-SHA256
                 *   console.log(...args)           captured to Debug Console below
                 *
                 * ── safety.* helpers (host-bridged) ──────────────────────────────────
                 *   safety.parser.html|yaml|csv|xml(text)          always available
                 *   safety.fs.readText|list|exists|stat|grep|
                 *            lineCount|slice|cut|sort|find         Filesystem mode ≥ read
                 *   safety.fs.writeText(path, text)                Filesystem mode = read+write
                 *   (all fs paths are resolved under the Base path set in the Sandbox panel)
                 *
                 * ── Java interop ─────────────────────────────────────────────────────
                 *   Java.type('java.lang|math|time|util|text.*')   allowlist — see application.yaml
                 *   Reflection / Thread / ClassLoader / ServiceLoader → denied
                 *
                 * ── Execution model ──────────────────────────────────────────────────
                 *   Your script runs in an async wrapper (top-level await OK), stateless per call.
                 *   You MUST return a value — it becomes the tool result sent back to the agent.
                 */

                // ── Example: Hello World — replace with your tool's logic ──
                const Instant = Java.type('java.time.Instant');

                return {
                  message: 'Hello World from Spring AI Playground 👋',
                  timestamp: new Date().toISOString(),
                  epochMilli: Instant.now().toEpochMilli(),
                  hint: 'Try fetch(url) · safety.parser.yaml(text) · safety.fs.readText(path)',
                };
                """;
        ace.setValue(exampleJs);
        ace.getElement().getStyle()
                .set("min-height", "400px")
                .set("font-size", "1rem");

        consoleTextArea = new TextArea();
        consoleTextArea.setReadOnly(true);
        consoleTextArea.setWidthFull();
        consoleTextArea.addClassName("debug-console");
        consoleTextArea.setPlaceholder("No run yet. Click Test Run to execute the JS code and see output here.");
        consoleTextArea.getStyle().set("flex-grow", "1");
        consoleTextArea.getElement().getStyle().set("min-height", "300px");

        this.consoleStatusBadge = new Span();
        this.consoleTimingBadge = new Span();
        this.consoleTimingBadge.getElement().setAttribute("theme", "badge contrast pill small");
        this.consoleStatusRow = new HorizontalLayout(this.consoleStatusBadge, this.consoleTimingBadge);
        this.consoleStatusRow.setSpacing(false);
        this.consoleStatusRow.getStyle().set("gap", "0.4em");
        this.consoleStatusRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        this.consoleStatusRow.setVisible(false);

        VerticalLayout bottomContainer = new VerticalLayout();
        bottomContainer.setPadding(false);
        bottomContainer.setSpacing(true);
        bottomContainer.setWidthFull();
        HorizontalLayout consoleHeader = new HorizontalLayout(new H5("Debug Console"), this.consoleStatusRow);
        consoleHeader.setAlignItems(HorizontalLayout.Alignment.BASELINE);
        consoleHeader.setSpacing(false);
        consoleHeader.getStyle().set("gap", "var(--lumo-space-m)");
        bottomContainer.add(consoleHeader, consoleTextArea);
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

        this.staticVariableContainer = new FlexLayout();
        staticVariableContainer.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        staticVariableContainer.setWidthFull();

        VerticalLayout staticVarsSection = new VerticalLayout(staticVarsHeader, staticVariableContainer);
        staticVarsSection.setPadding(false);
        staticVarsSection.setSpacing(true);
        staticVarsSection.setWidthFull();

        HorizontalLayout actionBar = new HorizontalLayout(testRunButton, clearButton, formatButton);

        actionBar.setPadding(false);
        actionBar.setSpacing(false);
        actionBar.getStyle().set("gap", "var(--lumo-space-s)");
        actionBar.setWidthFull();

        add(new H4("Tool Action"), this.sandboxView, staticVarsSection, new H5("JS Code Editor"), ace, actionBar, bottomContainer);
        setFlexGrow(1, ace);
        setFlexGrow(1, bottomContainer);
        addDefaultStaticVariableForm();
    }

    private void addDefaultStaticVariableForm() {
        addStaticVariableForm(1);
    }

    private StaticVariableForm addStaticVariableForm(int index) {
        StaticVariableForm staticVariableForm = new StaticVariableForm(index, this::removeStaticVariableForm,
                () -> addStaticVariableForm(staticVariableForms.size() + 1));
        staticVariableForms.add(staticVariableForm);
        staticVariableContainer.add(staticVariableForm);
        updateStaticVariableButtons();
        return staticVariableForm;
    }

    private void removeStaticVariableForm(StaticVariableForm form) {
        if (staticVariableForms.size() <= 1) {
            return;
        }

        staticVariableContainer.remove(form);
        staticVariableForms.remove(form);

        for (int i = 0; i < staticVariableForms.size(); i++) {
            staticVariableForms.get(i).updateIndex(i + 1);
        }

        updateStaticVariableButtons();
    }

    private void updateStaticVariableButtons() {
        int size = staticVariableForms.size();
        boolean hasMultiple = size > 1;
        for (int i = 0; i < size; i++) {
            StaticVariableForm form = staticVariableForms.get(i);
            boolean isLast = (i == size - 1);
            form.setAddButtonVisible(isLast);
            form.setDeleteButtonVisible(hasMultiple);
        }
    }

    public List<Map.Entry<String, String>> getStaticVariables() {
        return staticVariableForms.stream().map(staticVariableForm -> {
            String key = staticVariableForm.getKey().trim();
            return !key.isBlank() ? Map.entry(key, staticVariableForm.getValue()) : null;
        }).filter(Objects::nonNull).toList();
    }

    public ToolSpec.SandboxOverrides currentSandboxOverrides() {
        return this.sandboxView.currentOverrides();
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
                error -> UI.getCurrent().access(() -> {
                    VaadinUtils.showErrorNotification("JS Error: " + error);
                    formatButton.setEnabled(true);
                })
        );
    }

    private Object convertValueForType(Object testValue, JsonSchemaType type) throws JsonProcessingException {
        if (Objects.nonNull(testValue) && testValue instanceof String strValue && !strValue.isBlank()) {
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
        return this.testRunButton.isEnabled() && runTestJavascript();
    }

    private boolean runTestJavascript() {
        this.testRunButton.setEnabled(false);
        consoleTextArea.clear();
        this.consoleStatusRow.setVisible(false);
        long start = System.currentTimeMillis();

        try {
            LinkedHashMap<String, Object> toolParams = new LinkedHashMap<>();
            for (ToolParamSpec spec : this.currentToolParamsSupplier.get()) {
                toolParams.put(spec.name(), convertValueForType(spec.testValue(), spec.type()));
            }
            String resolvedName = this.currentToolNameSupplier.get();
            if (resolvedName == null || resolvedName.isBlank()) {
                resolvedName = "draft";
            }
            JsExecutionResult jsExecutionResult =
                    toolSpecService.executeTool(resolvedName, getStaticVariables(), getCurrentJsCode(),
                            toolParams, currentSandboxOverrides());
            VaadinUtils.getUi(this).access(() -> {
                long end = System.currentTimeMillis();
                consoleTextArea.setValue(buildResultString(jsExecutionResult));
                updateConsoleStatusRow(jsExecutionResult.isOk(), start, end);
                if (!jsExecutionResult.isOk()) {
                    VaadinUtils.showErrorNotification("Error Details:\n" + jsExecutionResult.error());
                }
            });
            return jsExecutionResult.isOk();
        } catch (Exception ex) {
            VaadinUtils.showErrorNotification("Exception: " + ex.getMessage());
            return false;
        } finally {
            this.testRunButton.setEnabled(true);
        }
    }

    private static String buildResultString(JsExecutionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(result.debugInfo()).append("\n");
        if (result.isOk()) {
            sb.append("\nResult:\n").append(result.result());
        } else {
            sb.append("\nError Details:\n").append(result.error());
        }
        return sb.toString();
    }

    private void updateConsoleStatusRow(boolean ok, long startMs, long endMs) {
        this.consoleStatusBadge.setText(ok ? "✓ Success" : "✗ Error");
        this.consoleStatusBadge.getElement().setAttribute(
                "theme", ok ? "badge success pill small" : "badge error pill small");
        this.consoleTimingBadge.setText(formatDuration(endMs - startMs));
        this.consoleTimingBadge.getElement().setAttribute("title",
                "Started " + formatTs(startMs) + "  →  ended " + formatTs(endMs));
        this.consoleStatusRow.setVisible(true);
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
        this.staticVariableForms.clear();
        this.staticVariableContainer.removeAll();
        if (!staticVariables.isEmpty()) {
            for (int i = 0; i < staticVariables.size(); i++)
                addStaticVariableForm(i).update(staticVariables.get(i));
        } else {
            addDefaultStaticVariableForm();
        }
        this.ace.setValue(code);
    }

    public void applySandboxOverrides(ToolSpec.SandboxOverrides overrides) {
        this.sandboxView.applyOverrides(overrides);
    }
}