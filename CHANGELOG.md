# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
