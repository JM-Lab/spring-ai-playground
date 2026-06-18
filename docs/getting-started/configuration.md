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

**Property ↔ environment-variable mapping.** Any property binds from an env var via Spring's relaxed binding - uppercase, dots and dashes to underscores. So `spring.ai.playground.tool-studio.timeout-seconds` is set by `SPRING_AI_PLAYGROUND_TOOL_STUDIO_TIMEOUT_SECONDS`. A handful of knobs also carry a **short env-var alias** (e.g. `SERVER_PORT`, `OBS_PERSIST`); those are noted in the tables.

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

On an **Apple Silicon Mac** the `mlx` profile is layered onto `ollama` automatically (default chat model `qwen3.5:4b-mlx` plus a `-mlx` model menu). An `EnvironmentPostProcessor` gates it on `spring.ai.playground.ollama.mlx-auto-select` (default `true`) and the OS/arch check, so it never activates on Intel, Windows, Linux, or Docker. Pass `--spring.ai.playground.ollama.mlx-auto-select=false` to opt out. The desktop launcher disables this and resolves MLX builds itself - see [Alternative Runtimes → Apple Silicon and MLX models](alternative-runtimes.md#apple-silicon-mlx).

## Server & web { #server }

| Property | Env / `-D` | Default | Notes |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8282` | HTTP port for the web UI **and** the built-in MCP endpoint at `/mcp`. |
| `server.shutdown` | relaxed-binding env | `graceful` | Graceful shutdown. |
| `spring.lifecycle.timeout-per-shutdown-phase` | relaxed-binding env | `30s` | Drain time per phase. |
| `vaadin.pushmode` | relaxed-binding env | `automatic` | Vaadin server push. |
| `spring.servlet.multipart.max-file-size` / `max-request-size` | relaxed-binding env | `20MB` / `20MB` | Upload limits (Vector Database ingest). |
| `management.endpoints.web.exposure.include` | `SPRING_AI_PLAYGROUND_ACTUATOR_INCLUDE` | `health,info,metrics,prometheus,beans` | Actuator endpoints exposed at `/actuator/*`. |

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
| `spring.ai.ollama.chat.options.model` | `qwen3.5:2b` | Default chat model. |
| `spring.ai.ollama.embedding.options.model` | `qwen3-embedding:0.6b` | Default embedding model. |
| `spring.ai.playground.chat.models` | `qwen3.5:2b, qwen3.5:9b, qwen3.6:35b, gemma4:e4b, gpt-oss:20b, deepseek-r1:8b` | The model menu shown in the chat UI. |
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
| Google Tag Manager in the UI | `SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false` (env) or `-Dspring.ai.playground.telemetry.enabled=false` | enabled | When `false`, the app omits the GTM tag and disables its own GA. Set this for QA / offline / privacy. |

## MCP built-in server & exposure { #mcp }

The playground publishes its own MCP server at `/mcp` (Streamable HTTP). These control its identity and what it exposes - see [MCP Server Proxy](../features/mcp-server/proxy.md) for the UI equivalent.

| Property | Env | Default | Notes |
|---|---|---|---|
| `spring.ai.playground.built-in-mcp-server.name` | `SPRING_AI_PLAYGROUND_MCP_NAME` | `spring-ai-playground-built-in-mcp` | Advertised server name. |
| `spring.ai.playground.built-in-mcp-server.description` | `SPRING_AI_PLAYGROUND_MCP_DESCRIPTION` | *(see yaml)* | Advertised description. |
| `spring.ai.playground.built-in-mcp-server.exposure-mode` | `SPRING_AI_PLAYGROUND_MCP_EXPOSURE_MODE` | `both` | `builtin-only` · `composed-only` · `both` - whether `/mcp` serves your Tool Studio tools, the composed external tools, or both. |
| `spring.ai.playground.mcp-server.composed-tools-max-risk` | relaxed-binding env | `L5` | Caps which composed tools are published (`L1`-`L5`). |
| `spring.ai.playground.mcp-server.composed-tools` | relaxed-binding env | `[]` | Declarative list of composed (proxied) external tools - see [Configure exposure via YAML](../features/mcp-server/proxy.md#yaml-exposure). |
| `spring.ai.mcp.server.protocol` | relaxed-binding env | `STREAMABLE` | `SSE` · `STREAMABLE` · `STATELESS`. |
| `spring.ai.playground.chat.tool-result-max-chars` | relaxed-binding env | `12000` | Caps the characters of any single tool result before it returns to the model in the [Agentic Chat](../features/agentic-chat/index.md) loop - built-in, authored, or external. Oversized results are truncated (with a marker) so one verbose tool call cannot blow up the context window. `0` disables the cap. |
| `spring.ai.playground.chat.memory-max-messages` | relaxed-binding env | `10` | How many recent messages are sent to the model each turn - the conversation **memory window**. Overridable per chat from the settings drawer's **Recent messages** field. See [Context Engineering → Conversation memory](../context-engineering-architecture.md#conversation-memory). |
| `spring.ai.playground.chat.history-max-messages` | relaxed-binding env | `2000` | Safety cap on the **full local conversation store** that the screen and on-disk history read from; messages beyond this are dropped. The memory window above is what the model actually sees. |
| `spring.ai.mcp.server.request-timeout` | relaxed-binding env | `150` | Seconds. |

## Observability { #observability }

`spring.ai.playground.observability.*` ([architecture](../observability-architecture.md)):

| Property | Short env | Default | Notes |
|---|---|---|---|
| `...observability.ring-buffer-capacity` | `OBS_RING_CAPACITY` | `2000` | Trace events held in memory. |
| `...observability.persist` | `OBS_PERSIST` | `true` | Write traces to disk (one JSON file per trace). |
| `...observability.retain-days` | `OBS_RETAIN_DAYS` | `30` | Days of persisted traces to keep. |
| `...observability.max-spans-per-trace` | `OBS_MAX_SPANS` | `200` | Span cap per trace. |
| `...observability.capture-prompt-content` | relaxed-binding env | `true` | Capture prompt/response text in spans. |
| `...observability.max-prompt-content-bytes` | relaxed-binding env | `4096` | Truncation limit per captured message. |
| `...observability.max-captured-messages-per-span` | relaxed-binding env | `16` | Message cap per span. |
| `...observability.active-trace-ttl-seconds` | relaxed-binding env | `300` | Idle-trace finalize timeout. |
| `management.tracing.sampling.probability` | `SPRING_AI_PLAYGROUND_TRACE_SAMPLE` | `1.0` | Trace sampling (0.0-1.0). |
| OTLP export endpoint | `MANAGEMENT_OTLP_TRACING_ENDPOINT` | *(unset)* | Opt-in: set to a collector URL to export spans. |

Spring AI's own prompt/completion logging is **off** by default and toggled with `SPRING_AI_OBSERVE_LOG_PROMPT` / `SPRING_AI_OBSERVE_LOG_COMPLETION`, `SPRING_AI_CLIENT_OBSERVE_LOG_PROMPT` / `_COMPLETION`, `SPRING_AI_TOOLS_OBSERVE_INCLUDE_CONTENT`, `SPRING_AI_VECTORSTORE_OBSERVE_LOG` (all `false`).

## Tool Studio & JS sandbox { #tool-studio }

`spring.ai.playground.tool-studio.*` ([sandbox architecture](../safety-architecture.md)):

| Property | Env | Default | Notes |
|---|---|---|---|
| `...tool-studio.timeout-seconds` | relaxed-binding env | `30` | Per-tool JS execution timeout. |
| `...tool-studio.fs.base-path` | `TOOL_STUDIO_FS_BASE` | `${user.home}/spring-ai-playground/fs-tool-workspace` | Root the filesystem tools are confined to. |
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
| Filesystem-tool workspace | `TOOL_STUDIO_FS_BASE` | `<app-home>/fs-tool-workspace` |

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
