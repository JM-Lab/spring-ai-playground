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

public final class McpRiskMdcKeys {

    public static final String CID = "saip.cid";
    public static final String VIA = "saip.via";
    public static final String ORIGIN = "saip.tool.origin";
    public static final String COMPOSITION_ID = "saip.composition.id";
    public static final String COMPOSITION_NAME = "saip.composition.name";
    public static final String EXPOSED_ALIAS = "saip.tool.exposed_alias";
    public static final String UPSTREAM_SERVER = "saip.upstream.server";
    public static final String UPSTREAM_TOOL = "saip.upstream.tool";
    public static final String UPSTREAM_TRANSPORT = "saip.upstream.transport";
    public static final String RISK_FINAL = "saip.risk.final";
    public static final String RISK_SERVER = "saip.risk.server";
    public static final String RISK_PUBLISH = "saip.risk.publish";
    public static final String FLOOR_TRIGGER = "saip.risk.floor_trigger";

    public static final String VIA_CHAT = "chat";
    public static final String VIA_INSPECTOR = "inspector";
    public static final String VIA_MCP_SERVER = "mcp-server";

    public static final String ORIGIN_INTERNAL_JS = "internal_js";
    public static final String ORIGIN_WRAPPED_EXTERNAL = "wrapped_external";

    private McpRiskMdcKeys() {}
}
