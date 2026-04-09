Description: Spring AI Playground overview covering the desktop app, MCP tool workflow, quick start options, and links to setup, features, and tutorials.

# Spring AI Playground

Spring AI Playground is a cross-platform desktop app for building, testing, and exposing executable MCP tools for MCP-compatible hosts and clients.

The desktop app is the recommended default experience, but Docker and local source execution are still supported when you want a server-style deployment or a development workflow.

Unlike many playgrounds that stop at prompt testing, this project connects AI conversations to real actions:

- build JavaScript tools directly in the app
- expose them immediately through MCP without restart or redeploy
- validate retrieval pipelines against your own documents
- run agentic chat that combines tool use and grounded context

<div style="text-align: center;">
  <b>Agentic Chat Demo</b><br/>
  Tool-enabled agentic AI built with Spring AI and MCP
</div>

<div style="text-align: center;">
  <iframe
    width="800"
    height="450"
    src="https://www.youtube.com/embed/FlzV7TN67f0"
    title="Spring AI Playground Agentic Chat Demo"
    style="border: 0;"
    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
    referrerpolicy="strict-origin-when-cross-origin"
    allowfullscreen>
  </iframe>
</div>

## :material-rocket-launch: Quick Start

The recommended default is the desktop app distributed from GitHub Releases.

### 1. Download the Desktop App

Open the [Spring AI Playground Releases](https://github.com/spring-ai-community/spring-ai-playground/releases) page and download the installer for your OS:

- macOS Apple Silicon: DMG for `arm64`
- macOS Intel: DMG for `x64`
- Windows: NSIS installer
- Linux: DEB or RPM package

### 2. Install and Launch

Install the package the same way you would for a normal desktop application, then launch **Spring AI Playground** from your applications menu.

The desktop app bundles the backend runtime together with a launcher that provides provider starter templates, YAML override editing, environment-variable based secret handling, and one-click launch.

For macOS-specific install notes, Gatekeeper guidance, and quarantine troubleshooting, see [Getting Started](getting-started.md).

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
- [:material-chat-processing: Agentic Chat](features.md#agentic-chat): combine grounded context and tool-enabled reasoning in one interaction flow.

## :material-lightbulb-on-outline: Why This Project Exists

Spring AI Playground is intentionally positioned as a tool-first environment for building, validating, and operationalizing MCP tools in a practical workflow.

Its current focus is:

- providing a UI-driven environment for building, testing, and validating MCP tools in a practical workflow
- testing tool execution flows, environment-backed tool configuration, and RAG integration in one place
- making tools easier to inspect, easier to test, and easier to operationalize before they are reused elsewhere
- promoting validated tools into standalone, deployment-ready MCP servers that can be reused by multiple MCP-compatible hosts and clients

It is intentionally opinionated and scope-limited in its current stage. The goal is a stable, reproducible platform for practical MCP tool work rather than a feature-complete agent orchestration product.

## :material-book-open-page-variant: Documentation Flow

- Getting Started: install the desktop app first, configure models, and understand alternative runtimes
- Features: understand the architecture and the main product areas
- Tutorials: follow real workflows for tools, MCP, vector search, and agentic chat

## Further Reading

- [Getting Started](getting-started.md): install the desktop app, configure models, and understand alternative runtimes
- [Features](features.md): understand the architecture and the main product areas
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
