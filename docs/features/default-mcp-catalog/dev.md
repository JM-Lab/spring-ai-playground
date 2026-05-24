description: Default MCP Catalog — Dev & Project Management reference: 12 preset MCP connections with transport, auth, required env, and full description per card.

# Default MCP Catalog — Dev & Project Management

Code hosting, issue trackers, code-quality monitors, learning content, and local repo helpers. Mixes vendor-official remote entries (GitHub, Linear, Atlassian Rovo, Sentry, Asana, Azure DevOps, Microsoft Learn, Context7, Korean Law) with community stdio entries from `modelcontextprotocol/servers` (Git, Puppeteer, Playwright).

## Entries (12)

Click any card to expand the full spec inline — transport (Streamable HTTP / STDIO), authentication shape (OAuth 2.1 / API key / Bearer / none), required environment variables, vendor URL or stdio command, and the upstream docs link.

<div class="tcg-grid" markdown>

<div class="tcg-card tcg-card--clickable" id="Linear" data-tool-id="Linear" data-tool-title="Linear" markdown>
<div class="tcg-name"><span class="tcg-name__text">Linear</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Linear](https://cdn.simpleicons.org/linear){ width="40" .tcg-favicon }</div>
<div class="tcg-type">project_mgmt · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Create, query, and update Linear issues, projects, cycles, and teams. Linear's official remote MCP with OAuth 2.1.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Linear · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Linear (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.linear.app/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global

**Description**

Create, query, and update Linear issues, projects, cycles, and teams. Linear's official remote MCP with OAuth 2.1.

Docs: https://linear.app/docs/mcp

**Docs** — [https://linear.app/docs/mcp](https://linear.app/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Atlassian" data-tool-id="Atlassian" data-tool-title="Atlassian Rovo (Jira + Confluence)" markdown>
<div class="tcg-name"><span class="tcg-name__text">Atlassian Rovo (Jira + Confluence)</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Atlassian Rovo (Jira + Confluence)](https://cdn.simpleicons.org/atlassian){ width="40" .tcg-favicon }</div>
<div class="tcg-type">project_mgmt · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Read and update Jira issues and Confluence pages via Atlassian's official Rovo MCP. Replaces legacy /v1/sse on 2026-06-30.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Atlassian · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Atlassian (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.atlassian.com/v1/mcp/authv2`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global

**Description**

Read and update Jira issues and Confluence pages via Atlassian's official Rovo MCP. Replaces legacy /v1/sse on 2026-06-30.

Docs: https://support.atlassian.com/atlassian-rovo-mcp-server/docs/getting-started-with-the-atlassian-remote-mcp-server/

**Docs** — [https://support.atlassian.com/atlassian-rovo-mcp-server/docs/getting-started-with-the-atlassian-remote-mcp-server/](https://support.atlassian.com/atlassian-rovo-mcp-server/docs/getting-started-with-the-atlassian-remote-mcp-server/)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-github" id="GitHub" data-tool-id="GitHub" data-tool-title="GitHub" markdown>
<div class="tcg-name"><span class="tcg-name__text">GitHub</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![GitHub](https://cdn.simpleicons.org/github){ width="40" .tcg-favicon }</div>
<div class="tcg-type">dev · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search and manage GitHub repositories, issues, pull requests, code reviews, and Actions. Sign in via OAuth Device Flow or pass a personal access token (GITHUB_PERSONAL_ACCESS_TOKEN).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; GitHub · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1 / PAT</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — GitHub (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://api.githubcopilot.com/mcp/`

**Auth** — OAuth 2.1 / PAT

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — `GITHUB_PERSONAL_ACCESS_TOKEN`

**Tags** — global

**Description**

Search and manage GitHub repositories, issues, pull requests, code reviews, and Actions. Sign in via OAuth Device Flow or pass a personal access token (GITHUB_PERSONAL_ACCESS_TOKEN).

Docs: https://github.com/github/github-mcp-server

**Docs** — [https://github.com/github/github-mcp-server](https://github.com/github/github-mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Microsoft-Learn" data-tool-id="Microsoft-Learn" data-tool-title="Microsoft Learn" markdown>
<div class="tcg-name"><span class="tcg-name__text">Microsoft Learn</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-school-outline:</div>
<div class="tcg-type">dev · global · free-tier <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search the full Microsoft Learn documentation catalogue — Azure, .NET, Windows, Power Platform, Microsoft 365 and more. No authentication required, free to use.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://learn.microsoft.com/api/mcp`

**Auth** — None

**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global · free-tier

**Description**

Search the full Microsoft Learn documentation catalogue — Azure, .NET, Windows, Power Platform, Microsoft 365 and more. No authentication required, free to use.

Docs: https://learn.microsoft.com/en-us/training/support/mcp

**Docs** — [https://learn.microsoft.com/en-us/training/support/mcp](https://learn.microsoft.com/en-us/training/support/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Sentry" data-tool-id="Sentry" data-tool-title="Sentry" markdown>
<div class="tcg-name"><span class="tcg-name__text">Sentry</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Sentry](https://cdn.simpleicons.org/sentry){ width="40" .tcg-favicon }</div>
<div class="tcg-type">dev · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Investigate Sentry issues and events across projects, query alert rules, releases, and performance data. OAuth with device-code flow.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Sentry · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Sentry (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.sentry.dev/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 1

**Required env** — —

**Tags** — global

**Description**

Investigate Sentry issues and events across projects, query alert rules, releases, and performance data. OAuth with device-code flow.

Docs: https://docs.sentry.io/product/sentry-mcp/

**Docs** — [https://docs.sentry.io/product/sentry-mcp/](https://docs.sentry.io/product/sentry-mcp/)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Asana" data-tool-id="Asana" data-tool-title="Asana" markdown>
<div class="tcg-name"><span class="tcg-name__text">Asana</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>![Asana](https://cdn.simpleicons.org/asana){ width="40" .tcg-favicon }</div>
<div class="tcg-type">project_mgmt · global <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Manage Asana tasks, projects, sections, and custom fields across workspaces. Asana's official OAuth MCP (SSE transport).
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Asana · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Asana (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.asana.com/v2/mcp`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global

**Description**

Manage Asana tasks, projects, sections, and custom fields across workspaces. Asana's official OAuth MCP (SSE transport).

Docs: https://developers.asana.com/docs/mcp

**Docs** — [https://developers.asana.com/docs/mcp](https://developers.asana.com/docs/mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Azure-DevOps" data-tool-id="Azure-DevOps" data-tool-title="Azure DevOps" markdown>
<div class="tcg-name"><span class="tcg-name__text">Azure DevOps</span> <span class="cost">🔐</span></div>
<div class="tcg-art" markdown>:material-microsoft-azure-devops:</div>
<div class="tcg-type">dev · global · preview <span class="risk risk-l3">preview</span></div>
<div class="tcg-body" markdown>
Query and update Azure DevOps repos, work items (Boards), pipelines, and pull requests within your organisation. Preview, requires AZURE_DEVOPS_ORG.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;OAuth 2.1</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.dev.azure.com/${AZURE_DEVOPS_ORG}`

**Auth** — OAuth 2.1

**OAuth 2.1** — runs the [Authorization Code flow](../mcp-server/index.md#oauth-21-authorization-code) on Save & Connect → **Authorize**.
**Stability** — PREVIEW · **Tier** — Tier 2

**Required env** — `AZURE_DEVOPS_ORG`

**Tags** — global · preview

**Description**

Query and update Azure DevOps repos, work items (Boards), pipelines, and pull requests within your organisation. Preview, requires AZURE_DEVOPS_ORG.

Docs: https://learn.microsoft.com/en-us/azure/devops/mcp-server/remote-mcp-server

**Docs** — [https://learn.microsoft.com/en-us/azure/devops/mcp-server/remote-mcp-server](https://learn.microsoft.com/en-us/azure/devops/mcp-server/remote-mcp-server)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Context7" data-tool-id="Context7" data-tool-title="Context7" markdown>
<div class="tcg-name"><span class="tcg-name__text">Context7</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>![Context7](https://cdn.simpleicons.org/upstash){ width="40" .tcg-favicon }</div>
<div class="tcg-type">dev · global · free-tier <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Pull up-to-date library documentation and code examples by library name and version, designed to ground AI code generation. Free, no authentication.
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Upstash · T2 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Upstash (vendor-official (Tier 1))

**Transport** — Streamable HTTP

**URL** — `https://mcp.context7.com/mcp`

**Auth** — None

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · free-tier

**Description**

Pull up-to-date library documentation and code examples by library name and version, designed to ground AI code generation. Free, no authentication.

Docs: https://github.com/upstash/context7

**Docs** — [https://github.com/upstash/context7](https://github.com/upstash/context7)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Korean-Law-MCP" data-tool-id="Korean-Law-MCP" data-tool-title="Korean Law MCP" markdown>
<div class="tcg-name"><span class="tcg-name__text">Korean Law MCP</span> <span class="cost">🆓</span></div>
<div class="tcg-art" markdown>:material-scale-balance:</div>
<div class="tcg-type">dev · korea · legal <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
Search Korean law, court precedents, Constitutional Court decisions, ordinances, and administrative rules. Wraps the Ministry of Government Legislation Open API into 17 MCP tools — citation…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; chrisryugj · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;None</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — chrisryugj (community-maintained)

**Transport** — Streamable HTTP

**URL** — `https://korean-law-mcp.fly.dev/mcp`

**Auth** — None

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — korea · legal

**Description**

Search Korean law, court precedents, Constitutional Court decisions, ordinances, and administrative rules. Wraps the Ministry of Government Legislation Open API into 17 MCP tools — citation verification, time-travel diff, and impact graph.

• Quick start — free remote endpoint (pre-filled below):
  https://korean-law-mcp.fly.dev/mcp

• Local STDIO install (npm):
  npm install -g korean-law-mcp
  korean-law-mcp --api-key <LAW_API_KEY>
  Get the API key at https://open.law.go.kr

Repo: https://github.com/chrisryugj/korean-law-mcp

**Docs** — [https://github.com/chrisryugj/korean-law-mcp](https://github.com/chrisryugj/korean-law-mcp)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Git" data-tool-id="Git" data-tool-title="Git" markdown>
<div class="tcg-name"><span class="tcg-name__text">Git</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Git](https://cdn.simpleicons.org/git){ width="40" .tcg-favicon }</div>
<div class="tcg-type">dev · global · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Local Git repository operations — log, diff, status, blame, show. Read-only by default. The activated form is pre-filled to run: uvx mcp-server-git --repository .
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; modelcontextprotocol/servers · T2 community</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — modelcontextprotocol/servers (community-maintained)

**Transport** — STDIO

**Command** — `uvx`

**Args** — `mcp-server-git --repository .`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · community

**Description**

[macOS] Local Git repository operations — log, diff, status, blame, show. Read-only by default.

Prereq: install uv on macOS.
  brew install uv

The activated form is pre-filled to run:
  uvx mcp-server-git --repository .

Note: change '.' to the absolute path of the repo you want to inspect.

Docs: https://github.com/modelcontextprotocol/servers/tree/main/src/git

**Docs** — [https://github.com/modelcontextprotocol/servers/tree/main/src/git](https://github.com/modelcontextprotocol/servers/tree/main/src/git)

</div>
</div>

<div class="tcg-card tcg-card--clickable" id="Puppeteer" data-tool-id="Puppeteer" data-tool-title="Puppeteer" markdown>
<div class="tcg-name"><span class="tcg-name__text">Puppeteer</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>![Puppeteer](https://cdn.simpleicons.org/puppeteer){ width="40" .tcg-favicon }</div>
<div class="tcg-type">dev · global · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Headless Chrome automation — navigate, click, fill forms, screenshot, evaluate JavaScript in-page. The activated form is pre-filled to run: npx -y @modelcontextprotocol/server-puppeteer…
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

**Args** — `-y @modelcontextprotocol/server-puppeteer`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · community

**Description**

[macOS] Headless Chrome automation — navigate, click, fill forms, screenshot, evaluate JavaScript in-page.

Prereq: Node.js 18+ on macOS. First run downloads ~170 MB Chromium under ~/Library/Caches/Puppeteer.

The activated form is pre-filled to run:
  npx -y @modelcontextprotocol/server-puppeteer

Optional — set in the env section:
  PUPPETEER_LAUNCH_OPTIONS — JSON launch options, e.g. {"executablePath":"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
  ALLOW_DANGEROUS — set to true to enable --no-sandbox

Security: visits arbitrary URLs as instructed by the agent — review prompts before granting net access.

Docs: https://github.com/modelcontextprotocol/servers/tree/main/src/puppeteer

**Docs** — [https://github.com/modelcontextprotocol/servers/tree/main/src/puppeteer](https://github.com/modelcontextprotocol/servers/tree/main/src/puppeteer)

</div>
</div>

<div class="tcg-card tcg-card--clickable t-microsoft" id="Playwright" data-tool-id="Playwright" data-tool-title="Playwright" markdown>
<div class="tcg-name"><span class="tcg-name__text">Playwright</span> <span class="cost">🛠</span></div>
<div class="tcg-art" markdown>:material-script-text-play-outline:</div>
<div class="tcg-type">dev · global · community <span class="risk risk-l0">ga</span></div>
<div class="tcg-body" markdown>
[macOS] Microsoft's accessibility-snapshot browser automation — successor to the Puppeteer reference. Drives Chromium/Firefox/WebKit without screenshot vision models. The activated form is pre-filled…
</div>
<div class="tcg-stats" markdown>
<div class="tcg-stats__line" markdown>**Vendor** &nbsp; Microsoft · T1 vendor</div>
<div class="tcg-stats__line" markdown>**Auth** &nbsp; &nbsp; &nbsp; &nbsp;STDIO</div>
</div>
<div class="tcg-cta">Click for transport · auth · required env · description · docs</div>
<div class="tcg-detail-template" hidden markdown>

**Vendor** — Microsoft (vendor-official)

**Transport** — STDIO

**Command** — `npx`

**Args** — `-y @playwright/mcp@latest`

**OS variants** — mac · linux · win (catalog picks the entry matching the host OS automatically; macOS / Linux use `npx` or `uvx`; Windows uses `npx.cmd`).

**Auth** — STDIO

**Stability** — GA · **Tier** — Tier 2

**Required env** — —

**Tags** — global · community

**Description**

[macOS] Microsoft's accessibility-snapshot browser automation — successor to the Puppeteer reference. Drives Chromium/Firefox/WebKit without screenshot vision models.

Prereq: Node.js 18+ on macOS (Homebrew, nvm, or installer). First run downloads ~300 MB browser binaries under ~/Library/Caches/ms-playwright.

The activated form is pre-filled to run:
  npx -y @playwright/mcp@latest

Optional flags:
  --browser firefox|webkit          # default chromium
  --port 8931                       # serve over HTTP instead of stdio

Security: visits arbitrary URLs as instructed by the agent — review prompts before granting net access.

Docs: https://github.com/microsoft/playwright-mcp

**Docs** — [https://github.com/microsoft/playwright-mcp](https://github.com/microsoft/playwright-mcp)

</div>
</div>

</div>

## Workflow combinations { #combinations }

This page mixes remote (vendor-official) DevOps surfaces with community-published stdio dev helpers. Common combinations:

- **PR review pipeline** — `GitHub` + `Sentry` + `Linear`. The agent reads a PR, checks the latest issues in Sentry, and adds a Linear ticket if it spots a regression — all in one turn.
- **Ticket-driven coding** — `Linear` (issue body) + `Context7` (look up the right SDK fragment) + `Git` stdio (apply patches against a local checkout). The stdio entry keeps PR drafts local until you push.
- **Docs grounding for unfamiliar APIs** — `Microsoft Learn` + `Context7`. Both are free, no-auth, and explicitly designed to ground AI code generation; chain them as a fallback when GitHub README content isn't enough.
- **Headless browser automation** — `Puppeteer` *or* `Playwright` stdio. They cover the same surface — pick Puppeteer for lightweight scraping, Playwright when you need cross-browser parity.
- **Legal-grounded answers (KR)** — `Korean Law MCP` as a single-source grounding service for Korean-statute lookups.

## Auth & secrets { #auth-secrets }

Mixed-auth page — OAuth for remote vendor surfaces, none for the community stdio helpers, and one entry (Korean Law) that's free and unauthenticated:

| Connection | Auth | Extra env / prereq |
|---|---|---|
| GitHub | OAuth 2.1 *or* Personal Access Token | — (PAT goes in headers as `${GITHUB_TOKEN}`) |
| Linear | Linear OAuth | — |
| Atlassian Rovo (Jira + Confluence) | Atlassian OAuth | — |
| Sentry | Sentry OAuth | — |
| Asana | Asana OAuth | — |
| Azure DevOps | Microsoft OAuth (preview) | `MS_TENANT_ID` for `dev.azure.com` orgs scoped to a tenant |
| Microsoft Learn | None (free) | — |
| Context7 | None (free) | — |
| Korean Law MCP | None (free) | — |
| Git (stdio) | None | Node.js 18+ for `npx` (macOS / Linux) or `npx.cmd` (Windows) |
| Puppeteer (stdio) | None | Node.js 18+ — first run downloads a Chromium |
| Playwright (stdio) | None | Node.js 18+ — first run downloads browser binaries |

## Picking guide { #picking-guide }

| If you need… | Reach for |
|---|---|
| Code hosting + PR / Actions context | `GitHub` |
| Linear-style fast issue trackers | `Linear` |
| Jira / Confluence in one connection | `Atlassian Rovo` |
| Runtime error context | `Sentry` |
| Microsoft / Azure project tracking | `Azure DevOps` |
| Free, unauthenticated doc grounding | `Microsoft Learn` (MS stack) · `Context7` (everything else) |
| Local repo inspection without granting GitHub write scope | `Git` stdio (read-only by default) |
| Browser automation | `Puppeteer` for scraping · `Playwright` for cross-browser tests |
| Korean statutes / case law lookup | `Korean Law MCP` |

