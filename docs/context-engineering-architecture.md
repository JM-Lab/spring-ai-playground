description: How Spring AI Playground builds each chat turn's context window: system prompt, retrieved documents, tools, conversation memory, and per-request options.

# Context Engineering

Prompt engineering is about wording one instruction well. **Context engineering** is the broader job of deciding *everything* the model sees on a given turn - the system prompt, the documents you retrieved, the tools you exposed, the conversation so far, and the per-request options that shape how it answers.

[Agentic Chat](features/agentic-chat/index.md) is where that context is assembled. This page is the architecture behind it: the sources a turn draws on, and the components that build each one. It is the design-side companion to the [Prompt Templates](features/agentic-chat/prompt-templates.md) and [Prompt Presets](features/agentic-chat/prompt-presets.md) feature pages.

## The context a turn carries

Every chat turn sends the model a context window composed from five sources, each configured in a different place. The diagram shows how they converge into the single input the model receives, and how tool results and memory feed back into the next turn:

![Context engineering in Spring AI Playground - five sources (system prompt, retrieved documents, tool schemas, conversation memory, and per-request options) converge into the context window the model sees each turn; the request options factory builds provider-native options, the model streams a response, tool calls run through the human-in-the-loop gate and re-enter the context, and each turn is appended to conversation memory](assets/images/context-engineering.svg){ loading=lazy }

| Source | Built from | Configured in | Reference |
| --- | --- | --- | --- |
| **System prompt** | a preset, a filled template, or free text | Prompt Library / settings drawer | [below](#system-prompts-presets-and-templates) |
| **Retrieved documents** | vector search over selected collections | Vector Database | [RAG grounding](#retrieved-documents-rag) |
| **Tools** | built-in, authored, and proxied tool schemas | chat tool selector | [Tools](#tools) |
| **Conversation memory** | a sliding window of prior turns | Spring AI chat memory | [Memory](#conversation-memory) |
| **Per-request options** | reasoning effort + generation options | reasoning control / settings drawer | [reasoning](#reasoning-effort), [generation](#generation-options) |

The first one - the system prompt and the preset/template machinery that produces it - is owned entirely here; the rest are summarized below and detailed on their own pages.

## System prompts, presets, and templates { #system-prompts-presets-and-templates }

The system prompt is authored through the Prompt Library - as a ready-made [preset](features/agentic-chat/prompt-presets.md) or a variable-driven [template](features/agentic-chat/prompt-templates.md). Three components back it:

- **[`ChatSystemPromptPresetCatalog`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatSystemPromptPresetCatalog.java)** loads the built-in entries from the classpath resource `chat/system-prompt-presets.json`. Each is a `Preset` record - `id`, `displayName`, `description`, `prompt`, `kind`, and a `tools` list - where `kind` is `TEMPLATE` (has `{{variables}}`) or `EXAMPLE` (ready to use). This mirrors the `DefaultToolPresetCatalog` pattern used for built-in tools.
- **[`ChatSystemPromptPresetService`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatSystemPromptPresetService.java)** merges those built-ins with the user's own entries, persisted to `<home>/spring-ai-playground/chat/save/system-prompt-presets.json`. Saving derives a stable id from the display name (`user-<slug>`), so re-saving the same name updates in place; writes go through the shared non-daemon persistence executor.
- **[`ChatSystemPromptTemplateRenderer`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatSystemPromptTemplateRenderer.java)** is a Spring AI `TemplateRenderer` for the `{{variable}}` syntax.

### Why double braces

Spring AI's default `StTemplateRenderer` (StringTemplate) uses single braces `{var}`, which collides with the literal `{ }` that prompts routinely contain - JSON examples, code, set notation. The renderer therefore uses the **double-brace** convention `{{var}}` and leaves single braces untouched, so a template can show a JSON example without escaping.

### Variable grammar

The token form is `{{name}}`, `{{name:type}}`, or `{{name:type(args)}}`, each optionally ending in `=default`:

| Type | Args | Renders as |
| --- | --- | --- |
| `text` *(default)* | - | single-line field |
| `multiline` | - | text area |
| `number` | `(min,max)` | bounded number field |
| `select` | `(a,b,c)` | dropdown |
| `list` | `(a,b,c,max=N)` | multi-pick, up to N |

`variableSpecs(template)` parses the form fields; `render(template, values)` substitutes them, falling back to each variable's `=default` when a value is blank - so a template is always renderable, even unfilled. The assembled string becomes the conversation's system prompt.

## Reasoning effort { #reasoning-effort }

[`ReasoningEffort`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ReasoningEffort.java) (`OFF`, `LOW`, `MEDIUM`, `HIGH`) is chosen on the selector row and applied **per turn** - it is not baked into the saved conversation options. A `null`/`OFF` choice leaves the model default untouched, which keeps non-reasoning models safe.

Whether the control appears, and what it is called, comes from [`ChatProvider`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatProvider.java) - resolved from the active `ChatModel` bean (`OpenAiChatModel` → `OPENAI`, `OllamaChatModel` → `OLLAMA`, else `GENERIC`). The level is mapped at request-build time:

| Effort | OpenAI (`reasoning_effort`) | Ollama (thinking) |
| --- | --- | --- |
| `OFF` | no override | `disableThinking()` |
| `LOW` | `low` | `thinkLow()` |
| `MEDIUM` | `medium` | `thinkMedium()` |
| `HIGH` | `high` | `thinkHigh()` |

OpenAI reasoning models cannot be fully switched off, so `OFF` maps to "no override" there rather than a minimal effort.

## Generation options { #generation-options }

Saved per conversation are the standard `DefaultChatOptions` (model, temperature, top-p, max tokens, frequency/presence penalties, top-k) plus [`ChatExtraOptions`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatExtraOptions.java) - `seed`, `stop`, and a free-form `providerOptionsJson`. A `null` extra-options block preserves prior behavior exactly.

[`ChatRequestOptionsFactory`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/ChatRequestOptionsFactory.java) turns these into the provider's native options object:

1. `ChatProvider.from(chatModel)` selects the branch.
2. **OpenAI** builds `OpenAiChatOptions` with `streamUsage(true)` (so the streamed response carries token counts for the chat footer), plus seed, stop, and reasoning effort.
3. **Ollama** builds `OllamaChatOptions`, mapping max tokens to `numPredict`, carrying top-k, and applying the thinking level.
4. **Generic** builds a plain `DefaultToolCallingChatOptions` with the common fields.
5. The free-form **provider-options JSON** is overlaid last with Jackson `readerForUpdating` (override wins).

The overlay mapper ignores connection fields through a mixin (`baseUrl`, `apiKey`, `credential`, `proxy`, ...), so a provider-options override can tune request parameters but **cannot touch the connection or inject credentials**; a malformed override is logged and skipped rather than breaking the chat. The Ollama option keys bind through snake_case `@JsonProperty` names, so the JSON placeholder uses `top_k`, `num_ctx`, and the like. The full property surface is in the [Configuration reference](getting-started/configuration.md#ai).

## Retrieved documents (RAG)

When documents are selected, the `SpringAiPlaygroundRagAdvisor` retrieves from the vector store and augments the prompt; it short-circuits when nothing is selected, so retrieval is opt-in per conversation. Indexing, embedding models, and retrieval parameters are covered in [Vector Database](features/vector-database.md).

## Tools

Tool schemas are added to the context from three sources - built-in tools, tools authored in [Tool Studio](features/tool-studio/index.md), and tools proxied from external [MCP servers](features/mcp-server/index.md). The chat [tool selector](features/agentic-chat/index.md#choosing-tools-and-documents) decides which are exposed for a given conversation; every call runs through one `McpToolCallingManager` loop with the [human-in-the-loop gate](hitl-architecture.md).

### Static exposure vs dynamic discovery

There are two ways the exposed tools enter the context. The default is **static exposure**: each selected tool's schema is inlined into the request - direct, but it grows the prompt in step with the toolset, and a broad agent preset can spend tens of thousands of tokens on definitions before the first turn. When a chat opts into **[dynamic tool discovery](features/agentic-chat/index.md#dynamic-tool-discovery)** (the tool popover, or a preset/template that sets it), the request instead carries a single `toolSearchTool`. A `ToolSearchToolCallingAdvisor` then drives a discovery loop: the model searches for a capability, the advisor resolves the returned tool names against the full callback pool and binds just those for that round, and the loop repeats until the model answers. Both paths land in the same `McpToolCallingManager`, so risk scoring, observation, and the human-in-the-loop gate apply identically - discovery changes only *when* a tool's definition enters the context, not how it executes.

The searchable pool is every Local-Passed tool (built-in and authored) plus composed external tools. In the default `DEDICATED` mode it is embedded into a dedicated in-memory vector store - kept separate from the RAG store - by [`PersistentToolIndex`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/PersistentToolIndex.java), which is content-addressed and persisted under `<home>/spring-ai-playground/tool-index/`: each tool is embedded once and reused across chats and restarts, re-embedding only when the embedding model changes, and the index is warmed at boot. Behaviour is tunable under `spring.ai.playground.chat.tool-search` (see the [Configuration reference](getting-started/configuration.md#ai)) - `enabled`, `default-on`, `min-tools` (the floor that gates the chat checkbox, default 10), `max-results` (tool names returned per search, default 3), `index-type` (`HYBRID` exact-then-vector, or `VECTOR`), and `vector-store` (`DEDICATED` or `SHARED`). Because matches come from embedding similarity, a capable chat model and embedding model give the most reliable discovery.

## Conversation memory

Memory is split in two, so that *what you keep* and *what the model sees* are decided separately:

- The **full conversation** is retained locally - the screen and the on-disk history both read from it - bounded only by a generous safety cap (`spring.ai.playground.chat.history-max-messages`, default 2000 messages).
- Only the **last N messages** are handed to the model on each turn. [`LlmWindowChatMemory`](https://github.com/spring-ai-community/spring-ai-playground/blob/main/src/main/java/org/springaicommunity/playground/service/chat/LlmWindowChatMemory.java) wraps the store as a `ChatMemory` decorator and, in its `get()`, returns just that tail window to the `MessageChatMemoryAdvisor` - so older turns stay on your machine without inflating every request.

The window is `spring.ai.playground.chat.memory-max-messages` (default 10), and each conversation can override it through the **Recent messages** field in the [settings drawer](features/agentic-chat/index.md#the-chat-settings-drawer) (stored as `ChatExtraOptions.memoryWindow`, baked in on Apply & New Chat). This is the memory lever of context engineering: a longer window is more grounding but more tokens and latency on every turn.

## Provider awareness and the conversation lock

Because options, reasoning, and even the model menu differ by provider, each saved conversation is **stamped** with the provider that produced it. Opening it while the app runs a different provider renders it read-only with a banner (see [Agentic Chat → Provider lock](features/agentic-chat/index.md#provider-lock)) - the history is preserved, but you cannot append turns the current provider could not have generated.

## Where it comes together

This is all assembled live in [Agentic Chat](features/agentic-chat/index.md), and captured after the fact in the [Observability](features/observability/index.md) dashboards - so the context you engineered for a turn is the same context you can inspect once it has run.
