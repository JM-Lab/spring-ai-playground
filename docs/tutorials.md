Description: Follow Spring AI Playground tutorials for Tool Studio, MCP servers, RAG, and Agentic Chat workflows using validated tools and grounded context.

# Tutorials

These tutorials follow the practical workflow of the product rather than treating each screen as isolated demos. The goal is to guide you from creating capabilities to using them in Agentic Chat.

## Before You Start

Complete the setup in [Getting Started](getting-started.md) first.

For the most reliable experience:

- launch the desktop app with a working provider setting
- make sure Ollama is running if your setting depends on it
- confirm plain chat works before testing tools or RAG

## Tutorial 1: Create and Publish a Tool in Tool Studio

This tutorial introduces the main low-code tool-authoring workflow.

### Goal

Understand the built-in example tools, create or adapt a tool, test it, publish it through the built-in MCP server, and validate it through MCP Inspector.

### What Tool Studio Is Best At

Tool Studio is especially useful for wrapping existing REST APIs into simple MCP-callable tools.

That is one of the most practical uses of the product:

- define a small set of structured parameters
- call an external HTTP API from JavaScript
- return a compact JSON result
- expose that result immediately through MCP

This makes it easy to turn an existing HTTP integration into an AI-usable tool without building a separate application layer first.

### Built-in Example Tools

Tool Studio already includes several example tools that show the main patterns:

- `googlePseSearch`: search the web using Google Programmable Search Engine
- `extractPageContent`: fetch and clean the main text from a web page
- `buildGoogleCalendarCreateLink`: generate a Google Calendar event creation URL
- `sendSlackMessage`: send a message through a Slack webhook
- `openaiResponseGenerator`: call OpenAI and return a generated response
- `getWeather`: fetch a compact weather summary
- `getCurrentTime`: return the current time in ISO format

These examples are useful because they demonstrate:

- clear tool naming
- structured parameter design
- static variables for configuration
- compact JavaScript actions
- JSON-friendly outputs for downstream agent use

### JavaScript Runtime

Tool actions are implemented in **JavaScript (ECMAScript 2023)** executed inside the JVM through the GraalVM Polyglot runtime.

- **Runtime**: JavaScript (ECMAScript 2023) inside the JVM
- **Java interop**: controlled through configuration and whitelist-based access
- **Sandboxing**: unsafe operations such as file I/O and native access are restricted by design, so tool code should stay small and deterministic

Security-related characteristics of the default setup include:

- network I/O allowed for HTTP-based tool integrations
- file I/O blocked
- native access blocked
- thread creation blocked
- explicit allowed-class configuration

Typical allowed-class examples include:

- `java.lang.*`
- `java.math.*`
- `java.time.*`
- `java.util.*`
- `java.text.*`
- `java.net.*`
- `java.io.*`
- `java.net.http.HttpClient`
- `java.net.http.HttpRequest`
- `java.net.http.HttpResponse`
- `java.net.http.HttpHeaders`
- `org.jsoup.*`

This balance is what makes Tool Studio practical for low-code API wrappers while still keeping the runtime constrained.

If you want the full runtime background, including MCP security and GraalVM sandbox references, the detailed explanation lives on the [Features](features.md#javascript-runtime) page.

### Recommended Starting Point

Use one of these built-in examples as your base:

- `getCurrentTime`
- `getWeather`
- `extractPageContent`

Then either test it as-is to understand the flow or copy it and adapt it to your own use case.

### Steps

1. Open **Tool Studio**.
2. Select a built-in tool from the tool list.
3. Review the tool name, tool description, parameters, static variables, and JavaScript action.
4. Modify the tool or duplicate it with the copy workflow.
5. If you are creating a new tool, define a stable tool name and a clear description for model selection.
6. Add structured parameters instead of relying on one large free-form input whenever possible.
7. Add static variables for secrets or base URLs when needed.
8. Write a JavaScript action that returns a compact structured result.
9. Run **Test Run**.
10. Inspect the debug console, execution status, elapsed time, and result.
11. Click **Test & Update Tool**.

### What to Validate

- the JavaScript action runs successfully
- the returned JSON shape is usable
- the published tool appears on the built-in MCP server

### Writing Your Own Tool

When creating a tool directly, a strong pattern is:

1. define the request inputs as explicit parameters
2. keep secrets and configuration in static variables or environment variables
3. call the target HTTP API
4. normalize the response into a compact JSON object
5. test it before publishing

Good tools are usually:

- small
- deterministic
- explicit about inputs
- explicit about outputs
- easy for a model to select correctly

### Practical REST API Wrapper Pattern

If your goal is to wrap an existing REST API, a useful shape is:

1. use parameters for the request inputs the model should control
2. keep credentials and fixed endpoints in static variables
3. make the HTTP request from the JavaScript action
4. normalize the upstream response into a compact result object
5. return only the fields the model actually needs

### Secret-Backed Variables

If your tool requires external credentials, prefer environment-backed values instead of hardcoding them into the tool definition.

Typical built-in mappings are:

- `openaiResponseGenerator` uses `${OPENAI_API_KEY}`
- `googlePseSearch` uses `${GOOGLE_API_KEY}` and `${PSE_ID}`
- `sendSlackMessage` uses `${SLACK_WEBHOOK_URL}`

When using the desktop launcher, put those values in the Environment Variables section of the selected desktop setting.

### Validate the Tool Through the Built-in MCP Server

This validation is part of the same authoring flow.

1. Open **MCP Server**.
2. Select the built-in MCP connection if it is already listed, or connect to:

```text
http://localhost:8282/mcp
```

3. Open **MCP Inspector**.
4. Browse the available tools.
5. Select the tool you published from Tool Studio.
6. Review the schema and required arguments.
7. Execute the tool directly from the inspector.

Validate that:

- the tool is visible through MCP
- the schema matches your expectations
- the output is correct when invoked through the MCP layer, not just from Tool Studio

## Tutorial 2: Connect an External MCP Server

The Playground can also act as an MCP client for remote tools.

### Goal

Add an external MCP connection and verify that its tools can be inspected and executed.

### Steps

1. Open **MCP Server**.
2. Add a new server connection.
3. Choose the correct transport type: `Streamable HTTP`, `STDIO`, or legacy HTTP plus SSE when needed.
4. Fill in the server details.
5. Save the connection.
6. Open **MCP Inspector** and inspect the available tools.
7. Run one tool directly from the inspector.

### Client Examples

Common connection patterns include:

- Claude Code with Streamable HTTP
- Cursor with a Streamable HTTP MCP server entry
- Claude Desktop using `mcp-remote` when a proxy-style local process is more practical

If you plan to use those tools in Agentic Chat later, validate them here first rather than debugging them for the first time inside a live conversation.

### What to Validate

- the transport configuration is correct
- the connection initializes successfully
- argument schemas are understandable
- direct execution works before you rely on the tool in Agentic Chat

### Why This Step Matters

Agentic Chat is much easier to debug when tools have already been validated in MCP Inspector.

## Tutorial 3: Register Knowledge in Vector Database

This tutorial prepares the knowledge side of the product.

### Goal

Upload content, create embeddings, store vector data, and confirm retrieval quality.

### Steps

1. Open **Vector Database**.
2. Choose one input type: PDF, Word document, PowerPoint file, or custom text input.
3. Upload or paste the content.
4. Let the app complete text extraction, chunking, embedding, and vector storage.
5. Run a similarity search.
6. Inspect returned chunks, metadata, and similarity scores.

### What to Review Carefully

When validating the indexed result, pay attention to:

- whether chunk boundaries make sense
- whether similarity scores are good enough for your use case
- whether metadata is useful for later filtering
- whether filter expressions can narrow the search effectively

### What to Validate

- ingestion succeeds end to end
- the chunks are sensible for your content
- the selected embedding model produces usable retrieval results
- the search results are good enough to be trusted in chat

### Important Note

If you later change the embedding model, rebuild or re-import your indexed data before relying on the old vector contents.

## Tutorial 4: Agentic Chat With Tools

This is the first end-to-end tool-enabled workflow.

### Goal

Use MCP-connected tools from a reasoning-capable model during a chat conversation.

### Steps

1. Open **Agentic Chat**.
2. Enable the MCP connection you want to use.
3. Select a model that supports tool use and reasoning.
4. Send a prompt that should require a tool call.

Example:

```text
What time is it right now in ISO format?
```

If you published `getCurrentTime`, the built-in MCP connection is a good first validation path. External MCP connections work too when you have explicitly added and trusted them.

### What to Observe

- whether the model decides to use a tool
- whether the tool is actually executed
- whether the final answer reflects the tool output

### Ollama Guidance

For Ollama, tool-enabled agentic use works best with models that support both reasoning and function calling. Models such as Qwen 3 and GPT-OSS are strong candidates for this kind of workflow.

Helpful discovery links:

- [Ollama Tool Category](https://ollama.com/search?c=tools)
- [Ollama Thinking Category](https://ollama.com/search?c=thinking)

Before using a tool in Agentic Chat, validate that tool in MCP Inspector first.

## Tutorial 5: Agentic Chat With RAG

Now validate the knowledge-grounding workflow without introducing tools.

### Goal

Use indexed documents as grounded context in chat.

### Steps

1. Make sure your document is already indexed in **Vector Database**.
2. Open **Agentic Chat**.
3. Select one or more indexed documents as knowledge sources.
4. Ask a question that should be answerable from the indexed content.

Example:

```text
What are the three most important points in this document?
```

### What to Observe

- relevant chunks are retrieved from the vector store
- those chunks are added as prompt context
- the answer reflects the indexed knowledge rather than only the base model’s generic memory

### Good Validation Pattern

1. verify the document indexed correctly in Vector Database
2. run similarity search first
3. then test the same knowledge source in Agentic Chat

## Tutorial 6: Agentic Chat With Tools and RAG Together

This is the full product workflow.

### Goal

Use both knowledge grounding and MCP-based tool execution in a single conversation.

### Setup

Before starting, make sure you have:

- at least one validated tool from Tool Studio or an external MCP connection
- any external MCP connection you plan to use has already been added and trusted by you
- at least one indexed document in Vector Database

### Steps

1. Open **Agentic Chat**.
2. Enable the relevant MCP connections.
3. Enable the relevant indexed documents.
4. Select an appropriate reasoning-capable model.
5. Send a prompt that may need both retrieved knowledge and live tool execution.

### What to Observe

- RAG supplies grounded factual context
- tool calls happen only when needed
- the final answer reflects both retrieved knowledge and external action results

### Recommended Workflow Integration

1. develop tools in Tool Studio
2. validate them in MCP Server
3. register documents in Vector Database
4. compose documents and MCP connections in Agentic Chat
5. observe retrieval and tool execution together

### Why This Tutorial Is the Most Important

Spring AI Playground is designed around composition:

- Tool Studio creates capabilities
- MCP Server validates and manages them
- Vector Database prepares grounded knowledge
- Agentic Chat composes all of that into one working workflow

This final tutorial is where that architecture becomes visible to the user. It shows the intended balance of the product: deterministic knowledge grounding through RAG, dynamic action through MCP tools, and a shared chat runtime where both can be validated together.

## Tutorial 7: Weather to Slack — Your First Agentic Workflow

### Goal

Trigger a chain of two built-in tools in a single chat turn. This is the fastest way to see the agentic loop end to end without writing any code, and it is the canonical "Try an agentic workflow" task the Home screen checklist points at.

### Why Start Here

- Uses only pre-loaded built-in tools: `getWeather` and `sendSlackMessage`. No Tool Studio authoring required.
- Exercises the full agent path: plan → call tool A → read result → call tool B with that result → summarize.
- Works the moment the model provider is connected. Every tool has already earned its Local Pass and is live on the built-in MCP server.

### Prerequisites

- Provider pill on Home shows a green dot and "Ready" (Ollama reachable or OpenAI key set)
- `SLACK_WEBHOOK_URL` is set in the environment the app runs in, so `sendSlackMessage` can actually post. On the desktop launcher, add it under **Environment Variables**; when running from source or Docker, export it before launching.
- A tool-capable model (see Tutorial 4 for the recommended Ollama models)

### Steps

1. Open **Agentic Chat**.
2. Make sure the built-in MCP connection is enabled. The tools you need — `getWeather`, `sendSlackMessage` — will appear in the tool inventory.
3. Send this prompt, verbatim:

        Get today's weather for Seoul and send a short summary to Slack.

4. Watch the chat stream:
    - the assistant calls `getWeather` with your location
    - the tool returns a compact weather payload
    - the assistant reasons over the result and calls `sendSlackMessage` with a short natural-language summary
    - the final assistant turn confirms the Slack message was posted

5. Open Slack and verify the message actually landed in the target channel.

### What to Validate

- The assistant makes **two distinct tool calls in order**, not a single one.
- The Slack message content is derived from the weather tool's output — the agent is chaining, not guessing.
- If either tool fails, the failure shows up in the chat stream with the tool name and the error. That same failure would also have blocked the tool from earning its Local Pass — every tool you see here already passed its local test.

### Where to Go Next

- Swap `sendSlackMessage` for one of your own tools authored in Tool Studio. The moment it passes its test, it goes live on the built-in MCP server and becomes available to the assistant in the same way.
- Combine this flow with a RAG document (Tutorial 5) — ask the assistant to summarize a policy document and post the summary to Slack. That is the full Tool + RAG composition from Tutorial 6, phrased as a concrete task.

## Further Reading

- [Overview](index.md): return to the main product overview and documentation map
- [Getting Started](getting-started.md): review installation, runtime options, and provider configuration
- [Architecture](architecture.md): runtime layers, data flows, and extension points
- [Features](features.md): the main product areas in more detail
