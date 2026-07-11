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

public final class AgentTurnMessages {

    private AgentTurnMessages() {
    }

    public static String declined(String toolName) {
        return "The user declined to approve running the tool '" + toolName + "'. It was NOT executed. "
                + "Do not call '" + toolName + "' again for this request. If another available tool can accomplish "
                + "the goal, use it instead; otherwise tell the user the action could not be completed because they "
                + "declined approval.";
    }

    public static String notCompleted(String toolName, String note) {
        return note + " Do NOT call '" + toolName + "' again for this request. Continue without it, or tell "
                + "the user what is missing so they can retry in a new message.";
    }

    public static String interactionBudget(String toolName) {
        return "Only one user interaction can run per step. The '" + toolName + "' request was skipped this "
                + "step; call it again in your next step if it is still needed.";
    }

    public static String repeated(String toolName, int maxIdenticalCalls) {
        return "You already called '" + toolName + "' " + maxIdenticalCalls + " times with identical arguments "
                + "and the result will not change. Do not repeat this call; proceed differently or answer the "
                + "user with what you have.";
    }

    public static String wrapUp() {
        return "The tool budget for this message is exhausted. Do not call any more tools. Answer the user now "
                + "with what you have, and mention anything that could not be completed.";
    }

    public static String hardStopped(int rounds) {
        return "Stopped after " + rounds + " tool rounds without a final answer. Send a new message to "
                + "continue from here.";
    }

    public static String cancelled(String toolName) {
        return "The user stopped this response. '" + toolName + "' was not executed. Do not call any more "
                + "tools.";
    }
}
