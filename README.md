> [!NOTE]
> **Official repository**
>
> The official Spring AI Playground repository has moved to:
> https://github.com/spring-ai-community/spring-ai-playground
>
> This repository is kept for personal experiments / notes and may diverge from the official version.
> For issues and pull requests, please use the official repository.

---

# Spring AI Playground

**Safe Local Execution Layer for AI Agent Tools**

Spring AI Playground is a cross-platform desktop app for building, testing, validating, and executing MCP tools in a controlled local environment. It helps you create reusable MCP tools once and use them across macOS, Windows, and Linux through a self-contained runtime. Unlike platforms that focus primarily on generating agents or authoring tools, Spring AI Playground focuses on making the tools it manages inside the app safer and easier to inspect before reuse.

> **No pass, no run.**

Every tool you build earns a **Local Pass** — a local test-run with your sample arguments. Only passing tools are added live to the built-in MCP server and become callable from Agentic Chat. A tool that has not passed is never exposed to an agent.

In Tool Studio, new or updated built-in tools are test-run before they are published to the built-in MCP server. You do not need to know Java, Spring, or JVM internals to use it. If you can install a desktop app and write a small JavaScript function, you can build tools here and connect them to hosts and clients such as Claude Desktop, Claude Code, Cursor, IDEs, and other MCP-compatible environments.

## The Problem

AI agents can generate tools quickly, but generated tools are not inherently safe to execute.

- It is often unclear what actually runs at execution time
- Failures are difficult to predict before real usage
- Execution is not easily traceable or inspectable

Most platforms focus on creation.

Very few make verification part of the default workflow for built-in tool publication.

## Who is this for?

- Developers building MCP tools who want validation built into the default workflow
- Teams connecting MCP tools into Python, Node.js, or mixed-stack agent environments
- Users of Claude Desktop, Claude Code, Cursor, and other MCP-compatible environments

## Quick Start

The fastest path is the desktop app distributed through GitHub Releases.

Spring AI Playground is a standalone desktop app, so you can install it and start building MCP tools without setting up a Java project, Docker environment, or source build first.

### 1. Download the Desktop App

Choose the installer for your platform from the latest release:

[![Windows](https://img.shields.io/badge/Windows-NSIS%20Installer-0078D6?logo=windows&logoColor=white)](https://spring-ai-community.github.io/spring-ai-playground/#win-x64)
[![macOS Apple Silicon](https://img.shields.io/badge/macOS-Apple%20Silicon%20arm64-000000?logo=apple&logoColor=white)](https://spring-ai-community.github.io/spring-ai-playground/#mac-arm64)
[![macOS Intel](https://img.shields.io/badge/macOS-Intel%20x64-555555?logo=apple&logoColor=white)](https://spring-ai-community.github.io/spring-ai-playground/#mac-x64)
[![Linux DEB](https://img.shields.io/badge/Linux-DEB-A81D33?logo=debian&logoColor=white)](https://spring-ai-community.github.io/spring-ai-playground/#linux-deb)
[![Linux RPM](https://img.shields.io/badge/Linux-RPM-EE0000?logo=redhat&logoColor=white)](https://spring-ai-community.github.io/spring-ai-playground/#linux-rpm)

Each badge resolves to the latest published release automatically and opens a confirm dialog with the filename, size, and OS-specific default save path. The downloaded file keeps the version in its name (e.g. `spring-ai-playground-0.2.0-M4-mac-arm64.dmg`). Or browse all available assets on the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases).

### 2. Install and Launch

Install the app like a normal desktop application, then launch **Spring AI Playground** from your applications menu.

The desktop app bundles the backend runtime together with a launcher that provides provider starter templates, YAML override editing, environment-variable based secret handling, and one-click launch.

If you install the app, you can run Spring AI Playground immediately without setting up Docker or running the source manually.

> **macOS**
>
> Gatekeeper may block the install flow in two places:
>
> - When you open the downloaded DMG, macOS may show a warning such as “cannot be opened because the developer cannot be verified.” If you trust the release source, go to **System Settings > Privacy & Security** and click **Open Anyway**.
> - After copying the app into **Applications**, macOS may block the first app launch again. If that happens, open the app once, then return to **System Settings > Privacy & Security** and click **Open Anyway**.
>
> If the app still doesn’t open because it remains quarantined, and you trust the app, one practical workaround is:
>
> ```
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
> For more detailed platform guidance, see the [Getting Started guide](https://spring-ai-community.github.io/spring-ai-playground/getting-started/).

### Verify Your Download

Each release ships with two integrity guarantees. You do not have to verify, but it is recommended for production use.

**1. SHA-256 checksum** — every installer has a matching `.sha256` file in the release assets.

```bash
# macOS / Linux
shasum -a 256 -c spring-ai-playground-0.2.0-M4-mac-arm64.dmg.sha256

# Windows (PowerShell)
Get-FileHash spring-ai-playground-0.2.0-M4-win-x64.exe -Algorithm SHA256
# compare the value with the one inside the .sha256 file
```

**2. Sigstore build provenance (SLSA)** — every installer is signed by the official GitHub Actions release workflow using a short-lived Sigstore key, and the attestation is recorded in the public transparency log.

```bash
gh attestation verify spring-ai-playground-0.2.0-M4-mac-arm64.dmg \
  --owner spring-ai-community
```

A successful verification proves the file came from this repo's release workflow and was not tampered with after build.

<p align="center">
  <b>First-Launch Configuration Screen</b><br/>
  Desktop launcher overview with the built-in config editor
</p>

<p align="center">
  <a href="docs/assets/images/launcher-openai.png">
    <img src="docs/assets/images/launcher-openai.png" width="760" alt="Spring AI Playground first-launch configuration screen"/>
  </a>
</p>

<p align="center">
  <b>Ollama Model Manager</b><br/>
  Review recommended models, search exact Ollama names, and manage downloaded models
</p>

<p align="center">
  <a href="docs/assets/images/launcher-ollama-config.png">
    <img src="docs/assets/images/launcher-ollama-config.png" width="760" alt="Spring AI Playground Ollama model manager"/>
  </a>
</p>

## Documentation

Detailed installation, configuration, features, and tutorials live in the documentation site:

- Documentation site: https://spring-ai-community.github.io/spring-ai-playground/
- Getting Started: https://spring-ai-community.github.io/spring-ai-playground/getting-started/
- Features: https://spring-ai-community.github.io/spring-ai-playground/features/
- Tutorials: https://spring-ai-community.github.io/spring-ai-playground/tutorials/

Alternative runtimes are still supported:

- Docker for server-style deployment
- local source execution for development workflows and MCP STDIO testing

<p align="center">
  <b>Agentic Chat Demo</b><br/>
  Tool-enabled agentic AI built with Spring AI and MCP
</p>

<p align="center">
  <a href="docs/assets/images/agentic-chat-demo.gif">
    <img src="docs/assets/images/agentic-chat-demo.gif" width="800" alt="Spring AI Playground Agentic Chat Demo"/>
  </a>
</p>

## Why Spring AI Playground?

- **Built-In MCP Server**: Publish tools directly from the app and expose them immediately through the built-in MCP server instead of wiring ad-hoc local scripts by hand.
- **No Pass, No Run Workflow**: In Tool Studio, built-in tools are test-run before they are published, making validation part of the default product flow instead of an optional afterthought.
- **Executable Tool Validation**: Test tools with real inputs, outputs, and runtime constraints before you reuse them from other MCP-compatible hosts and clients.
- **Secure Secret Management**: Keep API keys and sensitive configuration out of YAML and manage them through the desktop app's secret storage and launcher-backed environment settings. When OS-backed secure storage is unavailable, the app clearly warns before falling back to plain-text local storage.
- **Tool-to-Agent Workflow**: Create tools in Tool Studio, inspect them through MCP, and use them in Agentic Chat in one continuous workflow.
- **Provider Agnostic**: Switch between Ollama, OpenAI, and other OpenAI-compatible APIs without changing the overall workflow.
- **OS-Independent Tool Runtime**: Tools are authored once as JavaScript and run through the same bundled runtime, so the same tool definition works consistently across macOS, Windows, and Linux.
- **Single-Agent Execution**: Use validated built-in tools together with grounded context (RAG) in Agentic Chat to handle focused, practical workflows without needing a larger orchestration layer. Agentic Chat can also call tools exposed by MCP servers that you explicitly connect and trust.

The intended workflow is practical and composable:

- create or adapt tools in Tool Studio
- test them before publishing
- expose them through the built-in MCP server
- inspect them through MCP Inspector
- index knowledge in Vector Database
- combine tools and documents in Agentic Chat

## Why Not Just Use Agent Builders?

Agent builders focus on generating tools and composing workflows.

Spring AI Playground focuses on validating tools and controlling execution.

It complements agent builders by providing a reliable execution layer.

## Project Scope & Positioning

Spring AI Playground is a **tool-first environment** for building, testing, validating, and operationalizing MCP tools in a practical workflow.

It is best understood as a **safe local execution layer for AI agent tools**.

> **Note:** This project is intentionally focused in its current stage.  
> The goal is to make MCP tool building, validation, inspection, and runtime exposure simple and reliable, so the tools you create here can be reused from MCP-compatible hosts and clients such as Claude Code, Claude Desktop, IDEs, and other agent environments.

Current focus:

- providing a UI-driven environment for building, testing, and validating MCP tools in a practical workflow
- making test-before-publish the default path for built-in local tool exposure
- testing tool execution flows, environment-backed tool configuration, and RAG integration in one place
- making tools easier to inspect, easier to test, and easier to operationalize before they are reused elsewhere
- supporting practical single-agent workflows through Agentic Chat with tools and grounded context. See [Agentic Chat Architecture Overview](https://spring-ai-community.github.io/spring-ai-playground/features/#agentic-chat-architecture-overview).
- promoting validated built-in tools into reusable MCP-hosted runtimes that can be shared across multiple MCP-compatible hosts and clients

It is not trying to replace the tools where agents actually run. It is designed to give you a clearer path from local tool prototype to inspectable, reusable MCP server.

## Contributing & Scope

Please read this section before opening issues or submitting contributions.

### Current Scope

- bug reports with reproducible steps
- targeted documentation fixes
- usage examples

### Out of Scope For Now

- feature requests and code contributions — the project is currently in a maintainer-driven phase with limited bandwidth. We are not accepting code PRs or feature proposals from outside contributors at this time. Bug reports and documentation fixes are very welcome.
- experimental model integrations outside the current supported provider list (currently: Ollama, OpenAI, and OpenAI-compatible APIs)
- high-level multi-agent orchestration layers
- platform-level marketplace or governance features

### Reporting Issues

Pick the right channel:

- **Bug Report** issue template — for reproducible app failures
- **Documentation Issue** template — for docs errors you can't fix via PR
- documentation PR — for targeted fixes in `docs/` (typos, errors, broken links); open a Discussion first for README changes or new sections
- **GitHub Discussions** — Q&A for questions, Show and tell for examples; not Issues
- read the project scope above before requesting broader changes

We triage issues regularly, and issues outside the current scope may be closed with guidance.

If you have a contribution that fits the current scope (bug report, doc fix, usage example), submit a PR or a targeted issue. For anything else, please open a Discussion first — but be aware we may not act on it given current capacity.

## Anonymous Usage Telemetry

The official build sends anonymous usage data (page views, app surface, device/browser
info) to the maintainer's Google Tag Manager / Google Analytics account so the most-used
features can be prioritized. IPs are anonymized by Google. The same opt-out switch applies
to both the web app and every desktop launcher window (splash, server-splash, config
editor, Ollama manager):

- **Server / Docker / `mvn`**: `SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false`
- **Desktop launcher**: set `SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false` before launching
  the app (the launcher forwards this env var to every window and to the bundled Spring
  process)
- **From source / IDE**: pass `-Dspring.ai.playground.telemetry.enabled=false` as a JVM arg

If you self-host this project for EU users, adding cookie consent on top is the
operator's responsibility under GDPR.

## Upcoming Improvements

These are the near-term areas we plan to improve while keeping the project focused on practical, reusable tool execution.

### Observability

- **Execution Visibility**: improve tracing and inspection for tool execution, MCP calls, failures, and runtime behavior
- **Operational Insight**: make it easier to understand what ran, why it failed, and how a published tool behaves in practice

### Hardening Existing Capabilities

- **Tool Runtime Improvements**: strengthen the current workflow for building, validating, and publishing tools
- **Secret Handling**: continue improving how tool configuration and environment-backed values are stored, managed, and used at runtime
- **Validation and Reuse**: make validated tools easier to inspect, reuse, and operationalize as MCP-hosted runtimes
- **Agentic Chat Usability**: improve practical workflows that combine tools and grounded context in one focused runtime

### Platform Support

- **Authentication**: improve access control where it fits the current product boundary
- **Multimodal Support**: image and audio input/output with supported multimodal-capable models
