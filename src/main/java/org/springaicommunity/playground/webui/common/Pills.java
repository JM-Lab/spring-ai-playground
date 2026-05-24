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
package org.springaicommunity.playground.webui.common;

import com.vaadin.flow.component.html.Span;

public final class Pills {

    private Pills() {}

    public static Span pill(String text, String themeAttr) {
        Span span = new Span(text);
        span.getElement().setAttribute("theme", themeAttr);
        span.getStyle()
                .set("font-size", "0.65em")
                .set("padding", "0 0.4em")
                .set("line-height", "1.4");
        return span;
    }

    public static Span dot(String cssColor) {
        Span dot = new Span();
        dot.getStyle()
                .set("display", "inline-block")
                .set("width", "0.55em")
                .set("height", "0.55em")
                .set("border-radius", "50%")
                .set("background", cssColor)
                .set("flex", "0 0 auto");
        return dot;
    }
}
