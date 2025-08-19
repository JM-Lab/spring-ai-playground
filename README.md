# Spring AI Playground

**Spring AI Playground** is a self-hosted web UI that simplifies AI experimentation and testing. It provides Java developers with an intuitive interface for working with **large language models (LLMs)**, **vector databases**, **prompt engineering**, and **Model Context Protocol (MCP)** integrations.

Built on **Spring AI**, it supports leading model providers and includes comprehensive tools for testing **retrieval-augmented generation (RAG)** workflows and MCP integrations. The goal is to make AI more accessible to developers, helping them quickly prototype **Spring AI-based applications** with enhanced contextual awareness and external tool capabilities.

## Table of Contents
- [Quick Start](#quick-start)
   - [Prerequisites](#prerequisites)
   - [Getting Started](#getting-started)
   - [Running the Application](#running-the-application)
      - [Running with Docker (Recommended)](#running-with-docker-recommended)
      - [Cleaning Up Docker](#cleaning-up-docker)
      - [Running Locally (Optional)](#running-locally-optional)
   - [PWA Installation](#pwa-installation)
- [Auto-configuration](#auto-configuration)
- [AI Models](#ai-models)
   - [Support for Major AI Model Providers](#support-for-major-ai-model-providers)
   - [Selecting and Configuring Ollama Models](#selecting-and-configuring-ollama-models)
   - [Switching to OpenAI](#switching-to-openai)
- [MCP (Model Context Protocol) Playground](#mcp-model-context-protocol-playground)
   - [Key Features](#key-features)
   - [Getting Started with MCP](#getting-started-with-mcp)
- [Chat Using MCP](#chat-using-mcp)
   - [Ollama Tool-Enabled Models](#ollama-tool-enabled-models)
- [Vector Database](#vector-database)
   - [Support for Major Vector Database Providers](#support-for-major-vector-database-providers)
   - [Vector Database Playground Features](#vector-database-playground-features)
- [Chat Using RAG](#chat-using-rag)
- [Upcoming Features](#upcoming-features)
   - [Spring AI Agent](#spring-ai-agent)
   - [Observability](#observability)
   - [Authentication](#authentication)
   - [Multimodal Support](#multimodal-support)

## Quick Start
### Prerequisites
- Java 21 or later installed (required for building the project).
- Ollama running on your machine (refer to AI Models).
- Docker installed and running on your machine. (only if you choose to run the application using Docker)

### Getting Started
First, clone the Spring AI Playground project from GitHub:
```
git clone https://github.com/JM-Lab/spring-ai-playground.git
cd spring-ai-playground
```

### Running the Application
#### Running with Docker (Recommended)
1. Build the Docker Image:
   ```
   ./mvnw spring-boot:build-image -Pproduction -DskipTests=true \
   -Dspring-boot.build-image.env.RUN_IMAGE_USER=spring \
   -Dspring-boot.build-image.imageName=jmlab/spring-ai-playground:latest
   ```
2. Run the Docker Container:
   ```
   docker run -d -p 8282:8282 --name spring-ai-playground \
   -e SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434 \
   -v spring-ai-playground:/home/spring/spring-ai-playground \
   --restart unless-stopped \
   jmlab/spring-ai-playground:latest
   ```
3. Access the Application:
   Open http://localhost:8282 in your browser.

> ***Notes:***
>- Data Persistence: Application data is stored in the spring-ai-playground Docker volume, ensuring data persists even if the container is removed.
>- Ollama Connection: The environment variable SPRING_AI_OLLAMA_BASE_URL is set to http://host.docker.internal:11434. Adjust the URL if Ollama runs on a different host or port.
>- Automatic Restart: The --restart unless-stopped option ensures the container restarts automatically unless manually stopped with docker stop.
> - ***For Linux Users:*** The `host.docker.internal` DNS name may not be available on all Linux distributions. If you encounter connection issues, you may need to use `--network="host"` in your `docker run` command or replace `host.docker.internal` with your host machine's IP address on the Docker bridge network (e.g., `172.17.0.1`).

#### Cleaning Up Docker
- To stop and remove the Docker container, image, and volume:
   ```
   docker stop spring-ai-playground
   docker rm spring-ai-playground
   docker rmi jmlab/spring-ai-playground:latest
   docker volume rm spring-ai-playground
   ```

#### Running Locally (Optional)
1. **Build and Run the Application**:
   ```
   ./mvnw clean install -Pproduction -DskipTests=true
   ./mvnw spring-boot:run
   ```
2. **Access the Application**:
   Open `http://localhost:8282` in your browser.

### PWA Installation

> **Note**: Complete either the Docker or Local installation steps above before proceeding with PWA installation.

**Spring AI Playground** comes with Progressive Web App (PWA) capabilities, allowing you to install it as a standalone application on your device for a native app-like experience.

#### Installing as PWA

1. Open the application in your web browser at `http://localhost:8282`
2. Install using one of the following methods:
    - Browser PWA Install Popup: Most modern browsers will automatically show a PWA installation popup or prompt in the address bar
    - Install PWA Button: Look for the "Install PWA" button on the application's home page and click it
3. Follow the installation wizard to complete the setup and add the app to your device

## Auto-configuration

Spring AI Playground uses Ollama by default for local LLM and embedding models. No API keys are 
required, which makes it easy to get started.

## AI Models
To enable Ollama, ensure it is installed and running on your system. Refer to the [Spring AI Ollama Chat Prerequisites](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_prerequisites) for setup details.

### Support for Major AI Model Providers
Spring AI Playground supports all major AI model providers, including Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama. For more details on the available implementations, visit the [Spring AI Chat Models Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_available_implementations).

### Selecting and Configuring Ollama Models
When running Spring AI Playground with the **`ollama`** profile, you can configure the default chat and embedding models, as well as the list of available models in the playground UI, by updating your configuration file (`application.yaml`).
> **Notes:**
>- `pull-model-strategy: when_missing` ensures that the configured models are automatically pulled from Ollama if they are not already available locally.
>- `playground.chat.models` controls which models appear in the model selection dropdown in the web UI.
>- Changing the `chat.options.model` or `embedding.options.model` here updates the defaults used by the application.

> **Pre‑pull Recommended Ollama Models** to avoid delays when first using a model, pre-pull it with Ollama before starting Spring AI Playground.

### Switching to OpenAI

Switching to **OpenAI** is a primary example of how you can use a different AI model with Spring AI Playground. To explore other models supported by Spring AI, learn more in the [Spring AI Documentation](https://spring.io/projects/spring-ai).

To switch to OpenAI, follow these steps:

- **Modify the [`pom.xml`](./pom.xml) file**:
   
   - Remove the Ollama dependency:
     ```xml
     <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
     </dependency>
     ```

   - Add the OpenAI dependency:
     ```xml
     <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
     </dependency>
     ```

- **Update [`application.yaml`](./src/main/resources/application.yaml)**:  

   - Update the following configuration to set OpenAI as the default profile:
     ```yaml 
     spring:
       profiles:
         default: openai
       ai:
         openai:
           api-key: your-openai-api-key
     ```

## MCP (Model Context Protocol) Playground

![Spring AI MCP Playground Demo](spring-ai-playground-mcp.png)

Spring AI Playground now includes a comprehensive **MCP (Model Context Protocol) Playground** that provides a visual 
interface for managing connections to external tools through AI models. This feature leverages Spring AI's Model Context Protocol implementation to offer client-side capabilities.

### Key Features

- **Connection Management**: Configure and manage MCP connections with multiple transport types including STREAMABLE HTTP, STDIO, and SSE (Server-Sent Events).
- **Server Configuration**: Configure connections to MCP servers with customizable names, descriptions, and connection parameters.
- **MCP Inspector**: Explore available tools and their capabilities with detailed information including:
    - Tool names and descriptions
    - Required arguments and parameters
    - Action definitions and specifications
- **Interactive Tool Testing**: Execute MCP tools directly from the playground with real-time results and execution history.

> **Note**: **STREAMABLE HTTP** officially introduced in the MCP v2025‑03‑26 specification (March 26, 2025) — is a 
> single-endpoint HTTP transport that replaces the former HTTP+SSE setup. Clients send JSON‑RPC via POST to /mcp, while responses may optionally use an SSE-style stream, with session‑ID tracking and resumable connections.

### Getting Started with MCP

1. **Configure MCP Server Connection**:
    - Access the MCP Playground from the main interface
    - Set up your MCP server connection with the appropriate transport type and connection details

2. **Explore Available Tools**:
    - Use the MCP Inspector to browse available tools and their specifications
    - Review tool descriptions, required arguments, and expected parameters
    - Understand the capabilities of your MCP server connection setup

3. **Test Tool Execution**:
    - Select tools from the inspector and execute them with appropriate arguments
    - Monitor execution results and review the execution history
    - Debug and refine your MCP integration based on real-time feedback

This MCP Playground provides developers with a powerful visual tool for prototyping, testing, and debugging Model Context Protocol integrations, making it easier to build sophisticated AI applications with contextual awareness.

## Chat Using MCP

![Spring AI Chat Using MCP Demo](spring-ai-playground-chat-mcp.gif)

Spring AI Playground now provides seamless integration with MCP (Model Context Protocol) tools directly within the chat interface, enabling you to enhance AI conversations with external tools. Here's how you can leverage this powerful feature:

1. **Set Up Your MCP Connections**:
- First, configure your MCP servers through the MCP Playground interface.
- Define your MCP connections with appropriate transport types (STREAMABLE HTTP, STDIO, or SSE).
- Set up server configurations including names, descriptions, and connection parameters.
- Test your tools in the MCP Inspector to ensure they're working correctly.

2. **Select MCP Connections in the Chat Page**:
- Choose one or more MCP connections from the dropdown menu to enable tool access for your conversations.
- Only the selected MCP connections are available for the AI to use during the chat session. If no connections are selected, MCP tools will not be accessible.

3. **Send a Message**:
- Enter your prompts in the chat input, and request actions that require external tools or capabilities.
- The AI model will automatically determine when to use available MCP tools based on your conversation context and requirements.
- Tools will be executed automatically, and their results will be integrated into the AI's responses.

4. **Review and Refine**:
- Examine the generated responses, which now incorporate information and actions from your MCP tools.
- Adjust your MCP connection selection or refine your tool configurations to further improve the functionality and relevance of the responses.
- Monitor tool execution and results to optimize your MCP integration.

### Ollama Tool-Enabled Models

> **⚠️ Important for Ollama Users**  
> When using Ollama as your AI provider, ensure you're using a **tool-enabled model** that supports external function calling. Not all Ollama models support MCP tool integration.

#### How to Verify Tool Support

1. **Check Model Compatibility**: Visit the [Ollama Models page](https://ollama.com/search?c=tools) and filter by "Tools" category
2. **Pull the Model**: Ensure you have the latest version using `ollama pull <model-name>`
3. **Test in MCP Playground**: Use the MCP Inspector to verify tool functionality before chat integration

> **Tip**  
> Models like **OpenAI GPT-OSS**, **Qwen 3**, and **DeepSeek-R1** offer advanced reasoning capabilities with visible thought processes, making them particularly effective for complex MCP tool workflows.

This integration enables developers to quickly prototype and test tool-enhanced AI interactions, bringing the power of external systems and capabilities directly into your Spring AI conversations through the Model Context Protocol.

## Vector Database

![Spring AI Vector Database Playground Demo](spring-ai-playground-vectordb.gif)

**Spring AI Playground** offers a comprehensive vector database playground with advanced retrieval capabilities powered by Spring AI's VectorStore API integration.

- **Multi-Provider Testing**: Switch between vector database providers without code changes
- **Syntax Standardization**: Query different databases using Spring AI's unified interface

### Support for Major Vector Database Providers
[Vector Database providers](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_vectorstore_implementations) including Apache Cassandra, Azure Cosmos DB, Azure Vector Search, Chroma, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, SAP Hana, Typesense and Weaviate.

### Vector Database Playground Features

- **Custom Chunk Input**: Directly input and chunk custom text for embedding, allowing detailed RAG pipeline testing.
- **Document Uploads**: Upload files such as **PDFs, Word documents, and PowerPoint presentations**, and benefit from an end-to-end process of **text extraction → chunking → embedding**.
- **Search and Scoring**: Perform vector similarity searches and visualize results with **similarity scores (0-1)** for easy evaluation.
- **Spring AI Filter Expressions**: Utilize metadata-based filtering (e.g., `author == 'John' && year >= 2023`) to narrow search scopes and refine query results.

These features, combined with Spring AI's flexibility, provide a comprehensive playground for vector database testing and advanced integration into your applications.

## Chat Using RAG

Spring AI Playground now offers a fully integrated RAG (Retrieval-Augmented Generation) feature, allowing you to enhance AI responses with knowledge from your own documents. Here’s how you can make the most of this capability:

1. **Set Up Your Vector Database**:
    - First, upload your documents (PDFs, Word, PowerPoint, etc.) through the Vector Database Playground.
    - The system extracts text, splits it into chunks, and generates vector embeddings for semantic search.
    - You have full control over your data and can add, remove, or modify individual chunks to improve retrieval results.
    - Additionally, you can configure search options such as similarity thresholds and the Top K value (the number of top matching chunks to retrieve), allowing you to further tailor how relevant information is selected during retrieval.

2. **Select Documents in the Chat Page**:
    - Choose one or more documents from the vector database to define the knowledge base for responses.
    - Only the selected documents are filtered and used as the knowledge source for RAG. If no documents are selected, RAG will not be performed.

3. **Send a Message**:
    - Enter your prompts in the chat input
    - The system retrieves the most relevant content from your selected documents and uses it to generate a contextual, knowledge-grounded response.

4. **Review and Refine**:
    - Examine the generated responses, which now incorporate information from your vector database
    - Adjust your document selection or refine your queries to further improve the quality and relevance of the responses

This seamless integration enables developers to quickly prototype and optimize knowledge-enhanced AI interactions within a single, intuitive interface-bringing the power of Retrieval-Augmented Generation to your Spring AI applications.

## Upcoming Features

Here are some features we are planning to develop for future releases of Spring AI Playground:

### Spring AI Agent

Build production-ready AI Agents by combining Model Context Protocol (MCP) for external tool integration, Retrieval-Augmented Generation (RAG) for knowledge retrieval, and Chat for natural interaction — all inside a single, unified workflow.

Inspired by the [Effective Agents patterns from Spring AI](https://docs.spring.io/spring-ai/reference/api/effective-agents.html#_best_practices_and_recommendations), developers can:

1. **Prototype** agents in Spring AI Playground with MCP tools, vector DBs, and RAG-enhanced chat
2. **Refine** agent logic, contextual memory, and tool orchestration
3. **Export** configurations directly to Spring AI Agent YAML/Java settings
4. **Build & Package** as Docker-ready Spring AI Agent applications
5. **Deploy** seamlessly from local experimentation to production environments
6. **Test** deployed Spring AI Agents directly from the Playground UI using interactive Chat

This streamlined approach enables going from concept to tested, cloud-ready deployment with minimal friction.

### Observability

Introducing tools to track and monitor AI performance, usage, and errors for better management and debugging.

### Authentication

Implementing login and security features to control access to the Spring AI Playground.

### Multimodal Support

Supporting embedding, image, audio, and moderation models from Spring AI.


These features will help make Spring AI Playground even better for testing and building AI projects.