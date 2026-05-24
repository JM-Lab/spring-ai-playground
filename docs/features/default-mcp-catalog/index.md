description: Default MCP Catalog — 57 preset external MCP server connections (49 remote + 8 stdio per OS), browseable + filterable across 6 category pages.

# Default MCP Catalog

Spring AI Playground ships with **57 default MCP server connections** spread across **49 vendor-official remote entries** (Streamable HTTP — Gmail, Outlook, Notion, GitHub, Linear, Atlassian, Stripe, BigQuery, …) and **8 community stdio entries per OS** (`modelcontextprotocol/servers` — Git, Memory, Puppeteer, MCP Everything, …). They are ready to activate the moment the desktop launcher is running — you do not need to type a URL or hunt down a stdio command yourself to start chatting against an external service.

The MCP Server sidebar does not connect them all by default — every entry starts in the sidebar's **Inactive MCP** section as a *ghost row*. Clicking one promotes it into the right-hand configuration form pre-filled with the catalog template, so you only fill in your local secret / tenant before **Save & Connect**. The catalog itself lives in `src/main/resources/mcp/default-mcp-specs*.json` and the activation/filter state lives entirely in the sidebar.

## Sidebar filtering and form prefill { #sidebar-filtering-and-form-prefill }

The MCP Server screen has two cooperating regions — a **filter bar** at the top of the left sidebar (①) and a **connection form** in the right pane (②). Clicking any catalog row in the sidebar prefills the form on the right without leaving the page.

![MCP Server screen — ① numbered marker in the sidebar pointing to the filter bar (search + Categories + Tags multi-selects). ② numbered marker in the right pane pointing to the MCP Server Info connection form (Server name · Category · Tags · Description · Transport type · URL · Endpoint · Headers preset)](../../assets/images/default-mcp-catalog/sidebar-filter-callout.png){ width="640" loading=lazy }

### ① Filter bar { #filter-bar-detail }

Built from the shared `webui/common/sidebar/SidebarFilterBar` widget (Tool Studio uses the same one). Three controls that compose AND across groups, OR within a group:

- **Search** — matches against server name, vendor, description, and — for active connections only — live tool names returned by `listTools`. 200 ms debounce before re-rendering.
- **Categories** multi-select — the 13 built-in catalog categories plus `Custom` for user-added entries: `Example · Productivity · Storage · Communication · Project Management · Dev · Search · Cloud · Database · Finance · CRM · Design · Utility · Custom`.
- **Tags** multi-select — drawn from the union of every catalog entry's tags and every active server's tags. Cohort vocabulary: `aggregator · beta · community · free-tier · geo · global · korea · legal · pipeline · preview · us`.

For example, picking `Productivity` + `preview` narrows to Gmail and Google Calendar (the two preview-stability Workspace entries); a search term layered on top further trims the visible count. The sidebar header counter swaps between `(N)` and `(N filtered of M)` when a filter is active; an empty match offers a **Clear filters** button.

### ② Connection form { #form-prefill-detail }

Clicking any **Inactive MCP** entry copies the catalog template into the right pane:

- **Transport** — `STREAMABLE_HTTP` for remote entries, `STDIO` for the per-OS entries
- **URL** or **Command + Args** — with `${ENV_VAR}` placeholders for anything secret; STDIO arguments render as one row per argv element
- **OAuth issuer URI + scopes** — pre-filled for OAuth-protected entries
- **Category + Tag chips** — matching the catalog row
- **Inline description** — carrying prerequisites and a `Docs:` link
- **Headers preset** — Bearer / Basic / API Key / OAuth 2.1, with `${VAR}` substitution wired in

The row stays in the Inactive layer until you click **Save & Connect**; on save it moves into the Active layer under the same category group and the playground spawns the child process (STDIO) or opens the HTTP transport. For OAuth entries this records the registration without yet connecting — see [MCP Server → OAuth 2.1 Authorization Code](../mcp-server/index.md#oauth-21-authorization-code) for the **Authorize** click.

## Browse all 57 catalog entries { #browse-all-entries }

Click a card to jump to its full reference (with transport / auth defaults / required env / docs expanded) on the right sub-page — same UX as the **Default Tools** directory and the **MCP Server Setting** drawer in Tool Studio. Six reference pages organise the catalog by category cohort: [Productivity & Communication](productivity.md) · [Dev & Project Management](dev.md) · [Data & Cloud](data-cloud.md) · [Business](business.md) · [Search](search.md) · [Examples](examples.md).

**Filter modes**: combine a **search** keyword with one or more **Category** / **Tag** / **Transport** chips — search is AND across the chip filters; chip selections within a group are OR (a card is shown when its category OR tag OR transport matches *any* selected chip in that group, and the search keyword matches its name or vendor or description).

<div class="tool-directory" markdown>
<div class="tool-directory__controls">
<input type="search" class="tool-directory__search" placeholder="Search by name, vendor, or description…" aria-label="Search MCP catalog">
<div class="tool-directory__chips">
<span class="tool-directory__chip-label">Category</span> <button class="tool-directory__chip" data-group="category" data-value="cloud" aria-pressed="false">Cloud</button> <button class="tool-directory__chip" data-group="category" data-value="communication" aria-pressed="false">Communication</button> <button class="tool-directory__chip" data-group="category" data-value="crm" aria-pressed="false">Crm</button> <button class="tool-directory__chip" data-group="category" data-value="database" aria-pressed="false">Database</button> <button class="tool-directory__chip" data-group="category" data-value="design" aria-pressed="false">Design</button> <button class="tool-directory__chip" data-group="category" data-value="dev" aria-pressed="false">Dev</button> <button class="tool-directory__chip" data-group="category" data-value="example" aria-pressed="false">Example</button> <button class="tool-directory__chip" data-group="category" data-value="finance" aria-pressed="false">Finance</button> <button class="tool-directory__chip" data-group="category" data-value="productivity" aria-pressed="false">Productivity</button> <button class="tool-directory__chip" data-group="category" data-value="project_mgmt" aria-pressed="false">Project Mgmt</button> <button class="tool-directory__chip" data-group="category" data-value="search" aria-pressed="false">Search</button> <button class="tool-directory__chip" data-group="category" data-value="storage" aria-pressed="false">Storage</button> <button class="tool-directory__chip" data-group="category" data-value="util" aria-pressed="false">Util</button>
</div>
<div class="tool-directory__chips">
<span class="tool-directory__chip-label">Tag</span> <button class="tool-directory__chip" data-group="tag" data-value="aggregator" aria-pressed="false">aggregator</button> <button class="tool-directory__chip" data-group="tag" data-value="beta" aria-pressed="false">beta</button> <button class="tool-directory__chip" data-group="tag" data-value="community" aria-pressed="false">community</button> <button class="tool-directory__chip" data-group="tag" data-value="free-tier" aria-pressed="false">free-tier</button> <button class="tool-directory__chip" data-group="tag" data-value="geo" aria-pressed="false">geo</button> <button class="tool-directory__chip" data-group="tag" data-value="global" aria-pressed="false">global</button> <button class="tool-directory__chip" data-group="tag" data-value="korea" aria-pressed="false">korea</button> <button class="tool-directory__chip" data-group="tag" data-value="legal" aria-pressed="false">legal</button> <button class="tool-directory__chip" data-group="tag" data-value="pipeline" aria-pressed="false">pipeline</button> <button class="tool-directory__chip" data-group="tag" data-value="preview" aria-pressed="false">preview</button> <button class="tool-directory__chip" data-group="tag" data-value="us" aria-pressed="false">us</button>
</div>
<div class="tool-directory__chips">
<span class="tool-directory__chip-label">Transport</span> <button class="tool-directory__chip" data-group="transport" data-value="streamable-http" aria-pressed="false">Streamable HTTP</button> <button class="tool-directory__chip" data-group="transport" data-value="stdio" aria-pressed="false">STDIO</button>
</div>
</div>
<div class="tool-directory__count">Showing 57 of 57 entries</div>
<div class="tool-directory__list" markdown>

<div class="tcg-grid tcg-grid--directory" markdown>

<div class="tcg-card tcg-card--directory t-google" data-name="gmail" data-desc="read, search, and send gmail messages, manage labels and drafts. google workspace mcp (preview)." data-category="productivity" data-tags="global,preview" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Gmail" aria-label="Open Gmail">Gmail</a>
<div class="tcg-name"><span class="tcg-name__text">Gmail</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Gmail](https://cdn.simpleicons.org/gmail){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PRODUCTIVITY · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Read, search, and send Gmail messages, manage labels and drafts. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="outlook mail" data-desc="read, send, and organise outlook mail across folders. routed through microsoft 365 agent365 — requires your tenant id (ms_tenant_id)." data-category="productivity" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Outlook-Mail" aria-label="Open Outlook Mail">Outlook Mail</a>
<div class="tcg-name"><span class="tcg-name__text">Outlook Mail</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-outlook:</div>
<div class="tcg-type">PRODUCTIVITY <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read, send, and organise Outlook mail across folders. Routed through Microsoft 365 Agent365 — requires your tenant ID (MS_TENANT_ID).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google calendar" data-desc="create, list, and manage google calendar events, attendees, reminders, and recurring schedules. google workspace mcp (preview)." data-category="productivity" data-tags="global,preview" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Google-Calendar" aria-label="Open Google Calendar">Google Calendar</a>
<div class="tcg-name"><span class="tcg-name__text">Google Calendar</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Calendar](https://cdn.simpleicons.org/googlecalendar){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PRODUCTIVITY · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Create, list, and manage Google Calendar events, attendees, reminders, and recurring schedules. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="outlook calendar" data-desc="browse outlook calendars, schedule and update meetings, manage availability via microsoft 365 agent365. requires ms_tenant_id." data-category="productivity" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Outlook-Calendar" aria-label="Open Outlook Calendar">Outlook Calendar</a>
<div class="tcg-name"><span class="tcg-name__text">Outlook Calendar</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-outlook:</div>
<div class="tcg-type">PRODUCTIVITY <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse Outlook calendars, schedule and update meetings, manage availability via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="notion" data-desc="browse, create, and edit notion pages, databases, and properties. vendor-hosted remote mcp with oauth 2.1 + pkce." data-category="productivity" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Notion" aria-label="Open Notion">Notion</a>
<div class="tcg-name"><span class="tcg-name__text">Notion</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Notion](https://cdn.simpleicons.org/notion){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PRODUCTIVITY <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse, create, and edit Notion pages, databases, and properties. Vendor-hosted remote MCP with OAuth 2.1 + PKCE.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Notion · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="kakao playmcp" data-desc="kakao aggregator hub — kakaotalk send-to-self, talk calendar, kakaomap, gift, melon, plus 200+ third-party mcps. kr-focused." data-category="productivity" data-tags="korea,aggregator" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Kakao-PlayMCP" aria-label="Open Kakao PlayMCP">Kakao PlayMCP</a>
<div class="tcg-name"><span class="tcg-name__text">Kakao PlayMCP</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Kakao PlayMCP](https://cdn.simpleicons.org/kakaotalk){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PRODUCTIVITY · korea · aggregator <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Kakao aggregator hub — KakaoTalk send-to-self, Talk Calendar, KakaoMap, Gift, Melon, plus 200+ third-party MCPs. KR-focused.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Kakao · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory t-slack" data-name="slack" data-desc="read and post slack messages across channels and dms, search the workspace, manage user/channel metadata. slack's official remote mcp." data-category="communication" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Slack" aria-label="Open Slack">Slack</a>
<div class="tcg-name"><span class="tcg-name__text">Slack</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-pound-box-outline:</div>
<div class="tcg-type">COMMUNICATION <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read and post Slack messages across channels and DMs, search the workspace, manage user/channel metadata. Slack's official remote MCP.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Slack · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="microsoft teams" data-desc="send messages to teams chats and channels, search conversations, manage meetings via microsoft 365 agent365. requires ms_tenant_id." data-category="communication" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="productivity/#Microsoft-Teams" aria-label="Open Microsoft Teams">Microsoft Teams</a>
<div class="tcg-name"><span class="tcg-name__text">Microsoft Teams</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-teams:</div>
<div class="tcg-type">COMMUNICATION <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Send messages to Teams chats and channels, search conversations, manage meetings via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Productivity & Communication</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="linear" data-desc="create, query, and update linear issues, projects, cycles, and teams. linear's official remote mcp with oauth 2.1." data-category="project_mgmt" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Linear" aria-label="Open Linear">Linear</a>
<div class="tcg-name"><span class="tcg-name__text">Linear</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Linear](https://cdn.simpleicons.org/linear){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PROJECT_MGMT <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Create, query, and update Linear issues, projects, cycles, and teams. Linear's official remote MCP with OAuth 2.1.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Linear · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="atlassian rovo (jira + confluence)" data-desc="read and update jira issues and confluence pages via atlassian's official rovo mcp. replaces legacy /v1/sse on 2026-06-30." data-category="project_mgmt" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Atlassian" aria-label="Open Atlassian Rovo (Jira + Confluence)">Atlassian Rovo (Jira + Confluence)</a>
<div class="tcg-name"><span class="tcg-name__text">Atlassian Rovo (Jira + Confluence)</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Atlassian Rovo (Jira + Confluence)](https://cdn.simpleicons.org/atlassian){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PROJECT_MGMT <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read and update Jira issues and Confluence pages via Atlassian's official Rovo MCP. Replaces legacy /v1/sse on 2026-06-30.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Atlassian · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory t-github" data-name="github" data-desc="search and manage github repositories, issues, pull requests, code reviews, and actions. sign in via oauth device flow or pass a personal access token…" data-category="dev" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#GitHub" aria-label="Open GitHub">GitHub</a>
<div class="tcg-name"><span class="tcg-name__text">GitHub</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![GitHub](https://cdn.simpleicons.org/github){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DEV <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search and manage GitHub repositories, issues, pull requests, code reviews, and Actions. Sign in via OAuth Device Flow or pass a personal access token…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; GitHub · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1 / PAT</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="microsoft learn" data-desc="search the full microsoft learn documentation catalogue — azure, .net, windows, power platform, microsoft 365 and more. no authentication required, free to use." data-category="dev" data-tags="global,free-tier" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Microsoft-Learn" aria-label="Open Microsoft Learn">Microsoft Learn</a>
<div class="tcg-name"><span class="tcg-name__text">Microsoft Learn</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-school-outline:</div>
<div class="tcg-type">DEV · free-tier <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search the full Microsoft Learn documentation catalogue — Azure, .NET, Windows, Power Platform, Microsoft 365 and more. No authentication required, free to use.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="sentry" data-desc="investigate sentry issues and events across projects, query alert rules, releases, and performance data. oauth with device-code flow." data-category="dev" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Sentry" aria-label="Open Sentry">Sentry</a>
<div class="tcg-name"><span class="tcg-name__text">Sentry</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Sentry](https://cdn.simpleicons.org/sentry){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DEV <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Investigate Sentry issues and events across projects, query alert rules, releases, and performance data. OAuth with device-code flow.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Sentry · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="asana" data-desc="manage asana tasks, projects, sections, and custom fields across workspaces. asana's official oauth mcp (sse transport)." data-category="project_mgmt" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Asana" aria-label="Open Asana">Asana</a>
<div class="tcg-name"><span class="tcg-name__text">Asana</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Asana](https://cdn.simpleicons.org/asana){ width="40" .tcg-favicon }</div>
<div class="tcg-type">PROJECT_MGMT <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Asana tasks, projects, sections, and custom fields across workspaces. Asana's official OAuth MCP (SSE transport).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Asana · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="azure devops" data-desc="query and update azure devops repos, work items (boards), pipelines, and pull requests within your organisation. preview, requires azure_devops_org." data-category="dev" data-tags="global,preview" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Azure-DevOps" aria-label="Open Azure DevOps">Azure DevOps</a>
<div class="tcg-name"><span class="tcg-name__text">Azure DevOps</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-azure-devops:</div>
<div class="tcg-type">DEV · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Query and update Azure DevOps repos, work items (Boards), pipelines, and pull requests within your organisation. Preview, requires AZURE_DEVOPS_ORG.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="context7" data-desc="pull up-to-date library documentation and code examples by library name and version, designed to ground ai code generation. free, no authentication." data-category="dev" data-tags="global,free-tier" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Context7" aria-label="Open Context7">Context7</a>
<div class="tcg-name"><span class="tcg-name__text">Context7</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>![Context7](https://cdn.simpleicons.org/upstash){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DEV · free-tier <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Pull up-to-date library documentation and code examples by library name and version, designed to ground AI code generation. Free, no authentication.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Upstash · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="korean law mcp" data-desc="search korean law, court precedents, constitutional court decisions, ordinances, and administrative rules. wraps the ministry of government legislation open…" data-category="dev" data-tags="korea,legal" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="dev/#Korean-Law-MCP" aria-label="Open Korean Law MCP">Korean Law MCP</a>
<div class="tcg-name"><span class="tcg-name__text">Korean Law MCP</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-scale-balance:</div>
<div class="tcg-type">DEV · korea · legal <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search Korean law, court precedents, Constitutional Court decisions, ordinances, and administrative rules. Wraps the Ministry of Government Legislation Open…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; chrisryugj · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="git" data-desc="[macos] local git repository operations — log, diff, status, blame, show. read-only by default. the activated form is pre-filled to run: uvx mcp-server-git…" data-category="dev" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="dev/#Git" aria-label="Open Git">Git</a>
<div class="tcg-name"><span class="tcg-name__text">Git</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Git](https://cdn.simpleicons.org/git){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DEV · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Local Git repository operations — log, diff, status, blame, show. Read-only by default. The activated form is pre-filled to run: uvx mcp-server-git…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="puppeteer" data-desc="[macos] headless chrome automation — navigate, click, fill forms, screenshot, evaluate javascript in-page. the activated form is pre-filled to run: npx -y…" data-category="dev" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="dev/#Puppeteer" aria-label="Open Puppeteer">Puppeteer</a>
<div class="tcg-name"><span class="tcg-name__text">Puppeteer</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Puppeteer](https://cdn.simpleicons.org/puppeteer){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DEV · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Headless Chrome automation — navigate, click, fill forms, screenshot, evaluate JavaScript in-page. The activated form is pre-filled to run: npx -y…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="playwright" data-desc="[macos] microsoft's accessibility-snapshot browser automation — successor to the puppeteer reference. drives chromium/firefox/webkit without screenshot vision…" data-category="dev" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="dev/#Playwright" aria-label="Open Playwright">Playwright</a>
<div class="tcg-name"><span class="tcg-name__text">Playwright</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-script-text-play-outline:</div>
<div class="tcg-type">DEV · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Microsoft's accessibility-snapshot browser automation — successor to the Puppeteer reference. Drives Chromium/Firefox/WebKit without screenshot vision…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Dev & Project Management</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google drive" data-desc="list, read, and upload files in google drive, manage permissions, shared drives, and folder structure. google workspace mcp (preview)." data-category="storage" data-tags="global,preview" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Google-Drive" aria-label="Open Google Drive">Google Drive</a>
<div class="tcg-name"><span class="tcg-name__text">Google Drive</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Drive](https://cdn.simpleicons.org/googledrive){ width="40" .tcg-favicon }</div>
<div class="tcg-type">STORAGE · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
List, read, and upload files in Google Drive, manage permissions, shared drives, and folder structure. Google Workspace MCP (Preview).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-microsoft" data-name="onedrive & sharepoint" data-desc="browse and edit onedrive personal files and sharepoint sites, lists, document libraries via microsoft 365 agent365. requires ms_tenant_id." data-category="storage" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#OneDrive-SharePoint" aria-label="Open OneDrive & SharePoint">OneDrive & SharePoint</a>
<div class="tcg-name"><span class="tcg-name__text">OneDrive & SharePoint</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-onedrive:</div>
<div class="tcg-type">STORAGE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse and edit OneDrive personal files and SharePoint sites, lists, document libraries via Microsoft 365 Agent365. Requires MS_TENANT_ID.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="cloudflare" data-desc="cloudflare umbrella covering workers, r2, kv, d1, workers ai, browser rendering, hyperdrive, queues, logs, and observability." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Cloudflare" aria-label="Open Cloudflare">Cloudflare</a>
<div class="tcg-name"><span class="tcg-name__text">Cloudflare</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Cloudflare](https://cdn.simpleicons.org/cloudflare){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Cloudflare umbrella covering Workers, R2, KV, D1, Workers AI, Browser Rendering, Hyperdrive, Queues, Logs, and observability.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Cloudflare · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="vercel" data-desc="inspect and manage vercel projects, deployments, domains, environment variables, and observability via oauth." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Vercel" aria-label="Open Vercel">Vercel</a>
<div class="tcg-name"><span class="tcg-name__text">Vercel</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Vercel](https://cdn.simpleicons.org/vercel){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Inspect and manage Vercel projects, deployments, domains, environment variables, and observability via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Vercel · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="netlify" data-desc="manage netlify sites, deploys, build hooks, edge functions, and form submissions via oauth." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Netlify" aria-label="Open Netlify">Netlify</a>
<div class="tcg-name"><span class="tcg-name__text">Netlify</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Netlify](https://cdn.simpleicons.org/netlify){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Netlify sites, deploys, build hooks, edge functions, and form submissions via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Netlify · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="heroku" data-desc="inspect heroku apps, dynos, releases, config vars, addons, and logs via oauth." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Heroku" aria-label="Open Heroku">Heroku</a>
<div class="tcg-name"><span class="tcg-name__text">Heroku</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-cloud-cog-outline:</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Inspect Heroku apps, dynos, releases, config vars, addons, and logs via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Heroku · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="render" data-desc="manage render web services, background workers, cron jobs, and deploys via oauth." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Render" aria-label="Open Render">Render</a>
<div class="tcg-name"><span class="tcg-name__text">Render</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Render](https://cdn.simpleicons.org/render){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Render web services, background workers, cron jobs, and deploys via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Render · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google cloud run" data-desc="deploy and manage cloud run services, revisions, traffic splits, and jobs. uses google oauth with the cloud-platform scope." data-category="cloud" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Google-Cloud-Run" aria-label="Open Google Cloud Run">Google Cloud Run</a>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Run</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Run](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CLOUD <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Deploy and manage Cloud Run services, revisions, traffic splits, and jobs. Uses Google OAuth with the cloud-platform scope.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google cloud storage" data-desc="manage gcs buckets and objects — list, upload, download, iam, lifecycle rules. uses google oauth." data-category="storage" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Google-Cloud-Storage" aria-label="Open Google Cloud Storage">Google Cloud Storage</a>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Storage</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Storage](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">STORAGE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage GCS buckets and objects — list, upload, download, IAM, lifecycle rules. Uses Google OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="bigquery" data-desc="query datasets, manage tables and views, and run jobs in google bigquery. google oauth with the bigquery scope." data-category="database" data-tags="global,pipeline" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#BigQuery" aria-label="Open BigQuery">BigQuery</a>
<div class="tcg-name"><span class="tcg-name__text">BigQuery</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![BigQuery](https://cdn.simpleicons.org/googlebigquery){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE · pipeline <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Query datasets, manage tables and views, and run jobs in Google BigQuery. Google OAuth with the bigquery scope.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="neon" data-desc="provision and query neon serverless postgres — branches, roles, schema migrations, query analysis. oauth." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Neon" aria-label="Open Neon">Neon</a>
<div class="tcg-name"><span class="tcg-name__text">Neon</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Neon](https://cdn.simpleicons.org/neon){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Provision and query Neon serverless Postgres — branches, roles, schema migrations, query analysis. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Neon · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="supabase" data-desc="manage supabase projects end-to-end — postgres queries, auth users, storage buckets, edge functions. oauth." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Supabase" aria-label="Open Supabase">Supabase</a>
<div class="tcg-name"><span class="tcg-name__text">Supabase</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Supabase](https://cdn.simpleicons.org/supabase){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Supabase projects end-to-end — Postgres queries, Auth users, Storage buckets, Edge Functions. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Supabase · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="planetscale" data-desc="query planetscale mysql databases, manage branches, deploy schema changes via oauth." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#PlanetScale" aria-label="Open PlanetScale">PlanetScale</a>
<div class="tcg-name"><span class="tcg-name__text">PlanetScale</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![PlanetScale](https://cdn.simpleicons.org/planetscale){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Query PlanetScale MySQL databases, manage branches, deploy schema changes via OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; PlanetScale · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google cloud sql" data-desc="administer google cloud sql instances (mysql, postgres, sql server) — databases, users, backups, and queries." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Cloud-SQL" aria-label="Open Google Cloud SQL">Google Cloud SQL</a>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud SQL</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud SQL](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Administer Google Cloud SQL instances (MySQL, Postgres, SQL Server) — databases, users, backups, and queries.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google cloud spanner" data-desc="query google cloud spanner instances and databases — globally distributed, strong consistency, sql." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Spanner" aria-label="Open Google Cloud Spanner">Google Cloud Spanner</a>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Spanner</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Spanner](https://cdn.simpleicons.org/googlecloud){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Query Google Cloud Spanner instances and databases — globally distributed, strong consistency, SQL.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google cloud firestore" data-desc="query and manage firestore documents, collections, composite indexes, and security rules via google oauth." data-category="database" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="data-cloud/#Firestore" aria-label="Open Google Cloud Firestore">Google Cloud Firestore</a>
<div class="tcg-name"><span class="tcg-name__text">Google Cloud Firestore</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Cloud Firestore](https://cdn.simpleicons.org/firebase){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Query and manage Firestore documents, collections, composite indexes, and security rules via Google OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="sqlite" data-desc="[macos] query a local sqlite database — select, schema introspection, plus insert/update/delete when permitted. the activated form is pre-filled to run: uvx…" data-category="database" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="data-cloud/#SQLite" aria-label="Open SQLite">SQLite</a>
<div class="tcg-name"><span class="tcg-name__text">SQLite</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![SQLite](https://cdn.simpleicons.org/sqlite){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DATABASE · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Query a local SQLite database — SELECT, schema introspection, plus INSERT/UPDATE/DELETE when permitted. The activated form is pre-filled to run: uvx…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers-archived · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Data & Cloud</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="stripe" data-desc="manage stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and connect. oauth." data-category="finance" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Stripe" aria-label="Open Stripe">Stripe</a>
<div class="tcg-name"><span class="tcg-name__text">Stripe</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Stripe](https://cdn.simpleicons.org/stripe){ width="40" .tcg-favicon }</div>
<div class="tcg-type">FINANCE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Stripe payments, customers, subscriptions, invoices, refunds, product catalogue, and Connect. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Stripe · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="paypal" data-desc="manage paypal orders, refunds, payouts, subscriptions, and invoicing through paypal's official oauth mcp (sse)." data-category="finance" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#PayPal" aria-label="Open PayPal">PayPal</a>
<div class="tcg-name"><span class="tcg-name__text">PayPal</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![PayPal](https://cdn.simpleicons.org/paypal){ width="40" .tcg-favicon }</div>
<div class="tcg-type">FINANCE <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage PayPal orders, refunds, payouts, subscriptions, and invoicing through PayPal's official OAuth MCP (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; PayPal · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="square" data-desc="square payments, catalogue, inventory, customers, and orders across locations. beta program from block." data-category="finance" data-tags="global,beta" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Square" aria-label="Open Square">Square</a>
<div class="tcg-name"><span class="tcg-name__text">Square</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Block](https://cdn.simpleicons.org/cashapp){ width="40" .tcg-favicon }</div>
<div class="tcg-type">FINANCE · beta <span class="risk risk-l4">beta</span></div>
<div class="tcg-body" markdown>
Square payments, catalogue, inventory, customers, and orders across locations. Beta program from Block.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Block · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="hubspot" data-desc="hubspot crm contacts, companies, deals, pipelines, lists, and engagements. oauth 2.1 + pkce." data-category="crm" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#HubSpot" aria-label="Open HubSpot">HubSpot</a>
<div class="tcg-name"><span class="tcg-name__text">HubSpot</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![HubSpot](https://cdn.simpleicons.org/hubspot){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CRM <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
HubSpot CRM contacts, companies, deals, pipelines, lists, and engagements. OAuth 2.1 + PKCE.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; HubSpot · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="intercom" data-desc="intercom conversations, contacts, tags, segments, and help-centre articles. us workspace region only." data-category="crm" data-tags="us" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Intercom" aria-label="Open Intercom">Intercom</a>
<div class="tcg-name"><span class="tcg-name__text">Intercom</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Intercom](https://cdn.simpleicons.org/intercom){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CRM · us <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Intercom conversations, contacts, tags, segments, and help-centre articles. US workspace region only.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Intercom · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="mixpanel" data-desc="run mixpanel product analytics queries — events, funnels, retention, cohorts, and user properties. oauth." data-category="crm" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Mixpanel" aria-label="Open Mixpanel">Mixpanel</a>
<div class="tcg-name"><span class="tcg-name__text">Mixpanel</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Mixpanel](https://cdn.simpleicons.org/mixpanel){ width="40" .tcg-favicon }</div>
<div class="tcg-type">CRM <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Run Mixpanel product analytics queries — events, funnels, retention, cohorts, and user properties. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Mixpanel · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="figma" data-desc="browse figma files, frames, components, styles, comments, and design system tokens. oauth." data-category="design" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Figma" aria-label="Open Figma">Figma</a>
<div class="tcg-name"><span class="tcg-name__text">Figma</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Figma](https://cdn.simpleicons.org/figma){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DESIGN <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Browse Figma files, frames, components, styles, comments, and design system tokens. OAuth.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Figma · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="canva" data-desc="manage canva designs, brand kits, folders, templates, and assets through the official canva connect mcp." data-category="design" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Canva" aria-label="Open Canva">Canva</a>
<div class="tcg-name"><span class="tcg-name__text">Canva</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-palette-outline:</div>
<div class="tcg-type">DESIGN <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Canva designs, brand kits, folders, templates, and assets through the official Canva Connect MCP.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Canva · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="webflow" data-desc="manage webflow sites, cms collections, items, and form submissions via oauth (sse)." data-category="design" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Webflow" aria-label="Open Webflow">Webflow</a>
<div class="tcg-name"><span class="tcg-name__text">Webflow</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Webflow](https://cdn.simpleicons.org/webflow){ width="40" .tcg-favicon }</div>
<div class="tcg-type">DESIGN <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Webflow sites, CMS collections, items, and form submissions via OAuth (SSE).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Webflow · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory t-google" data-name="google maps grounding" data-desc="google maps places, directions, distance matrix, geocoding, and street view for grounding llm responses with real-world geo data." data-category="util" data-tags="global,geo" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="business/#Maps-Grounding" aria-label="Open Google Maps Grounding">Google Maps Grounding</a>
<div class="tcg-name"><span class="tcg-name__text">Google Maps Grounding</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Google Maps Grounding](https://cdn.simpleicons.org/googlemaps){ width="40" .tcg-favicon }</div>
<div class="tcg-type">UTIL · geo <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Google Maps Places, Directions, Distance Matrix, Geocoding, and Street View for grounding LLM responses with real-world geo data.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Google · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="memory (knowledge graph)" data-desc="[macos] persistent knowledge graph for cross-session memory — entities, relations, and observations in a local json file. the activated form is pre-filled to…" data-category="util" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="business/#Memory" aria-label="Open Memory (Knowledge Graph)">Memory (Knowledge Graph)</a>
<div class="tcg-name"><span class="tcg-name__text">Memory (Knowledge Graph)</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-graph-outline:</div>
<div class="tcg-type">UTIL · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Persistent knowledge graph for cross-session memory — entities, relations, and observations in a local JSON file. The activated form is pre-filled to…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="sequential thinking" data-desc="[macos] structured step-by-step reasoning helper — logs intermediate thoughts to the server for review, revision, or branching. the activated form is…" data-category="util" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="business/#Sequential-Thinking" aria-label="Open Sequential Thinking">Sequential Thinking</a>
<div class="tcg-name"><span class="tcg-name__text">Sequential Thinking</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-thought-bubble-outline:</div>
<div class="tcg-type">UTIL · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Structured step-by-step reasoning helper — logs intermediate thoughts to the server for review, revision, or branching. The activated form is…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Business</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="tavily" data-desc="ai-optimised web search and answer engine for grounding llms with up-to-date facts and sources. requires tavily_api_key." data-category="search" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="search/#Tavily" aria-label="Open Tavily">Tavily</a>
<div class="tcg-name"><span class="tcg-name__text">Tavily</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-magnify-scan:</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
AI-optimised web search and answer engine for grounding LLMs with up-to-date facts and sources. Requires TAVILY_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Tavily · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="exa" data-desc="neural web search by exa — semantic ranking, source-aware retrieval, and high-quality result snippets. requires exa_api_key (x-api-key header)." data-category="search" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="search/#Exa" aria-label="Open Exa">Exa</a>
<div class="tcg-name"><span class="tcg-name__text">Exa</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-magnify-expand:</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Neural web search by Exa — semantic ranking, source-aware retrieval, and high-quality result snippets. Requires EXA_API_KEY (x-api-key header).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Exa · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="firecrawl" data-desc="web scraping, crawling, and structured data extraction with anti-bot handling. bearer-authenticated with firecrawl_api_key." data-category="search" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="search/#Firecrawl" aria-label="Open Firecrawl">Firecrawl</a>
<div class="tcg-name"><span class="tcg-name__text">Firecrawl</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-spider-web:</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Web scraping, crawling, and structured data extraction with anti-bot handling. Bearer-authenticated with FIRECRAWL_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Firecrawl · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;Bearer</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="jina ai" data-desc="jina ai reader (url-to-clean-markdown) and search apis for llm-grounded retrieval. bearer-authenticated with jina_api_key." data-category="search" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="search/#Jina-AI" aria-label="Open Jina AI">Jina AI</a>
<div class="tcg-name"><span class="tcg-name__text">Jina AI</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-vector-link:</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Jina AI Reader (URL-to-clean-markdown) and search APIs for LLM-grounded retrieval. Bearer-authenticated with JINA_API_KEY.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Jina AI · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;Bearer</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="serpapi" data-desc="real-time google, bing, baidu, duckduckgo, naver and other serp scraping with structured json. api key embedded in url path (serpapi_api_key)." data-category="search" data-tags="global" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="search/#SerpAPI" aria-label="Open SerpAPI">SerpAPI</a>
<div class="tcg-name"><span class="tcg-name__text">SerpAPI</span> <span class="cost">🔑</span></div>
<div class="tcg-art" markdown>:material-google:</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Real-time Google, Bing, Baidu, DuckDuckGo, Naver and other SERP scraping with structured JSON. API key embedded in URL path (SERPAPI_API_KEY).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; SerpAPI · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;API key</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="brave search" data-desc="[macos] brave search api — web, local, news, image, and video search. outbound calls only to api.search.brave.com (no ssrf surface). the activated form is…" data-category="search" data-tags="global" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="search/#Brave-Search" aria-label="Open Brave Search">Brave Search</a>
<div class="tcg-name"><span class="tcg-name__text">Brave Search</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Brave Search](https://cdn.simpleicons.org/brave){ width="40" .tcg-favicon }</div>
<div class="tcg-type">SEARCH <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Brave Search API — web, local, news, image, and video search. Outbound calls only to api.search.brave.com (no SSRF surface). The activated form is…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Brave · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Search</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="deepwiki" data-desc="ai-generated, structured wikis for any public github repository — architecture diagrams, module-level explanations, navigable source links, and a…" data-category="example" data-tags="global,free-tier" data-transport="streamable-http" markdown>
<a class="tcg-stretched-link" href="examples/#DeepWiki" aria-label="Open DeepWiki">DeepWiki</a>
<div class="tcg-name"><span class="tcg-name__text">DeepWiki</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-book-search-outline:</div>
<div class="tcg-type">EXAMPLE · free-tier <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
AI-generated, structured wikis for any public GitHub repository — architecture diagrams, module-level explanations, navigable source links, and a…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Cognition · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

<div class="tcg-card tcg-card--directory" data-name="mcp everything (reference test server)" data-desc="[macos] official mcp reference test server exercising every protocol feature — tools, resources, prompts, sampling, completion, logging, progress, root…" data-category="example" data-tags="global,community" data-transport="stdio" markdown>
<a class="tcg-stretched-link" href="examples/#MCP-Everything" aria-label="Open MCP Everything (Reference Test Server)">MCP Everything (Reference Test Server)</a>
<div class="tcg-name"><span class="tcg-name__text">MCP Everything (Reference Test Server)</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-flask-outline:</div>
<div class="tcg-type">EXAMPLE · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Official MCP reference test server exercising every protocol feature — tools, resources, prompts, sampling, completion, logging, progress, root…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-page">→ Examples</div>
</div>

</div>

</div>
</div>

## Two ways to use these entries

### Activate from the sidebar

The simplest mode — open MCP Server, scroll the sidebar's **Inactive MCP** layer, click the entry you want. The right pane fills in:

- transport (`STREAMABLE_HTTP` for remote entries, `STDIO` for the per-OS entries)
- URL or command + args, with `${ENV_VAR}` placeholders for anything secret
- default OAuth issuer URI + scopes for OAuth-protected vendors
- category + tags chips matching the catalog row
- inline description with prerequisites and the upstream `Docs:` link

Fill in only what's local to you (an API key, a tenant ID, the OAuth **Authorize** click) and **Save & Connect**. The row moves into the **Active MCP** sidebar layer; the in-app **MCP Inspector** becomes live; the connection is available to **Agentic Chat** as a tool source.

- → [MCP Server: Catalog & Sidebar Filtering](../mcp-server/index.md#catalog-sidebar-filtering) — 3-layer sidebar · filter bar · activation
- → [MCP Server: OAuth 2.1 Authorization Code](../mcp-server/index.md#oauth-21-authorization-code) — for the OAuth entries (Gmail, Outlook, Notion, Linear, Atlassian, …)
- → [MCP Server: MCP Inspector](../mcp-server/index.md#mcp-inspector) — exercise tools, resources, prompts before relying on the connection from chat
- → [Agentic Chat](../agentic-chat.md) — call them from a model conversation
- → [Tutorial 2 — Connect an External MCP Server](../../tutorials/2-external-mcp.md) — first-time activation walkthrough

### Compose with custom servers and authored tools

The deeper mode — combine multiple catalog connections with your own custom servers and Tool Studio-authored tools in one chat. Agentic Chat picks any subset of active MCP servers per turn, so an agent can read your Notion + look up a Linear ticket + write a Slack reply in the same conversation. The catalog is the **fastest path** to that composition because it skips the URL-typing / OAuth-discovery / requiredEnv guesswork for the most common vendors.

- → [Tool Studio: Key Tool Studio Capabilities](../tool-studio/index.md#key-tool-studio-capabilities) — author and publish custom tools that ride alongside catalog connections
- → [Default Tools directory](../default-tools/index.md) — the parallel 86-tool JavaScript inventory the built-in MCP server publishes

## End-to-end flow

```text
[ Catalog entry  · Custom Server form ]
            │
            ▼
   [ Configuration form (pre-filled or blank) ]
            │
            ▼
   [ Save & Connect  ✅ ] ──── connection becomes Active
            │
            ▼
   [ Active MCP connection ]
            │
            ├── In-app MCP Inspector — exercise tools / resources / prompts / sampling / elicitation
            │
            ├── Agentic Chat — call the connection's tools from a model turn
            │
            └── (planned) Aggregator re-publish — combine the tools of multiple
                active catalog connections into a single MCP endpoint the
                playground re-exposes to external clients. Not in M7 — external
                HTTP / stdio clients today see only the Default-Tools surface
                (the built-in MCP server) at http://localhost:8282/mcp.
```

The catalog only changes step 1 (entry selection + form pre-fill). Everything after — Test Connection, OAuth Authorize, Inspector, Agentic Chat — is the same path a custom server takes. The catalog is sugar on top of the connection management surface, not a separate runtime.

!!! info "Aggregator re-publish is planned, not shipped"
    Today a catalog connection (or a Custom Server) is **per-playground** — it's reachable from the in-app Inspector and from Agentic Chat, but it is not re-exposed to external MCP clients. The planned aggregator pattern will let the playground compose the tool sets of several active catalog connections into a single MCP server it re-publishes (so an external client could call `gmail.search` + `linear.createIssue` through one endpoint without negotiating each vendor's OAuth separately). Until that ships, external clients reach only the **Default Tools** surface via the built-in MCP server at `http://localhost:8282/mcp` — see [Default Tools → Expose and call](../default-tools/index.md#expose-and-call) for the current external-client paths.

## Why a built-in catalog matters

Most MCP client implementations require the user to **type each external server's URL by hand**, look up OAuth issuer URIs from a vendor doc, and discover which env vars `requiredEnv` declares. That's three lookup hops before any tool runs.

Spring AI Playground's catalog **pre-resolves those hops**. The 49 remote entries carry the vendor-recommended Streamable HTTP URL + OAuth defaults + tenant-ID hints; the 8 stdio entries carry the `npx` / `uvx` command + args verified against `modelcontextprotocol/servers`. The desktop launcher even picks the OS-matching stdio variant automatically (`npx` for macOS / Linux, `npx.cmd` for Windows) so the pre-filled command can be saved without editing on any host.

Every entry is either **vendor-official** (Tier 1 — listed under the vendor's own documentation) or **community-published** (Tier 2 — `modelcontextprotocol/servers` and adjacent maintained projects). The catalog file shipped with the app is the source of truth; updates land as part of regular releases rather than requiring users to chase vendor doc URLs.

## Composition recipes

The reference pages list what's available; composition recipes show how to chain multiple catalog connections (and custom tools) into a useful workflow. Three combinations worth bookmarking:

- **PR digest → Slack** — `GitHub` (catalog) → `openaiResponseGenerator` (default tool) → `Slack` (catalog), ending in an agent that posts release-note summaries to a channel.
- **Mail + calendar triage** — `Gmail` (catalog) → `Google Calendar` (catalog) → custom tool, ending in an agent that drafts replies and books follow-ups in one turn.
- **Issue tracker grounding** — `Linear` (catalog) → `Atlassian Rovo` (catalog) → `Notion` (catalog), ending in an agent that cross-references tickets across all three trackers when answering project questions.

All three live on Streamable HTTP with OAuth 2.1 — once you've completed the **Authorize** click for each vendor, the playground holds encrypted refresh tokens and the agent reaches all three without re-prompting.

## Environment variables — short list

Some catalog entries depend on environment-backed values and stay inert until those are set. The full per-entry breakdown lives on each reference page; the most common are:

| Env var | Used by | Why |
|---|---|---|
| `MS_TENANT_ID` | `Outlook Mail`, `Outlook Calendar`, `Microsoft Teams` | Microsoft 365 Agent365 tenant routing — entries fail fast at connect time if missing |
| `MEMORY_FILE_PATH` | `Memory (Knowledge Graph)` (stdio) | Override the default JSON store path for cross-session memory |
| `BRAVE_API_KEY` | `Brave Search` (stdio) | Brave Search API key — passed through stdio `env` |
| `TAVILY_API_KEY` / `EXA_API_KEY` / `SERPAPI_KEY` / `JINA_API_KEY` / `FIRECRAWL_API_KEY` | Search-category entries | Vendor API keys for Tavily, Exa, SerpAPI, Jina AI, Firecrawl — surfaced via `${VAR}` placeholders in the headers |
| OAuth client secrets | Any OAuth entry with a non-public client | Storable as `${SOME_OAUTH_CLIENT_SECRET}` in the OAuth sub-form's **Client secret** field |

`${VAR}` placeholders in any value field resolve from the OS environment (or a JVM system-property fallback) at connect time; the persisted JSON keeps the literal placeholder string, never the resolved secret. A backend `SecretMasking` filter additionally sweeps connection-error notifications and per-call logs to replace resolved values with `***` so credentials cannot leak into the playground UI — see [Safety Architecture → Secret masking](../../safety-architecture.md#secret-masking).

→ [MCP Server: `${ENV_VAR}` substitution](../mcp-server/index.md#custom-http-headers-and-env_var-substitution) — placeholder syntax and the missing-reference fail-fast behaviour.
