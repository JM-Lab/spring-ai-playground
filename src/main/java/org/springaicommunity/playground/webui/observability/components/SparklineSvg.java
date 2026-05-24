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
package org.springaicommunity.playground.webui.observability.components;

import com.vaadin.flow.component.Html;

import java.util.List;
import java.util.Locale;

public final class SparklineSvg {

    private SparklineSvg() {}

    public static Html of(List<? extends Number> values, int width, int height, String stroke) {
        if (values == null || values.isEmpty()) {
            return new Html(String.format(Locale.ROOT,
                    "<svg width='%d' height='%d' xmlns='http://www.w3.org/2000/svg'></svg>", width, height));
        }
        double max = values.stream().mapToDouble(Number::doubleValue).max().orElse(1.0);
        double min = values.stream().mapToDouble(Number::doubleValue).min().orElse(0.0);
        double range = Math.max(0.000001, max - min);
        int n = values.size();
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double xRatio = n == 1 ? 0.5 : (double) i / (n - 1);
            double x = xRatio * (width - 2) + 1;
            double yNorm = (values.get(i).doubleValue() - min) / range;
            double y = (height - 2) - yNorm * (height - 2) + 1;
            path.append(i == 0 ? "M" : "L").append(String.format(Locale.ROOT, "%.2f,%.2f ", x, y));
        }
        String svg = String.format(Locale.ROOT,
                "<svg width='%d' height='%d' xmlns='http://www.w3.org/2000/svg'>"
                        + "<path d='%s' fill='none' stroke='%s' stroke-width='1.5'/>"
                        + "</svg>",
                width, height, path.toString().trim(), stroke);
        return new Html(svg);
    }
}
