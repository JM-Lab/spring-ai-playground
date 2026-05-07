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
package org.springaicommunity.playground.webui.mcp.inspector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import io.modelcontextprotocol.spec.McpSchema;
import org.springaicommunity.playground.webui.VaadinUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InspectorHelpers {

    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final String ARG_REQUIRED = "Required field";
    public static final ObjectMapper INSPECTOR_OBJECT_MAPPER = new ObjectMapper();

    private InspectorHelpers() {}

    public record ToolInfo(
            String displayTitle,
            String name,
            String description,
            McpSchema.ToolAnnotations annotations,
            List<String> required,
            Map<String, Map<String, Object>> propertySchemas) {}

    public static ToolInfo toToolInfo(McpSchema.Tool tool) {
        String displayTitle = pickFirstNonBlank(annotationsTitle(tool.annotations()), tool.title());
        String name = Optional.ofNullable(tool.name()).orElse(displayTitle == null ? "" : displayTitle);
        Map<String, Map<String, Object>> propertySchemas = extractPropertySchemas(tool.inputSchema());
        List<String> req = Optional.ofNullable(tool.inputSchema())
                .map(McpSchema.JsonSchema::required)
                .orElseGet(List::of);
        return new ToolInfo(displayTitle, name, tool.description(), tool.annotations(), req, propertySchemas);
    }

    public static String annotationsTitle(McpSchema.ToolAnnotations annotations) {
        return annotations == null ? null : annotations.title();
    }

    public static String pickFirstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> extractPropertySchemas(McpSchema.JsonSchema schema) {
        if (schema == null || schema.properties() == null) return Map.of();
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : schema.properties().entrySet()) {
            Map<String, Object> propSchema =
                    e.getValue() instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            out.put(e.getKey(), propSchema);
        }
        return out;
    }

    public static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    public static Number asNumber(Object o) {
        if (o instanceof Number n) return n;
        if (o instanceof String s) {
            try { return Double.valueOf(s); } catch (NumberFormatException ignore) { return null; }
        }
        return null;
    }

    public static String now() {
        return LocalDateTime.now().format(TIME_FMT);
    }

    public static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    public static List<String> extractTemplateVars(String uriTemplate) {
        if (uriTemplate == null) return List.of();
        List<String> vars = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([^}]+)}").matcher(uriTemplate);
        while (m.find()) {
            String token = m.group(1);
            if (!token.isBlank() && !vars.contains(token)) vars.add(token);
        }
        return vars;
    }

    public static String tryPrettyJson(String maybeJson) {
        if (maybeJson == null || maybeJson.isBlank()) return maybeJson == null ? "" : maybeJson;
        String trimmed = maybeJson.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return maybeJson;
        try {
            JsonNode parsed = INSPECTOR_OBJECT_MAPPER.readTree(trimmed);
            return INSPECTOR_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            return maybeJson;
        }
    }

    public static String prettyPrint(Object value) {
        try {
            return INSPECTOR_OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    public static Component simpleSectionLabel(String text) {
        Span label = new Span(text);
        label.getStyle()
                .set("font-size", "0.72em")
                .set("font-weight", "600")
                .set("letter-spacing", "0.06em")
                .set("text-transform", "uppercase")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin", "0.4em 0 0.2em");
        return label;
    }

    public static HorizontalLayout simpleStatusHeader(String label, String name, long elapsedMs, boolean error) {
        Span badge = new Span(label);
        String badgeBg = error ? "var(--lumo-error-color-10pct)" : "var(--lumo-success-color-10pct)";
        String badgeColor = error ? "var(--lumo-error-text-color)" : "var(--lumo-success-text-color)";
        badge.getStyle()
                .set("font-size", "0.7em")
                .set("font-weight", "600")
                .set("letter-spacing", "0.05em")
                .set("padding", "0.15em 0.6em")
                .set("border-radius", "999px")
                .set("background-color", badgeBg)
                .set("color", badgeColor);

        Span elapsed = new Span(elapsedMs + " ms");
        elapsed.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.85em")
                .set("color", "var(--lumo-secondary-text-color)");

        Span nameSpan = new Span(name == null ? "" : name);
        nameSpan.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.85em")
                .set("color", "var(--lumo-body-text-color)")
                .set("word-break", "break-all");

        Span timestamp = new Span(now());
        timestamp.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-left", "auto");

        HorizontalLayout row = new HorizontalLayout(badge, nameSpan, elapsed, timestamp);
        row.setWidthFull();
        row.setPadding(false);
        row.setSpacing(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("gap", "0.7em").set("margin-bottom", "0.6em");
        return row;
    }

    public static Span infoBadge(String label, String color) {
        Span badge = new Span(label);
        badge.getStyle()
                .set("font-size", "0.75em")
                .set("padding", "0.1em 0.5em")
                .set("border-radius", "999px")
                .set("border", "1px solid " + color)
                .set("color", color)
                .set("white-space", "nowrap");
        return badge;
    }

    public static String humanizeBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    public static Div codeBlock(String text, boolean error) {
        boolean blank = text == null || text.trim().isEmpty();
        String displayText = blank ? "(empty)" : text;
        Pre pre = new Pre(displayText);
        pre.getStyle()
                .set("white-space", "pre-wrap")
                .set("word-break", "break-word")
                .set("margin", "0")
                .set("padding", "0.6em 2.5em 0.6em 0.8em")
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.85em")
                .set("line-height", "1.4")
                .set("min-height", "1.4em")
                .set("color", blank ? "var(--lumo-secondary-text-color)"
                        : (error ? "var(--lumo-error-text-color)" : "var(--lumo-body-text-color)"));

        Div container = new Div(pre);
        container.getStyle()
                .set("position", "relative")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("background-color", error ? "var(--lumo-error-color-10pct)"
                        : "var(--lumo-contrast-5pct)");

        if (!blank) {
            Button copy = iconCopyButton(text);
            copy.getStyle()
                    .set("position", "absolute")
                    .set("top", "0.3em")
                    .set("right", "0.3em");
            container.add(copy);
        }
        return container;
    }

    public static Button iconCopyButton(String payload) {
        Button btn = new Button(VaadinIcon.COPY_O.create());
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
        btn.setTooltipText("Copy to clipboard");
        btn.addClickListener(e -> {
            UI ui = UI.getCurrent();
            if (ui == null || payload == null) return;
            ui.getPage().executeJs("navigator.clipboard.writeText($0)", payload);
            VaadinUtils.showInfoNotification("Copied to clipboard");
        });
        return btn;
    }

    public static HorizontalLayout annotationBadges(McpSchema.ToolAnnotations annotations) {
        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(false);
        row.setPadding(false);
        row.getStyle().set("gap", "0.3em").set("flex-wrap", "wrap").set("margin-top", "0.3em");
        if (annotations == null) return row;
        addBadge(row, Boolean.TRUE.equals(annotations.readOnlyHint()), "read-only", "var(--lumo-primary-color)");
        addBadge(row, Boolean.TRUE.equals(annotations.destructiveHint()), "destructive", "var(--lumo-error-color)");
        addBadge(row, Boolean.TRUE.equals(annotations.idempotentHint()), "idempotent", "var(--lumo-contrast-50pct)");
        addBadge(row, Boolean.TRUE.equals(annotations.openWorldHint()), "open-world",
                "var(--lumo-warning-text-color)");
        return row;
    }

    public static void addBadge(HorizontalLayout row, boolean show, String label, String color) {
        if (!show) return;
        row.add(infoBadge(label, color));
    }

    public static HorizontalLayout resourceBadges(McpSchema.Resource res) {
        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(false);
        row.setPadding(false);
        row.getStyle().set("gap", "0.3em").set("flex-wrap", "wrap");
        if (res.mimeType() != null && !res.mimeType().isBlank()) {
            row.add(infoBadge(res.mimeType(), "var(--lumo-primary-color)"));
        }
        if (res.size() != null) {
            row.add(infoBadge(humanizeBytes(res.size()), "var(--lumo-contrast-50pct)"));
        }
        return row;
    }

    public static AbstractField<?, ?> buildArgField(String key, Map<String, Object> schema, boolean required) {
        String type = asString(schema.get("type"));
        Object enumObj = schema.get("enum");
        String description = asString(schema.get("description"));

        if (enumObj instanceof List<?> enumList && !enumList.isEmpty()) {
            ComboBox<String> combo = new ComboBox<>(key);
            List<String> opts = new ArrayList<>();
            for (Object e : enumList) opts.add(String.valueOf(e));
            combo.setItems(opts);
            combo.setWidthFull();
            combo.setPlaceholder("Select…");
            if (description != null) combo.setHelperText(description);
            if (required) combo.setRequiredIndicatorVisible(true);
            return combo;
        }

        if ("boolean".equalsIgnoreCase(type)) {
            Checkbox cb = new Checkbox(key);
            if (description != null) cb.setHelperText(description);
            return cb;
        }

        if ("integer".equalsIgnoreCase(type) || "number".equalsIgnoreCase(type)) {
            NumberField nf = new NumberField(key);
            nf.setWidthFull();
            Number min = asNumber(schema.get("minimum"));
            Number max = asNumber(schema.get("maximum"));
            if (min != null) nf.setMin(min.doubleValue());
            if (max != null) nf.setMax(max.doubleValue());
            if ("integer".equalsIgnoreCase(type)) nf.setStep(1);
            if (description != null) nf.setHelperText(description);
            if (required) nf.setRequiredIndicatorVisible(true);
            return nf;
        }

        if ("array".equalsIgnoreCase(type) || "object".equalsIgnoreCase(type)) {
            TextArea ta = new TextArea(key);
            ta.setWidthFull();
            ta.setPlaceholder(type.equalsIgnoreCase("array") ? "[ … ]" : "{ … }");
            ta.setHeight("5em");
            ta.getStyle().set("font-family", "var(--lumo-font-family-monospace)");
            if (description != null) ta.setHelperText(description + " (JSON)");
            else ta.setHelperText("JSON");
            if (required) ta.setRequiredIndicatorVisible(true);
            return ta;
        }

        TextField tf = new TextField(key);
        tf.setWidthFull();
        if (description != null) tf.setHelperText(description);
        if (required) tf.setRequiredIndicatorVisible(true);
        return tf;
    }

    public static Object readArgValue(String key, AbstractField<?, ?> field, boolean required) {
        if (field instanceof Checkbox cb) return cb.getValue();
        if (field instanceof NumberField nf) {
            Double v = nf.getValue();
            if (v == null) {
                if (required) throw new IllegalArgumentException(ARG_REQUIRED + ": " + key);
                return null;
            }
            return v == v.intValue() ? Integer.valueOf(v.intValue()) : v;
        }
        if (field instanceof ComboBox<?> combo) {
            Object v = combo.getValue();
            if (v == null || (v instanceof String s && s.isBlank())) {
                if (required) throw new IllegalArgumentException(ARG_REQUIRED + ": " + key);
                return null;
            }
            return v;
        }
        if (field instanceof TextArea ta) {
            String raw = ta.getValue() == null ? "" : ta.getValue().trim();
            if (raw.isEmpty()) {
                if (required) throw new IllegalArgumentException(ARG_REQUIRED + ": " + key);
                return null;
            }
            try {
                return INSPECTOR_OBJECT_MAPPER.readValue(raw, Object.class);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid JSON for '" + key + "': " + e.getOriginalMessage());
            }
        }
        if (field instanceof TextField tf) {
            String raw = tf.getValue() == null ? "" : tf.getValue().trim();
            if (raw.isEmpty()) {
                if (required) throw new IllegalArgumentException(ARG_REQUIRED + ": " + key);
                return null;
            }
            return raw;
        }
        return ((HasValue<?, ?>) field).getValue();
    }

    public static Component buildContentBlock(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent text) {
            return codeBlock(tryPrettyJson(text.text()), false);
        }
        if (content instanceof McpSchema.ImageContent image) {
            return imageBlock(image);
        }
        if (content instanceof McpSchema.EmbeddedResource embedded) {
            return codeBlock(prettyPrint(embedded), false);
        }
        return codeBlock(String.valueOf(content), false);
    }

    public static Component imageBlock(McpSchema.ImageContent image) {
        String mime = image.mimeType() == null ? "image/png" : image.mimeType();
        Image img = new Image("data:" + mime + ";base64," + image.data(), "tool result image");
        img.getStyle().set("max-width", "100%").set("max-height", "20em").set("display", "block");

        Div container = new Div(img);
        container.getStyle()
                .set("position", "relative")
                .set("padding", "0.6em")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("background-color", "var(--lumo-contrast-5pct)");

        Button copy = iconCopyButton(image.data());
        copy.getStyle()
                .set("position", "absolute")
                .set("top", "0.3em")
                .set("right", "0.3em");
        container.add(copy);
        return container;
    }

    public static Component buildResourceContentBlock(McpSchema.ResourceContents content) {
        if (content instanceof McpSchema.TextResourceContents text) {
            return codeBlock(tryPrettyJson(text.text()), false);
        }
        if (content instanceof McpSchema.BlobResourceContents blob) {
            String mime = blob.mimeType() == null ? "application/octet-stream" : blob.mimeType();
            String label = "Binary resource (" + mime + ", " + blob.blob().length() + " base64 chars)";
            Span msg = new Span(label);
            msg.getStyle()
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-family", "var(--lumo-font-family-monospace)")
                    .set("font-size", "0.85em")
                    .set("display", "block")
                    .set("padding", "0.6em 0.8em")
                    .set("background-color", "var(--lumo-contrast-5pct)")
                    .set("border-radius", "var(--lumo-border-radius-s)");
            return msg;
        }
        return codeBlock(String.valueOf(content), false);
    }

    public static Component buildPromptMessageBlock(McpSchema.PromptMessage msg) {
        Span roleBadge = new Span(msg.role() == null ? "?" : msg.role().name().toLowerCase());
        roleBadge.getStyle()
                .set("font-size", "0.7em")
                .set("font-weight", "600")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("padding", "0.15em 0.6em")
                .set("border-radius", "999px")
                .set("background-color", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("margin-right", "0.6em");

        Component contentBlock = buildContentBlock(msg.content());
        Div container = new Div();
        container.add(roleBadge, contentBlock);
        container.getStyle().set("margin-bottom", "0.4em");
        return container;
    }

    public static Component emptyState(String message) {
        Span msg = new Span(message);
        msg.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9em")
                .set("padding", "1em 0.4em")
                .set("display", "block");
        return msg;
    }

    public static Component sectionHeader(String text, int count) {
        Span title = new Span(text);
        title.getStyle()
                .set("font-size", "0.8em")
                .set("font-weight", "700")
                .set("letter-spacing", "0.06em")
                .set("text-transform", "uppercase")
                .set("color", "var(--lumo-secondary-text-color)");
        Span counter = new Span(String.valueOf(count));
        counter.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace)")
                .set("font-size", "0.8em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-left", "0.6em");
        HorizontalLayout row = new HorizontalLayout(title, counter);
        row.setPadding(false);
        row.setSpacing(false);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("margin", "0.3em 0 0.6em");
        return row;
    }
}
