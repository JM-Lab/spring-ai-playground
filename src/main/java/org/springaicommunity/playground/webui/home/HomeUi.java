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
package org.springaicommunity.playground.webui.home;

import com.vaadin.flow.component.html.Span;

import java.time.Duration;
import java.time.Instant;

final class HomeUi {

    private HomeUi() {}

    static Span divider() {
        Span span = new Span("·");
        span.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        return span;
    }

    static Span mutedLabel(String text) {
        Span span = new Span(text);
        span.getStyle().set("color", "var(--lumo-secondary-text-color)");
        return span;
    }

    static String relativeTime(long epochMillis) {
        Duration diff = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now());
        long seconds = Math.max(diff.getSeconds(), 0);
        if (seconds < 60) return "just now";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        long days = hours / 24;
        if (days == 1) return "yesterday";
        if (days < 7) return days + " days ago";
        long weeks = days / 7;
        if (weeks < 5) return weeks + " week" + (weeks == 1 ? "" : "s") + " ago";
        long months = days / 30;
        if (months < 12) return months + " month" + (months == 1 ? "" : "s") + " ago";
        long years = days / 365;
        return years + " year" + (years == 1 ? "" : "s") + " ago";
    }
}
