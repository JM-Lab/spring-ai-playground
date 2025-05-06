# Spring AI Playground

![Spring AI Playground Demo](spring-ai-playground.gif)

**Spring AI Playground** is a self-hosted web UI designed to simplify AI experimentation, integration, and testing. 
It provides Java developers with an intuitive interface to experiment with large language models (LLMs), vector 
databases, and prompt engineering.

Inspired by popular LLM playgrounds, Spring AI Playground leverages the power of **Spring AI** to support leading AI model providers.
It also includes tools for testing retrieval-augmented generation (RAG) workflows and other advanced AI capabilities.
The goal is to make AI more accessible to Java developers, helping them quickly prototype and build **Spring AI-based applications**.

## Quick Start

Build and run the app:
```
./mvnw clean install
./mvnw spring-boot:run
```
Open http://localhost:8080 in your browser.

## Auto-configurations

Spring AI Playground is configured to use **Ollama** by default for local LLM and embedding models. No API keys are 
required, which makes it easy to get started.

## AI Models
To enable Ollama, ensure it is installed and running on your system. Refer to the [Spring AI Ollama Chat Prerequisites](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_prerequisites) for setup details.

### Support for All Major AI Model Providers
Spring AI Playground supports all major AI model providers, including Anthropic, OpenAI, Microsoft, Amazon, Google, and Ollama. For more details on the available implementations, visit the [Spring AI Chat Models Reference Documentation](https://docs.spring.io/spring-ai/reference/api/chatmodel.html#_available_implementations).

### Switching to OpenAI

Switching to **OpenAI** is a primary example of how you can use a different AI model with Spring AI Playground. To explore other models supported by Spring AI, learn more in the [Spring AI Documentation](https://spring.io/projects/spring-ai).

To switch to OpenAI, follow these steps:

1. **Modify the [`pom.xml`](./pom.xml) file**:
   
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

2. **Update [`application.yaml`](./src/main/resources/application.yaml)**:  

   - Update the following configuration to set OpenAI as the default profile:
     ```yaml 
     spring:
       profiles:
         default: openai
       ai:
         openai:
           api-key: your-openai-api-key
     ```
## Vector Database

![Spring AI Vector Database Playground Demo](spring-ai-playground-vectordb.gif)

**Spring AI Playground** offers a comprehensive vector database playground with advanced retrieval capabilities powered by Spring AI's VectorStore API integration.

- **Multi-Provider Testing**: Switch between vector database providers without code changes
- **Syntax Standardization**: Query different databases using Spring AI's unified interface

### Support for All Major Vector Database Providers
[Vector Database providers](https://docs.spring.io/spring-ai/reference/api/vectordbs.html#_vectorstore_implementations) including Apache Cassandra, Azure Cosmos DB, Azure Vector Search, Chroma, Elasticsearch, GemFire, MariaDB, Milvus, MongoDB Atlas, Neo4j, OpenSearch, Oracle, PostgreSQL/PGVector, Pinecone, Qdrant, Redis, SAP Hana, Typesense and Weaviate.

### Vector Database Playground Features

- **Custom Chunk Input**: Directly input and chunk custom text for embedding, allowing detailed RAG pipeline testing.
- **Document Uploads**: Upload files such as **PDFs, Word documents, and PowerPoint presentations**, and benefit from an end-to-end process of **text extraction → chunking → embedding**.
- **Search and Scoring**: Perform vector similarity searches and visualize results with **accuracy scores (0-1)** for easy evaluation.
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

5. **Review and Refine**:
    - Examine the generated responses, which now incorporate information from your vector database
    - Adjust your document selection or refine your queries to further improve the quality and relevance of the responses

This seamless integration enables developers to quickly prototype and optimize knowledge-enhanced AI interactions within a single, intuitive interface-bringing the power of Retrieval-Augmented Generation to your Spring AI applications.

## Upcoming Features

Here are some features we are planning to develop for future releases of Spring AI Playground:

- **MCP (Model Context Protocol)**:  
  A user interface implementing Spring AI's Model Context Protocol for both client and server sides. This feature will provide a visual way to manage context between AI models and applications, allowing developers to configure, monitor, and debug model context flows with an intuitive interface.

- **Observability**:  
  Introducing tools to track and monitor AI performance, usage, and errors for better management and debugging.

- **Authentication**:  
  Implementing login and security features to control access to the Spring AI Playground.

- **Multimodal Support**:  
  Supporting embedding, image, audio, and moderation models from Spring AI

These features will help make Spring AI Playground even better for testing and building AI projects.