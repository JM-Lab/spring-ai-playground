description: Runtime configuration reference - server, AI providers and keys, MCP exposure, observability, sandbox, and data dirs, set from the desktop app, Docker, or source.

# Runtime Configuration

Spring AI Playground is a Spring Boot application, so it reads **standard Spring configuration** - `application.yaml`, OS environment variables, JVM system properties, and command-line arguments. Every knob below can be set the same way in all three launch modes; only the *mechanism* for supplying it differs.

This page is the single reference for those knobs. The task pages - [Desktop App](desktop.md), [Alternative Runtimes](alternative-runtimes.md) - show the everyday paths; come here for the full list.

## How configuration is supplied { #how }

**Precedence**, lowest to highest (a later source overrides an earlier one):

1. The bundled `application.yaml` (including the active profile section, `ollama` by default - the `ollama` / `openai` / `mlx` profiles are defined inline) - the defaults.
2. An external `application.yaml` next to the runtime or under the app config directory.
3. OS **environment variables**.
4. JVM **system properties** (`-Dkey=value`).
5. Command-line arguments (`--key=value`).

**Property ↔ environment-variable mapping.** Any property binds from an env var via Spring's relaxed binding - uppercase, dots and dashes to underscores. So `spring.ai.playground.tool-studio.timeout-seconds` is set by `SPRING_AI_PLAYGROUND_TOOL_STUDIO_TIMEOUT_SECONDS`.

**Per launch mode:**

| Mode | How you set configuration |
|---|---|
| **Desktop app** | The launcher has a **YAML editor** and an **environment-variable editor**, plus seven provider **templates** (Ollama · OpenAI · OpenAI-Compatible for Ollama / llama.cpp / TabbyAPI / LM Studio / vLLM). Pick a template, edit, and it writes your `application.yaml` into the app config directory before starting the bundled runtime. See [Desktop App](desktop.md). |
| **Docker** | Pass `-e KEY=value` for each env var, `-p 8282:8282` to publish the port, and `-e SPRING_PROFILES_INCLUDE=mcp-stdio` to switch transport. Mount a volume to persist data. See [Alternative Runtimes → Docker](alternative-runtimes.md). |
| **Source build** | `./mvnw spring-boot:run` or `java -jar target/*.jar`; supply env vars, `-Dkey=value`, `--key=value`, or an external `application.yaml`. |

## Profiles { #profiles }

The default active profile is **`ollama`** (`spring.profiles.default: ollama`). Profiles ship in the jar:

| Profile | Effect |
|---|---|
| `ollama` *(default)* | Local Ollama chat + embedding models. |
| `openai` | OpenAI chat + embedding (needs `OPENAI_API_KEY`). |
| `mcp-stdio` | Switches the built-in MCP server transport from Streamable HTTP to **STDIO** (see [below](#mcp-stdio-profile)). |
| `mlx` *(auto)* | MLX-optimized Ollama model defaults for Apple Silicon. **Auto-activated**, not set by hand - see the note below. |

Activate with `SPRING_PROFILES_ACTIVE=openai`, layer with `SPRING_PROFILES_INCLUDE=mcp-stdio`, or use `--spring.profiles.active=` / `-Dspring.profiles.active=`.

On an **Apple Silicon Mac** the `mlx` profile is layered onto `ollama` automatically (default chat model `qwen3.5:4b-mlx` plus a model menu of `-mlx` builds alongside the standard GGUF builds, which stay in the menu for image chat - MLX builds ship without vision tensors). An `EnvironmentPostProcessor` gates it on `spring.ai.playground.ollama.mlx-auto-select` (default `true`) and the OS/arch check, so it never activates on Intel, Windows, Linux, or Docker. Pass `--spring.ai.playground.ollama.mlx-auto-select=false` to opt out. The desktop launcher disables this and resolves MLX builds itself - see [Alternative Runtimes → Apple Silicon and MLX models](alternative-runtimes.md#apple-silicon-mlx).

## Server & web { #server }

| Property | Env / `-D` | Default | Notes |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8282` | HTTP port for the web UI **and** the built-in MCP endpoint at `/mcp`. |
| `server.shutdown` | relaxed-binding env | `graceful` | Graceful shutdown. |
| `spring.lifecycle.timeout-per-shutdown-phase` | relaxed-binding env | `30s` | Drain time per phase. |
| `vaadin.pushmode` | relaxed-binding env | `automatic` | Vaadin server push. |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | relaxed-binding env | `20MB` / `20MB` | Upload limits (Vector Database ingest). |
| `management.endpoints.web.exposure.include` | relaxed-binding env | `health,info,metrics,prometheus` | Actuator endpoints exposed at `/actuator/*`. |

## AI providers & models { #ai }

Provider selection (which Spring AI model backs each capability):

| Property | Default | Notes |
|---|---|---|
| `spring.ai.model.chat` | `ollama` | Set to `openai` by the `openai` Spring profile. |
| `spring.ai.model.embedding` | `ollama` | Used by the Vector Database. |
| `spring.ai.model.image` / `moderation` / `audio.speech` / `audio.transcription` | `none` | Opt-in capabilities. |

**Ollama profile** (`ollama`, default):

| Property | Default | Notes |
|---|---|---|
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Point at a remote/host Ollama via `SPRING_AI_OLLAMA_BASE_URL` (common in Docker). |
| `spring.ai.ollama.init.pull-model-strategy` | `when_missing` | Auto-pull models on startup. |
| `spring.ai.ollama.chat.options.model` | `qwen3.5:4b` | Default chat model. |
| `spring.ai.ollama.embedding.options.model` | `qwen3-embedding:0.6b` | Default embedding model. |
| `spring.ai.ollama.chat.keep-alive` / `spring.ai.ollama.embedding.keep-alive` | `30m` | How long Ollama keeps each model loaded in memory after a call, as a [Go duration](https://pkg.go.dev/time#ParseDuration) (`0` unloads it at once, `-1` keeps it forever). The default trades VRAM for no reload stall when you come back to a chat. Sent per request, so it wins over the Ollama server's own `OLLAMA_KEEP_ALIVE` default; that server env var is still the right knob when you want one duration for every client of a self-managed Ollama. Override a single conversation from the chat settings drawer's provider-options JSON with `{"keep_alive": "5m"}`. |
| `spring.ai.playground.chat.models` | `qwen3.5:2b/4b/9b, qwen3.6:27b/35b, gemma4:e2b/e4b/12b/31b, gpt-oss:20b, deepseek-r1:8b` | The model menu shown in the chat UI. |
| `spring.ai.playground.ollama.mlx-auto-select` | `true` | On Apple Silicon, auto-activate the [`mlx` profile](#profiles) (MLX model defaults). Set `false` to keep the generic model names. |

**OpenAI profile** (`openai`):

| Property | Env | Default | Notes |
|---|---|---|---|
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | *(required)* | Set as an env var; never commit it. |
| `spring.ai.openai.chat.options.model` | - | `gpt-5.4-mini` | Default chat model. |
| `spring.ai.openai.embedding.options.model` | - | `text-embedding-3-small` | Default embedding model. |
| `spring.ai.playground.chat.models` | - | `gpt-5.4, gpt-5.4-mini, gpt-5.4-nano, gpt-5.2` | Chat model menu. |

Tool / MCP-server API keys (GitHub PAT, `BRAVE_API_KEY`, `MS_TENANT_ID`, Google keys, ...) are documented per surface on the [Default Tools](../features/default-tools/index.md) and [Default MCP Servers](../features/default-mcp-catalog/index.md) pages - they are supplied the same way (env var or the launcher's env editor).

## Telemetry & analytics { #telemetry }

| Knob | How to set | Default | Notes |
|---|---|---|---|
| Google Tag Manager in the UI | `SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false` (env) or `-Dspring.ai.playground.telemetry.enabled=false` | enabled (production builds only) | When `false`, the app omits the GTM tag and disables its own GA. Development mode (`mvnw spring-boot:run`, IDE runs) never sends telemetry regardless of this switch. Set this for QA / offline / privacy. |

### What is collected { #telemetry-events }

Telemetry is anonymous and content-free: no prompts, no responses, no file names, no
URLs, no API keys, no server addresses. Names of things you create yourself (tools,
presets, custom MCP servers) are masked to `authored` / `custom` / `external` - only
names that ship in the built-in catalogs are ever sent as-is. Every sender goes through
one of two classes, so the complete list is auditable in code: `UsageEventTracker` for
the events below, and `GoogleAnalyticsNavigationListener` for `page_view`, which
overrides the browser's default page location and title with the bare route path so that
neither the conversation id in the URL nor the conversation title can reach the wire.

| Event | Parameters | Fired when |
|---|---|---|
| `page_view` | route path | Navigating between views |
| `chat_message_sent` | provider, model, reasoning, dynamic_tools, tool_count, image_count, rag_enabled | Sending a chat message |
| `model_selected` | provider, model | Applying chat settings |
| `model_downloaded` | model, via | An Ollama model download completes |
| `tool_called` | tool_name (catalog names only), source, risk_level, hitl, catalog_id (catalog ids only) | A tool call finishes in chat |
| `preset_applied` / `preset_blocked` | preset_id (catalog ids only), dynamic_tools, tool_count | Applying a preset / the key gate blocks Apply |
| `mcp_server_added` | transport, catalog_id (catalog ids only), oauth | Saving and connecting an MCP server |
| `mcp_tools_exposed` | mode, builtin_count, composed_count, max_risk | Applying the Expose Tools drawer |
| `rag_document_indexed` | doc_type, chunk_count | Embedding a document into the vector store |
| `tool_authored` | action, sandbox_overrides, hitl | Saving a tool in Tool Studio |
| `action_card_rendered` | card_type | A visualization action card renders in chat |
| `voice_input_used` | mode | Starting voice input |
| `usage_snapshot` (at most once per day, per app run) | aggregate counters only: recent call/token/error/latency totals, conversation count, installed model count, VRAM, RAM, top quantization, HITL and risk-signal totals | First page load of the day |
| `mcp_server_active` (at most once per day and app run, per configured server) | transport, catalog_id (catalog ids only), oauth, connected | First page load of the day |
| `model_usage` (at most once per day and app run, per model used in the last 24h) | model, provider, calls, tokens, errors, p95_ms | First page load of the day |
| `js_error` (via Google Tag Manager) | error_message, error_url, error_line | An uncaught JavaScript error occurs in the UI |

Session-level user properties: `app_version`, `app_surface` (web/desktop), `platform`,
`chat_provider`, `embedding_provider`, `embedding_model`, `tool_search_index`.

## MCP built-in server & exposure { #mcp }

The playground publishes its own MCP server at `/mcp` (Streamable HTTP). These control its identity and what it exposes - see [MCP Server Proxy](../features/mcp-server/proxy.md) for the UI equivalent.

| Property | Env | Default | Notes |
|---|---|---|---|
| `spring.ai.playground.built-in-mcp-server.name` | relaxed-binding env | `spring-ai-playground-built-in-mcp` | Advertised server name. |
| `spring.ai.playground.built-in-mcp-server.description` | relaxed-binding env | *(see yaml)* | Advertised description. |
| `spring.ai.playground.built-in-mcp-server.exposure-mode` | relaxed-binding env | `both` | `builtin-only` · `composed-only` · `both` - whether `/mcp` serves your Tool Studio tools, the composed external tools, or both. |
| `spring.ai.playground.mcp-server.composed-tools-max-risk` | relaxed-binding env | `L5` | Caps which composed tools are published (`L1`-`L5`). |
| `spring.ai.playground.mcp-server.composed-tools` | relaxed-binding env | `[]` | Declarative list of composed (proxied) external tools - see [Configure exposure via YAML](../features/mcp-server/proxy.md#yaml-exposure). |
| `spring.ai.mcp.server.protocol` | relaxed-binding env | `STREAMABLE` | `SSE` · `STREAMABLE` · `STATELESS`. |
| `spring.ai.playground.chat.tool-result-max-chars` | relaxed-binding env | `12000` | Caps the characters of any single tool result before it returns to the model in the [Agentic Chat](../features/agentic-chat/index.md) loop - built-in, authored, or external. Oversized results are truncated (with a marker) so one verbose tool call cannot blow up the context window. `0` disables the cap. |
| `spring.ai.playground.chat.memory-max-messages` | relaxed-binding env | `10` | How many recent messages are sent to the model each turn - the conversation **memory window**. Overridable per chat from the settings drawer's **Recent messages** field. See [Context Engineering → Conversation memory](../context-engineering-architecture.md#conversation-memory). |
| `spring.ai.playground.chat.history-max-messages` | relaxed-binding env | `2000` | Safety cap on the **full local conversation store** that the screen and on-disk history read from; messages beyond this are dropped. The memory window above is what the model actually sees. |
| `spring.ai.playground.chat.default-preset` | relaxed-binding env | `self-equipping-agent` | The [prompt preset](../features/agentic-chat/prompt-presets.md) a **brand-new chat** opens with - its system prompt plus, for a dynamic preset, [dynamic tool discovery](../features/agentic-chat/dynamic-tool-discovery.md). It does not touch the built-in MCP exposure. Empty or an unknown id falls back to a plain chat. |
| `spring.ai.playground.chat.tool-search.enabled` | relaxed-binding env | `true` | Master switch for **[dynamic tool discovery](../features/agentic-chat/dynamic-tool-discovery.md)** - the `toolSearchTool` advisor, the boot-time tool index, and the chat checkbox. `false` removes the feature entirely. |
| `spring.ai.playground.chat.tool-search.default-on` | relaxed-binding env | `false` | Whether new chats start in dynamic-discovery mode when no preset decides it. Note the shipped `chat.default-preset` (`self-equipping-agent`, below) already opens new chats in dynamic mode; this flag matters once you change or clear that preset. |
| `spring.ai.playground.chat.tool-search.min-tools` | relaxed-binding env | `10` | Minimum searchable tools before the chat's **Dynamic tool discovery** checkbox enables - discovery only pays off with a real catalog to search. |
| `spring.ai.playground.chat.tool-search.max-results` | relaxed-binding env | `3` | Tool names returned per `toolSearchTool` search. |
| `spring.ai.playground.chat.tool-search.index-type` | relaxed-binding env | `HYBRID` | `HYBRID` (exact tool-name match, then vector search) or `VECTOR` (vector only). |
| `spring.ai.playground.chat.tool-search.vector-store` | relaxed-binding env | `DEDICATED` | `DEDICATED` (a private, persisted tool index) or `SHARED` (reuse the RAG vector store). See [Context Engineering → Tools](../context-engineering-architecture.md#tools). |
| `spring.ai.mcp.server.request-timeout` | relaxed-binding env | `150` | Seconds. |

## Agent loop { #agent-loop }

`spring.ai.playground.chat.agent-loop.*` bounds the [Agentic Chat](../features/agentic-chat/index.md) tool-calling loop and its interactive dialogs. See [Agent Loop](../agent-loop-architecture.md) for how these are enforced. Defaults suit a broad agent; raise the round caps only if a legitimate multi-step task needs more tool rounds.

| Property | Env | Default | Notes |
|---|---|---|---|
| `spring.ai.playground.chat.agent-loop.soft-max-rounds` | relaxed-binding env | `16` | Once a turn exceeds this many tool rounds, further tool calls are answered with a "wrap up now" message instead of running, nudging the model to reply with what it has. |
| `spring.ai.playground.chat.agent-loop.hard-max-rounds` | relaxed-binding env | `18` | Hard stop: the loop is ended with a final message if the model keeps calling tools past this. Raised to `soft-max-rounds` if set lower. |
| `spring.ai.playground.chat.agent-loop.max-identical-calls` | relaxed-binding env | `3` | How many times a tool may be called with identical arguments in one turn before repeats are short-circuited (a legitimate re-read is fine; a stuck loop is not). |
| `spring.ai.playground.chat.agent-loop.interactions-per-round` | relaxed-binding env | `1` | How many interactive dialogs (approval, file upload, image pick) may open per round. Keep at `1` so a round's blocking waits cannot stack past the stream timeout. |
| `spring.ai.playground.chat.agent-loop.approval-timeout-seconds` | relaxed-binding env | `120` | How long a human-in-the-loop approval dialog waits before failing safe to **decline**. Clamped below the 300s stream timeout. |
| `spring.ai.playground.chat.agent-loop.dialog-timeout-seconds` | relaxed-binding env | `180` | How long a file-upload or image dialog waits before returning "not provided". Clamped below the 300s stream timeout. |

## Observability { #observability }

`spring.ai.playground.observability.*` ([architecture](../observability-architecture.md)):

| Property | Env | Default | Notes |
|---|---|---|---|
| `...observability.ring-buffer-capacity` | relaxed-binding env | `2000` | Trace events held in memory. |
| `...observability.persist` | relaxed-binding env | `true` | Write traces to disk (one JSON file per trace). |
| `...observability.retain-days` | relaxed-binding env | `30` | Days of persisted traces to keep. |
| `...observability.max-spans-per-trace` | relaxed-binding env | `200` | Span cap per trace. |
| `...observability.capture-prompt-content` | relaxed-binding env | `true` | Capture prompt/response text in spans. |
| `...observability.max-prompt-content-bytes` | relaxed-binding env | `4096` | Truncation limit per captured message. |
| `...observability.max-captured-messages-per-span` | relaxed-binding env | `16` | Message cap per span. |
| `...observability.active-trace-ttl-seconds` | relaxed-binding env | `300` | Idle-trace finalize timeout. |
| `management.tracing.sampling.probability` | relaxed-binding env | `1.0` | Trace sampling (0.0-1.0). |
| OTLP export endpoint | `MANAGEMENT_OTLP_TRACING_ENDPOINT` | *(unset)* | Opt-in: set to a collector URL to export spans. |

Spring AI's own prompt/completion logging is **off** by default and toggled with `spring.ai.chat.observations.log-prompt` / `log-completion`, `spring.ai.chat.client.observations.log-prompt` / `log-completion`, `spring.ai.tools.observations.include-content`, `spring.ai.vectorstore.observations.log-query-response` (all `false`).

## Tool Studio & JS sandbox { #tool-studio }

`spring.ai.playground.tool-studio.*` ([sandbox architecture](../safety-architecture.md)):

| Property | Env | Default | Notes |
|---|---|---|---|
| `...tool-studio.timeout-seconds` | relaxed-binding env | `30` | Per-tool JS execution timeout. |
| `...tool-studio.fs.base-path` | relaxed-binding env | `${user.home}/spring-ai-playground/workspace` | Root the filesystem tools are confined to. |
| `...tool-studio.js-sandbox.allow-network-io` | relaxed-binding env | `false` | Raw Java network access in tool JS (the built-in `fetch` is preferred). |
| `...tool-studio.js-sandbox.allow-file-io` | relaxed-binding env | `false` | Raw Java file access (use `safety.fs`). |
| `...tool-studio.js-sandbox.allow-native-access` / `allow-create-thread` | relaxed-binding env | `false` | Native / thread capabilities. |
| `...tool-studio.js-sandbox.max-statements` | relaxed-binding env | `500000` | Statement budget before the tool is killed. |
| `...tool-studio.js-sandbox.deny-classes` / `allow-classes` | relaxed-binding env | *(see yaml)* | Class allow/deny lists. **Deny always wins**; lowering these weakens the sandbox. |

!!! warning "The sandbox defaults are security-critical"
    `allow-*` flags are `false` and the `deny-classes` list blocks `System`, `Runtime`, `ProcessBuilder`, reflection, `ClassLoader`, and more. Don't widen these globally - grant capability per tool instead (which raises that tool's [Risk Level](../safety-architecture.md)).

## Data directories & logs { #data }

| Path | Controlled by | Default |
|---|---|---|
| App home | `spring.ai.playground.user-home` | `${user.home}/spring-ai-playground` |
| Logs | (derived) | `<app-home>/logs` (rolling file; the `mcp-stdio` profile detaches the console appender) |
| Tool specs | (derived) | `<app-home>/tool/save` |
| Vector store | (derived) | `<app-home>/vectorstore/save` |
| Filesystem-tool workspace | `SPRING_AI_PLAYGROUND_TOOL_STUDIO_FS_BASE_PATH` | `<app-home>/workspace` |

The desktop app stores this tree under the OS app-data location (macOS `~/Library/Application Support/spring-ai-playground`, Windows `%APPDATA%/spring-ai-playground`, Linux `~/.config/spring-ai-playground`).

## The `mcp-stdio` profile { #mcp-stdio-profile }

For embedding the playground as a **stdio MCP server** inside another MCP client (Claude Desktop, an IDE), activate `mcp-stdio`:

- The MCP JSON-RPC channel becomes process **stdout/stdin**, so the boot banner and startup-info line are silenced and the console log appender is detached (rolling file logging continues under `<app-home>/logs`).
- The Spring web stack and Vaadin UI **stay up on `8282`** - publish it (`-p 8282:8282`) to reach the Inspector in a browser alongside the stdio channel.
- Actuator HTTP endpoints are disabled in this profile.

```bash
# Docker, stdio transport (web UI still available on 8282)
docker run -i --rm -p 8282:8282 -e SPRING_PROFILES_INCLUDE=mcp-stdio ghcr.io/spring-ai-community/spring-ai-playground
```

See [Alternative Runtimes](alternative-runtimes.md) for the full Docker / `java -jar` walkthrough.

## Related

- [Desktop App](desktop.md) · [Alternative Runtimes](alternative-runtimes.md) - the launch flows that consume these knobs
- [MCP Server Proxy](../features/mcp-server/proxy.md) - the UI for exposure; `#yaml-exposure` for the declarative form
- [AI Agent Tool Safety](../safety-architecture.md) - what the sandbox flags mean
- [AI Agent Observability](../observability-architecture.md) - what the observability knobs feed
