# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Local speech-to-text mic in Agentic Chat — captures voice input and runs whisper.cpp locally via an Electron Node addon (no cloud round-trip). Whisper model selection and download surface in the desktop launcher's config editor and startup splash.

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
