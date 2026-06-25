import hljs from 'highlight.js/lib/common';
import 'highlight.js/styles/github.css';
import renderMathInElement from 'katex/contrib/auto-render';
import 'katex/dist/katex.min.css';
import mermaid from 'mermaid';

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

function renderMermaid(root) {
    root.querySelectorAll('pre > code.language-mermaid').forEach((code) => {
        const pre = code.parentElement;
        if (pre.dataset.saipMermaid) return;
        pre.dataset.saipMermaid = 'done';
        const source = code.textContent;
        const container = document.createElement('div');
        container.className = 'mermaid saip-mermaid';
        container.textContent = source;
        pre.replaceWith(container);
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
    const iconEl = document.createElement('span');
    iconEl.className = 'saip-action-icon';
    iconEl.textContent = icon;
    const titleEl = document.createElement('span');
    titleEl.className = 'saip-action-title';
    titleEl.textContent = title;
    head.appendChild(iconEl);
    head.appendChild(titleEl);
    card.appendChild(head);
    return card;
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
