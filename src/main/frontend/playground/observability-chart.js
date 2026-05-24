/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

import * as echarts from 'echarts';

/**
 * playground-observability-chart
 *
 * Web component wrapping Apache ECharts. Server passes a fully-formed ECharts
 * `option` object via the `chartOption` property; the component instantiates /
 * updates the chart in place. Chart click events (e.g. clicking a bar, line
 * segment, slice) bubble up as a `chart-click` CustomEvent so Vaadin can
 * receive them via `@DomEvent`.
 *
 * The ECharts toolbox stays hidden (toolbox.show = false from the server).
 * User-visible chart actions live in dedicated card-header buttons that call
 * the public _saveAsPng / _restoreZoom / _toggleMagicType methods below.
 */
class ObservabilityChart extends HTMLElement {
    constructor() {
        super();
        this._chart = null;
        this._optionRaw = null;
        this._resizeObserver = null;
    }

    connectedCallback() {
        if (!this._holder) {
            this._holder = document.createElement('div');
            this._holder.style.width = '100%';
            this._holder.style.height = '100%';
            this.style.display = 'block';
            this.style.position = 'relative';
            this.appendChild(this._holder);
        }
        this._init();
    }

    disconnectedCallback() {
        this._destroyChart();
        if (this._resizeObserver) {
            this._resizeObserver.disconnect();
            this._resizeObserver = null;
        }
    }

    set chartOption(value) {
        this._optionRaw = value;
        this._render();
    }

    get chartOption() {
        return this._optionRaw;
    }

    _init() {
        if (this._chart || !this._holder) return;
        this._chart = echarts.init(this._holder, null, { renderer: 'canvas' });

        this._chart.on('click', params => {
            const detail = {
                seriesName: params.seriesName,
                seriesType: params.seriesType,
                name: params.name,
                value: params.value,
                dataIndex: params.dataIndex,
                componentType: params.componentType
            };
            this.dispatchEvent(new CustomEvent('chart-click', { detail, bubbles: true }));
        });

        this._chart.on('dataZoom', () => {
            const opt = this._chart.getOption();
            const dz = opt.dataZoom && opt.dataZoom[0];
            if (!dz) return;
            this.dispatchEvent(new CustomEvent('chart-zoom', {
                detail: { startPercent: dz.start, endPercent: dz.end },
                bubbles: true
            }));
        });

        this._resizeObserver = new ResizeObserver(() => {
            if (this._chart) this._chart.resize();
        });
        this._resizeObserver.observe(this);

        if (this._optionRaw) this._render();
    }

    _destroyChart() {
        if (this._chart) {
            try { this._chart.dispose(); } catch (_) {}
            this._chart = null;
        }
    }

    /** Save the current chart as a PNG via blob download. */
    _saveAsPng(filename) {
        if (!this._chart) return;
        try {
            const url = this._chart.getDataURL({type: 'png', pixelRatio: 2, backgroundColor: '#ffffff'});
            const a = document.createElement('a');
            a.href = url;
            a.download = filename || 'spring-ai-playground-chart.png';
            document.body.appendChild(a);
            a.click();
            a.remove();
        } catch (e) {
            console.error('observability-chart: save PNG failed', e);
        }
    }

    /** Reset any zoom / dataZoom state back to defaults. */
    _restoreZoom() {
        if (!this._chart) return;
        this._chart.dispatchAction({type: 'restore'});
    }

    /**
     * Toggle the first series between line and bar. Pure no-op when the chart
     * has no series, or the first series is not line/bar (donut / heatmap /
     * horizontal-bar charts ignore this — guarded by Java's
     * `setMagicTypeSupported(true)`).
     */
    _toggleMagicType() {
        if (!this._chart) return;
        const opt = this._chart.getOption();
        const series = opt.series && opt.series[0];
        if (!series) return;
        const current = series.type;
        const next = current === 'line' ? 'bar' : (current === 'bar' ? 'line' : null);
        if (!next) return;
        const swapped = opt.series.map(s =>
                (s.type === 'line' || s.type === 'bar') ? Object.assign({}, s, {type: next}) : s);
        this._chart.setOption({series: swapped}, false);
    }

    _render() {
        if (!this._chart || !this._optionRaw) return;
        let option;
        try {
            option = typeof this._optionRaw === 'string' ? JSON.parse(this._optionRaw) : this._optionRaw;
        } catch (e) {
            console.error('observability-chart: bad option', e);
            return;
        }
        this._chart.setOption(option, true);
    }
}

if (!customElements.get('playground-observability-chart')) {
    customElements.define('playground-observability-chart', ObservabilityChart);
}
