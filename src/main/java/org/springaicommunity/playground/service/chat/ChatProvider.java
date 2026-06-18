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

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;

// Single source of truth for provider-specific knowledge shared by the request-options factory and the chat UI
// (reasoning control label, whether reasoning applies, the advanced-JSON placeholder). Resolved from the active
// ChatModel bean.
public enum ChatProvider {

    OPENAI("OpenAI", "Reasoning effort", true, "{\"verbosity\": \"low\", \"serviceTier\": \"flex\"}", 4),
    // Ollama option keys bind through @JsonProperty snake_case names; camelCase keys are silently dropped.
    OLLAMA("Ollama", "Thinking", true, "{\"top_k\": 40, \"repeat_penalty\": 1.1, \"num_ctx\": 8192}", null),
    GENERIC("Model", "Reasoning", false, "{}", null);

    private final String displayName;
    private final String reasoningLabel;
    private final boolean supportsReasoning;
    private final String jsonPlaceholder;
    private final Integer maxStopSequences;

    ChatProvider(String displayName, String reasoningLabel, boolean supportsReasoning, String jsonPlaceholder,
            Integer maxStopSequences) {
        this.displayName = displayName;
        this.reasoningLabel = reasoningLabel;
        this.supportsReasoning = supportsReasoning;
        this.jsonPlaceholder = jsonPlaceholder;
        this.maxStopSequences = maxStopSequences;
    }

    public String displayName() {
        return this.displayName;
    }

    public String reasoningLabel() {
        return this.reasoningLabel;
    }

    public boolean supportsReasoning() {
        return this.supportsReasoning;
    }

    public String jsonPlaceholder() {
        return this.jsonPlaceholder;
    }

    public Integer maxStopSequences() {
        return this.maxStopSequences;
    }

    public static ChatProvider from(ChatModel chatModel) {
        if (chatModel instanceof OpenAiChatModel) return OPENAI;
        if (chatModel instanceof OllamaChatModel) return OLLAMA;
        return GENERIC;
    }
}
