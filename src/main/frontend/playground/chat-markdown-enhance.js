// Post-processing pipeline for chat markdown messages.
//
// <vaadin-markdown> renders parsed HTML into its own light DOM via an in-place DOM diff
// (marked + DOMPurify). That diff strips any node or attribute we inject as soon as the next
// content sync runs, so during streaming our additions would flicker away. We therefore enhance
// only once rendering has settled: a debounced MutationObserver re-applies every registered
// enhancer SETTLE_MS after the last mutation, disconnecting itself while it works so it never
// reacts to its own DOM writes. Enhancers must be idempotent (skip nodes they already touched)
// because re-attach and re-render replay the whole subtree.
//
// Libraries are bundled locally (npm, not CDN) so highlighting and math work offline.

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

function enhanceCodeBlocks(root) {
    root.querySelectorAll('pre').forEach((pre) => {
        if (pre.querySelector(':scope > .saip-code-copy')) return;
        const code = pre.querySelector('code');
        const langClass = code && Array.from(code.classList).find((c) => c.startsWith('language-'));
        // hljs auto-detect stamps "language-undefined" on fences without a language tag - no label for those.
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

// Replace ```mermaid blocks with a rendered SVG. We swap the <pre> for a div and let mermaid render it
// in place (mermaid.run, the v11-recommended API). Rendering is async and, in some prod bundles, can
// stall even after its chunks load, so a timeout falls the block back to readable source rather than
// leaving it blank or hanging. Marked synchronously so the settle pass never processes it twice.
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
        // mermaid's internal render isn't concurrency-safe, so chain diagrams one at a time.
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

registerEnhancer(enhanceLinks);
registerEnhancer(renderMermaid);
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

// Print / Save-as-PDF. We open a blank window, copy the document's stylesheets (so highlight/katex
// styles carry over), and clone each message's already-rendered markdown CHILDREN (plain HTML, not the
// <vaadin-markdown> custom element, which would not upgrade in the new window). The browser print dialog
// then offers "Save as PDF".
function openPrintWindow() {
    const w = window.open('', '_blank');
    if (!w) return null;
    document.querySelectorAll('style, link[rel="stylesheet"]').forEach((node) => {
        try {
            w.document.head.appendChild(node.cloneNode(true));
        } catch (e) {
            /* cross-origin stylesheet node - skip */
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
    w.onafterprint = () => { try { w.close(); } catch (e) { /* already closed */ } };
    setTimeout(() => { try { w.focus(); w.print(); } catch (e) { /* popup blocked */ } }, 500);
}

function printContainer(root) {
    if (!root) return;
    printMessages(Array.from(root.querySelectorAll('vaadin-message')).filter((m) => !m.closest('vaadin-details')));
}

function printMessage(messageEl) {
    if (messageEl) printMessages([messageEl]);
}

window.Saip = window.Saip || {};
window.Saip.chatMarkdown = { enhance, registerEnhancer, runEnhancers, printContainer, printMessage };
