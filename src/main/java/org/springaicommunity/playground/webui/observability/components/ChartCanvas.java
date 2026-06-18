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
package org.springaicommunity.playground.webui.observability.components;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag("playground-observability-chart")
@NpmPackage(value = "echarts", version = "5.6.0")
@JsModule("./playground/observability-chart.js")
public class ChartCanvas extends Component implements HasSize, HasStyle {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private boolean magicTypeSupported;

    public ChartCanvas() {
        setWidth("100%");
        setHeight("220px");
    }

    public void clear() {
        getElement().setProperty("chartOption", "");
    }

    public boolean isMagicTypeSupported() {
        return magicTypeSupported;
    }

    public void lineChart(List<String> labels, String datasetLabel, double[] values, String color, String yUnit) {
        ObjectNode root = baseOption(yUnit, false);
        addCategoryAxis(root, labels);
        ArrayNode series = root.putArray("series");
        ObjectNode line = series.addObject();
        line.put("name", datasetLabel == null ? "" : datasetLabel);
        line.put("type", "line");
        line.put("smooth", true);
        line.put("showSymbol", false);
        line.putObject("itemStyle").put("color", color);
        line.putObject("areaStyle").put("opacity", 0.18);
        ArrayNode data = line.putArray("data");
        boolean hasData = false;
        for (double v : values) {
            data.add(v);
            if (v != 0) hasData = true;
        }
        addDataZoom(root);
        addToolbox(root, true);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void multiLineChart(List<String> labels, List<LineSeries> seriesList, String yUnit) {
        ObjectNode root = baseOption(yUnit, true);
        addCategoryAxis(root, labels);
        ArrayNode series = root.putArray("series");
        ArrayNode legendData = ((ObjectNode) root.get("legend")).putArray("data");
        boolean hasData = false;
        for (LineSeries s : seriesList) {
            ObjectNode line = series.addObject();
            line.put("name", s.label);
            line.put("type", "line");
            line.put("smooth", true);
            line.put("showSymbol", false);
            line.putObject("itemStyle").put("color", s.color);
            ArrayNode data = line.putArray("data");
            for (double v : s.values) {
                data.add(v);
                if (v != 0) hasData = true;
            }
            legendData.add(s.label);
        }
        addDataZoom(root);
        addToolbox(root, true);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void stackedBarChart(List<String> labels, String aLabel, double[] aValues, String aColor,
            String bLabel, double[] bValues, String bColor) {
        ObjectNode root = baseOption("", true);
        addCategoryAxis(root, labels);
        ArrayNode legendData = ((ObjectNode) root.get("legend")).putArray("data");
        legendData.add(aLabel);
        legendData.add(bLabel);
        ArrayNode series = root.putArray("series");
        boolean hasData = false;
        for (double v : aValues) {
            if (v != 0) {
                hasData = true;
                break;
            }
        }
        if (!hasData) {
            for (double v : bValues) {
                if (v != 0) {
                    hasData = true;
                    break;
                }
            }
        }
        addBarSeries(series, aLabel, aValues, aColor);
        addBarSeries(series, bLabel, bValues, bColor);
        addDataZoom(root);
        addToolbox(root, true);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void multiStackedBar(List<String> labels, List<LineSeries> seriesList) {
        ObjectNode root = baseOption("", true);
        addCategoryAxis(root, labels);
        ArrayNode legendData = ((ObjectNode) root.get("legend")).putArray("data");
        ArrayNode series = root.putArray("series");
        boolean hasData = false;
        for (LineSeries s : seriesList) {
            legendData.add(s.label);
            addBarSeries(series, s.label, s.values, s.color);
            for (double v : s.values) {
                if (v != 0) {
                    hasData = true;
                    break;
                }
            }
        }
        addDataZoom(root);
        addToolbox(root, true);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void horizontalBarChart(Map<String, Long> data, String color, int topN) {
        horizontalBarChart(data, color, topN, "");
    }

    public void horizontalBarChart(Map<String, Long> data, String color, int topN, String unitSuffix) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode tooltip = root.putObject("tooltip");
        tooltip.put("trigger", "axis");
        tooltip.putObject("axisPointer").put("type", "shadow");
        root.putObject("grid").put("left", 110).put("right", 60).put("top", 36).put("bottom", 24).put("containLabel", true);

        Map<String, Long> sorted = new LinkedHashMap<>();
        data.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        ObjectNode xAxis = root.putObject("xAxis");
        xAxis.put("type", "value");
        ObjectNode xAxisLabel = xAxis.putObject("axisLabel");
        xAxisLabel.put("fontSize", 12).put("color", "#64748b");
        if (unitSuffix != null && !unitSuffix.isEmpty()) {
            xAxisLabel.put("formatter", "{value}" + unitSuffix);
        }
        ObjectNode yAxis = root.putObject("yAxis");
        yAxis.put("type", "category");
        yAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#475569");
        ArrayNode yData = yAxis.putArray("data");
        // ECharts puts last label at top of axis; reverse for "biggest at top"
        List<String> keys = new ArrayList<>(sorted.keySet());
        Collections.reverse(keys);
        for (String k : keys) yData.add(k);

        ArrayNode series = root.putArray("series");
        ObjectNode bar = series.addObject();
        bar.put("type", "bar");
        bar.put("barMaxWidth", 18);
        bar.putObject("itemStyle").put("color", color).put("borderRadius", 3);
        ObjectNode label = bar.putObject("label");
        label.put("show", true).put("position", "right")
                .put("fontSize", 12).put("color", "#475569");
        if (unitSuffix != null && !unitSuffix.isEmpty()) {
            label.put("formatter", "{c}" + unitSuffix);
        }
        ArrayNode dataArr = bar.putArray("data");
        boolean hasData = false;
        for (String k : keys) {
            long v = sorted.get(k);
            dataArr.add(v);
            if (v != 0) hasData = true;
        }
        addToolbox(root, false);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void horizontalBarChartDecimal(Map<String, Double> data, String color, int topN,
            String unitPrefix) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode tooltip = root.putObject("tooltip");
        tooltip.put("trigger", "axis");
        tooltip.putObject("axisPointer").put("type", "shadow");
        root.putObject("grid").put("left", 110).put("right", 60).put("top", 36).put("bottom", 24).put("containLabel", true);

        Map<String, Double> sorted = new LinkedHashMap<>();
        data.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        String prefix = unitPrefix == null ? "" : unitPrefix;
        ObjectNode xAxis = root.putObject("xAxis");
        xAxis.put("type", "value");
        ObjectNode xAxisLabel = xAxis.putObject("axisLabel");
        xAxisLabel.put("fontSize", 12).put("color", "#64748b");
        if (!prefix.isEmpty()) xAxisLabel.put("formatter", prefix + "{value}");
        ObjectNode yAxis = root.putObject("yAxis");
        yAxis.put("type", "category");
        yAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#475569");
        ArrayNode yData = yAxis.putArray("data");
        List<String> keys = new ArrayList<>(sorted.keySet());
        Collections.reverse(keys);
        for (String k : keys) yData.add(k);

        ArrayNode series = root.putArray("series");
        ObjectNode bar = series.addObject();
        bar.put("type", "bar");
        bar.put("barMaxWidth", 18);
        bar.putObject("itemStyle").put("color", color).put("borderRadius", 3);
        ObjectNode label = bar.putObject("label");
        label.put("show", true).put("position", "right")
                .put("fontSize", 12).put("color", "#475569");
        if (!prefix.isEmpty()) label.put("formatter", prefix + "{c}");
        ArrayNode dataArr = bar.putArray("data");
        boolean hasData = false;
        for (String k : keys) {
            double v = sorted.get(k);
            dataArr.add(v);
            if (v != 0.0) hasData = true;
        }
        addToolbox(root, false);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void donutChartDecimal(Map<String, Double> data, String[] palette, String unitPrefix) {
        ObjectNode root = MAPPER.createObjectNode();
        String prefix = unitPrefix == null ? "" : unitPrefix;
        root.putObject("tooltip").put("trigger", "item")
                .put("formatter", "{b}: " + prefix + "{c} ({d}%)")
                .putObject("textStyle").put("fontSize", 13);
        ObjectNode donutLegend = root.putObject("legend");
        donutLegend.put("orient", "vertical").put("right", 8).put("top", 36);
        donutLegend.putObject("textStyle").put("fontSize", 13).put("color", "#475569");
        ArrayNode color = root.putArray("color");
        if (palette != null) for (String c : palette) color.add(c);
        ArrayNode series = root.putArray("series");
        ObjectNode pie = series.addObject();
        pie.put("type", "pie");
        ArrayNode radius = pie.putArray("radius");
        radius.add("45%");
        radius.add("70%");
        ArrayNode center = pie.putArray("center");
        center.add("38%");
        center.add("50%");
        pie.put("avoidLabelOverlap", true);
        pie.putObject("label").put("show", false);
        pie.putObject("emphasis").putObject("label").put("show", true).put("fontSize", 14).put("fontWeight", "bold");
        ArrayNode dataArr = pie.putArray("data");
        boolean hasData = false;
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            ObjectNode item = dataArr.addObject();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            if (entry.getValue() != null && entry.getValue() != 0.0) hasData = true;
        }
        addToolbox(root, false);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void horizontalStackedBar(Map<String, long[]> data,
            List<String> seriesLabels, List<String> seriesColors) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode tooltip = root.putObject("tooltip");
        tooltip.put("trigger", "axis");
        tooltip.putObject("axisPointer").put("type", "shadow");

        ObjectNode legend = root.putObject("legend");
        legend.put("top", 4);
        legend.putObject("textStyle").put("fontSize", 13).put("color", "#475569");
        ArrayNode legendData = legend.putArray("data");
        for (String l : seriesLabels) legendData.add(l);

        root.putObject("grid").put("left", 110).put("right", 60)
                .put("top", 36).put("bottom", 24).put("containLabel", true);

        ObjectNode xAxisStacked = root.putObject("xAxis");
        xAxisStacked.put("type", "value");
        xAxisStacked.putObject("axisLabel").put("fontSize", 12).put("color", "#64748b");
        ObjectNode yAxis = root.putObject("yAxis");
        yAxis.put("type", "category");
        yAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#475569");
        ArrayNode yData = yAxis.putArray("data");
        // ECharts puts last label at top of axis; reverse for "biggest at top"
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.reverse(keys);
        for (String k : keys) yData.add(k);

        ArrayNode series = root.putArray("series");
        boolean hasData = false;
        for (int s = 0; s < seriesLabels.size(); s++) {
            ObjectNode bar = series.addObject();
            bar.put("name", seriesLabels.get(s));
            bar.put("type", "bar");
            bar.put("stack", "total");
            bar.put("barMaxWidth", 18);
            bar.putObject("itemStyle").put("color", seriesColors.get(s)).put("borderRadius", 0);
            ArrayNode dataArr = bar.putArray("data");
            for (String k : keys) {
                long v = data.get(k)[s];
                dataArr.add(v);
                if (v != 0) hasData = true;
            }
        }
        addToolbox(root, false);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void donutChart(Map<String, Long> data, String[] palette) {
        ObjectNode root = MAPPER.createObjectNode();
        root.putObject("tooltip").put("trigger", "item").put("formatter", "{b}: {c} ({d}%)")
                .putObject("textStyle").put("fontSize", 13);
        ObjectNode donutLegend = root.putObject("legend");
        donutLegend.put("orient", "vertical").put("right", 8).put("top", 36);
        donutLegend.putObject("textStyle").put("fontSize", 13).put("color", "#475569");
        ArrayNode color = root.putArray("color");
        if (palette != null) for (String c : palette) color.add(c);
        ArrayNode series = root.putArray("series");
        ObjectNode pie = series.addObject();
        pie.put("type", "pie");
        ArrayNode radius = pie.putArray("radius");
        radius.add("45%");
        radius.add("70%");
        ArrayNode center = pie.putArray("center");
        center.add("38%");
        center.add("50%");
        pie.put("avoidLabelOverlap", true);
        pie.putObject("label").put("show", false);
        pie.putObject("emphasis").putObject("label").put("show", true).put("fontSize", 14).put("fontWeight", "bold");
        ArrayNode dataArr = pie.putArray("data");
        boolean hasData = false;
        for (Map.Entry<String, Long> entry : data.entrySet()) {
            ObjectNode item = dataArr.addObject();
            item.put("name", entry.getKey());
            item.put("value", entry.getValue());
            if (entry.getValue() != null && entry.getValue() != 0) hasData = true;
        }
        addToolbox(root, false);
        addEmptyState(root, hasData);
        applyOption(root);
    }

    public void heatmap(List<String> xLabels, List<String> yLabels, List<int[]> data, long maxValue) {
        ObjectNode root = MAPPER.createObjectNode();
        root.putObject("tooltip").put("position", "top");
        root.putObject("grid").put("left", 60).put("right", 24).put("top", 36).put("bottom", 50).put("containLabel", true);
        ObjectNode xAxis = root.putObject("xAxis");
        xAxis.put("type", "category");
        ArrayNode xData = xAxis.putArray("data");
        for (String l : xLabels) xData.add(l);
        xAxis.putObject("splitArea").put("show", true);
        xAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#64748b");
        ObjectNode yAxis = root.putObject("yAxis");
        yAxis.put("type", "category");
        ArrayNode yData = yAxis.putArray("data");
        for (String l : yLabels) yData.add(l);
        yAxis.putObject("splitArea").put("show", true);
        yAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#475569");
        ObjectNode visualMap = root.putObject("visualMap");
        visualMap.put("min", 0);
        visualMap.put("max", Math.max(1, maxValue));
        visualMap.put("calculable", true);
        visualMap.put("orient", "horizontal");
        visualMap.put("left", "center");
        visualMap.put("bottom", 8);
        ArrayNode colorRange = visualMap.putObject("inRange").putArray("color");
        colorRange.add("#ede7f6");
        colorRange.add("#7e57c2");
        colorRange.add("#3f1d8c");
        ArrayNode series = root.putArray("series");
        ObjectNode heatmap = series.addObject();
        heatmap.put("type", "heatmap");
        ArrayNode dataArr = heatmap.putArray("data");
        for (int[] cell : data) {
            ArrayNode row = dataArr.addArray();
            row.add(cell[0]);
            row.add(cell[1]);
            row.add(cell[2]);
        }
        ObjectNode itemStyle = heatmap.putObject("emphasis").putObject("itemStyle");
        itemStyle.put("shadowBlur", 8);
        itemStyle.put("shadowColor", "rgba(0,0,0,0.4)");
        addToolbox(root, false);
        addEmptyState(root, maxValue > 0);
        applyOption(root);
    }

    public Registration addClickListener(ComponentEventListener<ChartClickEvent> listener) {
        return addListener(ChartClickEvent.class, listener);
    }

    public void savePng() {
        getElement().executeJs("if (this._saveAsPng) { this._saveAsPng('spring-ai-playground-chart.png'); }");
    }

    public void restoreZoom() {
        getElement().executeJs("if (this._restoreZoom) { this._restoreZoom(); }");
    }

    public void toggleMagicType() {
        getElement().executeJs("if (this._toggleMagicType) { this._toggleMagicType(); }");
    }

    @DomEvent("chart-click")
    public static class ChartClickEvent extends ComponentEvent<ChartCanvas> {
        private final String name;
        private final String seriesName;
        private final int dataIndex;
        public ChartClickEvent(ChartCanvas source, boolean fromClient,
                @EventData("event.detail.name") String name,
                @EventData("event.detail.seriesName") String seriesName,
                @EventData("event.detail.dataIndex") int dataIndex) {
            super(source, fromClient);
            this.name = name;
            this.seriesName = seriesName;
            this.dataIndex = dataIndex;
        }
        public String getName() { return name; }
        public String getSeriesName() { return seriesName; }
        public int getDataIndex() { return dataIndex; }
    }

    private ObjectNode baseOption(String yUnit, boolean showLegend) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode tooltip = root.putObject("tooltip");
        tooltip.put("trigger", "axis");
        tooltip.putObject("axisPointer").put("type", "cross");
        tooltip.putObject("textStyle").put("fontSize", 13);
        ObjectNode legend = root.putObject("legend");
        legend.put("show", showLegend);
        legend.put("bottom", 0);
        // Match common dashboard conventions (Grafana / Datadog default
        // legend ~13px in secondary text colour, not the 11px tertiary
        // tone ECharts ships by default).
        legend.putObject("textStyle").put("fontSize", 13)
                .put("color", "#475569");
        ObjectNode grid = root.putObject("grid");
        grid.put("left", 12);
        grid.put("right", 12);
        // leave 36px on top so the toolbox icons don't overlap the plot
        grid.put("top", 36);
        grid.put("bottom", showLegend ? 60 : 36);
        grid.put("containLabel", true);
        ObjectNode yAxis = root.putObject("yAxis");
        yAxis.put("type", "value");
        yAxis.putObject("axisLabel")
                .put("formatter", yUnitFormatter(yUnit))
                .put("fontSize", 12)
                .put("color", "#64748b");
        yAxis.putObject("splitLine").put("show", true).putObject("lineStyle").put("opacity", 0.3);
        return root;
    }

    private void addToolbox(ObjectNode root, boolean magicLineBar) {
        this.magicTypeSupported = magicLineBar;
        ObjectNode tb = root.putObject("toolbox");
        tb.put("show", false);
        tb.put("right", 12);
        tb.put("top", 4);
        tb.put("itemSize", 14);
        tb.putObject("iconStyle").put("borderColor", "#90a4ae");
        ObjectNode feature = tb.putObject("feature");
        ObjectNode save = feature.putObject("saveAsImage");
        save.put("title", "Save as PNG");
        save.put("name", "spring-ai-playground-chart");
        save.put("pixelRatio", 2);
        ObjectNode dv = feature.putObject("dataView");
        dv.put("title", "Data view (CSV)");
        dv.put("readOnly", false);
        ArrayNode lang = dv.putArray("lang");
        lang.add("Data view");
        lang.add("Close");
        lang.add("Refresh");
        feature.putObject("restore").put("title", "Reset");
        if (magicLineBar) {
            ArrayNode types = feature.putObject("magicType").putArray("type");
            types.add("line");
            types.add("bar");
            ((ObjectNode) feature.get("magicType")).putObject("title")
                    .put("line", "Line").put("bar", "Bar");
        }
    }

    private void addEmptyState(ObjectNode root, boolean hasData) {
        if (hasData) return;
        ArrayNode graphic = root.putArray("graphic");
        ObjectNode g = graphic.addObject();
        g.put("type", "text");
        g.put("left", "center");
        g.put("top", "middle");
        g.put("z", 100);
        g.put("silent", true);
        ObjectNode style = g.putObject("style");
        style.put("text", "No data in this window");
        style.put("fill", "#9aa5ad");
        style.put("font", "12px sans-serif");
    }

    private void addCategoryAxis(ObjectNode root, List<String> labels) {
        ObjectNode xAxis = root.putObject("xAxis");
        xAxis.put("type", "category");
        xAxis.put("boundaryGap", false);
        ArrayNode xData = xAxis.putArray("data");
        if (labels != null) for (String l : labels) xData.add(l);
        xAxis.putObject("axisLabel").put("fontSize", 12).put("color", "#64748b");
    }

    private void addBarSeries(ArrayNode series, String name, double[] values, String color) {
        ObjectNode bar = series.addObject();
        bar.put("name", name);
        bar.put("type", "bar");
        bar.put("stack", "total");
        bar.putObject("itemStyle").put("color", color);
        ArrayNode data = bar.putArray("data");
        for (double v : values) data.add(v);
    }

    private void addDataZoom(ObjectNode root) {
        ArrayNode dz = root.putArray("dataZoom");
        // inside zoom (mouse wheel + drag inside)
        ObjectNode inside = dz.addObject();
        inside.put("type", "inside");
        inside.put("start", 0);
        inside.put("end", 100);
        // slider zoom (visible scrollbar/brush at the bottom)
        ObjectNode slider = dz.addObject();
        slider.put("type", "slider");
        slider.put("height", 18);
        slider.put("bottom", 28);
        slider.put("start", 0);
        slider.put("end", 100);
    }

    private String yUnitFormatter(String unit) {
        if (unit == null || unit.isEmpty()) return "{value}";
        if ("%".equals(unit)) return "{value}%";
        return "{value} " + unit;
    }

    private void applyOption(ObjectNode option) {
        try {
            getElement().setProperty("chartOption", MAPPER.writeValueAsString(option));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize chart option", e);
        }
    }

    public record LineSeries(String label, double[] values, String color) {}

    public static final String[] DEFAULT_PALETTE = new String[] {
            "#7e57c2", "#42a5f5", "#26a69a", "#ffa726",
            "#66bb6a", "#ef5350", "#ab47bc", "#78909c"
    };
}
