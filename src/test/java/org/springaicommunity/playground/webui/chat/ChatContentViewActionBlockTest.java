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
package org.springaicommunity.playground.webui.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContentViewActionBlockTest {

    @Test
    void noMarkerReturnsEmpty() {
        assertThat(ChatContentView.extractActionBlocks("just a normal reply")).isEmpty();
        assertThat(ChatContentView.extractActionBlocks(null)).isEmpty();
    }

    @Test
    void extractsPlainAndReturnDirectBlocks() {
        String text = "a\n\n```saip-action\n{\"type\":\"email\"}\n```\nb\n\n"
                + "```saip-action-return-direct\n{\"type\":\"map\"}\n```";
        List<String> blocks = ChatContentView.extractActionBlocks(text);
        assertThat(blocks).containsExactly(
                "```saip-action\n{\"type\":\"email\"}\n```",
                "```saip-action-return-direct\n{\"type\":\"map\"}\n```");
    }

    @Test
    void literalBacktickInFieldDoesNotTruncateBlock() {
        String text = "Ready.\n\n```saip-action-return-direct\n"
                + "{\"type\":\"email\",\"body\":\"see ```code``` here\"}\n```";
        List<String> blocks = ChatContentView.extractActionBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).endsWith("\n```");
        assertThat(blocks.get(0)).contains("see ```code``` here");
    }

    @Test
    void unwrapsJsonQuotedOutput() {
        String text = "\"Ready.\\n\\n```saip-action\\n{\\\"type\\\":\\\"email\\\"}\\n```\"";
        List<String> blocks = ChatContentView.extractActionBlocks(text);
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).contains("\"type\":\"email\"");
    }

    @Test
    void unwrapsMcpContentEnvelope() {
        String text = "[{\"type\":\"text\",\"text\":\"Shown.\\n\\n```saip-action-return-direct\\n"
                + "{\\\"type\\\":\\\"map\\\"}\\n```\"}]";
        List<String> blocks = ChatContentView.extractActionBlocks(text);
        assertThat(blocks).containsExactly("```saip-action-return-direct\n{\"type\":\"map\"}\n```");
    }
}
