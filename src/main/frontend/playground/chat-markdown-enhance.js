import hljs from 'highlight.js/lib/common';
import 'highlight.js/styles/github.css';
import renderMathInElement from 'katex/contrib/auto-render';
import 'katex/dist/katex.min.css';
import 'leaflet/dist/leaflet.css';
import * as leaflet from 'leaflet';
import * as echarts from 'echarts';
import mermaid from 'mermaid';

const L = leaflet.default || leaflet;

mermaid.initialize({ startOnLoad: false, securityLevel: 'strict' });

const SETTLE_MS = 200;
const enhancers = [];
let mermaidQueue = Promise.resolve();

function registerEnhancer(fn) {
    enhancers.push(fn);
}

function runEnhancers(markdownEl) {
    for (const fn of enhancers) {
        try {
            fn(markdownEl);
        } catch (e) {
            console.warn('[saip] markdown enhancer failed', e);
        }
    }
}

function applyNow(markdownEl) {
    const observer = markdownEl.__saipObserver;
    if (observer) observer.disconnect();
    runEnhancers(markdownEl);
    if (observer) observer.observe(markdownEl, { childList: true, subtree: true });
}

function enhanceLinks(root) {
    root.querySelectorAll('a[href]').forEach((anchor) => {
        if (anchor.target === '_blank') return;
        anchor.target = '_blank';
        anchor.rel = 'noopener noreferrer';
    });
}

const POSIX_ABS_PATH = /^\/(?:[^/\0\n]+\/?)+$/;
const WINDOWS_ABS_PATH = /^[A-Za-z]:[\\/](?:[^\\/\0\n]+[\\/]?)*$/;

function isAbsolutePath(value) {
    return value.length > 1 && (POSIX_ABS_PATH.test(value) || WINDOWS_ABS_PATH.test(value));
}

function enhanceFilePaths(root) {
    root.querySelectorAll('code').forEach((code) => {
        if (code.closest('pre')) return;
        const value = code.textContent.trim();
        if (!isAbsolutePath(value)) return;
        code.classList.add('saip-path');
        code.title = 'Open in file browser';
        if (code.dataset.saipPath) return;
        code.dataset.saipPath = 'yes';
        code.addEventListener('click', () => {
            if (window.Saip && typeof window.Saip.invoke === 'function') {
                window.Saip.invoke('openPath', value);
            }
        });
    });
}

function enhanceCodeBlocks(root) {
    root.querySelectorAll('pre').forEach((pre) => {
        if (pre.querySelector(':scope > .saip-code-copy')) return;
        const code = pre.querySelector('code');
        const langClass = code && Array.from(code.classList).find((c) => c.startsWith('language-'));
        if (langClass && langClass !== 'language-mermaid' && langClass !== 'language-undefined') {
            const label = document.createElement('span');
            label.className = 'saip-code-lang';
            label.textContent = langClass.slice('language-'.length);
            pre.appendChild(label);
        }
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'saip-code-copy';
        button.title = 'Copy';
        button.innerHTML = '<vaadin-icon icon="vaadin:copy-o"></vaadin-icon>';
        button.addEventListener('click', () => {
            if (!navigator.clipboard) return;
            const text = (pre.querySelector('code') || pre).innerText;
            navigator.clipboard.writeText(text).then(() => {
                button.innerHTML = '<vaadin-icon icon="vaadin:check"></vaadin-icon>';
                button.title = 'Copied';
                button.classList.add('saip-code-copied');
                setTimeout(() => {
                    button.innerHTML = '<vaadin-icon icon="vaadin:copy-o"></vaadin-icon>';
                    button.title = 'Copy';
                    button.classList.remove('saip-code-copied');
                }, 1500);
            });
        });
        pre.appendChild(button);
    });
}

function highlightCode(root) {
    root.querySelectorAll('pre code').forEach((code) => {
        if (code.classList.contains('language-mermaid')) return;
        if (code.dataset.highlighted === 'yes') return;
        try {
            hljs.highlightElement(code);
        } catch (e) {
            console.warn('[saip] code highlight failed', e);
        }
    });
}

function mountMermaid(container, source) {
    container.classList.add('mermaid', 'saip-mermaid');
    container.textContent = source;
    mermaidQueue = mermaidQueue.then(() => {
        const timeout = new Promise((resolve, reject) => setTimeout(() => reject(new Error('timeout')), 8000));
        return Promise.race([mermaid.run({ nodes: [container], suppressErrors: true }), timeout]).catch((e) => {
            console.warn('[saip] mermaid render failed, falling back to source', e);
            const fallback = document.createElement('pre');
            const fallbackCode = document.createElement('code');
            fallbackCode.textContent = source;
            fallback.appendChild(fallbackCode);
            container.replaceWith(fallback);
        });
    });
}

function renderMermaid(root) {
    root.querySelectorAll('pre > code.language-mermaid').forEach((code) => {
        const pre = code.parentElement;
        if (pre.dataset.saipMermaid) return;
        pre.dataset.saipMermaid = 'done';
        const container = document.createElement('div');
        pre.replaceWith(container);
        mountMermaid(container, code.textContent);
    });
}

function renderMath(root) {
    try {
        renderMathInElement(root, {
            delimiters: [
                { left: '$$', right: '$$', display: true },
                { left: '$', right: '$', display: false },
                { left: '\\(', right: '\\)', display: false },
                { left: '\\[', right: '\\]', display: true },
            ],
            ignoredTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code'],
            throwOnError: false,
        });
    } catch (e) {
        console.warn('[saip] math render failed', e);
    }
}

function pad2(n) {
    return String(n).padStart(2, '0');
}

function buildMailtoHref(data) {
    const recipients = String(data.to || '').split(',').map((x) => x.trim()).filter(Boolean).join(',');
    const params = [];
    if (data.subject) params.push('subject=' + encodeURIComponent(data.subject));
    if (data.cc) {
        const cc = String(data.cc).split(',').map((x) => x.trim()).filter(Boolean).join(',');
        if (cc) params.push('cc=' + encodeURIComponent(cc));
    }
    if (data.body) params.push('body=' + encodeURIComponent(data.body));
    return 'mailto:' + recipients + (params.length ? '?' + params.join('&') : '');
}

function formatIcsUtc(value) {
    const d = new Date(value);
    return d.getUTCFullYear() + pad2(d.getUTCMonth() + 1) + pad2(d.getUTCDate())
        + 'T' + pad2(d.getUTCHours()) + pad2(d.getUTCMinutes()) + pad2(d.getUTCSeconds()) + 'Z';
}

function escapeIcsText(value) {
    return String(value == null ? '' : value)
        .replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
}

function buildIcs(data) {
    const uid = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : Date.now() + '@spring-ai-playground';
    const lines = [
        'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Spring AI Playground//Chat//EN', 'CALSCALE:GREGORIAN',
        'BEGIN:VEVENT', 'UID:' + uid, 'DTSTAMP:' + formatIcsUtc(new Date().toISOString()),
        'DTSTART:' + formatIcsUtc(data.start), 'DTEND:' + formatIcsUtc(data.end),
        'SUMMARY:' + escapeIcsText(data.title),
    ];
    if (data.location) lines.push('LOCATION:' + escapeIcsText(data.location));
    if (data.description) lines.push('DESCRIPTION:' + escapeIcsText(data.description));
    lines.push('END:VEVENT', 'END:VCALENDAR');
    return lines.join('\r\n');
}

function downloadIcs(data) {
    const blob = new Blob([buildIcs(data)], { type: 'text/calendar;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = (String(data.title || 'event').replace(/[\\/:*?"<>|]+/g, '_').slice(0, 60) || 'event') + '.ics';
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 2000);
}

function addIcsToCalendar(data) {
    if (window.electronAPI && typeof window.electronAPI.invoke === 'function') {
        window.electronAPI.invoke('calendar:open-ics', { content: buildIcs(data), filename: data.title || 'event' });
    } else {
        downloadIcs(data);
    }
}

function googleCalendarUrl(data) {
    const params = ['action=TEMPLATE', 'text=' + encodeURIComponent(data.title || ''),
        'dates=' + formatIcsUtc(data.start) + '/' + formatIcsUtc(data.end)];
    if (data.description) params.push('details=' + encodeURIComponent(data.description));
    if (data.location) params.push('location=' + encodeURIComponent(data.location));
    return 'https://calendar.google.com/calendar/render?' + params.join('&');
}

function isoSeconds(value) {
    return new Date(value).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

function outlookCalendarUrl(data) {
    const params = ['path=/calendar/action/compose', 'rru=addevent',
        'subject=' + encodeURIComponent(data.title || ''),
        'startdt=' + encodeURIComponent(isoSeconds(data.start)),
        'enddt=' + encodeURIComponent(isoSeconds(data.end))];
    if (data.description) params.push('body=' + encodeURIComponent(data.description));
    if (data.location) params.push('location=' + encodeURIComponent(data.location));
    return 'https://outlook.live.com/calendar/0/deeplink/compose?' + params.join('&');
}

function yahooCalendarUrl(data) {
    const params = ['v=60', 'title=' + encodeURIComponent(data.title || ''),
        'st=' + formatIcsUtc(data.start), 'et=' + formatIcsUtc(data.end)];
    if (data.description) params.push('desc=' + encodeURIComponent(data.description));
    if (data.location) params.push('in_loc=' + encodeURIComponent(data.location));
    return 'https://calendar.yahoo.com/?' + params.join('&');
}

function formatEventWhen(start, end) {
    try {
        const opts = { dateStyle: 'medium', timeStyle: 'short' };
        return new Date(start).toLocaleString(undefined, opts) + ' – ' + new Date(end).toLocaleString(undefined, opts);
    } catch (e) {
        return String(start) + ' – ' + String(end);
    }
}

function actionField(label, value) {
    const row = document.createElement('div');
    row.className = 'saip-action-field';
    const name = document.createElement('span');
    name.className = 'saip-action-label';
    name.textContent = label;
    const text = document.createElement('span');
    text.className = 'saip-action-value';
    text.textContent = value;
    row.appendChild(name);
    row.appendChild(text);
    return row;
}

function actionCardShell(icon, title) {
    const card = document.createElement('div');
    card.className = 'saip-action-card';
    const head = document.createElement('div');
    head.className = 'saip-action-head';
    const main = document.createElement('span');
    main.className = 'saip-action-head-main';
    const iconEl = document.createElement('span');
    iconEl.className = 'saip-action-icon';
    iconEl.textContent = icon;
    const titleEl = document.createElement('span');
    titleEl.className = 'saip-action-title';
    titleEl.textContent = title;
    main.appendChild(iconEl);
    main.appendChild(titleEl);
    head.appendChild(main);
    const actions = document.createElement('span');
    actions.className = 'saip-action-head-actions';
    head.appendChild(actions);
    card.appendChild(head);
    return card;
}

function fileSlug(value, fallback) {
    const s = String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
    return (s || fallback).slice(0, 60);
}

function downloadDataUrl(dataUrl, filename) {
    const a = document.createElement('a');
    a.href = dataUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
}

async function copyPngToClipboard(dataUrl) {
    if (!navigator.clipboard || !window.ClipboardItem) return false;
    try {
        const blob = await (await fetch(dataUrl)).blob();
        await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
        return true;
    } catch (e) {
        return false;
    }
}

function flashButton(btn, text) {
    const prev = btn.dataset.label;
    btn.textContent = text;
    setTimeout(() => { btn.textContent = prev; }, 1400);
}

function exportButton(label, title, onClick) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'saip-action-export';
    btn.dataset.label = label;
    btn.textContent = label;
    btn.title = title;
    btn.addEventListener('click', onClick);
    return btn;
}

function addImageExport(card, getDataUrl, filename) {
    const slot = card.querySelector('.saip-action-head-actions');
    if (!slot) return;
    const resolve = async () => {
        try { return await getDataUrl(); } catch (e) { return null; }
    };
    const copyBtn = exportButton('Copy', 'Copy image to clipboard', async () => {
        const url = await resolve();
        if (!url) { flashButton(copyBtn, 'N/A'); return; }
        flashButton(copyBtn, (await copyPngToClipboard(url)) ? 'Copied' : 'Use PNG');
    });
    const pngBtn = exportButton('PNG', 'Download as PNG', async () => {
        const url = await resolve();
        if (!url) { flashButton(pngBtn, 'N/A'); return; }
        downloadDataUrl(url, filename);
    });
    slot.appendChild(copyBtn);
    slot.appendChild(pngBtn);
}

const actionCardRenderers = {};

function registerActionCard(type, renderer) {
    actionCardRenderers[type] = renderer;
}

function buildActionCard(data) {
    if (!data || typeof data !== 'object') return null;
    const renderer = actionCardRenderers[data.type];
    return renderer ? renderer(data) : null;
}

registerActionCard('email', (data) => {
    const card = actionCardShell('📧', 'Email draft');
    if (data.to) card.appendChild(actionField('To', data.to));
    if (data.cc) card.appendChild(actionField('Cc', data.cc));
    card.appendChild(actionField('Subject', data.subject || ''));
    if (data.body) card.appendChild(actionField('Body', data.body));
    const button = document.createElement('a');
    button.className = 'saip-action-btn';
    button.href = buildMailtoHref(data);
    button.target = '_blank';
    button.rel = 'noopener noreferrer';
    button.textContent = '📧 Send email';
    card.appendChild(button);
    return card;
});

function calendarMenuItem(label, href, onClick) {
    const el = document.createElement(href ? 'a' : 'button');
    el.className = 'saip-action-menu-item';
    el.textContent = label;
    if (href) {
        el.href = href;
        el.target = '_blank';
        el.rel = 'noopener noreferrer';
    } else {
        el.type = 'button';
    }
    if (onClick) el.addEventListener('click', onClick);
    return el;
}

registerActionCard('calendar', (data) => {
    const card = actionCardShell('📅', 'Calendar event');
    card.appendChild(actionField('Title', data.title || ''));
    card.appendChild(actionField('When', formatEventWhen(data.start, data.end)));
    if (data.location) card.appendChild(actionField('Location', data.location));
    if (data.description) card.appendChild(actionField('Notes', data.description));

    const wrap = document.createElement('div');
    wrap.className = 'saip-action-menu-wrap';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'saip-action-btn';
    button.textContent = '📅 Add to calendar ▾';
    const menu = document.createElement('div');
    menu.className = 'saip-action-menu';
    menu.hidden = true;
    menu.appendChild(calendarMenuItem('Google Calendar', googleCalendarUrl(data)));
    menu.appendChild(calendarMenuItem('Outlook', outlookCalendarUrl(data)));
    menu.appendChild(calendarMenuItem('Yahoo Calendar', yahooCalendarUrl(data)));
    const desktop = window.electronAPI && typeof window.electronAPI.invoke === 'function';
    const icsLabel = desktop ? 'Open in calendar app (.ics)' : 'Download .ics (calendar app, others)';
    menu.appendChild(calendarMenuItem(icsLabel, null, () => addIcsToCalendar(data)));
    const closeMenu = () => {
        menu.hidden = true;
        document.removeEventListener('click', onOutside);
        document.removeEventListener('keydown', onEscape);
    };
    const onOutside = (e) => { if (!wrap.contains(e.target)) closeMenu(); };
    const onEscape = (e) => { if (e.key === 'Escape') closeMenu(); };
    menu.addEventListener('click', closeMenu);
    button.addEventListener('click', () => {
        if (!menu.hidden) { closeMenu(); return; }
        menu.hidden = false;
        setTimeout(() => {
            document.addEventListener('click', onOutside);
            document.addEventListener('keydown', onEscape);
        }, 0);
    });
    wrap.appendChild(button);
    wrap.appendChild(menu);
    card.appendChild(wrap);
    return card;
});

registerActionCard('map', (data) => {
    const query = String(data.query || '').trim();
    if (!query) return null;
    const card = actionCardShell('📍', data.label || 'Location');
    const lang = (navigator.language || 'en').split('-')[0] || 'en';
    const frame = document.createElement('iframe');
    frame.className = 'saip-action-map';
    frame.loading = 'lazy';
    frame.referrerPolicy = 'no-referrer';
    frame.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-popups');
    frame.src = 'https://maps.google.com/maps?q=' + encodeURIComponent(query)
        + '&z=15&hl=' + encodeURIComponent(lang) + '&output=embed';
    card.appendChild(frame);
    const link = document.createElement('a');
    link.className = 'saip-action-btn';
    link.href = 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent(query);
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = '📍 Open in Google Maps';
    card.appendChild(link);
    return card;
});

const ECHARTS_PALETTE = ['#4ab26e', '#3b82f6', '#f4a236', '#d7263d', '#a855f7', '#14b8a6'];

function echartsCard(icon, title, option) {
    const card = actionCardShell(icon, title || 'Chart');
    const box = document.createElement('div');
    box.className = 'saip-action-chart';
    card.appendChild(box);
    try {
        const chart = echarts.init(box, null, { renderer: 'canvas' });
        chart.setOption(option);
        if (window.ResizeObserver) new ResizeObserver(() => chart.resize()).observe(box);
        setTimeout(() => chart.resize(), 80);
        addImageExport(card, () => chart.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#fff' }),
            fileSlug(title, 'chart') + '.png');
    } catch (e) {
        box.classList.add('saip-visualization-failed');
        box.textContent = 'Chart could not be rendered.';
    }
    return card;
}

const CHART_TYPES = ['bar', 'line', 'area', 'pie', 'radar', 'scatter', 'gauge'];

function normalizeChartSeries(rawSeries, seriesName) {
    let series = rawSeries;
    if (Array.isArray(series) && series.length && typeof series[0] === 'number') {
        series = [{ name: seriesName || 'Value', data: series }];
    }
    return Array.isArray(series) ? series : [];
}

function buildChartOption(data) {
    if (data.echartsOption && typeof data.echartsOption === 'object') return data.echartsOption;
    let type = String(data.chartType || 'bar').toLowerCase();
    if (CHART_TYPES.indexOf(type) === -1) type = 'bar';
    const labels = Array.isArray(data.labels) ? data.labels : [];
    const series = normalizeChartSeries(data.series, data.seriesName);
    const itemTrigger = type === 'pie' || type === 'radar' || type === 'gauge';
    const option = {
        color: ECHARTS_PALETTE,
        tooltip: { trigger: itemTrigger ? 'item' : 'axis' },
        textStyle: { fontFamily: 'inherit' },
    };
    if (series.length > 1 && type !== 'gauge') option.legend = { top: 0, type: 'scroll' };

    if (type === 'pie') {
        const first = series[0] || { data: [] };
        option.series = [{
            type: 'pie', radius: ['40%', '70%'],
            data: labels.map((l, i) => ({ name: l, value: (first.data || [])[i] })),
        }];
        return option;
    }
    if (type === 'gauge') {
        const raw = (series[0] && Array.isArray(series[0].data)) ? series[0].data[0] : undefined;
        const value = Number(raw != null ? raw : (Array.isArray(data.series) ? data.series[0] : 0)) || 0;
        const max = Number(data.max) > 0 ? Number(data.max) : Math.max(100, Math.ceil(value / 10) * 10);
        option.series = [{
            type: 'gauge', min: 0, max, progress: { show: true }, axisLine: { lineStyle: { width: 12 } },
            detail: { valueAnimation: true, fontSize: 22, formatter: '{value}' },
            data: [{ value, name: (labels[0] != null ? String(labels[0]) : (data.seriesName || '')) }],
        }];
        return option;
    }
    if (type === 'radar') {
        let max = 0;
        series.forEach((s) => (Array.isArray(s.data) ? s.data : []).forEach((v) => {
            const n = Number(v); if (isFinite(n) && n > max) max = n;
        }));
        option.radar = { indicator: labels.map((l) => ({ name: String(l), max: max > 0 ? max : 1 })) };
        option.series = [{ type: 'radar', data: series.map((s) => ({ name: s.name, value: s.data })) }];
        return option;
    }
    if (type === 'scatter') {
        let pts = data.series;
        if (Array.isArray(pts) && pts.length && Array.isArray(pts[0])) {
            pts = [{ name: data.seriesName || 'Points', data: pts }];
        }
        if (!Array.isArray(pts)) pts = [];
        option.legend = pts.length > 1 ? { top: 0, type: 'scroll' } : undefined;
        option.grid = { left: 12, right: 16, top: pts.length > 1 ? 34 : 16, bottom: 8, containLabel: true };
        option.xAxis = { type: 'value' };
        option.yAxis = { type: 'value' };
        option.series = pts.map((s) => ({ type: 'scatter', name: s.name, data: s.data, symbolSize: 10 }));
        return option;
    }
    const lineish = type === 'line' || type === 'area';
    option.grid = { left: 12, right: 16, top: series.length > 1 ? 34 : 16, bottom: 8, containLabel: true };
    option.xAxis = { type: 'category', data: labels, axisLabel: { interval: 0, rotate: labels.length > 6 ? 32 : 0 } };
    option.yAxis = { type: 'value' };
    option.series = series.map((s) => ({
        type: lineish ? 'line' : 'bar',
        name: s.name,
        data: s.data,
        smooth: lineish,
        areaStyle: type === 'area' ? { opacity: 0.25 } : (type === 'line' && series.length === 1 ? { opacity: 0.12 } : undefined),
        barMaxWidth: 46,
    }));
    return option;
}

registerActionCard('chart', (data) => echartsCard('📊', data.title, buildChartOption(data)));

function buildCandlestickOption(data) {
    const rows = Array.isArray(data.data) ? data.data : [];
    const cats = rows.map((r) => String(r.t || r.date || r.time || ''));
    const ohlc = rows.map((r) => [Number(r.o), Number(r.c), Number(r.l), Number(r.h)]);
    const opt = {
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
        textStyle: { fontFamily: 'inherit' },
        grid: { left: 12, right: 16, top: 16, bottom: 8, containLabel: true },
        xAxis: { type: 'category', data: cats, axisLabel: { rotate: cats.length > 10 ? 32 : 0 } },
        yAxis: { type: 'value', scale: true },
        series: [{
            type: 'candlestick', data: ohlc,
            itemStyle: { color: '#4ab26e', color0: '#d7263d', borderColor: '#4ab26e', borderColor0: '#d7263d' },
        }],
    };
    if (data.volume) {
        opt.series.push({ type: 'bar', data: rows.map((r) => Number(r.v) || 0), itemStyle: { color: 'rgba(59,130,246,0.35)' } });
    }
    return opt;
}

registerActionCard('candlestick', (data) => echartsCard('📈', data.title || 'Candlestick', buildCandlestickOption(data)));

function buildHeatmapOption(data) {
    const xLabels = Array.isArray(data.xLabels) ? data.xLabels.map(String) : [];
    const yLabels = Array.isArray(data.yLabels) ? data.yLabels.map(String) : [];
    const raw = Array.isArray(data.data) ? data.data : [];
    const cells = raw.map((p) => Array.isArray(p) ? [p[0], p[1], Number(p[2])] : [p.x, p.y, Number(p.value)]);
    let max = 0;
    cells.forEach((c) => { if (isFinite(c[2]) && c[2] > max) max = c[2]; });
    return {
        tooltip: { position: 'top' },
        textStyle: { fontFamily: 'inherit' },
        grid: { left: 12, right: 16, top: 16, bottom: 36, containLabel: true },
        xAxis: { type: 'category', data: xLabels, splitArea: { show: true } },
        yAxis: { type: 'category', data: yLabels, splitArea: { show: true } },
        visualMap: { min: 0, max: max || 1, calculable: true, orient: 'horizontal', left: 'center', bottom: 0,
            inRange: { color: ['#e9f5ee', '#4ab26e', '#1f7a44'] } },
        series: [{ type: 'heatmap', data: cells, label: { show: cells.length <= 80 }, emphasis: { itemStyle: { shadowBlur: 6 } } }],
    };
}

registerActionCard('heatmap', (data) => echartsCard('🔥', data.title || 'Heatmap', buildHeatmapOption(data)));

function buildSankeyOption(data) {
    const nodes = (Array.isArray(data.nodes) ? data.nodes : []).map((n) => typeof n === 'string' ? { name: n } : n);
    const links = (Array.isArray(data.links) ? data.links : []).map((l) => ({
        source: l.source, target: l.target, value: Number(l.value) || 0,
    }));
    return {
        color: ECHARTS_PALETTE,
        tooltip: { trigger: 'item', triggerOn: 'mousemove' },
        textStyle: { fontFamily: 'inherit' },
        series: [{ type: 'sankey', data: nodes, links, emphasis: { focus: 'adjacency' },
            lineStyle: { color: 'gradient', opacity: 0.5 }, label: { fontSize: 11 } }],
    };
}

registerActionCard('sankey', (data) => echartsCard('🌊', data.title || 'Flow', buildSankeyOption(data)));

function buildFunnelOption(data) {
    const items = (Array.isArray(data.data) ? data.data : []).map((i) => ({ name: i.name, value: Number(i.value) || 0 }));
    return {
        color: ECHARTS_PALETTE,
        tooltip: { trigger: 'item', formatter: '{b}: {c}' },
        textStyle: { fontFamily: 'inherit' },
        legend: { top: 0, type: 'scroll' },
        series: [{ type: 'funnel', left: '10%', width: '80%', top: 28, label: { show: true, position: 'inside' },
            data: items }],
    };
}

registerActionCard('funnel', (data) => echartsCard('🔻', data.title || 'Funnel', buildFunnelOption(data)));

function buildTreemapOption(data) {
    return {
        color: ECHARTS_PALETTE,
        tooltip: { trigger: 'item', formatter: '{b}: {c}' },
        textStyle: { fontFamily: 'inherit' },
        series: [{ type: 'treemap', data: Array.isArray(data.data) ? data.data : [], roam: false,
            breadcrumb: { show: false }, label: { show: true, formatter: '{b}' },
            levels: [{ itemStyle: { borderColor: '#fff', borderWidth: 2, gapWidth: 2 } }] }],
    };
}

registerActionCard('treemap', (data) => echartsCard('🗂️', data.title || 'Treemap', buildTreemapOption(data)));

function buildGraphOption(data) {
    const cats = [];
    const catIndex = {};
    const nodes = (Array.isArray(data.nodes) ? data.nodes : []).map((n) => {
        const o = typeof n === 'string' ? { name: n } : Object.assign({}, n);
        const v = Number(o.value);
        o.symbolSize = isFinite(v) && v > 0 ? Math.max(12, Math.min(64, 12 + v)) : (o.symbolSize || 26);
        if (o.category != null) {
            const c = String(o.category);
            if (!(c in catIndex)) { catIndex[c] = cats.length; cats.push({ name: c }); }
            o.category = catIndex[c];
        }
        return o;
    });
    const links = (Array.isArray(data.links) ? data.links : []).map((l) => {
        const w = Number(l.value);
        return {
            source: l.source, target: l.target, value: l.value,
            lineStyle: isFinite(w) && w > 0 ? { width: Math.max(1, Math.min(8, w)) } : undefined,
        };
    });
    const option = {
        color: ECHARTS_PALETTE,
        tooltip: {},
        textStyle: { fontFamily: 'inherit' },
        series: [{
            type: 'graph', layout: 'force', roam: true, draggable: true,
            top: 34, left: 24, right: 24, bottom: 16,
            label: { show: true, position: 'right', fontSize: 11 },
            force: { repulsion: 200, edgeLength: 90, gravity: 0.08 },
            emphasis: { focus: 'adjacency' },
            lineStyle: { color: 'source', curveness: 0.08, opacity: 0.7 },
            edgeSymbol: data.directed ? ['none', 'arrow'] : ['none', 'none'],
            edgeSymbolSize: 8,
            data: nodes, links, categories: cats.length ? cats : undefined,
        }],
    };
    if (cats.length) option.legend = { top: 0, type: 'scroll', data: cats.map((c) => c.name) };
    return option;
}

registerActionCard('graph', (data) => echartsCard('🔗', data.title || 'Graph', buildGraphOption(data)));

function buildWindRoseOption(data) {
    const dirs = Array.isArray(data.directions) ? data.directions.map(String) : [];
    const series = normalizeChartSeries(data.series, data.seriesName);
    const stacked = series.length > 1;
    const option = {
        color: ECHARTS_PALETTE,
        tooltip: { trigger: 'item' },
        textStyle: { fontFamily: 'inherit' },
        polar: {},
        angleAxis: { type: 'category', data: dirs, startAngle: 90, boundaryGap: true },
        radiusAxis: { min: 0 },
        series: series.map((s) => ({
            type: 'bar', name: s.name, data: s.data, coordinateSystem: 'polar',
            stack: stacked ? 'total' : undefined,
        })),
    };
    if (stacked) option.legend = { top: 0, type: 'scroll' };
    return option;
}

registerActionCard('windrose', (data) => echartsCard('🧭', data.title || 'Wind rose', buildWindRoseOption(data)));

let choroplethSeq = 0;

function buildChoroplethOption(data, mapName) {
    const values = Array.isArray(data.values) ? data.values : [];
    const nums = values.map((v) => Number(v.value)).filter((n) => isFinite(n));
    const min = nums.length ? Math.min(...nums) : 0;
    const max = nums.length ? Math.max(...nums) : 1;
    return {
        tooltip: {
            trigger: 'item',
            formatter: (p) => p.name + ': ' + (p.value == null || isNaN(p.value) ? 'n/a' : p.value),
        },
        textStyle: { fontFamily: 'inherit' },
        visualMap: {
            min, max: max > min ? max : min + 1, calculable: true,
            left: 'left', bottom: 0, orient: 'horizontal',
            inRange: { color: ['#e9f5ee', '#4ab26e', '#1f7a44'] },
        },
        series: [{
            type: 'map', map: mapName, roam: true,
            nameProperty: data.nameProperty || 'name',
            label: { show: values.length <= 20 },
            emphasis: { label: { show: true }, itemStyle: { areaColor: '#f4a236' } },
            data: values.map((v) => ({ name: v.name, value: Number(v.value) })),
        }],
    };
}

registerActionCard('choropleth', (data) => {
    let geo = data.geoJson;
    if (typeof geo === 'string') {
        try { geo = JSON.parse(geo); } catch (e) { geo = null; }
    }
    if (!geo || typeof geo !== 'object') return null;
    const mapName = 'saip-choro-' + (++choroplethSeq);
    try { echarts.registerMap(mapName, geo); } catch (e) { return null; }
    return echartsCard('🗺️', data.title || 'Region map', buildChoroplethOption(data, mapName));
});

function weightColor(w) {
    if (w == null) return '#4ab26e';
    return w >= 6 ? '#d7263d' : w >= 5.5 ? '#f46036' : w >= 5 ? '#f4a236' : '#f4d03f';
}

const MAP_BASEMAPS = {
    light: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png',
    dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
};

function basemapLayer(style) {
    return L.tileLayer(MAP_BASEMAPS[style], { maxZoom: 8, subdomains: 'abcd', crossOrigin: true });
}

function exportLeafletMap(map, box) {
    const size = map.getSize();
    const out = document.createElement('canvas');
    out.width = size.x;
    out.height = size.y;
    const ctx = out.getContext('2d');
    ctx.fillStyle = box.dataset.style === 'dark' ? '#0b1322' : '#eaeef2';
    ctx.fillRect(0, 0, size.x, size.y);
    const base = box.getBoundingClientRect();
    box.querySelectorAll('img.leaflet-tile').forEach((img) => {
        if (!img.complete || !img.naturalWidth) return;
        const r = img.getBoundingClientRect();
        ctx.drawImage(img, r.left - base.left, r.top - base.top, r.width, r.height);
    });
    box.querySelectorAll('canvas').forEach((cv) => {
        if (cv === out || !cv.width || !cv.height) return;
        const r = cv.getBoundingClientRect();
        ctx.drawImage(cv, r.left - base.left, r.top - base.top, r.width, r.height);
    });
    return out.toDataURL('image/png');
}

registerActionCard('pointmap', (data) => {
    const points = Array.isArray(data.points)
        ? data.points.filter((p) => p && typeof p.lat === 'number' && typeof p.lng === 'number') : [];
    if (!points.length) return null;
    const card = actionCardShell('🗺️', data.title || 'Map');
    let style = data.style === 'dark' ? 'dark' : 'light';
    const box = document.createElement('div');
    box.className = 'saip-action-pointmap';
    box.dataset.style = style;
    card.appendChild(box);
    try {
        const map = L.map(box, {
            worldCopyJump: true, attributionControl: false, scrollWheelZoom: false, preferCanvas: true,
        });
        let tiles = basemapLayer(style).addTo(map);
        const latlngs = [];
        points.forEach((p) => {
            const w = typeof p.weight === 'number' ? p.weight : (typeof p.mag === 'number' ? p.mag : null);
            const r = w == null ? 6 : 4 + Math.max(0, w - 4) * 3.5;
            L.circleMarker([p.lat, p.lng], { radius: r, color: '#fff', weight: 1.2, fillColor: weightColor(w), fillOpacity: 0.9 })
                .bindTooltip(String(p.label || '') + (w != null ? ' · ' + w : ''))
                .addTo(map);
            latlngs.push([p.lat, p.lng]);
        });
        const fitView = () => {
            if (latlngs.length > 1) map.fitBounds(latlngs, { padding: [28, 28], maxZoom: 6 });
            else map.setView(latlngs[0], 5);
        };
        fitView();
        const toggle = L.control({ position: 'topright' });
        toggle.onAdd = () => {
            const div = L.DomUtil.create('div', 'saip-map-style leaflet-bar');
            ['light', 'dark'].forEach((s) => {
                const b = L.DomUtil.create('button', s === style ? 'active' : '', div);
                b.type = 'button';
                b.textContent = s === 'light' ? 'Light' : 'Dark';
                b.addEventListener('click', () => {
                    if (s === style) return;
                    style = s;
                    box.dataset.style = s;
                    map.removeLayer(tiles);
                    tiles = basemapLayer(s).addTo(map);
                    div.querySelectorAll('button').forEach((x) => x.classList.toggle('active', x.textContent.toLowerCase() === s));
                });
            });
            L.DomEvent.disableClickPropagation(div);
            return div;
        };
        toggle.addTo(map);
        setTimeout(() => { map.invalidateSize(); fitView(); }, 150);
        addImageExport(card, () => exportLeafletMap(map, box), fileSlug(data.title, 'map') + '.png');
    } catch (e) {
        box.classList.add('saip-visualization-failed');
        box.textContent = 'Map could not be rendered.';
    }
    return card;
});

let heatLutCache = null;

function heatGradientLut() {
    if (heatLutCache) return heatLutCache;
    const c = document.createElement('canvas');
    c.width = 1;
    c.height = 256;
    const ctx = c.getContext('2d');
    const g = ctx.createLinearGradient(0, 0, 0, 256);
    g.addColorStop(0.4, '#2c7fb8');
    g.addColorStop(0.55, '#41b6c4');
    g.addColorStop(0.7, '#7fcdbb');
    g.addColorStop(0.82, '#c7e9b4');
    g.addColorStop(0.92, '#fdae61');
    g.addColorStop(1.0, '#d7263d');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 1, 256);
    heatLutCache = ctx.getImageData(0, 0, 1, 256).data;
    return heatLutCache;
}

function heatBrush(radius) {
    const brush = document.createElement('canvas');
    const r2 = radius * 2;
    brush.width = r2;
    brush.height = r2;
    const ctx = brush.getContext('2d');
    const grd = ctx.createRadialGradient(radius, radius, 0, radius, radius, radius);
    grd.addColorStop(0, 'rgba(0,0,0,1)');
    grd.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = grd;
    ctx.fillRect(0, 0, r2, r2);
    return brush;
}

function drawHeat(canvas, map, points, radius, maxIntensity) {
    const size = map.getSize();
    canvas.width = size.x;
    canvas.height = size.y;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, size.x, size.y);
    const brush = heatBrush(radius);
    const maxV = maxIntensity > 0 ? maxIntensity : 1;
    points.forEach((p) => {
        const pt = map.latLngToContainerPoint([p.lat, p.lng]);
        if (pt.x < -radius || pt.y < -radius || pt.x > size.x + radius || pt.y > size.y + radius) return;
        ctx.globalAlpha = Math.max(0.08, Math.min(1, (p.intensity != null ? p.intensity : 1) / maxV));
        ctx.drawImage(brush, pt.x - radius, pt.y - radius);
    });
    ctx.globalAlpha = 1;
    const img = ctx.getImageData(0, 0, size.x, size.y);
    const d = img.data;
    const lut = heatGradientLut();
    for (let i = 0; i < d.length; i += 4) {
        const a = d[i + 3];
        if (!a) continue;
        const off = a << 2;
        d[i] = lut[off];
        d[i + 1] = lut[off + 1];
        d[i + 2] = lut[off + 2];
        d[i + 3] = Math.min(220, a + 60);
    }
    ctx.putImageData(img, 0, 0);
}

registerActionCard('geoheat', (data) => {
    const points = Array.isArray(data.points)
        ? data.points.filter((p) => p && typeof p.lat === 'number' && typeof p.lng === 'number') : [];
    if (!points.length) return null;
    const card = actionCardShell('🌡️', data.title || 'Density map');
    let style = data.style === 'dark' ? 'dark' : 'light';
    const radius = Math.max(8, Math.min(60, Number(data.radius) > 0 ? Number(data.radius) : 28));
    let maxIntensity = 0;
    points.forEach((p) => { const v = p.intensity != null ? Number(p.intensity) : 1; if (v > maxIntensity) maxIntensity = v; });
    const box = document.createElement('div');
    box.className = 'saip-action-pointmap';
    box.dataset.style = style;
    card.appendChild(box);
    try {
        const map = L.map(box, {
            worldCopyJump: true, attributionControl: false, scrollWheelZoom: false, preferCanvas: true,
        });
        let tiles = basemapLayer(style).addTo(map);
        const heatCanvas = document.createElement('canvas');
        heatCanvas.className = 'saip-heat-layer';
        box.appendChild(heatCanvas);
        const latlngs = points.map((p) => [p.lat, p.lng]);
        const fitView = () => {
            if (latlngs.length > 1) map.fitBounds(latlngs, { padding: [28, 28], maxZoom: 7 });
            else map.setView(latlngs[0], 6);
        };
        fitView();
        const redraw = () => drawHeat(heatCanvas, map, points, radius, maxIntensity);
        map.on('moveend zoomend resize', redraw);
        const toggle = L.control({ position: 'topright' });
        toggle.onAdd = () => {
            const div = L.DomUtil.create('div', 'saip-map-style leaflet-bar');
            ['light', 'dark'].forEach((s) => {
                const b = L.DomUtil.create('button', s === style ? 'active' : '', div);
                b.type = 'button';
                b.textContent = s === 'light' ? 'Light' : 'Dark';
                b.addEventListener('click', () => {
                    if (s === style) return;
                    style = s;
                    box.dataset.style = s;
                    map.removeLayer(tiles);
                    tiles = basemapLayer(s).addTo(map);
                    div.querySelectorAll('button').forEach((x) => x.classList.toggle('active', x.textContent.toLowerCase() === s));
                });
            });
            L.DomEvent.disableClickPropagation(div);
            return div;
        };
        toggle.addTo(map);
        setTimeout(() => { map.invalidateSize(); fitView(); redraw(); }, 150);
        addImageExport(card, () => exportLeafletMap(map, box), fileSlug(data.title, 'density') + '.png');
    } catch (e) {
        box.classList.add('saip-visualization-failed');
        box.textContent = 'Map could not be rendered.';
    }
    return card;
});

registerActionCard('image', (data) => {
    const src = String(data.url || '').trim();
    if (!/^https?:\/\//i.test(src)) return null;
    const card = actionCardShell('🖼️', data.caption || data.title || 'Image');
    const img = document.createElement('img');
    img.className = 'saip-action-image';
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.alt = String(data.alt || data.caption || 'image');
    img.addEventListener('error', () => {
        img.remove();
        const fail = document.createElement('div');
        fail.className = 'saip-visualization-failed';
        const link = document.createElement('a');
        link.href = src;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = 'Image could not be loaded. Open in new tab';
        fail.appendChild(link);
        card.appendChild(fail);
    });
    img.src = src;
    card.appendChild(img);
    return card;
});

registerActionCard('diagram', (data) => {
    const code = String(data.code || '').trim();
    if (!code) return null;
    const card = actionCardShell('🧩', data.title || 'Diagram');
    const container = document.createElement('div');
    container.className = 'saip-action-diagram';
    card.appendChild(container);
    mountMermaid(container, code);
    return card;
});

function lineDiff(a, b) {
    const A = a.split('\n');
    const B = b.split('\n');
    const n = A.length;
    const m = B.length;
    const dp = [];
    for (let i = 0; i <= n; i++) dp.push(new Int32Array(m + 1));
    for (let i = n - 1; i >= 0; i--) {
        for (let j = m - 1; j >= 0; j--) {
            dp[i][j] = A[i] === B[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
        }
    }
    const rows = [];
    let i = 0;
    let j = 0;
    while (i < n && j < m) {
        if (A[i] === B[j]) { rows.push({ op: '=', a: A[i], b: B[j] }); i++; j++; }
        else if (dp[i + 1][j] >= dp[i][j + 1]) { rows.push({ op: '-', a: A[i], b: null }); i++; }
        else { rows.push({ op: '+', a: null, b: B[j] }); j++; }
    }
    while (i < n) { rows.push({ op: '-', a: A[i], b: null }); i++; }
    while (j < m) { rows.push({ op: '+', a: null, b: B[j] }); j++; }
    const common = rows.filter((r) => r.op === '=').length;
    return { rows, similarity: common / Math.max(n, m, 1) };
}

registerActionCard('diff', (data) => {
    const card = actionCardShell('🔃', data.title || 'Diff');
    const aText = String(data.left == null ? '' : data.left);
    const bText = String(data.right == null ? '' : data.right);
    const ll = String(data.leftLabel || 'A');
    const rl = String(data.rightLabel || 'B');
    const MAX_LINES = 1200;
    if (aText.split('\n').length > MAX_LINES || bText.split('\n').length > MAX_LINES) {
        const warn = document.createElement('div');
        warn.className = 'saip-visualization-failed';
        warn.textContent = 'Inputs too large to diff inline (over ' + MAX_LINES + ' lines each).';
        card.appendChild(warn);
        return card;
    }
    const result = lineDiff(aText, bText);
    const pct = Math.round(result.similarity * 100);
    const summary = document.createElement('div');
    summary.className = 'saip-diff-summary';
    if (result.similarity < 0.1) {
        summary.classList.add('saip-diff-verydifferent');
        summary.textContent = ll + ' and ' + rl + ' are completely different (' + pct + '% of lines in common).';
    } else {
        const added = result.rows.filter((r) => r.op === '+').length;
        const removed = result.rows.filter((r) => r.op === '-').length;
        summary.textContent = pct + '% of lines in common, +' + added + ' / -' + removed;
    }
    card.appendChild(summary);
    const table = document.createElement('table');
    table.className = 'saip-diff';
    const head = document.createElement('tr');
    const hl = document.createElement('th'); hl.colSpan = 2; hl.textContent = ll;
    const hr = document.createElement('th'); hr.colSpan = 2; hr.textContent = rl;
    head.appendChild(hl); head.appendChild(hr);
    table.appendChild(head);
    let ln = 0;
    let rn = 0;
    result.rows.forEach((r) => {
        const tr = document.createElement('tr');
        tr.className = 'saip-diff-' + (r.op === '=' ? 'eq' : r.op === '-' ? 'del' : 'add');
        const lNum = document.createElement('td'); lNum.className = 'saip-diff-num';
        const lTxt = document.createElement('td'); lTxt.className = 'saip-diff-text';
        const rNum = document.createElement('td'); rNum.className = 'saip-diff-num';
        const rTxt = document.createElement('td'); rTxt.className = 'saip-diff-text';
        if (r.a != null) { ln++; lNum.textContent = String(ln); lTxt.textContent = r.a; }
        if (r.b != null) { rn++; rNum.textContent = String(rn); rTxt.textContent = r.b; }
        tr.appendChild(lNum); tr.appendChild(lTxt); tr.appendChild(rNum); tr.appendChild(rTxt);
        table.appendChild(tr);
    });
    const wrap = document.createElement('div');
    wrap.className = 'saip-diff-wrap';
    wrap.appendChild(table);
    card.appendChild(wrap);
    return card;
});

registerActionCard('table', (data) => {
    const cols = Array.isArray(data.columns) ? data.columns : [];
    const rowsData = Array.isArray(data.rows) ? data.rows : [];
    if (!cols.length) return null;
    const card = actionCardShell('📋', data.title || 'Table');
    const heatRange = {};
    cols.forEach((c) => {
        if (!c.heat) return;
        const vals = rowsData.map((r) => Number(r[c.key])).filter((v) => isFinite(v));
        if (vals.length) heatRange[c.key] = { min: Math.min(...vals), max: Math.max(...vals) };
    });
    let filter = '';
    let sortKey = null;
    let sortDir = 1;
    if (data.searchable) {
        const search = document.createElement('input');
        search.className = 'saip-table-search';
        search.type = 'search';
        search.placeholder = 'Filter rows';
        search.addEventListener('input', () => { filter = search.value.toLowerCase(); render(); });
        card.appendChild(search);
    }
    const wrap = document.createElement('div');
    wrap.className = 'saip-table-wrap';
    const table = document.createElement('table');
    table.className = 'saip-table';
    wrap.appendChild(table);
    card.appendChild(wrap);
    function render() {
        table.textContent = '';
        const thead = document.createElement('tr');
        cols.forEach((c) => {
            const th = document.createElement('th');
            th.textContent = (c.label || c.key) + (sortKey === c.key ? (sortDir > 0 ? ' ▲' : ' ▼') : '');
            if (c.align === 'right') th.style.textAlign = 'right';
            if (data.sortable) {
                th.classList.add('saip-table-sortable');
                th.addEventListener('click', () => {
                    if (sortKey === c.key) sortDir = -sortDir; else { sortKey = c.key; sortDir = 1; }
                    render();
                });
            }
            thead.appendChild(th);
        });
        table.appendChild(thead);
        let view = rowsData.slice();
        if (filter) {
            view = view.filter((r) => cols.some((c) => String(r[c.key] == null ? '' : r[c.key]).toLowerCase().includes(filter)));
        }
        if (sortKey) {
            view.sort((a, b) => {
                const nx = Number(a[sortKey]);
                const ny = Number(b[sortKey]);
                const cmp = (isFinite(nx) && isFinite(ny)) ? nx - ny
                    : String(a[sortKey] == null ? '' : a[sortKey]).localeCompare(String(b[sortKey] == null ? '' : b[sortKey]));
                return cmp * sortDir;
            });
        }
        view.forEach((r) => {
            const tr = document.createElement('tr');
            cols.forEach((c) => {
                const td = document.createElement('td');
                let v = r[c.key];
                if (c.format === 'number' && isFinite(Number(v))) v = Number(v).toLocaleString();
                td.textContent = v == null ? '' : String(v);
                if (c.align === 'right') td.style.textAlign = 'right';
                const hr = heatRange[c.key];
                if (hr && hr.max > hr.min) {
                    const t = (Number(r[c.key]) - hr.min) / (hr.max - hr.min);
                    if (isFinite(t)) td.style.background = 'rgba(74,178,110,' + (0.08 + t * 0.45).toFixed(2) + ')';
                }
                tr.appendChild(td);
            });
            table.appendChild(tr);
        });
    }
    render();
    return card;
});

registerActionCard('stats', (data) => {
    const tiles = Array.isArray(data.cards) ? data.cards : [];
    if (!tiles.length) return null;
    const card = actionCardShell('📇', data.title || 'Stats');
    const grid = document.createElement('div');
    grid.className = 'saip-stat-grid';
    tiles.forEach((c) => {
        const tile = document.createElement('div');
        tile.className = 'saip-stat-tile';
        const label = document.createElement('div');
        label.className = 'saip-stat-label';
        label.textContent = c.label == null ? '' : String(c.label);
        const value = document.createElement('div');
        value.className = 'saip-stat-value';
        value.textContent = c.value == null ? '' : String(c.value);
        tile.appendChild(label);
        tile.appendChild(value);
        if (c.delta != null && String(c.delta).trim() !== '') {
            const trend = c.trend || (String(c.delta).trim().startsWith('-') ? 'down' : 'up');
            const delta = document.createElement('div');
            delta.className = 'saip-stat-delta saip-stat-' + (trend === 'down' ? 'down' : trend === 'flat' ? 'flat' : 'up');
            delta.textContent = (trend === 'up' ? '▲ ' : trend === 'down' ? '▼ ' : '') + c.delta;
            tile.appendChild(delta);
        }
        grid.appendChild(tile);
    });
    card.appendChild(grid);
    return card;
});

registerActionCard('comparison', (data) => {
    const entities = Array.isArray(data.entities) ? data.entities : [];
    const rows = Array.isArray(data.rows) ? data.rows : [];
    if (entities.length < 2) return null;
    const card = actionCardShell('⚖️', data.title || 'Comparison');
    const table = document.createElement('table');
    table.className = 'saip-compare';
    const head = document.createElement('tr');
    head.appendChild(document.createElement('th'));
    entities.forEach((e) => { const th = document.createElement('th'); th.textContent = String(e); head.appendChild(th); });
    table.appendChild(head);
    rows.forEach((r) => {
        const tr = document.createElement('tr');
        const label = document.createElement('td');
        label.className = 'saip-compare-label';
        label.textContent = r.label == null ? '' : String(r.label);
        tr.appendChild(label);
        (Array.isArray(r.values) ? r.values : []).forEach((v, idx) => {
            const td = document.createElement('td');
            td.textContent = v == null ? '' : String(v);
            if (r.best === idx) td.classList.add('saip-compare-best');
            tr.appendChild(td);
        });
        table.appendChild(tr);
    });
    const wrap = document.createElement('div');
    wrap.className = 'saip-table-wrap';
    wrap.appendChild(table);
    card.appendChild(wrap);
    return card;
});

function formatTimelineDate(value) {
    if (value == null) return '';
    try {
        const d = new Date(value);
        if (!isNaN(d.getTime())) return d.toLocaleString(undefined, { dateStyle: 'medium' });
    } catch (e) {
        return String(value);
    }
    return String(value);
}

registerActionCard('timeline', (data) => {
    const events = Array.isArray(data.events) ? data.events : [];
    if (!events.length) return null;
    const card = actionCardShell('🕒', data.title || 'Timeline');
    const list = document.createElement('div');
    list.className = 'saip-timeline';
    events.forEach((e) => {
        const item = document.createElement('div');
        item.className = 'saip-timeline-item';
        const dot = document.createElement('div');
        dot.className = 'saip-timeline-dot';
        const body = document.createElement('div');
        body.className = 'saip-timeline-body';
        const when = document.createElement('div');
        when.className = 'saip-timeline-when';
        when.textContent = formatTimelineDate(e.date);
        const title = document.createElement('div');
        title.className = 'saip-timeline-title';
        title.textContent = e.title == null ? '' : String(e.title);
        body.appendChild(when);
        body.appendChild(title);
        if (e.detail) {
            const detail = document.createElement('div');
            detail.className = 'saip-timeline-detail';
            detail.textContent = String(e.detail);
            body.appendChild(detail);
        }
        item.appendChild(dot);
        item.appendChild(body);
        list.appendChild(item);
    });
    card.appendChild(list);
    return card;
});

function enhanceActionCards(root) {
    root.querySelectorAll('pre > code').forEach((code) => {
        if (!code.classList.contains('language-saip-action')
            && !code.classList.contains('language-saip-action-return-direct')) return;
        const pre = code.parentElement;
        if (pre.dataset.saipAction) return;
        let data;
        try {
            data = JSON.parse(code.textContent.trim());
        } catch (e) {
            return;
        }
        const card = buildActionCard(data);
        if (!card) return;
        if (window.gtag) window.gtag('event', 'action_card_rendered', { card_type: data.type });
        pre.dataset.saipAction = 'done';
        pre.replaceWith(card);
    });
}

registerEnhancer(enhanceLinks);
registerEnhancer(enhanceFilePaths);
registerEnhancer(renderMermaid);
registerEnhancer(enhanceActionCards);
registerEnhancer(highlightCode);
registerEnhancer(renderMath);
registerEnhancer(enhanceCodeBlocks);

function enhance(markdownEl) {
    if (!markdownEl) return;
    if (!markdownEl.__saipObserver) {
        let timer = null;
        const observer = new MutationObserver(() => {
            clearTimeout(timer);
            timer = setTimeout(() => applyNow(markdownEl), SETTLE_MS);
        });
        markdownEl.__saipObserver = observer;
        observer.observe(markdownEl, { childList: true, subtree: true });
    }
    applyNow(markdownEl);
}

function openPrintWindow() {
    const w = window.open('', '_blank');
    if (!w) return null;
    document.querySelectorAll('style, link[rel="stylesheet"]').forEach((node) => {
        try {
            w.document.head.appendChild(node.cloneNode(true));
        } catch (e) {
        }
    });
    w.document.title = 'Spring AI Playground - Chat';
    return w;
}

function printMessages(messages) {
    const list = Array.from(messages);
    if (!list.length) return;
    const w = openPrintWindow();
    if (!w) return;
    const body = w.document.body;
    body.style.maxWidth = '800px';
    body.style.margin = '0 auto';
    body.style.padding = '24px';
    list.forEach((msg) => {
        const md = msg.querySelector('vaadin-markdown');
        if (!md) return;
        const block = w.document.createElement('section');
        block.style.margin = '0 0 20px';
        const who = msg.getAttribute('userName');
        if (who) {
            const heading = w.document.createElement('div');
            heading.textContent = who;
            heading.style.cssText = 'font-weight:600;opacity:0.6;margin:0 0 6px';
            block.appendChild(heading);
        }
        Array.from(md.children).forEach((child) => {
            const clone = child.cloneNode(true);
            if (clone.querySelectorAll) clone.querySelectorAll('.saip-code-copy, .saip-code-lang').forEach((b) => b.remove());
            block.appendChild(clone);
        });
        body.appendChild(block);
    });
    w.onafterprint = () => { try { w.close(); } catch (e) { } };
    setTimeout(() => { try { w.focus(); w.print(); } catch (e) { } }, 500);
}

function printContainer(root) {
    if (!root) return;
    printMessages(Array.from(root.querySelectorAll('vaadin-message')).filter((m) => !m.closest('vaadin-details')));
}

function printMessage(messageEl) {
    if (messageEl) printMessages([messageEl]);
}

window.Saip = window.Saip || {};
window.Saip.chatMarkdown = { enhance, registerEnhancer, registerActionCard, runEnhancers, printContainer, printMessage };
