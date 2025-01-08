# Spring AI Playground

![Spring AI Playground Demo](spring-ai-playground.gif)

**Spring AI Playground** is a self-hosted web UI designed to simplify AI experimentation, integration, and testing. It offers Java developers an easy way to explore and use large language models (LLMs), vector databases, and prompts.

Built with inspiration from various LLM Playgrounds, it uses the power of **Spring AI** to support major AI model
providers. The goal is to make AI more accessible to Java developers, helping them quickly prototype and build
Spring AI-powered applications.

## Quick Start

Build and run the app:
```
./mvnw clean install
./mvnw spring-boot:run
```
Open http://localhost:8080 in your browser.

## Auto-configurations

Spring AI Playground is configured to use **Ollama** by default for local LLM and embedding models. No API keys are
required, making it easy to get started.

To enable Ollama, ensure it is installed and running on your system. Refer to
the [Spring AI Ollama Chat Prerequisites](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html#_prerequisites)
for setup details.

### Switching to OpenAI

Switching to **OpenAI** is a primary example of how you can use a different AI model with Spring AI Playground. To explore other models supported by Spring AI, learn more in the [Spring AI Documentation](https://spring.io/projects/spring-ai).

To switch to OpenAI, follow these steps:

1. **Modify the [`pom.xml`](./pom.xml) file**:  
   
   - **Remove the Ollama dependency**:
     ```xml
     <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
     </dependency>
     ```

   - **Add the OpenAI dependency**:
     ```xml
     <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
     </dependency>
     ```

   2. **Update [`application.yaml`](./src/main/resources/application.yaml)**:  

      - **Update the following configuration to set OpenAI as the default profile:**
      ```yaml
      spring:
        profiles:
          default: openai
        ai:
          openai:
            api-key: your-openai-api-key
      ```

### Upcoming Features

Here are some features we are planning to develop for future releases of Spring AI Playground:

- **Vector Databases**:  
  Adding support for vector databases to enhance data storage and search capabilities for AI tasks.

- **RAG Chat (Retrieval-Augmented Generation)**:  
  Improving AI chat by using external data to provide more accurate and relevant responses.

- **Observability**:  
  Introducing tools to track and monitor AI performance, usage, and errors for better management and debugging.

- **Authentication**:  
  Implementing login and security features to control access to the Spring AI Playground.

- **Multimodal Support**:  
  Supporting embedding, image, audio, and moderation models from Spring AI

These features will help make Spring AI Playground even better for testing and building AI projects.