(function () {
  const READY_ATTR = 'data-panzoom-ready';
  const MIN_HEIGHT = 220;
  const MAX_HEIGHT = 620;
  const SHADOW_CSS = `
    svg {
      width: 100% !important;
      height: 100% !important;
      max-width: 100% !important;
      max-height: 100% !important;
      cursor: grab;
    }
    .nodeLabel, .edgeLabel, .messageText, .actor text {
      white-space: normal !important;
      word-wrap: break-word;
    }
  `;
  let scanTimer = null;

  function injectShadowCss(shadow) {
    if (shadow._panzoomCss) return;
    const style = document.createElement('style');
    style.textContent = SHADOW_CSS;
    shadow.appendChild(style);
    shadow._panzoomCss = true;
  }

  function desiredHeight(svg, host) {
    let width, height;
    try {
      const vb = svg.viewBox && svg.viewBox.baseVal;
      if (vb && vb.width && vb.height) {
        width = vb.width;
        height = vb.height;
      } else {
        const b = svg.getBBox();
        width = b.width;
        height = b.height;
      }
    } catch (e) {
      return 400;
    }
    const hostWidth = host.clientWidth || 800;
    const scaled = height * (hostWidth / width);
    return Math.max(MIN_HEIGHT, Math.min(scaled, MAX_HEIGHT));
  }

  function enhance(host) {
    if (host.getAttribute(READY_ATTR) === '1') return;
    const shadow = host.shadowRoot;
    if (!shadow) return;
    const svg = shadow.querySelector('svg');
    if (!svg) return;
    if (svg.querySelectorAll('g').length === 0) return;
    if (typeof svgPanZoom !== 'function') return;

    // Ensure viewBox exists so svg-pan-zoom can compute bounds correctly.
    if (!svg.getAttribute('viewBox')) {
      try {
        const bbox = svg.getBBox();
        svg.setAttribute('viewBox', [bbox.x, bbox.y, bbox.width, bbox.height].join(' '));
      } catch (e) {
        return;
      }
    }

    // Strip mermaid's inline max-width so the SVG can scale to the host.
    svg.removeAttribute('style');
    svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');

    injectShadowCss(shadow);

    host.style.display = 'block';
    host.style.width = '100%';
    host.style.overflow = 'hidden';
    host.style.border = '1px solid var(--md-default-fg-color--lightest, rgba(0,0,0,.07))';
    host.style.borderRadius = '4px';
    host.style.padding = '0';
    host.style.margin = '1rem 0';
    host.style.boxSizing = 'border-box';
    host.style.height = desiredHeight(svg, host) + 'px';

    host.setAttribute(READY_ATTR, '1');

    try {
      svgPanZoom(svg, {
        zoomEnabled: true,
        controlIconsEnabled: true,
        fit: true,
        center: true,
        minZoom: 0.3,
        maxZoom: 6,
      });
    } catch (e) {
      console.warn('[mermaid pan-zoom] failed', e);
      host.removeAttribute(READY_ATTR);
    }
  }

  function scan() {
    document.querySelectorAll('.mermaid').forEach(enhance);
  }

  function scheduleScan() {
    if (scanTimer) clearTimeout(scanTimer);
    scanTimer = setTimeout(scan, 250);
  }

  function start() {
    scan();
    new MutationObserver(scheduleScan).observe(document.body, { childList: true, subtree: true });
    let attempts = 0;
    const poll = setInterval(() => {
      scan();
      if (++attempts > 60) clearInterval(poll);
    }, 500);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', start);
  } else {
    start();
  }
})();
