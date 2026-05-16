title: Safe Local Execution Layer for AI Agent Tools
description: Cross-platform desktop app for building, testing, and publishing MCP tools — Tool Studio, defense-in-depth sandbox, Risk Level (L0–L5), Agentic Chat, and RAG.

# Spring AI Playground

## Safe Local Execution Layer for AI Agent Tools

Spring AI Playground is a cross-platform desktop app for building, testing, validating, and executing MCP tools in a controlled local environment.

> **No pass, no run.**

Every tool you build earns a **Local Pass** — a local test-run with sample arguments. Only tools that pass are added live to the built-in MCP server and become callable from Agentic Chat. Nothing you author reaches an agent until you have seen it work on your own machine.

The desktop app is the recommended default experience, but Docker and local source execution are still supported when you want a server-style deployment or a development workflow.

Unlike many playgrounds that stop at prompt testing, this project connects AI conversations to real actions while making the tools it manages inside the app safer and easier to inspect before reuse:

- build JavaScript tools directly in the app
- earn a **Local Pass** by test-running each tool against sample arguments you define
- **add tools live to the built-in MCP server** the moment each passes — no restart, no redeploy
- start immediately with **86 pre-loaded default tools** spanning web fetch / datetime / math / security / encoding / crypto / filesystem / GitHub / Wikipedia / weather / finance / geo / Korean services — searchable + filterable on the [Default Tools directory](features/default-tools/index.md#browse-all-tools), and grouped across 5 reference pages: [Examples](features/default-tools/examples.md) (7) · [Utilities](features/default-tools/utilities.md) (26) · [Filesystem](features/default-tools/filesystem.md) (10) · [Global](features/default-tools/global.md) (22) · [Korea](features/default-tools/korea.md) (21)
- validate retrieval pipelines against your own documents
- run agentic chat that combines tool use and grounded context (e.g. *"Get today's weather and send it to Slack"*)
- run every tool through a **defense-in-depth GraalVM sandbox** with a deny-first class allowlist, SSRF-guarded `fetch`, rooted `safety.fs`, statement + wall-clock limits, and a visible per-tool **Risk Level** (L0–L5) — see [AI Agent Tool Safety Architecture](safety-architecture.md)

<div style="text-align: center;">
  <b>Agentic Chat Demo</b><br/>
  Tool-enabled agentic AI built with Spring AI and MCP
</div>

<div style="text-align: center;">
  <a href="assets/images/agentic-chat-demo.gif">
    <img src="assets/images/agentic-chat-demo.gif" width="800" alt="Spring AI Playground Agentic Chat Demo"/>
  </a>
</div>

## :material-rocket-launch: Quick Start

The recommended default is the desktop app distributed from GitHub Releases.

Spring AI Playground is a standalone desktop app, so you can install it and start building MCP tools without setting up a Java project, Docker environment, or source build first.

### 1. Download the Desktop App

Pick the installer for your platform. Each link resolves to the latest published release automatically.

<p class="download-badges">
  <a id="win-x64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="win-x64.exe" data-label="Windows (NSIS, x64)" rel="noopener"><img src="https://img.shields.io/badge/Windows-NSIS%20Installer-0078D6?logo=windows&logoColor=white" alt="Windows NSIS Installer"/></a>
  <a id="mac-arm64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="mac-arm64.dmg" data-label="macOS (Apple Silicon)" rel="noopener"><img src="https://img.shields.io/badge/macOS-Apple%20Silicon%20arm64-000000?logo=apple&logoColor=white" alt="macOS Apple Silicon arm64"/></a>
  <a id="mac-x64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="mac-x64.dmg" data-label="macOS (Intel)" rel="noopener"><img src="https://img.shields.io/badge/macOS-Intel%20x64-555555?logo=apple&logoColor=white" alt="macOS Intel x64"/></a>
  <a id="linux-deb" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="linux-amd64.deb" data-label="Linux (DEB, amd64)" rel="noopener"><img src="https://img.shields.io/badge/Linux-DEB-A81D33?logo=debian&logoColor=white" alt="Linux DEB"/></a>
  <a id="linux-rpm" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="linux-x86_64.rpm" data-label="Linux (RPM, x86_64)" rel="noopener"><img src="https://img.shields.io/badge/Linux-RPM-EE0000?logo=redhat&logoColor=white" alt="Linux RPM"/></a>
</p>

<p id="dl-resolved" class="dl-resolved-version" hidden></p>

<div id="dl-confirm" class="dl-confirm-overlay" hidden role="dialog" aria-modal="true" aria-labelledby="dl-confirm-title">
  <div class="dl-confirm-modal" role="document">
    <button id="dl-confirm-close" class="dl-confirm__close" type="button" aria-label="Close">&times;</button>
    <div class="dl-confirm__header">
      <span class="dl-confirm__icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3v12"/><path d="m6 11 6 6 6-6"/><path d="M5 21h14"/></svg>
      </span>
      <div class="dl-confirm__heading">
        <h4 id="dl-confirm-title" class="dl-confirm__title">Confirm download</h4>
        <p class="dl-confirm__platform"><span id="dl-confirm-label"></span></p>
      </div>
    </div>
    <dl class="dl-confirm__body">
      <div class="dl-confirm__row">
        <dt class="dl-confirm__row-label">File</dt>
        <dd class="dl-confirm__row-value">
          <code id="dl-confirm-filename"></code>
          <span id="dl-confirm-message" class="dl-confirm__message" hidden></span>
        </dd>
      </div>
      <div class="dl-confirm__row">
        <dt class="dl-confirm__row-label">Size</dt>
        <dd class="dl-confirm__row-value" id="dl-confirm-size">&mdash;</dd>
      </div>
      <div class="dl-confirm__row">
        <dt class="dl-confirm__row-label">Save to</dt>
        <dd class="dl-confirm__row-value dl-confirm__saveto">
          <span id="dl-confirm-saveto-path" class="dl-confirm__saveto-path">Downloads folder</span>
          <span class="dl-confirm__saveto-hint">(set by your browser)</span>
        </dd>
      </div>
    </dl>
    <div class="dl-confirm__actions">
      <a id="dl-confirm-go" class="download-button download-button--primary" href="#" rel="noopener">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 5v14"/><path d="m19 12-7 7-7-7"/></svg>
        <span id="dl-confirm-go-label">Download</span>
      </a>
      <button id="dl-confirm-cancel" class="download-button download-button--ghost" type="button">Cancel</button>
    </div>
  </div>
</div>

<noscript>

JavaScript is disabled in your browser, so the buttons above link to the
[latest release page](https://github.com/spring-ai-community/spring-ai-playground/releases/latest).
Pick the asset that matches your platform.

</noscript>

<script>
(function () {
  const REPO = 'spring-ai-community/spring-ai-playground';
  const FALLBACK = `https://github.com/${REPO}/releases/latest`;
  const buttons = Array.from(document.querySelectorAll('a[data-pattern]'));
  if (buttons.length === 0) return;

  const confirmEl = document.getElementById('dl-confirm');
  const confirmLabel = document.getElementById('dl-confirm-label');
  const confirmFilename = document.getElementById('dl-confirm-filename');
  const confirmMessage = document.getElementById('dl-confirm-message');
  const confirmSize = document.getElementById('dl-confirm-size');
  const confirmSavetoPath = document.getElementById('dl-confirm-saveto-path');
  const confirmGo = document.getElementById('dl-confirm-go');
  const confirmGoLabel = document.getElementById('dl-confirm-go-label');
  const confirmGoSvg = confirmGo ? confirmGo.querySelector('svg') : null;
  const confirmCancel = document.getElementById('dl-confirm-cancel');
  const confirmClose = document.getElementById('dl-confirm-close');
  let lastTrigger = null;

  // Show the typical default download path for this OS (informational only).
  if (confirmSavetoPath) {
    const ua = navigator.userAgent || '';
    if (/Mac/i.test(ua)) confirmSavetoPath.textContent = '~/Downloads';
    else if (/Win/i.test(ua)) confirmSavetoPath.textContent = '%USERPROFILE%\\Downloads';
    else if (/Linux/i.test(ua)) confirmSavetoPath.textContent = '~/Downloads';
  }

  function formatBytes(bytes) {
    if (!bytes || bytes <= 0) return null;
    const units = ['B', 'KB', 'MB', 'GB'];
    let i = 0;
    let n = bytes;
    while (n >= 1024 && i < units.length - 1) {
      n /= 1024;
      i++;
    }
    return `${n.toFixed(n >= 10 || i === 0 ? 0 : 1)} ${units[i]}`;
  }

  function showConfirm(button) {
    if (!confirmEl) return;
    const isUnresolved = button.classList.contains('download-badge--unresolved');
    confirmLabel.textContent = button.dataset.label || button.textContent.trim();
    if (isUnresolved) {
      confirmFilename.hidden = true;
      confirmFilename.textContent = '';
      confirmMessage.hidden = false;
      confirmMessage.textContent = 'No matching asset in the latest release.';
    } else {
      confirmMessage.hidden = true;
      confirmFilename.hidden = false;
      confirmFilename.textContent = button.dataset.resolved || '';
    }
    confirmSize.textContent = (!isUnresolved && button.dataset.size) ? button.dataset.size : '—';
    confirmGo.href = button.href;
    if (confirmGoLabel) {
      confirmGoLabel.textContent = isUnresolved ? 'Open Releases page' : 'Download';
    }
    if (confirmGoSvg) {
      confirmGoSvg.style.display = isUnresolved ? 'none' : '';
    }
    if (!isUnresolved && button.dataset.resolved) {
      confirmGo.setAttribute('download', button.dataset.resolved);
    } else {
      confirmGo.removeAttribute('download');
    }
    lastTrigger = button;
    confirmEl.hidden = false;
    document.body.classList.add('dl-confirm-open');
    requestAnimationFrame(() => {
      if (confirmClose && typeof confirmClose.focus === 'function') confirmClose.focus();
    });
  }

  function hideConfirm() {
    if (!confirmEl || confirmEl.hidden) return;
    confirmEl.hidden = true;
    document.body.classList.remove('dl-confirm-open');
    if (lastTrigger && typeof lastTrigger.focus === 'function') {
      try { lastTrigger.focus(); } catch (_) {}
    }
    lastTrigger = null;
  }

  function attachClickHandlers() {
    buttons.forEach((button) => {
      button.addEventListener('click', (event) => {
        // Always intercept: resolved → confirm filename + size,
        // unresolved → friendly message with "Open Releases page".
        event.preventDefault();
        showConfirm(button);
      });
    });
  }

  if (confirmCancel) {
    confirmCancel.addEventListener('click', hideConfirm);
  }
  if (confirmClose) {
    confirmClose.addEventListener('click', hideConfirm);
  }
  if (confirmGo) {
    // Let the browser proceed with the link, then dismiss the modal
    confirmGo.addEventListener('click', () => setTimeout(hideConfirm, 0));
  }
  // Click on the overlay (outside the modal box) closes the modal
  if (confirmEl) {
    confirmEl.addEventListener('click', (event) => {
      if (event.target === confirmEl) hideConfirm();
    });
  }
  // Escape key closes the modal
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && confirmEl && !confirmEl.hidden) {
      hideConfirm();
    }
  });

  fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then((response) => response.ok ? response.json() : Promise.reject(new Error(`HTTP ${response.status}`)))
    .then((release) => {
      const assets = release.assets || [];
      // Defensive filter: only consider assets whose filename also contains
      // the release tag's version. Without this, a release whose draft was
      // polluted with older-version assets (the way `0.2.0-M4-*.dmg` lived
      // inside the `v0.2.0-M5` draft) would let `find()` return the older
      // file just because it appeared first in upload order.
      const releaseVersion = (release.tag_name || '').replace(/^v/, '');
      buttons.forEach((button) => {
        const pattern = button.dataset.pattern;
        const asset = assets.find((a) =>
          a.name &&
          a.name.endsWith(pattern) &&
          (!releaseVersion || a.name.includes(releaseVersion))
        );
        if (asset) {
          button.href = asset.browser_download_url;
          button.dataset.resolved = asset.name;
          const formatted = formatBytes(asset.size);
          if (formatted) button.dataset.size = formatted;
        } else {
          button.href = FALLBACK;
          button.classList.add('download-badge--unresolved');
        }
      });
      const tag = release.tag_name || release.name;
      if (tag) {
        const note = document.getElementById('dl-resolved');
        if (note) {
          note.textContent = `Latest release: ${tag}`;
          note.hidden = false;
        }
      }
      attachClickHandlers();
      autoOpenFromHash();
    })
    .catch(() => {
      buttons.forEach((button) => {
        button.href = FALLBACK;
        button.classList.add('download-badge--unresolved');
      });
      attachClickHandlers();
      autoOpenFromHash();
    });

  function autoOpenFromHash() {
    const hash = (window.location.hash || '').replace(/^#/, '');
    if (!hash) return;
    const target = buttons.find((b) => b.id === hash);
    if (!target) return;
    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    showConfirm(target);
  }
})();
</script>

Or browse all available assets on the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases). Need [verification info](getting-started.md#verify-your-download)?

### 2. Install and Launch

Install the package the same way you would for a normal desktop application, then launch **Spring AI Playground** from your applications menu.

The desktop app bundles the backend runtime together with a launcher that provides provider starter templates, YAML override editing, environment-variable based secret handling, and one-click launch.

If you install the app, you can run Spring AI Playground immediately without setting up Docker or running the server manually.

> **macOS**
>
> Gatekeeper may block the install flow in two places:
>
> - When you open the downloaded DMG, macOS may show a warning such as “cannot be opened because the developer cannot be verified.” If you trust the release source, go to **System Settings > Privacy & Security** and click **Open Anyway**.
> - After copying the app into **Applications**, macOS may block the first app launch again. If that happens, open the app once, then return to **System Settings > Privacy & Security** and click **Open Anyway**.
>
> If the app still doesn’t open because it remains quarantined, and you trust the app, one practical workaround is:
>
> ```bash
> xattr -dr com.apple.quarantine "/Applications/Spring AI Playground.app"
> ```
>
> **Windows**
>
> The most common warning appears when you run the downloaded installer (`.exe`).
>
> If Microsoft Defender SmartScreen shows a warning such as “Windows protected your PC” or says the app is unrecognized:
>
> - Click **More info**
> - Then click **Run anyway**
>
> **Linux**
>
> Separate Gatekeeper- or SmartScreen-style reputation warnings are uncommon. When installing the `.deb` or `.rpm` package, you usually only need to complete the normal package-install confirmation steps.
>
> For more detailed platform guidance and first-launch configuration screens, see [Getting Started](getting-started.md).

<div style="text-align: center;">
  <b>First-Launch Configuration Screen</b><br/>
  Desktop launcher overview — config editor, Default MCP Tools curation, JVM &amp; environment cards on one screen
</div>

<div style="text-align: center;">
  <a href="assets/images/launcher-openai.png">
    <img src="assets/images/launcher-openai.png" width="760" alt="Spring AI Playground first-launch configuration screen — Spring AI Playground Config + Ollama startup + Default MCP Tools + Environment Variables + JVM Settings"/>
  </a>
</div>

<div style="text-align: center;">
  <b>Ollama Model Manager</b><br/>
  Review recommended models, search exact Ollama names, and manage downloaded models
</div>

<div style="text-align: center;">
  <a href="assets/images/launcher-ollama-config.png">
    <img src="assets/images/launcher-ollama-config.png" width="760" alt="Spring AI Playground Ollama model manager"/>
  </a>
</div>

### 3. Start with the Built-in Desktop Runtime

The desktop build is intended to be the easiest way to get started without setting up Docker or running the server manually.

### 4. Optional: Use Docker Instead

By default the container behaves like the desktop / source app — Vaadin web UI on `http://localhost:8282` and a `streamable-http` MCP server in the same process. To use it as a stdio MCP server for Claude Desktop and other MCP clients instead, add `-e SPRING_PROFILES_INCLUDE=mcp-stdio` (see the [Docker section in Getting Started](getting-started.md#docker)).

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/root \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Then open `http://localhost:8282`.

## :material-view-grid-outline: What You Can Do

- [:material-robot-outline: AI Models](getting-started.md#model-configuration): switch between Ollama, OpenAI, and OpenAI-compatible runtime paths.
- [:material-tools: Tool Studio](features/tool-studio.md): build low-code tools in JavaScript and expose them instantly through MCP.
- [:material-connection: MCP Server](features/mcp-server.md): inspect external MCP servers and consume built-in MCP tools.
- [:material-database-search: RAG](features/vector-database.md): upload content, chunk it, embed it, index it, and validate retrieval quality.
- [:material-chat-processing: Agentic Chat](features/agentic-chat.md): combine grounded context, built-in tools, and explicitly trusted MCP connections in one interaction flow.

## :material-lightbulb-on-outline: Why This Project Exists

Spring AI Playground is intentionally positioned as a tool-first environment for building, testing, validating, and operationalizing MCP tools in a practical workflow.

Its current focus is:

- providing a UI-driven environment for building, testing, and validating MCP tools in a practical workflow
- making test-before-publish the default path for built-in local tool exposure
- testing tool execution flows, environment-backed tool configuration, and RAG integration in one place
- making tools easier to inspect, easier to test, and easier to operationalize before they are reused elsewhere
- supporting practical single-agent workflows through Agentic Chat with tools and grounded context. See [Agentic Chat Architecture Overview](features/agentic-chat.md#agentic-chat-architecture-overview).
- promoting validated built-in tools into reusable MCP-hosted runtimes that can be shared across multiple MCP-compatible hosts and clients

It is intentionally opinionated and scope-limited in its current stage. The goal is a stable, reproducible platform for practical MCP tool work rather than a feature-complete agent orchestration product.

## :material-book-open-page-variant: Documentation Flow

- Getting Started: install the desktop app first, configure models, and understand alternative runtimes
- Architecture: runtime layers, data flows, and extension points (Application + AI Agent Tool Safety)
- Features: the main product areas and what they do
- Tutorials: follow real workflows for tools, MCP, vector search, and agentic chat

## Further Reading

- [Getting Started](getting-started.md): install the desktop app, configure models, and understand alternative runtimes
- [Application Architecture](architecture.md): runtime layers, data flows, and extension points
- [AI Agent Tool Safety Architecture](safety-architecture.md): defense-in-depth sandbox model, policy resolution, threat model, and Risk Level reference
- [Features](features/index.md): the main product areas and what they do
- [Tutorials](tutorials/index.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
