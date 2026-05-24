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
package org.springaicommunity.playground.webui.common.sidebar;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;

public final class CategoryGroupDetails {

    private CategoryGroupDetails() {}

    public static Details build(String displayName, int count, Component body) {
        return wrap(header(displayName, count, false), body, true);
    }

    public static Details buildUserDefined(String displayName, int count, Component body) {
        return wrap(header(displayName, count, true), body, true);
    }

    public static Details buildCollapsed(String label, Component body) {
        Span header = new Span(label);
        header.getStyle()
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        return wrap(header, body, false);
    }

    private static Span header(String displayName, int count, boolean userDefined) {
        Span header = new Span(displayName + "  (" + count + ")");
        header.getStyle()
                .set("font-weight", "var(--lumo-font-weight-semibold)")
                .set("font-size", "var(--lumo-font-size-s)");
        if (userDefined) header.getStyle().set("color", "var(--lumo-primary-text-color)");
        return header;
    }

    private static Details wrap(Component header, Component body, boolean opened) {
        Details details = new Details(header, body);
        details.addClassName("workspace-sidebar-details");
        details.setOpened(opened);
        details.setWidthFull();
        details.getStyle()
                .set("margin", "0")
                .set("--vaadin-details-summary-padding", "var(--lumo-space-xs) var(--lumo-space-s)");
        return details;
    }
}
