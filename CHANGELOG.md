# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Local speech-to-text mic in Agentic Chat — captures voice input and runs whisper.cpp locally via an Electron Node addon (no cloud round-trip). Whisper model selection and download surface in the desktop launcher's config editor and startup splash.
- Developer-facing **Observability dashboards** — Micrometer-backed collector with MDC + scheduled samplers, ring-buffer + time-series data store with disk mirror and pricing inputs, ECharts dashboard shell with sidebar nav, and per-area surfaces (Overview, Tokens & Cost, Models, Tools, MCP, Vector, System, Logs, Traces).
- **Modular RAG pipeline studio** in Vector Database — composable ETL pipeline editor for reader / chunker / pre-retrieval / retrieval / post-retrieval stages, reworked chunk-confirmation dialog UX, Name + Description fields on documents, and an internal rename of `VectorStoreDocumentService` → `OfflineEtlPipelineService`.
- **Vendor-official remote MCP server catalog** — curated list of remote MCP servers grouped by category in a sidebar, with env-gated activation so only entries whose required env vars are set become enabled.

## [0.2.0-M6]

### Added

- Bundled **default-tool catalog of 86 tools** split across five JSON source files (`default-tool-specs.json`, `default-tool-specs-builtin.json`, `default-tool-specs-builtin-helpers.json`, `default-tool-specs-builtin-fs.json`, `default-tool-specs-network.json`, `default-tool-specs-kr.json`) loaded via wildcard `classpath*:default-tool-specs*.json`. Categories: Text & Strings, Data Formats, Date & Time, Math & Compute, Encoding, Crypto & Random, Security, Files, Web (global + Korea), Productivity, Messaging, AI APIs.
- **`DefaultToolPresetCatalog` + preset/preference resolver** picking which slice of the bundled catalog the built-in MCP server exposes. Five presets shipped: `Starter 5` (default, no setup), `Dev Essentials`, `Korea Toolkit (free)`, `File Toolkit`, `Everything`. `DefaultToolsPreferenceService` layers per-tool include / exclude rules (by tag / category / name) on top of the chosen preset and persists to `~/spring-ai-playground/tool/save/default-tools-preference.json`. CLI override resolvable through `defaultToolsPreferenceResolver`.
- **Tool MCP Server Setting drawer** in Tool Studio (toolbox icon) — single surface for what the built-in MCP server exposes. Three sections: **Tools exposed** chip summary, **Custom tools (you created)** with auto-add toggle + Manually exposed combo, **Default tools (built-in)** with Preset radio and Advanced curation (include / exclude rules). Writes to the same `default-tools-preference.json` file as the desktop launcher.
- **Desktop launcher Default MCP Tools card** in the config editor — preset chooser with active-preset chip preview + collapsible Advanced curation block (include / exclude by tag / category / name). Edits the same `default-tools-preference.json` as Tool Studio's drawer.
- **Tool Studio Draft state + MCP exposure gate**: a new or unverified tool sits in the **Drafts** section and is **not** registered with the built-in MCP server. A Local Pass flips the `McpToolDefinition` exposure flag and `ToolActivationCalculator` registers the callback with `McpSyncServer` live (no restart). Tool Studio sidebar surfaces Drafts and Local Pass panels separately.
- **`McpToolDefinition` record + `ToolManifest` envelope** for every published tool — manifest hash, code hash, runtime helpers list, sandbox risk level, audit timestamps, integrity hashes, optional signature.
- **Per-tool sandbox capability overrides** via `EffectivePolicyResolver` — `addAllowClasses`, `addAllowedHosts`, `egressLevel` (`strict` / `allowlist` / `custom` / `permissive`), `fileRead` / `fileWrite`, `fsBasePath`. Exposed in the **Sandbox & Capabilities** pane with a visible per-tool **Risk Level** badge (L0–L5) computed by `SandboxPostureCalculator`.
- **Tool Studio per-tool surface** — category-grouped sidebar (backed by `ToolCategoryCatalog` + `ChipListBinding`) with chip filters across cohort tags (`korea`, `example`, `util`, `pipeline`, `github`, `search`, `finance`, `weather`, `geo`).
- **Tier 1 built-in JavaScript helpers** (registered through `JsRuntimeGlobals`):
  - `fetch` with a 4-layer SSRF guard — scheme allowlist, literal-IP check, DNS resolve check, and per-tool egress policy. 5 redirect cap, 10 MB body cap, 30 s timeout, 5 s connect timeout. Restricted hop-by-hop headers stripped before sending; 303 redirects downgrade `POST → GET`. Response paging via `init.maxLength` / `init.startIndex`. Backed by `SafeHttpFetch`.
  - `URL`, `URLSearchParams`, `atob`, `btoa`, and `crypto.subtle` (digest / sign / verify / `getRandomValues`).
- **Tier 2 built-in JavaScript helpers**:
  - `safety.fs` — `readText`, `writeText`, `list`, `stat`, `grep`, `lineCount`, `slice`, `cut`, `sort`, `find`. All paths resolved against `tool-studio.fs.base-path` and rejected on escape. Backed by `SafeFs`.
  - `safety.parser.{html,yaml,csv,xml}` — strict, non-instantiating parsers (HTML via jsoup, XML via XXE-hardened `javax.xml.parsers`, YAML via SnakeYAML, CSV via Apache Commons CSV) exposed without leaking host classes to user code.
- **`JsHelperException`** for structured helper errors (security / invalid-input / helper-runtime) plus an executor-level error classifier so JS-side failures surface consistently on both the Test Run path and the MCP path.
- **Tool execution observability** — per-execution `tool.exec.start` / `tool.exec.done` / `tool.exec.crash` log lines with correlation id, risk level, capability summary, allowed hosts, fs base path, masked params, and `durationMs`. Env-backed static-variable values are masked from the params summary before logging.
- **Spring AI MCP client capability surface** expanded with an in-memory notification store and loopback safety so the embedded tool MCP server can co-host with the loopback `spring-ai-playground-tool-mcp` client on the same JVM without race conditions.

### Changed

- **Default sandbox posture is now deny-first**:
  - `allow-network-io` defaults to `false` (was `true`).
  - `allow-classes` shrunk to pure-compute packages only (`java.lang/math/time/util/text.*`). `java.net.*`, `java.io.*`, `java.net.http.*`, `org.jsoup.*` are no longer reachable directly.
  - New `deny-classes` list (evaluated before allow-classes; deny always wins) covers `System`, `Runtime`, `ProcessBuilder`, `Process`, `Class`, `java.lang.invoke.*`, `java.lang.reflect.*`, `Thread`, `ThreadGroup`, `ClassLoader`, `ServiceLoader`, `java.util.spi.*` — closing reflection / SPI / process-spawn escape vectors.
  - `tool-studio.fs.base-path` introduced for `safety.fs`, defaulting to `${TOOL_STUDIO_FS_BASE:${user.home}}`.
- `default-tool-location` moved under `spring.ai.playground` and switched to wildcard `classpath*:default-tool-specs*.json` so the bundled catalog can ship as multiple JSON files without code changes.
- Tool taxonomy refactored to a tool-centric category model (`ToolCategoryCatalog`, `ToolCategory` taxonomy). Sidebar grouped by category and filterable with chips.
- Tool studio view extracted (`SandboxCapabilitiesView`, `ToolMcpServerSettingView`) and polished alongside Electron window chrome and chat send affordances.
- `JsToolExecutor` and its helpers moved to `service/tool/runtime/`; policy code lives under `service/tool/policy/`.
- Env-var fail-fast hoisted into Java (drop JS-side `ensureResolved`) so a tool with an unresolved `${ENV_VAR}` placeholder fails at registration / load time instead of inside the JS sandbox.
- Strict network mode now rehydrates sandbox overrides on tool edit so capability changes (e.g. switching `egressLevel` from `strict` to `allowlist`) are reflected in the next test run without restarting Tool Studio.

### Fixed

- Surface JS errors on the MCP path consistently with the Test Run path; correctly materialise `Map` / `List` parameters from MCP JSON.
- Scope **Manually exposed tools** combo in the Tool MCP Server Setting drawer to custom-authored tools only — the 86 bundled default tools are managed through the Preset / Advanced curation block above and no longer leak into this combo.

### Security

- Network access from tool JS goes through a single reviewable choke point (`fetch` → `SafeHttpFetch`). Strict mode rejects loopback / link-local / site-local / any-local / multicast / IPv6 ULA / CGNAT addresses both as literals and after DNS resolution.
- `safety.fs` enforces a base-path jail; any path that resolves outside `tool-studio.fs.base-path` is rejected before any I/O.
- Environment-backed static variables are masked in `console.log` output, in the Test Run debug pane, and in `tool.exec.*` observability log lines.
- `deny-classes` evaluation order (deny before allow) closes reflection / `invoke` / `ClassLoader` / `ServiceLoader` / `Thread` / `Process` escape vectors that a permissive allow-list could otherwise re-open per tool.

### Documentation

- Docs site restructured to a per-page surface: **Features** (`tool-studio`, `mcp-server`, `vector-database`, `agentic-chat`) and **Tutorials** (1–8 individually) replace the previous monolithic `features.md` / `tutorials.md`.
- New **Default Tools** reference set under `docs/features/default-tools/` — index + Examples / Utilities / Filesystem / Global / Korea pages, each with per-tool cards (params, env vars, sandbox risk level, JS source), a Keys & secrets footer (issuance URLs for every required key including the eight-service `data.go.kr` keychain), and shared composition-pattern guidance.
- New **AI Agent Tool Safety Architecture** page (`docs/safety-architecture.md`) — defense-in-depth sandbox model, policy resolution, per-execution enforcement, Risk Level decision matrix, threat-to-layer mapping, known limitations, configuration reference.
- New **Tutorial 8: Default Tool Recipes** walks composition patterns (search → summarise, fetch → notify, time + calendar, cross-exchange spread, disaster → Slack, etc.) end-to-end.
- README and Home updated for the 86-default-tool blurb, version refs `M4` → `M6`, and the Default MCP Tools curation entry-point.

## [0.2.0-M5] - 2026-05-06

### Added

- OAuth 2.1 Authorization Code support per MCP server: dedicated sub-form (Client ID, Issuer URI, Scopes, Advanced overrides, Authorize button), browser-based consent via the system default browser, AWAITING_AUTHORIZATION state, Home dashboard awaiting-auth counter, and an encrypted disk-persisted token store under `~/spring-ai-playground/mcp/oauth-tokens/` with host-specific salt + `user.home` key derivation and transparent refresh.
- Per-server HTTP headers with `${ENV_VAR}` placeholder substitution (resolved at connect time from OS environment with JVM system-property fallback). Auth header presets — Bearer Token, Basic Auth, API Key Header — drop a templated row into the Headers section. The same `${VAR}` syntax also applies to STDIO `env` values and `requiredEnv` lists.
- MCP Inspector expansion to eight tabs: Tools (refactored), Resources (with templates), Prompts, Ping, Notifications, Roots, Sampling, Elicitation — server-side primitives and client-side primitives both surfaced for development and debugging.
- MCP server view redesign with per-server status indicators (OK / OFFLINE / ERROR sidebar dots), a Test Connection button that spins up a transient sync client without touching the live connection, full-width Tools cards with JSON-Schema-typed inputs and tool-annotation badges (read-only / destructive / idempotent / open-world), an inline per-card result panel (REQUEST / RESPONSE / Raw JSON-RPC toggle, Copy with toast feedback, dismiss), and a search filter.
- `mcp-stdio` Spring profile that switches the embedded MCP server to stdio transport while keeping the Vaadin Inspector on port 8282 in the same process. Opt-in via `SPRING_PROFILES_INCLUDE=mcp-stdio` (preserves the default `ollama` profile so model config still applies). Logback detaches the CONSOLE appender in this mode so stdout stays a clean JSON-RPC channel; rolling-file logs at `~/spring-ai-playground/logs/` are unaffected.
- Container repackaging with a custom jlink JRE (~70 MB vs ~380 MB full JDK; Truffle/JS support intact for the JavaScript tool sandbox), dual-mode build (CI-built fat JAR fast path or local `mvn package` fallback driven by `.dockerignore`), `debian:bookworm-slim` runtime base, and an `io.modelcontextprotocol.server.name` OCI label for MCP Registry ownership verification.
- Unified release pipeline (`.github/workflows/release.yml`) builds the fat JAR once and shares it across both the Docker image matrix (native `linux/amd64` + `ubuntu-24.04-arm` runners, no QEMU emulation) and the desktop installer matrix (mac arm64/Intel, Windows NSIS, Linux DEB/RPM). The GitHub Release is atomic across both tracks, with SHA-256 checksums and Sigstore SLSA build provenance attestations.
- Home sidebar links to Discussions and the bug-report flow.

### Changed

- MCP client capability surface expanded with an in-memory notification store. When the embedded server runs in stdio mode, the loopback `updateDefaultMcpTool` flow short-circuits to avoid attempting a streamable-http connection to its own non-HTTP endpoint.
- CI workflow housekeeping: `ci.yml` cancels duplicate runs from push + pull-request event pairs via a concurrency group; `actions/checkout` bumped to v5 on the docs-site workflow; greeter workflow's `repo-token` renamed to `repo_token` to match `actions/first-interaction@v3`.
- PWA install popup no longer auto-shows on the first web visit; installation remains available through the browser's normal install affordance.

## [0.2.0-M4] - 2026-04-27

### Added

- New adaptive Home dashboard with workspace status, provider health pill, recent activity, and a Getting Started checklist that mirrors the first-run flow.
- Agentic Chat surfaces RAG search options (query, filter, top-K, similarity threshold) and emits a structured retrieval-completion event so the UI can show retrieved doc count and titles.
- Agentic Chat anchors the new prompt at the top of the viewport on submit, labels each step with timing/counts (`RAG · {time} · {N docs} · {titles}`, `THINK · {time}`, `MCP TOOLS · {time} · {N calls} · {names}`), preserves the partial assistant response when the user clicks Stop, and shows a Stopped/Error stream-status badge that persists across reload.
- Common workspace shell shared by Chat, MCP, Tool Studio, and Vector Database surfaces (sidebar + content header + settings drawer) for consistent layout.
- Telemetry opt-out (`SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED=false`) now applies uniformly across the web app and every desktop launcher window (splash, server-splash, config editor, Ollama manager).
- Release assets ship with SHA-256 checksums (`.sha256`) and Sigstore build provenance attestations, verifiable via `gh attestation verify --owner spring-ai-community`.
- Documentation site generates per-page Open Graph / Twitter social cards (1200×630) and embeds `SoftwareApplication` JSON-LD schema for richer search results.
- Getting Started page now hosts a platform-aware download confirm popup that resolves the latest release through the GitHub API, shows filename, size, and the typical OS download path; README and the docs home redirect platform badges to the same popup.

### Changed

- Persistence rewritten to save on mutation through an async executor with atomic writes; `SimpleVectorStore` dumps are debounced to coalesce bursts into a single write, keeping disk-write threads non-daemon so in-flight writes finish on shutdown.
- Generic `RuntimeException` wrapping replaced with targeted error handling; MCP startup uses sequential per-server iteration with try/catch in place of `parallelStream` to isolate failures.
- Standardized desktop installer filenames to `spring-ai-playground-<version>-<platform>-<arch>.<ext>` (no spaces, version included). Bumped to `0.2.0-M4`. CI now resolves the installer version from the branch/tag ref and injects it into `electron/package.json` at build time.
- Linux `.deb` / `.rpm` package metadata renamed from `spring-ai-playground-desktop` to `spring-ai-playground` for consistency with the bundle ID, binary, and user-visible app name.
- Architecture documentation split into a dedicated page; README and mkdocs nav aligned with the new Home dashboard.

### Fixed

- `APP_HOME` now defaults to `<user.home>/<app-name>` when the JVM `user.home` property is unset, preventing log/config write failures in containerized environments.
- Loopback MCP client and embedded MCP server are closed before Tomcat graceful shutdown so the JVM exits cleanly.
- Saved persistence files are deleted immediately on removal instead of being deferred to JVM exit, eliminating stale files after a crash.
- MCP client map keys scoped per-server, stale clients closed on update, and self-update flows allowed without orphaning the previous client.
- Chat history reload restores `tool_calls` and matching tool responses correctly.
- Renamed `lancher-*` documentation assets to `launcher-*` (typo fix).

### Security

- Persisted file names are sanitized to prevent path traversal when writing under the application home directory.

## [0.2.0-M3] - 2026-04-18

### Added

- Shared community CI workflow for build verification across branches and pull requests.
- Desktop launcher support for downloading, reviewing, copying, retrying, and deleting Ollama models from a dedicated model manager window.
- Desktop launcher guidance screenshots covering the config editor, startup flow, and Ollama model manager.

### Changed

- Expanded the desktop launcher documentation in the README and getting started guide, including platform-specific install notes and launcher walkthroughs.
- Improved launcher setup UX with clearer Ollama status information, installed-model visibility, and a more capable configuration flow.
- Limited desktop and container release workflows to `main` pushes and version tags, while keeping the general CI workflow available on every branch.

### Fixed

- Corrected invalid launcher config template YAML structure for Spring AI settings.
- Improved Electron launcher startup and splash behavior, including more consistent Spring server shutdown handling across platforms.
