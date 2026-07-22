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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ChatStylesCssTest {

    private static final String CSS = readChatStyles();

    @Test
    void scrollerChildrenAreWidthCapped() {
        assertThat(bodyOf(".chat-message-scroller vaadin-message")).contains("max-width: 100%");
        assertThat(bodyOf(".chat-message-scroller vaadin-details")).contains("max-width: 100%");
        assertThat(bodyOf("vaadin-message::part(content)")).contains("min-width: 0");
        assertThat(bodyOf("vaadin-details-summary::part(content)"))
                .contains("min-width: 0").contains("overflow: hidden");
    }

    @Test
    void wideBlocksScrollInternallyInsteadOfWideningThePage() {
        assertThat(bodyOf(".chat-message-scroller vaadin-details vaadin-message::part(message)"))
                .contains("overflow-x: auto");
        assertThat(bodyOf("vaadin-markdown pre")).contains("overflow: hidden");
        assertThat(bodyOf("vaadin-markdown pre code")).contains("overflow-x: auto");
        assertThat(bodyOf(".saip-table-scroll")).contains("overflow-x: auto");
    }

    private static String bodyOf(String selector) {
        String stripped = CSS.replaceAll("(?s)/\\*.*?\\*/", "");
        for (String rule : stripped.split("}")) {
            int brace = rule.indexOf('{');
            if (brace < 0) continue;
            boolean matches = Arrays.stream(rule.substring(0, brace).split(","))
                    .map(part -> part.trim().replaceAll("\\s+", " "))
                    .anyMatch(selector::equals);
            if (matches) return rule.substring(brace + 1);
        }
        throw new AssertionError("No CSS rule found for selector: " + selector);
    }

    private static String readChatStyles() {
        try {
            return Files.readString(Path.of("src/main/frontend/playground/chat-styles.css"));
        } catch (IOException e) {
            throw new IllegalStateException("chat-styles.css not readable", e);
        }
    }

}
