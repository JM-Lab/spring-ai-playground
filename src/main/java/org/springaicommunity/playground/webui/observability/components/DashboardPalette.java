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

public final class DashboardPalette {

    private DashboardPalette() {}

    public static final String PRIMARY = "#7e57c2";
    public static final String INFO = "#42a5f5";
    public static final String SUCCESS = "#26a69a";
    public static final String WARN = "#ffa726";
    public static final String ERROR = "#ef5350";
    public static final String MUTED = "#90a4ae";
    public static final String ACCENT = "#ab47bc";
    public static final String NEUTRAL = "#78909c";
    public static final String SUCCESS_ALT = "#66bb6a";

    public static final String P50 = INFO;
    public static final String P95 = WARN;
    public static final String P99 = ERROR;
    public static final String TOKEN_INPUT = INFO;
    public static final String TOKEN_OUTPUT = SUCCESS;
    public static final String STATUS_OK = SUCCESS;
    public static final String STATUS_ERROR = ERROR;
}
