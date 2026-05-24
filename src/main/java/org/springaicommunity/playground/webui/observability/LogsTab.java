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
package org.springaicommunity.playground.webui.observability;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.virtuallist.VirtualList;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import org.springaicommunity.playground.observability.Window;
import org.springaicommunity.playground.webui.VaadinUtils;
import org.springaicommunity.playground.webui.observability.components.BaseDashboardTab;
import org.springaicommunity.playground.webui.observability.components.ObservabilityGlobalSettings;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LogsTab extends BaseDashboardTab {

    private static final int TAIL_BYTES = 4 * 1024 * 1024;
    private static final DateTimeFormatter LOG_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^(?<time>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+"
                    + "\\[(?<thread>[^\\]]+)\\]\\s+"
                    + "(?<level>[A-Z]+)\\s+"
                    + "(?<logger>\\S+)\\s+"
                    + "\\[conv=(?<conv>[^ ]*) msg=(?<msg>[^ ]*) traceId=(?<traceId>[^ ]*) spanId=(?<spanId>[^\\]]*)\\]\\s+-\\s+"
                    + "(?<rest>.*)$");

    private final Path logFile;
    private final ObservabilityGlobalSettings globalSettings;
    private final Span matchCountChip = new Span();
    private final ComboBox<String> levelFilter = new ComboBox<>();
    private final TextField textFilter = new TextField();
    private final Checkbox caseInsensitive = new Checkbox("Aa-insensitive");
    private final Checkbox regexMode = new Checkbox("Regex");
    private final Checkbox followTail = new Checkbox("Follow tail");
    private final Button exportBtn = new Button("Export visible",
            VaadinIcon.DOWNLOAD.create());
    private final VirtualList<LogLine> list = new VirtualList<>();
    private List<LogLine> currentFiltered = List.of();

    public LogsTab(Path springAiPlaygroundHomeDir, ObservabilityGlobalSettings globalSettings) {
        super();
        this.logFile = springAiPlaygroundHomeDir.resolve("logs").resolve("spring-ai-playground.log");
        this.globalSettings = globalSettings;

        matchCountChip.getStyle().set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("padding", "4px 10px")
                .set("border-radius", "999px")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("color", "var(--lumo-primary-text-color)")
                .set("white-space", "nowrap");

        levelFilter.setItems("ALL", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");
        levelFilter.setValue("ALL");
        levelFilter.setPlaceholder("Level");
        levelFilter.setWidth("120px");
        levelFilter.addValueChangeListener(e -> refresh(null));

        textFilter.setPlaceholder("Contains / Regex — filter visible lines");
        textFilter.setClearButtonVisible(true);
        textFilter.setValueChangeMode(ValueChangeMode.LAZY);
        textFilter.setPrefixComponent(VaadinIcon.SEARCH.create());
        textFilter.addValueChangeListener(e -> refresh(null));

        caseInsensitive.setValue(true);
        caseInsensitive.addValueChangeListener(e -> refresh(null));

        regexMode.getElement().setAttribute("title",
                "Treat the search text as a Java regular expression — Pattern.matcher().find()");
        regexMode.addValueChangeListener(e -> refresh(null));

        followTail.setValue(true);
        followTail.getElement().setAttribute("title",
                "Auto-scroll to the newest line on every refresh (tail -f style). "
                        + "Automatically turned off when a custom time range is set.");
        followTail.addValueChangeListener(e -> refresh(null));

        exportBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        exportBtn.setTooltipText(
                "Download the currently visible (filtered) lines as a .log file");
        exportBtn.addClickListener(e -> downloadVisibleLines());

        HorizontalLayout filterRow = new HorizontalLayout(
                matchCountChip, levelFilter, textFilter,
                caseInsensitive, regexMode, followTail, exportBtn);
        filterRow.setWidthFull();
        filterRow.setSpacing(true);
        filterRow.setPadding(false);
        filterRow.setAlignItems(FlexComponent.Alignment.CENTER);
        filterRow.getStyle().set("gap", "var(--lumo-space-m)")
                .set("flex-wrap", "nowrap");
        filterRow.expand(textFilter);

        content.add(filterRow);

        list.setSizeFull();
        list.getStyle().set("background", "#1e1f24")
                .set("color", "#e3e6eb")
                .set("font-family", "var(--lumo-font-family-monospace, ui-monospace, SFMono-Regular, Menlo, monospace)")
                .set("font-size", "12px")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("padding", "var(--lumo-space-xs) 0")
                .set("overflow-x", "hidden")
                .set("min-height", "300px");
        list.setRenderer(new ComponentRenderer<>(this::renderRow));

        content.add(list);

        globalSettings.onRangeChanged(() -> {
            if (globalSettings.hasCustomRange() && Boolean.TRUE.equals(followTail.getValue())) {
                followTail.setValue(false);
            }
        });
    }

    private Div renderRow(LogLine line) {
        Div row = new Div();
        row.getStyle().set("display", "flex")
                .set("align-items", "baseline")
                .set("gap", "8px")
                .set("padding", "2px 12px")
                .set("white-space", "pre-wrap")
                .set("word-break", "break-word")
                .set("overflow-wrap", "anywhere")
                .set("min-width", "0")
                .set("cursor", "pointer");
        String lv = line.level() == null ? "" : line.level();
        if ("ERROR".equals(lv)) row.getStyle().set("background", "rgba(239,83,80,0.10)");
        else if ("WARN".equals(lv)) row.getStyle().set("background", "rgba(255,167,38,0.07)");

        Span time = new Span(line.time() == null ? "" : line.time());
        time.getStyle().set("color", "#8a8f99").set("flex", "0 0 auto");

        Span levelChip = new Span(lv);
        levelChip.getStyle().set("flex", "0 0 50px")
                .set("text-align", "center")
                .set("font-weight", "700")
                .set("color", levelColor(lv));

        Span logger = new Span(shortenLogger(line.logger()));
        logger.getStyle().set("color", "#9aa5ad").set("flex", "0 0 auto");

        Span trace = new Span(line.traceId() == null || line.traceId().isEmpty() ? ""
                : ("trace=" + abbrev(line.traceId(), 8)));
        trace.getStyle().set("color", "#7e57c2").set("flex", "0 0 auto");

        Span msg = new Span(line.message() == null ? line.raw() : line.message());
        msg.getStyle().set("color", "#e3e6eb").set("flex", "1 1 auto");

        row.add(time, levelChip, logger, trace, msg);
        row.addClickListener(e -> openDetail(line));
        row.getElement().getStyle().set("transition", "background 80ms");
        row.getElement().addEventListener("mouseenter",
                e -> row.getElement().executeJs("this.style.background='rgba(255,255,255,0.05)'"));
        row.getElement().addEventListener("mouseleave",
                e -> row.getElement().executeJs(
                        "this.style.background=$0",
                        "ERROR".equals(lv) ? "rgba(239,83,80,0.10)"
                                : "WARN".equals(lv) ? "rgba(255,167,38,0.07)" : "transparent"));
        return row;
    }

    private static String levelColor(String lv) {
        return switch (lv) {
            case "ERROR" -> "#ff6b6b";
            case "WARN" -> "#ffb74d";
            case "INFO" -> "#64b5f6";
            case "DEBUG" -> "#90a4ae";
            case "TRACE" -> "#78909c";
            default -> "#cfd8dc";
        };
    }

    private static String shortenLogger(String logger) {
        if (logger == null) return "";
        int lastDot = logger.lastIndexOf('.');
        if (lastDot < 0) return logger;
        String pkg = logger.substring(0, lastDot);
        String simple = logger.substring(lastDot + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : pkg.split("\\.")) {
            if (part.isEmpty()) continue;
            sb.append(part.charAt(0)).append('.');
        }
        sb.append(simple);
        return sb.toString();
    }

    private static String abbrev(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n);
    }

    private void openDetail(LogLine line) {
        Dialog d = new Dialog();
        d.setWidth("900px");
        d.setMaxWidth("90vw");
        d.setHeight("700px");
        d.setMaxHeight("85vh");
        d.setHeaderTitle("Log line — " + line.level() + " · " + line.time());
        d.setDraggable(true);
        d.setResizable(true);

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(false);
        body.add(detailLine("Logger", line.logger()));
        body.add(detailLine("Conv", line.conv()));
        body.add(detailLine("UserMessageId", line.userMessageId()));
        body.add(detailLine("TraceId", line.traceId()));
        body.add(detailLine("SpanId", line.spanId()));

        Pre pre = new Pre(line.raw());
        pre.getStyle().set("white-space", "pre-wrap").set("word-break", "break-word")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("padding", "var(--lumo-space-m)")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "var(--lumo-border-radius-s)")
                .set("max-height", "55vh")
                .set("overflow", "auto");
        body.add(pre);

        Button copy = new Button("Copy raw line", VaadinIcon.COPY.create(), e -> {
            UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0);", line.raw());
            VaadinUtils.showInfoNotification("Copied");
        });
        Button traceLink = new Button("Open trace", VaadinIcon.SITEMAP.create(), e -> {
            if (line.traceId() != null && !line.traceId().isEmpty()) {
                UI.getCurrent().navigate("observability?tab=traces&trace=" + line.traceId());
                d.close();
            } else {
                VaadinUtils.showInfoNotification("No traceId on this line");
            }
        });
        d.getFooter().add(copy, traceLink, new Button("Close", e -> d.close()));
        d.add(body);
        d.open();
    }

    private HorizontalLayout detailLine(String label, String value) {
        Span k = new Span(label);
        k.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("min-width", "8rem").set("font-size", "var(--lumo-font-size-xs)");
        Span v = new Span(value == null || value.isEmpty() ? "—" : value);
        v.getStyle().set("font-family", "var(--lumo-font-family-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout row = new HorizontalLayout(k, v);
        row.setSpacing(true);
        row.setPadding(false);
        return row;
    }

    @Override
    public void refresh(Window window) {
        if (!Files.isRegularFile(logFile)) {
            setStatus("Log file not found: " + logFile);
            list.setItems(List.of());
            return;
        }
        String tail = readTail(logFile);
        if (tail == null) {
            setStatus("Failed to read log file");
            return;
        }
        String[] rawLines = tail.split("\\R");
        List<LogLine> all = new ArrayList<>(rawLines.length);
        for (String r : rawLines) {
            LogLine ln = parse(r);
            if (ln != null) all.add(ln);
        }
        String levelVal = levelFilter.getValue();
        String contains = textFilter.getValue() == null ? "" : textFilter.getValue().trim();
        boolean ci = Boolean.TRUE.equals(caseInsensitive.getValue());
        boolean useRegex = Boolean.TRUE.equals(regexMode.getValue()) && !contains.isEmpty();

        Pattern pattern = null;
        String regexError = null;
        if (useRegex) {
            try {
                pattern = ci
                        ? Pattern.compile(contains, Pattern.CASE_INSENSITIVE)
                        : Pattern.compile(contains);
            } catch (PatternSyntaxException ex) {
                regexError = ex.getDescription();
                useRegex = false;
            }
        }
        String containsLc = contains.toLowerCase();

        LocalDateTime fromInstant;
        LocalDateTime toInstant;
        boolean customRange = globalSettings.hasCustomRange();
        if (customRange) {
            fromInstant = globalSettings.customFrom();
            toInstant = globalSettings.customTo();
        } else {
            Window effective = window == null ? globalSettings.window() : window;
            fromInstant = LocalDateTime.now().minusMinutes(effective.minutes);
            toInstant = null;
        }

        List<LogLine> filtered = new ArrayList<>(all.size());
        for (LogLine ln : all) {
            if (levelVal != null && !"ALL".equals(levelVal) && !levelVal.equals(ln.level())) continue;

            LocalDateTime lt = parseLogTime(ln.time());
            if (lt == null) continue;
            if (fromInstant != null && lt.isBefore(fromInstant)) continue;
            if (toInstant != null && lt.isAfter(toInstant)) continue;

            if (!contains.isEmpty()) {
                if (useRegex) {
                    if (!pattern.matcher(ln.raw()).find()) continue;
                } else {
                    String hay = ci ? ln.raw().toLowerCase() : ln.raw();
                    String needle = ci ? containsLc : contains;
                    if (!hay.contains(needle)) continue;
                }
            }
            filtered.add(ln);
        }

        filtered.sort(Comparator.comparing(LogLine::time, Comparator.nullsFirst(Comparator.naturalOrder())));

        list.setItems(filtered);
        currentFiltered = filtered;

        boolean hasFilter = !contains.isEmpty() || customRange
                || (levelVal != null && !"ALL".equals(levelVal));
        matchCountChip.setText(hasFilter
                ? ("matched " + filtered.size() + " / " + all.size())
                : ("showing " + filtered.size() + " lines"));

        StringBuilder status = new StringBuilder();
        status.append(logFile).append(" · ");
        status.append(filtered.size()).append(" of ").append(all.size()).append(" lines · ")
                .append(tail.length() / 1024).append(" KB tail");
        status.append(" · mode: ").append(useRegex ? "regex" : (regexError != null ? "literal (regex error)" : "literal"));
        status.append(" · follow tail ")
                .append(Boolean.TRUE.equals(followTail.getValue()) ? "ON" : "OFF");
        if (customRange) {
            status.append(" · custom range: ");
            status.append(fromInstant == null ? "—" : fromInstant.toString());
            status.append(" → ");
            status.append(toInstant == null ? "—" : toInstant.toString());
        } else if (window != null) {
            status.append(" · window: ").append(window.minutes).append("m");
        } else {
            status.append(" · window: ").append(globalSettings.window().minutes).append("m");
        }
        if (regexError != null) {
            status.append(" · regex error: ").append(regexError);
        }
        setStatus(status.toString());

        if (Boolean.TRUE.equals(followTail.getValue()) && !customRange && !filtered.isEmpty()) {
            list.scrollToEnd();
        }
    }

    private void downloadVisibleLines() {
        if (currentFiltered.isEmpty()) {
            VaadinUtils.showInfoNotification("Nothing to export — no visible lines.");
            return;
        }
        StringBuilder buf = new StringBuilder(currentFiltered.size() * 200);
        for (LogLine ln : currentFiltered) {
            buf.append(ln.raw()).append('\n');
        }
        String filename = "spring-ai-playground-logs-"
                + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".log";
        UI.getCurrent().getPage().executeJs(
                "const blob = new Blob([$0], {type: 'text/plain;charset=utf-8'});"
                        + "const url = URL.createObjectURL(blob);"
                        + "const a = document.createElement('a'); a.href = url; a.download = $1;"
                        + "document.body.appendChild(a); a.click(); a.remove();"
                        + "setTimeout(function(){ URL.revokeObjectURL(url); }, 1000);",
                buf.toString(), filename);
        VaadinUtils.showInfoNotification(
                "Exported " + currentFiltered.size() + " lines → " + filename);
    }

    private LocalDateTime parseLogTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, LOG_TIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String readTail(Path path) {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long len = raf.length();
            long start = Math.max(0, len - TAIL_BYTES);
            raf.seek(start);
            byte[] buf = new byte[(int) Math.min(TAIL_BYTES, len - start)];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private LogLine parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = LINE_PATTERN.matcher(raw);
        if (!m.matches()) {
            return new LogLine("", "", "", "", "", "", "", raw, raw);
        }
        return new LogLine(
                m.group("time"),
                m.group("level"),
                m.group("logger"),
                shorten(m.group("conv")),
                m.group("msg"),
                m.group("traceId"),
                m.group("spanId"),
                m.group("rest"),
                raw);
    }

    private String shorten(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.length() <= 8 ? s : s.substring(0, 8);
    }

    public record LogLine(
            String time, String level, String logger,
            String conv, String userMessageId,
            String traceId, String spanId,
            String message, String raw
    ) {}
}
