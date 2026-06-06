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
package org.springaicommunity.playground.service.mcp.risk;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class McpToolPoisoningScanner {

    public enum PatternId {
        HIDDEN_INSTRUCTION,
        SYSTEM_PROMPT_OVERRIDE,
        ROLE_HIJACK,
        UNICODE_ZERO_WIDTH,
        UNICODE_RTL_OVERRIDE,
        UNICODE_HOMOGLYPH_RISK,
        ANSI_ESCAPE,
        CROSS_SERVER_IMPERATIVE,
        EXFILTRATION_DIRECTIVE
    }

    public record Match(PatternId pattern, String matchedText, int startOffset) {}

    public record ScanResult(boolean clean, List<Match> matches) {

        public ScanResult {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }

        public boolean shouldBlockPublish() {
            return !clean;
        }
    }

    static final List<PatternRule> RULES = List.of(
            new PatternRule(PatternId.HIDDEN_INSTRUCTION,
                    Pattern.compile("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions")),
            new PatternRule(PatternId.SYSTEM_PROMPT_OVERRIDE,
                    Pattern.compile("(?i)<\\s*system\\s*>|\\[\\s*system\\s*\\]|<\\|im_start\\|>")),
            new PatternRule(PatternId.ROLE_HIJACK,
                    Pattern.compile("(?i)you\\s+are\\s+(now|actually|really)\\s+a\\s+")),
            new PatternRule(PatternId.UNICODE_ZERO_WIDTH,
                    Pattern.compile("[\\u200B\\u200C\\u200D\\u2060\\uFEFF]")),
            new PatternRule(PatternId.UNICODE_RTL_OVERRIDE,
                    Pattern.compile("[\\u202A-\\u202E\\u2066-\\u2069]")),
            new PatternRule(PatternId.ANSI_ESCAPE,
                    Pattern.compile("\\u001B\\[[0-9;]*[a-zA-Z]")),
            new PatternRule(PatternId.EXFILTRATION_DIRECTIVE,
                    Pattern.compile(
                            "(?i)(exfiltrate|send\\s+to|post\\s+to|forward\\s+all)\\s+(?:[a-z0-9_-]+@|https?://)")));

    public ScanResult scan(String text) {
        if (text == null || text.isEmpty()) {
            return new ScanResult(true, List.of());
        }
        List<Match> matches = new ArrayList<>();
        for (PatternRule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            while (m.find()) {
                matches.add(new Match(rule.id(), m.group(), m.start()));
            }
        }
        boolean homoglyphRisk = containsHomoglyphMix(text);
        if (homoglyphRisk) {
            matches.add(new Match(PatternId.UNICODE_HOMOGLYPH_RISK,
                    "mixed Latin/Cyrillic characters", 0));
        }
        return new ScanResult(matches.isEmpty(), matches);
    }

    public ScanResult scanWithCrossServerCheck(String text, List<String> otherToolNamesAndAliases) {
        ScanResult base = scan(text);
        if (text == null || otherToolNamesAndAliases == null || otherToolNamesAndAliases.isEmpty()) return base;
        List<Match> matches = new ArrayList<>(base.matches());
        String lower = text.toLowerCase();
        Pattern verb = Pattern.compile("(?i)\\b(use|call|invoke|run|then\\s+(?:use|call|invoke)|first\\s+(?:call|use))\\s+");
        Matcher verbMatch = verb.matcher(lower);
        while (verbMatch.find()) {
            int end = verbMatch.end();
            String tail = lower.substring(end, Math.min(end + 80, lower.length()));
            for (String name : otherToolNamesAndAliases) {
                if (name == null || name.isBlank()) continue;
                if (tail.contains(name.toLowerCase())) {
                    matches.add(new Match(PatternId.CROSS_SERVER_IMPERATIVE,
                            text.substring(verbMatch.start(), Math.min(verbMatch.start() + 60, text.length())),
                            verbMatch.start()));
                    break;
                }
            }
        }
        return new ScanResult(matches.isEmpty(), matches);
    }

    static boolean containsHomoglyphMix(String text) {
        boolean hasLatin = false;
        boolean hasCyrillic = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) hasLatin = true;
            if (c >= 0x0400 && c <= 0x04FF) hasCyrillic = true;
            if (hasLatin && hasCyrillic) return true;
        }
        return false;
    }

    public record McpRiskEventsHit(PoisoningHit hit) {
        public record PoisoningHit(Instant at, String serverId, String toolName, String patternId,
                String matchedText, String details) {}
    }

    public record PatternRule(PatternId id, Pattern pattern) {}
}
