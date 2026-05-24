# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Local speech-to-text mic in Agentic Chat — captures voice input and runs whisper.cpp locally via an Electron Node addon (no cloud round-trip). Whisper model selection and download surface in the desktop launcher's config editor and startup splash.
- **Modular RAG pipeline studio** in Vector Database — composable ETL pipeline editor for reader / chunker / pre-retrieval / retrieval / post-retrieval stages, reworked chunk-confirmation dialog UX, Name + Description fields on documents, and an internal rename of `VectorStoreDocumentService` → `OfflineEtlPipelineService`.

## [0.2.0-M7]

### Added

- **Developer-facing Observability dashboards** — Micrometer-backed `ObservabilityCollector` consumes `ChatClientObservation`, `gen_ai.client.operation`, MCP tool callbacks, sandbox metrics, and JVM samplers with MDC propagation (`conv`, `msg`, `traceId`, `spanId`); aggregates into an in-memory ring buffer (2,000 traces, configurable via `spring.ai.playground.observability.*`) plus dated disk persistence with 30-day retention (`~/spring-ai-playground/observability/<YYYY-MM-DD>/<traceId>.json`); surfaces 12 dashboard tabs via Vaadin sidebar — Overview, Tokens & Cost, AI Models, Tool Studio, MCP Servers, MCP Inspector, Vector Database, Agentic Chat, Host, Web Application, Logs, Traces — backed by `BucketedTimeSeries` + `BoundedRingBuffer` and ECharts panels. Recent activity table drills into Trace Detail Dialog (timeline, spans with raw attributes, raw JSON) and Conversation Thread Dialog (USER/ASSISTANT messages with KPIs). Deep-link via `/observability?tab=<slug>&trace=<traceId>` and the "Continue in chat" button lands the conversation back in Agentic Chat. `ObservationRegistry` wired into `ToolCallingManager` and `SimpleVectorStore` so tool / vector spans appear alongside chat client spans.
- **Vendor-official remote MCP server catalog (49 entries)** — curated list of remote MCP servers (`Gmail`, `Outlook-Mail`, `Notion`, `Slack`, `Microsoft-Teams`, `GitHub`, `Linear`, `Atlassian`, `Tavily`, `Exa`, `Firecrawl`, `Sentry`, `Asana`, `HubSpot`, `Mixpanel`, `Figma`, `Canva`, `Webflow`, ...) grouped into Productivity / CRM / Design / DEV / Search / Utility / Example categories in a left-side filter sidebar. Each entry pre-fills the connection JSON with `${ENV_VAR}` placeholders (e.g. `${GITHUB_PERSONAL_ACCESS_TOKEN}`, `${MS_TENANT_ID}`) and surfaces its preview / community / free-tier tags. Activation gates on env-var presence so disabled servers can't be turned on without setup.
- **OS-split stdio MCP catalog** — `default-mcp-specs-stdio-{mac,windows,linux}.json` (8 entries each) loaded per-platform, with `[macOS]` / `[Windows]` / `[Linux]` prefix in description. Tag suggestions derive dynamically from existing entries so new connections auto-complete from the live catalog. Tier 1 (built-in) + Tier 2 (catalog) = 57 inactive entries plus the built-in `spring-ai-playground-tool-mcp` connection (58 total visible on the MCP page).
- **MCP secret masking + tool call logging** — `SecretMasking.mask()` extracts `${ENV_VAR}` references from connection templates, resolves their values via `EnvVarResolver`, and redacts any value ≥ 4 characters from `mcp.tool.crash` error logs (replaced with `***`). `LoggingMcpToolCallback` wraps every MCP tool callback with `mcp.tool.start` / `mcp.tool.done` / `mcp.tool.crash` log lines carrying an 8-character correlation id (`cid`), server name, tool name, duration ms, and `via=chat` channel marker — feeds the Observability MCP / Tools tabs.
- **Electron launch hardening** — `fs.existsSync` validation of the bundled JRE path (`process.resourcesPath/jre-bundle/bin/java[.exe]`) with explicit fatal-error message pointing to the `electron/scripts/prepare-resources.mjs` step. Telemetry env (`SPRING_AI_PLAYGROUND_TELEMETRY_ENABLED`) propagated from the Electron main process into the spawned Java via `buildSpawnArguments` (entire `process.env` merged into `spawn` env) and into the splash / config editor / Ollama manager / server splash windows via `?telemetry=0` query string. `launchReadinessState` machine (`idle → starting → ready / failed / timedOut`) feeds the splash with stage messages.

### Changed

- **Shared sidebar widgets extracted** — `SidebarSection`, `SidebarItem`, and `PageSidebar` factored out as reusable components (`refactor(ui)`). Tool Studio, MCP Server, Vector Database, Agentic Chat, and the Observability dashboards now share the same look and interaction (bolded active item, group headers, count chips). The MCP Inspector header also picks up the status indicator + active counter.
- **Tool resources regrouped under `tool/`** — bundled tool catalog JSON files (`default-tool-specs*.json`), categories, presets, and workspace-samples moved into `src/main/resources/tool/` (`refactor(resources)`). The wildcard pattern (`classpath*:tool/default-tool-specs*.json`) at `default-tool-location` continues to load all six bundles.
- **MCP startup spawn deduplicated and Inspector polished** — removed the duplicate MCP client startup spawn and consolidated the sidebar / Inspector header into a single entry point (`fix(mcp)`).

### Fixed

- **Tool Studio empty optional testValue no longer breaks Test Run** — an optional parameter with an empty `testValue` used to throw at Test Run time; the regression is fixed alongside JSHint editor cleanup and KR locale tidy (`fix(tool-studio)`).
- **Persistence boot NPE** — added a `shouldLoadFile(Path)` hook on `PersistenceServiceInterface` so the default `loads()` path skips `.tmp`, `.tmp.json`, and `.deprecated` files plus foreign JSON files (e.g. `default-tools-preference.json`). `ToolSpecPersistenceService.shouldLoadFile` further narrows to exactly `toolSpecsMcpSetting`. Closes the boot-time NPE from bad deserialization (`fix(persistence)`).
- **Catalog seed test alignment** — `McpCatalogServiceTest.tierSplitMatchesCatalogSeed` updated to tier2 size `30 to 39` and the new `MS_TENANT_ID` placeholder reflecting the catalog expansion (`fix(mcp)`).
- **Style polish** — `@Test` methods unified to camelCase, FQN dropped in `PersistenceService`, sidebar active items rendered bold (`fix(style)`).

### Documentation

- **Observability feature pages** — new `docs/features/observability/` directory: `overview.md`, `ai-usage/{index, tokens-cost, ai-models}.md`, `ai-stack/{index, tool-studio, mcp-servers, mcp-inspector, vector-database, agentic-chat}.md`, and `runtime/{index, host, web-application, logs, traces}.md` (14 pages). Each page covers KPI / chart descriptions, full-page screenshots, and drill-down scenarios.
- **Default MCP Catalog feature pages** — `docs/features/default-mcp-catalog/` split per category (`business.md`, `data-cloud.md`, `dev.md`, `examples.md`, `productivity.md`, `search.md`, `index.md`).
- **MCP Inspector feature page** — new `docs/features/mcp-server/inspector.md` covering all 8 inspector tabs (Tools / Resources / Prompts / Ping / Notifications / Roots / Sampling / Elicitation) with screenshots.
- **Safe Tool Specification reference** — `docs/safe-tool-specification.md` plus `docs/safe-tool-spec.schema.json` (JSON Schema for tool spec authoring).
- **Tutorial 9: MCP-Everything walkthrough** — end-to-end from `@modelcontextprotocol/server-everything` activation through every Inspector tab.
- **Tool Studio docs promoted** — `docs/features/tool-studio.md` is now `docs/features/tool-studio/index.md` (directory layout).
- **Observability architecture overview** — `docs/observability-architecture.md` walks the collector → ring buffer → time series → dashboard flow.
- **Mobile nav polish** — pre-paint flicker guard CSS in `docs-overrides/main.html`, depth-1 auto-expand via `docs/assets/javascripts/nav-default-expand.js`, and `navigation.instant` + `navigation.instant.progress` features enabled in `mkdocs.yml`.

### Build / Tooling

- **Spring AI checkstyle baseline adopted** — `maven-checkstyle-plugin 3.6.0` + `checkstyle 10.21.0` + `spring-javaformat-checkstyle 0.0.47` plugin block added to `pom.xml`, bound to the `verify` phase with `failOnViolation=true`. `src/checkstyle/checkstyle.xml` mirrors the Spring AI baseline with project-specific deltas (4-space indent kept; `JavadocPackage`, `SpringLambdaCheck`, `NeedBracesCheck`, `InnerTypeLastCheck` removed; `RightCurlyCheck option=same`; `WhitespaceAround` empty-* options relaxed; `AvoidStaticImport` excludes extended with internal helpers). The license header regex is inlined as a `header` property so the same config works in IntelliJ Checkstyle without extra path setup. `src/checkstyle/checkstyle-suppressions.xml` excludes 131 M6 baseline files, the OllamaChatModel upstream patch, and 5 files introduced by commits already pushed to `origin/main` — incremental adoption: only new and modified files participate in the scan.
- **CI verify** — `.github/workflows/ci.yml` `maven-goals` flipped from `clean compile -B` to `clean verify -B` (skip-tests unchanged), so checkstyle runs on every push and PR.
- **Spring AI bumped to 1.1.6** — `chore: bump to 0.2.0-M7 with spring-ai 1.1.6`.

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
