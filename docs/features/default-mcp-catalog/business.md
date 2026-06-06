description: Default MCP Servers - Business reference: 12 preset MCP connections with transport, auth defaults, required env, and full description per card.

# Default MCP Servers - Business

Payments, CRM, customer analytics, design, mapping, plus two community stdio utility servers (`Memory`, `Sequential Thinking`) from `modelcontextprotocol/servers`. Remote entries are all OAuth 2.1; stdio entries need only Node.js on the host. `Memory (Knowledge Graph)` keeps state in a local JSON file - overridable via `MEMORY_FILE_PATH`.

## Entries (12)

Click any card to expand the full spec inline - transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="Stripe" data-tool-id="Stripe" data-tool-title="Stripe" markdown>
<div class="tcg-name"><span class="tcg-name__text">Stripe</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Stripe](https://cdn.simpleicons.org/stripe){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and Connect. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Stripe · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Stripe (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.stripe.com`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 25 tools published by the vendor (per its [MCP docs](https://docs.stripe.com/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (10 of 25) - create_customer · list_customers · create_payment_link · list_payment_intents · create_invoice · finalize_invoice · create_refund · create_product · list_subscriptions · search_stripe_documentation"
    - `create_customer`
    - `list_customers`
    - `create_payment_link`
    - `list_payment_intents`
    - `create_invoice`
    - `finalize_invoice`
    - `create_refund`
    - `create_product`
    - `list_subscriptions`
    - `search_stripe_documentation`

**Description**

Manage Stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and Connect. OAuth.


**Docs** - [https://docs.stripe.com/mcp](https://docs.stripe.com/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="PayPal" data-tool-id="PayPal" data-tool-title="PayPal" markdown>
<div class="tcg-name"><span class="tcg-name__text">PayPal</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![PayPal](https://cdn.simpleicons.org/paypal){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage PayPal orders, refunds, payouts, subscriptions, and invoicing through PayPal's official OAuth MCP (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; PayPal · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - PayPal (vendor-official)

**Transport** - SSE

**URL** - `https://mcp.paypal.com/sse`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 26 tools published by the vendor (per its [MCP docs](https://github.com/paypal/paypal-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (9 of 26) - create_invoice · send_invoice · create_order · pay_order · create_refund · list_disputes · create_shipment_tracking · create_subscription · list_transactions"
    - `create_invoice`
    - `send_invoice`
    - `create_order`
    - `pay_order`
    - `create_refund`
    - `list_disputes`
    - `create_shipment_tracking`
    - `create_subscription`
    - `list_transactions`

**Description**

Manage PayPal orders, refunds, payouts, subscriptions, and invoicing through PayPal's official OAuth MCP (SSE).


**Docs** - [https://developer.paypal.com/tools/mcp-server/](https://developer.paypal.com/tools/mcp-server/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Square" data-tool-id="Square" data-tool-title="Square" markdown>
<div class="tcg-name"><span class="tcg-name__text">Square</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Block](https://cdn.simpleicons.org/cashapp){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global · beta <span class="risk risk-l4">beta</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Square payments, catalogue, inventory, customers, and orders across locations. Beta program from Block.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Block · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Block (vendor-official)

**Transport** - SSE

**URL** - `https://mcp.squareup.com/sse`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - BETA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · beta

**Tools** - 3 tools published by the vendor (per its [MCP docs](https://github.com/square/square-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools). consolidated meta-tools; the full Square API surface is reached through these.

??? note "Tools (3) - get_service_info · get_type_info · make_api_request"
    - `get_service_info`
    - `get_type_info`
    - `make_api_request`

**Description**

Square payments, catalogue, inventory, customers, and orders across locations. Beta program from Block.


**Docs** - [https://developer.squareup.com/docs/mcp](https://developer.squareup.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="HubSpot" data-tool-id="HubSpot" data-tool-title="HubSpot" markdown>
<div class="tcg-name"><span class="tcg-name__text">HubSpot</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![HubSpot](https://cdn.simpleicons.org/hubspot){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
HubSpot CRM contacts, companies, deals, pipelines, lists, and engagements. OAuth 2.1 + PKCE.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; HubSpot · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - HubSpot (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.hubspot.com`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 12 tools published by the vendor (per its [MCP docs](https://developers.hubspot.com/docs/apps/developer-platform/build-apps/integrate-with-the-remote-hubspot-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (8 of 12) - get_user_details · search_crm_objects · get_crm_objects · manage_crm_objects · search_properties · search_owners · get_campaign_analytics · submit_feedback"
    - `get_user_details`
    - `search_crm_objects`
    - `get_crm_objects`
    - `manage_crm_objects`
    - `search_properties`
    - `search_owners`
    - `get_campaign_analytics`
    - `submit_feedback`

**Description**

HubSpot CRM contacts, companies, deals, pipelines, lists, and engagements. OAuth 2.1 + PKCE.


**Docs** - [https://developers.hubspot.com/docs/mcp](https://developers.hubspot.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Intercom" data-tool-id="Intercom" data-tool-title="Intercom" markdown>
<div class="tcg-name"><span class="tcg-name__text">Intercom</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Intercom](https://cdn.simpleicons.org/intercom){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · us <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Intercom conversations, contacts, tags, segments, and help-centre articles. US workspace region only.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Intercom · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Intercom (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.intercom.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - us

**Tools** - 6 tools published by the vendor (per its [MCP docs](https://github.com/intercom/intercom-mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (6) - search · fetch · search_conversations · get_conversation · search_contacts · get_contact"
    - `search`
    - `fetch`
    - `search_conversations`
    - `get_conversation`
    - `search_contacts`
    - `get_contact`

**Description**

Intercom conversations, contacts, tags, segments, and help-centre articles. US workspace region only.


**Docs** - [https://developers.intercom.com/docs/mcp](https://developers.intercom.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Mixpanel" data-tool-id="Mixpanel" data-tool-title="Mixpanel" markdown>
<div class="tcg-name"><span class="tcg-name__text">Mixpanel</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Mixpanel](https://cdn.simpleicons.org/mixpanel){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Run Mixpanel product analytics queries - events, funnels, retention, cohorts, and user properties. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Mixpanel · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Mixpanel (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.mixpanel.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 35 tools published by the vendor (per its [MCP docs](https://docs.mixpanel.com/docs/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (9 of 35) - Run-Query · Get-Query-Schema · Get-Report · Create-Dashboard · List-Dashboards · Get-Events · List-Properties · Get-Property-Values · Search-Entities"
    - `Run-Query`
    - `Get-Query-Schema`
    - `Get-Report`
    - `Create-Dashboard`
    - `List-Dashboards`
    - `Get-Events`
    - `List-Properties`
    - `Get-Property-Values`
    - `Search-Entities`

**Description**

Run Mixpanel product analytics queries - events, funnels, retention, cohorts, and user properties. OAuth.


**Docs** - [https://docs.mixpanel.com/docs/mcp](https://docs.mixpanel.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Figma" data-tool-id="Figma" data-tool-title="Figma" markdown>
<div class="tcg-name"><span class="tcg-name__text">Figma</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Figma](https://cdn.simpleicons.org/figma){ width="40" .tcg-favicon }</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Browse Figma files, frames, components, styles, comments, and design system tokens. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Figma · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Figma (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.figma.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 18 tools published by the vendor (per its [MCP docs](https://developers.figma.com/docs/figma-mcp-server/tools-and-prompts/)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (9 of 18) - get_design_context · get_metadata · get_screenshot · get_variable_defs · search_design_system · generate_figma_design · create_new_file · get_code_connect_map · upload_assets"
    - `get_design_context`
    - `get_metadata`
    - `get_screenshot`
    - `get_variable_defs`
    - `search_design_system`
    - `generate_figma_design`
    - `create_new_file`
    - `get_code_connect_map`
    - `upload_assets`

**Description**

Browse Figma files, frames, components, styles, comments, and design system tokens. OAuth.


**Docs** - [https://help.figma.com/hc/en-us/articles/mcp](https://help.figma.com/hc/en-us/articles/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Canva" data-tool-id="Canva" data-tool-title="Canva" markdown>
<div class="tcg-name"><span class="tcg-name__text">Canva</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-palette-outline:</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Canva designs, brand kits, folders, templates, and assets through the official Canva Connect MCP.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Canva · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Canva (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mcp.canva.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - published by the vendor and discovered live on connect; its [MCP docs](https://www.canva.dev/docs/connect/mcp-server/) don't enumerate tool names, so open the [Inspector](../mcp-server/inspector.md#tools) after Save & Connect for the live tools and their recomputed levels.

**Description**

Manage Canva designs, brand kits, folders, templates, and assets through the official Canva Connect MCP.


**Docs** - [https://www.canva.dev/docs/connect/mcp/](https://www.canva.dev/docs/connect/mcp/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Webflow" data-tool-id="Webflow" data-tool-title="Webflow" markdown>
<div class="tcg-name"><span class="tcg-name__text">Webflow</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Webflow](https://cdn.simpleicons.org/webflow){ width="40" .tcg-favicon }</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Manage Webflow sites, CMS collections, items, and form submissions via OAuth (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Webflow · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Webflow (vendor-official)

**Transport** - SSE

**URL** - `https://mcp.webflow.com/sse`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global

**Tools** - 22 tools published by the vendor (per its [MCP docs](https://github.com/webflow/mcp-server)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools). consolidated dispatcher tools; sub-operations are input-schema fields.

??? note "Tools (8 of 22) - data_sites_tool · data_pages_tool · data_cms_tool · data_components_tool · data_scripts_tool · data_comments_tool · data_webhook_tool · ask_webflow_ai"
    - `data_sites_tool`
    - `data_pages_tool`
    - `data_cms_tool`
    - `data_components_tool`
    - `data_scripts_tool`
    - `data_comments_tool`
    - `data_webhook_tool`
    - `ask_webflow_ai`

**Description**

Manage Webflow sites, CMS collections, items, and form submissions via OAuth (SSE).


**Docs** - [https://developers.webflow.com/data/docs/mcp](https://developers.webflow.com/data/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Maps-Grounding" data-tool-id="Maps-Grounding" data-tool-title="Google Maps Grounding" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Maps Grounding</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Maps Grounding](https://cdn.simpleicons.org/googlemaps){ width="40" .tcg-favicon }</div>
<div class="tcg-type">util · global · geo <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Google Maps Places, Directions, Distance Matrix, Geocoding, and Street View for grounding LLM responses with real-world geo data.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Google (vendor-official)

**Transport** - Streamable HTTP

**URL** - `https://mapstools.googleapis.com/mcp`

**Auth** - OAuth 2.1

**OAuth 2.1** - runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · geo

**Tools** - 3 tools published by the vendor (per its [MCP docs](https://developers.google.com/maps/ai/grounding-lite/reference/mcp)); no static per-tool levels - the live set varies by plan / scopes / release, so confirm it and the recomputed levels on the [Inspector](../mcp-server/inspector.md#tools):

??? note "Tools (3) - search_places · compute_routes · lookup_weather"
    - `search_places`
    - `compute_routes`
    - `lookup_weather`

**Description**

Google Maps Places, Directions, Distance Matrix, Geocoding, and Street View for grounding LLM responses with real-world geo data.


**Docs** - [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Memory" data-tool-id="Memory" data-tool-title="Memory (Knowledge Graph)" markdown>
<div class="tcg-name"><span class="tcg-name__text">Memory (Knowledge Graph)</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-graph-outline:</div>
<div class="tcg-type">util · global · community <span class="risk risk-l0">ga</span> <span class="rl rl-l3">L3</span></div>
<div class="tcg-body" markdown>
[macOS] Persistent knowledge graph for cross-session memory - entities, relations, and observations in a local JSON file. The activated form is pre-filled to run: npx -y...
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - modelcontextprotocol/servers (community-maintained)

**Transport** - STDIO

**Command** - `npx`

**Args** - `-y @modelcontextprotocol/server-memory`

**OS variants** - mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** - STDIO

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · community

**Tools** - discovered on connect (a local JSON knowledge graph). Community-trust STDIO with no per-tool descriptors, so most tools compose to <span class="rl rl-l3">L3 - Moderate</span>; the three `delete_*` tools start with an irreversible verb and trip the publish **floor** to <span class="rl rl-l5">L5 - Critical</span> - see them on the connection's [Inspector](../mcp-server/inspector.md#tools):

??? abstract "Tools (9) - create_entities · create_relations · add_observations · delete_entities · delete_observations · delete_relations · read_graph · search_nodes · open_nodes"
    - **`create_entities`** - add new entities (nodes) to the graph. <span class="rl rl-l3">L3 - Moderate</span>
    - **`create_relations`** - add directed relations between entities. <span class="rl rl-l3">L3 - Moderate</span>
    - **`add_observations`** - attach observations to existing entities. <span class="rl rl-l3">L3 - Moderate</span>
    - **`delete_entities`** - remove entities and their relations (cascading). <span class="rl rl-l5">L5 - Critical</span> *(floor: irreversible verb)*
    - **`delete_observations`** - remove specific observations from entities. <span class="rl rl-l5">L5 - Critical</span> *(floor: irreversible verb)*
    - **`delete_relations`** - remove specific relations from the graph. <span class="rl rl-l5">L5 - Critical</span> *(floor: irreversible verb)*
    - **`read_graph`** - read the entire knowledge graph. <span class="rl rl-l3">L3 - Moderate</span>
    - **`search_nodes`** - search nodes by query. <span class="rl rl-l3">L3 - Moderate</span>
    - **`open_nodes`** - fetch specific nodes by name. <span class="rl rl-l3">L3 - Moderate</span>

**Description**

[macOS] Persistent knowledge graph for cross-session memory - entities, relations, and observations in a local JSON file.

Prereq: Node.js 18+ on macOS (Homebrew, nvm, or installer).

The activated form is pre-filled to run:
  npx -y @modelcontextprotocol/server-memory

Optional: set MEMORY_FILE_PATH in the env section to override the default storage path (any absolute file path).


**Docs** - [https://github.com/modelcontextprotocol/servers/tree/main/src/memory](https://github.com/modelcontextprotocol/servers/tree/main/src/memory)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Sequential-Thinking" data-tool-id="Sequential-Thinking" data-tool-title="Sequential Thinking" markdown>
<div class="tcg-name"><span class="tcg-name__text">Sequential Thinking</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-thought-bubble-outline:</div>
<div class="tcg-type">util · global · community <span class="risk risk-l0">ga</span> <span class="rl rl-l3">L3</span></div>
<div class="tcg-body" markdown>
[macOS] Structured step-by-step reasoning helper - logs intermediate thoughts to the server for review, revision, or branching. The activated form is pre-filled to run: npx -y...
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - modelcontextprotocol/servers (community-maintained)

**Transport** - STDIO

**Command** - `npx`

**Args** - `-y @modelcontextprotocol/server-sequential-thinking`

**OS variants** - mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** - STDIO

**Stability** - GA · **Tier** - Tier 2

**Required env** - -

**Tags** - global · community

**Tools** - a single reasoning tool; community-trust STDIO with no per-tool descriptor, so it composes to <span class="rl rl-l3">L3 - Moderate</span> on its own connection's [Inspector](../mcp-server/inspector.md#tools):

??? abstract "Tools (1) - sequentialthinking"
    - **`sequentialthinking`** - record one reasoning step, with optional revision or branching of earlier thoughts. <span class="rl rl-l3">L3 - Moderate</span>

**Description**

[macOS] Structured step-by-step reasoning helper - logs intermediate thoughts to the server for review, revision, or branching.

Prereq: Node.js 18+ on macOS.

The activated form is pre-filled to run:
  npx -y @modelcontextprotocol/server-sequential-thinking


**Docs** - [https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking](https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking)

</div>
</div>

</div>

## Workflow combinations { #combinations }

12 entries spanning payments, CRM, customer analytics, design, mapping, plus two community stdio utility servers. The combinations are domain-specific rather than cross-cutting:

- **Customer journey** - `Stripe` (payments) + `HubSpot` (CRM) + `Intercom` (support chat) + `Mixpanel` (product analytics). The agent can trace a single customer from payment to support thread in one turn.
- **Payment provider mix** - `Stripe` + `PayPal` + `Square (Block)`. Useful when you accept all three rails and need a unified view of a single order's payment status.
- **Design → publish** - `Figma` (source) + `Webflow` (target) + `Canva` (post-graphics). The agent can lift a Figma component, render a publish-ready variant in Canva, and push it through Webflow.
- **Local memory + reasoning aid** - `Memory (Knowledge Graph)` (stdio) + `Sequential Thinking` (stdio). Two community servers that turn any agent loop into one that *remembers* previous turns and can spell out its reasoning steps. Override `MEMORY_FILE_PATH` to use the same JSON store across sessions.
- **Location-aware answers** - `Google Maps Grounding`. Pairs well with anything from the Productivity page (e.g. "schedule a coffee meeting nearer the customer's office").

## Auth & secrets { #auth-secrets }

| Connection | Auth | Extra env |
|---|---|---|
| Stripe | Stripe OAuth | - |
| PayPal | PayPal OAuth | - |
| Square (Block) | Block OAuth (beta) | - |
| HubSpot | HubSpot OAuth | - |
| Intercom | Intercom OAuth (US tag - separate EU instance if needed) | - |
| Mixpanel | Mixpanel OAuth | - |
| Figma | Figma OAuth | - |
| Canva | Canva OAuth | - |
| Webflow | Webflow OAuth | - |
| Google Maps Grounding | `https://accounts.google.com` | - |
| Memory (Knowledge Graph) (stdio) | None | Node.js 18+; optional `MEMORY_FILE_PATH` for persistent store |
| Sequential Thinking (stdio) | None | Node.js 18+ |

## Picking guide { #picking-guide }

| If you need... | Reach for |
|---|---|
| Payments status / refunds | `Stripe` (primary) · `PayPal` · `Square` |
| CRM record lookup | `HubSpot` |
| Support-thread / NPS context | `Intercom` |
| Product-event analytics | `Mixpanel` |
| Design source of truth | `Figma` |
| Marketing-graphics generation | `Canva` |
| Static / CMS publishing | `Webflow` |
| Maps + addresses | `Google Maps Grounding` |
| Cross-session memory in any agent loop | `Memory (Knowledge Graph)` stdio |
| Step-by-step reasoning surface | `Sequential Thinking` stdio |

