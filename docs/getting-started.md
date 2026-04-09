Description: Install Spring AI Playground, configure Ollama or OpenAI, choose a runtime, and launch the desktop app, Docker image, or local source build.

# Getting Started

Spring AI Playground is best introduced through the desktop app. The desktop launcher gives you the easiest installation path, a built-in configuration editor, provider starter templates, secure environment-variable handling, and a one-click launch flow for the bundled runtime.

This page starts with the desktop app because that is the default installation experience. Docker and direct source execution are still supported and documented here as alternative runtimes.

## Desktop App First

The recommended default is the desktop build published through GitHub Releases.

### Download the Desktop Installer

Open the [Spring AI Playground Releases](https://github.com/spring-ai-community/spring-ai-playground/releases) page and install the package for your operating system:

- macOS Apple Silicon: DMG for `arm64`
- macOS Intel: DMG for `x64`
- Windows: NSIS installer
- Linux: DEB or RPM package

The desktop package wraps the launcher and the backend runtime together, so this is the simplest way to get started without manually running Docker or Maven.

On macOS, if you install from a DMG, drag the app into **Applications** before launching it. Do not run it directly from the mounted DMG, and eject the DMG after copying.

If macOS still blocks launch because the app is quarantined, and you trust the app, one practical open-source distribution workaround is:

```bash
xattr -dr com.apple.quarantine "/Applications/Spring AI Playground.app"
```

### What the Desktop App Gives You

The desktop launcher includes a built-in configuration editor. In practical terms, that means:

- provider-specific starter settings for Ollama, OpenAI, and OpenAI-compatible servers
- YAML override editing instead of forcing you to edit the full bundled config
- environment-variable entry for API keys and tool secrets
- JVM options and application arguments for launch-time tuning
- import, export, save-as, delete, factory reset, and save-and-launch workflows

### How the Desktop Config Works

The launcher does not expose the entire built-in config directly. Instead, the editor shows only the override YAML for the selected setting, and at launch that override is merged on top of the bundled default configuration.

That behavior is reflected in the desktop UI:

- selected config and setup notes
- provider type selector: `Ollama`, `OpenAI`, `OpenAI-Compatible`
- saved setting selection and `Save As`
- environment-variable management
- JVM settings
- `Save and Launch`

This makes it much easier to keep multiple clean launch profiles without hand-managing full runtime configuration files.

## Prerequisites

To fully experience the local-first capabilities of Spring AI Playground, the following are required:

### Ollama

- Download and install [Ollama](https://ollama.com/) on your machine.
- Run `ollama serve` or ensure the Ollama app is running.
- For prerequisite details, see the [Spring AI Ollama Chat Prerequisites](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_prerequisites).

Recommended models to pre-pull:

```bash
ollama pull qwen3.5
ollama pull qwen3-embedding:0.6b
```

### Docker

- Install Docker and make sure it is running if you plan to use the container runtime.

### Optional: Java 21+ and Git

- Only required if you plan to build from source.

### If You Do Not Want to Use Ollama

If you switch to the `OpenAI` setting, Ollama is not required at startup.

In that case, provide `OPENAI_API_KEY` in the desktop app Environment Variables section and launch with the OpenAI setting.

For OpenAI-compatible settings, whether Ollama is still required depends on the selected backend and whether embeddings still use Ollama.

## Desktop Configuration Walkthrough

The desktop configuration editor is one of the most important parts of the product experience.

### 1. Choose a Config Type

The launcher offers starter templates for:

- `Ollama`
- `OpenAI`
- `OpenAI Compatible - Ollama`
- `OpenAI Compatible - llama.cpp`
- `OpenAI Compatible - TabbyAPI`
- `OpenAI Compatible - LM Studio`
- `OpenAI Compatible - vLLM`

### 2. Select or Clone a Saved Setting

The config editor supports:

- choosing a saved setting
- creating a variation with `Save As`
- deleting a setting you no longer need
- importing and exporting reusable launcher settings
- resetting to factory defaults

### 3. Edit Only the Override YAML

The YAML editor is intentionally scoped to override content, not the full base file. The selected YAML is merged on top of the built-in default configuration at launch.

That design keeps the common configuration flow simpler:

- keep a stable bundled default
- store only what differs for this setting
- switch between clean launch profiles quickly

### 4. Add Environment Variables Instead of Hardcoding Secrets

The desktop launcher is designed so API keys and runtime secrets stay out of YAML whenever possible.

The Environment Variables section is where you enter values such as:

- `OPENAI_API_KEY`
- tool-related variables like `GOOGLE_API_KEY`, `PSE_ID`, `SLACK_WEBHOOK_URL`
- any custom environment variable needed by a backend or tool

The launcher behavior is important here:

- variables are stored by the launcher per saved setting
- they are exported only for the app launch process
- they are not meant to be written into the YAML override
- config export intentionally excludes local secret values for safety

### 5. Understand Secret Storage

The desktop app uses Electron `safeStorage` when OS-backed secure encryption is available.

That means:

- secrets are encrypted at rest when the operating system secure storage is available
- the UI tells the user whether encrypted storage is active
- if OS-backed encryption is unavailable, the launcher falls back and indicates that status in the UI

So the practical behavior is:

- on supported systems, desktop secrets are encrypted by the OS-integrated secure storage layer
- regardless of encryption mode, the launcher exports them only into the app launch process instead of asking you to hardcode them in YAML

### 6. Set JVM and App Args Only When Needed

The desktop editor also includes:

- JVM options such as `-Xmx2g`
- application args such as `--logging.level.root=INFO`

These are launch-time settings, not provider secrets.

### 7. Save and Launch

Once the selected setting looks right:

1. save the setting
2. verify the required environment variables
3. make sure Ollama is running when the setting requires it
4. click `Save and Launch`

## Running the Application

### Desktop App

This is the recommended default runtime for most users.

1. Download the installer from GitHub Releases.
2. Install it like a normal desktop application.
3. Choose a launcher setting.
4. Save and launch.

### Docker

Docker is a strong option when you want a server-style deployment instead of the desktop launcher.

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  -v spring-ai-playground:/home \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Notes:

- application data is stored in the `spring-ai-playground` Docker volume
- the container expects to reach Ollama at `http://host.docker.internal:11434`
- on Linux, `host.docker.internal` may not resolve, so you may need host networking or a bridge IP such as `172.17.0.1`
- the `--restart unless-stopped` option keeps the container available after restarts

Docker is not suitable for MCP STDIO transport testing because STDIO-based MCP depends on direct process-to-process communication.

### Local Source Run

Use a local source run when you need development workflows or MCP STDIO transport features.

```bash
git clone https://github.com/spring-ai-community/spring-ai-playground.git
cd spring-ai-playground
./mvnw clean install -Pproduction -DskipTests=true
./mvnw spring-boot:run
```

Then open `http://localhost:8282`.

## PWA Installation

If you are running the browser-based version instead of the desktop installer, Spring AI Playground can also be installed as a Progressive Web App.

Complete either the Docker or local source setup first so the app is already available in the browser.

1. Open the application in your browser at `http://localhost:8282`.
2. Install it using the browser install prompt or the install option shown on the home page.
3. Complete the installation flow to add it as an app-like experience.

## Auto-configuration

Spring AI Playground uses Ollama by default for local chat and embedding models. No API key is required for that default setup, which makes the initial local-first experience straightforward.

## Model Configuration

Spring AI Playground is provider-agnostic, but the runtime defaults are intentionally optimized for a local-first Ollama experience.

### Support for Major AI Model Providers

Spring AI as a framework supports many providers, including Ollama, OpenAI, Anthropic, Microsoft, Amazon, Google, and other integrations.

For the broader list of officially supported chat model integrations, see the [Spring AI Chat Models Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_available_implementations).

Spring AI Playground itself is currently centered on these runtime paths:

- Ollama
- OpenAI
- OpenAI-compatible servers

In the desktop app, OpenAI-compatible support is mainly provided through starter templates and YAML override configuration rather than a larger first-class provider matrix.

If you want to use other Spring AI provider integrations, that is not part of the default desktop app flow. In practice, you would need to modify the source dependencies and configuration, then build and run your own customized version.

### Selecting and Configuring Ollama Models

The default profile is `ollama`, and the default setup uses Ollama for both chat and embeddings.

The current default model choices are:

- chat model: `qwen3.5`
- embedding model: `qwen3-embedding:0.6b`
- selectable chat models: `gpt-oss`, `qwen3.5`, `qwen3`

Important notes:

- missing Ollama models are automatically pulled when needed
- the selectable chat model list controls what appears in the Playground model selector
- changing the chat or embedding model changes the runtime defaults used by the application

### Switching to OpenAI

To switch to OpenAI:

1. use the `OpenAI` setting in the desktop launcher, or activate the `openai` profile in another runtime
2. provide `OPENAI_API_KEY`
3. launch the application with that setting

If you want a broader overview of supported Spring AI provider options beyond the default Playground flows, see the main [Spring AI Documentation](https://spring.io/projects/spring-ai).

Desktop launcher:

- set `OPENAI_API_KEY` in the Environment Variables section
- launch the `OpenAI` setting

Docker:

```bash
docker run -d -p 8282:8282 --name spring-ai-playground \
  -e SPRING_PROFILES_ACTIVE=openai \
  -e OPENAI_API_KEY=your-openai-api-key \
  -v spring-ai-playground:/home \
  --restart unless-stopped \
  ghcr.io/spring-ai-community/spring-ai-playground:latest
```

Unix/macOS source run:

```bash
export OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

Windows source run:

```bash
set OPENAI_API_KEY=your-openai-api-key
./mvnw spring-boot:run --spring.profiles.active=openai
```

### Switching to OpenAI-Compatible Servers

You can also connect to OpenAI-compatible servers such as `llama.cpp`, `TabbyAPI`, `LM Studio`, `vLLM`, `Ollama`, or others that expose OpenAI-compatible endpoints.

Typical configuration points are:

- `api-key`: a real key if the server requires authentication, otherwise a placeholder like `not-used`
- `base-url`: the server root endpoint, often including `/v1`
- `model`: the exact model name registered on that server
- `completions-path`: only override this if the server does not follow the standard OpenAI chat completions path
- `extra-body`: optional provider-specific parameters
- `http-headers`: optional custom authentication or transport headers
- streaming support: works when the target server supports OpenAI-style streaming responses
- token controls: use `maxTokens` for standard models or `maxCompletionTokens` for reasoning-style models, but avoid setting both

Quick example using Ollama in OpenAI-compatible mode:

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:11434/v1"
      chat:
        options:
          model: "llama3.2"
```

`llama.cpp`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:8080/v1"
      chat:
        options:
          model: "your-model-name"
          extra-body:
            top_k: 40
            repetition_penalty: 1.1
```

`TabbyAPI`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "your-tabby-key"
      base-url: "http://localhost:5000/v1"
      chat:
        options:
          model: "your-exllama-model"
          extra-body:
            top_p: 0.95
```

`LM Studio`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:1234/v1"
      chat:
        options:
          model: "your-loaded-model"
          extra-body:
            num_predict: 100
```

`vLLM`

```yaml
spring:
  ai:
    model:
      chat: openai-sdk
      embedding: ollama
    openai-sdk:
      api-key: "not-used"
      base-url: "http://localhost:8000/v1"
      chat:
        options:
          model: "meta-llama/Llama-3-8B-Instruct"
          extra-body:
            top_p: 0.95
            repetition_penalty: 1.1
```

For best compatibility, make sure the target server supports OpenAI-style endpoints and model listing.

In practice, it is worth testing the target with a `/v1/models` request first so you can confirm the exact model names and endpoint shape before launching the app against it.

For the complete Spring AI OpenAI chat configuration model, see the [Spring AI OpenAI Chat Documentation](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).

### Important RAG Note

If you change the embedding model after documents have already been indexed, existing vector data can become inconsistent. Re-import or rebuild the vector database before trusting retrieval results again.

## Built-in MCP Endpoint

No matter whether you run the app through the desktop launcher, Docker, or direct source execution, the built-in MCP endpoint is exposed at:

```text
http://localhost:8282/mcp
```

That endpoint is central to Tool Studio, MCP Inspector, and Agentic Chat with tools.

## Next Step

After the app is running and the model backend is configured:

1. read [Features](features.md) to understand the product structure
2. follow [Tutorials](tutorials.md) to create tools, connect MCP servers, register knowledge, and run Agentic Chat with tools and RAG

## Further Reading

- [Overview](index.md): see the product positioning, quick start path, and documentation map
- [Features](features.md): understand the architecture and the main product areas
- [Tutorials](tutorials.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
