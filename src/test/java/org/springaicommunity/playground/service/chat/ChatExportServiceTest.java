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
package org.springaicommunity.playground.service.chat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.DefaultChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatExportServiceTest {

    private final ChatExportService service = new ChatExportService(new ObjectMapper());

    private ChatHistory sampleHistory() {
        DefaultChatOptions options = (DefaultChatOptions) ChatOptions.builder().model("gpt-5-mini").build();
        return new ChatHistory("Chat-1", "My Title", 1L, 1L, "You are helpful", options,
                () -> List.of(new UserMessage("hello"), new AssistantMessage("hi there")));
    }

    @Test
    void markdownIncludesTitleSystemAndRoles() {
        String md = this.service.conversationToMarkdown(sampleHistory());
        assertTrue(md.contains("# My Title"));
        assertTrue(md.contains("**System:** You are helpful"));
        assertTrue(md.contains("**User:** hello"));
        assertTrue(md.contains("**Assistant:** hi there"));
    }

    @Test
    void plainTextHasRolesWithoutMarkdown() {
        String txt = this.service.conversationToPlainText(sampleHistory());
        assertTrue(txt.contains("User: hello"));
        assertTrue(txt.contains("Assistant: hi there"));
        assertFalse(txt.contains("**"));
    }

    @Test
    void jsonHasModelAndMessageRoles() throws Exception {
        JsonNode node = new ObjectMapper().readTree(this.service.conversationToJson(sampleHistory()));
        assertEquals("gpt-5-mini", node.get("model").asText());
        assertEquals("My Title", node.get("title").asText());
        assertEquals(2, node.get("messages").size());
        assertEquals("user", node.get("messages").get(0).get("role").asText());
        assertEquals("hello", node.get("messages").get(0).get("content").asText());
        assertEquals("assistant", node.get("messages").get(1).get("role").asText());
    }

    @Test
    void toolMessagesAreExcludedFromExport() {
        DefaultChatOptions options = (DefaultChatOptions) ChatOptions.builder().model("m").build();
        ChatHistory history = new ChatHistory("Chat-2", "T", 1L, 1L, "", options,
                () -> List.of(new UserMessage("q"),
                        ToolResponseMessage.builder()
                                .responses(List.of(new ToolResponseMessage.ToolResponse("id", "tool", "data")))
                                .build(),
                        new AssistantMessage("a")));
        String md = this.service.conversationToMarkdown(history);
        assertFalse(md.contains("Tool:"));
        assertTrue(md.contains("**User:** q"));
        assertTrue(md.contains("**Assistant:** a"));
    }

    @Test
    void singleMessageExport() throws Exception {
        AssistantMessage message = new AssistantMessage("the answer");
        assertEquals("the answer", this.service.messageToMarkdown(message));
        assertEquals("the answer", this.service.messageToPlainText(message));
        JsonNode node = new ObjectMapper().readTree(this.service.messageToJson(message));
        assertEquals("assistant", node.get("role").asText());
        assertEquals("the answer", node.get("content").asText());
    }
}
