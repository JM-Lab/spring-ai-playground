description: Agentic Chat — one runtime combining documents, tools, models, and conversation state. RAG chains and MCP tool-driven agents in one chat surface.

# Agentic Chat

Agentic Chat is the unified runtime where Spring AI Playground combines documents, tools, models, and conversation state.

![Agentic Chat session — model conversation with inline tool-call traces, MCP tool selector, document context drawer, and streamed assistant response](../assets/images/chat-mcp.gif)

This unified interface lets you:

- run RAG workflows grounded in indexed documents
- execute tool-enabled agent flows through MCP
- test complete agent strategies by combining documents and tools in a single chat session

## Key Features

- document selection for RAG grounding
- MCP connection selection for tool-enabled execution
- real-time visibility into retrieved context and tool usage
- one conversational surface for both chain-style and agentic patterns

This area is closely aligned with Spring AI's workflow and agentic guidance. If you want the conceptual background behind these two modes, see [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html).

## Two Integrated Paradigms

### 1. RAG: Knowledge via Chain Workflow

When documents are selected, Agentic Chat follows a deterministic retrieval pattern:

- retrieval from the vector store
- prompt augmentation with grounded context
- response generation based on that context

### 2. MCP: Actions via Agentic Reasoning

When MCP connections are enabled, Agentic Chat can behave like an agent:

- reasoning about which tools are needed
- invoking tools through MCP
- observing the result
- continuing or answering directly

## Workflow Integration

The intended end-to-end flow is:

1. prepare tools in Tool Studio or connect them in MCP Server
2. prepare knowledge in Vector Database
3. enable the relevant documents and MCP connections in Agentic Chat
4. send a request and observe how retrieval and tool use combine

This is the place where the rest of the product becomes visible as one coherent system rather than separate screens. The outputs of Tool Studio, MCP Server, and Vector Database all converge here.

## Requirements for Agentic Reasoning

Basic chat can work with any supported provider. Tool-enabled agentic behavior works best with models that support function calling and stronger reasoning.

For Ollama-based flows:

- use tool-capable models from [Ollama's Tool Category](https://ollama.com/search?c=tools)
- use reasoning-capable models from [Ollama's Thinking Category](https://ollama.com/search?c=thinking)
- validate tools in MCP Inspector before relying on them in Agentic Chat

The default `playground.chat.models` list features `qwen3.5:2b` (default) plus `qwen3.5:9b` / `qwen3.6:35b` for stronger tool-oriented reasoning, with `gemma4:e4b`, `gpt-oss:20b`, and `deepseek-r1:8b` as alternatives. See [Picking a Model](../tutorials/index.md#picking-a-model) in the Tutorials for the tradeoffs.

## Agentic Chat Architecture Overview

The diagram below is included as a conceptual reference to the related agentic systems material in the Spring AI docs.

It is included here to explain how the Playground's Agentic Chat maps onto the broader Spring AI mental model. In this project, the diagram is not describing a separate product feature hidden behind the UI. It is a conceptual reference for understanding how the Playground combines model reasoning, retrieval, tool execution, and memory in one chat runtime.

![Spring AI Agentic System Structure](https://raw.githubusercontent.com/spring-io/spring-io-static/refs/heads/main/blog/tzolov/spring-ai-agentic-systems.jpg)

If you want the fuller conceptual background, start with [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html). That reference explains the workflow-versus-agent distinction that this Playground makes concrete through Tool Studio, MCP Server, Vector Database, and Agentic Chat.

This Chat experience facilitates exploration of Spring AI's workflow and agentic paradigms, empowering developers to build AI systems that combine chain-based RAG workflows with agentic, tool-augmented reasoning. In practice, it follows Spring AI's Agentic Systems architecture, where grounded retrieval and dynamic tool execution coexist in one context-aware chat runtime.

| Component | Type | Description | Configuration Location | Key Benefits | Model Requirements |
| --- | --- | --- | --- | --- | --- |
| **LLM** | Core Model | Executes chain-based workflows and performs agentic reasoning for tool usage within a unified chat runtime. | Agentic Chat | Central reasoning and response generation; supports both deterministic workflows and agentic patterns. | Chat models; tool-aware and reasoning-capable models recommended. |
| **Retrieval (RAG)** | Chain Workflow | Deterministic retrieval and prompt augmentation using vector search over selected documents. | Vector Database | Predictable, controllable knowledge grounding; tunable retrieval parameters such as Top-K and thresholds. | Standard chat plus embedding models. |
| **Tools (MCP)** | Agentic Execution | Dynamic tool selection and invocation via MCP, driven by LLM reasoning and tool schemas. | Tool Studio, MCP Server | Enables external actions, multi-step reasoning, and adaptive behavior. | Tool-enabled models with function calling and reasoning support. |
| **Memory** | Shared Agentic State | Sliding window conversation memory shared across workflows and agents through `MessageChatMemoryAdvisor` and the underlying Spring AI chat memory support. | Spring AI chat runtime (`MessageWindowChatMemory` over `InMemoryChatMemoryRepository`, last 10 messages) | Coherent multi-turn dialogue with a sliding window improves coherence, planning, and tool usage quality. | Models benefit from longer context and structured reasoning. |

By leveraging these elements, Agentic Chat goes beyond basic Q&A and becomes a practical environment for building effective, modular AI applications that combine workflow predictability with agentic autonomy.

## What the Chat can reach

Agentic Chat is a **consumer** of three inventories curated elsewhere in the Playground. Use these references to know what's available before composing a chat session:

- **[Default Tools](default-tools/index.md)** — 86 pre-loaded built-in tools (Examples · Utilities · Filesystem · Global · Korea) callable directly from chat without any external setup. Each carries a Risk Level (L0–L5) and `${ENV_VAR}` requirements per page.
- **[Default MCP Catalog](default-mcp-catalog/index.md)** — 57 preset external MCP server connections (Gmail, Notion, GitHub, Linear, BigQuery, Stripe, …). One-click activation from the MCP Server sidebar adds them as tool sources for chat.
- **[Vector Database](vector-database.md)** — indexed document collections that the **RAG advisor chain** retrieves from at chat time (`SpringAiPlaygroundRagAdvisor` short-circuits when no documents are selected, so retrieval is opt-in per conversation).

→ Try it: [Tutorials](../tutorials/index.md) — end-to-end flows that combine Tool Studio, MCP Inspector, Vector Database, and Agentic Chat.
