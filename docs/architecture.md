title: Application Architecture
description: Spring AI Playground architecture — runtime layers, feature modules, data flows, and extension points across Tool Studio, MCP, RAG, and Agentic Chat.

# Application Architecture

Spring AI Playground is a **tool-first Spring Boot application** with several UI surfaces layered on top of a shared runtime. The primary packaged experience is a cross-platform desktop app; Docker and source execution are supported as alternative runtimes.

This page explains how the system is organized, how requests flow through it, and where to extend it. It is intended for contributors, integrators, and anyone evaluating how the product is built under the hood. The sandbox-specific architecture — defense-in-depth model, policy resolution, threat-to-layer mapping, and known limitations — has its own page at [AI Agent Tool Safety Architecture](safety-architecture.md).

## Design Goals

Most playground-style apps stop at prompt entry and response display. Spring AI Playground deliberately goes further:

- **Tools are executable, not descriptive.** Tool definitions run in a sandbox and can be tested before they are published.
- **MCP is a first-class runtime boundary.** Built-in and external tools are consumed through the same Model Context Protocol surface.
- **RAG is inspectable.** Retrieved chunks are visible in Chat before the model uses them.
- **Chat composes capabilities.** Agentic Chat is where tools and grounded context are combined — not where they are invented.
- **Local-first defaults.** No external database or cloud services required to try the product end-to-end.

## Runtime Layers

The application is easiest to think about as five layers. Each layer has a well-defined responsibility and a narrow interface with the one above it.

![Runtime layer diagram — five stacked layers from Electron launcher down to data stores, with feature modules and JS sandbox annotated](assets/images/architecture-layers.svg){ loading=lazy }

### Layer 1 — Desktop Launcher (Electron)

Located in `electron/`. Responsible for packaging, configuration, and process lifecycle — not for any AI behavior.

- `main.js` spawns the bundled Spring Boot JAR, polls for readiness, opens the main `BrowserWindow`, and terminates the JVM on quit.
- `launcher-config.js` holds YAML templates per provider (Ollama, OpenAI, OpenAI-compatible).
- `ollama-manager.js` drives the Ollama model manager window.

The launcher is optional. Running the JAR directly or the Docker image skips this layer entirely.

### Layer 2 — UI Surfaces

Hand-written **Vaadin 24** views under `src/main/java/org/springaicommunity/playground/webui/`. Not Hilla-generated. Each feature area has a root view (`@Route`, `@SpringComponent`, `@UIScope`) and smaller component views composed inside it.

| Package | Root view | Purpose |
|---|---|---|
| `webui/home` | `HomeView` | Landing page with product surfaces |
| `webui/tool` | `ToolStudioView` | JavaScript tool authoring and test runner |
| `webui/mcp` | `McpServerView` | Inspector for built-in and external MCP servers |
| `webui/vectorstore` | `VectorStoreView` | Document upload, chunk inspection, search |
| `webui/chat` | `ChatView` | Agentic Chat with tools and RAG |

Streaming responses (chat, tool execution traces) are pushed to the browser over Vaadin's WebSocket push.

### Layer 3 — Service Layer

Under `src/main/java/org/springaicommunity/playground/service/`. One service per feature area, each owning its persistence and runtime concerns.

| Package | Key services | Owns |
|---|---|---|
| `service/chat` | `ChatService`, `ChatHistoryService` | Chat execution, history, tool/RAG composition |
| `service/tool` | `ToolSpecService`, `ToolCategoryCatalog`, `ChipListBinding`, `DefaultToolPresetCatalog`, `DefaultToolsPreference{Resolver,Service}`, `ToolActivationCalculator`, `McpToolDefinition` + `ToolManifest` envelope | Tool definitions, preset/preference resolution, draft/exposure state |
| `service/tool/runtime` | `JsToolExecutor`, `JsRuntimeGlobals`, `SafeHttpFetch`, `SafeFs`, `JsHelperException` | GraalVM sandbox, `fetch` SSRF guard, `safety.fs`, `safety.parser.*` |
| `service/tool/policy` | `EffectivePolicyResolver`, `SandboxPostureCalculator` | Per-tool capability overrides + risk-level (L0–L5) calculation |
| `service/mcp` | `McpServerInfoService`, `McpToolCallingManager` | Built-in MCP server metadata, tool-call eventing |
| `service/mcp/client` | `McpClientService`, `Mcp*PropertiesService` | External MCP clients across STDIO / HTTP / SSE |
| `service/vectorstore` | `VectorStoreService`, `VectorStoreDocumentService` | Tika ingestion, chunking, embedding, search |

Persistence is pluggable via `PersistenceServiceInterface` and coordinated by `SpringAiPlaygroundPersistenceManager` on startup / shutdown. The default writes JSON files under the user home directory.

### Layer 4 — Spring AI Integration

Thin adapter layer configured in `SpringAiPlaygroundApplication` and related Spring `@Configuration` classes.

- `ChatClient` is built once with an advisor chain: **MessageChatMemoryAdvisor → SpringAiPlaygroundRagAdvisor → SimpleLoggerAdvisor**.
- `ChatMemory` defaults to `MessageWindowChatMemory` (last 10 messages) backed by `InMemoryChatMemoryRepository`.
- `VectorStore` defaults to `SimpleVectorStore` (in-memory). Swap via Spring profile or user configuration.
- `EmbeddingModel` is resolved from the active model profile (Ollama by default, OpenAI optional).
- **Built-in MCP Server** — wired through `spring-ai-starter-mcp-server` and exposes published Tool Studio tools over **Streamable HTTP at `/mcp`**. Runs in-process inside the JVM.
- **MCP Client** — wired through `spring-ai-starter-mcp-client` to connect out to external MCP servers.

### Layer 5 — External Runtimes

Everything outside the JVM:

- **Model providers** — Ollama (local, default), OpenAI, OpenAI-compatible servers (llama.cpp, LM Studio, TabbyAPI, vLLM).
- **Vector databases** (optional) — pgvector, Weaviate, Qdrant, Milvus, and any other Spring AI `VectorStore`. These are opt-in: add the corresponding starter dependency, rebuild, and configure the bean. The default `SimpleVectorStore` is in-process and lives in Layer 4.
- **External MCP servers** — connect through STDIO (spawned process), Streamable HTTP, or legacy SSE.
- **Document readers** — Apache Tika handles PDF, DOCX, HTML, and others.

## Feature Modules

```mermaid
flowchart LR
    TS[Tool Studio]
    MCPV[MCP Server]
    BUILTIN[Built-in MCP Server]
    EXT[External MCP Servers]
    VDB[Vector Database]
    CHAT[Agentic Chat]

    TS -- "publishes tools" --> BUILTIN
    MCPV -. "inspects · tests" .-> BUILTIN
    MCPV -. "registers · tests" .-> EXT
    BUILTIN -- "exposes tools" --> CHAT
    EXT -- "exposes tools" --> CHAT
    VDB -- "retrieves grounded context" --> CHAT
```

The four main surfaces are **connected parts of one workflow**, not isolated demos:

- **Tool Studio** creates and publishes tools into the **Built-in MCP Server**.
- **MCP Server** is the validation boundary — register, inspect, and test external MCP connections before trusting them; the Built-in MCP Server is included there by default.
- **Vector Database** prepares indexed knowledge for retrieval.
- **Agentic Chat** composes tools (from the Built-in MCP Server or External MCP Servers) and retrieved documents into one conversational runtime.

## Key Data Flows

### Flow 1 — Tool authoring and publication

A tool defined in Tool Studio is a `FunctionToolCallback` whose executor delegates to the GraalVM JavaScript sandbox. Publishing registers the callback with the built-in `McpSyncServer` so external MCP clients (Claude Desktop, Claude Code, etc.) can call it.

```mermaid
flowchart TB
    UI["ToolStudioView<br/>(code · params · static vars)"]
    SVC["ToolSpecService.update()"]
    CB["FunctionToolCallback(name, executor)"]
    EXE["JsToolExecutor.execute()"]
    POLY["GraalVM Polyglot Context<br/>Host allowlist · IOAccess<br/>Statement limit · timeout"]
    MCPSRV["McpSyncServer.addTool()"]
    MCPEP["Built-in MCP @ /mcp<br/>(Streamable HTTP)"]

    UI --> SVC
    SVC --> CB
    CB -. "test run" .-> EXE
    EXE --> POLY
    SVC -- "publish" --> MCPSRV
    MCPSRV --> MCPEP
```

Sandbox policy is configurable under `spring.ai.playground.tool-studio.js-sandbox`. The defaults are deny-first: raw network I/O, file I/O, native access, and thread creation are all blocked at the Java level. A `deny-classes` list (System, Runtime, Process, Class, reflect, invoke, Thread, ClassLoader, ServiceLoader, spi) is evaluated before any allow-class match, so deny always wins. The allow-classes are limited to pure-compute packages (`java.lang/math/time/util/text.*`). Tools talk to the outside world through built-in helpers — `fetch` (four-layer SSRF guard, `strict` egress), `safety.fs` (rooted at `tool-studio.fs.base-path`), and `safety.parser.{html,yaml,csv,xml}` — and a tool that genuinely needs more opens specific capabilities through per-tool overrides on its `SandboxOverrides` block (`addAllowClasses`, `hostsAllow`, `networkMode`, `fileRead`/`fileWrite`, `fsBasePath`), which raise its visible risk level (L0–L5) computed by `SandboxPostureCalculator`. See [Tool Studio → Sandbox & Capabilities](features/tool-studio.md#sandbox-capabilities) for the full override shape, egress mode behavior, and risk-level rules.

Publishing has two states. A new or unverified tool is a **Draft** — it lives in Tool Studio and is **not** registered with the built-in MCP server. A Local Pass (a successful test run with the declared test values) flips the `McpToolDefinition` exposure flag and `ToolActivationCalculator` registers the callback with `McpSyncServer`. Which Local-Passed tools ship to MCP on boot is decided by `DefaultToolPresetCatalog` + `DefaultToolsPreferenceResolver` (configurable through Tool Studio's Tool MCP Server Setting drawer, the launcher's Default MCP Tools card, or a CLI override).

### Flow 2 — External MCP server connection

`McpClientService` is transport-agnostic. A dedicated `Mcp*PropertiesService` knows how to build a transport from the connection JSON for each `McpTransportType`.

```mermaid
flowchart TB
    FORM["McpServerConnectionView<br/>(transport + connection JSON)"]
    START["McpClientService.startMcpClient()"]
    MAP{{"McpTransportType"}}
    STDIO["StdioClientPropertiesService<br/>(spawn process)"]
    HTTP["StreamableHttpClient<br/>PropertiesService<br/>(HTTP transport)"]
    SSE["SseClientPropertiesService<br/>(SSE transport)"]
    INIT["McpClient.sync() / async()<br/>→ initialize() handshake"]
    REG["connectingMcpClientOpsMap<br/>(serverInfo → McpClientOps)"]
    INSP["McpServerInspectorView<br/>(list tools · test execution)"]

    FORM --> START
    START --> MAP
    MAP --> STDIO & HTTP & SSE
    STDIO & HTTP & SSE --> INIT
    INIT --> REG
    REG --> INSP
```

Once registered, the same connection becomes available as a tool source in Agentic Chat.

### Flow 3 — Document ingestion and RAG

```mermaid
flowchart LR
    UP["Upload<br/>(PDF · DOCX · HTML · ...)"]
    TIKA["TikaDocumentReader"]
    SPLIT["TokenTextSplitter<br/>(chunk=800, min=350)"]
    TAG["Tag with docInfoId"]
    EMBED["EmbeddingModel.embed()"]
    STORE["VectorStore.add()"]
    DI["VectorStoreDocumentInfo<br/>(metadata · lazy supplier)"]

    UP --> TIKA --> SPLIT --> TAG --> EMBED --> STORE
    STORE --> DI
```

Searches go through `VectorStoreService.search(query, filterExpression)` which builds a `SearchRequest` with similarity threshold `0.6` and top-K `10` by default. The `docInfoId` metadata makes it possible to scope retrieval to specific documents in Chat.

### Flow 4 — Chat advisor chain (memory + RAG)

Every chat request passes through the `ChatClient` advisor chain before it reaches the model. The chain is built once with three default advisors — **MessageChatMemoryAdvisor → SpringAiPlaygroundRagAdvisor → SimpleLoggerAdvisor** — and runs in order for every call.

```mermaid
sequenceDiagram
    autonumber
    participant CS as ChatService
    participant CCL as ChatClient
    participant MEM as MessageChatMemoryAdvisor
    participant CMEM as ChatMemory<br/>(MessageWindow, last 10)
    participant RAG as SpringAiPlaygroundRagAdvisor
    participant RAA as RetrievalAugmentationAdvisor<br/>(built per-request)
    participant VSS as VectorStoreService
    participant LOG as SimpleLoggerAdvisor
    participant MODEL as ChatModel

    CS->>CCL: prompt().user(..).advisors(conversationId, ragFilter)
    CCL->>MEM: before(request)
    MEM->>CMEM: read prior messages
    CMEM-->>MEM: last-N window
    MEM-->>CCL: request + attached history
    alt ragFilterExpression present
        CCL->>RAG: before(request)
        RAG->>RAA: build with filter-bound retriever
        RAA->>VSS: search(query, filter)
        VSS-->>RAA: documents (threshold 0.6, top-K 10)
        RAA-->>RAG: DOCUMENT_CONTEXT populated
        RAG-->>CCL: request + grounded context
    end
    CCL->>LOG: before(request)
    LOG-->>CCL: (logged)
    CCL->>MODEL: send prompt
```

RAG only runs when the user selected at least one document — otherwise `SpringAiPlaygroundRagAdvisor` short-circuits and the chain moves on. Retrieved documents are carried in the request's `DOCUMENT_CONTEXT` so the UI can render them alongside the final answer.

### Flow 5 — Chat with MCP tools

Tool callbacks come from MCP clients, not from code you compile in. When a user picks one or more MCP servers in Chat, `McpClientService` hands back a `ToolCallbackProvider` for each live connection (built-in or external). The model sees their tools as ordinary function tools; `McpToolCallingManager` intercepts every call so the UI can show it.

```mermaid
sequenceDiagram
    autonumber
    participant CCV as ChatContentView
    participant MCS as McpClientService
    participant PROV as Sync · Async<br/>ToolCallbackProvider
    participant CCL as ChatClient
    participant MODEL as ChatModel
    participant TCM as McpToolCallingManager
    participant CB as Sync · Async<br/>McpToolCallback
    participant MCP as MCP Server<br/>(built-in · STDIO · HTTP · SSE)
    participant UI as UI stream

    CCV->>MCS: buildToolCallbackProviders(selected servers)
    MCS-->>CCV: ToolCallbackProvider per server
    CCV->>PROV: getToolCallbacks()
    PROV-->>CCV: ToolCallback list
    CCV->>CCL: .toolCallbacks(callbacks) + toolContext(MCP_PROCESS_MESSAGE_CONSUMER)
    CCL->>MODEL: prompt with tool definitions
    MODEL-->>CCL: assistant message with tool_calls
    CCL->>TCM: executeToolCalls(prompt, response)
    TCM-->>UI: push user / tool-call events
    TCM->>CB: invoke callback
    CB->>MCP: callTool(name, args) over transport
    MCP-->>CB: CallToolResult
    CB-->>TCM: tool response message
    TCM-->>UI: push tool-result event
    TCM-->>CCL: conversation with tool output appended
    CCL->>MODEL: follow-up request
    MODEL-->>CCL: final assistant text
```

The same path handles the Built-in MCP Server (loopback Streamable HTTP at `/mcp`) and external servers (STDIO, Streamable HTTP, SSE) — only the transport differs.

### Flow 6 — Agentic Chat

Agentic Chat is the compose step: it drives Flow 4 and Flow 5 in one streaming request, letting the model decide how many tool-call rounds to run before it produces the final answer.

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant CCV as ChatContentView
    participant CS as ChatService
    participant CCL as ChatClient
    participant ADV as Advisor chain<br/>(Flow 4)
    participant VSS as VectorStoreService
    participant MODEL as ChatModel
    participant TCM as McpToolCallingManager<br/>(Flow 5)
    participant MCP as MCP Server(s)
    participant UI as UI stream

    U->>CCV: prompt + selected docs + selected MCP servers
    CCV->>CS: stream(prompt, filter, toolCallbacks, consumers)
    CS->>CCL: prompt().user(..).toolCallbacks(..).advisors(..)
    CCL->>ADV: before(request)
    ADV->>VSS: RAG search (if filter present)
    VSS-->>ADV: grounded documents
    ADV-->>CCL: request with memory + documents
    loop until model stops calling tools
        CCL->>MODEL: streaming request (tools attached)
        MODEL-->>UI: thinking · partial text
        MODEL-->>CCL: tool_calls (if any)
        CCL->>TCM: executeToolCalls()
        TCM->>MCP: callTool over transport
        MCP-->>TCM: CallToolResult
        TCM-->>UI: tool call · result events
        TCM-->>CCL: conversation with tool output
    end
    MODEL-->>UI: final answer stream
    UI-->>CCV: render (retrieved docs · tool calls · results · thinking · answer)
```

Retrieved documents, every tool call, every tool result, and any reasoning trace are all surfaced in the UI — the agent's path from question to answer is explicit rather than hidden.

## Sandbox Safety

Tool Studio is the only part of the system that runs user-authored code. The implementation models safety as three independent layers — an always-on Java-level sandbox, a per-tool override surface with a visible risk badge, and a transport-level security layer in front of the MCP endpoint.

For the system-level reference (three-layer diagram, policy resolution, per-execution enforcement, threat-to-layer mapping, known limitations, and the next-pass HITL design), see → [**AI Agent Tool Safety Architecture**](safety-architecture.md).

For the user-facing surface (override fields, Risk Level rules, SSRF four-layer steps), see → [Tool Studio → Safety](features/tool-studio.md#safety) and [Tool Studio → Sandbox & Capabilities](features/tool-studio.md#sandbox-capabilities).

## Configuration and Profiles

| Location | Purpose |
|---|---|
| `src/main/resources/application.yaml` | Base defaults, profile declarations |
| `src/main/resources/default-tool-specs.json` | Built-in tools shipped with the app |
| `electron/resources/default-application.yaml` | Desktop launcher's default config template |
| `electron/launcher-config.js` | Provider starter templates (Ollama, OpenAI, OpenAI-compatible) |

Runtime selection happens through Spring profiles (`ollama`, `openai`) combined with user configuration written by the launcher. The same JAR can target any supported provider — no rebuild required.

## Persistence

`SpringAiPlaygroundPersistenceManager` hooks into Spring's lifecycle and delegates to per-feature persistence services:

- `ChatHistoryPersistenceService` — conversation metadata and messages
- `ToolSpecPersistenceService` — authored tools
- `VectorStoreDocumentPersistenceService` — uploaded documents and metadata
- `McpServerInfoPersistenceService` — saved external MCP connections

State is serialized as JSON under the user home directory. `SimpleVectorStore` itself is volatile — vectors are recomputed on restart when the default store is in use. Swapping in a durable vector store (pgvector, Weaviate) removes that constraint.

## Extensibility Points

| Extension | Where |
|---|---|
| New model provider | Spring profile + `application.yaml` + launcher template |
| New vector store | Standard Spring AI `VectorStore` bean override |
| New MCP transport | Add an `McpClientPropertiesService<T>` implementation and register it against `McpTransportType` |
| New tool | Tool Studio (runtime, no rebuild) or a Spring bean exposing a `ToolCallback` |
| Custom advisor | Register an additional `Advisor` bean; picked up by `ChatClient` builder |
| Custom persistence | Implement `PersistenceServiceInterface` |

## Why This Shape

The five-layer model is deliberate. Each capability has a dedicated runtime area, but the user-facing flows **compose** those capabilities rather than hiding them behind a single opaque screen. That is what makes the app useful as a validation environment: every boundary — sandbox, MCP transport, retrieval, tool execution — is visible and testable in isolation before it is combined in Chat.

## Further Reading

- [Overview](index.md) — product positioning, quick start path, and documentation map
- [Getting Started](getting-started.md) — install, configure, and run the app
- [Features](features/index.md) — what each product area does and how to use it
- [Tutorials](tutorials/index.md) — end-to-end walkthroughs that exercise these flows
