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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSpecCategoryDeserializationTest {

    @Test
    void staticVariablesShapeFromBundledJsonForSlackTool() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/tool/default-tool-specs.json")) {
            List<ToolSpec> specs = mapper.readValue(in,
                    mapper.getTypeFactory().constructCollectionType(List.class, ToolSpec.class));
            ToolSpec slack = specs.stream().filter(s -> "sendSlackMessage".equals(s.name())).findFirst().orElseThrow();
            // Print every entry so we can see the actual key/value pairs
            System.out.println("--- sendSlackMessage staticVariables ---");
            for (Map.Entry<String, String> e : slack.staticVariables()) {
                System.out.printf("  key=[%s] value=[%s]%n", e.getKey(), e.getValue());
            }
            // What we expect: an entry with key=slackWebhookUrl and value=${SLACK_WEBHOOK_URL}
            boolean foundEnvPlaceholder = slack.staticVariables().stream()
                    .anyMatch(e -> e.getValue() != null && e.getValue().contains("${SLACK_WEBHOOK_URL}"));
            assertTrue(foundEnvPlaceholder,
                    "Expected at least one staticVariable value containing ${SLACK_WEBHOOK_URL} — actual entries above");
        }
    }

    @Test
    void carryOverHelpersPreserveCategoryAndTags() {
        ToolSpec target = new ToolSpec("id1", "name", "desc", java.util.List.of(), java.util.List.of(),
                "code", ToolSpec.CodeType.Javascript, null);
        target.withCategory("WEB").withTags(java.util.Set.of("oss", "global"));
        assertEquals("WEB", target.category());
        assertEquals(java.util.Set.of("oss", "global"), target.tags());
    }

    @Test
    void draftFlagSurvivesSerializationRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolSpec draftSpec = new ToolSpec("draft-1", "draftTool", "desc",
                java.util.List.of(), java.util.List.of(),
                "return 1;", ToolSpec.CodeType.Javascript, null).withDraft(true);
        String json = mapper.writeValueAsString(draftSpec);
        assertTrue(json.contains("\"draft\":true"), "Expected draft:true in JSON, got: " + json);

        ToolSpec restored = mapper.readValue(json, ToolSpec.class);
        assertEquals(true, restored.draft());
        assertEquals("draftTool", restored.name());
    }

    @Test
    void missingDraftFieldDeserializesAsFalse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "{\"toolId\":\"x\",\"name\":\"n\",\"description\":\"d\","
                + "\"staticVariables\":[],\"params\":[],\"code\":\"return 1;\",\"codeType\":\"Javascript\"}";
        ToolSpec spec = mapper.readValue(json, ToolSpec.class);
        assertEquals(false, spec.draft(),
                "Built-in JSON without explicit draft field must default to published (false)");
    }

}
