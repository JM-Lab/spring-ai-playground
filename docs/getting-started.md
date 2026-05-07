Description: Install Spring AI Playground, use the desktop launcher, configure Ollama or OpenAI, manage Ollama models and secrets, and launch the app, Docker image, or local source build.

# Getting Started

Spring AI Playground is best introduced through the desktop app. The desktop launcher gives you the easiest installation path, a built-in configuration editor, provider starter templates, secure environment-variable handling, and a one-click launch flow for the bundled runtime.

This page starts with the desktop app because that is the default installation experience. Docker and direct source execution are still supported and documented here as alternative runtimes.

## Desktop App First

The recommended default is the desktop build published through GitHub Releases.

### Download the Desktop Installer

Pick your installer from the [Home page](index.md#1-download-the-desktop-app). Each badge resolves to the latest published release automatically and opens a confirm dialog with filename, size, and the typical OS save path. If you prefer the raw asset list, browse the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases) directly.

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

## Your First Five Tasks

Once the app is running, the **Home** screen shows a live checklist that mirrors the path below. Each item self-checks based on workspace state, so you can walk through them at your own pace.

![Getting started checklist on the Home screen](assets/images/home-getting-started.png)

1. **Configure a model provider** — Pick Ollama (default, local) or OpenAI. The provider pill on Home shows a green dot and "Ready" once the base URL is reachable (Ollama) or an API key is set (OpenAI). A red dot means the app cannot reach your provider — recheck the launcher config or env vars.
2. **Start a chat** — Agentic Chat is ready the moment a provider is connected. The app ships with seven built-in tools (`getWeather`, `sendSlackMessage`, `googlePseSearch`, `buildGoogleCalendarCreateLink`, `extractPageContent`, `getCurrentTime`, `openaiResponseGenerator`), so you can test end-to-end without writing any code.
3. **Upload a document for RAG** — Drop a PDF or text file into the Vector Database surface. The file is chunked, embedded, and indexed on the spot; retrieval becomes available inside chat immediately.
4. **Create your first tool** — Open Tool Studio, write a small JavaScript function, and define its sample arguments. Run it locally: if it passes, it earns its **Local Pass** and is added live to the built-in MCP server the same moment. No restart, no redeploy. Agentic Chat picks it up immediately.
5. **Try an agentic workflow** — Ask the assistant: *"Get today's weather and send it to Slack."* This exercises two built-in tools in sequence and shows the full agentic path (plan → call tool → read result → call next tool → reply).

> Verifying your provider: the Home provider pill is the fastest sanity check. If it is stuck on "Checking…" or flips to red, open the desktop launcher startup card or run `curl $OLLAMA_BASE_URL` before proceeding.

## Built-in MCP Endpoint

No matter whether you run the app through the desktop launcher, Docker, or direct source execution, the built-in MCP endpoint is exposed at:

```text
http://localhost:8282/mcp
```

That endpoint is central to Tool Studio, MCP Inspector, and Agentic Chat with tools.

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
ollama pull gemma4
ollama pull qwen3-embedding:0.6b
```

`qwen3.5` is the recommended chat model for tool-calling and the [Tutorials](tutorials.md). Pull `gemma4` as well if you want a stronger natural-language alternative for longer tool chains.

### If You Use OpenAI Instead of Ollama

If you switch to the `OpenAI` setting, Ollama is not required at startup.

In that case, provide `OPENAI_API_KEY` in the desktop app Environment Variables section and launch with the OpenAI setting.

For `OpenAI-Compatible` settings, whether Ollama is still required depends on the selected backend and whether embeddings still use Ollama.

### If You Run the Docker Image

- Install Docker and make sure it is running if you plan to use the container runtime.

### If You Build from Source

- Install Java 21+ and Git.

### Alternative Runtimes

The desktop app is the default runtime — covered earlier in [Desktop App First](#desktop-app-first). The runtimes below are alternatives for non-desktop environments (server-style deployment, source-level development, browser-only use).

#### How distribution channels map to MCP transports

Every channel below ships the same Spring Boot fat JAR. The default mode is the web app with a `streamable-http` MCP server on port 8282 — switching to a stdio MCP server is opt-in by adding the `mcp-stdio` profile.

| Mode | Profile setup | MCP transport | Channel |
|---|---|---|---|
| **App (web/desktop)** — default | (nothing extra) | `streamable-http` on port 8282 | DMG / EXE / DEB / RPM, web app, Docker, source `mvn spring-boot:run` |
| **MCP server (for Claude Desktop, Claude Code, IDEs, …)** | `SPRING_PROFILES_INCLUDE=mcp-stdio` | `stdio` (process stdin/stdout) | Docker, or `java -jar` against the released JAR |

`SPRING_PROFILES_INCLUDE` (rather than `ACTIVE`) is what lets the stdio profile **layer on top of** the default profile — so model config like Ollama / OpenAI keeps applying. Setting `SPRING_PROFILES_ACTIVE=mcp-stdio` would *replace* the active list and disable the default `ollama` profile, which is rarely what you want.

In other words:

- The **desktop app and PWA** are the primary user-facing experience — full Vaadin UI, tool authoring, agentic chat. The built-in MCP server is reachable over `streamable-http` so the in-app Inspector and other HTTP clients can introspect it.
- The **stdio path** is the same product with the stdio profile layered in for direct MCP client integration. Docker handles it for non-Java users; the raw JAR handles it for Java developers and CI integrations.

The web UI on port 8282 keeps booting in stdio mode too, so a Docker user can both connect Claude Desktop over stdio and open the Inspector in a browser at the same time when they pass `-p 8282:8282`.

#### Docker

The published container behaves like the desktop / source app by default — Vaadin UI on `http://localhost:8282` and the embedded MCP server speaking `streamable-http`. Stdio mode is one env var away (`SPRING_PROFILES_INCLUDE=mcp-stdio`).

##### Use as a plain web app (default)

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/root \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Notes:

- application data is stored in the `spring-ai-playground` Docker volume
- the container expects to reach Ollama at `http://host.docker.internal:11434`
- on Linux, `host.docker.internal` may not resolve, so you may need host networking or a bridge IP such as `172.17.0.1`
- the `--restart unless-stopped` option keeps the container available after restarts

##### Use as an MCP server (opt-in via env)

```bash
docker run -i --rm \
  -e SPRING_PROFILES_INCLUDE=mcp-stdio \
  -v spring-ai-playground:/root \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Add the same command to your MCP client config (Claude Desktop's `claude_desktop_config.json` shown):

```json
{
  "mcpServers": {
    "spring-ai-playground": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "SPRING_PROFILES_INCLUDE=mcp-stdio",
        "-v", "spring-ai-playground:/root",
        "ghcr.io/spring-ai-community/spring-ai-playground:latest"
      ]
    }
  }
}
```

The `-v spring-ai-playground:/root` named volume keeps authored tools, saved tool selections, secrets, and the local vector store across restarts; without it everything resets when the container exits.

Add `-p 8282:8282` if you also want browser access to the Vaadin Inspector alongside the stdio channel — the web UI runs in the same process either way; the port mapping just exposes it. Pick a different host port (e.g. `-p 9000:8282`) if 8282 is in use.

The container ships with the gateway and authoring UI on, but **built-in tools and built-in MCP servers stay dormant until you set their per-tool credentials as environment variables**. Pass them with `-e NAME=value` flags on the same `docker run` line — the typical entries (`OPENAI_API_KEY`, `GOOGLE_API_KEY`, `PSE_ID`, `SLACK_WEBHOOK_URL`, …) are listed in [Use Environment Variables for Keys and Secrets](#7-use-environment-variables-for-keys-and-secrets); each is optional and only enables the matching tool.

The `mcp-stdio` profile silences the CONSOLE log appender so stdout stays a clean JSON-RPC channel; rolling-file logs at `~/spring-ai-playground/logs/` are unaffected.

#### Local Source Run

Use a local source run when you need development workflows or MCP STDIO transport features.

```bash
git clone https://github.com/spring-ai-community/spring-ai-playground.git
cd spring-ai-playground
./mvnw clean install -Pproduction -DskipTests=true
./mvnw spring-boot:run
```

Then open `http://localhost:8282`.

##### Use as an MCP server from the fat JAR

Java developers who already have JDK 21+ on the machine can connect their MCP client straight at the Spring Boot fat JAR — no Docker, no Vaadin dev mode. The same `mcp-stdio` profile that powers the container is portable to any `java -jar` launch.

Pick the JAR up in one of two ways:

- Download `spring-ai-playground-<version>.jar` from the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases) (uploaded alongside the desktop installers; verify with the matching `.sha256` if you want).
- Or build it yourself from a source clone:
  ```bash
  ./mvnw -Pproduction -DskipTests package
  # produces target/spring-ai-playground-*.jar
  ```

Then launch it with the stdio profile included on top of the default:

```bash
SPRING_PROFILES_INCLUDE=mcp-stdio java -jar spring-ai-playground-*.jar
```

`SPRING_PROFILES_INCLUDE` adds `mcp-stdio` to the active profile list without replacing the default `ollama` profile — so model config keeps applying. Use `--spring.profiles.include=mcp-stdio` as a CLI alternative if you'd rather not rely on the env var.

For Claude Desktop, point `claude_desktop_config.json` at the absolute JAR path:

```json
{
  "mcpServers": {
    "spring-ai-playground": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/spring-ai-playground-0.2.0-M5.jar",
        "--spring.profiles.include=mcp-stdio"
      ]
    }
  }
}
```

Notes:

- the JVM must be Java 21 or newer (preferably GraalVM, so the JavaScript tool sandbox runs at full speed; on a non-GraalVM JVM the `-Dpolyglot.engine.WarnInterpreterOnly=false` flag falls back to interpreter mode automatically).
- the same caveats as the container apply: stdout is the MCP JSON-RPC channel, so the `mcp-stdio` profile silences the CONSOLE log appender. Rolling logs still land at `~/spring-ai-playground/logs/`.
- the Vaadin Inspector also boots on `http://localhost:8282` by default, so the same JAR doubles as the authoring UI. Set `SERVER_PORT=0` if you want a random port (or just don't open the browser).

### PWA Installation

If you are running the browser-based version instead of the desktop installer, Spring AI Playground can also be installed as a Progressive Web App.

Complete either the Docker or local source setup first so the app is already available in the browser.

1. Open the application in your browser at `http://localhost:8282`.
2. Install it using the browser install prompt or the install option shown on the home page.
3. Complete the installation flow to add it as an app-like experience.

### Auto-configuration

Spring AI Playground uses Ollama by default for local chat and embedding models. No API key is required for that default setup, which makes the initial local-first experience straightforward.

### Model Configuration

Spring AI Playground is provider-agnostic, but the runtime defaults are intentionally optimized for a local-first Ollama experience.

#### Support for Major AI Model Providers

Spring AI as a framework supports many providers, including Ollama, OpenAI, Anthropic, Microsoft, Amazon, Google, and other integrations.

For the broader list of officially supported chat model integrations, see the [Spring AI Chat Models Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_available_implementations).

Spring AI Playground itself is currently centered on these runtime paths:

- Ollama
- OpenAI
- OpenAI-compatible servers

In the desktop app, OpenAI-compatible support is mainly provided through starter templates and YAML override configuration rather than a larger first-class provider matrix.

If you want to use other Spring AI provider integrations, that is not part of the default desktop app flow. In practice, you would need to modify the source dependencies and configuration, then build and run your own customized version.

#### Selecting and Configuring Ollama Models

The default profile is `ollama`, and the default setup uses Ollama for both chat and embeddings.

The current default model choices are:

- chat model: `qwen3.5`
- embedding model: `qwen3-embedding:0.6b`
- selectable chat models: `qwen3.5`, `qwen3.5:2b`, `gemma4`, `gpt-oss`, `deepseek-r1`

Important notes:

- missing Ollama models are automatically pulled when needed
- the selectable chat model list controls what appears in the Playground model selector
- changing the chat or embedding model changes the runtime defaults used by the application

#### Switching to OpenAI

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
  -v spring-ai-playground:/root \
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

#### Switching to OpenAI-Compatible Servers

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

#### Important RAG Note

If you change the embedding model after documents have already been indexed, existing vector data can become inconsistent. Re-import or rebuild the vector database before trusting retrieval results again.

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

## Further Reading

- [Overview](index.md): see the product positioning, quick start path, and documentation map
- [Architecture](architecture.md): runtime layers, data flows, and extension points
- [Features](features.md): the main product areas and what they do
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
