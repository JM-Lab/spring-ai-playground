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

import org.springaicommunity.playground.service.tool.ToolSpec;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ToolActivationCalculator {

    public enum State {
        ACTIVE,
        TEST_FAILED,
        MISSING_REQUIREMENTS,
        DRAFT
    }

    private static final Pattern ENV_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)}");

    private final Function<String, String> envLookup;

    public ToolActivationCalculator() {
        this(System::getenv);
    }

    ToolActivationCalculator(Function<String, String> envLookup) {
        this.envLookup = envLookup;
    }

    public State calculate(ToolSpec tool) {
        if (Objects.isNull(tool)) return State.DRAFT;
        if (tool.draft()) return State.DRAFT;
        for (String envVar : declaredEnvVars(tool)) {
            String value = envLookup.apply(envVar);
            if (value == null || value.isBlank()) {
                return State.MISSING_REQUIREMENTS;
            }
        }
        return State.ACTIVE;
    }

    public List<String> declaredEnvVars(ToolSpec tool) {
        if (tool == null || tool.staticVariables() == null) return List.of();
        java.util.LinkedHashSet<String> envVars = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, String> entry : tool.staticVariables()) {
            String value = entry.getValue();
            if (value == null) continue;
            Matcher matcher = ENV_PLACEHOLDER.matcher(value);
            while (matcher.find()) {
                envVars.add(matcher.group(1));
            }
        }
        return List.copyOf(envVars);
    }
}
