/*
 * Copyright © 2025 Jemin Huh (hjm1980@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jm.kr.spring.ai.playground.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.DefaultChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.ai.model.chat=openai",
        "spring.ai.openai.base-url=http://localhost:11434",
        "spring.ai.openai.api-key=ollama",
        "spring.ai.openai.chat.options.model=qwen3"
})
class ChatServiceWithCompatibleOpenAiIT {

    @Autowired
    ChatService chatService;

    @Test
    void streamWithCalculatorToolRawGenerations() {
        long now = System.currentTimeMillis();

        ChatHistory chatHistory = new ChatHistory(
                "it-calc-stream-raw",
                "IT Calc Stream Raw",
                now,
                now,
                chatService.getSystemPrompt(),
                new DefaultChatOptions(),
                List::of
        );

        CalculatorTool calculatorTool = new CalculatorTool();
        List<ToolCallback> toolCallbacks = List.of(ToolCallbacks.from(calculatorTool));

        String userPrompt = """
                Use only the 'calculator' tool to add a=7 and b=5.
                Return only the numeric result.
                """;

        StringBuilder thinkingBuf = new StringBuilder();
        StringBuilder answerBuf = new StringBuilder();

        List<Generation> generations = chatService
                .streamWithRaw(chatHistory, userPrompt, null, toolCallbacks, null, thinkingChunk -> {
                    System.out.println(thinkingChunk);
                    if (thinkingChunk != null && !thinkingChunk.toString().isBlank()) {
                        thinkingBuf.append(thinkingChunk);
                    }
                })
                .map(generation -> {
                    String contentChunk = generation.getOutput().getText();
                    if (contentChunk != null && !contentChunk.isBlank()) {
                        answerBuf.append(contentChunk);
                    }
                    return generation;
                })
                .collectList()
                .block(Duration.ofSeconds(60));

        assertThat(generations).isNotNull().isNotEmpty();

        String combinedText = generations.stream()
                .map(g -> g.getOutput() != null ? g.getOutput().getText() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());

        assertThat(combinedText).contains("12");

        System.out.println("=== THINKING(STREAM) ===");
        System.out.println(thinkingBuf);
        System.out.println("=== ANSWER(STREAM) ===");
        System.out.println(answerBuf);

        assertThat(thinkingBuf.toString())
                .as("Streaming thinking should have some content")
                .isNotBlank();

        assertThat(answerBuf.toString())
                .as("Streaming answer should have some content")
                .isNotBlank();
    }

    static class CalculatorTool {

        @Tool(name = "calculator", description = "Adds two numbers a and b and returns the result")
        public int add(
                @ToolParam(description = "First operand") int a,
                @ToolParam(description = "Second operand") int b
        ) {
            return a + b;
        }
    }
}