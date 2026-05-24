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

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import java.util.List;

public final class SidebarItemLayout {

    private SidebarItemLayout() {}

    public static Div twoRow(Span dot, Span title, List<Span> pills, boolean dimmed) {
        Div row = new Div();
        row.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.35em")
                .set("width", "100%");
        if (dimmed) row.getStyle().set("opacity", "0.78");

        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setSpacing(false);
        titleRow.setAlignItems(HorizontalLayout.Alignment.CENTER);
        titleRow.getStyle()
                .set("gap", "0.4em")
                .set("font-weight", "600")
                .set("color", "var(--lumo-body-text-color)");
        titleRow.add(dot, title);
        row.add(titleRow);

        if (pills != null && !pills.isEmpty()) {
            HorizontalLayout pillRow = new HorizontalLayout();
            pillRow.setSpacing(false);
            pillRow.getStyle()
                    .set("gap", "0.25em")
                    .set("flex-wrap", "wrap")
                    .set("padding-inline-start", "1em");
            for (Span pill : pills) {
                if (pill != null) pillRow.add(pill);
            }
            row.add(pillRow);
        }
        return row;
    }
}
