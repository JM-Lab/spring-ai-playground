Description: Spring AI Playground is a cross-platform desktop app for AI agent tools with a desktop launcher, Tool Studio, MCP, Agentic Chat, RAG, and safe local execution.

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
- start immediately with seven pre-loaded built-in tools (`getWeather`, `sendSlackMessage`, `googlePseSearch`, and more)
- validate retrieval pipelines against your own documents
- run agentic chat that combines tool use and grounded context (e.g. *"Get today's weather and send it to Slack"*)

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

Choose the installer for your platform from the latest release:

[![Windows](https://img.shields.io/badge/Windows-NSIS%20Installer-0078D6?logo=windows&logoColor=white)](getting-started.md#win-x64)
[![macOS Apple Silicon](https://img.shields.io/badge/macOS-Apple%20Silicon%20arm64-000000?logo=apple&logoColor=white)](getting-started.md#mac-arm64)
[![macOS Intel](https://img.shields.io/badge/macOS-Intel%20x64-555555?logo=apple&logoColor=white)](getting-started.md#mac-x64)
[![Linux DEB](https://img.shields.io/badge/Linux-DEB-A81D33?logo=debian&logoColor=white)](getting-started.md#linux-deb)
[![Linux RPM](https://img.shields.io/badge/Linux-RPM-EE0000?logo=redhat&logoColor=white)](getting-started.md#linux-rpm)

Or browse all available assets on the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases).

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
  Desktop launcher overview with the built-in config editor
</div>

<div style="text-align: center;">
  <a href="assets/images/launcher-openai.png">
    <img src="assets/images/launcher-openai.png" width="760" alt="Spring AI Playground first-launch configuration screen"/>
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

If you prefer container-based startup, run:

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/home \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Then open `http://localhost:8282`.

## :material-view-grid-outline: What You Can Do

- [:material-robot-outline: AI Models](getting-started.md#model-configuration): switch between Ollama, OpenAI, and OpenAI-compatible runtime paths.
- [:material-tools: Tool Studio](features.md#tool-studio): build low-code tools in JavaScript and expose them instantly through MCP.
- [:material-connection: MCP Server](features.md#mcp-server): inspect external MCP servers and consume built-in MCP tools.
- [:material-database-search: RAG](features.md#vector-database): upload content, chunk it, embed it, index it, and validate retrieval quality.
- [:material-chat-processing: Agentic Chat](features.md#agentic-chat): combine grounded context, built-in tools, and explicitly trusted MCP connections in one interaction flow.

## :material-lightbulb-on-outline: Why This Project Exists

Spring AI Playground is intentionally positioned as a tool-first environment for building, testing, validating, and operationalizing MCP tools in a practical workflow.

Its current focus is:

- providing a UI-driven environment for building, testing, and validating MCP tools in a practical workflow
- making test-before-publish the default path for built-in local tool exposure
- testing tool execution flows, environment-backed tool configuration, and RAG integration in one place
- making tools easier to inspect, easier to test, and easier to operationalize before they are reused elsewhere
- supporting practical single-agent workflows through Agentic Chat with tools and grounded context. See [Agentic Chat Architecture Overview](features.md#agentic-chat-architecture-overview).
- promoting validated built-in tools into reusable MCP-hosted runtimes that can be shared across multiple MCP-compatible hosts and clients

It is intentionally opinionated and scope-limited in its current stage. The goal is a stable, reproducible platform for practical MCP tool work rather than a feature-complete agent orchestration product.

## :material-book-open-page-variant: Documentation Flow

- Getting Started: install the desktop app first, configure models, and understand alternative runtimes
- Architecture: runtime layers, data flows, and extension points
- Features: the main product areas and what they do
- Tutorials: follow real workflows for tools, MCP, vector search, and agentic chat

## Further Reading

- [Getting Started](getting-started.md): install the desktop app, configure models, and understand alternative runtimes
- [Architecture](architecture.md): runtime layers, data flows, and extension points
- [Features](features.md): the main product areas and what they do
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
