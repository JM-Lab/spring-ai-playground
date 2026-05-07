Description: Explore Spring AI Playground features: Tool Studio, MCP Server, Vector Database, Agentic Chat, and the safe local architecture behind its AI tool workflow.

# Features

This page explains the main product areas in the order they are designed to be understood: Tool Studio, MCP Server, Vector Database, and Agentic Chat.

For a system-level view — runtime layers, data flows, and extension points behind these surfaces — see [Architecture](architecture.md).

## Tool Studio

Tool Studio is the low-code authoring environment for JavaScript-based tools.

![Tool Studio](assets/images/tool-studio.png)

It is the part of the product that turns the Playground from a read-only testing interface into an executable tool runtime.

### What Tool Studio Does

Tool Studio lets you:

- create tools directly in the browser
- define structured input parameters
- define static variables
- test tool execution immediately
- publish tools to the built-in MCP server without restart or redeploy

### Local Pass: Test Before Publish

Spring AI Playground treats the local test-run as a gate, not a polish step. This is the rule surfaced on the Home screen as **No pass, no run.**

- every tool has at least one sample input (the static variables you define) used for its test-run
- the tool must **pass its test locally** before Tool Studio publishes it
- when the test passes, the tool earns a **Local Pass** and is **added live to the built-in MCP server** the same moment — no restart, no redeploy
- a tool that has not passed is **not added to the built-in MCP server** and is **not callable from Agentic Chat**

In practice this means the act of publishing is the act of testing. You never produce a tool whose first execution happens in front of an agent.

### Built-in MCP Server

Tool Studio is tightly integrated with the built-in MCP server.

- endpoint: `http://localhost:8282/mcp`
- protocol: Streamable HTTP
- default server name: `spring-ai-playground-tool-mcp`

When you publish a tool from Tool Studio, it becomes available through that MCP endpoint immediately.

### Security

The built-in MCP server leverages Spring AI's MCP security model through Spring Security, but the default local experience is intentionally simple.

- authentication is disabled by default
- for production-style security, you can apply Spring AI's official MCP security configuration without changing your tool logic
- for setup details, refer to the [Spring AI MCP Security Documentation](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-security.html)

The practical point is that the local Playground is optimized for fast iteration first. When you move toward a stricter deployment model, the MCP exposure boundary does not need to change, but the surrounding security model can.

### Connect to the Built-in MCP Server

Once Spring AI Playground is running, the built-in MCP server can be consumed directly by MCP-compatible clients.

#### Claude Code

Recent Claude Code versions support Streamable HTTP directly.

```bash
claude mcp add spring-ai-playground http://localhost:8282/mcp
```

Restart Claude Code if needed so the new server is picked up.

#### Cursor

Configure a Streamable HTTP server in Cursor with:

- Name: `Spring AI Playground`
- URL: `http://localhost:8282/mcp`

In practice, that means:

1. open Cursor Settings
2. navigate to **Features > MCP**
3. add a new MCP server
4. choose **Streamable HTTP**
5. enter the name and URL above

#### Claude Desktop

If your Claude Desktop plan supports native remote connectors, you can add `http://localhost:8282/mcp` directly from the Settings UI.

For broader compatibility, one practical approach is to use `mcp-remote`:

```json
{
  "mcpServers": {
    "spring-ai-playground": {
      "command": "npx",
      "args": ["-y", "mcp-remote", "http://localhost:8282/mcp"]
    }
  }
}
```

Restart Claude Desktop after saving the config.

This proxy-style setup is especially useful when direct remote configuration is unavailable or inconvenient, because it wraps the remote Streamable HTTP MCP server behind a local process contract that desktop clients already understand well.

### Dynamic Tool Exposure

Tool Studio and the built-in MCP server are intentionally designed for a no-restart workflow:

- create or update a tool
- test it
- publish it
- inspect it through MCP immediately

When a tool is created or updated in Tool Studio, it is dynamically discovered and exposed by the default built-in MCP server. You can then inspect its schema and validate execution behavior from the MCP Server screen without restarting or redeploying the application.

### JavaScript Runtime

Tool actions run as JavaScript through GraalVM Polyglot inside the JVM.

The runtime characteristics are:

- ECMAScript 2023 execution
- controlled Java interop
- sandbox-oriented restrictions
- whitelist-based access to approved Java classes

The default sandbox configuration is intentionally restrictive:

- network I/O allowed
- file I/O blocked
- native access blocked
- thread creation blocked
- allowed classes explicitly listed

Typical sandbox settings look like this:

```yaml
js-sandbox:
  allow-network-io: true
  allow-file-io: false
  allow-native-access: false
  allow-create-thread: false
  max-statements: 500000
  allow-classes:
    - java.lang.*
    - java.math.*
    - java.time.*
    - java.util.*
    - java.text.*
    - java.net.*
    - java.io.*
    - java.net.http.HttpClient
    - java.net.http.HttpRequest
    - java.net.http.HttpResponse
    - java.net.http.HttpHeaders
    - org.jsoup.*
```

That makes Tool Studio suitable for low-code integrations without turning it into an unrestricted scripting surface. If you need a stricter deployment posture, GraalVM sandbox policies can be layered on top in a custom source build. For more background, see the [GraalVM Security Guide](https://www.graalvm.org/latest/security-guide/sandboxing/).

The reason this matters is that Tool Studio is intended for small, deterministic tool actions rather than arbitrary unrestricted scripting. In other words, the runtime is flexible enough for practical HTTP-based tool integrations while still keeping the safety boundary explicit.

Because tool code can call network APIs and depend on environment values, cross-platform reuse is best understood as a portable workflow rather than a guarantee that every tool behaves identically on every OS and runtime environment.

### Key Tool Studio Capabilities

- Tool MCP Server Setting: control which tools are exposed through the built-in MCP server
- Enable Auto-Add Tools: decide whether newly created or updated tools are exposed automatically
- Registered Tools: keep a larger local tool library while exposing only a curated subset
- Tool Specification View: inspect the generated JSON schema, metadata, and parameter contract
- Copy to New Tool: clone an existing tool as a template instead of starting from scratch
- Tool List and Selection: browse existing tools and reload them into the editor
- Tool Metadata: define stable names and agent-friendly descriptions
- Structured Parameters: define required inputs, descriptions, and test values for model-side tool calling
- Static Variables: inject configuration values and environment-backed secrets
- Test Run and Debug Console: validate console output, status, elapsed time, and result before publishing

In the UI, these capabilities show up as the practical authoring workflow:

- expose only the tools you want MCP clients to see
- inspect the generated tool specification before publishing
- copy a working tool into a new template instead of starting from a blank definition
- test with representative values and review the debug output before updating the runtime

That combination is one of the strongest product-specific ideas in the Playground. You can keep many tools in your workspace, expose only a controlled set, validate the exact contract the model will see, and update the runtime without a restart cycle.

### Low-code Tool Development Workflow

1. Open Tool Studio.
2. Define the tool name and description.
3. Add structured parameters with test values.
4. Add static variables if needed.
5. Write the JavaScript action.
6. Run **Test Run** and inspect the debug output.
7. Publish with **Test & Update Tool**.

### Pre-built Example Tools

The app ships with **seven built-in tools** pre-loaded into Tool Studio. They are ready to call from chat the moment a model provider is connected — you do not need to author anything yourself to see agentic workflows work end-to-end. They also serve as editable references when you start writing your own.

- `googlePseSearch`: search the web using Google Programmable Search Engine
- `extractPageContent`: fetch and clean the main text from a web page
- `buildGoogleCalendarCreateLink`: generate a Google Calendar event creation URL
- `sendSlackMessage`: send a message through a Slack webhook
- `openaiResponseGenerator`: call OpenAI and return a generated response
- `getWeather`: fetch a compact weather summary
- `getCurrentTime`: return the current time in ISO format

Some of these depend on environment-backed secrets:

- `googlePseSearch` typically depends on `GOOGLE_API_KEY` and `PSE_ID`
- `sendSlackMessage` depends on `SLACK_WEBHOOK_URL`
- `openaiResponseGenerator` depends on `OPENAI_API_KEY`

That is one of the reasons the desktop launcher’s environment-variable workflow is important.

### Using Tools in Agentic Chat

Tool Studio tools can be used in Agentic Chat through MCP integration. With a tool-capable model and the built-in MCP connection enabled, the model can call those built-in tools during agentic workflows.

Agentic Chat can also call tools exposed by external MCP servers that you explicitly connect and trust.

## MCP Server

The MCP Server area is where you manage and inspect tool connections.

![MCP Server](assets/images/mcp.png)

It serves two roles:

- it helps you validate tools published by Spring AI Playground itself
- it acts as a client-side inspection surface for external MCP servers

That distinction matters for safety expectations: built-in tools follow the Tool Studio authoring and test-run workflow, while external MCP servers are connections that you choose to add and trust.

### Connection Management

The MCP runtime supports multiple transport styles, including:

- Streamable HTTP
- STDIO
- legacy HTTP plus SSE-style setups where needed

This makes the Playground useful both for local tool exposure and for external MCP integration testing.

Streamable HTTP is the modern single-endpoint transport used by the built-in MCP server, while STDIO and legacy HTTP plus SSE remain useful for compatibility and external integrations.

The modern Streamable HTTP transport formalized in the MCP v2025-03-26 specification uses a single MCP endpoint. Clients POST JSON-RPC requests to `/mcp`, responses can stream when supported, and session-oriented behavior can be layered on top by MCP clients and servers.

That modern transport replaces the older split HTTP-plus-SSE mental model with a simpler single endpoint, while still preserving compatibility value for STDIO and older integrations.

#### Live status in the sidebar

Each connection in the left rail carries a colored dot that reflects its last health check: **green** for OK, **gray** for offline (not connected), and **red** for an error returned during initialization or a recent ping. The dot updates as the playground reconnects or re-pings, so a connection that drops mid-session is visible without opening the inspector.

![Sidebar with a connected server showing the OK status dot](assets/images/tutorials/tutorial-2-sidebar-status.png){ width="320" }

*① the green dot next to a server name means the most recent ping succeeded — it goes red if the server returned an error and gray when nothing has connected yet.*

#### Test Connection without disturbing live clients

The config form has a **Test Connection** button next to **Save & Connect**. It spins up a transient sync MCP client, runs `initialize` and a one-shot `listTools`, then disposes the client — without touching the running connection map. Use it to validate a config change against a remote server before saving (which would otherwise replace the live client and might briefly drop tool availability in chat).

#### Custom HTTP headers and `${ENV_VAR}` substitution

HTTP and SSE connections both expose a single **Headers** section in the config form, edited as key/value rows. The same row layout drives the auth-preset dropdown — picking **Authorization (Bearer Token)**, **Authorization (Basic Auth)**, or **API Key Header** inserts a templated row whose value you fill in.

Header values, STDIO `env` values, and any name listed in `requiredEnv` accept `${VAR}` placeholders that resolve from the OS environment at connect time (with a JVM system-property fallback). That keeps secrets out of the saved JSON: the persisted file stores `${OPENAI_API_KEY}` literally, and the actual key only enters memory when the connection is brought up. A missing reference throws at connect time so the inspector can surface it instead of silently sending an empty header.

![Auth header preset dropdown showing Bearer Token, Basic Auth, API Key, and OAuth 2.1 options](assets/images/tutorials/tutorial-2-auth-preset.png)

*① the **+** button adds a blank header row. ②③④ pick a templated header (Bearer / Basic / API Key) and only the value is left for you to fill in. ⑤ switches the form to the dedicated [OAuth 2.1](#oauth-21-authorization-code) sub-form instead of an inline header.*

### OAuth 2.1 Authorization Code

Some MCP servers expect an OAuth 2.1 Authorization Code flow rather than a static bearer token. Picking **OAuth 2.1 Authorization Code (configure)** from the auth preset opens a dedicated sub-form on the connection.

![OAuth 2.1 sub-form with Client ID, Issuer URI, Scopes, Advanced details, Redirect URI, and Authorize button](assets/images/tutorials/tutorial-2-oauth-subform.png)

*① the OAuth section, opened from the auth preset dropdown. ② **Client ID** (required) and **Issuer URI** — the issuer alone is enough for OIDC discovery to auto-resolve the authorization and token endpoints. ③ **Scopes** are comma-separated; leave blank to inherit the issuer's defaults. ④ **Advanced** discloses manual `authorization_uri` / `token_uri` / client-secret / client auth method overrides for non-OIDC providers. ⑤ the **Redirect URI** the playground listens on — register this URI as an allowed redirect on the issuer side. ⑥ **Authorize** opens your system browser to grant access — click it after **Save & Connect** records the registration.*

The flow has three observable states:

- **Configured** — Save & Connect persists the OAuth registration but does not connect yet (no token).
- **AWAITING_AUTHORIZATION** — clicking **Authorize** opens the system browser to the issuer's consent screen and the connection sits in this state until the redirect lands. The Home dashboard surfaces a counter so half-finished authorizations don't get lost.
- **Connected** — once the redirect completes, the playground exchanges the code for tokens and the connection comes up like any other.

Tokens are kept in an encrypted file store under `${user.home}/spring-ai-playground/mcp/oauth-tokens/`. The encryption key is derived from a per-install salt plus the host's `user.home`, so copying the directory to another machine doesn't disclose tokens to that host. Refresh tokens are used transparently — once you authorize, the playground keeps the connection live across restarts as long as the issuer accepts the refresh.

!!! tip "Use `${ENV_VAR}` for client secrets"
    The OAuth sub-form's **Client secret** field accepts placeholders the same way header values do. Storing `${SOME_OAUTH_CLIENT_SECRET}` in the form keeps the secret out of the persisted JSON; the actual value is read from the OS environment at connect time.

### MCP Inspector

The Inspector is the practical center of the MCP screen — once a connection is up, it lets you exercise every primitive the server (or your client) exposes, isolated from chat.

![Inspector tab strip — Tools, Resources, Prompts, Ping, Notifications, Roots, Sampling, Elicitation](assets/images/tutorials/tutorial-2-inspector-tabs.png)

The eight tabs split cleanly into **server primitives** (Tools, Resources, Prompts, Ping, Notifications) and **client primitives** (Roots, Sampling, Elicitation — these are inverted: the *server* asks the playground to act as the client). The split matters because client-side primitives are how a server drives interactive behavior back into the playground, not how you call the server.

#### Tools

Each tool is a full-width card. The card shows the tool name, the description shown to the model, any tool annotations the server published as badges (read-only / destructive / idempotent / open-world hint), an inputs panel typed by JSON Schema (boolean / number / array / object / enum render with the matching control), and a **Run** button that calls the tool through the live transport.

![Tools tab with the new card layout — search, run buttons, JSON-Schema-typed inputs, parameter rows](assets/images/tutorials/tutorial-2-inspector-tools.png)

*① the selected tab — Tools is the default. ② all eight tabs run side by side. ③ a search input filters the cards by name or description, with the count alongside. ④ the **Run** play button on each card calls the tool through the actual transport (not just a sandbox). ⑤ the tool name and ⑥ its parameter schema, rendered as the appropriate input control.*

The result of a run lands inline in the same card: a status header (OK / ERROR badge, tool name, elapsed ms, timestamp), a **REQUEST** section, a **RESPONSE** section, a **Raw** toggle that swaps the cleaned response for the JSON-RPC envelope, a **Copy** action with toast feedback, and a dismiss button. That keeps the entire request → response → diagnose loop in one place per tool.

#### Resources, Prompts, Ping, Notifications

The other server-primitive tabs follow the same per-card pattern with the type-appropriate fields:

- **Resources** lists static resources and resource templates the server exposes; selecting one fetches its content into an inline panel. Templates accept parameters via the same JSON-Schema-typed inputs the Tools tab uses.
- **Prompts** lists named prompts; you can fill in arguments and preview the rendered messages the server would return to the model.
- **Ping** sends a one-shot `ping` request and reports round-trip latency — a quick liveness check independent of any tool.
- **Notifications** is a feed of inbound server-initiated notifications (`tools/list_changed`, `resources/updated`, `prompts/list_changed`, log messages) with timestamps. It's the easiest way to see whether a server actually pushes change notifications, since chat doesn't surface them.

![Resources tab with the empty-state message — same layout reused for prompts, ping, and notifications](assets/images/tutorials/tutorial-2-inspector-resources-empty.png)

*① clicking another tab swaps the inspector content but keeps the connection. ② tabs that have nothing to show say so explicitly — useful when probing a new server's capability surface.*

#### Roots, Sampling, Elicitation (client primitives)

These three are flipped: the server initiates and the playground (acting as the MCP client) responds. The Inspector exposes them so you can see and answer those server-side requests instead of having them disappear into the void:

- **Roots** is the list of file/URI roots the playground advertises to the server when it asks `roots/list`. The default is empty; add roots here when a server needs to know which directories or URIs you've opted to expose.
- **Sampling** is what fires when a server calls `sampling/createMessage` — it's the server asking your client (the playground) to run a model turn on its behalf. The tab logs the request with its messages and model preferences and lets you approve or reject it; approved requests run through the playground's configured chat model.
- **Elicitation** is what fires when a server calls `elicitation/create` to ask the user a question (a typed input form) mid-conversation. The tab renders the form per the server's schema, captures your answer, and ships it back as the response.

Together with Notifications, these tabs make the playground a useful place to *develop* MCP servers, not just consume them — you can see exactly when your server emits a notification or asks the client to do something.

### Getting Started With MCP

1. configure an MCP connection (or use the built-in one)
2. inspect the available tools, resources, and prompts in the Inspector
3. review the argument schemas with the JSON-Schema-typed inputs
4. execute tools / fetch resources / preview prompts directly
5. for OAuth-protected servers, complete the **Authorize** browser handoff once
6. use the validated connection later in Agentic Chat

### Relationship to Tool Studio

Tool Studio and MCP Server are designed to work together:

- Tool Studio creates or updates a tool
- the built-in MCP server exposes it
- MCP Inspector verifies the contract and runtime behavior
- Agentic Chat consumes the validated connection

This is one of the cleanest parts of the overall product flow.

## Vector Database

Vector Database is the RAG preparation and retrieval-validation area.

![Vector Database](assets/images/vectordb.gif)

It gives you an end-to-end environment for document ingestion, chunking, embedding, storage, and similarity search.

### What It Supports

This area acts as a vector database playground built on Spring AI vector store integrations.

That includes:

- switching between vector providers without changing application code
- using a unified Spring AI retrieval model
- validating retrieval quality before relying on it in chat

### Support for Major Vector Database Providers

Spring AI Playground follows the Spring AI vector store ecosystem and can be used with providers such as Apache Cassandra, Azure Cosmos DB, Azure Vector Search, Chroma, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, SAP Hana, Typesense, Weaviate, and others supported by Spring AI.

### Major Capabilities

- Custom Chunk Input: enter raw text and test chunking directly
- Document Uploads: ingest PDF, Word, and PowerPoint-style content
- End-to-End Processing: extraction, chunking, embedding, and indexing
- Search and Scoring: run vector similarity search and inspect scores
- Spring AI Filter Expressions: narrow searches using metadata conditions

### Why It Matters

RAG often fails quietly when chunking, embeddings, or indexing are misaligned. This screen exists so those problems become observable:

- you can see whether ingestion completed
- you can inspect chunk quality
- you can verify retrieval relevance
- you can catch embedding-model changes that invalidate old vector data

That is why the desktop launcher warns users about changing embedding models after indexing content.

In practice, this is what turns the Vector Database page into a real RAG validation surface rather than a generic upload page. You can inspect ingestion quality, retrieval quality, and filter behavior before trusting the same data inside chat.

## Agentic Chat

Agentic Chat is the unified runtime where Spring AI Playground combines documents, tools, models, and conversation state.

![Agentic Chat](assets/images/chat-mcp.gif)

This unified interface lets you:

- run RAG workflows grounded in indexed documents
- execute tool-enabled agent flows through MCP
- test complete agent strategies by combining documents and tools in a single chat session

### Key Features

- document selection for RAG grounding
- MCP connection selection for tool-enabled execution
- real-time visibility into retrieved context and tool usage
- one conversational surface for both chain-style and agentic patterns

This area is closely aligned with Spring AI's workflow and agentic guidance. If you want the conceptual background behind these two modes, see [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html).

### Two Integrated Paradigms

#### 1. RAG: Knowledge via Chain Workflow

When documents are selected, Agentic Chat follows a deterministic retrieval pattern:

- retrieval from the vector store
- prompt augmentation with grounded context
- response generation based on that context

#### 2. MCP: Actions via Agentic Reasoning

When MCP connections are enabled, Agentic Chat can behave like an agent:

- reasoning about which tools are needed
- invoking tools through MCP
- observing the result
- continuing or answering directly

### Workflow Integration

The intended end-to-end flow is:

1. prepare tools in Tool Studio or connect them in MCP Server
2. prepare knowledge in Vector Database
3. enable the relevant documents and MCP connections in Agentic Chat
4. send a request and observe how retrieval and tool use combine

This is the place where the rest of the product becomes visible as one coherent system rather than separate screens. The outputs of Tool Studio, MCP Server, and Vector Database all converge here.

### Requirements for Agentic Reasoning

Basic chat can work with any supported provider. Tool-enabled agentic behavior works best with models that support function calling and stronger reasoning.

For Ollama-based flows:

- use tool-capable models from [Ollama's Tool Category](https://ollama.com/search?c=tools)
- use reasoning-capable models from [Ollama's Thinking Category](https://ollama.com/search?c=thinking)
- validate tools in MCP Inspector before relying on them in Agentic Chat

The default `playground.chat.models` list features `qwen3.5` and `gemma4` for stronger tool-oriented reasoning, with `gpt-oss` and `deepseek-r1` as alternatives. See [Picking a Model](tutorials.md#picking-a-model) in the Tutorials for the tradeoffs.

### Agentic Chat Architecture Overview

The diagram below is included as a conceptual reference to the related agentic systems material in the Spring AI docs.

It is included here to explain how the Playground's Agentic Chat maps onto the broader Spring AI mental model. In this project, the diagram is not describing a separate product feature hidden behind the UI. It is a conceptual reference for understanding how the Playground combines model reasoning, retrieval, tool execution, and memory in one chat runtime.

![Spring AI Agentic System Structure](https://raw.githubusercontent.com/spring-io/spring-io-static/refs/heads/main/blog/tzolov/spring-ai-agentic-systems.jpg)

If you want the fuller conceptual background, start with [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html). That reference explains the workflow-versus-agent distinction that this Playground makes concrete through Tool Studio, MCP Server, Vector Database, and Agentic Chat.

This Chat experience facilitates exploration of Spring AI's workflow and agentic paradigms, empowering developers to build AI systems that combine chain-based RAG workflows with agentic, tool-augmented reasoning. In practice, it follows Spring AI's Agentic Systems architecture, where grounded retrieval and dynamic tool execution coexist in one context-aware chat runtime.

| Component | Type | Description | Configuration Location | Key Benefits | Model Requirements |
| --- | --- | --- | --- | --- | --- |
| **LLM** | Core Model | Executes chain-based workflows and performs agentic reasoning for tool usage within a unified chat runtime. | Agentic Chat | Central reasoning and response generation; supports both deterministic workflows and agentic patterns. | Chat models; tool-aware and reasoning-capable models recommended. |
| **Retrieval (RAG)** | Chain Workflow | Deterministic retrieval and prompt augmentation using vector search over selected documents. | Vector Database | Predictable, controllable knowledge grounding; tunable retrieval parameters such as Top-K and thresholds. | Standard chat plus embedding models. |
| **Tools (MCP)** | Agentic Execution | Dynamic tool selection and invocation via MCP, driven by LLM reasoning and tool schemas. | Tool Studio, MCP Server | Enables external actions, multi-step reasoning, and adaptive behavior. | Tool-enabled models with function calling and reasoning support. |
| **Memory** | Shared Agentic State | Sliding window conversation memory shared across workflows and agents through `ChatMemoryAdvisor` and the underlying Spring AI chat memory support. | Spring AI chat runtime (`InMemoryChatMemory`) | Coherent multi-turn dialogue with a sliding window improves coherence, planning, and tool usage quality. | Models benefit from longer context and structured reasoning. |

By leveraging these elements, Agentic Chat goes beyond basic Q&A and becomes a practical environment for building effective, modular AI applications that combine workflow predictability with agentic autonomy.

## Further Reading

- [Overview](index.md): return to the main product overview and documentation map
- [Getting Started](getting-started.md): install the app, configure providers, and choose a runtime
- [Architecture](architecture.md): runtime layers, data flows, and extension points
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
