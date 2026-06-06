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
package org.springaicommunity.playground.webui.mcp;

import com.vaadin.flow.component.HtmlContainer;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.shared.Tooltip;
import org.springaicommunity.playground.service.tool.ToolManifest.Sandbox.RiskLevel;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag("span")
public class McpRiskChip extends HtmlContainer {

    private static final Map<RiskLevel, ChipStyle> STYLES = buildStyles();

    public McpRiskChip(RiskLevel level) {
        this(level, null, null);
    }

    public McpRiskChip(RiskLevel level, String floorTrigger) {
        this(level, floorTrigger, null);
    }

    public McpRiskChip(RiskLevel level, String floorTrigger, String prefix) {
        if (level == null) level = RiskLevel.L1;
        ChipStyle style = STYLES.get(level);
        StringBuilder text = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            text.append(prefix).append(": ");
        }
        text.append(level.name()).append(" — ").append(style.label);
        if (floorTrigger != null && !floorTrigger.isBlank()) {
            text.append(" (floor: ").append(shortTrigger(floorTrigger)).append(')');
        }
        setText(text.toString());
        getStyle()
                .set("background-color", style.background)
                .set("color", style.foreground)
                .set("border-radius", "12px")
                .set("padding", "3px 10px")
                .set("font-size", "12px")
                .set("font-weight", "600")
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("line-height", "1.2")
                .set("vertical-align", "middle")
                .set("white-space", "nowrap");
    }

    public McpRiskChip withTooltip(String tooltipText) {
        if (tooltipText != null && !tooltipText.isBlank()) {
            Tooltip.forComponent(this).withText(tooltipText);
        }
        return this;
    }

    static String shortTrigger(String trigger) {
        return switch (trigger) {
            case "non_loopback_no_auth_write_capability" -> "no-auth-write";
            case "non_loopback_no_auth_trust_unknown" -> "no-auth-unknown";
            case "privileged_oauth_scope" -> "privileged-scope";
            default -> trigger;
        };
    }

    static String labelFor(RiskLevel level) {
        if (level == null) return STYLES.get(RiskLevel.L1).label;
        return STYLES.get(level).label;
    }

    public static String levelRationale(RiskLevel level) {
        if (level == null) return "";
        return switch (level) {
            case L0 -> "Built-in trusted tool — risk model bypassed";
            case L1 -> "Safe — local operations, no external access";
            case L2 -> "Low — external read-only or API-key auth";
            case L3 -> "Moderate — external write, network fetch, or community trust";
            case L4 -> "High — admin-scope, exec capability, or irreversible actions";
            case L5 -> "Critical — floor rule triggered or unverified server";
        };
    }

    private record ChipStyle(String background, String foreground, String label) {}

    private static Map<RiskLevel, ChipStyle> buildStyles() {
        Map<RiskLevel, ChipStyle> styles = new LinkedHashMap<>();
        styles.put(RiskLevel.L0, new ChipStyle("#1b5e20", "#ffffff", "Verified"));
        styles.put(RiskLevel.L1, new ChipStyle("#2e7d32", "#ffffff", "Safe"));
        styles.put(RiskLevel.L2, new ChipStyle("#9ccc65", "#1b1b1b", "Low"));
        styles.put(RiskLevel.L3, new ChipStyle("#fdd835", "#1b1b1b", "Moderate"));
        styles.put(RiskLevel.L4, new ChipStyle("#fb8c00", "#ffffff", "High"));
        styles.put(RiskLevel.L5, new ChipStyle("#e53935", "#ffffff", "Critical"));
        return styles;
    }
}
