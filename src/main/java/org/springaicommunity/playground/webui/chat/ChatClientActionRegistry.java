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

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ChatClientActionRegistry {

    private final Map<String, ChatClientAction> byName;

    public ChatClientActionRegistry(List<ChatClientAction> actions) {
        Map<String, ChatClientAction> map = new LinkedHashMap<>();
        for (ChatClientAction action : actions) {
            if (map.put(action.name(), action) != null) {
                throw new IllegalStateException("Duplicate ChatClientAction name: " + action.name());
            }
        }
        this.byName = Map.copyOf(map);
    }

    public Optional<ChatClientAction> find(String name) {
        ChatClientAction action = this.byName.get(name);
        return action != null && action.isAvailable() ? Optional.of(action) : Optional.empty();
    }

    public List<String> availableNames() {
        return this.byName.values().stream()
                .filter(ChatClientAction::isAvailable)
                .map(ChatClientAction::name)
                .toList();
    }
}
