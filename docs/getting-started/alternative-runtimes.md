title: Alternative Runtimes
description: Run Spring AI Playground via Docker container or direct source / fat-JAR execution - same Vaadin UI plus opt-in stdio mode for MCP clients like Claude Desktop.

# Alternative Runtimes

The desktop installer is the recommended path (see [Desktop App](desktop.md)). When you can't or don't want to install the desktop bundle - containerised deployment, development workflow, MCP stdio integration for Claude Desktop or Claude Code - Docker or a direct source / fat-JAR run is the alternative. Both expose the same Vaadin UI on `http://localhost:8282` and the same built-in MCP server; both honour the same `mcp-stdio` profile.

For universal post-install steps (Your First Five Tasks, model configuration, telemetry), see [Getting Started](index.md). For the **complete list of configuration knobs** - every property, environment variable, and default, and how to set it in each mode - see the [Configuration reference](configuration.md).

## When to use which

- **Docker** - your machine already runs Docker; you want process isolation; you do not have a Java toolchain installed and do not want to install one; or you want quick MCP stdio integration with Claude Desktop / Claude Code without touching the host.
- **From source** - you want to modify the Playground codebase, or you want the fat JAR for portable `java -jar` deployment, or you specifically want GraalVM for full-speed JavaScript sandboxing.

Both inherit the same Spring profiles and the same configuration surface - only the launcher differs.

## Prerequisites

- **Docker**: install Docker Desktop or Docker Engine and ensure it is running.
- **From source**: install Java 21+ (GraalVM recommended for full-speed JS sandbox) and Git.

## How distribution channels map to MCP transports

Every channel ships the same Spring Boot fat JAR. The default mode is the web app with a `streamable-http` MCP server on port 8282 - switching to a stdio MCP server is opt-in by adding the `mcp-stdio` profile.

| Mode | Profile setup | MCP transport | Channel |
|---|---|---|---|
| **App (web/desktop)** - default | (nothing extra) | `streamable-http` on port 8282 | DMG / EXE / DEB / RPM, web app, Docker, source `mvn spring-boot:run` |
| **MCP server (for Claude Desktop, Claude Code, IDEs, ...)** | `SPRING_PROFILES_INCLUDE=mcp-stdio` | `stdio` (process stdin/stdout) | Docker, or `java -jar` against the released JAR |

`SPRING_PROFILES_INCLUDE` (rather than `ACTIVE`) is what lets the stdio profile **layer on top of** the default profile - so model config like Ollama / OpenAI keeps applying. Setting `SPRING_PROFILES_ACTIVE=mcp-stdio` would *replace* the active list and disable the default `ollama` profile, which is rarely what you want.

In other words:

- The **desktop app and PWA** are the primary user-facing experience - full Vaadin UI, tool authoring, agentic chat. The built-in MCP server is reachable over `streamable-http` so the in-app Inspector and other HTTP clients can introspect it.
- The **stdio path** is the same product with the stdio profile layered in for direct MCP client integration. Docker handles it for non-Java users; the raw JAR handles it for Java developers and CI integrations.

The web UI on port 8282 keeps booting in stdio mode too, so a Docker user can both connect Claude Desktop over stdio and open the Inspector in a browser at the same time when they pass `-p 8282:8282`.

## Docker

The published container behaves like the desktop / source app by default - Vaadin UI on `http://localhost:8282` and the embedded MCP server speaking `streamable-http`. Stdio mode is one env var away (`SPRING_PROFILES_INCLUDE=mcp-stdio`).

### Use as a plain web app (default)

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/root \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Notes:

- application data is stored in the `spring-ai-playground` Docker volume
- the container expects to reach Ollama at `http://host.docker.internal:11434`
- on Linux, `host.docker.internal` may not resolve, so you may need host networking or a bridge IP such as `172.17.0.1`
- the `--restart unless-stopped` option keeps the container available after restarts

### Use as an MCP server (opt-in via env)

```bash
docker run -i --rm \
  -e SPRING_PROFILES_INCLUDE=mcp-stdio \
  -v spring-ai-playground:/root \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Add the same command to your MCP client config (Claude Desktop's `claude_desktop_config.json` shown):

```json
{
  "mcpServers": {
    "spring-ai-playground": {
      "command": "docker",
      "args": [
        "run", "-i", "--rm",
        "-e", "SPRING_PROFILES_INCLUDE=mcp-stdio",
        "-v", "spring-ai-playground:/root",
        "ghcr.io/spring-ai-community/spring-ai-playground:latest"
      ]
    }
  }
}
```

The `-v spring-ai-playground:/root` named volume keeps authored tools, saved tool selections, secrets, and the local vector store across restarts; without it everything resets when the container exits.

Add `-p 8282:8282` if you also want browser access to the Vaadin Inspector alongside the stdio channel - the web UI runs in the same process either way; the port mapping just exposes it. Pick a different host port (e.g. `-p 9000:8282`) if 8282 is in use.

The container ships with the gateway and authoring UI on. The **Starter 5** preset has no required credentials and works out of the box; other presets and individual catalog tools stay dormant until you supply the matching environment variables. Pass them with `-e NAME=value` flags on the same `docker run` line - the typical entries (`OPENAI_API_KEY`, `GOOGLE_API_KEY`, `GOOGLE_PSE_ID`, `SLACK_WEBHOOK_URL`, ...) are listed in [Desktop App → Use Environment Variables for Keys and Secrets](desktop.md#9-use-environment-variables-for-keys-and-secrets). The File Toolkit preset additionally honors `TOOL_STUDIO_FS_BASE` to set the base path for `safety.fs`; if unset it defaults to `$HOME/spring-ai-playground/workspace` inside the container (typically `/root/spring-ai-playground/workspace`).

The `mcp-stdio` profile silences the CONSOLE log appender so stdout stays a clean JSON-RPC channel; rolling-file logs at `~/spring-ai-playground/logs/` are unaffected.

## From Source

Use a local source run when you need development workflows or MCP STDIO transport features.

### Local development run

```bash
git clone https://github.com/spring-ai-community/spring-ai-playground.git
cd spring-ai-playground
./mvnw clean install -Pproduction -DskipTests=true
./mvnw spring-boot:run
```

Then open `http://localhost:8282`.

### Apple Silicon and MLX models { #apple-silicon-mlx }

On an Apple Silicon Mac (`arm64`), a source or `java -jar` run automatically layers a bundled **`mlx`** profile on top of the default `ollama` profile. The chat defaults and the chat model menu switch to Apple's MLX-optimized Ollama builds, which run noticeably faster on M-series hardware: the default chat model becomes `qwen3.5:4b-mlx` and `spring.ai.playground.chat.models` lists the `-mlx` builds.

An `EnvironmentPostProcessor` makes this decision at startup, gated on the OS (`mac`) and architecture (`arm64`). Intel Macs, Windows, Linux, and **Docker containers - which run Linux even on an Apple Silicon host** - are unaffected and use the generic model names as-is. You still pull the `-mlx` builds into Ollama yourself; `spring.ai.ollama.init.pull-model-strategy: when_missing` pulls the configured chat and embedding models on first start.

To keep the generic model names, opt out with `spring.ai.playground.ollama.mlx-auto-select=false`:

```bash
# fat JAR
java -jar spring-ai-playground-*.jar --spring.ai.playground.ollama.mlx-auto-select=false

# source run
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.ai.playground.ollama.mlx-auto-select=false"
```

The desktop launcher sets this same flag and resolves the MLX build a different way - it only upgrades to an `-mlx` build that is already installed locally. See [Desktop App → Apple Silicon and MLX models](desktop.md#apple-silicon-and-mlx-models).

### Use as an MCP server from the fat JAR

Java developers who already have JDK 21+ on the machine can connect their MCP client straight at the Spring Boot fat JAR - no Docker, no Vaadin dev mode. The same `mcp-stdio` profile that powers the container is portable to any `java -jar` launch.

Pick the JAR up in one of two ways:

- Download `spring-ai-playground-<version>.jar` from the [Releases page](https://github.com/spring-ai-community/spring-ai-playground/releases) (uploaded alongside the desktop installers; verify with the matching `.sha256` if you want).
- Or build it yourself from a source clone:
  ```bash
  ./mvnw -Pproduction -DskipTests package
  # produces target/spring-ai-playground-*.jar
  ```

Then launch it with the stdio profile included on top of the default:

```bash
SPRING_PROFILES_INCLUDE=mcp-stdio java -jar spring-ai-playground-*.jar
```

`SPRING_PROFILES_INCLUDE` adds `mcp-stdio` to the active profile list without replacing the default `ollama` profile - so model config keeps applying. Use `--spring.profiles.include=mcp-stdio` as a CLI alternative if you'd rather not rely on the env var.

For Claude Desktop, point `claude_desktop_config.json` at the absolute JAR path:

```json
{
  "mcpServers": {
    "spring-ai-playground": {
      "command": "java",
      "args": [
        "-jar",
        "/absolute/path/to/spring-ai-playground-0.2.0-M7.jar",
        "--spring.profiles.include=mcp-stdio"
      ]
    }
  }
}
```

Notes:

- the JVM must be Java 21 or newer (preferably GraalVM, so the JavaScript tool sandbox runs at full speed; on a non-GraalVM JVM the `-Dpolyglot.engine.WarnInterpreterOnly=false` flag falls back to interpreter mode automatically).
- the same caveats as the container apply: stdout is the MCP JSON-RPC channel, so the `mcp-stdio` profile silences the CONSOLE log appender. Rolling logs still land at `~/spring-ai-playground/logs/`.
- the Vaadin Inspector also boots on `http://localhost:8282` by default, so the same JAR doubles as the authoring UI. Set `SERVER_PORT=0` if you want a random port (or just don't open the browser).

## Switching to OpenAI

To switch from the default Ollama profile to OpenAI on the alternative runtimes, provide `OPENAI_API_KEY` and activate the `openai` profile. The desktop launcher path is documented separately under [Desktop App → Use Environment Variables for Keys and Secrets](desktop.md#9-use-environment-variables-for-keys-and-secrets).

### Docker

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_PROFILES_ACTIVE=openai \
  -e OPENAI_API_KEY=your-openai-api-key \
  -v spring-ai-playground:/root \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

### From source - Unix / macOS

```bash
export OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

### From source - Windows

```bash
set OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

For OpenAI-compatible servers and the YAML overrides each one expects, see [External Connections → OpenAI-compatible servers](external-connections.md#switching-to-openai-compatible-servers).

## Further Reading

- [Getting Started](index.md) - universal post-install steps, model configuration, telemetry
- [Configuration](configuration.md) - every property / env var / default, and how to set it per launch mode
- [Desktop App](desktop.md) - recommended path with installer + configuration walkthrough
- [Features → MCP Server](../features/mcp-server/index.md) - what the built-in MCP server does, the Inspector, the external catalog
- [Tutorials](../tutorials/index.md) - end-to-end workflows
