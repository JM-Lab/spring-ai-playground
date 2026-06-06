title: Default MCP Server Examples
description: Default MCP Servers - Examples reference: MCP Everything and DeepWiki. Activate MCP Everything to exercise every Inspector primitive end-to-end.

# Default MCP Servers - Examples

The **Examples** category holds reference servers that exist primarily to *exercise the protocol itself* - not to deliver business data. Activating one of these is the fastest way to see every MCP primitive (tools, resources, prompts, sampling, completion, logging, progress, root listing) light up in the [Inspector](../mcp-server/inspector.md) without first wiring up a real account.

Click either card to see the activation spec inline - transport, command (for the STDIO variant), full description, and the upstream docs link.

!!! tip "Hands-on walkthrough - Tutorial 9"
    This page is the **catalog spec** for MCP Everything (transport, command, per-OS variants, full primitive matrix). For the **step-by-step hands-on tour** - Activate → Ping → Tools → Resources → Prompts → Notifications → Roots → Sampling → Elicitation, with sample inputs and expected results at each step - see **[Tutorial 9: MCP Everything - All 8 Primitives in One Walkthrough](../../tutorials/9-mcp-everything.md)**.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="MCP-Everything" data-tool-id="MCP-Everything" data-tool-title="MCP Everything (Reference Test Server)" markdown>
<div class="tcg-name"><span class="tcg-name__text">MCP Everything (Reference Test Server)</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-flask-outline:</div>
<div class="tcg-type">examples · community <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
The official MCP reference test server, maintained by the MCP working group. Implements **every** protocol primitive (tools / resources / prompts / sampling / completion / logging / progress / root listing) - including the ones server authors usually skip. The catalog ships per-OS STDIO entries; the in-app QA recipe uses the Docker form as a smoke test for the playground's own MCP client.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; `modelcontextprotocol` · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None - connects without credentials</div>
</div>
<div class="tcg-cta">Click for full protocol-coverage breakdown · per-OS command · Docker alternative</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - `modelcontextprotocol` (vendor-official reference; the project that defines the MCP spec itself)

**Transport** - STDIO

**Command + Args (per OS)**

| OS | Command | Arguments |
|---|---|---|
| macOS | `npx` | `-y @modelcontextprotocol/server-everything` |
| Linux | `npx` | `-y @modelcontextprotocol/server-everything` |
| Windows | `npx.cmd` | `-y @modelcontextprotocol/server-everything` |

**Auth** - None - connects without credentials.

**Prereq** - Node.js 18+ on the host so `npx` (or `npx.cmd`) can fetch and spawn the package. The catalog automatically picks the entry matching the host OS.

**Docker alternative** - if you'd rather not install Node, switch the form's **Command** to `docker` and **Arguments** to `["run", "-i", "--rm", "tzolov/mcp-everything-server:v2"]` before saving. This is the same form the playground's QA recipe uses as a smoke test.

**Why this server is catalogued under Examples**

MCP Everything is the only server in the ecosystem that *intentionally* implements every protocol primitive. That makes it the **"does my MCP client wire every primitive end-to-end?"** check for any new client - including Spring AI Playground itself. After Save & Connect:

| Inspector tab | What MCP Everything exposes |
|---|---|
| **Tools** | 15 tools, shown in the Inspector as **Echo Tool**, **Get Sum Tool**, **Print Environment Tool**, **Get Tiny Image Tool**, **GZip File as Resource Tool**, **Get Annotated Message Tool**, **Get Resource Links Tool**, **Get Resource Reference Tool**, **Get Structured Content Tool**, **Trigger Long Running Operation Tool**, **Trigger Sampling Request Tool**, **Trigger Elicitation Request Tool**, **Toggle Simulated Logging**, **Toggle Subscriber Updates**, **Simulate Research Query** - different argument schemas, progress notifications, and content shapes. As a vendor-official STDIO server they each compose to <span class="rl rl-l2">L2 - Low</span>; confirm the live set and exact tool names on the connection's [Inspector](../mcp-server/inspector.md#tools) |
| **Resources** | static resources (`test://static/resource/1` ... `100`) plus parameterised resource templates (`demo://resource/dynamic/text/{resourceId}` and `demo://resource/dynamic/blob/{resourceId}`) that render against the JSON-Schema-typed input panel |
| **Prompts** | `simple-prompt` (no args), `args-prompt` (city + state), `completable-prompt` (Team Management with an MCP-completion-backed `department` argument) |
| **Notifications** | live feed of `notifications/message`, `resources/updated`, `resources/list_changed`, `prompts/list_changed`, `tools/list_changed` - toggle on with `Toggle Subscriber Updates` to start the periodic resource-updated stream |
| **Sampling** | `Trigger Sampling Request Tool` invokes `sampling/createMessage` back into the playground (client primitive); the request lands as a Sampling-tab card with Approve / Reject |
| **Elicitation** | `Trigger Elicitation Request Tool` invokes `elicitation/create` (client primitive); the Elicitation tab renders the requested form |
| **Roots** | calls `roots/list` on startup - the Roots tab shows what was asked; if no roots are advertised, the server proceeds anyway |
| **Logging** | structured log records the Inspector folds into Notifications (`Toggle Simulated Logging` toggles the cadence) |
| **Completion** | `completion/complete` for argument auto-complete inside the Inspector (`completable-prompt`'s `department` argument shows this) |

If any of these don't behave as documented, the bug is almost certainly in the playground's client implementation, not in the server - which is the whole reason MCP Everything is shipped as the test target.

**Step-by-step walkthrough** - drive every row of the table above against a live MCP Everything connection in [Tutorial 9: MCP Everything - All 8 Primitives in One Walkthrough](../../tutorials/9-mcp-everything.md). Sample inputs (Echo, Add, Annotated Message), expected outputs (text + base64 image content), and the Sampling / Elicitation Approve-Reject cards are all spelled out there.

!!! tip "Hosted OAuth variant is intentionally not catalogued"
    The MCP working group also publishes a Streamable HTTP variant of MCP Everything at `https://example-server.modelcontextprotocol.io/` that requires an OAuth dance. That endpoint exists for spec-conformance demos and isn't included as a catalog entry - first-time users should hit the STDIO variant which needs no credentials at all. If you want to exercise the OAuth flow specifically, add a Custom Server entry pointing at that URL with the OAuth 2.1 preset.

**Description (full)**

[macOS] Official MCP reference test server exercising every protocol feature - tools, resources, prompts, sampling, completion, logging, progress, root listing.

The activated form is pre-filled to run: `npx -y @modelcontextprotocol/server-everything`. Docker alternative: `docker run -i --rm tzolov/mcp-everything-server:v2`. The hosted `example-server.modelcontextprotocol.io` endpoint requires OAuth and is not auto-activated.

**Docs** - [github.com/modelcontextprotocol/servers/tree/main/src/everything](https://github.com/modelcontextprotocol/servers/tree/main/src/everything)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="DeepWiki" data-tool-id="DeepWiki" data-tool-title="DeepWiki" markdown>
<div class="tcg-name"><span class="tcg-name__text">DeepWiki</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-book-search-outline:</div>
<div class="tcg-type">examples · free-tier <span class="risk risk-l0">ga</span> <span class="rl rl-l2">L2</span></div>
<div class="tcg-body" markdown>
Cognition's hosted, free, no-auth Streamable HTTP server that answers natural-language questions about any public GitHub repo, backed by auto-generated wikis. Catalogued under Examples because it's primarily useful as a *reference grounding* server for AI code generation - a concrete external MCP target with no signup friction.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Cognition · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None - free, no API key</div>
</div>
<div class="tcg-cta">Click for transport · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** - Cognition (vendor-official; the company behind Devin)

**Transport** - Streamable HTTP

**URL** - vendor-managed; the catalog entry carries the current production URL

**Auth** - None

**Cost** - Free

**Required env** - -

**Tools** - discovered on connect; the three this server publishes (all read-only, so each scores <span class="rl rl-l2">L2 - Low</span> on its own connection's Inspector):

??? abstract "Tools (3) - read_wiki_structure · read_wiki_contents · ask_question"
    - **`read_wiki_structure`** - list the documentation topics / sections for a repo. <span class="rl rl-l2">L2 - Low</span>
    - **`read_wiki_contents`** - fetch the full generated wiki text for a repo. <span class="rl rl-l2">L2 - Low</span>
    - **`ask_question`** - ask a natural-language question about a repo. <span class="rl rl-l2">L2 - Low</span>

**Why this server is catalogued under Examples**

DeepWiki is catalogued under Examples rather than Search because its primary use is as a *reference grounding* server for AI code generation - the kind of MCP target a tutorial points at when it wants a concrete external server with no signup friction. After Save & Connect the Tools tab exposes its three repo-wiki read tools - useful for verifying chat-side tool wiring without burning a vendor quota.

**Description (full)**

Free, no-auth Streamable HTTP server by Cognition that answers natural-language questions about any public GitHub repo, backed by auto-generated wikis. Three read-only tools - `read_wiki_structure`, `read_wiki_contents`, `ask_question`.

**Docs** - [deepwiki.com](https://deepwiki.com)

</div>
</div>

</div>

## When to reach for an Examples-category entry

- **Building a tool that depends on MCP push notifications?** Activate MCP Everything first; if its `tools/list_changed` notifications don't reach the playground, neither will yours.
- **Building a tool that needs Sampling or Elicitation?** Same - MCP Everything is the reference because those flows are inverted (server → client) and easy to misimplement.
- **Trying out the chat surface without configuring a vendor?** DeepWiki gives you a real, hosted, no-credential server that returns sensible content.
- **Validating production behaviour?** Use the catalog entry under the matching vendor category (Productivity, Dev, Search, ...); Examples is for protocol shape, not production data.

→ Hands-on per-primitive tour: [Tutorial 9 - MCP Everything: All 8 Primitives in One Walkthrough](../../tutorials/9-mcp-everything.md)  
