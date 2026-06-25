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

class ChatClientActionRegistryTest {

    @Test
    void findReturnsAvailableActionByName() {
        ChatClientActionRegistry registry = new ChatClientActionRegistry(List.of(action("openPath", true)));
        assertThat(registry.find("openPath")).isPresent();
    }

    @Test
    void findReturnsEmptyForUnavailableAction() {
        ChatClientActionRegistry registry = new ChatClientActionRegistry(List.of(action("openPath", false)));
        assertThat(registry.find("openPath")).isEmpty();
    }

    @Test
    void findReturnsEmptyForUnknownAction() {
        ChatClientActionRegistry registry = new ChatClientActionRegistry(List.of(action("openPath", true)));
        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void availableNamesExcludesUnavailableActions() {
        ChatClientActionRegistry registry = new ChatClientActionRegistry(
                List.of(action("openPath", true), action("hidden", false)));
        assertThat(registry.availableNames()).containsExactly("openPath");
    }

    private ChatClientAction action(String actionName, boolean available) {
        return new ChatClientAction() {
            @Override
            public String name() {
                return actionName;
            }

            @Override
            public boolean isAvailable() {
                return available;
            }

            @Override
            public void handle(String payload) {
            }
        };
    }
}
