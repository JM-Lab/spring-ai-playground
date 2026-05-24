description: Default MCP Catalog — Business reference: 12 preset MCP connections with transport, auth defaults, required env, and full description per card.

# Default MCP Catalog — Business

Payments, CRM, customer analytics, design, mapping, plus two community stdio utility servers (`Memory`, `Sequential Thinking`) from `modelcontextprotocol/servers`. Remote entries are all OAuth 2.1; stdio entries need only Node.js on the host. `Memory (Knowledge Graph)` keeps state in a local JSON file — overridable via `MEMORY_FILE_PATH`.

## Entries (12)

Click any card to expand the full spec inline — transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="Stripe" data-tool-id="Stripe" data-tool-title="Stripe" markdown>
<div class="tcg-name"><span class="tcg-name__text">Stripe</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Stripe](https://cdn.simpleicons.org/stripe){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and Connect. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Stripe · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Stripe (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.stripe.com`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Manage Stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and Connect. OAuth.

Docs: https://docs.stripe.com/mcp

**Docs** — [https://docs.stripe.com/mcp](https://docs.stripe.com/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="PayPal" data-tool-id="PayPal" data-tool-title="PayPal" markdown>
<div class="tcg-name"><span class="tcg-name__text">PayPal</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![PayPal](https://cdn.simpleicons.org/paypal){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage PayPal orders, refunds, payouts, subscriptions, and invoicing through PayPal's official OAuth MCP (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; PayPal · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — PayPal (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.paypal.com/sse`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Manage PayPal orders, refunds, payouts, subscriptions, and invoicing through PayPal's official OAuth MCP (SSE).

Docs: https://developer.paypal.com/tools/mcp-server/

**Docs** — [https://developer.paypal.com/tools/mcp-server/](https://developer.paypal.com/tools/mcp-server/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Square" data-tool-id="Square" data-tool-title="Square" markdown>
<div class="tcg-name"><span class="tcg-name__text">Square</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Block](https://cdn.simpleicons.org/cashapp){ width="40" .tcg-favicon }</div>
<div class="tcg-type">finance · global · beta <span class="risk risk-l4">beta</span></div>
<div class="tcg-body" markdown>
Square payments, catalogue, inventory, customers, and orders across locations. Beta program from Block.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Block · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Block (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.squareup.com/sse`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — BETA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · beta

**Description**

Square payments, catalogue, inventory, customers, and orders across locations. Beta program from Block.

Docs: https://developer.squareup.com/docs/mcp

**Docs** — [https://developer.squareup.com/docs/mcp](https://developer.squareup.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="HubSpot" data-tool-id="HubSpot" data-tool-title="HubSpot" markdown>
<div class="tcg-name"><span class="tcg-name__text">HubSpot</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![HubSpot](https://cdn.simpleicons.org/hubspot){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
HubSpot CRM contacts, companies, deals, pipelines, lists, and engagements. OAuth 2.1 + PKCE.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; HubSpot · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — HubSpot (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.hubspot.com`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

HubSpot CRM contacts, companies, deals, pipelines, lists, and engagements. OAuth 2.1 + PKCE.

Docs: https://developers.hubspot.com/docs/mcp

**Docs** — [https://developers.hubspot.com/docs/mcp](https://developers.hubspot.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Intercom" data-tool-id="Intercom" data-tool-title="Intercom" markdown>
<div class="tcg-name"><span class="tcg-name__text">Intercom</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Intercom](https://cdn.simpleicons.org/intercom){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · us <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Intercom conversations, contacts, tags, segments, and help-centre articles. US workspace region only.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Intercom · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Intercom (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.intercom.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — us

**Description**

Intercom conversations, contacts, tags, segments, and help-centre articles. US workspace region only.

Docs: https://developers.intercom.com/docs/mcp

**Docs** — [https://developers.intercom.com/docs/mcp](https://developers.intercom.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Mixpanel" data-tool-id="Mixpanel" data-tool-title="Mixpanel" markdown>
<div class="tcg-name"><span class="tcg-name__text">Mixpanel</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Mixpanel](https://cdn.simpleicons.org/mixpanel){ width="40" .tcg-favicon }</div>
<div class="tcg-type">crm · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Run Mixpanel product analytics queries — events, funnels, retention, cohorts, and user properties. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Mixpanel · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Mixpanel (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.mixpanel.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Run Mixpanel product analytics queries — events, funnels, retention, cohorts, and user properties. OAuth.

Docs: https://docs.mixpanel.com/docs/mcp

**Docs** — [https://docs.mixpanel.com/docs/mcp](https://docs.mixpanel.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Figma" data-tool-id="Figma" data-tool-title="Figma" markdown>
<div class="tcg-name"><span class="tcg-name__text">Figma</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Figma](https://cdn.simpleicons.org/figma){ width="40" .tcg-favicon }</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse Figma files, frames, components, styles, comments, and design system tokens. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Figma · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Figma (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.figma.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Browse Figma files, frames, components, styles, comments, and design system tokens. OAuth.

Docs: https://help.figma.com/hc/en-us/articles/mcp

**Docs** — [https://help.figma.com/hc/en-us/articles/mcp](https://help.figma.com/hc/en-us/articles/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Canva" data-tool-id="Canva" data-tool-title="Canva" markdown>
<div class="tcg-name"><span class="tcg-name__text">Canva</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-palette-outline:</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Canva designs, brand kits, folders, templates, and assets through the official Canva Connect MCP.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Canva · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Canva (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.canva.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Manage Canva designs, brand kits, folders, templates, and assets through the official Canva Connect MCP.

Docs: https://www.canva.dev/docs/connect/mcp/

**Docs** — [https://www.canva.dev/docs/connect/mcp/](https://www.canva.dev/docs/connect/mcp/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Webflow" data-tool-id="Webflow" data-tool-title="Webflow" markdown>
<div class="tcg-name"><span class="tcg-name__text">Webflow</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Webflow](https://cdn.simpleicons.org/webflow){ width="40" .tcg-favicon }</div>
<div class="tcg-type">design · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Webflow sites, CMS collections, items, and form submissions via OAuth (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Webflow · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Webflow (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.webflow.com/sse`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Manage Webflow sites, CMS collections, items, and form submissions via OAuth (SSE).

Docs: https://developers.webflow.com/data/docs/mcp

**Docs** — [https://developers.webflow.com/data/docs/mcp](https://developers.webflow.com/data/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-google" id="Maps-Grounding" data-tool-id="Maps-Grounding" data-tool-title="Google Maps Grounding" markdown>
<div class="tcg-name"><span class="tcg-name__text">Google Maps Grounding</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Maps Grounding](https://cdn.simpleicons.org/googlemaps){ width="40" .tcg-favicon }</div>
<div class="tcg-type">util · global · geo <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Google Maps Places, Directions, Distance Matrix, Geocoding, and Street View for grounding LLM responses with real-world geo data.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Google (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mapstools.googleapis.com/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · geo

**Description**

Google Maps Places, Directions, Distance Matrix, Geocoding, and Street View for grounding LLM responses with real-world geo data.

Docs: https://docs.cloud.google.com/mcp/supported-products

**Docs** — [https://docs.cloud.google.com/mcp/supported-products](https://docs.cloud.google.com/mcp/supported-products)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Memory" data-tool-id="Memory" data-tool-title="Memory (Knowledge Graph)" markdown>
<div class="tcg-name"><span class="tcg-name__text">Memory (Knowledge Graph)</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-graph-outline:</div>
<div class="tcg-type">util · global · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Persistent knowledge graph for cross-session memory — entities, relations, and observations in a local JSON file. The activated form is pre-filled to run: npx -y…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — modelcontextprotocol/servers (community-maintained)

**Transport** — STDIO

**Command** — `npx`

**Args** — `-y @modelcontextprotocol/server-memory`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · community

**Description**

[macOS] Persistent knowledge graph for cross-session memory — entities, relations, and observations in a local JSON file.

Prereq: Node.js 18+ on macOS (Homebrew, nvm, or installer).

The activated form is pre-filled to run:
  npx -y @modelcontextprotocol/server-memory

Optional: set MEMORY_FILE_PATH in the env section to override the default storage path (any absolute file path).

Docs: https://github.com/modelcontextprotocol/servers/tree/main/src/memory

**Docs** — [https://github.com/modelcontextprotocol/servers/tree/main/src/memory](https://github.com/modelcontextprotocol/servers/tree/main/src/memory)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Sequential-Thinking" data-tool-id="Sequential-Thinking" data-tool-title="Sequential Thinking" markdown>
<div class="tcg-name"><span class="tcg-name__text">Sequential Thinking</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-thought-bubble-outline:</div>
<div class="tcg-type">util · global · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Structured step-by-step reasoning helper — logs intermediate thoughts to the server for review, revision, or branching. The activated form is pre-filled to run: npx -y…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — modelcontextprotocol/servers (community-maintained)

**Transport** — STDIO

**Command** — `npx`

**Args** — `-y @modelcontextprotocol/server-sequential-thinking`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · community

**Description**

[macOS] Structured step-by-step reasoning helper — logs intermediate thoughts to the server for review, revision, or branching.

Prereq: Node.js 18+ on macOS.

The activated form is pre-filled to run:
  npx -y @modelcontextprotocol/server-sequential-thinking

Docs: https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking

**Docs** — [https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking](https://github.com/modelcontextprotocol/servers/tree/main/src/sequentialthinking)

</div>
</div>

</div>

## Workflow combinations { #combinations }

12 entries spanning payments, CRM, customer analytics, design, mapping, plus two community stdio utility servers. The combinations are domain-specific rather than cross-cutting:

- **Customer journey** — `Stripe` (payments) + `HubSpot` (CRM) + `Intercom` (support chat) + `Mixpanel` (product analytics). The agent can trace a single customer from payment to support thread in one turn.
- **Payment provider mix** — `Stripe` + `PayPal` + `Square (Block)`. Useful when you accept all three rails and need a unified view of a single order's payment status.
- **Design → publish** — `Figma` (source) + `Webflow` (target) + `Canva` (post-graphics). The agent can lift a Figma component, render a publish-ready variant in Canva, and push it through Webflow.
- **Local memory + reasoning aid** — `Memory (Knowledge Graph)` (stdio) + `Sequential Thinking` (stdio). Two community servers that turn any agent loop into one that *remembers* previous turns and can spell out its reasoning steps. Override `MEMORY_FILE_PATH` to use the same JSON store across sessions.
- **Location-aware answers** — `Google Maps Grounding`. Pairs well with anything from the Productivity page (e.g. "schedule a coffee meeting nearer the customer's office").

## Auth & secrets { #auth-secrets }

| Connection | Auth | Extra env |
|---|---|---|
| Stripe | Stripe OAuth | — |
| PayPal | PayPal OAuth | — |
| Square (Block) | Block OAuth (beta) | — |
| HubSpot | HubSpot OAuth | — |
| Intercom | Intercom OAuth (US tag — separate EU instance if needed) | — |
| Mixpanel | Mixpanel OAuth | — |
| Figma | Figma OAuth | — |
| Canva | Canva OAuth | — |
| Webflow | Webflow OAuth | — |
| Google Maps Grounding | `https://accounts.google.com` | — |
| Memory (Knowledge Graph) (stdio) | None | Node.js 18+; optional `MEMORY_FILE_PATH` for persistent store |
| Sequential Thinking (stdio) | None | Node.js 18+ |

## Picking guide { #picking-guide }

| If you need… | Reach for |
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

