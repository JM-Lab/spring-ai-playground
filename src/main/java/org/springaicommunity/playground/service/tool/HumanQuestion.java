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
package org.springaicommunity.playground.service.tool;

import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.List;

public record HumanQuestion(String id, String header, String question, List<Option> options,
                            boolean multiSelect, RiskLevel riskLevel) {

    public record Option(String label, String description) {}

    public static HumanQuestion approval(String id, String header, String question) {
        return approval(id, header, question, null);
    }

    public static HumanQuestion approval(String id, String header, String question, RiskLevel riskLevel) {
        return new HumanQuestion(id, header, question,
                List.of(new Option("Approve", "Run the tool with these arguments"),
                        new Option("Decline", "Cancel; do not run the tool")),
                false, riskLevel);
    }
}
