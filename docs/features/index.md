description: Explore Spring AI Playground features: Tool Studio, MCP Server, Vector Database, Agentic Chat, Observability, and the safe local execution architecture.

# Features

Spring AI Playground is organized around five product surfaces, designed to be understood in this order. The first four are the author / consume surfaces; **Observability** is the operator surface that watches them.

<div class="grid cards" markdown>

-   :material-tools:{ .lg .middle } **[Tool Studio](tool-studio/index.md)**

    ---

    Low-code authoring environment for JavaScript-based tools.
    Deny-first sandbox, Draft state, MCP server preset catalog, per-tool sandbox capability overrides.

-   :material-connection:{ .lg .middle } **[MCP Server](mcp-server/index.md)**

    ---

    Built-in MCP server over Streamable HTTP, external connections via HTTP / SSE / STDIO / OAuth 2.1, a multi-tab Inspector for tools, resources, prompts, and client primitives, and a preset catalog of 57 preset MCP servers (49 vendor-official remote + 8 community stdio) activatable from the sidebar - see the [MCP Catalog directory](default-mcp-catalog/index.md).

-   :material-database-search:{ .lg .middle } **[Vector Database](vector-database.md)**

    ---

    Document ingestion, chunking, embedding, storage, and similarity search across Spring AI vector stores - the RAG validation surface.

-   :material-chat-processing:{ .lg .middle } **[Agentic Chat](agentic-chat/index.md)**

    ---

    Unified runtime that composes tools and RAG context in one conversational interface - chain workflows and agentic tool-use side by side.

-   :material-chart-line:{ .lg .middle } **[Observability](observability/index.md)**

    ---

    Fourteen in-app dashboards across four groups (AI Usage · AI Stack · Runtime · Overview) covering token economics, tool and MCP behaviour, RAG quality, host runtime, and a live trace tail - the operator surface that watches the other four.

</div>

The first four surfaces are intentionally connected. A tool authored in **Tool Studio** is exposed by the **built-in MCP server**, verified through the **MCP Inspector**, and consumed by **Agentic Chat** together with documents indexed in the **Vector Database** - without restart or redeploy at any step. Every chat turn, tool call, MCP exchange, and vector query is captured by **Observability** as it happens.

For a system-level view - runtime layers, data flows, and extension points behind these surfaces - see [Architecture](../architecture.md).

## Further Reading

- [Overview](../index.md): return to the main product overview and documentation map
- [Getting Started](../getting-started/index.md): install the app, configure providers, and choose a runtime
- [Architecture](../architecture.md): runtime layers, data flows, and extension points
- [Tutorials](../tutorials/index.md): follow end-to-end workflows for tools, MCP, vector search, and agentic chat
