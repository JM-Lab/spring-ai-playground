title: Desktop App
description: Install Spring AI Playground from the desktop installer — platform-specific install notes, configuration walkthrough, MCP tools curation, environment variables and secrets.

# Desktop App

The desktop installer is the recommended default. It wraps the Electron launcher, the JVM, and the Spring Boot fat JAR into a single platform installer (DMG / EXE / DEB / RPM) — no separate Java toolchain, no manual Docker setup. Compared with [Docker or From Source](alternative-runtimes.md), the desktop path adds a built-in configuration editor with provider starter templates, OS-encrypted secret storage for API keys, and an Ollama model manager.

For Docker or source / fat-JAR runs instead of the desktop installer, see [Alternative Runtimes](alternative-runtimes.md). For universal post-install steps (Your First Five Tasks, model configuration, telemetry), see [Getting Started](index.md).

## Download the Desktop Installer

Pick your installer from the [Home page](../index.md#1-download-the-desktop-app). Each badge resolves to the latest published release automatically and opens a confirm dialog with filename, size, and the typical OS save path. If you prefer the raw asset list, browse the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases) directly.

The desktop package wraps the launcher and the backend runtime together, so this is the simplest way to get started without manually running Docker or Maven.

Want to confirm the file you downloaded is genuine? See [Verify Your Download](index.md#verify-your-download) on the hub page.

## Platform-specific install notes

### macOS

If you install from a DMG, drag the app into **Applications** before launching it. Do not run it directly from the mounted DMG, and eject the DMG after copying.

Gatekeeper may block the install flow in two places:

#### 1. Open the installer (DMG)

When you open the downloaded DMG, macOS may show a warning such as "cannot be opened because the developer cannot be verified."

If you trust the release source:

- Try opening the DMG.
- If macOS blocks it, go to **System Settings > Privacy & Security** and click **Open Anyway**.

#### 2. Launch the installed app

After copying `Spring AI Playground.app` into `/Applications`, macOS may block the first app launch again.

If that happens:

- Open the app once.
- Then go to **System Settings > Privacy & Security** and click **Open Anyway**.

If the app still doesn't open because it remains quarantined, and you trust the app, one practical workaround is:

```bash
xattr -dr com.apple.quarantine "/Applications/Spring AI Playground.app"
```

### Windows

The most common warning appears when you run the downloaded installer (`.exe`).

If Microsoft Defender SmartScreen shows a warning such as "Windows protected your PC" or says the app is unrecognized:

- Click **More info**
- Then click **Run anyway**

In most cases, the installer is the main warning point. Repeated blocking after installation is less common than on macOS.

### Linux

Separate Gatekeeper- or SmartScreen-style reputation warnings are uncommon.

Install the package using the format that matches your distribution:

- `.deb` for Debian or Ubuntu-based systems
- `.rpm` for Fedora, RHEL, Rocky Linux, AlmaLinux, openSUSE, and similar RPM-based systems

You usually only need to complete the normal package-install confirmation steps, which may include an administrator password.

## What the Desktop App Gives You

The desktop launcher includes a built-in configuration editor. In practical terms, that means:

- provider-specific starter settings for Ollama, OpenAI, and OpenAI-compatible servers
- YAML override editing instead of forcing you to edit the full bundled config
- environment-variable entry for API keys and tool secrets
- JVM options and application arguments for launch-time tuning
- import, export, save-as, delete, factory reset, and save-and-launch workflows

## How the Desktop Config Works

The launcher does not expose the entire built-in config directly. Instead, the editor shows only the override YAML for the selected setting, and at launch that override is merged on top of the bundled default configuration.

That behavior is reflected in the desktop UI:

- selected config and setup notes
- provider type selector: `Ollama`, `OpenAI`, `OpenAI-Compatible`
- saved setting selection and `Save As`
- environment-variable management
- JVM settings
- `Save and Launch`

This makes it much easier to keep multiple clean launch profiles without hand-managing full runtime configuration files.

## Default MCP Tools Curation

Preset selection for the built-in MCP server happens **inside the configuration editor**. The first launch opens Configure Spring AI Playground (the walkthrough below); pick a preset on the **Default MCP Tools** card before clicking Save and Launch and that choice is written to `<home>/spring-ai-playground/tool/save/default-tools-preference.json`. Without an explicit pick the app falls back to `Starter 5` — the cross-locale defaults that need no API keys.

![Default MCP Tools card on the configuration editor — collapsed view: TOOLS EXPOSED line shows the active preset (Starter 5 · 5 of 86 tools) + per-tool chips, a preset dropdown, and an Advanced curation section with a Show toggle](../assets/images/launcher/launcher-default-tools-card.png)
*The Default MCP Tools card, collapsed — preset chooser with the active preset's tools listed as chips. `Advanced curation` is folded by default.*

The card and Tool Studio's **Tool MCP Server Setting drawer** (toolbox icon, available once the app is running) write to the same `default-tools-preference.json` file. Two surfaces, one source of truth.

The five presets:

- `Starter 5` (default, no API keys) — `getCurrentTime`, `getWeather`, `searchWikipedia`, `extractPageContent`, `evalExpression`.
- `Dev Essentials` — local dev utilities (`uuid`, `hash`, `base64`, `jwtDecode`, `regexExtract`) plus `getCurrentTime` and `evalExpression`.
- `Korea Toolkit (free)` — free Korean services (Upbit, Bithumb, iTunes K-pop, K-beauty search) plus `getCurrentTime` and `evalExpression`.
- `File Toolkit` — filesystem pipeline (`readTextFile`, `listDir`, `grepFile`, `findFiles`, `sliceFile`, `sortFile`, `cutFileFields`) plus `getCurrentTime` and `evalExpression`. Set `TOOL_STUDIO_FS_BASE` to pin a custom workspace root.
- `Everything` — exposes every default tool. Heavy MCP catalog.

The non-Starter presets each carry only `getCurrentTime` and `evalExpression` from Starter 5 by design — they do not stack on top of it.

Clicking **Advanced curation → Show** expands an Include / Exclude pair that mirrors Tool Studio's **Tool MCP Server Setting** drawer: add tools by tag, by category, or by name; remove tools by tag or by name. The chip pickers populate from the live catalog when the section opens (a brief flash of `Loading…` is normal — that's the IPC fetch).

![Default MCP Tools card with Advanced curation expanded — two columns (Include / Exclude) with By tag chips, By category chips, and a By name picker on each side](../assets/images/launcher/launcher-default-tools-card-expanded.png)
*The same card with `Advanced curation` expanded — Include (`+`) on the left adds tools matching any rule; Exclude (`−`) on the right removes them. Rules layer in this order: name-add → tag-add → category-add → name-remove → tag-remove → category-remove.*

For the full preset contents, the CLI override, and the migration note from the prior `defaultToolOverrides.json`, see [Tool Studio → Pre-built Example Tools](../features/tool-studio/index.md#pre-built-example-tools).

## Desktop Configuration Walkthrough

The launcher's **Configure Spring AI Playground** screen opens on the very first launch and any later launch where you re-enter configuration mode. Once you have saved a configuration and chosen a preset, subsequent launches reuse them automatically and skip straight to the app.

### 1. Read the Setup Notes First

The first card is **Current Config and Setup Notes**. It explains the active setting before you edit the YAML below.

![Current config and setup notes](../assets/images/launcher/launcher-setup-notes.png)

- `Selected Config`: the active saved setting name
- `Base Setup`: the launcher hides bundled defaults and only lets you edit overrides
- `How It Runs`: the selected YAML is applied on top of the built-in default configuration at launch

If the selected setting includes an embedding model, the launcher also shows an **Embedding model warning**. That warning matters because changing the embedding model after documents were already indexed can leave existing vector data inconsistent until you re-import or rebuild the vector database.

### 2. Choose a Config Type

The main editor card is **Spring AI Playground Config**.

![Spring AI Playground config card](../assets/images/launcher/launcher-config-card.png)

Within that card, `Config Type` chooses the backend family for the current setting:

- `Ollama`
- `OpenAI`
- `OpenAI-Compatible`

This selector changes the kind of starter setting you are working with. It does not expose the full bundled configuration. It switches the saved-setting list and the override YAML editor to the selected backend family.

### 3. Choose a Saved Setting

??? note "Show details"


    `Setting Name` selects a saved launcher profile for the chosen config type.

    ![OpenAI-Compatible starter profiles](../assets/images/launcher/launcher-openai-compatible-card.png)

    In the current desktop build:

    - `Ollama` starts with the built-in `Ollama` setting
    - `OpenAI` starts with the built-in `OpenAI` setting
    - `OpenAI-Compatible` starts with built-in compatible profiles such as `OpenAI Compatible - Ollama`, `OpenAI Compatible - llama.cpp`, `OpenAI Compatible - TabbyAPI`, `OpenAI Compatible - LM Studio`, and `OpenAI Compatible - vLLM`

    `OpenAI-Compatible` is intended for servers that expose an OpenAI-style API but are not the official OpenAI endpoint.

### 4. Save, Clone, Delete, or Reset Settings

??? note "Show details"


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

??? note "Show details"


    The YAML editor is intentionally scoped to override content, not the full base file. At launch, the selected YAML is merged on top of the bundled default configuration.

    That design keeps the common configuration flow simpler:

    - keep a stable bundled default
    - store only what differs for this setting
    - switch between clean launch profiles quickly

### 6. Understand the Ollama Startup Card

When `Config Type` is set to `Ollama`, the launcher shows an additional **Ollama Startup** section.

![Ollama startup section](../assets/images/launcher/launcher-ollama-startup.png)

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

??? note "Show details"


    When the selected setting or bundled tools need secrets, the launcher shows an **Environment Variables** section. This is where you keep API keys and tool secrets out of YAML.

    ![Environment variables card](../assets/images/launcher/launcher-env-card.png)

    Typical entries include:

    - `OPENAI_API_KEY`
    - `GOOGLE_API_KEY`
    - `GOOGLE_PSE_ID`
    - `SLACK_WEBHOOK_URL`
    - custom variables added with `Add Environment Variable`

    The launcher behavior is important here:

    - values are stored per saved setting
    - values are exported only for the app launch process
    - values are not meant to be written into the YAML override
    - the UI can list both backend-required keys and optional tool-related keys

    The card also shows the current **secret-storage mode**:

    - **Encrypted by your OS secure storage** — Electron's `safeStorage` API is using the platform secure store under the hood: **macOS Keychain**, **Windows DPAPI** (current-user scope), or **libsecret / GNOME Keyring / KWallet** on Linux. The launcher writes the ciphertext to `<userData>/spring-ai-playground/config/secrets.store` on disk; the decryption key never leaves the OS keychain.
    - **OS-backed encryption unavailable — stored as plain text in this session** — fallback when no platform secure store is reachable (typical on bare Linux without a keyring daemon, or in some sandboxed Linux containers). The same file is written in plain JSON so values still survive the launch, but they are no longer encrypted at rest.

    The launcher's secret workflow is the same in both modes:

    - values are stored **per saved setting** (`configId` keyed)
    - values are **exported only as environment variables to the launched Spring AI Playground JVM** — they never get written into the YAML override or into chat history
    - the secrets file is rewritten on every save; a legacy `secrets.json.enc` from older versions is auto-renamed to `secrets.store` on first read

    This is why the env-var pathway is the recommended place for `OPENAI_API_KEY`, `SLACK_WEBHOOK_URL`, `GOOGLE_API_KEY`, and any other tool-side secret — the value reaches `Tool Studio` and the bundled tools through `System.getenv()` rather than through a config file checked into git.

    **The resolved value is also masked from `console.log` output** in Tool Studio's Debug Console and in Agentic Chat's tool-call trace — any tool that references the env var as a static variable (or builds a string containing its resolved value) sees the secret substring replaced before the line surfaces in the UI. See [Tool Studio → Built-in JavaScript Helpers — `console.log`](../features/tool-studio/index.md#built-in-javascript-helpers) for the masking rule details (anchored full-string env-refs are auto-collected; substring-concatenated values are masked best-effort).

    For the current desktop behavior:

    - `OpenAI` requires `OPENAI_API_KEY` before launch
    - `OpenAI-Compatible` can show an API key field, but it is only needed when that compatible server expects one
    - `Ollama` usually does not require an API key for the backend itself, but optional tool integrations may still use environment variables

### 8. Set JVM and App Args Only When Needed

??? note "Show details"


    The desktop editor also includes a **JVM Settings** section for launch-time runtime options.

    ![JVM settings and launch actions](../assets/images/launcher/launcher-jvm-footer.png)

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

??? note "Show details"


    After you click `Save and Launch`, the launcher opens a separate startup window while Spring AI Playground boots in the background.

    ![Spring AI Playground launcher startup screen](../assets/images/launcher-springai.png)

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

![Ollama model manager header](../assets/images/launcher/ollama-manager-top.png)

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

![Ollama model manager recommended tab](../assets/images/launcher/ollama-manager-recommended-wide.png)

That tab shows:

- the embedding model configured for the current profile first
- additional chat models from the active YAML profile
- whether each recommended model is already downloaded
- badges such as `Embedding`, `Chat`, `Default embedding`, and `Current chat model`
- a per-model download button that queues that exact recommended model when it is not already available locally

The `Downloaded` tab focuses on models that already exist in the local Ollama store.

![Ollama model manager downloaded tab](../assets/images/launcher/ollama-manager-downloaded-wide.png)

In that tab, the UI groups downloaded models by type and lets you manage them directly.

- `Copy model as...` duplicates a model under a new Ollama name
- `Delete model` removes the selected model from the local Ollama store

This makes the model manager useful both for first-time setup and for cleaning up or cloning downloaded models later.

## Further Reading

- [Getting Started](index.md) — universal post-install steps, model configuration, telemetry
- [Alternative Runtimes](alternative-runtimes.md) — Docker and source / fat-JAR alternatives
- [Features → Tool Studio](../features/tool-studio/index.md) — author tools that the built-in MCP server exposes
- [Tutorials](../tutorials/index.md) — end-to-end workflows
