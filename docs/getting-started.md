Description: Install Spring AI Playground, use the desktop launcher, configure Ollama or OpenAI, manage Ollama models and secrets, and launch the app, Docker image, or local source build.

# Getting Started

Spring AI Playground is best introduced through the desktop app. The desktop launcher gives you the easiest installation path, a built-in configuration editor, provider starter templates, secure environment-variable handling, and a one-click launch flow for the bundled runtime.

This page starts with the desktop app because that is the default installation experience. Docker and direct source execution are still supported and documented here as alternative runtimes.

## Desktop App First

The recommended default is the desktop build published through GitHub Releases.

### Download the Desktop Installer

Pick the installer for your platform. Each link resolves to the latest published release automatically; the downloaded file keeps the version in its name (e.g. `spring-ai-playground-0.2.0-M4-mac-arm64.dmg`).

<p class="download-badges">
  <a id="win-x64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="win-x64.exe" data-label="Windows (NSIS, x64)" rel="noopener"><img src="https://img.shields.io/badge/Windows-NSIS%20Installer-0078D6?logo=windows&logoColor=white" alt="Windows NSIS Installer"/></a>
  <a id="mac-arm64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="mac-arm64.dmg" data-label="macOS (Apple Silicon)" rel="noopener"><img src="https://img.shields.io/badge/macOS-Apple%20Silicon%20arm64-000000?logo=apple&logoColor=white" alt="macOS Apple Silicon arm64"/></a>
  <a id="mac-x64" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="mac-x64.dmg" data-label="macOS (Intel)" rel="noopener"><img src="https://img.shields.io/badge/macOS-Intel%20x64-555555?logo=apple&logoColor=white" alt="macOS Intel x64"/></a>
  <a id="linux-deb" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="linux-x64.deb" data-label="Linux (DEB, x64)" rel="noopener"><img src="https://img.shields.io/badge/Linux-DEB-A81D33?logo=debian&logoColor=white" alt="Linux DEB"/></a>
  <a id="linux-rpm" class="download-badge" href="https://github.com/spring-ai-community/spring-ai-playground/releases/latest" data-pattern="linux-x64.rpm" data-label="Linux (RPM, x64)" rel="noopener"><img src="https://img.shields.io/badge/Linux-RPM-EE0000?logo=redhat&logoColor=white" alt="Linux RPM"/></a>
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
      buttons.forEach((button) => {
        const pattern = button.dataset.pattern;
        const asset = assets.find((a) => a.name && a.name.endsWith(pattern));
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

Or browse all available assets on the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases).

The desktop package wraps the launcher and the backend runtime together, so this is the simplest way to get started without manually running Docker or Maven.

Want to confirm the file you downloaded is genuine? See [Verify Your Download](#verify-your-download) below.

### Platform-specific install notes

#### macOS

If you install from a DMG, drag the app into **Applications** before launching it. Do not run it directly from the mounted DMG, and eject the DMG after copying.

Gatekeeper may block the install flow in two places:

##### 1. Open the installer (DMG)

When you open the downloaded DMG, macOS may show a warning such as “cannot be opened because the developer cannot be verified.”

If you trust the release source:

- Try opening the DMG.
- If macOS blocks it, go to **System Settings > Privacy & Security** and click **Open Anyway**.

##### 2. Launch the installed app

After copying `Spring AI Playground.app` into `/Applications`, macOS may block the first app launch again.

If that happens:

- Open the app once.
- Then go to **System Settings > Privacy & Security** and click **Open Anyway**.

If the app still doesn’t open because it remains quarantined, and you trust the app, one practical workaround is:

```bash
xattr -dr com.apple.quarantine "/Applications/Spring AI Playground.app"
```

#### Windows

The most common warning appears when you run the downloaded installer (`.exe`).

If Microsoft Defender SmartScreen shows a warning such as “Windows protected your PC” or says the app is unrecognized:

- Click **More info**
- Then click **Run anyway**

In most cases, the installer is the main warning point. Repeated blocking after installation is less common than on macOS.

#### Linux

Separate Gatekeeper- or SmartScreen-style reputation warnings are uncommon.

Install the package using the format that matches your distribution:

- `.deb` for Debian or Ubuntu-based systems
- `.rpm` for Fedora, RHEL, Rocky Linux, AlmaLinux, openSUSE, and similar RPM-based systems

You usually only need to complete the normal package-install confirmation steps, which may include an administrator password.

### What the Desktop App Gives You

The desktop launcher includes a built-in configuration editor. In practical terms, that means:

- provider-specific starter settings for Ollama, OpenAI, and OpenAI-compatible servers
- YAML override editing instead of forcing you to edit the full bundled config
- environment-variable entry for API keys and tool secrets
- JVM options and application arguments for launch-time tuning
- import, export, save-as, delete, factory reset, and save-and-launch workflows

### How the Desktop Config Works

The launcher does not expose the entire built-in config directly. Instead, the editor shows only the override YAML for the selected setting, and at launch that override is merged on top of the bundled default configuration.

That behavior is reflected in the desktop UI:

- selected config and setup notes
- provider type selector: `Ollama`, `OpenAI`, `OpenAI-Compatible`
- saved setting selection and `Save As`
- environment-variable management
- JVM settings
- `Save and Launch`

This makes it much easier to keep multiple clean launch profiles without hand-managing full runtime configuration files.

## Desktop Configuration Walkthrough

The first desktop launch opens **Configure Spring AI Playground**. Later launches reuse the selected saved setting automatically.

### 1. Read the Setup Notes First

The first card is **Current Config and Setup Notes**. It explains the active setting before you edit the YAML below.

![Current config and setup notes](assets/images/launcher/launcher-setup-notes.png)

- `Selected Config`: the active saved setting name
- `Base Setup`: the launcher hides bundled defaults and only lets you edit overrides
- `How It Runs`: the selected YAML is applied on top of the built-in default configuration at launch

If the selected setting includes an embedding model, the launcher also shows an **Embedding model warning**. That warning matters because changing the embedding model after documents were already indexed can leave existing vector data inconsistent until you re-import or rebuild the vector database.

### 2. Choose a Config Type

The main editor card is **Spring AI Playground Config**.

![Spring AI Playground config card](assets/images/launcher/launcher-config-card.png)

Within that card, `Config Type` chooses the backend family for the current setting:

- `Ollama`
- `OpenAI`
- `OpenAI-Compatible`

This selector changes the kind of starter setting you are working with. It does not expose the full bundled configuration. It switches the saved-setting list and the override YAML editor to the selected backend family.

### 3. Choose a Saved Setting

`Setting Name` selects a saved launcher profile for the chosen config type.

![OpenAI-Compatible starter profiles](assets/images/launcher/launcher-openai-compatible-card.png)

In the current desktop build:

- `Ollama` starts with the built-in `Ollama` setting
- `OpenAI` starts with the built-in `OpenAI` setting
- `OpenAI-Compatible` starts with built-in compatible profiles such as `OpenAI Compatible - Ollama`, `OpenAI Compatible - llama.cpp`, `OpenAI Compatible - TabbyAPI`, `OpenAI Compatible - LM Studio`, and `OpenAI Compatible - vLLM`

`OpenAI-Compatible` is intended for servers that expose an OpenAI-style API but are not the official OpenAI endpoint.

### 4. Save, Clone, Delete, or Reset Settings

The launcher lets you manage settings without editing the bundled base configuration directly.

- `Save As` creates a new saved setting from the current YAML and launcher state
- `Delete` removes the current saved setting
- `Export` writes a portable config bundle
- `Import` loads a previously exported bundle
- `Factory Reset` deletes all saved configs, profiles, and stored API keys, then restarts the launcher
- `Save` stores the current launcher state without starting the app
- `Save and Launch` saves first, then starts Spring AI Playground

Config export intentionally leaves out local environment-variable values for safety.

### 5. Edit Only the Override YAML

The YAML editor is intentionally scoped to override content, not the full base file. At launch, the selected YAML is merged on top of the bundled default configuration.

That design keeps the common configuration flow simpler:

- keep a stable bundled default
- store only what differs for this setting
- switch between clean launch profiles quickly

### 6. Understand the Ollama Startup Card

When `Config Type` is set to `Ollama`, the launcher shows an additional **Ollama Startup** section.

![Ollama startup section](assets/images/launcher/launcher-ollama-startup.png)

That section shows:

- the Ollama endpoint, install status, connection status, and detected version
- the configured default chat model and default embedding model
- installed chat and embedding models, with the currently configured defaults highlighted
- whether a configured model appears to be installed, not installed, or unknown because Ollama is unreachable

The action area also includes:

- `Check Connection`
- `Open Ollama Download Page`
- `Download and Manage Ollama Models`: opens the separate [Download and Manage Ollama Models](#11-download-and-manage-ollama-models) guide below
- `Do not check Ollama at startup`

This section is currently shown for the `Ollama` config type. Even if an `OpenAI-Compatible` profile still uses Ollama for embeddings, the dedicated Ollama startup card is not shown automatically in this first-page flow.

### 7. Use Environment Variables for Keys and Secrets

When the selected setting or bundled tools need secrets, the launcher shows an **Environment Variables** section. This is where you keep API keys and tool secrets out of YAML.

![Environment variables card](assets/images/launcher/launcher-env-card.png)

Typical entries include:

- `OPENAI_API_KEY`
- `GOOGLE_API_KEY`
- `PSE_ID`
- `SLACK_WEBHOOK_URL`
- custom variables added with `Add Environment Variable`

The launcher behavior is important here:

- values are stored per saved setting
- values are exported only for the app launch process
- values are not meant to be written into the YAML override
- the UI can list both backend-required keys and optional tool-related keys

The card also shows the current secret-storage mode. When Electron `safeStorage` is available, the launcher stores secrets encrypted at rest and exports them only as environment variables during launch.

For the current desktop behavior:

- `OpenAI` requires `OPENAI_API_KEY` before launch
- `OpenAI-Compatible` can show an API key field, but it is only needed when that compatible server expects one
- `Ollama` usually does not require an API key for the backend itself, but optional tool integrations may still use environment variables

### 8. Set JVM and App Args Only When Needed

The desktop editor also includes a **JVM Settings** section for launch-time runtime options.

![JVM settings and launch actions](assets/images/launcher/launcher-jvm-footer.png)

That section includes:

- JVM options such as `-Xmx2g`
- application args such as `--logging.level.root=INFO`

These are launch-time settings, not provider secrets.

### 9. Recommended First-Launch Flow

For a clean first launch:

1. choose `Ollama`, `OpenAI`, or `OpenAI-Compatible`
2. review the generated YAML override instead of trying to recreate the full application config
3. fill only the environment variables required by that backend or by the tools you actually plan to use
4. for `Ollama`, make sure Ollama is installed, running, and has the models you selected
5. click `Save and Launch`

### 10. What You See After Save and Launch

After you click `Save and Launch`, the launcher opens a separate startup window while Spring AI Playground boots in the background.

![Spring AI Playground launcher startup screen](assets/images/launcher-springai.png)

That startup window shows:

- `Current Config`: the saved setting being launched
- `Config File`: the resolved YAML file path used for this launch
- `Final Launch Command`: the full Java command the launcher built for Spring AI Playground
- `Launch Log`: live startup output, including Ollama checks, config resolution, and server readiness messages

The action row also includes:

- `Back to Settings`: stops the current launch and returns to the configuration screen
- `Auto-copy launch logs to clipboard`: keeps launch logs copied automatically while the app starts
- `Retry Check`: reruns readiness checks if startup is taking longer than expected
- `Quit`: stops the launch and closes the launcher

If startup takes longer than expected, the launcher stays open and keeps streaming logs instead of failing immediately. This is especially helpful when local models are still warming up or downloads are still completing.

### 11. Download and Manage Ollama Models

From the `Ollama Startup` card, `Download and Manage Ollama Models` opens a separate model manager window.

The manager starts with profile context at the top, including the selected config name, Ollama install status, endpoint, and current connection state.

![Ollama model manager header](assets/images/launcher/ollama-manager-top.png)

Below that, the `Download by model name` area is for manual downloads.

- enter an exact Ollama model identifier as `model` or `model:tag`
- use the download button to queue that exact model for download
- use `Find on Ollama` to open the Ollama search page and look up the correct model name first

The recommended flow is:

1. click `Find on Ollama`
2. search the Ollama model page for the model you want
3. copy the exact model name and tag shown there
4. paste it into `Download by model name`
5. click the download button to queue that model for download

The download button next to the input means `Queue download`. It adds the requested model to the manager's download queue rather than changing the current YAML profile by itself.

The default tab is `Recommended`.

![Ollama model manager recommended tab](assets/images/launcher/ollama-manager-recommended-wide.png)

That tab shows:

- the embedding model configured for the current profile first
- additional chat models from the active YAML profile
- whether each recommended model is already downloaded
- badges such as `Embedding`, `Chat`, `Default embedding`, and `Current chat model`
- a per-model download button that queues that exact recommended model when it is not already available locally

The `Downloaded` tab focuses on models that already exist in the local Ollama store.

![Ollama model manager downloaded tab](assets/images/launcher/ollama-manager-downloaded-wide.png)

In that tab, the UI groups downloaded models by type and lets you manage them directly.

- `Copy model as...` duplicates a model under a new Ollama name
- `Delete model` removes the selected model from the local Ollama store

This makes the model manager useful both for first-time setup and for cleaning up or cloning downloaded models later.

## Verify Your Download

Each release ships with two integrity guarantees. Verifying is optional, but recommended for production use.

### 1. SHA-256 checksum

Every installer has a matching `.sha256` file in the release assets. Compare the hash of your downloaded file with the contents of that file.

macOS / Linux:

```bash
shasum -a 256 -c spring-ai-playground-0.2.0-M4-mac-arm64.dmg.sha256
```

Windows (PowerShell):

```powershell
Get-FileHash spring-ai-playground-0.2.0-M4-win-x64.exe -Algorithm SHA256
# then compare the value with the one inside the .sha256 file
```

### 2. Sigstore build provenance (SLSA)

Every installer is signed at build time by the official GitHub Actions release workflow using a short-lived Sigstore key, and the attestation is recorded in the public transparency log. The [GitHub CLI](https://cli.github.com/) can verify the file came from this repo's release workflow:

```bash
gh attestation verify spring-ai-playground-0.2.0-M4-mac-arm64.dmg \
  --owner spring-ai-community
```

A successful verification proves the file was produced by this project's release workflow and was not modified after build.

## Additional Setup for Specific Backends and Runtimes

The desktop app already bundles the launcher and application runtime, so you do not need extra setup just to install and open Spring AI Playground.

The items below apply only when you choose a specific backend or an alternative runtime path.

### If You Use Ollama

Use this setup when you want the default local-first chat and embedding flow.

- Download and install [Ollama](https://ollama.com/) on your machine.
- Run `ollama serve` or ensure the Ollama app is running.
- For prerequisite details, see the [Spring AI Ollama Chat Prerequisites](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_prerequisites).

Recommended models to pre-pull:

```bash
ollama pull qwen3.5
ollama pull qwen3-embedding:0.6b
```

### If You Use OpenAI Instead of Ollama

If you switch to the `OpenAI` setting, Ollama is not required at startup.

In that case, provide `OPENAI_API_KEY` in the desktop app Environment Variables section and launch with the OpenAI setting.

For `OpenAI-Compatible` settings, whether Ollama is still required depends on the selected backend and whether embeddings still use Ollama.

### If You Run the Docker Image

- Install Docker and make sure it is running if you plan to use the container runtime.

### If You Build from Source

- Install Java 21+ and Git.

## Running the Application

### Desktop App

This is the recommended default runtime for most users.

1. Download the installer from GitHub Releases.
2. Install it like a normal desktop application.
3. Choose a launcher setting.
4. Save and launch.

### Docker

Docker is a strong option when you want a server-style deployment instead of the desktop launcher.

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/home \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Notes:

- application data is stored in the `spring-ai-playground` Docker volume
- the container expects to reach Ollama at `http://host.docker.internal:11434`
- on Linux, `host.docker.internal` may not resolve, so you may need host networking or a bridge IP such as `172.17.0.1`
- the `--restart unless-stopped` option keeps the container available after restarts

Docker is not suitable for MCP STDIO transport testing because STDIO-based MCP depends on direct process-to-process communication.

### Local Source Run

Use a local source run when you need development workflows or MCP STDIO transport features.

```bash
git clone https://github.com/spring-ai-community/spring-ai-playground.git
cd spring-ai-playground
./mvnw clean install -Pproduction -DskipTests=true
./mvnw spring-boot:run
```

Then open `http://localhost:8282`.

## PWA Installation

If you are running the browser-based version instead of the desktop installer, Spring AI Playground can also be installed as a Progressive Web App.

Complete either the Docker or local source setup first so the app is already available in the browser.

1. Open the application in your browser at `http://localhost:8282`.
2. Install it using the browser install prompt or the install option shown on the home page.
3. Complete the installation flow to add it as an app-like experience.

## Auto-configuration

Spring AI Playground uses Ollama by default for local chat and embedding models. No API key is required for that default setup, which makes the initial local-first experience straightforward.

## Model Configuration

Spring AI Playground is provider-agnostic, but the runtime defaults are intentionally optimized for a local-first Ollama experience.

### Support for Major AI Model Providers

Spring AI as a framework supports many providers, including Ollama, OpenAI, Anthropic, Microsoft, Amazon, Google, and other integrations.

For the broader list of officially supported chat model integrations, see the [Spring AI Chat Models Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_available_implementations).

Spring AI Playground itself is currently centered on these runtime paths:

- Ollama
- OpenAI
- OpenAI-compatible servers

In the desktop app, OpenAI-compatible support is mainly provided through starter templates and YAML override configuration rather than a larger first-class provider matrix.

If you want to use other Spring AI provider integrations, that is not part of the default desktop app flow. In practice, you would need to modify the source dependencies and configuration, then build and run your own customized version.

### Selecting and Configuring Ollama Models

The default profile is `ollama`, and the default setup uses Ollama for both chat and embeddings.

The current default model choices are:

- chat model: `qwen3.5`
- embedding model: `qwen3-embedding:0.6b`
- selectable chat models: `gpt-oss`, `qwen3.5`, `qwen3`

Important notes:

- missing Ollama models are automatically pulled when needed
- the selectable chat model list controls what appears in the Playground model selector
- changing the chat or embedding model changes the runtime defaults used by the application

### Switching to OpenAI

To switch to OpenAI:

1. use the `OpenAI` setting in the desktop launcher, or activate the `openai` profile in another runtime
2. provide `OPENAI_API_KEY`
3. launch the application with that setting

If you want a broader overview of supported Spring AI provider options beyond the default Playground flows, see the main [Spring AI Documentation](https://spring.io/projects/spring-ai).

Desktop launcher:

- set `OPENAI_API_KEY` in the Environment Variables section
- launch the `OpenAI` setting

Docker:

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_PROFILES_ACTIVE=openai \
  -e OPENAI_API_KEY=your-openai-api-key \
  -v spring-ai-playground:/home \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Unix/macOS source run:

```bash
export OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

Windows source run:

```bash
set OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

### Switching to OpenAI-Compatible Servers

You can also connect to OpenAI-compatible servers such as `llama.cpp`, `TabbyAPI`, `LM Studio`, `vLLM`, `Ollama`, or others that expose OpenAI-compatible endpoints.

Typical configuration points are:

- `api-key`: a real key if the server requires authentication, otherwise a placeholder like `not-used`
- `base-url`: the server root endpoint, often including `/v1`
- `model`: the exact model name registered on that server
- `completions-path`: only override this if the server does not follow the standard OpenAI chat completions path
- `extra-body`: optional provider-specific parameters
- `http-headers`: optional custom authentication or transport headers
- streaming support: works when the target server supports OpenAI-style streaming responses
- token controls: use `maxTokens` for standard models or `maxCompletionTokens` for reasoning-style models, but avoid setting both

Quick example using Ollama in OpenAI-compatible mode:

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:11434/v1"
      chat:
        options:
          model: "llama3.2"
```

`llama.cpp`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:8080/v1"
      chat:
        options:
          model: "your-model-name"
          extra-body:
            top_k: 40
            repetition_penalty: 1.1
```

`TabbyAPI`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "your-tabby-key"
      base-url: "http://localhost:5000/v1"
      chat:
        options:
          model: "your-exllama-model"
          extra-body:
            top_p: 0.95
```

`LM Studio`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:1234/v1"
      chat:
        options:
          model: "your-loaded-model"
          extra-body:
            num_predict: 100
```

`vLLM`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:8000/v1"
      chat:
        options:
          model: "meta-llama/Llama-3-8B-Instruct"
          extra-body:
            top_p: 0.95
            repetition_penalty: 1.1
```

For best compatibility, make sure the target server supports OpenAI-style endpoints and model listing.

In practice, it is worth testing the target with a `/v1/models` request first so you can confirm the exact model names and endpoint shape before launching the app against it.

For the complete Spring AI OpenAI chat configuration model, see the [Spring AI OpenAI Chat Documentation](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).

### Important RAG Note

If you change the embedding model after documents have already been indexed, existing vector data can become inconsistent. Re-import or rebuild the vector database before trusting retrieval results again.

## Built-in MCP Endpoint

No matter whether you run the app through the desktop launcher, Docker, or direct source execution, the built-in MCP endpoint is exposed at:

```text
http://localhost:8282/mcp
```

That endpoint is central to Tool Studio, MCP Inspector, and Agentic Chat with tools.

## Next Step

After the app is running and the model backend is configured:

1. read [Features](features.md) to understand the product structure
2. follow [Tutorials](tutorials.md) to create tools, connect MCP servers, register knowledge, and run Agentic Chat with tools and RAG

## Anonymous Usage Telemetry

The official build sends anonymous usage data (page views, app surface, device/browser
info) to help prioritize features. IPs are anonymized by Google. The same opt-out switch
applies to both the web app and every desktop launcher window (splash, server-splash,
config editor, Ollama manager).

To opt out, set `SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false` before launching:

- **Server / Docker / `mvn`**: export the env var in your shell
- **Desktop launcher**: set it in your OS environment or launcher env config before starting
  the app — the launcher forwards it to every window and to the bundled Spring process
- **From source / IDE**: pass `-Dspring.ai.playground.telemetry.enabled=false` as a JVM arg

For more details, see the [README](https://github.com/spring-ai-community/spring-ai-playground#anonymous-usage-telemetry).

## Your First Five Tasks

Once the app is running, the **Home** screen shows a live checklist that mirrors the path below. Each item self-checks based on workspace state, so you can walk through them at your own pace.

![Getting started checklist on the Home screen](assets/images/home-getting-started.png)

1. **Configure a model provider** — Pick Ollama (default, local) or OpenAI. The provider pill on Home shows a green dot and "Ready" once the base URL is reachable (Ollama) or an API key is set (OpenAI). A red dot means the app cannot reach your provider — recheck the launcher config or env vars.
2. **Start a chat** — Agentic Chat is ready the moment a provider is connected. The app ships with seven built-in tools (`getWeather`, `sendSlackMessage`, `googlePseSearch`, `buildGoogleCalendarCreateLink`, `extractPageContent`, `getCurrentTime`, `openaiResponseGenerator`), so you can test end-to-end without writing any code.
3. **Upload a document for RAG** — Drop a PDF or text file into the Vector Database surface. The file is chunked, embedded, and indexed on the spot; retrieval becomes available inside chat immediately.
4. **Create your first tool** — Open Tool Studio, write a small JavaScript function, and define its sample arguments. Run it locally: if it passes, it earns its **Local Pass** and is added live to the built-in MCP server the same moment. No restart, no redeploy. Agentic Chat picks it up immediately.
5. **Try an agentic workflow** — Ask the assistant: *"Get today's weather and send it to Slack."* This exercises two built-in tools in sequence and shows the full agentic path (plan → call tool → read result → call next tool → reply).

> Verifying your provider: the Home provider pill is the fastest sanity check. If it is stuck on "Checking…" or flips to red, open the desktop launcher startup card or run `curl $OLLAMA_BASE_URL` before proceeding.

## Further Reading

- [Overview](index.md): see the product positioning, quick start path, and documentation map
- [Architecture](architecture.md): runtime layers, data flows, and extension points
- [Features](features.md): the main product areas and what they do
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
