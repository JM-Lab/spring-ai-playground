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
package org.springaicommunity.playground.service.agent;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

public interface AgentRoundInterceptor {

    Map<String, Interception> intercept(List<ToolCall> calls, AgentTurn turn, Map<String, Object> toolContext);

    record Interception(String response, UserMessage followUp) {

        public static Interception of(String response) {
            return new Interception(response, null);
        }
    }
}
