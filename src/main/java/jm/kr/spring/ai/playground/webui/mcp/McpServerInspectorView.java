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
package jm.kr.spring.ai.playground.webui.mcp;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import io.modelcontextprotocol.spec.McpSchema;
import jm.kr.spring.ai.playground.service.mcp.McpServerInfo;
import jm.kr.spring.ai.playground.service.mcp.client.McpClientService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@CssImport("./playground/mcp-inspector-styles.css")
public class McpServerInspectorView extends VerticalLayout {

    public record ToolInfo(String name, String description, List<String> required, Map<String, Object> arguments) {}

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ARG_REQUIRED = "Required field";

    private final McpServerInfo serverInfo;
    private final McpClientService clientService;
    private final TextArea historyArea;

    public McpServerInspectorView(McpServerInfo serverInfo, McpClientService clientService,
            List<McpSchema.Tool> tools) {
        this.serverInfo = Objects.requireNonNull(serverInfo);
        this.clientService = Objects.requireNonNull(clientService);

        setSizeFull();
        setPadding(false);
        setSpacing(false);

        Grid<ToolInfo> toolsGrid = createToolsGrid(tools);
        historyArea = createHistoryArea();
        historyArea.setHeight("20em");
        historyArea.setMinHeight("20em");
        historyArea.setMaxHeight("20em");

        add(createHeader(), createTabs(), toolsGrid, historyArea);
    }

    private Component createHeader() {
        H4 logo = new H4("MCP Inspector");
        logo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");

        HorizontalLayout headLayout = new HorizontalLayout(logo);
        headLayout.setWidthFull();
        headLayout.setPadding(true);
        headLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        headLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        return headLayout;
    }

    private Tabs createTabs() {
        Tabs tabs = new Tabs(
                tab(VaadinIcon.TOOLS, "Tools")
//                , tab(VaadinIcon.DATABASE, "Resources"),
//                tab(VaadinIcon.CLIPBOARD_TEXT, "Prompts"),
//                tab(VaadinIcon.CLOUD_O, "Ping"),
//                tab(VaadinIcon.COG, "Roots"),
//                tab(VaadinIcon.CUBES, "Sampling")
        );
        tabs.setWidthFull();
        tabs.setSelectedIndex(0);
        return tabs;
    }

    private static Tab tab(VaadinIcon icon, String caption) {
        return new Tab(icon.create(), new Text(caption));
    }

    private Grid<ToolInfo> createToolsGrid(List<McpSchema.Tool> tools) {
        Grid<ToolInfo> grid = new Grid<>(ToolInfo.class, false);
        grid.setWidthFull();
        grid.setHeightFull();
        grid.getStyle().set("max-height", "460px").set("overflow-y", "auto").set("min-height", "0");
        grid.addClassName("wrap-grid");
        grid.setAllRowsVisible(true);

        Map<ToolInfo, Map<String, TextField>> fieldCache = new HashMap<>();

        List<ToolInfo> items = tools.stream().map(this::toToolInfo).toList();
        grid.setItems(items);

        grid.addColumn(tool -> items.indexOf(tool) + 1).setWidth("60px").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER);

        grid.addColumn(callButtonRenderer(fieldCache)).setHeader("Action").setWidth("80px").setFlexGrow(0);

        grid.addColumn(ToolInfo::name).setHeader("Name").setWidth("200px").setFlexGrow(0);
        grid.addColumn(ToolInfo::description).setHeader("Description").setWidth("400px").setFlexGrow(1);

        grid.addColumn(argumentRenderer(fieldCache)).setHeader("Arguments").setFlexGrow(2);

        return grid;
    }

    private ComponentRenderer<Button, ToolInfo> callButtonRenderer(Map<ToolInfo, Map<String, TextField>> fieldCache) {
        return new ComponentRenderer<>(tool -> {
            Button callToolButton = new Button(VaadinIcon.PLAY_CIRCLE_O.create());
            callToolButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            callToolButton.getElement().setProperty("title", "Call Tool");
            callToolButton.getStyle().set("padding", "0").set("margin", "0 auto");
            callToolButton.addClickListener(__ -> handleCall(tool, fieldCache.get(tool)));
            return callToolButton;
        });
    }

    private ComponentRenderer<Component, ToolInfo> argumentRenderer(Map<ToolInfo, Map<String, TextField>> fieldCache) {
        return new ComponentRenderer<>(tool -> {
            Div scroll = new Div();
            scroll.getStyle().set("max-height", "130px").set("overflow-y", "auto");

            VerticalLayout layout = new VerticalLayout();
            layout.setPadding(false);
            layout.setSpacing(false);

            Map<String, TextField> fields = new LinkedHashMap<>();
            fieldCache.put(tool, fields);

            tool.arguments().forEach((key, value) -> {
                TextField tf = new TextField(key);
                tf.setWidthFull();
                tf.setPlaceholder(Objects.toString(value, ""));
                if (tool.required().contains(key)) tf.setRequiredIndicatorVisible(true);
                fields.put(key, tf);
                layout.add(tf);
            });

            scroll.add(layout);
            return scroll;
        });
    }

    private void handleCall(ToolInfo tool, Map<String, TextField> fields) {
        Map<String, Object> args = new LinkedHashMap<>();

        for (var entry : fields.entrySet()) {
            String k = entry.getKey();
            TextField tf = entry.getValue();
            String v = tf.getValue().trim();

            boolean required = tool.required().contains(k);
            if (required && v.isEmpty()) {
                tf.setInvalid(true);
                tf.setErrorMessage(ARG_REQUIRED);
                return;
            } else {
                tf.setInvalid(false);
                tf.setErrorMessage(null);
            }
            args.put(k, v);
        }

        writeLog("[%s] Executing %s result:%n".formatted(now(), tool.name()));

        try {
            String result = clientService
                    .callTool(serverInfo, tool.name(), args, null)
                    .map(McpSchema.CallToolResult::content)
                    .map(Object::toString)
                    .orElse("No result");
            writeLog(result + System.lineSeparator());
        } catch (Exception ex) {
            writeLog("Error: " + ex.getMessage() + System.lineSeparator());
        } finally {
            writeLog(System.lineSeparator());
        }
    }

    private static TextArea createHistoryArea() {
        TextArea ta = new TextArea("Execution History");
        ta.setId("historyArea");
        ta.setReadOnly(true);
        ta.setWidthFull();
        ta.setHeightFull();
        return ta;
    }

    private void writeLog(String text) {
        historyArea.setValue(historyArea.getValue() + text);
        historyArea.getElement().executeJs(
                "this.inputElement && (this.inputElement.scrollTop = this.inputElement.scrollHeight);");
    }

    private ToolInfo toToolInfo(McpSchema.Tool tool) {
        String name = Optional.ofNullable(tool.name()).orElse(tool.title());
        Map<String, Object> args = extractArguments(tool.inputSchema());
        List<String> req = Optional.ofNullable(tool.inputSchema().required()).orElseGet(List::of);
        return new ToolInfo(name, tool.description(), req, args);
    }

    private Map<String, Object> extractArguments(McpSchema.JsonSchema schema) {
        if (schema == null || schema.properties() == null) return Map.of();

        List<String> required = Optional.ofNullable(schema.required()).orElse(List.of());

        return schema.properties().entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, e -> defaultValueOf(e.getValue(), required.contains(e.getKey())),
                        (a, b) -> b, LinkedHashMap::new));
    }

    private Object defaultValueOf(Object propSchema, boolean required) {
        if (propSchema instanceof Map<?, ?> map) {
            Object d = map.get("description");
            if (d != null) return d.toString();
        }
        return required ? "<required>" : "";
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }
}